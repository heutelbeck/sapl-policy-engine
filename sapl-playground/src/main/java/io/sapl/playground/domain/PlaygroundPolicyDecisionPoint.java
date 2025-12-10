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
import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.parser.DefaultSAPLParser;
import io.sapl.parser.SAPLParser;
import io.sapl.pdp.DynamicPolicyDecisionPoint;
import jakarta.annotation.PreDestroy;
import lombok.val;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * UI-scoped Policy Decision Point for the SAPL playground.
 * Manages the embedded PDP instance and provides methods to configure
 * policies, variables, and combining algorithms dynamically.
 * <p>
 * This component is scoped to the UI session and maintains state for
 * the duration of the playground view lifecycle. Resources are cleaned
 * up automatically when the view is detached.
 */
@UIScope
@Component
public class PlaygroundPolicyDecisionPoint {

    /*
     * SAPL interpreter for parsing policy documents.
     * Shared across all instances as it is stateless.
     */
    private static final SAPLParser PARSER = new DefaultSAPLParser();

    /*
     * Source for variables and combining algorithm configuration.
     * Provides reactive streams of configuration changes to the PDP.
     */
    private final PlaygroundVariablesAndCombinatorSource variablesAndCombinatorSource = new PlaygroundVariablesAndCombinatorSource();

    /*
     * Source for policy documents.
     * Provides reactive stream of policy retrieval point updates.
     */
    private final PlaygroundPolicyRetrievalPointSource policyRetrievalPointSource;

    /*
     * Embedded policy decision point instance.
     * Evaluates authorization subscriptions against configured
     * policies.
     */
    private final DynamicPolicyDecisionPoint policyDecisionPoint;

    /**
     * Creates a new playground policy decision point.
     * Initializes the embedded PDP with the provided attribute broker and function
     * context.
     * Sets up reactive sources for policies, variables, and combining
     * algorithms.
     *
     * @param attributeStreamBroker broker for attribute streams and policy
     * information points
     * @param functionContext context providing function libraries for policy
     * evaluation
     */
    public PlaygroundPolicyDecisionPoint(AttributeBroker attributeStreamBroker, FunctionBroker functionContext) {
        policyRetrievalPointSource = new PlaygroundPolicyRetrievalPointSource(PARSER);
        val policyDecisionPointConfigurationProvider = new FixedFunctionsAndAttributesPDPConfigurationProvider(
                attributeStreamBroker, functionContext, variablesAndCombinatorSource, List.of(), List.of(),
                policyRetrievalPointSource);
        policyDecisionPoint = new EmbeddedPolicyDecisionPoint(policyDecisionPointConfigurationProvider);
    }

    /**
     * Evaluates an authorization subscription and returns traced decisions.
     * The returned flux emits a decision whenever the result changes based on
     * policy updates, variable changes, or attribute stream updates.
     *
     * @param authorizationSubscription the authorization subscription to evaluate
     * @return flux of traced authorization decisions with evaluation details
     */
    public Flux<TracedDecision> decide(AuthorizationSubscription authorizationSubscription) {
        return policyDecisionPoint.decideTraced(authorizationSubscription);
    }

    /**
     * Updates the variables available during policy evaluation.
     * Variables can be referenced in policies using their names.
     * Changes trigger re-evaluation of active subscriptions.
     *
     * @param variablesMap map of variable names to their values
     */
    public void setVariables(Map<String, Val> variablesMap) {
        variablesAndCombinatorSource.setVariables(variablesMap);
    }

    /**
     * Updates the policy retrieval point with new policy documents.
     * Replaces all existing policies with the provided documents.
     * Changes trigger re-evaluation of active subscriptions.
     *
     * @param documents list of SAPL policy document source strings
     */
    public void updatePolicyRetrievalPoint(List<String> documents) {
        policyRetrievalPointSource.updatePolicyRetrievalPoint(documents);
    }

    /**
     * Sets the combining algorithm for policy document combination.
     * Determines how multiple applicable policy documents are combined
     * into a single decision.
     * Changes trigger re-evaluation of active subscriptions.
     *
     * @param algorithm the combining algorithm to use
     */
    public void setCombiningAlgorithm(CombiningAlgorithm algorithm) {
        variablesAndCombinatorSource.setCombiningAlgorithm(algorithm);
    }

    /**
     * Cleans up resources when the component is destroyed.
     * Disposes of active subscriptions and releases resources to prevent memory
     * leaks.
     * Called automatically by Spring when the UI scope is destroyed.
     */
    @PreDestroy
    private void destroy() {
        variablesAndCombinatorSource.destroy();
        policyRetrievalPointSource.dispose();
    }
}
