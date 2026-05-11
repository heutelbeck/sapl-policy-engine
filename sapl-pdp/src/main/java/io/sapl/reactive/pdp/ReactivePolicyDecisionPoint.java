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

import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.IdentifiableAuthorizationSubscription;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.ast.Outcome;
import io.sapl.compiler.document.*;
import io.sapl.attributes.store.AttributeStore;
import io.sapl.compiler.pdp.CompiledPdp;
import io.sapl.compiler.pdp.PdpVoterMetadata;
import io.sapl.api.pdp.DecisionInterceptor;
import io.sapl.api.pdp.SubscriptionLifecycleListener;
import io.sapl.api.pdp.TracedDecision;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import io.sapl.pdp.IdFactory;

import static io.sapl.pdp.BlockingPolicyDecisionPoint.buildTracedVote;
import static io.sapl.pdp.BlockingPolicyDecisionPoint.dispatchDecisionObservers;
import static io.sapl.pdp.BlockingPolicyDecisionPoint.errorVote;
import static io.sapl.pdp.BlockingPolicyDecisionPoint.noConfigurationVote;
import static io.sapl.pdp.BlockingPolicyDecisionPoint.notifyOnSubscribe;
import static io.sapl.pdp.BlockingPolicyDecisionPoint.notifyOnUnsubscribe;
import static io.sapl.pdp.BlockingPolicyDecisionPoint.tracedNoConfiguration;
import io.sapl.pdp.configuration.PdpUpdateEvent;
import io.sapl.pdp.configuration.PdpVoterSource;
import io.sapl.reactive.api.pdp.PolicyDecisionPoint;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class ReactivePolicyDecisionPoint implements PolicyDecisionPoint {

    private static final String ERROR_INTERRUPTED                = "Voter evaluation interrupted.";
    private static final String ERROR_VOTER_PRODUCED_NO_DECISION = "Voter produced no decision.";

    private final PdpVoterSource                      pdpConfigurationSource;
    private final AttributeStore                      attributeStore;
    private final IdFactory                           idFactory;
    private final Clock                               clock;
    private final List<DecisionInterceptor>           decisionInterceptors;
    private final List<SubscriptionLifecycleListener> lifecycleListeners;
    private final boolean                             hasDecisionInterceptors;

    public ReactivePolicyDecisionPoint(PdpVoterSource pdpConfigurationSource,
            AttributeStore attributeStore,
            IdFactory idFactory) {
        this(pdpConfigurationSource, attributeStore, idFactory, Clock.systemUTC(), List.of(), List.of());
    }

    public ReactivePolicyDecisionPoint(PdpVoterSource pdpConfigurationSource,
            AttributeStore attributeStore,
            IdFactory idFactory,
            List<DecisionInterceptor> decisionInterceptors,
            List<SubscriptionLifecycleListener> lifecycleListeners) {
        this(pdpConfigurationSource, attributeStore, idFactory, Clock.systemUTC(), decisionInterceptors,
                lifecycleListeners);
    }

    public ReactivePolicyDecisionPoint(PdpVoterSource pdpConfigurationSource,
            AttributeStore attributeStore,
            IdFactory idFactory,
            Clock clock,
            List<DecisionInterceptor> decisionInterceptors,
            List<SubscriptionLifecycleListener> lifecycleListeners) {
        this.pdpConfigurationSource  = pdpConfigurationSource;
        this.attributeStore          = attributeStore;
        this.idFactory               = idFactory;
        this.clock                   = clock;
        this.decisionInterceptors    = List.copyOf(decisionInterceptors);
        this.lifecycleListeners      = List.copyOf(lifecycleListeners);
        this.hasDecisionInterceptors = !this.decisionInterceptors.isEmpty();
    }

    @Override
    public Flux<AuthorizationDecision> decide(AuthorizationSubscription sub, String pdpId) {
        return Flux.defer(() -> {
            val                         subscriptionId = idFactory.newRandom();
            Flux<AuthorizationDecision> decisions      = configurationStream(pdpId).switchMap(maybePdp -> {
                                                           if (hasDecisionInterceptors) {
                                                               Flux<TracedVote> tracedVotes = maybePdp
                                                                       .map(pdp -> evaluateAsTracedFlux(pdp, sub,
                                                                               subscriptionId))
                                                                       .orElseGet(() -> Flux.just(
                                                                               tracedNoConfiguration(pdpId, clock)));
                                                               return tracedVotes.map(tv -> {
                                                                                                              dispatchDecisionObservers(
                                                                                                                      decisionInterceptors,
                                                                                                                      tv,
                                                                                                                      tv.timestamp(),
                                                                                                                      subscriptionId,
                                                                                                                      sub);
                                                                                                              return tv
                                                                                                                      .authorizationDecision();
                                                                                                          });
                                                           }
                                                           return maybePdp
                                                                   .map(pdp -> evaluateAsFlux(pdp, sub, subscriptionId))
                                                                   .orElseGet(
                                                                           () -> Flux.just(noConfigurationVote(pdpId)))
                                                                   .map(Vote::authorizationDecision);
                                                       });
            return decisions.doOnSubscribe(s -> notifyOnSubscribe(lifecycleListeners, subscriptionId, sub, pdpId))
                    .doFinally(signal -> notifyOnUnsubscribe(lifecycleListeners, subscriptionId));
        }).distinctUntilChanged();
    }

    @Override
    public Mono<AuthorizationDecision> decideOnce(AuthorizationSubscription sub, String pdpId) {
        return Mono.defer(() -> {
            val                         subscriptionId = idFactory.newRandom();
            Mono<AuthorizationDecision> decision       = hasDecisionInterceptors
                    ? computeTracedVoteMono(sub, subscriptionId, pdpId).map(tv -> {
                                                                   dispatchDecisionObservers(decisionInterceptors, tv,
                                                                           tv.timestamp(), subscriptionId, sub);
                                                                   return tv.authorizationDecision();
                                                               })
                    : computeVoteMono(sub, subscriptionId, pdpId).map(Vote::authorizationDecision);
            return decision.doOnSubscribe(s -> notifyOnSubscribe(lifecycleListeners, subscriptionId, sub, pdpId))
                    .doFinally(signal -> notifyOnUnsubscribe(lifecycleListeners, subscriptionId));
        });
    }

    @Override
    public AuthorizationDecision decideOnceBlocking(AuthorizationSubscription sub, String pdpId) {
        val subscriptionId = idFactory.newRandom();
        notifyOnSubscribe(lifecycleListeners, subscriptionId, sub, pdpId);
        try {
            if (hasDecisionInterceptors) {
                val tv = computeTracedVoteSync(sub, subscriptionId, pdpId);
                dispatchDecisionObservers(decisionInterceptors, tv, tv.timestamp(), subscriptionId, sub);
                return tv.authorizationDecision();
            }
            return computeVoteSync(sub, subscriptionId, pdpId).authorizationDecision();
        } finally {
            notifyOnUnsubscribe(lifecycleListeners, subscriptionId);
        }
    }

    /**
     * Public stream of {@link TracedVote} instances for a subscription.
     * Always uses the traced pipeline regardless of whether interceptors
     * are registered, so callers that explicitly want emit timestamps
     * and attribute trace get them. Used by tooling (playground,
     * tests).
     */
    public Flux<TracedVote> gatherVotes(AuthorizationSubscription sub, String pdpId) {
        return Flux.defer(() -> computeTracedVoteFlux(sub, idFactory.newRandom(), pdpId));
    }

    private Mono<Vote> computeVoteMono(AuthorizationSubscription sub, String subscriptionId, String pdpId) {
        val pdpConfiguration = pdpConfigurationSource.getCurrentConfiguration(pdpId);
        return pdpConfiguration.map(pdp -> evaluateAsMono(pdp, sub, subscriptionId))
                .orElseGet(() -> Mono.just(noConfigurationVote(pdpId)));
    }

    private Vote computeVoteSync(AuthorizationSubscription sub, String subscriptionId, String pdpId) {
        val pdpConfiguration = pdpConfigurationSource.getCurrentConfiguration(pdpId);
        if (pdpConfiguration.isEmpty()) {
            return noConfigurationVote(pdpId);
        }
        return evaluateOnceSync(pdpConfiguration.get(), sub, subscriptionId);
    }

    private Flux<TracedVote> computeTracedVoteFlux(AuthorizationSubscription sub, String subscriptionId, String pdpId) {
        val pdpConfiguration = pdpConfigurationSource.getCurrentConfiguration(pdpId);
        return pdpConfiguration.map(pdp -> evaluateAsTracedFlux(pdp, sub, subscriptionId))
                .orElseGet(() -> Flux.just(tracedNoConfiguration(pdpId, clock)));
    }

    private Mono<TracedVote> computeTracedVoteMono(AuthorizationSubscription sub, String subscriptionId, String pdpId) {
        val pdpConfiguration = pdpConfigurationSource.getCurrentConfiguration(pdpId);
        return pdpConfiguration.map(pdp -> evaluateAsTracedMono(pdp, sub, subscriptionId))
                .orElseGet(() -> Mono.just(tracedNoConfiguration(pdpId, clock)));
    }

    private TracedVote computeTracedVoteSync(AuthorizationSubscription sub, String subscriptionId, String pdpId) {
        val pdpConfiguration = pdpConfigurationSource.getCurrentConfiguration(pdpId);
        return pdpConfiguration.map(pdp -> evaluateOnceTracedSync(pdp, sub, subscriptionId))
                .orElseGet(() -> tracedNoConfiguration(pdpId, clock));
    }

    private Vote evaluateOnceSync(CompiledPdp pdp, AuthorizationSubscription sub, String subscriptionId) {
        val baseCtx = evaluationContext(pdp, sub, subscriptionId);
        return switch (pdp.voter()) {
        case Vote v        -> v;
        case PureVoter p   -> p.vote(baseCtx);
        case StreamVoter s -> evaluateStreamingSync(pdp, baseCtx, subscriptionId, s);
        };
    }

    private Vote evaluateStreamingSync(CompiledPdp pdp, EvaluationContext baseCtx, String subscriptionId,
            StreamVoter voter) {
        val initial = voter.evaluate(baseCtx);
        if (initial.dependencies().isEmpty()) {
            return initial.vote() == null ? errorVote(pdp, ERROR_VOTER_PRODUCED_NO_DECISION) : initial.vote();
        }
        if (initial.vote() != null) {
            return initial.vote();
        }
        try {
            return Voters.awaitFirstVote(attributeStore, subscriptionId, initial.dependencies().keySet(),
                    snapshot -> voter.evaluate(baseCtx.withSnapshot(snapshot)));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return errorVote(pdp, ERROR_INTERRUPTED);
        }
    }

    private Mono<Vote> evaluateAsMono(CompiledPdp pdp, AuthorizationSubscription sub, String subscriptionId) {
        val baseCtx = evaluationContext(pdp, sub, subscriptionId);
        return switch (pdp.voter()) {
        case Vote v        -> Mono.just(v);
        case PureVoter p   -> Mono.fromSupplier(() -> p.vote(baseCtx));
        case StreamVoter s -> evaluateStreamingAsMono(pdp, baseCtx, subscriptionId, s);
        };
    }

    private Mono<Vote> evaluateStreamingAsMono(CompiledPdp pdp, EvaluationContext baseCtx, String subscriptionId,
            StreamVoter voter) {
        return Mono.fromSupplier(() -> voter.evaluate(baseCtx)).flatMap(initial -> {
            if (initial.dependencies().isEmpty()) {
                return Mono.just(
                        initial.vote() == null ? errorVote(pdp, ERROR_VOTER_PRODUCED_NO_DECISION) : initial.vote());
            }
            if (initial.vote() != null) {
                return Mono.just(initial.vote());
            }
            return firstVote(attributeStore, subscriptionId, initial.dependencies().keySet(),
                    snapshot -> voter.evaluate(baseCtx.withSnapshot(snapshot)));
        });
    }

    private Flux<Vote> evaluateAsFlux(CompiledPdp pdp, AuthorizationSubscription sub, String subscriptionId) {
        val baseCtx = evaluationContext(pdp, sub, subscriptionId);
        return switch (pdp.voter()) {
        case Vote v        -> Flux.just(v);
        case PureVoter p   -> Mono.fromSupplier(() -> p.vote(baseCtx)).flux();
        case StreamVoter s -> evaluateStreamingAsFlux(pdp, baseCtx, subscriptionId, s);
        };
    }

    private Flux<Vote> evaluateStreamingAsFlux(CompiledPdp pdp, EvaluationContext baseCtx, String subscriptionId,
            StreamVoter voter) {
        return Flux.defer(() -> {
            val initial = voter.evaluate(baseCtx);
            if (initial.dependencies().isEmpty()) {
                return Flux.just(
                        initial.vote() == null ? errorVote(pdp, ERROR_VOTER_PRODUCED_NO_DECISION) : initial.vote());
            }
            val initialEmission = initial.vote() == null ? Flux.<Vote>empty() : Flux.just(initial.vote());
            val streamEmissions = streamVotes(attributeStore, subscriptionId, initial.dependencies().keySet(),
                    snapshot -> voter.evaluate(baseCtx.withSnapshot(snapshot)));
            return Flux.concat(initialEmission, streamEmissions);
        });
    }

    private TracedVote evaluateOnceTracedSync(CompiledPdp pdp, AuthorizationSubscription sub, String subscriptionId) {
        val baseCtx = evaluationContext(pdp, sub, subscriptionId);
        return switch (pdp.voter()) {
        case Vote v        -> TracedVote.of(v, clock.instant());
        case PureVoter p   -> TracedVote.of(p.vote(baseCtx), clock.instant());
        case StreamVoter s -> evaluateStreamingTracedSync(pdp, baseCtx, subscriptionId, s);
        };
    }

    private TracedVote evaluateStreamingTracedSync(CompiledPdp pdp, EvaluationContext baseCtx, String subscriptionId,
            StreamVoter voter) {
        val initial = voter.evaluate(baseCtx);
        if (initial.dependencies().isEmpty()) {
            return TracedVote.of(
                    initial.vote() == null ? errorVote(pdp, ERROR_VOTER_PRODUCED_NO_DECISION) : initial.vote(),
                    clock.instant());
        }
        if (initial.vote() != null) {
            return TracedVote.of(initial.vote(), clock.instant());
        }
        try {
            return awaitFirstTracedVoteSync(subscriptionId, initial.dependencies().keySet(),
                    snapshot -> voter.evaluate(baseCtx.withSnapshot(snapshot)));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return TracedVote.of(errorVote(pdp, ERROR_INTERRUPTED), clock.instant());
        }
    }

    private TracedVote awaitFirstTracedVoteSync(String subscriptionId, Set<SubscriptionKey> initialDependencies,
            Function<Map<SubscriptionKey, AttributeSnapshot>, VoteResult> evaluator) throws InterruptedException {
        val future = new CompletableFuture<TracedVote>();
        try (val ignored = attributeStore.open(subscriptionId, initialDependencies, snapshot -> {
            val r = evaluator.apply(snapshot);
            if (r.vote() != null) {
                future.complete(buildTracedVote(r, snapshot, clock));
            }
            return r.dependencies().keySet();
        })) {
            return future.get();
        } catch (ExecutionException ee) {
            val cause = ee.getCause();
            throw new IllegalStateException(cause == null ? ee.toString() : cause.toString(), ee);
        }
    }

    private Mono<TracedVote> evaluateAsTracedMono(CompiledPdp pdp, AuthorizationSubscription sub,
            String subscriptionId) {
        val baseCtx = evaluationContext(pdp, sub, subscriptionId);
        return switch (pdp.voter()) {
        case Vote v        -> Mono.just(TracedVote.of(v, clock.instant()));
        case PureVoter p   -> Mono.fromSupplier(() -> TracedVote.of(p.vote(baseCtx), clock.instant()));
        case StreamVoter s -> evaluateStreamingAsTracedMono(pdp, baseCtx, subscriptionId, s);
        };
    }

    private Mono<TracedVote> evaluateStreamingAsTracedMono(CompiledPdp pdp, EvaluationContext baseCtx,
            String subscriptionId, StreamVoter voter) {
        return Mono.fromSupplier(() -> voter.evaluate(baseCtx)).flatMap(initial -> {
            if (initial.dependencies().isEmpty()) {
                return Mono.just(TracedVote.of(
                        initial.vote() == null ? errorVote(pdp, ERROR_VOTER_PRODUCED_NO_DECISION) : initial.vote(),
                        clock.instant()));
            }
            if (initial.vote() != null) {
                return Mono.just(TracedVote.of(initial.vote(), clock.instant()));
            }
            return firstTracedVote(attributeStore, subscriptionId, initial.dependencies().keySet(),
                    snapshot -> voter.evaluate(baseCtx.withSnapshot(snapshot)));
        });
    }

    private Flux<TracedVote> evaluateAsTracedFlux(CompiledPdp pdp, AuthorizationSubscription sub,
            String subscriptionId) {
        val baseCtx = evaluationContext(pdp, sub, subscriptionId);
        return switch (pdp.voter()) {
        case Vote v        -> Flux.just(TracedVote.of(v, clock.instant()));
        case PureVoter p   -> Mono.fromSupplier(() -> TracedVote.of(p.vote(baseCtx), clock.instant())).flux();
        case StreamVoter s -> evaluateStreamingAsTracedFlux(pdp, baseCtx, subscriptionId, s);
        };
    }

    private Flux<TracedVote> evaluateStreamingAsTracedFlux(CompiledPdp pdp, EvaluationContext baseCtx,
            String subscriptionId, StreamVoter voter) {
        return Flux.defer(() -> {
            val initial = voter.evaluate(baseCtx);
            if (initial.dependencies().isEmpty()) {
                return Flux.just(TracedVote.of(
                        initial.vote() == null ? errorVote(pdp, ERROR_VOTER_PRODUCED_NO_DECISION) : initial.vote(),
                        clock.instant()));
            }
            val initialEmission = initial.vote() == null ? Flux.<TracedVote>empty()
                    : Flux.just(TracedVote.of(initial.vote(), clock.instant()));
            val streamEmissions = streamTracedVotes(attributeStore, subscriptionId, initial.dependencies().keySet(),
                    snapshot -> voter.evaluate(baseCtx.withSnapshot(snapshot)));
            return Flux.concat(initialEmission, streamEmissions);
        });
    }

    @Override
    public Flux<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiSubscription,
            String pdpId) {
        if (!multiSubscription.hasSubscriptions()) {
            return Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE);
        }
        val previous = new AtomicReference<MultiAuthorizationDecision>();
        return decideAll(multiSubscription, pdpId)
                .flatMapIterable(current -> identifiableChanges(previous.getAndSet(current), current));
    }

    @Override
    public Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiSubscription, String pdpId) {
        if (!multiSubscription.hasSubscriptions()) {
            return Flux.just(MultiAuthorizationDecision.indeterminate());
        }
        return Flux.defer(() -> {
            val subscriptionId = idFactory.newRandom();
            return configurationStream(pdpId)
                    .switchMap(maybePdp -> maybePdp.map(pdp -> multiVoteFlux(multiSubscription, pdp, subscriptionId))
                            .orElseGet(() -> Flux.just(MultiAuthorizationDecision.indeterminate())));
        }).distinctUntilChanged();
    }

    /**
     * Hot stream of compiled-PDP states for {@code pdpId}: emits the
     * current snapshot immediately, then one item on every Load or Remove
     * event. Never completes; subscribers stay registered until the
     * downstream cancels. Consecutive identical states are deduplicated.
     */
    private Flux<Optional<CompiledPdp>> configurationStream(String pdpId) {
        return Flux.<Optional<CompiledPdp>>create(sink -> {
            Consumer<PdpUpdateEvent> listener = event -> {
                switch (event) {
                case PdpUpdateEvent.Voter(var ignoredId, var voter) -> sink.next(Optional.of(voter));
                case PdpUpdateEvent.Removed(var ignoredId)          -> sink.next(Optional.empty());
                }
            };
            pdpConfigurationSource.subscribeToUpdates(pdpId, listener);
            sink.next(pdpConfigurationSource.getCurrentConfiguration(pdpId));
            sink.onCancel(() -> pdpConfigurationSource.unsubscribeFromUpdates(pdpId, listener));
        }).distinctUntilChanged();
    }

    /**
     * Shared-subscription multi-vote loop. One {@link AttributeStore}
     * handle drives every sub-evaluation against the same snapshot, so
     * combination happens before emission and a
     * {@link MultiAuthorizationDecision} can never reflect a partial
     * update across subs. Emission is gated on every sub having a
     * non-null vote in the current round; rounds where any sub fails
     * to produce a vote are silently skipped.
     * <p>
     * When interceptors are registered, each sub-evaluation fires its
     * own interceptor invocation inline within the round, with the
     * per-sub {@link TracedVote} (vote, emit timestamp, attribute
     * trace).
     */
    private Flux<MultiAuthorizationDecision> multiVoteFlux(MultiAuthorizationSubscription multiSubscription,
            CompiledPdp pdp, String subscriptionId) {
        val items = new ArrayList<IdentifiableAuthorizationSubscription>();
        for (val item : multiSubscription) {
            items.add(item);
        }
        return Flux.create(sink -> {
            val initialDeps = new HashSet<SubscriptionKey>();
            val initial     = evaluateRound(items, pdp, subscriptionId, Map.of(), initialDeps);
            if (initial != null) {
                sink.next(initial);
            }
            if (initialDeps.isEmpty()) {
                sink.complete();
                return;
            }
            val handle = attributeStore.open(subscriptionId, initialDeps, snapshot -> {
                val newDeps = new HashSet<SubscriptionKey>();
                val multi   = evaluateRound(items, pdp, subscriptionId, snapshot, newDeps);
                if (multi != null) {
                    sink.next(multi);
                }
                return newDeps.isEmpty() ? initialDeps : newDeps;
            });
            sink.onDispose(handle::close);
        }, FluxSink.OverflowStrategy.LATEST);
    }

    private MultiAuthorizationDecision evaluateRound(List<IdentifiableAuthorizationSubscription> items, CompiledPdp pdp,
            String subscriptionId, Map<SubscriptionKey, AttributeSnapshot> snapshot,
            Set<SubscriptionKey> depsAccumulator) {
        return BlockingPolicyDecisionPoint.evaluateRound(items, pdp, subscriptionId, snapshot, depsAccumulator,
                pdpConfigurationSource.getFunctionBroker(), clock,
                hasDecisionInterceptors ? this::observePerSubTrace : null);
    }

    private void observePerSubTrace(TracedVote perSubTraced, IdentifiableAuthorizationSubscription item) {
        dispatchDecisionObservers(decisionInterceptors, perSubTraced, perSubTraced.timestamp(), item.subscriptionId(),
                item.subscription());
    }

    private static List<IdentifiableAuthorizationDecision> identifiableChanges(MultiAuthorizationDecision previous,
            MultiAuthorizationDecision current) {
        val changes = new ArrayList<IdentifiableAuthorizationDecision>();
        for (val identifiable : current) {
            val prevDecision = previous == null ? null : previous.getDecision(identifiable.subscriptionId());
            if (!Objects.equals(prevDecision, identifiable.decision())) {
                changes.add(identifiable);
            }
        }
        return changes;
    }

    private EvaluationContext evaluationContext(CompiledPdp pdp, AuthorizationSubscription sub, String subscriptionId) {
        return BlockingPolicyDecisionPoint.evaluationContext(pdp, sub, subscriptionId,
                pdpConfigurationSource.getFunctionBroker());
    }

    private static Mono<Vote> firstVote(AttributeStore store, String subscriptionId,
            Set<SubscriptionKey> initialDependencies,
            Function<Map<SubscriptionKey, AttributeSnapshot>, VoteResult> evaluator) {
        return Mono.create(sink -> {
            val handle = store.open(subscriptionId, initialDependencies, snapshot -> {
                val r = evaluator.apply(snapshot);
                if (r.vote() != null) {
                    sink.success(r.vote());
                }
                return r.dependencies().keySet();
            });
            sink.onDispose(handle::close);
        });
    }

    private static Flux<Vote> streamVotes(AttributeStore store, String subscriptionId,
            Set<SubscriptionKey> initialDependencies,
            Function<Map<SubscriptionKey, AttributeSnapshot>, VoteResult> evaluator) {
        return Flux.create(sink -> {
            val handle = store.open(subscriptionId, initialDependencies, snapshot -> {
                val r = evaluator.apply(snapshot);
                if (r.vote() != null) {
                    sink.next(r.vote());
                }
                return r.dependencies().keySet();
            });
            sink.onDispose(handle::close);
        }, FluxSink.OverflowStrategy.LATEST);
    }

    private Mono<TracedVote> firstTracedVote(AttributeStore store, String subscriptionId,
            Set<SubscriptionKey> initialDependencies,
            Function<Map<SubscriptionKey, AttributeSnapshot>, VoteResult> evaluator) {
        return Mono.create(sink -> {
            val handle = store.open(subscriptionId, initialDependencies, snapshot -> {
                val r = evaluator.apply(snapshot);
                if (r.vote() != null) {
                    sink.success(buildTracedVote(r, snapshot, clock));
                }
                return r.dependencies().keySet();
            });
            sink.onDispose(handle::close);
        });
    }

    private Flux<TracedVote> streamTracedVotes(AttributeStore store, String subscriptionId,
            Set<SubscriptionKey> initialDependencies,
            Function<Map<SubscriptionKey, AttributeSnapshot>, VoteResult> evaluator) {
        return Flux.create(sink -> {
            val handle = store.open(subscriptionId, initialDependencies, snapshot -> {
                val r = evaluator.apply(snapshot);
                if (r.vote() != null) {
                    sink.next(buildTracedVote(r, snapshot, clock));
                }
                return r.dependencies().keySet();
            });
            sink.onDispose(handle::close);
        }, FluxSink.OverflowStrategy.LATEST);
    }
}
