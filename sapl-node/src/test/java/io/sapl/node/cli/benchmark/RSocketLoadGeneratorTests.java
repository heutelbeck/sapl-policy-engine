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
package io.sapl.node.cli.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.core.RSocketServer;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.util.DefaultPayload;
import lombok.val;
import reactor.core.publisher.Mono;
import reactor.netty.tcp.TcpServer;

@DisplayName("RSocketLoadGenerator")
class RSocketLoadGeneratorTests {

    private CloseableChannel server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.dispose();
            server.onClose().block(Duration.ofSeconds(5));
        }
    }

    @Test
    @Timeout(30)
    @DisplayName("returns null with a clean error and no stack trace when the server is unreachable")
    void whenServerUnreachableThenReturnsNullWithCleanError() throws Exception {
        val port = freePort();
        val sw   = new StringWriter();
        val out  = new PrintWriter(sw);

        val outcome = RSocketLoadGenerator.run("127.0.0.1", port, null, new byte[] { 1 }, 1, 1, 0, 1, 0, out);
        out.flush();

        assertThat(outcome.result()).isNull();
        assertThat(outcome.failures()).isZero();
        assertThat(sw.toString()).contains("could not connect").doesNotContain("Exception");
    }

    @Test
    @Timeout(30)
    @DisplayName("completes a load test over multiple live RSocket connections")
    void whenServerRespondsThenCompletesLoadTestWithoutFailures() {
        server = RSocketServer.create((setup, sending) -> Mono.just(new RSocket() {
            @Override
            public Mono<Payload> requestResponse(Payload payload) {
                payload.release();
                return Mono.just(DefaultPayload.create("permit"));
            }
        })).bind(TcpServerTransport.create(TcpServer.create().host("127.0.0.1").port(0))).block(Duration.ofSeconds(5));
        assertThat(server).isNotNull();

        val output  = new StringWriter();
        val outcome = RSocketLoadGenerator.run("127.0.0.1", server.address().getPort(), null, new byte[] { 1 }, 8, 2, 0,
                1, 0, new PrintWriter(output));

        assertThat(outcome.result()).isNotNull().satisfies(result -> assertThat(result.mean()).isPositive());
        assertThat(outcome.failures()).isZero();
    }

    private static int freePort() throws Exception {
        try (val socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }
}
