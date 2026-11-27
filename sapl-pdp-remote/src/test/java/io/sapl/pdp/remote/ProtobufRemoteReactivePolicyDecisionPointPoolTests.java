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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.util.DefaultPayload;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.proto.SaplProtobufCodec;
import lombok.val;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * A client with a pool of RSocket connections to one node: requests in flight
 * together spread over the connections, a stream stays on the connection it
 * was opened on, and a lost connection costs only its own streams a moment
 * while the pool opens it anew. A pool of one connection is the client with
 * one shared connection.
 */
@Timeout(30)
@DisplayName("RSocket remote PDP connection pool")
class ProtobufRemoteReactivePolicyDecisionPointPoolTests {

    private static final AuthorizationSubscription SUBSCRIPTION = AuthorizationSubscription.of("subject", "action",
            "resource");

    private static final Duration PATIENCE = Duration.ofSeconds(10);

    private final List<ProtobufRemoteReactivePolicyDecisionPoint> clients       = new ArrayList<>();
    private final List<Disposable>                                subscriptions = new ArrayList<>();

    @AfterEach
    void disposeClientsAndStreams() {
        subscriptions.forEach(Disposable::dispose);
        clients.forEach(ProtobufRemoteReactivePolicyDecisionPoint::dispose);
    }

    static Stream<Arguments> poolsAndSpread() {
        return Stream.of(arguments(1, List.of(8)), arguments(2, List.of(4, 4)), arguments(4, List.of(2, 2, 2, 2)));
    }

    @ParameterizedTest(name = "a pool of {0} connections carries {1} of 8 requests")
    @MethodSource("poolsAndSpread")
    @DisplayName("Requests in flight together spread evenly over the connections of the pool")
    void whenRequestsAreInFlightTogetherThenTheySpreadEvenlyOverTheConnections(int size, List<Integer> expected)
            throws Exception {
        val node = new FakeNode();
        val pdp  = pool(node, size);
        pdp.decideOnce(SUBSCRIPTION).block(PATIENCE);
        await().atMost(PATIENCE).until(() -> node.connections().size() == size);
        val before = node.requestsPerConnection();
        node.hold();
        val decided = Flux.range(0, 8).flatMap(request -> pdp.decideOnce(SUBSCRIPTION), 8).collectList().toFuture();
        await().atMost(PATIENCE).until(() -> node.requests() == 9);
        val spread = spreadSince(before, node.requestsPerConnection());
        node.release();

        assertThat(decided.get(PATIENCE.toSeconds(), TimeUnit.SECONDS)).hasSize(8)
                .containsOnly(AuthorizationDecision.PERMIT);
        assertThat(spread).isEqualTo(expected);
        assertThat(node.connections()).hasSize(size);
    }

    @Test
    @DisplayName("A lost connection makes only its own stream indeterminate until it subscribes again")
    void whenOneConnectionIsLostThenOnlyItsStreamIsIndeterminateMeanwhileAndSubscribesAgain() {
        val node = new FakeNode();
        val pdp  = pool(node, 2);
        pdp.decideOnce(SUBSCRIPTION).block(PATIENCE);
        await().atMost(PATIENCE).until(() -> node.connections().size() == 2);
        val first  = collect(pdp.decide(SUBSCRIPTION));
        val second = collect(pdp.decide(SUBSCRIPTION));
        await().atMost(PATIENCE).until(() -> first.size() == 1 && second.size() == 1);

        node.connections().getFirst().dispose();
        await().atMost(PATIENCE).until(() -> first.size() == 3 || second.size() == 3);
        node.decide(AuthorizationDecision.DENY);
        await().atMost(PATIENCE).until(() -> first.size() + second.size() == 6);

        val streams = new ArrayList<>(List.of(List.copyOf(first), List.copyOf(second)));
        streams.sort(Comparator.comparingInt(List::size));
        assertThat(streams).containsExactly(List.of(AuthorizationDecision.PERMIT, AuthorizationDecision.DENY),
                List.of(AuthorizationDecision.PERMIT, AuthorizationDecision.INDETERMINATE, AuthorizationDecision.PERMIT,
                        AuthorizationDecision.DENY));
    }

    @Test
    @DisplayName("A lost connection is avoided by one-shot requests and opened anew by the pool")
    void whenOneConnectionIsLostThenRequestsAvoidItAndThePoolOpensItAnew() {
        val node = new FakeNode();
        val pdp  = pool(node, 2);
        pdp.decideOnce(SUBSCRIPTION).block(PATIENCE);
        await().atMost(PATIENCE).until(() -> node.connections().size() == 2);
        val lost = node.connections().getFirst();
        lost.dispose();
        val requestsBefore = lost.requests();

        val decided = Flux.range(0, 10).concatMap(request -> pdp.decideOnce(SUBSCRIPTION)).collectList()
                .block(PATIENCE);
        await().atMost(PATIENCE).until(() -> node.connections().size() == 3);

        assertThat(decided).hasSize(10).containsOnly(AuthorizationDecision.PERMIT);
        assertThat(lost.requests()).isEqualTo(requestsBefore);
    }

