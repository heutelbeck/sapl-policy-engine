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
package io.sapl.pdp.remote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.netty.channel.EventLoop;
import lombok.val;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.DisposableServer;
import reactor.netty.resources.LoopResources;
import reactor.netty.tcp.TcpClient;
import reactor.netty.tcp.TcpServer;
import reactor.test.StepVerifier;

@Timeout(30)
@DisplayName("Shared RSocket client event loops")
class RSocketClientResourcesTests {

    private final List<Connection> connections = new ArrayList<>();
    private DisposableServer       server;

    @BeforeEach
    void startServer() {
        server = TcpServer.create().host("127.0.0.1").port(0)
                .handle((inbound, outbound) -> outbound.send(inbound.receive().retain())).bindNow();
    }

    @AfterEach
    void stopConnectionsAndServer() {
        connections.forEach(Connection::dispose);
        if (server != null) {
            server.disposeNow();
        }
    }

    @Test
    @DisplayName("Sequential connections use at least the requested I/O parallelism")
    void whenConnectingSequentiallyThenUsesRequestedIoParallelism() {
        val activeLoops = ConcurrentHashMap.<Thread>newKeySet();
        try (val resources = RSocketClientResources.acquire()) {
            val client = resources.configure(TcpClient.create()).host("127.0.0.1").port(server.port())
                    .doOnConnected(connection -> activeLoops.add(Thread.currentThread()));
            for (int i = 0; i < 4 * LoopResources.DEFAULT_IO_WORKER_COUNT; i++) {
                connections.add(client.connectNow(Duration.ofSeconds(5)));
            }

            assertThat(activeLoops).hasSizeGreaterThanOrEqualTo(LoopResources.DEFAULT_IO_WORKER_COUNT);
        }
    }

    @Test
    @DisplayName("Closing one handle twice leaves another client's shared loops usable")
    void whenOneHandleClosesThenOtherClientRemainsUsable() {
        try (val first = RSocketClientResources.acquire(); val second = RSocketClientResources.acquire()) {
            val firstConnection  = connect(first);
            val secondConnection = connect(second);
            assertThat(firstConnection.channel().eventLoop().parent())
                    .isSameAs(secondConnection.channel().eventLoop().parent());

            first.close();
            first.close();
            val response = secondConnection.outbound().sendString(Mono.just("permit")).then()
                    .then(secondConnection.inbound().receive().asString().next());

            StepVerifier.create(response).expectNext("permit").expectComplete().verify(Duration.ofSeconds(5));
        }
    }

    @Test
    @DisplayName("Closing the last client terminates its event loops")
    void whenLastClientClosesThenTerminatesEventLoops() {
        val             resources = RSocketClientResources.acquire();
        final EventLoop eventLoop;
        try (resources) {
            eventLoop = connect(resources).channel().eventLoop();
        }

        await().atMost(Duration.ofSeconds(10)).until(eventLoop::isTerminated);
        val client = TcpClient.create();
        assertThatIllegalStateException().isThrownBy(() -> resources.configure(client))
                .withMessage("RSocket client resources are closed.");
    }

    private Connection connect(RSocketClientResources resources) {
        val connection = resources.configure(TcpClient.create()).host("127.0.0.1").port(server.port())
                .connectNow(Duration.ofSeconds(5));
        connections.add(connection);
        return connection;
    }
}
