/*
 * Streaming Attribute Policy Language (SAPL) Engine
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.LinkedList;
import java.util.List;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.grammar.sapl.PolicyElement;
import io.sapl.grammar.sapl.impl.util.CombiningAlgorithmUtil;
import io.sapl.interpreter.CombinedDecision;
import io.sapl.interpreter.DocumentEvaluationResult;
import reactor.core.publisher.Flux;

/**
 * This algorithm is used if policy sets and policies are constructed in a way
 * that multiple policy documents with a matching target are considered an
 * error. A PERMIT or DENY decision will only be returned if there is exactly
 * one policy set or policy with matching target expression and if this policy
 * document evaluates to PERMIT or DENY.
 * <p>
 * It works as follows:
 * <p>
 * 1. If any target evaluation results in an error (INDETERMINATE) or if more
 * than one policy documents have a matching target, the decision is
 * INDETERMINATE.
 * <p>
 * 2. Otherwise:
 * <p>
 * a) If there is no matching policy document, the decision is NOT_APPLICABLE.
 * <p>
 * b) Otherwise, i.e., there is exactly one matching policy document, the
 * decision is the result of evaluating this policy document.
 *
 */
public class OnlyOneApplicableCombiningAlgorithmImplCustom extends OnlyOneApplicableCombiningAlgorithmImpl {

    @Override
    public Flux<CombinedDecision> combinePolicies(List<PolicyElement> policies) {
        return CombiningAlgorithmUtil.eagerlyCombinePolicyElements(policies, this::combinator, getName());
    }

    @Override
    public String getName() {
        return "ONLY_ONE_APPLICABLE";
    }

    private CombinedDecision combinator(DocumentEvaluationResult[] evaluationResults) {
        var aPolicyWasIndeterminate = false;
        var applicableCount         = 0;
        var authzDecision           = AuthorizationDecision.NOT_APPLICABLE;
        var decisions               = new LinkedList<DocumentEvaluationResult>();
        for (var evaluationResult : evaluationResults) {
            decisions.add(evaluationResult);
            var decisionUnderInspection = evaluationResult.getAuthorizationDecision();
            var decision                = decisionUnderInspection.getDecision();
            if (decision != Decision.NOT_APPLICABLE) {
                applicableCount++;
                authzDecision            = decisionUnderInspection;
                aPolicyWasIndeterminate |= decision == Decision.INDETERMINATE;
            }
        }
        if (aPolicyWasIndeterminate || applicableCount > 1) {
            authzDecision = AuthorizationDecision.INDETERMINATE;
        }

        return CombinedDecision.of(authzDecision, getName(), decisions);
    }

}
