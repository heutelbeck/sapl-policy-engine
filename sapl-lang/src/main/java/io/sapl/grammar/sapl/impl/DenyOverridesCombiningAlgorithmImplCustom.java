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
package io.sapl.grammar.sapl.impl;

import static io.sapl.api.pdp.Decision.DENY;
import static io.sapl.api.pdp.Decision.INDETERMINATE;
import static io.sapl.api.pdp.Decision.NOT_APPLICABLE;
import static io.sapl.api.pdp.Decision.PERMIT;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.grammar.sapl.PolicyElement;
import io.sapl.grammar.sapl.impl.util.CombiningAlgorithmUtil;
import io.sapl.interpreter.CombinedDecision;
import io.sapl.interpreter.DocumentEvaluationResult;
import io.sapl.interpreter.combinators.ObligationAdviceCollector;
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
public class DenyOverridesCombiningAlgorithmImplCustom extends DenyOverridesCombiningAlgorithmImpl {

    @Override
    public Flux<CombinedDecision> combinePolicies(List<PolicyElement> policies) {
        return CombiningAlgorithmUtil.eagerlyCombinePolicyElements(policies, this::combinator, getName(),
                AuthorizationDecision.NOT_APPLICABLE);
    }

    @Override
    public String getName() {
        return "DENY_OVERRIDES";
    }

    private CombinedDecision combinator(DocumentEvaluationResult[] policyDecisions) {
        var entitlement = NOT_APPLICABLE;
        var collector   = new ObligationAdviceCollector();
        var resource    = Optional.<JsonNode>empty();
        var decisions   = new LinkedList<DocumentEvaluationResult>();
        for (var policyDecision : policyDecisions) {
            decisions.add(policyDecision);
            var authzDecision = policyDecision.getAuthorizationDecision();
            if (authzDecision.getDecision() == DENY) {
                entitlement = DENY;
            }
            if (authzDecision.getDecision() == INDETERMINATE && entitlement != DENY) {
                entitlement = INDETERMINATE;
            }
            if (authzDecision.getDecision() == PERMIT && entitlement == NOT_APPLICABLE) {
                entitlement = PERMIT;
            }
            collector.add(authzDecision);
            if (authzDecision.getResource().isPresent()) {
                if (resource.isPresent()) {
                    /*
                     * This is a transformation uncertainty. another policy already defined a
                     * transformation this the overall result is basically INDETERMINATE. However,
                     * existing DENY overrides with this algorithm.
                     */
                    if (entitlement != DENY) {
                        entitlement = INDETERMINATE;
                    }
                } else {
                    resource = authzDecision.getResource();
                }
            }
        }

        var finalDecision = new AuthorizationDecision(entitlement, resource, collector.getObligations(entitlement),
                collector.getAdvice(entitlement));
        return CombinedDecision.of(finalDecision, getName(), decisions);

    }

}
