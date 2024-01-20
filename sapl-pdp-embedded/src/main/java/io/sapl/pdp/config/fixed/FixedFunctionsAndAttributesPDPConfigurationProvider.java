/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.AuthorizationSubscriptionInterceptor;
import io.sapl.api.pdp.TracedDecision;
import io.sapl.api.pdp.TracedDecisionInterceptor;
import io.sapl.grammar.sapl.CombiningAlgorithm;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.pdp.config.PDPConfiguration;
import io.sapl.pdp.config.PDPConfigurationProvider;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import reactor.core.publisher.Flux;

public class FixedFunctionsAndAttributesPDPConfigurationProvider implements PDPConfigurationProvider {

    private final AttributeContext attributeCtx;

    private final FunctionContext functionCtx;

    private final VariablesAndCombinatorSource variablesAndCombinatorSource;

    private final List<AuthorizationSubscriptionInterceptor> subscriptionInterceptors;

    private final List<TracedDecisionInterceptor> decisionInterceptors;

    public FixedFunctionsAndAttributesPDPConfigurationProvider(AttributeContext attributeCtx,
            FunctionContext functionCtx, VariablesAndCombinatorSource variablesAndCombinatorSource,
            Collection<AuthorizationSubscriptionInterceptor> subscriptionInterceptors,
            Collection<TracedDecisionInterceptor> decisionInterceptors) {
        this.attributeCtx                 = attributeCtx;
        this.functionCtx                  = functionCtx;
        this.variablesAndCombinatorSource = variablesAndCombinatorSource;
        this.subscriptionInterceptors     = subscriptionInterceptors.stream().sorted(Comparator.reverseOrder())
                .toList();
        this.decisionInterceptors         = decisionInterceptors.stream().sorted(Comparator.reverseOrder()).toList();
    }

    @Override
    public Flux<PDPConfiguration> pdpConfiguration() {
        return Flux.combineLatest(variablesAndCombinatorSource.getCombiningAlgorithm(),
                variablesAndCombinatorSource.getVariables(), this::createConfiguration);
    }

    private PDPConfiguration createConfiguration(Optional<CombiningAlgorithm> combinator,
            Optional<Map<String, JsonNode>> variables) {
        return new PDPConfiguration(attributeCtx, functionCtx, variables.orElse(null), combinator.orElse(null),
                decisionInterceptorChain(), subscriptionInterceptorChain());
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
