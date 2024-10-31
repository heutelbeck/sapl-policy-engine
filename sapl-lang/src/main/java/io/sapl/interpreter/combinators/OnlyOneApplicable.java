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
package io.sapl.interpreter.combinators;

import java.util.LinkedList;
import java.util.List;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.grammar.sapl.CombiningAlgorithm;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.interpreter.CombinedDecision;
import io.sapl.interpreter.DocumentEvaluationResult;
import io.sapl.prp.DocumentMatch;
import lombok.experimental.UtilityClass;
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
@UtilityClass
public class OnlyOneApplicable {

    public Flux<CombinedDecision> onlyOneApplicable(PolicySet policySet) {
        return BasicCombiningAlgorithm.eagerlyCombinePolicyElements(policySet.getPolicies(),
                OnlyOneApplicable::combinator, CombiningAlgorithm.ONLY_ONE_APPLICABLE,
                AuthorizationDecision.NOT_APPLICABLE);
    }

    public Flux<CombinedDecision> onlyOneApplicable(List<DocumentMatch> documents) {
        return BasicCombiningAlgorithm.eagerlyCombineMatchingDocuments(documents, OnlyOneApplicable::combinator,
                CombiningAlgorithm.ONLY_ONE_APPLICABLE, AuthorizationDecision.NOT_APPLICABLE);
    }

    private CombinedDecision combinator(DocumentEvaluationResult[] evaluationResults) {
        var       aPolicyWasIndeterminate = false;
        var       applicableCount         = 0;
        var       authzDecision           = AuthorizationDecision.NOT_APPLICABLE;
        final var decisions               = new LinkedList<DocumentEvaluationResult>();
        for (var evaluationResult : evaluationResults) {
            decisions.add(evaluationResult);
            final var decisionUnderInspection = evaluationResult.getAuthorizationDecision();
            final var decision                = decisionUnderInspection.getDecision();
            if (decision != Decision.NOT_APPLICABLE) {
                applicableCount++;
                authzDecision            = decisionUnderInspection;
                aPolicyWasIndeterminate |= decision == Decision.INDETERMINATE;
            }
        }
        if (aPolicyWasIndeterminate || applicableCount > 1) {
            authzDecision = AuthorizationDecision.INDETERMINATE;
        }

        return CombinedDecision.of(authzDecision, CombiningAlgorithm.ONLY_ONE_APPLICABLE, decisions);
    }

}
