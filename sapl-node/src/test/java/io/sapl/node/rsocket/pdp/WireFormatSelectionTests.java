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

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.TestSocketUtils;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.core.RSocketConnector;
import io.rsocket.core.RSocketServer;
import io.rsocket.exceptions.InvalidException;
import io.rsocket.exceptions.UnsupportedSetupException;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.util.DefaultPayload;
import io.sapl.api.binary.SaplBinaryCodec;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.proto.SaplProtobufCodec;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import lombok.val;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * The data MIME type of the SETUP frame selects the encoding of all payloads of
 * a connection: protobuf under its two names, the SAPL binary format under its
 * own, and nothing else.
 */
@DisplayName("RSocket data MIME type")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WireFormatSelectionTests {

    private static final Duration PATIENCE = Duration.ofSeconds(10);

    private static final AuthorizationSubscription SUBSCRIPTION = AuthorizationSubscription.of("alice", "read", "doc");

    private final int  port = TestSocketUtils.findAvailableTcpPort();
    private Disposable server;

    @BeforeAll
    void startServer() {
        val blockingPdp = mock(BlockingPolicyDecisionPoint.class);
        when(blockingPdp.decideOnce(any(), any())).thenReturn(AuthorizationDecision.DENY);
        val pdp = mock(ReactivePolicyDecisionPoint.class);
        when(pdp.decide(any(AuthorizationSubscription.class), any()))
                .thenReturn(Flux.just(AuthorizationDecision.PERMIT, AuthorizationDecision.DENY));
        server = RSocketServer.create(new ProtobufRSocketAcceptor(blockingPdp, pdp))
                .interceptors(registry -> registry.forConnection(new UnsupportedSetupInterceptor()))
                .bindNow(TcpServerTransport.create(port));
    }

    @AfterAll
    void stopServer() {
        server.dispose();
    }

    /** How a test writes a subscription and reads a decision. */
    interface Codec {
        byte[] write(AuthorizationSubscription subscription) throws IOException;

        AuthorizationDecision read(byte[] decision) throws IOException;
    }

    private static final Codec PROTOBUF = new Codec() {
        @Override
        public byte[] write(AuthorizationSubscription subscription) throws IOException {
            return SaplProtobufCodec.writeAuthorizationSubscription(subscription);
        }

        @Override
        public AuthorizationDecision read(byte[] decision) throws IOException {
            return SaplProtobufCodec.readAuthorizationDecision(decision);
        }
    };

    private static final Codec BINARY = new Codec() {
        @Override
        public byte[] write(AuthorizationSubscription subscription) throws IOException {
            return SaplBinaryCodec.writeAuthorizationSubscription(subscription);
        }

        @Override
        public AuthorizationDecision read(byte[] decision) throws IOException {
            return SaplBinaryCodec.readAuthorizationDecision(decision);
        }
    };

    static Stream<Arguments> spokenMimeTypes() {
        return Stream.of(arguments("application/binary", PROTOBUF), arguments("application/x-protobuf", PROTOBUF),
                arguments("application/vnd.sapl.binary", BINARY));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("spokenMimeTypes")
    @DisplayName("a connection in a format the node speaks is answered in that format on one-shot and stream routes")
    void whenSetupNamesASpokenFormatThenRequestsAreReadAndAnsweredInIt(String mimeType, Codec codec)
            throws IOException {
        val rsocket = connect(mimeType);
        val once    = rsocket.requestResponse(request("decide-once", codec.write(SUBSCRIPTION)))
                .map(answer -> read(codec, answer));
        val stream  = rsocket.requestStream(request("decide", codec.write(SUBSCRIPTION)))
                .map(answer -> read(codec, answer));
        StepVerifier.create(once.concatWith(stream))
                .expectNext(AuthorizationDecision.DENY, AuthorizationDecision.PERMIT, AuthorizationDecision.DENY)
                .expectComplete().verify(PATIENCE);
        rsocket.dispose();
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = { "application/json", "APPLICATION/BINARY", "application/protobuf", "application/sapl",
            "application/vnd.sapl.binary;version=1", "APPLICATION/VND.SAPL.BINARY",
            "application/vnd.sapl.value+binary;version=1" })
    @DisplayName("a connection in a data MIME type the node does not speak is refused as unsupported")
    void whenSetupNamesAnotherDataMimeTypeThenConnectionIsRefused(String mimeType) throws IOException {
        val rsocket = connect(mimeType);
        StepVerifier.create(rsocket.requestResponse(request("decide-once", PROTOBUF.write(SUBSCRIPTION))))
                .expectError(UnsupportedSetupException.class).verify(PATIENCE);
        rsocket.dispose();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("misreadPayloads")
    @DisplayName("a payload in the other format is refused as invalid, not misread")
    void whenPayloadIsInTheOtherFormatThenItIsRefusedAsInvalid(String mimeType, Codec otherFormat) throws IOException {
        val rsocket = connect(mimeType);
        StepVerifier.create(rsocket.requestResponse(request("decide-once", otherFormat.write(SUBSCRIPTION))))
                .expectError(InvalidException.class).verify(PATIENCE);
        rsocket.dispose();
    }

    static Stream<Arguments> misreadPayloads() {
        return Stream.of(arguments("application/binary", BINARY), arguments("application/vnd.sapl.binary", PROTOBUF));
    }

    private RSocket connect(String dataMimeType) {
        return RSocketConnector.create().dataMimeType(dataMimeType).connect(TcpClientTransport.create(port))
                .block(PATIENCE);
    }

    private static Payload request(String route, byte[] data) {
        return DefaultPayload.create(data, route.getBytes(StandardCharsets.UTF_8));
    }

    private static AuthorizationDecision read(Codec codec, Payload answer) {
        try {
            val buffer = answer.data();
            val bytes  = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return codec.read(bytes);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            answer.release();
        }
    }
}