    @ParameterizedTest(name = "a pool of {0} connections")
    @ValueSource(ints = { 1, 4 })
    @DisplayName("With every connection down requests are indeterminate, and once the node is back they decide at once and the pool fills again")
    void whenAllConnectionsAreDownThenRequestsAreIndeterminateAndDecideAtOnceWhenTheNodeIsBack(int size) {
        val node   = new FakeNode();
        val pdp    = pool(node, size);
        val before = pdp.decideOnce(SUBSCRIPTION).block(PATIENCE);
        await().atMost(PATIENCE).until(() -> node.liveConnections() == size);

        node.goAway();
        val meanwhile = pdp.decideOnce(SUBSCRIPTION).block(PATIENCE);
        node.comeBack();
        val after = pdp.decideOnce(SUBSCRIPTION).block(PATIENCE);
        await().atMost(PATIENCE).until(() -> node.liveConnections() == size);

        assertThat(List.of(before, meanwhile, after)).containsExactly(AuthorizationDecision.PERMIT,
                AuthorizationDecision.INDETERMINATE, AuthorizationDecision.PERMIT);
    }

    @ParameterizedTest(name = "{0} connections")
    @ValueSource(ints = { 0, -1 })
    @DisplayName("A pool without connections is refused when the client is built")
    void whenThePoolHasNoConnectionsThenBuildingFails(int connections) {
        val builder = ProtobufRemoteReactivePolicyDecisionPoint.builder().host("localhost").port(7000);
        assertThatThrownBy(() -> builder.connections(connections)).isInstanceOf(IllegalArgumentException.class);
    }

    private ProtobufRemoteReactivePolicyDecisionPoint pool(FakeNode node, int connections) {
        val client = new ProtobufRemoteReactivePolicyDecisionPoint(node.connect(), 1, 2, 5000, connections);
        clients.add(client);
        return client;
    }

    private List<AuthorizationDecision> collect(Flux<AuthorizationDecision> decisions) {
        val collected = new CopyOnWriteArrayList<AuthorizationDecision>();
        subscriptions.add(decisions.subscribe(collected::add));
        return collected;
    }

    /**
     * How many requests each connection got since the counts before, most first,
     * with the connections that got none.
     */
    private static List<Integer> spreadSince(List<Integer> before, List<Integer> after) {
        val spread = new ArrayList<Integer>();
        for (var index = 0; index < after.size(); index++) {
            spread.add(after.get(index) - (index < before.size() ? before.get(index) : 0));
        }
        spread.sort(Comparator.reverseOrder());
        return spread;
    }

    private static Payload payloadOf(AuthorizationDecision decision) {
        try {
            return DefaultPayload.create(SaplProtobufCodec.writeAuthorizationDecision(decision));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A node as the client sees it through its connections: it answers one-shot
     * requests with its decision, unless it holds them back, and streams its
     * decision as it changes. It can go away, which closes every connection and
     * refuses new ones, and come back.
     */
    private static final class FakeNode {

        private final List<FakeConnection>              connections = new CopyOnWriteArrayList<>();
        private final Sinks.Many<AuthorizationDecision> decisions   = Sinks.many().replay().latest();
        private volatile Sinks.Empty<Void>              gate        = openGate();
        private volatile boolean                        reachable   = true;

        FakeNode() {
            decisions.tryEmitNext(AuthorizationDecision.PERMIT);
        }

        Mono<RSocket> connect() {
            return Mono.defer(() -> {
                if (!reachable) {
                    return Mono.error(new ConnectException("The fake node is away."));
                }
                val connection = new FakeConnection(this);
                connections.add(connection);
                return Mono.just(connection);
            });
        }

        List<FakeConnection> connections() {
            return connections;
        }

        long liveConnections() {
            return connections.stream().filter(connection -> !connection.isDisposed()).count();
        }

        List<Integer> requestsPerConnection() {
            return connections.stream().map(FakeConnection::requests).toList();
        }

        int requests() {
            return connections.stream().mapToInt(FakeConnection::requests).sum();
        }

        void hold() {
            gate = Sinks.empty();
        }

        void release() {
            gate.tryEmitEmpty();
        }

        void decide(AuthorizationDecision decision) {
            decisions.tryEmitNext(decision);
        }

        void goAway() {
            reachable = false;
            connections.forEach(FakeConnection::dispose);
        }

        void comeBack() {
            reachable = true;
        }

        private static Sinks.Empty<Void> openGate() {
            val open = Sinks.<Void>empty();
            open.tryEmitEmpty();
            return open;
        }
    }

    /**
     * A connection to a {@link FakeNode} that counts its one-shot requests. When
     * it is disposed, as when the network loses it, its streams fail.
     */
    private static final class FakeConnection implements RSocket {

        private final FakeNode          node;
        private final AtomicInteger     requests = new AtomicInteger();
        private final Sinks.Empty<Void> closed   = Sinks.empty();
        private final AtomicBoolean     disposed = new AtomicBoolean();

        FakeConnection(FakeNode node) {
            this.node = node;
        }

        int requests() {
            return requests.get();
        }

        @Override
        public Mono<Payload> requestResponse(Payload payload) {
            payload.release();
            requests.incrementAndGet();
            return node.gate.asMono().then(node.decisions.asFlux().next())
                    .map(ProtobufRemoteReactivePolicyDecisionPointPoolTests::payloadOf);
        }

        @Override
        public Flux<Payload> requestStream(Payload payload) {
            payload.release();
            return Flux.merge(
                    node.decisions.asFlux().map(ProtobufRemoteReactivePolicyDecisionPointPoolTests::payloadOf),
                    closed.asMono().then(Mono.error(new ClosedChannelException())));
        }

        @Override
        public void dispose() {
            disposed.set(true);
            closed.tryEmitEmpty();
        }

        @Override
        public boolean isDisposed() {
            return disposed.get();
        }

        @Override
        public Mono<Void> onClose() {
            return closed.asMono();
        }
    }
}
