/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jspecify.annotations.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import io.sapl.lsp.server.SAPLLanguageServer;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket endpoint that bridges the browser to an embedded SAPL Language
 * Server.
 * Each WebSocket connection gets its own LSP server instance.
 */
@Slf4j
public class LspWebSocketEndpoint extends TextWebSocketHandler {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("LSP WebSocket connection established: {}", session.getId());

        // Create pipes for LSP communication
        var clientToServer = new PipedOutputStream();
        var serverInput    = new PipedInputStream(clientToServer);
        var serverToClient = new WebSocketOutputStream(session);

        // Store streams in session for cleanup
        session.getAttributes().put("clientToServer", clientToServer);
        session.getAttributes().put("serverInput", serverInput);

        // Create and start LSP server in background
        executor.submit(() -> startLspServer(session, serverInput, serverToClient));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, @NonNull TextMessage message) throws Exception {
        var clientToServer = (PipedOutputStream) session.getAttributes().get("clientToServer");
        if (clientToServer == null) {
            log.warn("No LSP connection for session {}", session.getId());
            return;
        }

        // Convert JSON-RPC message to LSP wire format (Content-Length header + body)
        var payload    = message.getPayload();
        var lspMessage = formatLspMessage(payload);

        try {
            clientToServer.write(lspMessage);
            clientToServer.flush();
        } catch (IOException e) {
            log.error("Failed to send message to LSP server", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, @NonNull CloseStatus status) throws Exception {
        log.info("LSP WebSocket connection closed: {} ({})", session.getId(), status);

        // Close pipes to shut down LSP server
        try {
            var clientToServer = (PipedOutputStream) session.getAttributes().get("clientToServer");
            if (clientToServer != null) {
                clientToServer.close();
            }
        } catch (IOException e) {
            log.debug("Error closing client-to-server pipe", e);
        }

        try {
            var serverInput = (PipedInputStream) session.getAttributes().get("serverInput");
            if (serverInput != null) {
                serverInput.close();
            }
        } catch (IOException e) {
            log.debug("Error closing server input pipe", e);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, @NonNull Throwable exception) throws Exception {
        log.error("LSP WebSocket transport error for session {}", session.getId(), exception);
    }

    private void startLspServer(WebSocketSession session, InputStream input, OutputStream output) {
        try {
            log.info("Starting embedded LSP server for session {}", session.getId());

            var server   = new SAPLLanguageServer();
            var launcher = createLauncher(server, input, output);

            var client = launcher.getRemoteProxy();
            server.connect(client);

            // Block until the connection is closed
            launcher.startListening().get();

            log.info("LSP server stopped for session {}", session.getId());
        } catch (Exception e) {
            if (!session.isOpen()) {
                log.debug("LSP server stopped (session closed)");
            } else {
                log.error("LSP server error for session {}", session.getId(), e);
            }
        }
    }

    private Launcher<LanguageClient> createLauncher(SAPLLanguageServer server, InputStream input, OutputStream output) {
        return LSPLauncher.createServerLauncher(server, input, output);
    }

    private byte[] formatLspMessage(String jsonContent) {
        var contentBytes = jsonContent.getBytes(StandardCharsets.UTF_8);
        var header       = "Content-Length: " + contentBytes.length + "\r\n\r\n";
        var headerBytes  = header.getBytes(StandardCharsets.UTF_8);

        var result = new byte[headerBytes.length + contentBytes.length];
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);
        System.arraycopy(contentBytes, 0, result, headerBytes.length, contentBytes.length);
        return result;
    }

    /**
     * Output stream that writes LSP messages to a WebSocket session.
     * Parses the LSP wire format and sends JSON content as WebSocket text
     * messages.
     * Uses byte array operations to avoid UTF-8 encoding issues with split writes.
     */
    private static class WebSocketOutputStream extends OutputStream {

        private static final byte[] HEADER_SEPARATOR = "\r\n\r\n".getBytes(StandardCharsets.UTF_8);
        private static final int    SEPARATOR_LENGTH = 4;

        private final WebSocketSession              session;
        private final java.io.ByteArrayOutputStream buffer         = new java.io.ByteArrayOutputStream();
        private int                                 expectedLength = -1;
        private int                                 headerEndIndex = -1;

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

        private void processBuffer() throws IOException {
            while (buffer.size() > 0) {
                var data = buffer.toByteArray();

                // Look for header separator
                if (expectedLength < 0) {
                    headerEndIndex = indexOf(data, HEADER_SEPARATOR);
                    if (headerEndIndex < 0) {
                        return; // Need more data
                    }

                    var header = new String(data, 0, headerEndIndex, StandardCharsets.UTF_8);
                    expectedLength = parseContentLength(header);

                    if (expectedLength < 0) {
                        // Invalid header - discard up to separator and try again
                        log.debug("Discarding invalid LSP header data");
                        buffer.reset();
                        buffer.write(data, headerEndIndex + SEPARATOR_LENGTH,
                                data.length - headerEndIndex - SEPARATOR_LENGTH);
                        headerEndIndex = -1;
                        continue;
                    }
                }

                // Check if we have the full body
                var bodyStart = headerEndIndex + SEPARATOR_LENGTH;
                if (data.length < bodyStart + expectedLength) {
                    return; // Need more data
                }

                // Extract and send the JSON content
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

                // Remove processed message from buffer
                var remaining = data.length - bodyStart - expectedLength;
                buffer.reset();
                if (remaining > 0) {
                    buffer.write(data, bodyStart + expectedLength, remaining);
                }
                expectedLength = -1;
                headerEndIndex = -1;
            }
        }

        private static int indexOf(byte[] data, byte[] pattern) {
            outer: for (int i = 0; i <= data.length - pattern.length; i++) {
                for (int j = 0; j < pattern.length; j++) {
                    if (data[i + j] != pattern[j]) {
                        continue outer;
                    }
                }
                return i;
            }
            return -1;
        }

        private static int parseContentLength(String header) {
            for (var line : header.split("\r\n")) {
                var trimmed = line.trim();
                if (trimmed.toLowerCase().startsWith("content-length:")) {
                    try {
                        return Integer.parseInt(trimmed.substring(15).trim());
                    } catch (NumberFormatException e) {
                        return -1;
                    }
                }
            }
            return -1;
        }
    }
}
