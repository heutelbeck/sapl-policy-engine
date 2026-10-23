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

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.support.GenericApplicationContext;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.core.RSocketServer;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.util.DefaultPayload;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.proto.SaplProtobufCodec;
import lombok.val;
import reactor.core.publisher.Mono;
import reactor.netty.tcp.TcpServer;
import reactor.test.StepVerifier;

@Timeout(30)
@DisplayName("RSocket remote PDP transport resources")
class ProtobufRemoteTransportTests {

    private final List<ProtobufRemoteReactivePolicyDecisionPoint> clients = new ArrayList<>();
    private CloseableChannel                                      server;

    @AfterEach
    void disposeClientsAndServer() {
        clients.forEach(ProtobufRemoteReactivePolicyDecisionPoint::dispose);
        if (server != null) {
            server.dispose();
            server.onClose().block(Duration.ofSeconds(5));
        }
    }

    @BeforeEach
    void startServer() throws IOException {
        val response = SaplProtobufCodec.writeAuthorizationDecision(AuthorizationDecision.PERMIT);
        server = RSocketServer.create((setup, sending) -> Mono.just(new RSocket() {
            @Override
            public Mono<Payload> requestResponse(Payload payload) {
                payload.release();
                return Mono.just(DefaultPayload.create(response));
            }
        })).bind(TcpServerTransport.create(TcpServer.create().host("127.0.0.1").port(0))).block(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("Disposing one remote PDP leaves another PDP able to obtain decisions")
    void whenOnePdpDisposesThenOtherPdpStillObtainsDecisions() {
        val first        = createClient();
        val second       = createClient();
        val subscription = AuthorizationSubscription.of("subject", "action", "resource");

        StepVerifier.create(first.decideOnce(subscription)).expectNext(AuthorizationDecision.PERMIT).expectComplete()
                .verify(Duration.ofSeconds(5));
        StepVerifier.create(second.decideOnce(subscription)).expectNext(AuthorizationDecision.PERMIT).expectComplete()
                .verify(Duration.ofSeconds(5));
        first.dispose();
        StepVerifier.create(second.decideOnce(subscription)).expectNext(AuthorizationDecision.PERMIT).expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("Closing a Spring context closes its remote PDP connection")
    void whenSpringContextClosesThenRemotePdpFailsClosed() {
        val client       = createClient();
        val subscription = AuthorizationSubscription.of("subject", "action", "resource");
        try (val context = new GenericApplicationContext()) {
            context.registerBean("remotePdp", ProtobufRemoteReactivePolicyDecisionPoint.class, () -> client);
            context.refresh();
            StepVerifier.create(client.decideOnce(subscription)).expectNext(AuthorizationDecision.PERMIT)
                    .expectComplete().verify(Duration.ofSeconds(5));
        }

        StepVerifier.create(client.decideOnce(subscription)).expectNext(AuthorizationDecision.INDETERMINATE)
                .expectComplete().verify(Duration.ofSeconds(5));
    }

    private ProtobufRemoteReactivePolicyDecisionPoint createClient() {
        val client = ProtobufRemoteReactivePolicyDecisionPoint.builder().host("127.0.0.1")
                .port(server.address().getPort()).build();
        clients.add(client);
        return client;
    }
}
