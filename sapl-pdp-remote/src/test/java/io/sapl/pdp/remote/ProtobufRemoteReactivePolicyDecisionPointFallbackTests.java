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

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.TestSocketUtils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.rsocket.DuplexConnection;
import io.rsocket.RSocketErrorException;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketServer;
import io.rsocket.exceptions.InvalidException;
import io.rsocket.exceptions.UnsupportedSetupException;
import io.rsocket.plugins.DuplexConnectionInterceptor;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.util.DefaultPayload;
import io.sapl.api.binary.SaplBinaryCodec;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.proto.SaplProtobufCodec;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * A client in the SAPL binary format against nodes that do not speak it: one
 * that refuses the data MIME type at SETUP, and one that reads protobuf
 * whatever the data MIME type, as the node did before the format. The client
 * falls back to protobuf for the connection, sends the request again in
 * protobuf, and tries the binary format again on its next connection.
 */
@Timeout(30)
@DisplayName("RSocket remote PDP fallback from the binary format to protobuf")
class ProtobufRemoteReactivePolicyDecisionPointFallbackTests {

    private static final String PROTOBUF = "application/binary";
    private static final String BINARY   = "application/vnd.sapl.binary";

    /** The most subscriptions of a multi-subscription that a node of these tests reads. */
    private static final int MAX_SUBSCRIPTIONS = 2;

    private static final Duration PATIENCE = Duration.ofSeconds(10);

    /** The message of the refusal of a SETUP with a data MIME type a node does not speak. */
    private static final String UNSUPPORTED_DATA_MIME_TYPE = "Unsupported data MIME type";

    private static final AuthorizationSubscription SUBSCRIPTION = AuthorizationSubscription.of("alice", "read", "doc");

    private final List<ProtobufRemoteReactivePolicyDecisionPoint> clients = new ArrayList<>();
    private final List<FormatNode>                                nodes   = new ArrayList<>();

    @AfterEach
    void disposeClientsAndNodes() {
        clients.forEach(ProtobufRemoteReactivePolicyDecisionPoint::dispose);
        nodes.forEach(FormatNode::stop);
    }

    /** What a node of these tests knows of the data formats. */
    enum Knows {
        /** Protobuf and the binary format; it refuses any other data MIME type at SETUP. */
        BINARY_FORMAT,
        /** Protobuf only; it refuses any other data MIME type at SETUP. */
        PROTOBUF_CHECKING_SETUP,
        /** Protobuf only, whatever the data MIME type. */
        PROTOBUF_IGNORING_SETUP
    }

    /** The first request a client sends on its connection. */
    enum FirstRequest {
        DECIDE_ONCE,
        DECIDE_ALL_ONCE,
        DECIDE
    }

