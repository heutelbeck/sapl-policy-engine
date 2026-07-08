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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.mock.web.MockHttpServletResponse;

import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.stream.LatestSlotStream;
import io.sapl.api.stream.Stream;
import io.sapl.node.auth.http.HttpAuthHandler;
import io.sapl.node.auth.http.HttpAuthHandler.HttpAuthResult;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
                // never reached, the pump is rejected before it runs
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
        when(authHandler.authenticate(any())).thenReturn(new HttpAuthResult("default", null));
        val body = "{\"subject\":\"u\",\"action\":\"r\",\"resource\":\"d\"}".getBytes(UTF_8);
        when(request.getInputStream()).thenReturn(new DelegatingServletInputStream(new ByteArrayInputStream(body)));
        when(request.startAsync()).thenReturn(asyncContext);
        when(pumpExecutor.submit(any(Runnable.class))).thenThrow(new RejectedExecutionException("shutting down"));

        servlet().handlePost(request, new MockHttpServletResponse());

        verify(connectionRegistry).unregister(any(SseConnection.class));
        verify(asyncContext).complete();
    }

    @Test
    @DisplayName("an SSE stream authenticated with a JWT is scheduled to close at the token's expiry")
    void whenJwtExpiryThenConnectionScheduledToCloseAtExpiry() throws Exception {
        when(authHandler.authenticate(any())).thenReturn(new HttpAuthResult("default", Instant.now().plusSeconds(300)));
        val body = "{\"subject\":\"u\",\"action\":\"r\",\"resource\":\"d\"}".getBytes(UTF_8);
        when(request.getInputStream()).thenReturn(new DelegatingServletInputStream(new ByteArrayInputStream(body)));
        when(request.startAsync()).thenReturn(asyncContext);
        when(keepAliveScheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenReturn(mock(ScheduledFuture.class));
        val expiryTask = ArgumentCaptor.forClass(Runnable.class);
        val delay      = ArgumentCaptor.forClass(Long.class);

        servlet().handlePost(request, new MockHttpServletResponse());

        verify(keepAliveScheduler).schedule(expiryTask.capture(), delay.capture(), eq(TimeUnit.MILLISECONDS));
        assertThat(delay.getValue()).isBetween(290_000L, 300_000L);

        expiryTask.getValue().run();
        verify(connectionRegistry).unregister(any(SseConnection.class));
        verify(asyncContext).complete();
    }

    @Test
    @DisplayName("a non-expiring credential (basic auth or api key) schedules no expiry close")
    void whenNoTokenExpiryThenNoExpiryScheduled() throws Exception {
        when(authHandler.authenticate(any())).thenReturn(new HttpAuthResult("default", null));
        val body = "{\"subject\":\"u\",\"action\":\"r\",\"resource\":\"d\"}".getBytes(UTF_8);
        when(request.getInputStream()).thenReturn(new DelegatingServletInputStream(new ByteArrayInputStream(body)));
        when(request.startAsync()).thenReturn(asyncContext);

        servlet().handlePost(request, new MockHttpServletResponse());

        verify(keepAliveScheduler, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("at the JWT expiry the upstream stream is closed and the response completed, so delivery stops")
    void whenJwtExpiresThenStreamClosedAndConnectionCompleted() throws Exception {
        val scheduler    = Executors.newSingleThreadScheduledExecutor(r -> {
                             val t = new Thread(r, "sapl-test-expiry");
                             t.setDaemon(true);
                             return t;
                         });
        val pump         = Executors.newVirtualThreadPerTaskExecutor();
        val stream       = new LatestSlotStream<AuthorizationDecision>(); // never fed, pump parks in awaitNext()
        val streamClosed = new CountDownLatch(1);
        stream.onClose(streamClosed::countDown);
        try (stream) {
            val response = mock(HttpServletResponse.class);
            // the writer is fetched lazily on the first frame write, which may not happen
            // here
            // getWriter and getResponse are both on the lazy first-frame-write path,
            // which the expiry-driven teardown may reach only after the invariant below
            // is already observed. Both are conditional stubs, so both are lenient.
            lenient().when(response.getWriter()).thenReturn(new PrintWriter(Writer.nullWriter()));
            lenient().when(asyncContext.getResponse()).thenReturn(response);
            // exp 300ms out, keep-alive 30s, so only the expiry timer closes the stream in
            // the await window
            when(authHandler.authenticate(any()))
                    .thenReturn(new HttpAuthResult("default", Instant.now().plusMillis(300)));
            val body = "{\"subject\":\"u\",\"action\":\"r\",\"resource\":\"d\"}".getBytes(UTF_8);
            when(request.getInputStream()).thenReturn(new DelegatingServletInputStream(new ByteArrayInputStream(body)));
            when(request.startAsync()).thenReturn(asyncContext);

            val servlet = new SseStreamServlet<AuthorizationSubscription, AuthorizationDecision>(authHandler, mapper,
                    Duration.ofSeconds(30), scheduler, pump, connectionRegistry) {
                @Override
                protected Class<AuthorizationSubscription> subscriptionType() {
                    return AuthorizationSubscription.class;
                }

                @Override
                protected Stream<AuthorizationDecision> openStream(AuthorizationSubscription subscription,
                        String pdpId) {
                    return stream;
                }

                @Override
                protected AuthorizationDecision indeterminate() {
                    return AuthorizationDecision.INDETERMINATE;
                }
            };

            servlet.handlePost(request, response);

            assertThat(streamClosed.await(10, TimeUnit.SECONDS)).isTrue();
            verify(asyncContext, timeout(10_000)).complete();
        } finally {
            scheduler.shutdownNow();
            pump.shutdownNow();
        }
    }

    @Test
    @DisplayName("a chunked over-limit body is rejected with 413 before the stream is opened")
    void whenBodyExceedsLimitThenContentTooLargeAndNoStreamOpened() throws Exception {
        when(authHandler.authenticate(any())).thenReturn(new HttpAuthResult("default", null));
        when(request.getInputStream()).thenReturn(new TooLargeInputStream());
        val response = new MockHttpServletResponse();

        servlet().handlePost(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        verifyNoInteractions(pumpExecutor, connectionRegistry);
    }

    @Test
    @DisplayName("a JSON literal null subscription is rejected with 400 before the stream is opened")
    void whenSubscriptionBodyIsJsonNullThenBadRequestAndNoStreamOpened() throws Exception {
        when(authHandler.authenticate(any())).thenReturn(new HttpAuthResult("default", null));
        val body = "null".getBytes(UTF_8);
        when(request.getInputStream()).thenReturn(new DelegatingServletInputStream(new ByteArrayInputStream(body)));
        val response = new MockHttpServletResponse();

        servlet().handlePost(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(pumpExecutor, connectionRegistry);
    }

    @Test
    @DisplayName("a streaming multi-subscription above the configured entry limit is rejected before the stream is opened")
    void whenStreamingMultiSubscriptionCountExceedsLimitThenBadRequestAndNoStreamOpened() throws Exception {
        val pdp = mock(BlockingPolicyDecisionPoint.class);
        when(authHandler.authenticate(any())).thenReturn(new HttpAuthResult("default", null));
        val body = """
                {
                  "sub1": { "subject": "alice", "action": "read", "resource": "doc1" },
                  "sub2": { "subject": "bob", "action": "write", "resource": "doc2" }
                }
                """.getBytes(UTF_8);
        when(request.getInputStream()).thenReturn(new DelegatingServletInputStream(new ByteArrayInputStream(body)));
        val response = new MockHttpServletResponse();
        val servlet  = new MultiDecideServlet(pdp, authHandler, mapper, Duration.ofSeconds(15), keepAliveScheduler,
                pumpExecutor, connectionRegistry, 1);

        servlet.handlePost(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(pdp, pumpExecutor, connectionRegistry);
    }

    @Test
    @DisplayName("keep-alive cannot be disabled: a non-positive interval falls back to the default and a sub-floor one is raised")
    void whenKeepAliveIntervalConfiguredThenNormalized() {
        assertThat(SseStreamServlet.effectiveKeepAliveInterval(Duration.ZERO)).isEqualTo(Duration.ofSeconds(15));
        assertThat(SseStreamServlet.effectiveKeepAliveInterval(Duration.ofSeconds(-5)))
                .isEqualTo(Duration.ofSeconds(15));
        assertThat(SseStreamServlet.effectiveKeepAliveInterval(null)).isEqualTo(Duration.ofSeconds(15));
        assertThat(SseStreamServlet.effectiveKeepAliveInterval(Duration.ofMillis(200)))
                .isEqualTo(Duration.ofSeconds(1));
        assertThat(SseStreamServlet.effectiveKeepAliveInterval(Duration.ofSeconds(30)))
                .isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("the keep-alive frame is flushed on the per-connection pump thread, not the bounded scheduler thread")
    void whenKeepAliveFiresThenWriteRunsOffTheScheduler() throws Exception {
        val schedulerThreadName = "sapl-test-keepalive";
        val scheduler           = Executors.newSingleThreadScheduledExecutor(r -> {
                                    val t = new Thread(r, schedulerThreadName);
                                    t.setDaemon(true);
                                    return t;
                                });
        val pump                = Executors.newVirtualThreadPerTaskExecutor();
        val stream              = new LatestSlotStream<AuthorizationDecision>(); // never fed, pump parks in awaitNext()
        val writeThreadName     = new AtomicReference<String>();
        val keepAliveWritten    = new CountDownLatch(1);
        try {
            val recordingWriter = new PrintWriter(new Writer() {
                                    @Override
                                    public void write(char[] cbuf, int off, int len) {
                                        writeThreadName.compareAndSet(null, Thread.currentThread().getName());
                                        keepAliveWritten.countDown();
                                    }

                                    @Override
                                    public void flush() {
                                        // no-op
                                    }

                                    @Override
                                    public void close() {
                                        // no-op
                                    }
                                });
            val response        = mock(HttpServletResponse.class);
            when(response.getWriter()).thenReturn(recordingWriter);
            when(asyncContext.getResponse()).thenReturn(response);
            when(authHandler.authenticate(any())).thenReturn(new HttpAuthResult("default", null));
            val body = "{\"subject\":\"u\",\"action\":\"r\",\"resource\":\"d\"}".getBytes(UTF_8);
            when(request.getInputStream()).thenReturn(new DelegatingServletInputStream(new ByteArrayInputStream(body)));
            when(request.startAsync()).thenReturn(asyncContext);

            val servlet = new SseStreamServlet<AuthorizationSubscription, AuthorizationDecision>(authHandler, mapper,
                    Duration.ofSeconds(1), scheduler, pump, connectionRegistry) {
                @Override
                protected Class<AuthorizationSubscription> subscriptionType() {
                    return AuthorizationSubscription.class;
                }

                @Override
                protected Stream<AuthorizationDecision> openStream(AuthorizationSubscription subscription,
                        String pdpId) {
                    return stream;
                }

                @Override
                protected AuthorizationDecision indeterminate() {
                    return AuthorizationDecision.INDETERMINATE;
                }
            };

            servlet.handlePost(request, response);

            assertThat(keepAliveWritten.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(writeThreadName.get()).isNotNull().isNotEqualTo(schedulerThreadName);
        } finally {
            stream.close();
            scheduler.shutdownNow();
            pump.shutdownNow();
        }
    }

    @Test
    @DisplayName("a keep-alive dispatched during teardown does not write to the closed writer after the stream has ended")
    void whenKeepAliveDispatchedDuringTeardownThenItDoesNotWriteToClosedWriter() throws Exception {
        val writerClosed       = new AtomicBoolean(false);
        val wroteAfterClose    = new AtomicBoolean(false);
        val deferredKeepAlives = new ConcurrentLinkedQueue<Runnable>();

        // submit runs synchronously. Execute defers keep-alives until after teardown.
        val deferringPump = new AbstractExecutorService() {
            @Override
            public void execute(Runnable command) {
                deferredKeepAlives.add(command);
            }

            @Override
            public Future<?> submit(Runnable task) {
                task.run();
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void shutdown() {
                // no-op
            }

            @Override
            public List<Runnable> shutdownNow() {
                return List.of();
            }

            @Override
            public boolean isShutdown() {
                return false;
            }

            @Override
            public boolean isTerminated() {
                return false;
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) {
                return true;
            }
        };

        // Run the keep-alive once, synchronously, at schedule time.
        when(keepAliveScheduler.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    invocation.<Runnable>getArgument(0).run();
                    return mock(ScheduledFuture.class);
                });

        val recordingWriter = new PrintWriter(new Writer() {
                                @Override
                                public void write(char[] cbuf, int off, int len) {
                                    if (writerClosed.get()) {
                                        wroteAfterClose.set(true);
                                    }
                                }

                                @Override
                                public void flush() {
                                    // no-op
                                }

                                @Override
                                public void close() {
                                    writerClosed.set(true);
                                }
                            });
        val response        = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(recordingWriter);
        when(asyncContext.getResponse()).thenReturn(response);
        when(authHandler.authenticate(any())).thenReturn(new HttpAuthResult("default", null));
        val body = "{\"subject\":\"u\",\"action\":\"r\",\"resource\":\"d\"}".getBytes(UTF_8);
        when(request.getInputStream()).thenReturn(new DelegatingServletInputStream(new ByteArrayInputStream(body)));
        when(request.startAsync()).thenReturn(asyncContext);

        // Stream ends immediately so the pump exits and teardown closes the writer.
        val endedStream = new LatestSlotStream<AuthorizationDecision>();
        endedStream.close();

        val servlet = new SseStreamServlet<AuthorizationSubscription, AuthorizationDecision>(authHandler, mapper,
                Duration.ofSeconds(1), keepAliveScheduler, deferringPump, connectionRegistry) {
            @Override
            protected Class<AuthorizationSubscription> subscriptionType() {
                return AuthorizationSubscription.class;
            }

            @Override
            protected Stream<AuthorizationDecision> openStream(AuthorizationSubscription subscription, String pdpId) {
                return endedStream;
            }

            @Override
            protected AuthorizationDecision indeterminate() {
                return AuthorizationDecision.INDETERMINATE;
            }
        };

        servlet.handlePost(request, response);

        assertThat(writerClosed).isTrue();
        deferredKeepAlives.forEach(Runnable::run);

        assertThat(wroteAfterClose).as("late keep-alive must not write to the closed writer").isFalse();
        verify(asyncContext).complete();
    }

    @Test
    @DisplayName("a client that died without closing is detected via the keep-alive write failure: the stream is closed and the async context completed and unregistered")
    void whenKeepAliveWriteFailsThenStreamClosedAndAsyncContextReclaimed() throws Exception {
        val scheduler = Executors.newSingleThreadScheduledExecutor();
        val pump      = Executors.newVirtualThreadPerTaskExecutor();
        val stream    = new LatestSlotStream<AuthorizationDecision>(); // never fed, pump parks in awaitNext()
        try {
            val brokenPipe = new PrintWriter(new Writer() {
                               @Override
                               public void write(char[] cbuf, int off, int len) throws IOException {
                                   throw new IOException("broken pipe");
                               }

                               @Override
                               public void flush() throws IOException {
                                   throw new IOException("broken pipe");
                               }

                               @Override
                               public void close() {
                                   // no-op
                               }
                           });
            val response   = mock(HttpServletResponse.class);
            when(response.getWriter()).thenReturn(brokenPipe);
            when(asyncContext.getResponse()).thenReturn(response);
            when(authHandler.authenticate(any())).thenReturn(new HttpAuthResult("default", null));
            val body = "{\"subject\":\"u\",\"action\":\"r\",\"resource\":\"d\"}".getBytes(UTF_8);
            when(request.getInputStream()).thenReturn(new DelegatingServletInputStream(new ByteArrayInputStream(body)));
            when(request.startAsync()).thenReturn(asyncContext);

            val servlet = new SseStreamServlet<AuthorizationSubscription, AuthorizationDecision>(authHandler, mapper,
                    Duration.ofSeconds(1), scheduler, pump, connectionRegistry) {
                @Override
                protected Class<AuthorizationSubscription> subscriptionType() {
                    return AuthorizationSubscription.class;
                }

                @Override
                protected Stream<AuthorizationDecision> openStream(AuthorizationSubscription subscription,
                        String pdpId) {
                    return stream;
                }

                @Override
                protected AuthorizationDecision indeterminate() {
                    return AuthorizationDecision.INDETERMINATE;
                }
            };

            servlet.handlePost(request, response);

            verify(connectionRegistry, timeout(10000)).unregister(any(SseConnection.class));
            verify(asyncContext, timeout(10000)).complete();
        } finally {
            scheduler.shutdownNow();
            pump.shutdownNow();
        }
    }
}
