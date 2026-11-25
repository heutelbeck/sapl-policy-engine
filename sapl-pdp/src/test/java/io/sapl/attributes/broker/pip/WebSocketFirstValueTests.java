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
package io.sapl.attributes.broker.pip;

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Poll;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.api.stream.Stream;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.attributes.http.BlockingWebClient;
import io.sapl.attributes.libraries.HttpPolicyInformationPoint;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocket subscription first value")
class WebSocketFirstValueTests {

    private static final Duration WAIT            = Duration.ofSeconds(3);
    private static final Duration INITIAL_TIMEOUT = Duration.ofMillis(200);

    @Mock
    private HttpClient        httpClient;
    @Mock
    private WebSocket.Builder builder;
    @Mock
    private WebSocket         socket;

    private final Handshake                                       handshake            = new Handshake();
    private final CountDownLatch                                  openingStarted       = new CountDownLatch(1);
    private final CountDownLatch                                  subscriptionReturned = new CountDownLatch(1);
    private final AtomicReference<WebSocket.Listener>             listener             = new AtomicReference<>();
    private final BlockingQueue<Value>                            observations         = new LinkedBlockingQueue<>();
    private final CompletableFuture<AttributeBroker.Subscription> subscription         = new CompletableFuture<>();
    private PolicyInformationPointAttributeBroker                 broker;
    private BlockingWebClient                                     client;
    private Thread                                                opener;

    @BeforeEach
    void setUp() {
        when(httpClient.newWebSocketBuilder()).thenReturn(builder);
        when(builder.buildAsync(any(URI.class), any(WebSocket.Listener.class))).thenAnswer(invocation -> {
            listener.set(invocation.getArgument(1));
            openingStarted.countDown();
            return handshake;
        });
        client = new BlockingWebClient(JsonMapper.builder().build(), httpClient);
        broker = new PolicyInformationPointAttributeBroker();
        broker.load(new HttpPolicyInformationPoint(client));
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        handshake.cancel(true);
        subscription.thenAccept(AttributeBroker.Subscription::close);
        broker.close();
        if (opener != null) {
            opener.join(WAIT);
            assertThat(opener.isAlive()).isFalse();
        }
    }

    @Test
    @DisplayName("a pending handshake is inside the real broker first-value deadline")
    void whenHandshakeIsPendingThenFirstValueTimeoutCancelsOpening() throws InterruptedException {
        openSubscription(INITIAL_TIMEOUT);
        assertThat(openingStarted.await(WAIT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        assertThat(subscriptionReturned.await(WAIT.toMillis(), TimeUnit.MILLISECONDS))
                .as("subscription returns before the handshake completes").isTrue();
        assertThat(handshake.cancelled.await(WAIT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();

        Value observed;
        do {
            observed = observations.poll(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } while (Value.UNDEFINED.equals(observed));
        assertThat(observed).isInstanceOfSatisfying(ErrorValue.class,
                error -> assertThat(error.message()).contains("http.websocket", "timeout", INITIAL_TIMEOUT.toString()));
        assertThat(handshake.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("a successful opening delivers the first value and closes normally")
    void whenHandshakeCompletesThenFirstValueArrivesAndCloseReleasesSocket()
            throws InterruptedException, ExecutionException, TimeoutException {
        openSubscription(Duration.ofSeconds(2));
        assertThat(openingStarted.await(WAIT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        listener.get().onOpen(socket);
        handshake.complete(socket);
        listener.get().onText(socket, "true", true);

        assertThat(observations.poll(WAIT.toMillis(), TimeUnit.MILLISECONDS)).isEqualTo(Value.TRUE);
        subscription.get(WAIT.toMillis(), TimeUnit.MILLISECONDS).close();
        verify(socket).sendClose(WebSocket.NORMAL_CLOSURE, "client-close");
        assertThat(handshake.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("closing during opening cancels it and aborts an already queued late socket")
    void whenClosedDuringHandshakeThenLateSocketIsAbortedAndNoValueIsDelivered()
            throws InterruptedException, ExecutionException {
        val result = new CompletableFuture<Stream<Value>>();
        opener = Thread.startVirtualThread(() -> result.complete(client.consumeWebSocket(settings())));
        assertThat(openingStarted.await(WAIT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        assertThat(result).succeedsWithin(WAIT);
        try (val values = result.get()) {
            values.close();
            assertThat(handshake.isCancelled()).isTrue();
            listener.get().onOpen(socket);
            listener.get().onText(socket, "true", true);
            verify(socket).abort();
            assertThat(values.tryNext()).isInstanceOf(Poll.Done.class);
        }
    }

    @Test
    @DisplayName("closing after opening completion also owns a not-yet-delivered socket")
    void whenClosedAfterHandshakeBeforeOnOpenThenSocketIsReleased() {
        handshake.complete(socket);
        try (val values = client.consumeWebSocket(settings())) {
            values.close();
            listener.get().onOpen(socket);
            verify(socket).sendClose(WebSocket.NORMAL_CLOSURE, "client-close");
            verify(socket).abort();
            assertThat(values.tryNext()).isInstanceOf(Poll.Done.class);
        }
    }

    @Test
    @DisplayName("an ordinary handshake failure preserves the original error message")
    void whenHandshakeFailsThenOriginalErrorIsDelivered() throws InterruptedException, TimeoutException {
        handshake.completeExceptionally(new CompletionException(new IOException("Opening ended.")));
        try (val values = client.consumeWebSocket(settings())) {
            assertThat(values.awaitNext(WAIT)).isEqualTo(Value.error("Opening ended."));
            assertThat(values.awaitNext(WAIT)).isNull();
        }
    }

    private void openSubscription(Duration firstValueTimeout) {
        val context      = new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
        val invocation   = new AttributeFinderInvocation("office", "rooms", "http.websocket", List.of(settings()),
                firstValueTimeout, Duration.ofHours(1), Duration.ofMillis(50), 0, false, context);
        val key          = new SubscriptionKey(invocation, false);
        val dependencies = Set.of(key);
        opener = Thread.startVirtualThread(() -> {
            try {
                subscription.complete(broker.open("rooms", dependencies, current -> observe(current, key)));
            } catch (RuntimeException failure) {
                subscription.completeExceptionally(failure);
            } finally {
                subscriptionReturned.countDown();
            }
        });
    }

    private Set<SubscriptionKey> observe(Map<SubscriptionKey, AttributeSnapshot> current, SubscriptionKey key) {
        val value = current.get(key);
        if (value != null) {
            observations.add(value.value());
        }
        return Set.of(key);
    }

    private static ObjectValue settings() {
        return ObjectValue.builder().put("baseUrl", Value.of("ws://office.example.test/rooms")).build();
    }

    private static final class Handshake extends CompletableFuture<WebSocket> {
        private final CountDownLatch cancelled = new CountDownLatch(1);

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            val result = super.cancel(mayInterruptIfRunning);
            if (result) {
                cancelled.countDown();
            }
            return result;
        }
    }
}
