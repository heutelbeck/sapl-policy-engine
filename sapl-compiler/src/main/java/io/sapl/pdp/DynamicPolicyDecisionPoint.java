/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.api.model.EvaluationContext;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.function.Function;

public class DynamicPolicyDecisionPoint implements PolicyDecisionPoint {
    private final CompiledPDPConfigurationSource pdpConfigurationSource;
    private final IdFactory                      idFactory;
    // Creates clean boundary and no need for complete SpringSecurity Dependency at
    // PDP level.
    private final Function<ContextView, Mono<String>> pdpIdExtractor;

    public DynamicPolicyDecisionPoint(CompiledPDPConfigurationSource pdpConfigurationSource, IdFactory idFactory) {
        this.pdpConfigurationSource = pdpConfigurationSource;
        this.idFactory              = idFactory;
        this.pdpIdExtractor         = ctx -> Mono.just("default");
    }

    public DynamicPolicyDecisionPoint(CompiledPDPConfigurationSource pdpConfigurationSource,
            IdFactory idFactory,
            Function<ContextView, Mono<String>> pdpIdExtractor) {
        this.pdpConfigurationSource = pdpConfigurationSource;
        this.idFactory              = idFactory;
        this.pdpIdExtractor         = pdpIdExtractor;
    }

    @Override
    public Flux<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription) {
        val subscriptionId = idFactory.newRandom();
        return pdpId().flatMapMany(pdpConfigurationSource::getPDPConfigurations)
                .switchMap(optionalConfig -> optionalConfig
                        .map(config -> decide(authorizationSubscription, config, subscriptionId))
                        .orElse(Flux.just(AuthorizationDecision.INDETERMINATE)));
    }

    private Flux<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription,
            CompiledPDPConfiguration pdpConfiguration, String subscriptionId) {
        val evaluationContext = evaluationContext(authorizationSubscription, pdpConfiguration, subscriptionId);
        return switch (pdpConfiguration.combiningAlgorithm()) {
        case DENY_OVERRIDES      -> decideDenyOverrides(pdpConfiguration, evaluationContext);
        case DENY_UNLESS_PERMIT  -> decideDenyUnlessPermit(pdpConfiguration, evaluationContext);
        case PERMIT_OVERRIDES    -> decidePermitOverrides(pdpConfiguration, evaluationContext);
        case PERMIT_UNLESS_DENY  -> decidePermitUnlessDeny(pdpConfiguration, evaluationContext);
        case ONLY_ONE_APPLICABLE -> decideOnlyOneApplicable(pdpConfiguration, evaluationContext);
        };
    }

    private Flux<AuthorizationDecision> decideOnlyOneApplicable(CompiledPDPConfiguration pdpConfiguration,
            EvaluationContext evaluationContext) {
        return Flux.just(AuthorizationDecision.INDETERMINATE);
    }

    private Flux<AuthorizationDecision> decidePermitUnlessDeny(CompiledPDPConfiguration pdpConfiguration,
            EvaluationContext evaluationContext) {
        return Flux.just(AuthorizationDecision.INDETERMINATE);
    }

    private Flux<AuthorizationDecision> decideDenyUnlessPermit(CompiledPDPConfiguration pdpConfiguration,
            EvaluationContext evaluationContext) {
        return Flux.just(AuthorizationDecision.INDETERMINATE);
    }

    private Flux<AuthorizationDecision> decidePermitOverrides(CompiledPDPConfiguration pdpConfiguration,
            EvaluationContext evaluationContext) {
        return Flux.just(AuthorizationDecision.INDETERMINATE);
    }

    private Flux<AuthorizationDecision> decideDenyOverrides(CompiledPDPConfiguration pdpConfiguration,
            EvaluationContext evaluationContext) {
        return Flux.just(AuthorizationDecision.INDETERMINATE);
    }

    private EvaluationContext evaluationContext(AuthorizationSubscription authorizationSubscription,
            CompiledPDPConfiguration pdpConfiguration, String subscriptionId) {
        return new EvaluationContext(pdpConfiguration.pdpId(), pdpConfiguration.configurationId(), subscriptionId,
                authorizationSubscription, pdpConfiguration.variables(), pdpConfiguration.functionBroker(),
                pdpConfiguration.attributeBroker());
    }

    private Mono<String> pdpId() {
        return Mono.deferContextual(pdpIdExtractor);
    }
}
