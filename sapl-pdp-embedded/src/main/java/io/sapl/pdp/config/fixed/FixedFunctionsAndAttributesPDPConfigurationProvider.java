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
package io.sapl.pdp.config.fixed;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.AuthorizationSubscriptionInterceptor;
import io.sapl.api.pdp.TracedDecision;
import io.sapl.api.pdp.TracedDecisionInterceptor;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.pdp.config.PDPConfiguration;
import io.sapl.pdp.config.PDPConfigurationProvider;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import io.sapl.prp.PolicyRetrievalPoint;
import io.sapl.prp.PolicyRetrievalPointSource;
import reactor.core.publisher.Flux;

public class FixedFunctionsAndAttributesPDPConfigurationProvider implements PDPConfigurationProvider {

    private final AttributeContext attributeCtx;

    private final FunctionContext functionCtx;

    private final VariablesAndCombinatorSource               variablesAndCombinatorSource;
    private PolicyRetrievalPointSource                       prpSource;
    private final List<AuthorizationSubscriptionInterceptor> subscriptionInterceptors;

    private final List<TracedDecisionInterceptor> decisionInterceptors;

    public FixedFunctionsAndAttributesPDPConfigurationProvider(AttributeContext attributeCtx,
            FunctionContext functionCtx, VariablesAndCombinatorSource variablesAndCombinatorSource,
            Collection<AuthorizationSubscriptionInterceptor> subscriptionInterceptors,
            Collection<TracedDecisionInterceptor> decisionInterceptors, PolicyRetrievalPointSource prpSource) {
        this.attributeCtx                 = attributeCtx;
        this.functionCtx                  = functionCtx;
        this.variablesAndCombinatorSource = variablesAndCombinatorSource;
        this.prpSource                    = prpSource;
        this.subscriptionInterceptors     = subscriptionInterceptors.stream().sorted(Comparator.reverseOrder())
                .toList();
        this.decisionInterceptors         = decisionInterceptors.stream().sorted(Comparator.reverseOrder()).toList();
    }

    @Override
    public Flux<PDPConfiguration> pdpConfiguration() {
        return Flux.combineLatest(variablesAndCombinatorSource.getCombiningAlgorithm(),
                variablesAndCombinatorSource.getVariables(), prpSource.policyRetrievalPoint(),
                this::createConfiguration);
    }

    @SuppressWarnings("unchecked")
    private PDPConfiguration createConfiguration(Object[] values) {
        final var combiningAlgorithm = ((Optional<PolicyDocumentCombiningAlgorithm>) values[0]).orElse(null);
        final var variables          = ((Optional<Map<String, Val>>) values[1]).orElse(null);
        final var prp                = (PolicyRetrievalPoint) values[2];
        return new PDPConfiguration("defaultConfiguration", attributeCtx, functionCtx, variables, combiningAlgorithm,
                decisionInterceptorChain(), subscriptionInterceptorChain(), prp);
    }

    private UnaryOperator<AuthorizationSubscription> subscriptionInterceptorChain() {
        return t -> {
            for (var intercept : subscriptionInterceptors) {
                t = intercept.apply(t);
            }
            return t;
        };
    }

    private UnaryOperator<TracedDecision> decisionInterceptorChain() {
        return t -> {
            for (var intercept : decisionInterceptors) {
                t = intercept.apply(t);
            }
            return t;
        };
    }

    @Override
    public void destroy() {
        variablesAndCombinatorSource.destroy();
    }
}
