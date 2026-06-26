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

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.StreamingPolicyDecisionPoint;
import io.sapl.api.stream.QueueStream;
import io.sapl.api.stream.Stream;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Adapter exposing a reactive remote {@link ReactivePolicyDecisionPoint}
 * through the blocking {@link StreamingPolicyDecisionPoint} surface.
 * <p>
 * Symmetric counterpart of
 * {@code io.sapl.reactive.pdp.DelegatingReactivePolicyDecisionPoint},
 * which wraps a blocking PDP and exposes it as reactive. One-shot
 * decisions block on the underlying {@link Mono}; streaming decisions
 * bridge each upstream emission into a {@link QueueStream} consumed
 * via {@code awaitNext()} / {@code tryNext()} and disposed on
 * {@code close()}.
 * <p>
 * Used so that consumers wired to the blocking PDP shape (Spring
 * starter Servlet PEP, sapl-node CLI) work uniformly against remote
 * PDPs, which natively speak reactive over the wire.
 */
@Slf4j
@RequiredArgsConstructor
public final class DelegatingBlockingPolicyDecisionPoint implements StreamingPolicyDecisionPoint {

    private static final String WARN_DECISION_STREAM_FAILED = "Remote PDP decision stream terminated with an error: {}";

    private final ReactivePolicyDecisionPoint reactive;

    @Override
    public AuthorizationDecision decideOnce(AuthorizationSubscription subscription, String pdpId) {
        return reactive.decideOnce(subscription, pdpId).block();
    }

    @Override
    public Stream<AuthorizationDecision> decide(AuthorizationSubscription subscription, String pdpId) {
        return bridge(reactive.decide(subscription, pdpId));
    }

    @Override
    public Stream<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription subscription, String pdpId) {
        return bridge(reactive.decide(subscription, pdpId));
    }

    @Override
    public Stream<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription subscription, String pdpId) {
        return bridge(reactive.decideAll(subscription, pdpId));
    }

    private static <T> Stream<T> bridge(Flux<T> source) {
        final var queue = new QueueStream<T>();
        // onError is terminal here, firing at most once. Remote PDPs emit a
        // fail-closed INDETERMINATE before erroring, so we log the cause and end.
        final var subscription = source.subscribe(queue::put, error -> {
            log.warn(WARN_DECISION_STREAM_FAILED, error.getMessage(), error);
            queue.complete();
        }, queue::complete);
        queue.onClose(subscription::dispose);
        return queue;
    }
}
