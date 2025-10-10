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
package io.sapl.playground.domain;

import com.vaadin.flow.spring.annotation.UIScope;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.TracedDecision;
import io.sapl.attributes.broker.api.AttributeStreamBroker;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.config.fixed.FixedFunctionsAndAttributesPDPConfigurationProvider;
import io.sapl.playground.ui.PlaygroundView;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@UIScope
@Component
public class PlaygroundPolicyDecisionPoint {
    private static final SAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

    private final PlaygroundVariablesAndCombinatorSource variablesAndCombinatorSource = new PlaygroundVariablesAndCombinatorSource();
    private final PlaygroundPolicyRetrievalPointSource   policyRetrievalPointSource;
    private final EmbeddedPolicyDecisionPoint            policyDecisionPoint;

    public PlaygroundPolicyDecisionPoint(AttributeStreamBroker attributeStreamBroker, FunctionContext functionContext) {
        policyRetrievalPointSource = new PlaygroundPolicyRetrievalPointSource(INTERPRETER);
        val pdpConfigurationProvider = new FixedFunctionsAndAttributesPDPConfigurationProvider(attributeStreamBroker,
                functionContext, variablesAndCombinatorSource, List.of(), List.of(), policyRetrievalPointSource);
        policyDecisionPoint = new EmbeddedPolicyDecisionPoint(pdpConfigurationProvider);
    }

    public Flux<TracedDecision> decide(AuthorizationSubscription authorizationSubscription) {
        return policyDecisionPoint.decideTraced(authorizationSubscription);
    }

    public void setVariables(Map<String, Val> stringValMap) {
        variablesAndCombinatorSource.setVariables(stringValMap);
    }

    public void updatePrp(List<String> documents) {
        policyRetrievalPointSource.updatePrp(documents);
    }

    public void setCombiningAlgorithm(PolicyDocumentCombiningAlgorithm value) {
        variablesAndCombinatorSource.setCombiningAlgorithm(value);
    }

    /**
     * Cleans up resources when the view is detached.
     * Disposes of active subscriptions to prevent memory leaks.
     */
    @PreDestroy
    private void destroy() {
        variablesAndCombinatorSource.destroy();
        policyRetrievalPointSource.dispose();
    }

}
