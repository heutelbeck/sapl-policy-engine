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
package io.sapl.vaadin.lsp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

@DisplayName("LSP WebSocket endpoint resource bounding")
class LspWebSocketEndpointTests {

    private LspWebSocketEndpoint endpoint;

    @AfterEach
    void tearDown() {
        if (endpoint != null) {
            endpoint.destroy();
        }
    }

    @Nested
    @DisplayName("when the concurrent session cap is reached")
    class SessionCap {

        @Test
        @DisplayName("then a connection beyond the cap is closed with SERVICE_OVERLOAD")
        void whenBeyondCapThenConnectionClosedWithServiceOverload() throws Exception {
            final var properties = new LspWebSocketProperties();
            properties.setMaxConcurrentSessions(1);
            endpoint = new NonStartingEndpoint(properties);

            final var first  = new RecordingSession("first");
            final var second = new RecordingSession("second");

            endpoint.afterConnectionEstablished(first);
            endpoint.afterConnectionEstablished(second);

            assertThat(first.closeStatus.get()).isNull();
            assertThat(second.closeStatus.get()).isNotNull().satisfies(
                    status -> assertThat(status.getCode()).isEqualTo(CloseStatus.SERVICE_OVERLOAD.getCode()));
        }

        @Test
        @DisplayName("then a slot freed by a closed session admits a new connection")
        void whenSessionClosedThenSlotIsReused() throws Exception {
            final var properties = new LspWebSocketProperties();
            properties.setMaxConcurrentSessions(1);
            endpoint = new NonStartingEndpoint(properties);

            final var first = new RecordingSession("first");
            endpoint.afterConnectionEstablished(first);
            endpoint.afterConnectionClosed(first, CloseStatus.NORMAL);

            final var second = new RecordingSession("second");
            endpoint.afterConnectionEstablished(second);

            assertThat(second.closeStatus.get()).isNull();
        }
    }

    @Nested
    @DisplayName("when the embedded LSP server stops draining incoming messages")
    class StalledServer {

        @Test
        @DisplayName("then a backed-up producer is not blocked and the session is closed with SERVICE_OVERLOAD")
        void whenServerStalledThenProducerNotBlockedAndSessionClosed() throws Exception {
            final var properties = new LspWebSocketProperties();
            properties.setMessageQueueCapacity(1);
            properties.setOfferTimeoutMillis(150);
            endpoint = new NonStartingEndpoint(properties);

            final var session = new RecordingSession("stalled");
            endpoint.afterConnectionEstablished(session);

            // Nothing drains the queue (the server is not started), so once the
            // queue is saturated the next write must time out and close the session
            // rather than block the container thread forever.
            final var producer = new Thread(() -> {
                for (int i = 0; i < 50 && session.closeStatus.get() == null; i++) {
                    try {
                        endpoint.handleTextMessage(session, new TextMessage("{\"seq\":" + i + "}"));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
            });
            producer.start();
            producer.join(5_000);

            assertThat(producer.isAlive()).isFalse();
            assertThat(session.closeStatus.get()).isNotNull().satisfies(
                    status -> assertThat(status.getCode()).isEqualTo(CloseStatus.SERVICE_OVERLOAD.getCode()));
        }
    }

    @Nested
    @DisplayName("the queue-backed input stream")
    class QueueBackedStream {

        @Test
        @DisplayName("hands bytes from producer to a blocking reader and signals EOF on close")
        void whenBytesEnqueuedThenReaderReceivesThemAndEofOnClose() throws Exception {
            final var stream    = new LspWebSocketEndpoint.QueueBackedInputStream(16, 1000);
            final var received  = new byte[5];
            final var readCount = new AtomicReference<Integer>();
            final var done      = new CountDownLatch(1);

            final var reader = new Thread(() -> {
                try {
                    final int n = stream.read(received);
                    readCount.set(n);
                    done.countDown();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            reader.start();

            assertThat(stream.offer("hello".getBytes())).isTrue();
            assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(readCount.get()).isEqualTo(5);
            assertThat(new String(received)).isEqualTo("hello");

            stream.close();
            assertThat(stream.read(received)).isEqualTo(-1);
        }
    }

    private static class NonStartingEndpoint extends LspWebSocketEndpoint {

        NonStartingEndpoint(LspWebSocketProperties properties) {
            super(properties);
        }

        @Override
        void launchLanguageServer(WebSocketSession session, InputStream input, OutputStream output) {
            // Deliberately do not start a server, so the input queue is never drained.
        }
    }

    private static final class RecordingSession implements WebSocketSession {

        private final String                       id;
        private final Map<String, Object>          attributes  = new ConcurrentHashMap<>();
        private final AtomicReference<CloseStatus> closeStatus = new AtomicReference<>();

        RecordingSession(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return attributes;
        }

        @Override
        public boolean isOpen() {
            return closeStatus.get() == null;
        }

        @Override
        public void close(CloseStatus status) {
            closeStatus.compareAndSet(null, status);
        }

        @Override
        public void close() {
            close(CloseStatus.NORMAL);
        }

        @Override
        public void sendMessage(WebSocketMessage<?> message) {
            // not exercised
        }

        @Override
        public URI getUri() {
            return null;
        }

        @Override
        public HttpHeaders getHandshakeHeaders() {
            return new HttpHeaders();
        }

        @Override
        public Principal getPrincipal() {
            return null;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public String getAcceptedProtocol() {
            return null;
        }

        @Override
        public void setTextMessageSizeLimit(int messageSizeLimit) {
            // not exercised
        }

        @Override
        public int getTextMessageSizeLimit() {
            return 0;
        }

        @Override
        public void setBinaryMessageSizeLimit(int messageSizeLimit) {
            // not exercised
        }

        @Override
        public int getBinaryMessageSizeLimit() {
            return 0;
        }

        @Override
        public List<WebSocketExtension> getExtensions() {
            return List.of();
        }
    }
}
