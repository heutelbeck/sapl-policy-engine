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

import java.util.List;
import java.util.function.Function;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.grammar.sapl.CombiningAlgorithm;
import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.PolicyElement;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.interpreter.CombinedDecision;
import io.sapl.interpreter.DocumentEvaluationResult;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

/**
 * This algorithm is used if the policy administrator manages the policy’s
 * priority by their order in a policy set. As soon as the first policy returns
 * PERMIT, DENY or INDETERMINATE, its result is the final decision. Thus, a
 * "default" can be specified by creating a last policy without any conditions.
 * If a decision is found, errors which might occur in later policies are
 * ignored.
 * <p>
 * Since there is no order in the policy documents known to the PDP, the PDP
 * cannot be configured with this algorithm. first-applicable might only be used
 * for policy combination inside a policy set.
 * <p>
 * It works as follows:
 * <p>
 * Each policy is evaluated in the order specified in the policy set.
 * <p>
 * If it evaluates to INDETERMINATE, the decision is INDETERMINATE.
 * <p>
 * If it evaluates to PERMIT or DENY, the decision is PERMIT or DENY
 * <p>
 * If it evaluates to NOT_APPLICABLE, the next policy is evaluated.
 * <p>
 * If no policy with a decision different from NOT_APPLICABLE has been found,
 * the decision of the policy set is NOT_APPLICABLE.
 *
 */
@UtilityClass
public class FirstApplicable {

    public Flux<CombinedDecision> firstApplicable(PolicySet policySet) {
        return combine(0, policySet.getPolicies())
                .apply(CombinedDecision.of(AuthorizationDecision.NOT_APPLICABLE, CombiningAlgorithm.FIRST_APPLICABLE));
    }

    private Function<CombinedDecision, Flux<CombinedDecision>> combine(int policyId, List<Policy> policies) {
        if (policyId == policies.size())
            return Flux::just;

        return combinedDecision -> evaluatePolicy(policies.get(policyId)).switchMap(documentEvaluationResult -> {
            final var authzDecision = documentEvaluationResult.getAuthorizationDecision();
            if (authzDecision.getDecision() != Decision.NOT_APPLICABLE) // Found first applicable
                return Flux.just(
                        combinedDecision.withDecisionAndEvaluationResult(authzDecision, documentEvaluationResult));

            return combine(policyId + 1, policies)
                    .apply(combinedDecision.withEvaluationResult(documentEvaluationResult));
        });
    }

    private Flux<DocumentEvaluationResult> evaluatePolicy(PolicyElement policyElement) {
        return policyElement.matches().flux().flatMap(match -> {
            if (!match.isBoolean() || !match.getBoolean()) {
                return Flux.just(policyElement.targetResult(match));
            }
            return policyElement.evaluate().map(result -> result.withTargetResult(match));
        });
    }

}
