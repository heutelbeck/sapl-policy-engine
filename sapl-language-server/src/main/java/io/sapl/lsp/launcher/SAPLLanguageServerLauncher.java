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
package io.sapl.lsp.launcher;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

import io.sapl.lsp.server.SAPLLanguageServer;
import lombok.extern.slf4j.Slf4j;

/**
 * Launcher for the SAPL Language Server.
 * Supports both stdio and socket-based communication.
 */
@Slf4j
public class SAPLLanguageServerLauncher {

    private static final int DEFAULT_PORT = 5007;

    /**
     * Main entry point for the SAPL Language Server.
     *
     * @param args command line arguments. Use --socket to start in socket mode,
     * or --port=XXXX to specify a custom port.
     */
    public static void main(String[] args) throws Exception {
        var useSocket = false;
        var port      = DEFAULT_PORT;

        for (var arg : args) {
            if ("--socket".equals(arg)) {
                useSocket = true;
            } else if (arg.startsWith("--port=")) {
                port = Integer.parseInt(arg.substring("--port=".length()));
            }
        }

        if (useSocket) {
            startSocketServer(port);
        } else {
            startStdioServer();
        }
    }

    /**
     * Starts the language server using standard input/output streams.
     * This is the default mode for most LSP clients.
     */
    public static void startStdioServer() throws ExecutionException, InterruptedException {
        log.info("Starting SAPL Language Server in stdio mode");
        startServer(System.in, System.out);
    }

    /**
     * Starts the language server listening on a socket bound to localhost only.
     * Useful for debugging and testing. Only accepts connections from the local
     * machine.
     *
     * @param port the port to listen on
     */
    public static void startSocketServer(int port) throws Exception {
        log.info("Starting SAPL Language Server on port {}", port);
        try (var serverSocket = new ServerSocket(port, 50, InetAddress.getLoopbackAddress())) {
            log.info("Waiting for client connection on port {}...", port);
            while (true) {
                var socket = serverSocket.accept();
                log.info("Client connected from {}", socket.getRemoteSocketAddress());

                var executor = Executors.newSingleThreadExecutor();
                executor.submit(() -> {
                    try {
                        startServer(socket.getInputStream(), socket.getOutputStream());
                    } catch (InterruptedException e) {
                        log.debug("Language server interrupted");
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        log.error("Error in language server", e);
                    }
                });
            }
        }
    }

    /**
     * Creates and starts the language server with the given streams.
     *
     * @param input input stream for receiving messages
     * @param output output stream for sending messages
     */
    public static void startServer(InputStream input, OutputStream output)
            throws ExecutionException, InterruptedException {
        var server   = new SAPLLanguageServer();
        var launcher = createLauncher(server, input, output);

        var client = launcher.getRemoteProxy();
        server.connect(client);

        launcher.startListening().get();
    }

    /**
     * Creates an LSP launcher for the given server.
     *
     * @param server the language server instance
     * @param input input stream
     * @param output output stream
     * @return the configured launcher
     */
    public static Launcher<LanguageClient> createLauncher(SAPLLanguageServer server, InputStream input,
            OutputStream output) {
        return LSPLauncher.createServerLauncher(server, input, output);
    }

    /**
     * Creates a language server instance for embedded use.
     * The server supports both SAPL and SAPLTest files.
     * The caller is responsible for connecting the client.
     *
     * @return a new SAPLLanguageServer instance
     */
    public static SAPLLanguageServer createEmbeddedServer() {
        return new SAPLLanguageServer();
    }

}
