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
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.ast.Outcome;
import io.sapl.compiler.document.*;
import io.sapl.compiler.eval.AttributeStore;
import io.sapl.compiler.pdp.CompiledPdp;
import io.sapl.compiler.pdp.PdpVoterMetadata;
import io.sapl.pdp.IdFactory;
import io.sapl.pdp.VoteInterceptor;
import io.sapl.pdp.configuration.PdpVoterSource;
import io.sapl.reactive.api.pdp.MultiTenantPolicyDecisionPoint;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class ReactivePolicyDecisionPoint implements MultiTenantPolicyDecisionPoint {

    private static final String ERROR_INTERRUPTED                = "Voter evaluation interrupted.";
    private static final String ERROR_NO_PDP_CONFIGURATION       = "No PDP configuration found.";
    private static final String ERROR_VOTER_PRODUCED_NO_DECISION = "Voter produced no decision.";

    private final PdpVoterSource        pdpConfigurationSource;
    private final AttributeStore        attributeStore;
    private final IdFactory             idFactory;
    private final Clock                 clock;
    private final List<VoteInterceptor> interceptors;

    /**
     * Creates a PDP with no interceptors and the system UTC clock.
     *
     * @param pdpConfigurationSource the source of PDP configurations
     * @param idFactory factory for generating subscription IDs
     */
    public ReactivePolicyDecisionPoint(PdpVoterSource pdpConfigurationSource,
            AttributeStore attributeStore,
            IdFactory idFactory) {
        this(pdpConfigurationSource, attributeStore, idFactory, Clock.systemUTC(), List.of());
    }

    /**
     * Creates a PDP with vote interceptors and the system UTC clock.
     *
     * @param pdpConfigurationSource the source of PDP configurations
     * @param idFactory factory for generating subscription IDs
     * @param interceptors interceptors invoked on each vote, sorted by priority
     */
    public ReactivePolicyDecisionPoint(PdpVoterSource pdpConfigurationSource,
            AttributeStore attributeStore,
            IdFactory idFactory,
            List<VoteInterceptor> interceptors) {
        this(pdpConfigurationSource, attributeStore, idFactory, Clock.systemUTC(), interceptors);
    }

    /**
     * Creates a PDP with vote interceptors and an explicit clock for
     * timestamping {@link TimestampedVote}s.
     *
     * @param pdpConfigurationSource the source of PDP configurations
     * @param idFactory factory for generating subscription IDs
     * @param clock clock used to timestamp emitted votes
     * @param interceptors interceptors invoked on each vote, sorted by priority
     */
    public ReactivePolicyDecisionPoint(PdpVoterSource pdpConfigurationSource,
            AttributeStore attributeStore,
            IdFactory idFactory,
            Clock clock,
            List<VoteInterceptor> interceptors) {
        this.pdpConfigurationSource = pdpConfigurationSource;
        this.attributeStore         = attributeStore;
        this.idFactory              = idFactory;
        this.clock                  = clock;
        this.interceptors           = interceptors.stream().sorted().toList();
    }

    @Override
    public Flux<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription, String pdpId) {
        return Flux.defer(() -> {
            val subscriptionId   = idFactory.newRandom();
            val pdpConfiguration = pdpConfigurationSource.getCurrentConfiguration(pdpId);
            val voteFlux         = pdpConfiguration
                    .map(pdp -> fluxEvaluate(pdp, authorizationSubscription, subscriptionId))
                    .orElseGet(() -> Flux.just(noConfigurationVote(pdpId)));
            return voteFlux.doOnSubscribe(s -> invokeOnSubscribe(subscriptionId, authorizationSubscription))
                    .doOnNext(tv -> invokeInterceptors(tv, subscriptionId, authorizationSubscription))
                    .doFinally(signal -> invokeOnUnsubscribe(subscriptionId));
        }).map(tv -> tv.vote().authorizationDecision()).distinctUntilChanged();
    }

    private Flux<TimestampedVote> fluxEvaluate(CompiledPdp pdp, AuthorizationSubscription sub, String subscriptionId) {
        val baseCtx = evaluationContext(pdp, sub, subscriptionId);
        return switch (pdp.voter()) {
        case Vote v        -> Flux.just(stamp(v));
        case PureVoter p   -> Mono.fromSupplier(() -> stamp(p.vote(baseCtx))).flux();
        case StreamVoter s -> fluxEvaluateStreaming(pdp, baseCtx, subscriptionId, s);
        };
    }

    private Flux<TimestampedVote> fluxEvaluateStreaming(CompiledPdp pdp, EvaluationContext baseCtx,
            String subscriptionId, StreamVoter voter) {
        return Flux.defer(() -> {
            val initial = voter.evaluate(baseCtx);
            if (initial.dependencies().isEmpty()) {
                return Flux.just(initial.vote() == null ? errorVote(pdp, ERROR_VOTER_PRODUCED_NO_DECISION)
                        : stamp(initial.vote()));
            }
            val initialEmission = initial.vote() == null ? Flux.<TimestampedVote>empty()
                    : Flux.just(stamp(initial.vote()));
            val streamEmissions = streamVotes(attributeStore, subscriptionId, initial.dependencies().keySet(),
                    snapshot -> voter.evaluate(baseCtx.withSnapshot(snapshot))).map(this::stamp);
            return Flux.concat(initialEmission, streamEmissions);
        });
    }

    @Override
    public AuthorizationDecision decideOnceBlocking(AuthorizationSubscription authorizationSubscription, String pdpId) {
        val subscriptionId = idFactory.newRandom();
        invokeOnSubscribe(subscriptionId, authorizationSubscription);
        try {
            val pdpConfiguration = pdpConfigurationSource.getCurrentConfiguration(pdpId);
            if (pdpConfiguration.isEmpty()) {
                return noConfigurationVote(pdpId).vote().authorizationDecision();
            }
            val timestampedVote = evaluateOnce(pdpConfiguration.get(), authorizationSubscription, subscriptionId);
            invokeInterceptors(timestampedVote, subscriptionId, authorizationSubscription);
            return timestampedVote.vote().authorizationDecision();
        } finally {
            invokeOnUnsubscribe(subscriptionId);
        }
    }

    @Override
    public Mono<AuthorizationDecision> decideOnce(AuthorizationSubscription authorizationSubscription, String pdpId) {
        return Mono.defer(() -> {
            val subscriptionId   = idFactory.newRandom();
            val pdpConfiguration = pdpConfigurationSource.getCurrentConfiguration(pdpId);
            val voteMono         = pdpConfiguration
                    .map(pdp -> monoEvaluateOnce(pdp, authorizationSubscription, subscriptionId))
                    .orElseGet(() -> Mono.just(noConfigurationVote(pdpId)));
            return voteMono.doOnSubscribe(s -> invokeOnSubscribe(subscriptionId, authorizationSubscription))
                    .doOnNext(tv -> invokeInterceptors(tv, subscriptionId, authorizationSubscription))
                    .doFinally(signal -> invokeOnUnsubscribe(subscriptionId));
        }).map(tv -> tv.vote().authorizationDecision());
    }

    private Mono<TimestampedVote> monoEvaluateOnce(CompiledPdp pdp, AuthorizationSubscription sub,
            String subscriptionId) {
        val baseCtx = evaluationContext(pdp, sub, subscriptionId);
        return switch (pdp.voter()) {
        case Vote v        -> Mono.just(stamp(v));
        case PureVoter p   -> Mono.fromSupplier(() -> stamp(p.vote(baseCtx)));
        case StreamVoter s -> monoEvaluateStreamingOnce(pdp, baseCtx, subscriptionId, s);
        };
    }

    private Mono<TimestampedVote> monoEvaluateStreamingOnce(CompiledPdp pdp, EvaluationContext baseCtx,
            String subscriptionId, StreamVoter voter) {
        return Mono.fromSupplier(() -> voter.evaluate(baseCtx)).flatMap(initial -> {
            if (initial.dependencies().isEmpty()) {
                return Mono.just(initial.vote() == null ? errorVote(pdp, ERROR_VOTER_PRODUCED_NO_DECISION)
                        : stamp(initial.vote()));
            }
            if (initial.vote() != null) {
                return Mono.just(stamp(initial.vote()));
            }
            return firstVote(attributeStore, subscriptionId, initial.dependencies().keySet(),
                    snapshot -> voter.evaluate(baseCtx.withSnapshot(snapshot))).map(this::stamp);
        });
    }

    /**
     * One-shot evaluation of the compiled PDP. Dispatches by voter shape:
     * a constant {@link Vote} returns immediately, a {@link PureVoter}
     * evaluates synchronously against the context, a {@link StreamVoter}
     * subscribes its declared dependencies on the {@link AttributeStore}
     * and blocks on the first complete snapshot.
     *
     * @param pdp the compiled PDP whose voter is to be evaluated
     * @param sub the authorization subscription
     * @param subscriptionId the per-evaluation subscription id used by the
     * attribute store for telemetry and de-duplication
     * @return the timestamped vote
     */
    private TimestampedVote evaluateOnce(CompiledPdp pdp, AuthorizationSubscription sub, String subscriptionId) {
        val baseCtx = evaluationContext(pdp, sub, subscriptionId);
        return switch (pdp.voter()) {
        case Vote v        -> stamp(v);
        case PureVoter p   -> stamp(p.vote(baseCtx));
        case StreamVoter s -> evaluateStreamingOnce(pdp, baseCtx, subscriptionId, s);
        };
    }

    /**
     * Bridges the callback-driven {@link AttributeStore} to a synchronous
     * single-vote return. Performs an initial in-memory pass; if that
     * already produces a vote (no streaming dependencies, or all reads
     * resolved without subscribing), returns it directly. Otherwise opens
     * a per-subscription store handle, lets the trigger loop fire as
     * dependencies fulfil, and returns on the first non-null vote. The
     * store handle is released in {@code finally} regardless of outcome
     * so the backing PIP refcount is freed promptly.
     */
    private TimestampedVote evaluateStreamingOnce(CompiledPdp pdp, EvaluationContext baseCtx, String subscriptionId,
            StreamVoter voter) {
        val initial = voter.evaluate(baseCtx);
        if (initial.dependencies().isEmpty()) {
            return initial.vote() == null ? errorVote(pdp, ERROR_VOTER_PRODUCED_NO_DECISION) : stamp(initial.vote());
        }
        if (initial.vote() != null) {
            return stamp(initial.vote());
        }
        try {
            return stamp(Voters.awaitFirstVote(attributeStore, subscriptionId, initial.dependencies().keySet(),
                    snapshot -> voter.evaluate(baseCtx.withSnapshot(snapshot))));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return errorVote(pdp, ERROR_INTERRUPTED);
        }
    }

    private TimestampedVote stamp(Vote vote) {
        return new TimestampedVote(vote, clock.instant().toString());
    }

    private TimestampedVote errorVote(CompiledPdp pdp, String message) {
        return stamp(Vote.error(new ErrorValue(message), pdp.metadata()));
    }

    /**
     * Gathers authorization votes for a subscription as a reactive stream.
     * Returns INDETERMINATE if no configuration is loaded.
     *
     * @param authorizationSubscription the authorization subscription to evaluate
     * @param pdpId the PDP identifier for tenant routing
     * @return a flux of timestamped votes
     */
    public Flux<TimestampedVote> gatherVotes(AuthorizationSubscription authorizationSubscription, String pdpId) {
        return Flux.defer(() -> {
            val subscriptionId   = idFactory.newRandom();
            val pdpConfiguration = pdpConfigurationSource.getCurrentConfiguration(pdpId);
            val voteFlux         = pdpConfiguration
                    .map(pdp -> fluxEvaluate(pdp, authorizationSubscription, subscriptionId))
                    .orElseGet(() -> Flux.just(noConfigurationVote(pdpId)));
            return voteFlux.doOnSubscribe(s -> invokeOnSubscribe(subscriptionId, authorizationSubscription))
                    .doOnNext(tv -> invokeInterceptors(tv, subscriptionId, authorizationSubscription))
                    .doFinally(signal -> invokeOnUnsubscribe(subscriptionId));
        });
    }

    private EvaluationContext evaluationContext(CompiledPdp pdp, AuthorizationSubscription sub, String subscriptionId) {
        return EvaluationContext.of(pdp.metadata().pdpId(), pdp.metadata().configurationId(), subscriptionId, sub,
                pdpConfigurationSource.getFunctionBroker());
    }

    private TimestampedVote noConfigurationVote(String pdpId) {
        val metadata = new PdpVoterMetadata("no-configuration", pdpId, "none", null, Outcome.PERMIT_OR_DENY, false);
        val error    = new ErrorValue(ERROR_NO_PDP_CONFIGURATION);
        val vote     = Vote.error(error, metadata);
        return new TimestampedVote(vote, clock.instant().toString());
    }

    private void invokeOnSubscribe(String subscriptionId, AuthorizationSubscription authorizationSubscription) {
        for (val interceptor : interceptors) {
            interceptor.onSubscribe(subscriptionId, authorizationSubscription);
        }
    }

    private void invokeOnUnsubscribe(String subscriptionId) {
        for (val interceptor : interceptors) {
            interceptor.onUnsubscribe(subscriptionId);
        }
    }

    private void invokeInterceptors(TimestampedVote vote, String subscriptionId,
            AuthorizationSubscription authorizationSubscription) {
        for (val interceptor : interceptors) {
            interceptor.intercept(vote, subscriptionId, authorizationSubscription);
        }
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

    @Override
    public Flux<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription) {
        return Flux.deferContextual(ctx -> decide(authorizationSubscription, pdpIdFromContext(ctx)));
    }

    @Override
    public Mono<AuthorizationDecision> decideOnce(AuthorizationSubscription authorizationSubscription) {
        return Mono.deferContextual(ctx -> decideOnce(authorizationSubscription, pdpIdFromContext(ctx)));
    }

    @Override
    public AuthorizationDecision decideOnceBlocking(AuthorizationSubscription authorizationSubscription) {
        return decideOnceBlocking(authorizationSubscription, DEFAULT_PDP_ID);
    }

    @Override
    public Flux<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiSubscription) {
        return Flux.deferContextual(ctx -> decide(multiSubscription, pdpIdFromContext(ctx)));
    }

    @Override
    public Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiSubscription) {
        return Flux.deferContextual(ctx -> decideAll(multiSubscription, pdpIdFromContext(ctx)));
    }

    @Override
    public Flux<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiSubscription,
            String pdpId) {
        if (!multiSubscription.hasSubscriptions()) {
            return Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE);
        }
        return Flux.merge(identifiableDecisionFluxes(multiSubscription, pdpId));
    }

    @Override
    public Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiSubscription, String pdpId) {
        if (!multiSubscription.hasSubscriptions()) {
            return Flux.just(MultiAuthorizationDecision.indeterminate());
        }
        // TODO: implement glitch-free combination. Flux.combineLatest of N
        // independent decision fluxes can emit transient intermediate
        // MultiAuthorizationDecision values when several sub-streams update
        // from the same upstream attribute change. The intended replacement
        // routes all sub-evaluations through one AttributeStore subscription
        // so combination happens before emission, not after.
        return Flux.combineLatest(identifiableDecisionFluxes(multiSubscription, pdpId),
                ReactivePolicyDecisionPoint::collectDecisions);
    }

    private List<Flux<IdentifiableAuthorizationDecision>> identifiableDecisionFluxes(
            MultiAuthorizationSubscription multiSubscription, String pdpId) {
        val fluxes = new ArrayList<Flux<IdentifiableAuthorizationDecision>>();
        for (val identifiableSubscription : multiSubscription) {
            val subscriptionId = identifiableSubscription.subscriptionId();
            val subscription   = identifiableSubscription.subscription();
            fluxes.add(decide(subscription, pdpId)
                    .map(decision -> new IdentifiableAuthorizationDecision(subscriptionId, decision)));
        }
        return fluxes;
    }

    private static MultiAuthorizationDecision collectDecisions(Object[] decisions) {
        val multiDecision = new MultiAuthorizationDecision();
        for (val value : decisions) {
            val identifiable = (IdentifiableAuthorizationDecision) value;
            multiDecision.setDecision(identifiable.subscriptionId(), identifiable.decision());
        }
        return multiDecision;
    }

    private static String pdpIdFromContext(ContextView ctx) {
        return ctx.getOrDefault(REACTOR_CONTEXT_PDP_ID_KEY, DEFAULT_PDP_ID);
    }

}
