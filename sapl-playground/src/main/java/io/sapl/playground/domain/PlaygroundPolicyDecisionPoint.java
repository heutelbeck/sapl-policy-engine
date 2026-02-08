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
package io.sapl.playground.domain;

import com.vaadin.flow.spring.annotation.UIScope;
import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.document.TimestampedVote;
import io.sapl.pdp.DynamicPolicyDecisionPoint;
import io.sapl.pdp.configuration.PdpVoterSource;
import lombok.val;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * UI-scoped Policy Decision Point for the SAPL playground. Manages the embedded
 * PDP instance and provides methods to
 * configure policies, variables, and combining algorithms dynamically.
 * <p>
 * This component is scoped to the UI session and maintains state for the
 * duration of the playground view lifecycle.
 * Resources are cleaned up automatically when the view is detached.
 */
@UIScope
@Component
public class PlaygroundPolicyDecisionPoint {

    private final PlaygroundConfigurationSource configurationSource;
    private final DynamicPolicyDecisionPoint    policyDecisionPoint;

    /**
     * Creates a new playground policy decision point. Initializes the embedded PDP
     * with the provided attribute broker
     * and function broker.
     *
     * @param attributeBroker
     * broker for attribute streams and policy information points
     * @param functionBroker
     * broker providing function libraries for policy evaluation
     */
    public PlaygroundPolicyDecisionPoint(AttributeBroker attributeBroker, FunctionBroker functionBroker) {
        val pdpVoterSource = new PdpVoterSource(functionBroker, attributeBroker);
        this.configurationSource = new PlaygroundConfigurationSource(pdpVoterSource);
        this.policyDecisionPoint = new DynamicPolicyDecisionPoint(pdpVoterSource, () -> UUID.randomUUID().toString(),
                Mono.just(DynamicPolicyDecisionPoint.DEFAULT_PDP_ID));
    }

    /**
     * Evaluates an authorization subscription and returns timestamped votes. The
     * returned flux emits a vote whenever
     * the result changes based on policy updates, variable changes, or attribute
     * stream updates.
     *
     * @param authorizationSubscription
     * the authorization subscription to evaluate
     *
     * @return flux of timestamped votes with evaluation details
     */
    public Flux<TimestampedVote> decide(AuthorizationSubscription authorizationSubscription) {
        return policyDecisionPoint.gatherVotes(authorizationSubscription);
    }

    /**
     * Updates the variables available during policy evaluation. Variables can be
     * referenced in policies using their
     * names. Changes trigger re-evaluation of active subscriptions.
     *
     * @param variables
     * map of variable names to their values
     */
    public void setVariables(Map<String, Value> variables) {
        configurationSource.setVariables(variables);
    }

    /**
     * Updates the policy retrieval point with new policy documents. Replaces all
     * existing policies with the provided
     * documents. Changes trigger re-evaluation of active subscriptions.
     *
     * @param documents
     * list of SAPL policy document source strings
     */
    public void updatePolicyRetrievalPoint(List<String> documents) {
        configurationSource.setPolicies(documents);
    }

    /**
     * Sets the combining algorithm for policy document combination. Determines how
     * multiple applicable policy documents
     * are combined into a single decision. Changes trigger re-evaluation of active
     * subscriptions.
     *
     * @param algorithm
     * the combining algorithm to use
     */
    public void setCombiningAlgorithm(CombiningAlgorithm algorithm) {
        configurationSource.setCombiningAlgorithm(algorithm);
    }

    /**
     * Attempts to compile a policy source and returns any compile errors. Useful
     * for validating policies in editors
     * before they are applied.
     *
     * @param source
     * the SAPL policy source to compile
     *
     * @return optional containing the exception if compilation failed, empty if
     * successful
     */
    public Optional<SaplCompilerException> tryCompile(String source) {
        return configurationSource.tryCompile(source);
    }

}
