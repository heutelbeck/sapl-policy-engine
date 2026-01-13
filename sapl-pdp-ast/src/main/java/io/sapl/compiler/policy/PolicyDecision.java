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
import io.sapl.api.pdp.Decision;
import lombok.NonNull;
import lombok.val;

import java.util.ArrayList;
import java.util.List;

public record PolicyDecision(
        @NonNull Decision decision,
        @NonNull ArrayValue obligations,
        @NonNull ArrayValue advice,
        @NonNull Value resource,
        Value error,
        PolicyMetadata metadata,
        List<AttributeRecord> contributingAttributes) implements PolicyBody {

    public static PolicyDecision simpleDecision(Decision decision, PolicyMetadata source) {
        return new PolicyDecision(decision, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED, null, source, null);
    }

    public static PolicyDecision tracedSimpleDecision(Decision decision, PolicyMetadata source,
            List<AttributeRecord> contributingAttributes) {
        return new PolicyDecision(decision, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED, null, source,
                contributingAttributes);
    }

    public static PolicyDecision error(ErrorValue error, PolicyMetadata source) {
        return new PolicyDecision(Decision.INDETERMINATE, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED, error,
                source, null);
    }

    public static PolicyDecision tracedError(ErrorValue error, PolicyMetadata source,
            List<AttributeRecord> contributingAttributes) {
        return new PolicyDecision(Decision.INDETERMINATE, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED, error,
                source, contributingAttributes);
    }

    public static PolicyDecision notApplicable(PolicyMetadata source) {
        return new PolicyDecision(Decision.NOT_APPLICABLE, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED, null,
                source, null);
    }

    public static PolicyDecision tracedNotApplicable(PolicyMetadata source,
            List<AttributeRecord> contributingAttributes) {
        return new PolicyDecision(Decision.NOT_APPLICABLE, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED, null,
                source, contributingAttributes);
    }

    public PolicyDecision with(List<AttributeRecord> moreContributingAttributes) {
        val mergedContributingAttributes = new ArrayList<AttributeRecord>();
        if (contributingAttributes != null) {
            mergedContributingAttributes.addAll(contributingAttributes);
        }
        if (moreContributingAttributes != null) {
            mergedContributingAttributes.addAll(moreContributingAttributes);
        }
        return new PolicyDecision(decision, obligations, advice, resource, error, metadata,
                mergedContributingAttributes);
    }
}
