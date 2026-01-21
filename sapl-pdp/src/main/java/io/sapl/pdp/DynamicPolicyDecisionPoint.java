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

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.compiler.pdp.TimestampedVote;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.List;
import java.util.function.Function;

public class DynamicPolicyDecisionPoint implements PolicyDecisionPoint {

    public static final String DEFAULT_PDP_ID = "default";

    public static final String ERROR_NO_PDP_CONFIGURATION = "No PDP configuration found.";

    private final CompiledPDPConfigurationSource      pdpConfigurationSource;
    private final IdFactory                           idFactory;
    private final Function<ContextView, Mono<String>> pdpIdExtractor;
    private final List<VoteInterceptor>               interceptors;

    public DynamicPolicyDecisionPoint(CompiledPDPConfigurationSource pdpConfigurationSource,
            IdFactory idFactory,
            Function<ContextView, Mono<String>> pdpIdExtractor) {
        this(pdpConfigurationSource, idFactory, pdpIdExtractor, List.of());
    }

    public DynamicPolicyDecisionPoint(CompiledPDPConfigurationSource pdpConfigurationSource,
            IdFactory idFactory,
            Function<ContextView, Mono<String>> pdpIdExtractor,
            List<VoteInterceptor> interceptors) {
        this.pdpConfigurationSource = pdpConfigurationSource;
        this.idFactory              = idFactory;
        this.pdpIdExtractor         = pdpIdExtractor;
        // Sort interceptors by priority (lower values execute first)
        this.interceptors = interceptors.stream().sorted().toList();
    }

    public Flux<TimestampedVote> gatherVotes(AuthorizationSubscription authorizationSubscription) {
        val subscriptionId = idFactory.newRandom();
        return pdpId().flatMapMany(pdpConfigurationSource::getPDPConfigurations)
                .switchMap(optionalConfig -> optionalConfig
                        .map(config -> config.vote(authorizationSubscription, subscriptionId))
                        .orElseThrow(() -> new IllegalArgumentException(ERROR_NO_PDP_CONFIGURATION)))
                .doOnNext(this::invokeInterceptors);
    }

    @Override
    public Flux<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription) {
        return gatherVotes(authorizationSubscription).map(tv -> tv.vote().authorizationDecision());
    }

    @Override
    public AuthorizationDecision decideOnceBlocking(AuthorizationSubscription authorizationSubscription) {
        return voteOnce(authorizationSubscription).vote().authorizationDecision();
    }

    public TimestampedVote voteOnce(AuthorizationSubscription authorizationSubscription) {
        val pdpConfiguration = pdpConfigurationSource.getCurrentConfiguration(DEFAULT_PDP_ID)
                .orElseThrow(() -> new IllegalStateException(ERROR_NO_PDP_CONFIGURATION));
        val subscriptionId   = idFactory.newRandom();
        val timestampedVote  = pdpConfiguration.voteOnce(authorizationSubscription, subscriptionId);
        invokeInterceptors(timestampedVote);
        return timestampedVote;
    }

    private Mono<String> pdpId() {
        return Mono.deferContextual(pdpIdExtractor);
    }

    private void invokeInterceptors(TimestampedVote vote) {
        for (val interceptor : interceptors) {
            interceptor.intercept(vote);
        }
    }

}
