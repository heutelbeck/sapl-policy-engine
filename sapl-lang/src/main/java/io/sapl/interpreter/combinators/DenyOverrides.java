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

import static io.sapl.api.pdp.Decision.DENY;
import static io.sapl.api.pdp.Decision.INDETERMINATE;
import static io.sapl.api.pdp.Decision.NOT_APPLICABLE;
import static io.sapl.api.pdp.Decision.PERMIT;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.CombiningAlgorithm;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.interpreter.CombinedDecision;
import io.sapl.interpreter.DocumentEvaluationResult;
import io.sapl.prp.DocumentMatch;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

/**
 * This algorithm is used if a DENY decision should prevail a PERMIT without
 * setting a default decision.
 * <p>
 * It works as follows:
 * <p>
 * - If any policy document evaluates to DENY, the decision is a DENY.
 * <p>
 * - Otherwise:
 * <p>
 * a) If there is any INDETERMINATE or there is a transformation uncertainty
 * (multiple policies evaluate to PERMIT and at least one of them has a
 * transformation statement), the decision is INDETERMINATE.
 * <p>
 * b) Otherwise:
 * <p>
 * i) If there is any PERMIT the decision is PERMIT.
 * <p>
 * ii) Otherwise the decision is NOT_APPLICABLE.
 */
@UtilityClass
public class DenyOverrides {

    public Flux<CombinedDecision> denyOverrides(PolicySet policySet) {
        return BasicCombiningAlgorithm.eagerlyCombinePolicyElements(policySet.getPolicies(), DenyOverrides::combinator,
                CombiningAlgorithm.DENY_OVERRIDES, AuthorizationDecision.NOT_APPLICABLE);
    }

    public Flux<CombinedDecision> denyOverrides(List<DocumentMatch> documents) {
        return BasicCombiningAlgorithm.eagerlyCombineMatchingDocuments(documents, DenyOverrides::combinator,
                CombiningAlgorithm.DENY_OVERRIDES, AuthorizationDecision.NOT_APPLICABLE);
    }

    private CombinedDecision combinator(DocumentEvaluationResult[] policyDecisions) {
        final var collector = new ObligationAdviceCollector();
        final var decisions = new LinkedList<DocumentEvaluationResult>();

        var combinedDecisionsResource = Optional.<JsonNode>empty();
        var entitlement               = NOT_APPLICABLE;

        for (var policyDecision : policyDecisions) {
            decisions.add(policyDecision);
            final var authorizationDecision = policyDecision.getAuthorizationDecision();
            if (authorizationDecision.getDecision() == DENY) {
                entitlement = DENY;
            }
            if (authorizationDecision.getDecision() == INDETERMINATE && entitlement != DENY) {
                entitlement = INDETERMINATE;
            }
            if (authorizationDecision.getDecision() == PERMIT && entitlement == NOT_APPLICABLE) {
                entitlement = PERMIT;
            }
            collector.add(authorizationDecision);
            final var currentDecisionsResource = authorizationDecision.getResource();
            if (currentDecisionsResource.isPresent() && combinedDecisionsResource.isPresent() && entitlement != DENY) {
                /*
                 * This is a transformation uncertainty. another policy already defined a
                 * transformation this the overall result is basically INDETERMINATE. However,
                 * existing DENY overrides with this algorithm.
                 */
                entitlement = INDETERMINATE;
            } else if (currentDecisionsResource.isPresent() && combinedDecisionsResource.isEmpty()) {
                combinedDecisionsResource = currentDecisionsResource;
            }
        }

        final var finalDecision = new AuthorizationDecision(entitlement, combinedDecisionsResource,
                collector.getObligations(entitlement), collector.getAdvice(entitlement));
        return CombinedDecision.of(finalDecision, CombiningAlgorithm.DENY_OVERRIDES, decisions);

    }

}
