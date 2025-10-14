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

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.CombiningAlgorithm;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.interpreter.CombinedDecision;
import io.sapl.interpreter.DocumentEvaluationResult;
import io.sapl.prp.DocumentMatch;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static io.sapl.api.pdp.Decision.DENY;
import static io.sapl.api.pdp.Decision.PERMIT;

/**
 * This strict algorithm is used if the decision should be a DENY except for
 * there is a PERMIT. It ensures that any decision is either DENY or PERMIT.
 * <p>
 * It works as follows:
 * <p>
 * - If any policy document evaluates to PERMIT and there is no transformation
 * uncertainty (multiple policies evaluate to PERMIT and at least one of them
 * has a transformation statement), the decision is PERMIT.
 * <p>
 * - Otherwise the decision is a DENY.
 */
@UtilityClass
public class DenyUnlessPermit {

    public Flux<CombinedDecision> denyUnlessPermit(PolicySet policySet) {
        return BasicCombiningAlgorithm.eagerlyCombinePolicyElements(policySet.getPolicies(),
                DenyUnlessPermit::combinator, CombiningAlgorithm.DENY_UNLESS_PERMIT, AuthorizationDecision.DENY);
    }

    public Flux<CombinedDecision> denyUnlessPermit(List<DocumentMatch> documents) {
        return BasicCombiningAlgorithm.eagerlyCombineMatchingDocuments(documents, DenyUnlessPermit::combinator,
                CombiningAlgorithm.DENY_UNLESS_PERMIT, AuthorizationDecision.DENY);
    }

    private CombinedDecision combinator(DocumentEvaluationResult[] policyDecisions) {
        final var collector   = new ObligationAdviceCollector();
        final var decisions   = new LinkedList<DocumentEvaluationResult>();
        var       resource    = Optional.<JsonNode>empty();
        var       entitlement = DENY;
        for (var policyDecision : policyDecisions) {
            decisions.add(policyDecision);
            final var authorizationDecision = policyDecision.getAuthorizationDecision();
            if (authorizationDecision.getDecision() == PERMIT) {
                entitlement = PERMIT;
            }
            collector.add(authorizationDecision);
            if (authorizationDecision.getResource().isPresent()) {
                if (resource.isPresent()) {
                    // this is a transformation uncertainty.
                    // another policy already defined a transformation
                    // this the overall result is basically INDETERMINATE.
                    // However, DENY overrides with this algorithm.
                    entitlement = DENY;
                } else {
                    resource = authorizationDecision.getResource();
                }
            }
        }

        final var finalDecision = new AuthorizationDecision(entitlement, resource,
                collector.getObligations(entitlement), collector.getAdvice(entitlement));
        return CombinedDecision.of(finalDecision, CombiningAlgorithm.DENY_UNLESS_PERMIT, decisions);
    }

}
