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
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * Proves the direct-RSocket PDP client fails closed within a bounded time on a
 * live but application-silent server across every decision entry point (single
 * and multi, streaming and one-shot), and that disposing the client while a
 * connect is in flight does not leak the connection that arrives afterwards.
 */
@ExtendWith(MockitoExtension.class)
class ProtobufRemoteReactivePolicyDecisionPointLivenessTests {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private static final AuthorizationSubscription SUBSCRIPTION = AuthorizationSubscription.of("subject", "action",
            "resource");

    private static final MultiAuthorizationSubscription MULTI_SUBSCRIPTION = new MultiAuthorizationSubscription()
            .addAuthorizationSubscription("id", JSON.stringNode("subject"), JSON.stringNode("action"),
                    JSON.stringNode("resource"));

    @Mock
    private RSocket rSocket;

    @Test
    @Timeout(10)
    @DisplayName("A live server that never sends the first multi-decide stream item times out to INDETERMINATE")
    void whenMultiDecideFirstItemNeverArrivesThenTimesOutToIndeterminate() {
        when(rSocket.requestStream(any(Payload.class))).thenAnswer(invocation -> {
            ((Payload) invocation.getArgument(0)).release();
            return Flux.never();
        });
        val pdp = new ProtobufRemoteReactivePolicyDecisionPoint(Mono.just(rSocket), 1, 2, 200);

        StepVerifier.create(pdp.decide(MULTI_SUBSCRIPTION, "default").take(1))
                .expectNext(IdentifiableAuthorizationDecision.INDETERMINATE).verifyComplete();
    }

    @Test
    @Timeout(10)
    @DisplayName("A live server that never sends the first decide-all stream item times out to INDETERMINATE")
    void whenDecideAllFirstItemNeverArrivesThenTimesOutToIndeterminate() {
        when(rSocket.requestStream(any(Payload.class))).thenAnswer(invocation -> {
            ((Payload) invocation.getArgument(0)).release();
            return Flux.never();
        });
        val pdp = new ProtobufRemoteReactivePolicyDecisionPoint(Mono.just(rSocket), 1, 2, 200);

        StepVerifier.create(pdp.decideAll(MULTI_SUBSCRIPTION, "default").take(1))
                .expectNext(MultiAuthorizationDecision.indeterminate()).verifyComplete();
    }

    @Test
    @Timeout(10)
    @DisplayName("A live server that never answers a one-shot decide-once request times out to INDETERMINATE")
    void whenDecideOnceResponseNeverArrivesThenTimesOutToIndeterminate() {
        when(rSocket.requestResponse(any(Payload.class))).thenAnswer(invocation -> {
            ((Payload) invocation.getArgument(0)).release();
            return Mono.never();
        });
        val pdp = new ProtobufRemoteReactivePolicyDecisionPoint(Mono.just(rSocket), 1, 2, 200);

        StepVerifier.create(pdp.decideOnce(SUBSCRIPTION, "default")).expectNext(AuthorizationDecision.INDETERMINATE)
                .verifyComplete();
    }

    @Test
    @Timeout(10)
    @DisplayName("A live server that never answers a one-shot decide-all-once request times out to INDETERMINATE")
    void whenDecideAllOnceResponseNeverArrivesThenTimesOutToIndeterminate() {
        when(rSocket.requestResponse(any(Payload.class))).thenAnswer(invocation -> {
            ((Payload) invocation.getArgument(0)).release();
            return Mono.never();
        });
        val pdp = new ProtobufRemoteReactivePolicyDecisionPoint(Mono.just(rSocket), 1, 2, 200);

        StepVerifier.create(pdp.decideAllOnce(MULTI_SUBSCRIPTION))
                .expectNext(MultiAuthorizationDecision.indeterminate()).verifyComplete();
    }

    @Test
    @Timeout(10)
    @DisplayName("A server that completes a one-shot decide-all-once with no decision fails closed to INDETERMINATE")
    void whenDecideAllOnceResponseIsEmptyThenFailsClosedToIndeterminate() {
        when(rSocket.requestResponse(any(Payload.class))).thenAnswer(invocation -> {
            ((Payload) invocation.getArgument(0)).release();
            return Mono.empty();
        });
        val pdp = new ProtobufRemoteReactivePolicyDecisionPoint(Mono.just(rSocket), 1, 2, 200);

        StepVerifier.create(pdp.decideAllOnce(MULTI_SUBSCRIPTION))
                .expectNext(MultiAuthorizationDecision.indeterminate()).verifyComplete();
    }

    @Test
    @Timeout(10)
    @DisplayName("Disposing the client while a connect is in flight disposes the socket that arrives afterwards")
    void whenDisposeRunsDuringInFlightConnectThenLateSocketIsDisposed() {
        val           connectStarted = new AtomicBoolean();
        Mono<RSocket> connect        = Mono.fromCallable(() -> {
                                         connectStarted.set(true);
                                         return rSocket;
                                     }).delayElement(Duration.ofMillis(300));
        when(rSocket.requestResponse(any(Payload.class))).thenAnswer(invocation -> {
            ((Payload) invocation.getArgument(0)).release();
            return Mono.never();
        });
        val pdp = new ProtobufRemoteReactivePolicyDecisionPoint(connect, 1, 2, 5000);

        val inFlight = pdp.decideOnce(SUBSCRIPTION, "default").subscribe();
        await().atMost(Duration.ofSeconds(2)).untilTrue(connectStarted);

        pdp.dispose();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(rSocket, atLeastOnce()).dispose());
        inFlight.dispose();
        assertThat(connectStarted).isTrue();
    }
}
