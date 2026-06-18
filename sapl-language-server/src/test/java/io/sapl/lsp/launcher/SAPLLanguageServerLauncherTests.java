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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import lombok.val;

@DisplayName("SAPL Language Server launcher socket lifecycle")
class SAPLLanguageServerLauncherTests {

    private ExecutorService executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("closes the accepted socket once the connection handler finishes")
    void whenConnectionHandlerFinishesThenAcceptedSocketIsClosed() throws Exception {
        executor = Executors.newCachedThreadPool();
        try (val serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
                val client = new Socket(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort())) {
            val accepted = serverSocket.accept();
            val handler  = SAPLLanguageServerLauncher.handleConnection(executor, accepted);

            client.close();
            awaitDone(handler);

            assertThat(handler).satisfies(f -> assertThat(f.isDone()).isTrue());
            assertThat(accepted.isClosed()).isTrue();
        }
    }

    @Test
    @DisplayName("reuses one shared executor across multiple connections instead of leaking one per connection")
    void whenHandlingMultipleConnectionsThenSharedExecutorIsReused() throws Exception {
        executor = Executors.newCachedThreadPool();
        try (val serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            val first  = handleClientCycle(serverSocket);
            val second = handleClientCycle(serverSocket);

            assertThat(executor.isShutdown()).isFalse();
            assertThat(first).isNotNull();
            assertThat(second).isNotNull();
        }
    }

    private Socket handleClientCycle(ServerSocket serverSocket) throws Exception {
        try (val client = new Socket(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort())) {
            val accepted = serverSocket.accept();
            val handler  = SAPLLanguageServerLauncher.handleConnection(executor, accepted);
            client.close();
            awaitDone(handler);
            assertThat(accepted.isClosed()).isTrue();
            return accepted;
        }
    }

    private void awaitDone(Future<?> handler) throws InterruptedException {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!handler.isDone() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }
}
