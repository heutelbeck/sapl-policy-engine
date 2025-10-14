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
package io.sapl.interpreter.combinators;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.CombiningAlgorithm;
import io.sapl.grammar.sapl.PolicyElement;
import io.sapl.grammar.sapl.impl.util.ErrorFactory;
import io.sapl.interpreter.CombinedDecision;
import io.sapl.interpreter.DocumentEvaluationResult;
import io.sapl.prp.DocumentMatch;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

@UtilityClass
public class BasicCombiningAlgorithm {

    public static Flux<CombinedDecision> eagerlyCombineMatchingDocuments(Collection<DocumentMatch> matchingDocuments,
            Function<DocumentEvaluationResult[], CombinedDecision> combinator, CombiningAlgorithm algorithm,
            AuthorizationDecision defaultDecisionIfEmpty) {
        if (matchingDocuments.isEmpty())
            return Flux.just(CombinedDecision.of(defaultDecisionIfEmpty, algorithm));
        final var policyDecisions = eagerMatchingDocumentsDecisionFluxes(matchingDocuments);
        return Flux.combineLatest(policyDecisions, decisionObjects -> combinator
                .apply(Arrays.copyOf(decisionObjects, decisionObjects.length, DocumentEvaluationResult[].class)));
    }

    public static Flux<CombinedDecision> eagerlyCombinePolicyElements(
            Collection<? extends PolicyElement> policyElements,
            Function<DocumentEvaluationResult[], CombinedDecision> combinator, CombiningAlgorithm algorithm,
            AuthorizationDecision defaultDecisionIfEmpty) {
        if (policyElements.isEmpty())
            return Flux.just(CombinedDecision.of(defaultDecisionIfEmpty, algorithm));
        final var policyDecisions = eagerPolicyElementDecisionFluxes(policyElements);
        return Flux.combineLatest(policyDecisions, decisionObjects -> combinator
                .apply(Arrays.copyOf(decisionObjects, decisionObjects.length, DocumentEvaluationResult[].class)));
    }

    private static List<Flux<DocumentEvaluationResult>> eagerMatchingDocumentsDecisionFluxes(
            Collection<DocumentMatch> matchingDocuments) {
        final var documentDecisions = new ArrayList<Flux<DocumentEvaluationResult>>(matchingDocuments.size());
        for (var matchingDocument : matchingDocuments) {
            documentDecisions.add(matchingDocument.document().sapl().getPolicyElement().evaluate()
                    .map(result -> result.withTargetResult(matchingDocument.targetExpressionResult())));
        }
        return documentDecisions;
    }

    private static List<Flux<DocumentEvaluationResult>> eagerPolicyElementDecisionFluxes(
            Collection<? extends PolicyElement> policyElements) {
        final var policyDecisions = new ArrayList<Flux<DocumentEvaluationResult>>(policyElements.size());
        for (var policyElement : policyElements) {
            policyDecisions.add(evaluatePolicyElementTargetAndPolicyIfApplicable(policyElement));
        }
        return policyDecisions;
    }

    private static Flux<DocumentEvaluationResult> evaluatePolicyElementTargetAndPolicyIfApplicable(
            PolicyElement policyElement) {
        final var matches = policyElement.matches()
                .map(BasicCombiningAlgorithm::requireTargetExpressionEvaluatesToBoolean);
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

        return ErrorFactory
                .error("Type mismatch. Target expression must evaluate to Boolean. Was: %s",
                        targetExpressionResult.getValType())
                .withTrace(BasicCombiningAlgorithm.class, false, targetExpressionResult);
    }

}