    static Stream<Arguments> nodesAndFirstRequests() {
        val cases = new ArrayList<Arguments>();
        for (val request : FirstRequest.values()) {
            cases.add(arguments(Knows.BINARY_FORMAT, request, List.of(BINARY)));
            cases.add(arguments(Knows.PROTOBUF_CHECKING_SETUP, request, List.of(BINARY, PROTOBUF)));
            cases.add(arguments(Knows.PROTOBUF_IGNORING_SETUP, request, List.of(BINARY, PROTOBUF)));
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("nodesAndFirstRequests")
    @DisplayName("The first request of a binary client is decided, over protobuf if the node does not speak the format")
    void whenNodeDoesNotSpeakTheBinaryFormatThenTheFirstRequestIsDecidedOverProtobuf(Knows knows, FirstRequest request,
            List<String> expectedSetups) {
        val node     = start(knows);
        val pdp      = binaryClient(node, 1);
        val verdicts = verdicts(pdp, request);
        val expected = request == FirstRequest.DECIDE_ALL_ONCE ? List.of(Decision.PERMIT, Decision.PERMIT)
                : List.of(Decision.PERMIT);
        assertThat(List.of(verdicts, node.setups())).containsExactly(expected, expectedSetups);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Knows.class, names = { "PROTOBUF_CHECKING_SETUP", "PROTOBUF_IGNORING_SETUP" })
    @DisplayName("When a connection that fell back to protobuf ends, the next connection tries the binary format again")
    void whenFallbackConnectionEndsThenTheNextConnectionTriesTheBinaryFormatAgain(Knows knows) {
        val node   = start(knows);
        val pdp    = binaryClient(node, 1);
        val before = pdp.decideOnce(SUBSCRIPTION).block(PATIENCE);
        node.closeConnections();
        await().atMost(PATIENCE)
                .until(() -> AuthorizationDecision.PERMIT.equals(pdp.decideOnce(SUBSCRIPTION).block(PATIENCE))
                        && node.setups().size() == 4);
        assertThat(List.of(before, node.setups())).containsExactly(AuthorizationDecision.PERMIT,
                List.of(BINARY, PROTOBUF, BINARY, PROTOBUF));
    }

    static Stream<Arguments> refusedRequests() {
        return Stream.of(arguments(List.of(List.of("a", "b"), List.of("a", "b", "c")),
                List.of(List.of(Decision.PERMIT, Decision.PERMIT), List.of(Decision.INDETERMINATE)), List.of(BINARY)),
                arguments(List.of(List.of("a", "b", "c")), List.of(List.of(Decision.INDETERMINATE)),
                        List.of(BINARY, PROTOBUF)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusedRequests")
    @DisplayName("A node that speaks the binary format and refuses a request makes the client fall back only on a new connection")
    void whenNodeSpeakingTheBinaryFormatRefusesARequestThenTheClientFallsBackOnlyOnANewConnection(
            List<List<String>> requests, List<List<Decision>> expectedVerdicts, List<String> expectedSetups) {
        val node     = start(Knows.BINARY_FORMAT);
        val pdp      = binaryClient(node, 1);
        val verdicts = requests.stream()
                .map(ids -> verdictsOf(requireNonNull(pdp.decideAllOnce(multiSubscription(ids)).block(PATIENCE))))
                .toList();
        assertThat(List.of(verdicts, node.setups())).containsExactly(expectedVerdicts, expectedSetups);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Knows.class, names = { "PROTOBUF_CHECKING_SETUP", "PROTOBUF_IGNORING_SETUP" })
    @DisplayName("Each connection of a pool falls back once and stays in protobuf")
    void whenPoolMeetsNodeWithoutTheBinaryFormatThenEachConnectionFallsBackOnce(Knows knows) {
        val node     = start(knows);
        val pdp      = binaryClient(node, 2);
        val verdicts = new ArrayList<AuthorizationDecision>();
        for (var request = 0; request < 20; request++) {
            verdicts.add(pdp.decideOnce(SUBSCRIPTION).block(PATIENCE));
        }
        await().atMost(PATIENCE).until(() -> node.setups().stream().filter(PROTOBUF::equals).count() == 2);
        for (var request = 0; request < 20; request++) {
            verdicts.add(pdp.decideOnce(SUBSCRIPTION).block(PATIENCE));
        }
        assertThat(List.of(verdicts.stream().distinct().toList(), node.setups().stream().sorted().toList()))
                .containsExactly(List.of(AuthorizationDecision.PERMIT), List.of(PROTOBUF, PROTOBUF, BINARY, BINARY));
    }

    @Test
    @DisplayName("A disposed connection's SETUP refusal is processed before its replacement chooses a format")
    void whenSetupRefusalArrivesAfterDisposalThenReplacementUsesProtobuf() {
        val formats = new ArrayList<PayloadFormat>();
        val sockets = new ArrayList<ControlledSocket>();
        val pool    = new RSocketPool(format -> Mono.fromSupplier(() -> {
                        formats.add(format);
                        val socket = new ControlledSocket();
                        sockets.add(socket);
                        return socket;
                    }), PayloadFormat.BINARY, 2, 10, 100);
        try {
            assertThat(permitted(pool)).isEqualTo(AuthorizationDecision.PERMIT);
            val background = sockets.get(1);
            background.disposed = true;
            assertThat(permitted(pool)).isEqualTo(AuthorizationDecision.PERMIT);
            assertThat(formats).containsExactly(PayloadFormat.BINARY, PayloadFormat.BINARY, PayloadFormat.PROTOBUF);
            background.refuse();
            for (var request = 0; request < 4; request++) {
                assertThat(permitted(pool)).isEqualTo(AuthorizationDecision.PERMIT);
            }
            assertThat(formats).containsExactly(PayloadFormat.BINARY, PayloadFormat.BINARY, PayloadFormat.PROTOBUF,
                    PayloadFormat.PROTOBUF);
        } finally {
            pool.dispose();
        }
    }

    @Test
    @DisplayName("A client not asked for the binary format speaks protobuf")
    void whenClientIsNotAskedForTheBinaryFormatThenItSpeaksProtobuf() {
        val node = start(Knows.BINARY_FORMAT);
        val pdp  = ProtobufRemoteReactivePolicyDecisionPoint.builder().host("localhost").port(node.port).build();
        clients.add(pdp);
        assertThat(List.of(pdp.decideOnce(SUBSCRIPTION).block(PATIENCE), node.setups()))
                .containsExactly(AuthorizationDecision.PERMIT, List.of(PROTOBUF));
    }

    private FormatNode start(Knows knows) {
        val node = new FormatNode(knows);
        nodes.add(node);
        return node;
    }

    private ProtobufRemoteReactivePolicyDecisionPoint binaryClient(FormatNode node, int connections) {
        val pdp = ProtobufRemoteReactivePolicyDecisionPoint.builder().host("localhost").port(node.port).binaryFormat()
                .connections(connections).build();
        clients.add(pdp);
        return pdp;
    }

    private static List<Decision> verdicts(ProtobufRemoteReactivePolicyDecisionPoint pdp, FirstRequest request) {
        return switch (request) {
        case DECIDE_ONCE     -> List.of(requireNonNull(pdp.decideOnce(SUBSCRIPTION).block(PATIENCE)).decision());
        case DECIDE_ALL_ONCE ->
            verdictsOf(requireNonNull(pdp.decideAllOnce(multiSubscription(List.of("a", "b"))).block(PATIENCE)));
        case DECIDE          -> List.of(requireNonNull(pdp.decide(SUBSCRIPTION).blockFirst(PATIENCE)).decision());
        };
    }

    private static List<Decision> verdictsOf(MultiAuthorizationDecision decisions) {
        val verdicts = new ArrayList<Decision>();
        decisions.forEach(decision -> verdicts.add(decision.decision().decision()));
        return verdicts;
    }

    private static MultiAuthorizationSubscription multiSubscription(List<String> ids) {
        val subscriptions = new MultiAuthorizationSubscription();
        ids.forEach(id -> subscriptions.addSubscription(id, SUBSCRIPTION));
        return subscriptions;
    }

    private record PendingRequest(RSocketPool.Connection connection, Sinks.One<AuthorizationDecision> answer) {}

    private static AuthorizationDecision permitted(RSocketPool pool) {
        val pending = new ArrayDeque<PendingRequest>();
        val result  = pool.request(connection -> {
                        val answer = Sinks.<AuthorizationDecision>one();
                        pending.add(new PendingRequest(connection, answer));
                        return answer.asMono();
                    }).toFuture();
        while (!result.isDone()) {
            val request = pending.removeFirst();
            if (request.connection().format() == PayloadFormat.BINARY) {
                ((ControlledSocket) request.connection().socket()).refuse();
                request.answer().tryEmitError(new UnsupportedSetupException(UNSUPPORTED_DATA_MIME_TYPE));
            } else {
                request.answer().tryEmitValue(AuthorizationDecision.PERMIT);
            }
        }
        return result.join();
    }

    private static final class ControlledSocket implements RSocket {
        private final Sinks.Empty<Void> closed = Sinks.empty();
        private boolean                 disposed;

        @Override
        public boolean isDisposed() {
            return disposed;
        }

        @Override
        public Mono<Void> onClose() {
            return closed.asMono();
        }

        @Override
        public void dispose() {
            disposed = true;
            closed.tryEmitEmpty();
        }

        private void refuse() {
            disposed = true;
            closed.tryEmitError(new UnsupportedSetupException(UNSUPPORTED_DATA_MIME_TYPE));
        }
    }

    /**
     * An RSocket node of the tests that permits every subscription, in protobuf
     * or in the binary format as it knows them, and keeps the data MIME type of
     * each SETUP. A request it cannot read in the format of its connection, or
     * one with more than {@link #MAX_SUBSCRIPTIONS} subscriptions, it refuses as
     * a bad request, as the node does.
     */
    private static final class FormatNode implements SocketAcceptor {

        private final Knows            knows;
        private final List<String>     setups      = new CopyOnWriteArrayList<>();
        private final List<RSocket>    connections = new CopyOnWriteArrayList<>();
        private final int              port        = TestSocketUtils.findAvailableTcpPort();
        private final CloseableChannel server;

        FormatNode(Knows knows) {
            this.knows  = knows;
            this.server = RSocketServer.create(this)
                    .interceptors(registry -> registry.forConnection(new UnsupportedSetupRelay()))
                    .bindNow(TcpServerTransport.create("localhost", port));
        }

        List<String> setups() {
            return List.copyOf(setups);
        }

        void closeConnections() {
            connections.forEach(RSocket::dispose);
        }

        void stop() {
            server.dispose();
        }

        @Override
        public Mono<RSocket> accept(ConnectionSetupPayload setup, RSocket sendingSocket) {
            val mimeType = setup.dataMimeType();
            setups.add(mimeType);
            connections.add(sendingSocket);
            val readsBinary = knows == Knows.BINARY_FORMAT && BINARY.equals(mimeType);
            if (knows != Knows.PROTOBUF_IGNORING_SETUP && !readsBinary && !PROTOBUF.equals(mimeType)) {
                return Mono.error(new UnsupportedSetupException(UNSUPPORTED_DATA_MIME_TYPE));
            }
            return Mono.just(new RSocket() {
                @Override
                public Mono<Payload> requestResponse(Payload request) {
                    return Mono.fromCallable(() -> permits(readsBinary, request));
                }

                @Override
                public Flux<Payload> requestStream(Payload request) {
                    return Mono.fromCallable(() -> permits(readsBinary, request)).concatWith(Flux.never());
                }
            });
        }

        private static Payload permits(boolean binary, Payload request) {
            try {
                val route = request.getMetadataUtf8();
                val data  = ByteBufUtil.getBytes(request.data());
                return DefaultPayload.create(switch (route) {
                case "decide-once", "decide" -> {
                    if (binary) {
                        SaplBinaryCodec.readAuthorizationSubscription(data);
                        yield SaplBinaryCodec.writeAuthorizationDecision(AuthorizationDecision.PERMIT);
                    }
                    SaplProtobufCodec.readAuthorizationSubscription(data);
                    yield SaplProtobufCodec.writeAuthorizationDecision(AuthorizationDecision.PERMIT);
                }
                default                      -> {
                    val subscriptions = binary
                            ? SaplBinaryCodec.readMultiAuthorizationSubscription(data, MAX_SUBSCRIPTIONS)
                            : SaplProtobufCodec.readMultiAuthorizationSubscription(data, MAX_SUBSCRIPTIONS);
                    val decisions     = new MultiAuthorizationDecision();
                    for (val subscription : subscriptions) {
                        decisions.setDecision(subscription.subscriptionId(), AuthorizationDecision.PERMIT);
                    }
                    yield binary ? SaplBinaryCodec.writeMultiAuthorizationDecision(decisions)
                            : SaplProtobufCodec.writeMultiAuthorizationDecision(decisions);
                }
                });
            } catch (IOException e) {
                throw new InvalidException("Bad request", e);
            } finally {
                request.release();
            }
        }
    }

    /**
     * Sends the refusal of an unsupported data MIME type as
     * {@code UNSUPPORTED_SETUP}, as the node does: rsocket-java sends every refusal
     * of an acceptor as {@code REJECTED_SETUP} with its message.
     */
    private static final class UnsupportedSetupRelay implements DuplexConnectionInterceptor {

        @Override
        public DuplexConnection apply(Type type, DuplexConnection connection) {
            if (type != Type.SOURCE) {
                return connection;
            }
            return new DuplexConnection() {
                @Override
                public void sendErrorAndClose(RSocketErrorException error) {
                    connection.sendErrorAndClose(UNSUPPORTED_DATA_MIME_TYPE.equals(error.getMessage())
                            ? new UnsupportedSetupException(UNSUPPORTED_DATA_MIME_TYPE)
                            : error);
                }

                @Override
                public void sendFrame(int streamId, ByteBuf frame) {
                    connection.sendFrame(streamId, frame);
                }

                @Override
                public Flux<ByteBuf> receive() {
                    return connection.receive();
                }

                @Override
                public ByteBufAllocator alloc() {
                    return connection.alloc();
                }

                @Override
                public SocketAddress remoteAddress() {
                    return connection.remoteAddress();
                }

                @Override
                public Mono<Void> onClose() {
                    return connection.onClose();
                }

                @Override
                public void dispose() {
                    connection.dispose();
                }

                @Override
                public boolean isDisposed() {
                    return connection.isDisposed();
                }
            };
        }
    }
}
