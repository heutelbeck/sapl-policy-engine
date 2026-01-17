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
package io.sapl.compiler.combining;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.compiler.pdp.Vote;
import io.sapl.compiler.pdp.VoterMetadata;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@UtilityClass
public class DecisionCombiner {

    public static Optional<Vote> combineAuthorizationDecisions(Vote setVote, Vote policyVote, Decision priority) {
        val setAuthzDecision      = setVote.authorizationDecision();
        val policyAuthzDecision   = policyVote.authorizationDecision();
        val combinedAuthzDecision = combineAuthorizationDecisions(setAuthzDecision, policyAuthzDecision, priority);

        return Optional.of(setVote);
    }

    public static Vote combineAuthorizationDecisions(Vote setVote, Vote policyVote) {
        return setVote;
    }

    public static AuthorizationDecision combineAuthorizationDecisions(AuthorizationDecision authzDecisionA,
            AuthorizationDecision authzDecisionB, Decision priority) {
        val entitlementA = authzDecisionA.decision();
        val entitlementB = authzDecisionB.decision();

        // Error propagates
        if (entitlementA == Decision.INDETERMINATE || entitlementB == Decision.INDETERMINATE) {
            return AuthorizationDecision.INDETERMINATE;
        }

        // Anything wins over NOT_APPLICABLE
        if (entitlementA == Decision.NOT_APPLICABLE)
            return authzDecisionB;
        if (entitlementB == Decision.NOT_APPLICABLE)
            return authzDecisionA;

        // From here both are either PERMIT or DENY

        // Same vote - merge
        if (entitlementA == entitlementB) {
            return merge(authzDecisionA, authzDecisionB);
        }

        // From here one is PERMIT and one is DENY
        // Thus one of both is the priority one and wins
        if (entitlementA == priority)
            return authzDecisionA;
        else
            return authzDecisionB;
    }

    private static AuthorizationDecision merge(AuthorizationDecision authzDecisionA,
            AuthorizationDecision authzDecisionB) {
        // Transformation uncertainty
        val resourceA = authzDecisionA.resource();
        val resourceB = authzDecisionB.resource();
        if (!Value.UNDEFINED.equals(resourceA) && !Value.UNDEFINED.equals(resourceB)) {
            return AuthorizationDecision.INDETERMINATE;
        }

        val resource    = Value.UNDEFINED.equals(resourceA) ? resourceB : resourceA;
        val obligations = mergeArrays(authzDecisionA.obligations(), authzDecisionB.obligations());
        val advice      = mergeArrays(authzDecisionA.advice(), authzDecisionB.advice());

        return new AuthorizationDecision(authzDecisionA.decision(), obligations, advice, resource);
    }

    private static ArrayValue mergeArrays(ArrayValue a, ArrayValue b) {
        if (a.isEmpty())
            return b;
        if (b.isEmpty())
            return a;
        val merged = new ArrayList<>(a);
        merged.addAll(b);
        return new ArrayValue(merged);
    }

    public static Optional<Vote> fold(List<Vote> decisions, Decision priority, VoterMetadata voterMetadata) {
        if (decisions.isEmpty()) {
            return Optional.empty();
        }

        var   entitlement               = Decision.NOT_APPLICABLE;
        Value resource                  = Value.UNDEFINED;
        var   hasResource               = false;
        var   transformationUncertainty = false;

        val permitObligations = new ArrayList<Value>();
        val permitAdvice      = new ArrayList<Value>();
        val denyObligations   = new ArrayList<Value>();
        val denyAdvice        = new ArrayList<Value>();

        for (var pdpDecision : decisions) {
            val authz    = pdpDecision.authorizationDecision();
            val decision = authz.decision();

            // Priority voting
            if (decision == priority) {
                entitlement = priority;
            } else if (decision == Decision.INDETERMINATE && entitlement != priority) {
                entitlement = Decision.INDETERMINATE;
            } else if ((decision == Decision.PERMIT || decision == Decision.DENY)
                    && entitlement == Decision.NOT_APPLICABLE) {
                entitlement = decision;
            }

            // Resource transformation uncertainty
            val res = authz.resource();
            if (!Value.UNDEFINED.equals(res)) {
                if (hasResource) {
                    transformationUncertainty = true;
                } else {
                    resource    = res;
                    hasResource = true;
                }
            }

            // Collect constraints
            if (decision == Decision.PERMIT) {
                permitObligations.addAll(authz.obligations());
                permitAdvice.addAll(authz.advice());
            } else if (decision == Decision.DENY) {
                denyObligations.addAll(authz.obligations());
                denyAdvice.addAll(authz.advice());
            }
        }

        // Build result
        if (transformationUncertainty) {
            entitlement = Decision.INDETERMINATE;
            resource    = Value.UNDEFINED;
        }

        val obligations = selectConstraints(entitlement, permitObligations, denyObligations);
        val advice      = selectConstraints(entitlement, permitAdvice, denyAdvice);
        val authzResult = new AuthorizationDecision(entitlement, obligations, advice, resource);

        return Optional.of(Vote.combinedVote(authzResult, voterMetadata, decisions));
    }

    private static ArrayValue selectConstraints(Decision entitlement, List<Value> permitList, List<Value> denyList) {
        return switch (entitlement) {
        case PERMIT -> permitList.isEmpty() ? Value.EMPTY_ARRAY : new ArrayValue(permitList);
        case DENY   -> denyList.isEmpty() ? Value.EMPTY_ARRAY : new ArrayValue(denyList);
        default     -> Value.EMPTY_ARRAY;
        };
    }

}
