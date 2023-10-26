/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.sapl.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.interpreter.CombinedDecision;
import io.sapl.interpreter.DocumentEvaluationResult;
import io.sapl.interpreter.PolicySetDecision;
import io.sapl.interpreter.context.AuthorizationContext;
import reactor.core.publisher.Flux;

public class PolicySetImplCustom extends PolicySetImpl {

    private static final String NAMES_NOT_UNIQUE_ERROR = "Inconsistent policy set. Names of policies in set are not unique.";

    /**
     * Evaluates the body of the policy set within the given evaluation context and
     * returns a {@link Flux} of {@link DocumentEvaluationResult} objects.
     * 
     * @return A {@link Flux} of {@link DocumentEvaluationResult} objects.
     */
    @Override
    public Flux<DocumentEvaluationResult> evaluate() {
        if (!policyNamesAreUnique()) {
            return Flux.just(PolicySetDecision.error(getSaplName(), NAMES_NOT_UNIQUE_ERROR));
        }
        var combinedDecisions = evaluateValueDefinitionsAndPolicies(0);
        return combinedDecisions.map(combined -> PolicySetDecision.of(combined, getSaplName()));
    }

    @Override
    public DocumentEvaluationResult targetResult(Val targetValue) {
        if (targetValue.isError())
            return PolicySetDecision.ofTargetError(getSaplName(), targetValue, this.algorithm.getName());
        return PolicySetDecision.notApplicable(getSaplName(), targetValue, this.algorithm.getName());

    }

    @Override
    public DocumentEvaluationResult importError(String errorMessage) {
        return PolicySetDecision.ofImportError(getSaplName(), errorMessage, this.algorithm.getName());
    }

    private boolean policyNamesAreUnique() {
        var policyNames = new HashSet<String>(policies.size(), 1.0F);
        for (var policy : policies)
            if (!policyNames.add(policy.getSaplName()))
                return false;

        return true;
    }

    private Flux<CombinedDecision> evaluateValueDefinitionsAndPolicies(int valueDefinitionId) {
        if (valueDefinitions == null || valueDefinitionId == valueDefinitions.size())
            return evaluateAndCombinePoliciesOfSet();

        var valueDefinition           = valueDefinitions.get(valueDefinitionId);
        var evaluatedValueDefinitions = valueDefinition.getEval().evaluate();
        return evaluatedValueDefinitions.switchMap(value -> evaluateValueDefinitionsAndPolicies(valueDefinitionId + 1)
                .contextWrite(ctx -> AuthorizationContext.setVariable(ctx, valueDefinition.getName(), value.withTrace(
                        PolicySet.class,
                        Map.of("policySet", Val.of(saplName), "variableName", Val.of(valueDefinition.getName()))))));
    }

    private Flux<CombinedDecision> evaluateAndCombinePoliciesOfSet() {
        return getAlgorithm().combinePolicies(new ArrayList<>(policies));
    }

}
