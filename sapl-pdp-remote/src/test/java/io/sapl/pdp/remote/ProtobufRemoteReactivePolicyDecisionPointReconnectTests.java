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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.util.DefaultPayload;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.proto.SaplProtobufCodec;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Proves the direct-RSocket streaming PDP client treats both a graceful
 * server-side stream COMPLETE and a transport error as reconnect triggers, so
 * the decision stream never terminates on its own while the subscriber is
 * still listening.
 */
@ExtendWith(MockitoExtension.class)
class ProtobufRemoteReactivePolicyDecisionPointReconnectTests {

    private static final AuthorizationSubscription SUBSCRIPTION = AuthorizationSubscription.of("subject", "action",
            "resource");

    @Mock
    private RSocket rSocket;

    private static Payload payloadFor(AuthorizationDecision decision) throws IOException {
        return DefaultPayload.create(SaplProtobufCodec.writeAuthorizationDecision(decision));
    }

    @Test
    @Timeout(10)
    @DisplayName("Graceful server-side stream complete triggers a reconnect rather than terminating the stream")
    void whenServerCompletesStreamThenClientReconnectsAndContinues() throws IOException {
        val attempt = new AtomicInteger();
        when(rSocket.requestStream(any(Payload.class))).thenAnswer(invocation -> Flux.defer(() -> {
            ((Payload) invocation.getArgument(0)).release();
            // First subscription emits one decision then COMPLETES gracefully,
            // every later subscription emits a different decision. A client that
            // honoured the graceful complete would terminate after PERMIT; the
            // reconnect path instead re-subscribes and yields DENY.
            if (attempt.getAndIncrement() == 0) {
                return Flux.just(decisionPayload(AuthorizationDecision.PERMIT));
            }
            return Flux.just(decisionPayload(AuthorizationDecision.DENY));
        }));

        val pdp = new ProtobufRemoteReactivePolicyDecisionPoint(Mono.just(rSocket), 1, 2);

        StepVerifier.create(pdp.decide(SUBSCRIPTION).take(3)).expectNext(AuthorizationDecision.PERMIT,
                AuthorizationDecision.INDETERMINATE, AuthorizationDecision.DENY).thenCancel().verify();

        assertThat(attempt.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @Timeout(10)
    @DisplayName("Transport error triggers a reconnect rather than terminating the stream")
    void whenTransportErrorsThenClientReconnectsAndContinues() throws IOException {
        val attempt = new AtomicInteger();
        when(rSocket.requestStream(any(Payload.class))).thenAnswer(invocation -> Flux.defer(() -> {
            ((Payload) invocation.getArgument(0)).release();
            // First subscription fails at the transport level, the reconnect path
            // re-subscribes and the second subscription yields a real decision.
            if (attempt.getAndIncrement() == 0) {
                return Flux.error(new IllegalStateException("simulated transport failure"));
            }
            return Flux.just(decisionPayload(AuthorizationDecision.PERMIT));
        }));

        val pdp = new ProtobufRemoteReactivePolicyDecisionPoint(Mono.just(rSocket), 1, 2);

        StepVerifier.create(pdp.decide(SUBSCRIPTION).take(2))
                .expectNext(AuthorizationDecision.INDETERMINATE, AuthorizationDecision.PERMIT).thenCancel().verify();

        assertThat(attempt.get()).isGreaterThanOrEqualTo(2);
    }

    private static Payload decisionPayload(AuthorizationDecision decision) {
        try {
            return payloadFor(decision);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
