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
package io.sapl.node.rsocket.pdp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import io.sapl.pdp.BlockingPolicyDecisionPoint;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.test.util.TestSocketUtils;

import io.rsocket.ConnectionSetupPayload;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.core.RSocketConnector;
import io.rsocket.core.RSocketServer;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.util.DefaultPayload;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.proto.SaplProtobufCodec;
import io.sapl.node.rsocket.pdp.ProtobufRSocketAcceptor;
import io.sapl.node.rsocket.pdp.RSocketConnectionAuthenticator.AuthenticationResult;
import lombok.val;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("ProtobufRSocketAcceptor")
class ProtobufRSocketAcceptorTests {

    @Nested
    @DisplayName("connection authentication")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ConnectionAuthTests {

        private final int  port = TestSocketUtils.findAvailableTcpPort();
        private Disposable server;

        @BeforeAll
        void startServer() {
            val blockingPdp = mock(BlockingPolicyDecisionPoint.class);
            when(blockingPdp.decideOnce(any(), any())).thenReturn(AuthorizationDecision.PERMIT);
            val pdp = mock(ReactivePolicyDecisionPoint.class);

            val acceptor = new ProtobufRSocketAcceptor(blockingPdp, pdp, setup -> {
                val metadata = setup.metadata();
                if (metadata.readableBytes() == 0) {
                    return Mono.error(new RuntimeException("No credentials"));
                }
                val token = metadata.toString(StandardCharsets.UTF_8);
                if ("valid-token".equals(token)) {
                    return Mono.just(new AuthenticationResult("tenant-1", null));
                }
                return Mono.error(new RuntimeException("Invalid token"));
            });
            server = RSocketServer.create(acceptor).bindNow(TcpServerTransport.create(port));
        }

        @AfterAll
        void stopServer() {
            if (server != null) {
                server.dispose();
            }
        }

        @Test
        @DisplayName("accepts connection with valid credentials")
        void whenValidCredentialsThenConnects() throws IOException {
            val setupPayload = DefaultPayload.create(new byte[0], "valid-token".getBytes(StandardCharsets.UTF_8));
            val rsocket      = RSocketConnector.create().setupPayload(setupPayload)
                    .connect(TcpClientTransport.create(port)).block();

            val payload = createDecideOncePayload(AuthorizationSubscription.of("alice", "read", "doc"));
            val result  = rsocket.requestResponse(payload).map(ProtobufRSocketAcceptorTests::decodeDecision);

            StepVerifier.create(result).assertNext(d -> assertThat(d.decision()).isEqualTo(Decision.PERMIT))
                    .verifyComplete();

            rsocket.dispose();
        }

        @Test
        @DisplayName("rejects request with invalid credentials")
        void whenInvalidCredentialsThenRejects() throws IOException {
            val setupPayload = DefaultPayload.create(new byte[0], "bad-token".getBytes(StandardCharsets.UTF_8));
            val rsocket      = RSocketConnector.create().setupPayload(setupPayload)
                    .connect(TcpClientTransport.create(port)).block();

            val payload = createDecideOncePayload(AuthorizationSubscription.of("alice", "read", "doc"));

            StepVerifier.create(rsocket.requestResponse(payload)).expectError().verify();

            rsocket.dispose();
        }

        @Test
        @DisplayName("rejects request with no credentials")
        void whenNoCredentialsThenRejects() throws IOException {
            val rsocket = RSocketConnector.create().connect(TcpClientTransport.create(port)).block();
            val payload = createDecideOncePayload(AuthorizationSubscription.of("alice", "read", "doc"));

            StepVerifier.create(rsocket.requestResponse(payload)).expectError().verify();

            rsocket.dispose();
        }
    }

    @Test
    @DisplayName("setup authentication runs on a virtual thread, not the calling/event-loop thread")
    void whenAuthenticatingThenRunsOffTheTransportThread() {
        val blockingPdp = mock(BlockingPolicyDecisionPoint.class);
        val pdp         = mock(ReactivePolicyDecisionPoint.class);
        val authThread  = new AtomicReference<Thread>();
        val acceptor    = new ProtobufRSocketAcceptor(blockingPdp, pdp, setup -> Mono.fromCallable(() -> {
                            authThread.set(Thread.currentThread());
                            return new AuthenticationResult("tenant-1", null);
                        }));

        acceptor.accept(mock(ConnectionSetupPayload.class), mock(RSocket.class)).block();

        assertThat(authThread.get()).isNotNull();
        assertThat(authThread.get().isVirtual()).isTrue();
    }

