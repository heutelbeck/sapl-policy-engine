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
package io.sapl.pdp;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.ast.Outcome;
import io.sapl.compiler.document.TimestampedVote;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.document.VoteWithCoverage;
import io.sapl.compiler.pdp.PdpVoterMetadata;
import io.sapl.pdp.configuration.PdpVoterSource;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

public class DynamicPolicyDecisionPoint implements PolicyDecisionPoint {

    public static final String DEFAULT_PDP_ID = "default";

    public static final String ERROR_NO_PDP_CONFIGURATION = "No PDP configuration found.";

    private final PdpVoterSource        pdpConfigurationSource;
    private final IdFactory             idFactory;
    private final Mono<String>          pdpIdExtractor;
    private final List<VoteInterceptor> interceptors;

    /**
     * Creates a PDP with no interceptors.
     *
     * @param pdpConfigurationSource the source of PDP configurations
     * @param idFactory factory for generating subscription IDs
     * @param pdpIdExtractor reactive extractor for the PDP identifier
     */
    public DynamicPolicyDecisionPoint(PdpVoterSource pdpConfigurationSource,
            IdFactory idFactory,
            Mono<String> pdpIdExtractor) {
        this(pdpConfigurationSource, idFactory, pdpIdExtractor, List.of());
    }

    /**
     * Creates a PDP with vote interceptors.
     *
     * @param pdpConfigurationSource the source of PDP configurations
     * @param idFactory factory for generating subscription IDs
     * @param pdpIdExtractor reactive extractor for the PDP identifier
     * @param interceptors interceptors invoked on each vote, sorted by priority
     */
    public DynamicPolicyDecisionPoint(PdpVoterSource pdpConfigurationSource,
            IdFactory idFactory,
            Mono<String> pdpIdExtractor,
            List<VoteInterceptor> interceptors) {
        this.pdpConfigurationSource = pdpConfigurationSource;
        this.idFactory              = idFactory;
        this.pdpIdExtractor         = pdpIdExtractor;
        // Sort interceptors by priority (lower values execute first)
        this.interceptors = interceptors.stream().sorted().toList();
    }

    /**
     * Gathers authorization votes for a subscription as a reactive stream.
     * Returns INDETERMINATE if no configuration is loaded.
     *
     * @param authorizationSubscription the authorization subscription to evaluate
     * @return a flux of timestamped votes
     */
    public Flux<TimestampedVote> gatherVotes(AuthorizationSubscription authorizationSubscription) {
        val subscriptionId = idFactory.newRandom();
        return pdpIdExtractor
                .flatMapMany(pdpId -> pdpConfigurationSource.getPDPConfigurations(pdpId)
                        .switchMap(optionalConfig -> optionalConfig
                                .map(config -> config.vote(authorizationSubscription, subscriptionId))
                                .orElseGet(() -> Flux.just(noConfigurationVote(pdpId)))))
                .doOnSubscribe(s -> invokeOnSubscribe(subscriptionId, authorizationSubscription))
                .doOnNext(tv -> invokeInterceptors(tv, subscriptionId, authorizationSubscription))
                .doFinally(signal -> invokeOnUnsubscribe(subscriptionId));
    }

    @Override
    public Flux<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription) {
        return gatherVotes(authorizationSubscription).map(tv -> tv.vote().authorizationDecision());
    }

    @Override
    public AuthorizationDecision decideOnceBlocking(AuthorizationSubscription authorizationSubscription) {
        return voteOnce(authorizationSubscription).vote().authorizationDecision();
    }

    /**
     * Streams votes with coverage information for a subscription.
     * Returns an empty flux if no configuration is loaded.
     *
     * @param authorizationSubscription the authorization subscription to evaluate
     * @param pdpId the PDP identifier
     * @return a flux of votes with coverage data
     */
    public Flux<VoteWithCoverage> coverageStream(AuthorizationSubscription authorizationSubscription, String pdpId) {
        val subscriptionId = idFactory.newRandom();
        return pdpConfigurationSource.getPDPConfigurations(pdpId)
                .switchMap(optionalConfig -> optionalConfig
                        .map(config -> config.voteWithCoverage(authorizationSubscription, subscriptionId))
                        .orElseGet(Flux::empty));
    }

    /**
     * Evaluates a subscription once and returns the vote synchronously.
     * Returns an INDETERMINATE vote if no configuration is loaded.
     *
     * @param authorizationSubscription the authorization subscription to evaluate
     * @return the timestamped vote
     */
    public TimestampedVote voteOnce(AuthorizationSubscription authorizationSubscription) {
        val subscriptionId   = idFactory.newRandom();
        val pdpConfiguration = pdpConfigurationSource.getCurrentConfiguration(DEFAULT_PDP_ID);
        if (pdpConfiguration.isEmpty()) {
            return noConfigurationVote(DEFAULT_PDP_ID);
        }
        val timestampedVote = pdpConfiguration.get().voteOnce(authorizationSubscription, subscriptionId);
        invokeInterceptors(timestampedVote, subscriptionId, authorizationSubscription);
        return timestampedVote;
    }

    private static TimestampedVote noConfigurationVote(String pdpId) {
        val metadata = new PdpVoterMetadata("no-configuration", pdpId, "none", null, Outcome.PERMIT_OR_DENY, false);
        val error    = new ErrorValue(ERROR_NO_PDP_CONFIGURATION);
        val vote     = Vote.error(error, metadata);
        return new TimestampedVote(vote, Instant.now().toString());
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
