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
package io.sapl.grammar.sapl.impl.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.CombiningAlgorithm;
import io.sapl.grammar.sapl.PolicyElement;
import io.sapl.interpreter.CombinedDecision;
import io.sapl.interpreter.DocumentEvaluationResult;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

@UtilityClass
public class CombiningAlgorithmUtil {

    public static Flux<CombinedDecision> eagerlyCombinePolicyElements(List<PolicyElement> policyElements,
            Function<DocumentEvaluationResult[], CombinedDecision> combinator, String algorithmName,
            AuthorizationDecision defaultDecisionIfEmpty) {
        if (policyElements.isEmpty())
            return Flux.just(CombinedDecision.of(defaultDecisionIfEmpty, algorithmName));
        var policyDecisions = eagerPolicyElementDecisionFluxes(policyElements);
        return Flux.combineLatest(policyDecisions, decisionObjects -> combinator
                .apply(Arrays.copyOf(decisionObjects, decisionObjects.length, DocumentEvaluationResult[].class)));
    }

    private static List<Flux<DocumentEvaluationResult>> eagerPolicyElementDecisionFluxes(
            Collection<PolicyElement> policyElements) {
        var policyDecisions = new ArrayList<Flux<DocumentEvaluationResult>>(policyElements.size());
        for (var policyElement : policyElements) {
            policyDecisions.add(evaluatePolicyElementTargetAndPolicyIfApplicable(policyElement));
        }
        return policyDecisions;
    }

    private static Flux<DocumentEvaluationResult> evaluatePolicyElementTargetAndPolicyIfApplicable(
            PolicyElement policyElement) {
        var matches = policyElement.matches().map(CombiningAlgorithmUtil::requireTargetExpressionEvaluatesToBoolean);
        return matches.flatMapMany(evaluatePolicyIfApplicable(policyElement));
    }

    private static Function<Val, Flux<DocumentEvaluationResult>> evaluatePolicyIfApplicable(
            PolicyElement policyElement) {
        return targetExpressionResult -> {
            if (targetExpressionResult.isError() || !targetExpressionResult.getBoolean()) {
                return Flux.just(policyElement.targetResult(targetExpressionResult));
            }
            return policyElement.evaluate().map(result -> result.withTargetResult(targetExpressionResult));
        };
    }

    private static Val requireTargetExpressionEvaluatesToBoolean(Val targetExpressionResult) {
        if (targetExpressionResult.isBoolean())
            return targetExpressionResult;

        return Val
                .error("Type mismatch. Target expression must evaluate to Boolean. Was: %s",
                        targetExpressionResult.getValType())
                .withTrace(CombiningAlgorithm.class, targetExpressionResult);
    }

}
