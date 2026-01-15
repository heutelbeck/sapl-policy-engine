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
package io.sapl.compiler.policy;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.AttributeRecord;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.compiler.pdp.PDPDecision;
import io.sapl.compiler.pdp.PolicyDecisionMetadata;
import lombok.NonNull;
import lombok.val;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link PDPDecision} from a single policy with factory methods for common
 * decision types.
 *
 * @param authorizationDecision see {@link PDPDecision#authorizationDecision()}
 * @param metadata policy-specific metadata including source policy
 */
public record PolicyDecision(@NonNull AuthorizationDecision authorizationDecision, PolicyDecisionMetadata metadata)
        implements PDPDecision {

    /** Creates a decision with constraints. */
    public static PolicyDecision decision(Decision decision, ArrayValue obligations, ArrayValue advice, Value resource,
            PolicyMetadata source) {
        val authzDecision = new AuthorizationDecision(decision, obligations, advice, resource);
        val metadata      = new PolicyDecisionMetadata(source, List.of(), null);
        return new PolicyDecision(authzDecision, metadata);
    }

    /** Creates a decision with constraints and attribute tracing. */
    public static PolicyDecision tracedDecision(Decision decision, ArrayValue obligations, ArrayValue advice,
            Value resource, PolicyMetadata source, List<AttributeRecord> contributingAttributes) {
        val authzDecision = new AuthorizationDecision(decision, obligations, advice, resource);
        val metadata      = new PolicyDecisionMetadata(source, contributingAttributes, null);
        return new PolicyDecision(authzDecision, metadata);
    }

    /** Creates an INDETERMINATE decision from an error. */
    public static PolicyDecision error(ErrorValue error, PolicyMetadata source) {
        val metadata = new PolicyDecisionMetadata(source, List.of(), error);
        return new PolicyDecision(AuthorizationDecision.INDETERMINATE, metadata);
    }

    /** Creates an INDETERMINATE decision from an error with attribute tracing. */
    public static PolicyDecision tracedError(ErrorValue error, PolicyMetadata source,
            List<AttributeRecord> contributingAttributes) {
        val metadata = new PolicyDecisionMetadata(source, contributingAttributes, error);
        return new PolicyDecision(AuthorizationDecision.INDETERMINATE, metadata);
    }

    /** Creates a NOT_APPLICABLE decision. */
    public static PolicyDecision notApplicable(PolicyMetadata source) {
        val metadata = new PolicyDecisionMetadata(source, List.of(), null);
        return new PolicyDecision(AuthorizationDecision.NOT_APPLICABLE, metadata);
    }

    /** Creates a NOT_APPLICABLE decision with attribute tracing. */
    public static PolicyDecision tracedNotApplicable(PolicyMetadata source,
            List<AttributeRecord> contributingAttributes) {
        val metadata = new PolicyDecisionMetadata(source, contributingAttributes, null);
        return new PolicyDecision(AuthorizationDecision.NOT_APPLICABLE, metadata);
    }

    /**
     * Returns a new decision with additional contributing attributes merged into
     * metadata.
     */
    public PolicyDecision with(List<AttributeRecord> moreContributingAttributes) {
        val mergedContributingAttributes   = new ArrayList<AttributeRecord>();
        val originalContributingAttributes = metadata.contributingAttributes();
        if (originalContributingAttributes != null) {
            mergedContributingAttributes.addAll(originalContributingAttributes);
        }
        if (moreContributingAttributes != null) {
            mergedContributingAttributes.addAll(moreContributingAttributes);
        }
        val newMetadata = new PolicyDecisionMetadata(metadata.source(), mergedContributingAttributes, metadata.error());
        return new PolicyDecision(authorizationDecision, newMetadata);
    }
}
