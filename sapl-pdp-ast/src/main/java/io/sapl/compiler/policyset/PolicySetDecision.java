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
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.compiler.pdp.PDPDecision;
import io.sapl.compiler.policy.PolicyDecision;
import lombok.NonNull;
import lombok.val;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link PDPDecision} from a policy set with factory methods for common
 * decision types.
 *
 * @param authorizationDecision see {@link PDPDecision#authorizationDecision()}
 * @param metadata policy set-specific metadata including source and
 * contributing policies
 */
public record PolicySetDecision(
        @NonNull AuthorizationDecision authorizationDecision,
        PolicySetDecisionMetadata metadata) implements PDPDecision {

    /** Creates a decision with constraints. */
    public static PolicySetDecision decision(Decision decision, ArrayValue obligations, ArrayValue advice,
            Value resource, PolicySetMetadata source, List<PolicyDecision> contributingPolicyDecisions) {
        val authzDecision = new AuthorizationDecision(decision, obligations, advice, resource);
        val metadata      = new PolicySetDecisionMetadata(source, contributingPolicyDecisions, List.of(), null);
        return new PolicySetDecision(authzDecision, metadata);
    }

    /** Creates a decision with constraints and attribute tracing. */
    public static PolicySetDecision tracedDecision(Decision decision, ArrayValue obligations, ArrayValue advice,
            Value resource, PolicySetMetadata source, List<PolicyDecision> contributingPolicyDecisions,
            List<AttributeRecord> contributingAttributes) {
        val authzDecision = new AuthorizationDecision(decision, obligations, advice, resource);
        val metadata      = new PolicySetDecisionMetadata(source, contributingPolicyDecisions, contributingAttributes,
                null);
        return new PolicySetDecision(authzDecision, metadata);
    }

    /** Creates an INDETERMINATE decision from an error. */
    public static PolicySetDecision error(ErrorValue error, PolicySetMetadata source,
            List<PolicyDecision> contributingPolicyDecisions) {
        val metadata = new PolicySetDecisionMetadata(source, contributingPolicyDecisions, List.of(), error);
        return new PolicySetDecision(AuthorizationDecision.INDETERMINATE, metadata);
    }

    /** Creates an INDETERMINATE decision from an error with attribute tracing. */
    public static PolicySetDecision tracedError(ErrorValue error, PolicySetMetadata source,
            List<PolicyDecision> contributingPolicyDecisions, List<AttributeRecord> contributingAttributes) {
        val metadata = new PolicySetDecisionMetadata(source, contributingPolicyDecisions, contributingAttributes,
                error);
        return new PolicySetDecision(AuthorizationDecision.INDETERMINATE, metadata);
    }

    /** Creates a NOT_APPLICABLE decision. */
    public static PolicySetDecision notApplicable(PolicySetMetadata source,
            List<PolicyDecision> contributingPolicyDecisions) {
        val metadata = new PolicySetDecisionMetadata(source, contributingPolicyDecisions, List.of(), null);
        return new PolicySetDecision(AuthorizationDecision.NOT_APPLICABLE, metadata);
    }

    /** Creates a NOT_APPLICABLE decision with attribute tracing. */
    public static PolicySetDecision tracedNotApplicable(PolicySetMetadata source,
            List<PolicyDecision> contributingPolicyDecisions, List<AttributeRecord> contributingAttributes) {
        val metadata = new PolicySetDecisionMetadata(source, contributingPolicyDecisions, contributingAttributes, null);
        return new PolicySetDecision(AuthorizationDecision.NOT_APPLICABLE, metadata);
    }

    /**
     * Returns a new decision with additional contributing attributes merged into
     * metadata.
     */
    public PolicySetDecision with(List<AttributeRecord> moreContributingAttributes) {
        val mergedContributingAttributes   = new ArrayList<AttributeRecord>();
        val originalContributingAttributes = metadata.contributingAttributes();
        if (originalContributingAttributes != null) {
            mergedContributingAttributes.addAll(originalContributingAttributes);
        }
        if (moreContributingAttributes != null) {
            mergedContributingAttributes.addAll(moreContributingAttributes);
        }
        val newMetadata = new PolicySetDecisionMetadata(metadata.source(), metadata.contributingPolicyDecisions(),
                mergedContributingAttributes, metadata.error());
        return new PolicySetDecision(authorizationDecision, newMetadata);
    }
}
