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
package io.sapl.reactive.pdp;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.stream.Stream;
import io.sapl.compiler.document.TracedVote;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import lombok.RequiredArgsConstructor;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.function.Supplier;

/**
 * Adapter that exposes a {@link BlockingPolicyDecisionPoint} through the
 * Reactor-flavoured {@link ReactivePolicyDecisionPoint} contract. Every
 * decision flow runs on the blocking PDP using the SAPL
 * {@link Stream} primitive; this class only bridges those streams to
 * {@link Flux}/{@link Mono} via a virtual-thread pump, with no
 * evaluation logic of its own.
 * <p>
 * Lifecycle listeners and decision interceptors fire on the wrapped
 * blocking PDP. They are not re-fired here.
 *
 * @since 4.1.0
 */
@RequiredArgsConstructor
public final class DelegatingReactivePolicyDecisionPoint implements ReactivePolicyDecisionPoint {

    private final BlockingPolicyDecisionPoint blocking;

    @Override
    public Flux<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription, String pdpId) {
        return adapt(() -> blocking.decide(authorizationSubscription, pdpId));
    }

    @Override
    public Mono<AuthorizationDecision> decideOnce(AuthorizationSubscription authorizationSubscription, String pdpId) {
        return Mono.create(sink -> {
            val worker = Thread.startVirtualThread(() -> {
                try {
                    sink.success(blocking.decideOnce(authorizationSubscription, pdpId));
                } catch (Throwable failure) {
                    sink.error(failure);
                }
            });
            sink.onCancel(worker::interrupt);
        });
    }

    @Override
    public Flux<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiSubscription,
            String pdpId) {
        return conflateBySubscription(() -> blocking.decide(multiSubscription, pdpId));
    }

    @Override
    public Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiSubscription, String pdpId) {
        return adapt(() -> blocking.decideAll(multiSubscription, pdpId));
    }

    /**
     * Engine-internal flux of {@link TracedVote}s for a subscription:
     * vote, emit timestamp, dependency map, and per-key snapshot read.
     * Mirrors {@link DelegatingReactivePolicyDecisionPoint#gatherVotes}
     * for tooling that consumes the trace through Reactor (the SAPL
     * playground).
     */
    public Flux<TracedVote> gatherVotes(AuthorizationSubscription sub, String pdpId) {
        return adapt(() -> blocking.gatherVotes(sub, pdpId));
    }

    /**
     * Bridges a SAPL {@link Stream} to a Reactor {@link Flux} via a
     * virtual-thread pump. The stream is opened lazily on subscription
     * and closed on cancel; the pump exits when the source completes
     * (returns {@code null}) or when the subscriber cancels.
     * <p>
     * Overflow strategy is {@link FluxSink.OverflowStrategy#LATEST}:
     * for an authorization-decision SSE, only the most recent decision
     * is operationally relevant, and bounding the buffer protects the
     * server from memory growth when a consumer falls behind a fast
     * attribute source.
     */
    private static <T> Flux<T> adapt(Supplier<Stream<T>> streamFactory) {
        return Flux.create(sink -> {
            val stream = streamFactory.get();
            val pump   = Thread.startVirtualThread(() -> pumpStreamToSink(stream, sink));
            sink.onCancel(() -> {
                pump.interrupt();
                stream.close();
            });
        }, FluxSink.OverflowStrategy.LATEST);
    }

    /**
     * Bridges a multi-subscription decision {@link Stream} to a {@link Flux}
     * with per-subscription latest-wins conflation. A slow consumer never loses
     * the latest decision of any individual subscription: only an older decision
     * for the SAME {@code subscriptionId} is superseded. Distinct subscriptions
     * never evict one another, so the pending buffer is bounded by the number of
     * subscriptions in the multi-subscription.
     * <p>
     * Emission is demand-gated: a decision is pushed only while the downstream
     * has outstanding request, so the global {@code LATEST} overflow strategy
     * (which conflates across all subscriptions and would drop other
     * subscriptions' decisions) is not used here.
     */
    static Flux<IdentifiableAuthorizationDecision> conflateBySubscription(
            Supplier<Stream<IdentifiableAuthorizationDecision>> streamFactory) {
        return Flux.create(sink -> {
            val pending = new LinkedHashMap<String, IdentifiableAuthorizationDecision>();
            val stream  = streamFactory.get();
            val pump    = Thread.startVirtualThread(() -> pumpConflating(stream, sink, pending));
            sink.onRequest(requested -> drainConflating(sink, pending));
            sink.onCancel(() -> {
                pump.interrupt();
                stream.close();
            });
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    private static void pumpConflating(Stream<IdentifiableAuthorizationDecision> stream,
            FluxSink<IdentifiableAuthorizationDecision> sink,
            LinkedHashMap<String, IdentifiableAuthorizationDecision> pending) {
        try (stream) {
            while (!Thread.interrupted()) {
                val value = stream.awaitNext();
                if (value == null) {
                    drainConflating(sink, pending);
                    sink.complete();
                    return;
                }
                synchronized (pending) {
                    pending.put(value.subscriptionId(), value);
                }
                drainConflating(sink, pending);
            }
        } catch (InterruptedException expected) {
            Thread.currentThread().interrupt();
        } catch (Throwable failure) {
            sink.error(failure);
        }
    }

    private static void drainConflating(FluxSink<IdentifiableAuthorizationDecision> sink,
            LinkedHashMap<String, IdentifiableAuthorizationDecision> pending) {
        synchronized (pending) {
            while (sink.requestedFromDownstream() > 0) {
                val iterator = pending.entrySet().iterator();
                if (!iterator.hasNext()) {
                    return;
                }
                val next = iterator.next().getValue();
                iterator.remove();
                sink.next(next);
            }
        }
    }

    private static <T> void pumpStreamToSink(Stream<T> stream, FluxSink<T> sink) {
        try {
            while (!Thread.interrupted()) {
                val value = stream.awaitNext();
                if (value == null) {
                    sink.complete();
                    return;
                }
                sink.next(value);
            }
        } catch (InterruptedException expected) {
            Thread.currentThread().interrupt();
        } catch (Throwable failure) {
            sink.error(failure);
        }
    }
}