    @Nested
    @DisplayName("unauthenticated mode")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class UnauthenticatedTests {

        private final int  port = TestSocketUtils.findAvailableTcpPort();
        private Disposable server;

        @BeforeAll
        void startServer() {
            val blockingPdp = mock(BlockingPolicyDecisionPoint.class);
            when(blockingPdp.decideOnce(any(), any())).thenReturn(AuthorizationDecision.DENY);
            val pdp = mock(ReactivePolicyDecisionPoint.class);
            when(pdp.decide(any(AuthorizationSubscription.class), any()))
                    .thenReturn(Flux.just(AuthorizationDecision.PERMIT, AuthorizationDecision.DENY));

            val acceptor = new ProtobufRSocketAcceptor(blockingPdp, pdp);
            server = RSocketServer.create(acceptor).bindNow(TcpServerTransport.create(port));
        }

        @AfterAll
        void stopServer() {
            if (server != null) {
                server.dispose();
            }
        }

        @Test
        @DisplayName("accepts connection without authenticator")
        void whenNoAuthenticatorThenAccepts() throws IOException {
            val rsocket = RSocketConnector.create().connect(TcpClientTransport.create(port)).block();
            val payload = createDecideOncePayload(AuthorizationSubscription.of("alice", "read", "doc"));
            val result  = rsocket.requestResponse(payload).map(ProtobufRSocketAcceptorTests::decodeDecision);

            StepVerifier.create(result).assertNext(d -> assertThat(d.decision()).isEqualTo(Decision.DENY))
                    .verifyComplete();

            rsocket.dispose();
        }

        @Test
        @DisplayName("handles streaming decide via request-stream")
        void whenDecideStreamThenReturnsFlux() throws IOException {
            val rsocket = RSocketConnector.create().connect(TcpClientTransport.create(port)).block();
            val payload = createDecidePayload(AuthorizationSubscription.of("alice", "read", "doc"));
            val result  = rsocket.requestStream(payload).map(ProtobufRSocketAcceptorTests::decodeDecision);

            StepVerifier.create(result).assertNext(d -> assertThat(d.decision()).isEqualTo(Decision.PERMIT))
                    .assertNext(d -> assertThat(d.decision()).isEqualTo(Decision.DENY)).verifyComplete();

            rsocket.dispose();
        }

        @Test
        @DisplayName("unknown route surfaces as RSocket error so the client knows the request was invalid, not policy-indeterminate")
        void whenUnknownRouteThenInvalidError() {
            val rsocket = RSocketConnector.create().connect(TcpClientTransport.create(port)).block();
            val payload = DefaultPayload.create(new byte[0], "unknown-route".getBytes(StandardCharsets.UTF_8));

            StepVerifier.create(rsocket.requestResponse(payload)).expectError().verify();

            rsocket.dispose();
        }

        @Test
        @DisplayName("malformed protobuf body for decide-once surfaces as RSocket error, not as INDETERMINATE")
        void whenMalformedDecideOnceThenInvalidError() {
            val rsocket = RSocketConnector.create().connect(TcpClientTransport.create(port)).block();
            val payload = DefaultPayload.create("not-protobuf".getBytes(StandardCharsets.UTF_8),
                    "decide-once".getBytes(StandardCharsets.UTF_8));

            StepVerifier.create(rsocket.requestResponse(payload)).expectError().verify();

            rsocket.dispose();
        }

        @Test
        @DisplayName("malformed protobuf body for decide stream surfaces as RSocket error, not a single INDETERMINATE next")
        void whenMalformedDecideStreamThenInvalidError() {
            val rsocket = RSocketConnector.create().connect(TcpClientTransport.create(port)).block();
            val payload = DefaultPayload.create("not-protobuf".getBytes(StandardCharsets.UTF_8),
                    "decide".getBytes(StandardCharsets.UTF_8));

            StepVerifier.create(rsocket.requestStream(payload)).expectError().verify();

            rsocket.dispose();
        }
    }

    private static AuthorizationDecision decodeDecision(Payload payload) {
        try {
            val buf   = payload.data();
            val bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return SaplProtobufCodec.readAuthorizationDecision(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Payload createDecideOncePayload(AuthorizationSubscription sub) throws IOException {
        val data = SaplProtobufCodec.writeAuthorizationSubscription(sub);
        return DefaultPayload.create(data, "decide-once".getBytes(StandardCharsets.UTF_8));
    }

    private static Payload createDecidePayload(AuthorizationSubscription sub) throws IOException {
        val data = SaplProtobufCodec.writeAuthorizationSubscription(sub);
        return DefaultPayload.create(data, "decide".getBytes(StandardCharsets.UTF_8));
    }

}
