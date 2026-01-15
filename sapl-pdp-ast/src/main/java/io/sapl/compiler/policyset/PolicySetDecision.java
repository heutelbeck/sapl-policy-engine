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

import io.sapl.api.model.AttributeRecord;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.compiler.pdp.PDPDecision;
import io.sapl.compiler.policy.PolicyDecision;
import lombok.NonNull;
import lombok.val;

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

    /** Creates a decision. */
    public static PolicySetDecision decision(AuthorizationDecision authzDecision,
            List<PolicyDecision> contributingPolicyDecisions, PolicySetMetadata source) {
        val metadata = new PolicySetDecisionMetadata(source, contributingPolicyDecisions, List.of(), null);
        return new PolicySetDecision(authzDecision, metadata);
    }

    /** Creates a decision with attribute tracing. */
    public static PolicySetDecision tracedDecision(AuthorizationDecision authzDecision, PolicySetMetadata source,
            List<PolicyDecision> contributingPolicyDecisions, List<AttributeRecord> contributingAttributes) {
        val metadata = new PolicySetDecisionMetadata(source, contributingPolicyDecisions, contributingAttributes, null);
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
     * Returns a new decision with an additional contributing policy decision.
     */
    public PolicySetDecision with(PolicyDecision policyDecision) {
        return new PolicySetDecision(authorizationDecision, metadata.with(policyDecision));
    }

    /**
     * Returns a new decision with additional contributing attributes merged into
     * metadata.
     */
    public PolicySetDecision with(List<AttributeRecord> moreContributingAttributes) {
        return new PolicySetDecision(authorizationDecision, metadata.with(moreContributingAttributes));
    }
}
