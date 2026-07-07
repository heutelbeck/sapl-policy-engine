/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.node.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.sapl.node.limits.ConcurrencyLimit;
import io.sapl.node.limits.RejectionReporter;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import lombok.val;

@DisplayName("SseStreamLimitFilter")
@ExtendWith(MockitoExtension.class)
class SseStreamLimitFilterTests {

    private static final String POST = "POST";
    private static final String PATH = "/api/pdp/decide";

    @Mock
    private FilterChain chain;

    private ConcurrencyLimit     limit;
    private SseStreamLimitFilter filter;

    @BeforeEach
    void setUp() {
        limit  = new ConcurrencyLimit(1);
        filter = new SseStreamLimitFilter(limit, new RejectionReporter("test", "test limit", null));
    }

    @Test
    @DisplayName("over-cap connection attempts are shed with 503 and Retry-After before reaching the servlet")
    void whenCapReachedThenRequestShedWithServiceUnavailable() throws Exception {
        val blocker = limit.tryAcquire();
        assertThat(blocker).isNotNull();
        val response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(POST, PATH), response, chain);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(response.getHeader(SseStreamLimitFilter.HEADER_RETRY_AFTER))
                .isEqualTo(SseStreamLimitFilter.RETRY_AFTER_SECONDS);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("a request that never starts async processing releases its slot on filter exit")
    void whenNoAsyncStartedThenSlotReleasedOnExit() throws Exception {
        filter.doFilter(new MockHttpServletRequest(POST, PATH), new MockHttpServletResponse(), chain);
        assertThat(limit.active()).isZero();
        verify(chain).doFilter(any(), any());
    }

    @MethodSource("terminalAsyncEvents")
    @ParameterizedTest(name = "released on {0}")
    @DisplayName("a streaming request holds its slot until the async lifecycle terminates, on any terminal event")
    void whenAsyncStartedThenSlotHeldUntilAsyncTerminates(String eventName, TerminalEvent terminalEvent)
            throws Exception {
        val capturedListener = startAsyncThroughFilter(HttpServletRequest::startAsync);

        assertThat(limit.active()).as("slot held while the stream is open").isEqualTo(1);
        assertThat(limit.tryAcquire()).isNull();

        terminalEvent.fire(capturedListener);
        assertThat(limit.active()).as("slot released on %s", eventName).isZero();
    }

    @Test
    @DisplayName("the two-argument startAsync overload binds the slot to the async lifecycle as well")
    void whenAsyncStartedWithArgumentsThenSlotFollowsAsyncLifecycle() throws Exception {
        val capturedListener = startAsyncThroughFilter(request -> request.startAsync(request, null));

        assertThat(limit.active()).isEqualTo(1);
        capturedListener.onComplete(mock(AsyncEvent.class));
        assertThat(limit.active()).isZero();
    }

    static Stream<Arguments> terminalAsyncEvents() {
        return Stream.of(
                arguments("onComplete", (TerminalEvent) listener -> listener.onComplete(mock(AsyncEvent.class))),
                arguments("onError", (TerminalEvent) listener -> listener.onError(mock(AsyncEvent.class))),
                arguments("onTimeout", (TerminalEvent) listener -> listener.onTimeout(mock(AsyncEvent.class))));
    }

    @FunctionalInterface
    interface TerminalEvent {
        void fire(AsyncListener listener) throws Exception;
    }

    @FunctionalInterface
    interface AsyncStart {
        void start(HttpServletRequest request);
    }

    /**
     * Runs a request through the filter whose downstream servlet starts async
     * processing, as the SSE servlets do before handing the connection to the
     * pump, and returns the listener the filter registered on the async context.
     */
    private AsyncListener startAsyncThroughFilter(AsyncStart asyncStart) throws Exception {
        val asyncContext     = mock(AsyncContext.class);
        val originalRequest  = mock(HttpServletRequest.class);
        val capturedListener = new AtomicReference<AsyncListener>();
        lenient().when(originalRequest.startAsync()).thenReturn(asyncContext);
        lenient().when(originalRequest.startAsync(any(), any())).thenReturn(asyncContext);
        doAnswer(invocation -> {
            val wrapped = invocation.<HttpServletRequest>getArgument(0);
            asyncStart.start(wrapped);
            val listenerCaptor = ArgumentCaptor.forClass(AsyncListener.class);
            verify(asyncContext).addListener(listenerCaptor.capture());
            capturedListener.set(listenerCaptor.getValue());
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(originalRequest, new MockHttpServletResponse(), chain);
        return capturedListener.get();
    }
}
