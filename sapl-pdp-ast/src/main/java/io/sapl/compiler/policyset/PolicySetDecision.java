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
package io.sapl.compiler.policyset;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.AttributeRecord;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.compiler.policy.PolicyDecision;
import io.sapl.compiler.policy.PolicyMetadata;
import lombok.NonNull;
import lombok.val;

import java.util.ArrayList;
import java.util.List;

public record PolicySetDecision(
        @NonNull Decision decision,
        @NonNull ArrayValue obligations,
        @NonNull ArrayValue advice,
        @NonNull Value resource,
        Value error,
        PolicySetMetadata metadata,
        List<PolicyDecision> contributingPolicyDecisions) implements PolicySetBody {

    public static PolicySetDecision simpleDecision(Decision decision, PolicySetMetadata source) {
        return new PolicySetDecision(decision, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED, null, source,
                null);
    }

    public static PolicySetDecision tracedSimpleDecision(Decision decision, PolicySetMetadata source,
            List<PolicyDecision> contributingPolicyDecisions) {
        return new PolicySetDecision(decision, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED, null, source,
                contributingPolicyDecisions);
    }

    public static PolicySetDecision error(ErrorValue error, PolicySetMetadata source,
            List<PolicyDecision> contributingPolicyDecisions) {
        return new PolicySetDecision(Decision.INDETERMINATE, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED,
                error, source, contributingPolicyDecisions);
    }

    public static PolicySetDecision notApplicable(PolicySetMetadata source, List<PolicyDecision> contributingAttributes) {
        return new PolicySetDecision(Decision.NOT_APPLICABLE, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED,
                null, source, contributingAttributes);
    }

    public PolicySetDecision with(List<PolicyDecision> moreContributingDecisions) {
        val mergedContributingAttributes = new ArrayList<PolicyDecision>();
        if (contributingPolicyDecisions != null) {
            mergedContributingAttributes.addAll(contributingPolicyDecisions);
        }
        if (moreContributingDecisions != null) {
            mergedContributingAttributes.addAll(moreContributingDecisions);
        }
        return new PolicySetDecision(decision, obligations, advice, resource, error, metadata,
                mergedContributingAttributes);
    }
}
