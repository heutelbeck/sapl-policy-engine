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
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

import io.sapl.lsp.server.SAPLLanguageServer;
import io.sapl.api.documentation.DocumentationBundle;
import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Launcher for the SAPL Language Server.
 * Supports both stdio and socket-based communication.
 */
@Slf4j
public class SAPLLanguageServerLauncher {

    private static final String ERROR_CATALOG      = "The catalog must be a JSON object with a libraries array.";
    private static final String ERROR_CATALOG_PATH = "The --catalog option requires a file path.";
    private static final int    DEFAULT_PORT       = 5007;

    /**
     * Main entry point for the SAPL Language Server.
     *
     * @param args command line arguments. Use --socket to start in socket mode,
     * or --port=XXXX to specify a custom port. --catalog file selects replacement
     * library and finder documentation without loading plugin implementations.
     */
    public static void main(String[] args) throws Exception {
        var                 useSocket     = false;
        var                 port          = DEFAULT_PORT;
        DocumentationBundle documentation = null;

        for (var index = 0; index < args.length; index++) {
            val arg = args[index];
            if ("--socket".equals(arg)) {
                useSocket = true;
            } else if (arg.startsWith("--port=")) {
                port = Integer.parseInt(arg.substring("--port=".length()));
            } else if ("--catalog".equals(arg)) {
                if (++index == args.length) {
                    throw new IllegalArgumentException(ERROR_CATALOG_PATH);
                }
                val mapper = JsonMapper.builder().build();
                val source = Files.readString(Path.of(args[index]));
                val tree   = mapper.readTree(source);
                if (!tree.isObject() || !tree.path("libraries").isArray()) {
                    throw new IllegalArgumentException(ERROR_CATALOG);
                }
                documentation = mapper.treeToValue(tree, DocumentationBundle.class);
            }
        }

        if (useSocket) {
            startSocketServer(port, documentation);
        } else {
            startServer(System.in, System.out, documentation);
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
        startSocketServer(port, null);
    }

    private static void startSocketServer(int port, DocumentationBundle documentation) throws Exception {
        log.info("Starting SAPL Language Server on port {}", port);
        val executor = Executors.newCachedThreadPool();
        try (var serverSocket = new ServerSocket(port, 50, InetAddress.getLoopbackAddress())) {
            log.info("Waiting for client connection on port {}...", port);
            while (true) {
                val socket = serverSocket.accept();
                log.info("Client connected from {}", socket.getRemoteSocketAddress());
                handleConnection(executor, socket, documentation);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Submits handling of a single accepted connection to the shared executor.
     * The accepted socket is always closed once the language server loop returns
     * or throws, so neither the socket descriptor nor the worker thread leaks.
     *
     * @param executor the shared executor servicing all connections
     * @param socket the accepted client socket
     * @return the future tracking completion of the connection handler
     */
    static Future<Void> handleConnection(ExecutorService executor, Socket socket) {
        return handleConnection(executor, socket, null);
    }

    private static Future<Void> handleConnection(ExecutorService executor, Socket socket,
            DocumentationBundle documentation) {
        return executor.submit(() -> {
            try (socket) {
                startServer(socket.getInputStream(), socket.getOutputStream(), documentation);
            } catch (InterruptedException e) {
                log.debug("Language server interrupted");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Error in language server", e);
            }
            return null;
        });
    }

    /**
     * Creates and starts the language server with the given streams.
     *
     * @param input input stream for receiving messages
     * @param output output stream for sending messages
     */
    public static void startServer(InputStream input, OutputStream output)
            throws ExecutionException, InterruptedException {
        startServer(input, output, null);
    }

    /**
     * Starts a server with host-selected documentation and local standard schema functions.
     *
     * @param input the client messages
     * @param output the server replies
     * @param documentation the host catalog, or null for the local standard catalog
     * @throws ExecutionException if the protocol listener fails
     * @throws InterruptedException if the listener is interrupted
     */
    public static void startServer(InputStream input, OutputStream output, DocumentationBundle documentation)
            throws ExecutionException, InterruptedException {
        var server   = createEmbeddedServer(documentation);
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

    /**
     * Creates an embedded server with a replacement documentation bundle.
     * No plugin implementation is loaded from this metadata.
     *
     * @param documentation the host catalog, or null for the local standard catalog;
     * an empty bundle offers no library or finder names
     * @return a new server with an independent catalog selection
     */
    public static SAPLLanguageServer createEmbeddedServer(DocumentationBundle documentation) {
        return new SAPLLanguageServer(documentation);
    }

}
