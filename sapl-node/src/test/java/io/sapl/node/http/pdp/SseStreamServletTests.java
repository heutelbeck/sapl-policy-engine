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
package io.sapl.node.http.pdp;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.mock.web.MockHttpServletResponse;

import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.stream.Stream;
import io.sapl.node.auth.http.HttpAuthHandler;
import io.sapl.node.auth.http.HttpAuthHandler.HttpAuthResult;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.val;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("SseStreamServlet")
class SseStreamServletTests {

    @Mock
    private HttpAuthHandler authHandler;

    @Mock
    private ExecutorService pumpExecutor;

    @Mock
    private ScheduledExecutorService keepAliveScheduler;

    @Mock
    private SseConnectionRegistry connectionRegistry;

    @Mock
    private HttpServletRequest request;

    @Mock
    private AsyncContext asyncContext;

    private final JsonMapper mapper = JsonMapper.builder().addModule(new SaplJacksonModule()).build();

    private SseStreamServlet<AuthorizationSubscription, AuthorizationDecision> servlet() {
        return new SseStreamServlet<>(authHandler, mapper, Duration.ofSeconds(15), keepAliveScheduler, pumpExecutor,
                connectionRegistry) {
            @Override
            protected Class<AuthorizationSubscription> subscriptionType() {
                return AuthorizationSubscription.class;
            }

            @Override
            protected Stream<AuthorizationDecision> openStream(AuthorizationSubscription subscription, String pdpId) {
                // Never reached: the pump is rejected before it runs.
                return null;
            }

            @Override
            protected AuthorizationDecision indeterminate() {
                return AuthorizationDecision.INDETERMINATE;
            }
        };
    }

    @Test
    @DisplayName("when the pump executor rejects the task the async context is completed and unregistered, not leaked")
    void whenPumpRejectedThenAsyncContextCompletedAndUnregistered() throws Exception {
        when(authHandler.authenticate(any())).thenReturn(new HttpAuthResult("default"));
        val body = "{\"subject\":\"u\",\"action\":\"r\",\"resource\":\"d\"}".getBytes(UTF_8);
        when(request.getInputStream()).thenReturn(new DelegatingServletInputStream(new ByteArrayInputStream(body)));
        when(request.startAsync()).thenReturn(asyncContext);
        when(pumpExecutor.submit(any(Runnable.class))).thenThrow(new RejectedExecutionException("shutting down"));

        servlet().handlePost(request, new MockHttpServletResponse());

        verify(connectionRegistry).unregister(asyncContext);
        verify(asyncContext).complete();
    }
}
