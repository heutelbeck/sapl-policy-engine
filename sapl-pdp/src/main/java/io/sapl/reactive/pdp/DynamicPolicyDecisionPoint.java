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

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.ast.Outcome;
import io.sapl.compiler.document.TimestampedVote;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.document.VoteWithCoverage;
import io.sapl.compiler.pdp.CompiledPdp;
import io.sapl.compiler.pdp.PdpVoterMetadata;
import io.sapl.pdp.IdFactory;
import io.sapl.pdp.VoteInterceptor;
import io.sapl.pdp.configuration.PdpUpdateEvent;
import io.sapl.pdp.configuration.PdpVoterSource;
import io.sapl.reactive.api.pdp.MultiTenantPolicyDecisionPoint;
import lombok.val;
import reactor.core.publisher.Flux;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class DynamicPolicyDecisionPoint implements MultiTenantPolicyDecisionPoint {

    public static final String  ERROR_NO_PDP_CONFIGURATION                   = "No PDP configuration found.";
    private static final String ERROR_STREAMING_COVERAGE_NOT_YET_IMPLEMENTED = "PDP-level streaming voteWithCoverage not yet implemented";
    private static final String ERROR_STREAMING_NOT_YET_IMPLEMENTED          = "PDP-level streaming vote not yet implemented";
    private static final String ERROR_VOTER_PRODUCED_NO_DECISION             = "Voter produced no decision.";

    private final PdpVoterSource        pdpConfigurationSource;
    private final IdFactory             idFactory;
    private final Clock                 clock;
    private final List<VoteInterceptor> interceptors;

    /**
     * Creates a PDP with no interceptors and the system UTC clock.
     *
     * @param pdpConfigurationSource the source of PDP configurations
     * @param idFactory factory for generating subscription IDs
     */
    public DynamicPolicyDecisionPoint(PdpVoterSource pdpConfigurationSource, IdFactory idFactory) {
        this(pdpConfigurationSource, idFactory, Clock.systemUTC(), List.of());
    }

    /**
     * Creates a PDP with vote interceptors and the system UTC clock.
     *
     * @param pdpConfigurationSource the source of PDP configurations
     * @param idFactory factory for generating subscription IDs
     * @param interceptors interceptors invoked on each vote, sorted by priority
     */
    public DynamicPolicyDecisionPoint(PdpVoterSource pdpConfigurationSource,
            IdFactory idFactory,
            List<VoteInterceptor> interceptors) {
        this(pdpConfigurationSource, idFactory, Clock.systemUTC(), interceptors);
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
    public DynamicPolicyDecisionPoint(PdpVoterSource pdpConfigurationSource,
            IdFactory idFactory,
            Clock clock,
            List<VoteInterceptor> interceptors) {
        this.pdpConfigurationSource = pdpConfigurationSource;
        this.idFactory              = idFactory;
        this.clock                  = clock;
        this.interceptors           = interceptors.stream().sorted().toList();
    }

    @Override
    public Flux<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription, String pdpId) {
        return gatherVotes(authorizationSubscription, pdpId).map(tv -> tv.vote().authorizationDecision())
                .distinctUntilChanged();
    }

    @Override
    public AuthorizationDecision decideOnceBlocking(AuthorizationSubscription authorizationSubscription, String pdpId) {
        return voteOnce(authorizationSubscription, pdpId).vote().authorizationDecision();
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
        val subscriptionId = idFactory.newRandom();
        return configFlux(pdpId)
                .switchMap(optionalConfig -> optionalConfig
                        .map(config -> evaluateAsFlux(config, authorizationSubscription, subscriptionId, pdpId))
                        .orElseGet(() -> Flux.just(noConfigurationVote(pdpId))))
                .doOnSubscribe(s -> invokeOnSubscribe(subscriptionId, authorizationSubscription))
                .doOnNext(tv -> invokeInterceptors(tv, subscriptionId, authorizationSubscription))
                .doFinally(signal -> invokeOnUnsubscribe(subscriptionId));
    }

    /**
     * Evaluates a subscription once and returns the vote synchronously.
     * Returns an INDETERMINATE vote if no configuration is loaded.
     *
     * @param authorizationSubscription the authorization subscription to evaluate
     * @param pdpId the PDP identifier for tenant routing
     * @return the timestamped vote
     */
    public TimestampedVote voteOnce(AuthorizationSubscription authorizationSubscription, String pdpId) {
        val subscriptionId = idFactory.newRandom();
        invokeOnSubscribe(subscriptionId, authorizationSubscription);
        try {
            val pdpConfiguration = pdpConfigurationSource.getCurrentConfiguration(pdpId);
            if (pdpConfiguration.isEmpty()) {
                return noConfigurationVote(pdpId);
            }
            val timestampedVote = evaluateOnce(pdpConfiguration.get(), authorizationSubscription, subscriptionId);
            invokeInterceptors(timestampedVote, subscriptionId, authorizationSubscription);
            return timestampedVote;
        } finally {
            invokeOnUnsubscribe(subscriptionId);
        }
    }

    /**
     * Streams votes with coverage information for a subscription. Currently
     * unimplemented at PDP level; returns a flux that errors out as soon as
     * a configuration is available. Empty flux if no configuration loaded.
     *
     * @param authorizationSubscription the authorization subscription to evaluate
     * @param pdpId the PDP identifier
     * @return a flux of votes with coverage data
     */
    public Flux<VoteWithCoverage> coverageStream(AuthorizationSubscription authorizationSubscription, String pdpId) {
        return configFlux(pdpId).switchMap(optionalConfig -> optionalConfig
                .map(config -> Flux.<VoteWithCoverage>error(
                        new UnsupportedOperationException(ERROR_STREAMING_COVERAGE_NOT_YET_IMPLEMENTED)))
                .orElseGet(Flux::empty));
    }

    /**
     * One-shot evaluation of the compiled PDP. Builds an
     * {@link EvaluationContext}, evaluates the voter, wraps the result in a
     * {@link TimestampedVote}. Falls back to an INDETERMINATE vote if the
     * voter produced no decision (incomplete snapshot).
     */
    private TimestampedVote evaluateOnce(CompiledPdp pdp, AuthorizationSubscription sub, String subscriptionId) {
        val ctx  = evaluationContext(pdp, sub, subscriptionId);
        val vote = pdp.voter().evaluate(ctx).vote();
        if (vote == null) {
            val error = new ErrorValue(ERROR_VOTER_PRODUCED_NO_DECISION);
            return new TimestampedVote(Vote.error(error, pdp.metadata()), clock.instant().toString());
        }
        return new TimestampedVote(vote, clock.instant().toString());
    }

    /**
     * Streaming evaluation of the compiled PDP. Currently emits a single
     * snapshot vote if it completes synchronously, otherwise errors with
     * the unimplemented marker until the trigger-loop snapshot pipeline
     * lands.
     */
    private Flux<TimestampedVote> evaluateAsFlux(CompiledPdp pdp, AuthorizationSubscription sub, String subscriptionId,
            String pdpId) {
        val ctx  = evaluationContext(pdp, sub, subscriptionId);
        val vote = pdp.voter().evaluate(ctx).vote();
        if (vote == null) {
            return Flux.error(new UnsupportedOperationException(ERROR_STREAMING_NOT_YET_IMPLEMENTED));
        }
        return Flux.just(new TimestampedVote(vote, clock.instant().toString()));
    }

    private EvaluationContext evaluationContext(CompiledPdp pdp, AuthorizationSubscription sub, String subscriptionId) {
        return EvaluationContext.of(pdp.metadata().pdpId(), pdp.metadata().configurationId(), subscriptionId, sub,
                pdpConfigurationSource.getFunctionBroker());
    }

    /**
     * Bridges the non-reactive {@link PdpVoterSource} update API to a Reactor
     * {@link Flux}. The flux emits the current configuration immediately
     * upon subscription, then every subsequent change. The bridge subscribes
     * the listener before reading the snapshot so an update emitted between
     * the two operations is not lost (an occasional duplicate can arrive,
     * which downstream stages treat as a regular update).
     *
     * @param pdpId the PDP identifier to observe
     * @return a flux of configuration snapshots
     */
    private Flux<Optional<CompiledPdp>> configFlux(String pdpId) {
        return Flux.create(sink -> {
            Consumer<PdpUpdateEvent> listener = event -> sink.next(switch (event) {
            case PdpUpdateEvent.Voter(var ignoredId, var voter) -> Optional.of(voter);
            case PdpUpdateEvent.Removed ignored                 -> Optional.empty();
            });
            sink.onCancel(() -> pdpConfigurationSource.unsubscribeFromUpdates(pdpId, listener));
            pdpConfigurationSource.subscribeToUpdates(pdpId, listener);
            sink.next(pdpConfigurationSource.getCurrentConfiguration(pdpId));
        });
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

}
