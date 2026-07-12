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

import io.sapl.lsp.server.SAPLLanguageServer;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket endpoint that bridges the browser to an embedded SAPL Language
 * Server.
 * Each WebSocket connection gets its own LSP server instance.
 *
 * <p>
 * Resource use is bounded. The number of concurrent sessions is capped, the
 * worker pool is sized to that cap, and incoming messages are buffered in a
 * bounded per-session queue drained by the LSP thread. A stalled or slow LSP
 * server therefore cannot block the container thread that delivers messages and
 * cannot allocate unbounded threads or memory.
 */
@Slf4j
public class LspWebSocketEndpoint extends TextWebSocketHandler implements DisposableBean {

    private static final String LSP_CONTENT_LENGTH_HEADER = "content-length:";
    private static final String LSP_HEADER_SEPARATOR      = "\r\n\r\n";
    private static final String LSP_LINE_SEPARATOR        = "\r\n";

    private static final String SESSION_KEY_SERVER_INPUT = "serverInput";

    private final LspWebSocketProperties properties;
    private final ExecutorService        executor;
    private final AtomicInteger          activeSessions = new AtomicInteger();

    public LspWebSocketEndpoint(LspWebSocketProperties properties) {
        this.properties = properties;
        this.executor   = Executors.newFixedThreadPool(Math.max(1, properties.getMaxConcurrentSessions()));
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (activeSessions.incrementAndGet() > properties.getMaxConcurrentSessions()) {
            activeSessions.decrementAndGet();
            log.warn("Rejecting LSP WebSocket connection {}: concurrent session cap of {} reached", session.getId(),
                    properties.getMaxConcurrentSessions());
            session.close(CloseStatus.SERVICE_OVERLOAD);
            return;
        }

        log.info("LSP WebSocket connection established: {}", session.getId());

        QueueBackedInputStream serverInput = null;
        try {
            serverInput = new QueueBackedInputStream(properties.getMessageQueueCapacity(),
                    properties.getOfferTimeoutMillis());
            var serverToClient = new WebSocketOutputStream(session);

            session.getAttributes().put(SESSION_KEY_SERVER_INPUT, serverInput);

            var finalServerInput = serverInput;
            executor.submit(() -> launchLanguageServer(session, finalServerInput, serverToClient));
        } catch (Exception e) {
            // Remove the stored input so afterConnectionClosed (which Spring still
            // calls) does not decrement the session counter a second time.
            closeQuietly(serverInput);
            session.getAttributes().remove(SESSION_KEY_SERVER_INPUT);
            activeSessions.decrementAndGet();
            throw e;
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, @NonNull TextMessage message) throws Exception {
        var serverInput = (QueueBackedInputStream) session.getAttributes().get(SESSION_KEY_SERVER_INPUT);
        if (serverInput == null) {
            log.warn("No LSP connection for session {}", session.getId());
            return;
        }

        // Convert JSON-RPC message to LSP wire format (Content-Length header + body)
        var payload    = message.getPayload();
        var lspMessage = formatLspMessage(payload);

        try {
            if (!serverInput.offer(lspMessage)) {
                // The LSP server is not draining its input. Dropping a message would
                // desynchronise the document model, so the session is closed instead.
                log.warn("LSP server input queue saturated for session {}, closing", session.getId());
                session.close(CloseStatus.SERVICE_OVERLOAD);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("Interrupted while delivering message to LSP server for session {}", session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, @NonNull CloseStatus status) throws Exception {
        log.info("LSP WebSocket connection closed: {} ({})", session.getId(), status);

        var serverInput = (QueueBackedInputStream) session.getAttributes().get(SESSION_KEY_SERVER_INPUT);
        if (serverInput != null) {
            serverInput.close();
            activeSessions.decrementAndGet();
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, @NonNull Throwable exception) throws Exception {
        if (exception instanceof IOException && exception.getCause() instanceof ClosedChannelException) {
            log.debug("LSP WebSocket closed for session {} (channel closed)", session.getId());
        } else {
            log.error("LSP WebSocket transport error for session {}", session.getId(), exception);
        }
    }

    void launchLanguageServer(WebSocketSession session, InputStream input, OutputStream output) {
        try {
            log.info("Starting embedded LSP server for session {}", session.getId());

            var server   = new SAPLLanguageServer();
            var launcher = createLauncher(server, input, output);

            var client = launcher.getRemoteProxy();
            server.connect(client);

            // Block until the connection is closed
            launcher.startListening().get();

            log.info("LSP server stopped for session {}", session.getId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("LSP server interrupted for session {}", session.getId());
        } catch (Exception e) {
            log.error("LSP server error for session {}", session.getId(), e);
        }
    }

    private Launcher<LanguageClient> createLauncher(SAPLLanguageServer server, InputStream input, OutputStream output) {
        return LSPLauncher.createServerLauncher(server, input, output);
    }

    private byte[] formatLspMessage(String jsonContent) {
        var contentBytes = jsonContent.getBytes(StandardCharsets.UTF_8);
        var header       = "Content-Length: " + contentBytes.length + LSP_HEADER_SEPARATOR;
        var headerBytes  = header.getBytes(StandardCharsets.UTF_8);

        var result = new byte[headerBytes.length + contentBytes.length];
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);
        System.arraycopy(contentBytes, 0, result, headerBytes.length, contentBytes.length);
        return result;
    }

    /**
     * Input stream backed by a bounded blocking queue of message chunks. The
     * container thread offers chunks with a timeout (never blocking
     * indefinitely), while the LSP reader thread blocks on take when idle. A
     * closed stream drains to end-of-stream so the reader terminates.
     */
    static final class QueueBackedInputStream extends InputStream {

        private static final byte[] END_OF_STREAM = new byte[0];

        private final BlockingQueue<byte[]> queue;
        private final long                  offerTimeoutMillis;

        private byte[]  current      = END_OF_STREAM;
        private int     currentIndex = 0;
        private boolean closed       = false;

        QueueBackedInputStream(int capacity, long offerTimeoutMillis) {
            this.queue              = new ArrayBlockingQueue<>(Math.max(1, capacity));
            this.offerTimeoutMillis = offerTimeoutMillis;
        }

        boolean offer(byte[] chunk) throws InterruptedException {
            return queue.offer(chunk, offerTimeoutMillis, TimeUnit.MILLISECONDS);
        }

        @Override
        public int read() throws IOException {
            if (!ensureCurrent()) {
                return -1;
            }
            return current[currentIndex++] & 0xFF;
        }

        @Override
        public int read(byte @NonNull [] destination, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            if (!ensureCurrent()) {
                return -1;
            }
            var available = Math.min(length, current.length - currentIndex);
            System.arraycopy(current, currentIndex, destination, offset, available);
            currentIndex += available;
            return available;
        }

        private boolean ensureCurrent() throws IOException {
            while (currentIndex >= current.length) {
                if (closed) {
                    return false;
                }
                try {
                    var next = queue.take();
                    if (next == END_OF_STREAM) {
                        closed = true;
                        return false;
                    }
                    current      = next;
                    currentIndex = 0;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while reading LSP input", e);
                }
            }
            return true;
        }

        @Override
        public void close() {
            // Signal end-of-stream to the reader. Clear to make room, then offer the
            // sentinel. If a concurrent producer refilled the queue, drop a chunk and
            // retry so the end-of-stream sentinel is never lost.
            queue.clear();
            while (!queue.offer(END_OF_STREAM)) {
                queue.poll();
            }
        }
    }

    /**
     * Output stream that writes LSP messages to a WebSocket session.
     * Parses the LSP wire format and sends JSON content as WebSocket text
     * messages.
     * Uses byte array operations to avoid UTF-8 encoding issues with split writes.
     */
    private static class WebSocketOutputStream extends OutputStream {

        private static final byte[] HEADER_SEPARATOR = LSP_HEADER_SEPARATOR.getBytes(StandardCharsets.UTF_8);
        private static final int    SEPARATOR_LENGTH = 4;

        private final WebSocketSession      session;
        private final ByteArrayOutputStream buffer         = new ByteArrayOutputStream();
        private int                         expectedLength = -1;
        private int                         headerEndIndex = -1;

        WebSocketOutputStream(WebSocketSession session) {
            this.session = session;
        }

        @Override
        public synchronized void write(int b) throws IOException {
            buffer.write(b);
            processBuffer();
        }

        @Override
        public synchronized void write(byte @NonNull [] bytes, int off, int len) throws IOException {
            buffer.write(bytes, off, len);
            processBuffer();
        }

        @Override
        public synchronized void flush() throws IOException {
            processBuffer();
        }

        private void processBuffer() {
            while (buffer.size() > 0) {
                var data = buffer.toByteArray();

                if (!parseHeaderIfNeeded(data)) {
                    return;
                }

                var bodyStart = headerEndIndex + SEPARATOR_LENGTH;
                if (data.length < bodyStart + expectedLength) {
                    return; // Need more data
                }

                sendMessage(data, bodyStart);
                removeProcessedMessage(data, bodyStart);
            }
        }

        private boolean parseHeaderIfNeeded(byte[] data) {
            if (expectedLength >= 0) {
                return true;
            }

            headerEndIndex = indexOf(data, HEADER_SEPARATOR);
            if (headerEndIndex < 0) {
                return false;
            }

            var header = new String(data, 0, headerEndIndex, StandardCharsets.UTF_8);
            expectedLength = parseContentLength(header);

            if (expectedLength < 0) {
                discardInvalidHeader(data);
                return false;
            }
            return true;
        }

        private void discardInvalidHeader(byte[] data) {
            log.debug("Discarding invalid LSP header data");
            buffer.reset();
            buffer.write(data, headerEndIndex + SEPARATOR_LENGTH, data.length - headerEndIndex - SEPARATOR_LENGTH);
            headerEndIndex = -1;
        }

        private void sendMessage(byte[] data, int bodyStart) {
            var jsonContent = new String(data, bodyStart, expectedLength, StandardCharsets.UTF_8);
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(jsonContent));
                } catch (IOException e) {
                    log.error("Failed to send LSP response to WebSocket", e);
                }
            } else {
                log.debug("Session closed, dropping LSP response");
            }
        }

        private void removeProcessedMessage(byte[] data, int bodyStart) {
            var remaining = data.length - bodyStart - expectedLength;
            buffer.reset();
            if (remaining > 0) {
                buffer.write(data, bodyStart + expectedLength, remaining);
            }
            expectedLength = -1;
            headerEndIndex = -1;
        }

        private static int indexOf(byte[] data, byte[] pattern) {
            for (int i = 0; i <= data.length - pattern.length; i++) {
                if (matchesAt(data, pattern, i)) {
                    return i;
                }
            }
            return -1;
        }

        private static boolean matchesAt(byte[] data, byte[] pattern, int offset) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[offset + j] != pattern[j]) {
                    return false;
                }
            }
            return true;
        }

        private static int parseContentLength(String header) {
            for (var line : header.split(LSP_LINE_SEPARATOR)) {
                var trimmed = line.trim();
                if (trimmed.toLowerCase().startsWith(LSP_CONTENT_LENGTH_HEADER)) {
                    try {
                        return Integer.parseInt(trimmed.substring(LSP_CONTENT_LENGTH_HEADER.length()).trim());
                    } catch (NumberFormatException e) {
                        return -1;
                    }
                }
            }
            return -1;
        }
    }
}
