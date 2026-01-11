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
package io.sapl.compiler.model;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.traced.AttributeRecord;
import lombok.NonNull;
import lombok.val;

import java.util.ArrayList;
import java.util.List;

public record AuditableAuthorizationDecision(
        @NonNull Decision decision,
        @NonNull ArrayValue obligations,
        @NonNull ArrayValue advice,
        @NonNull Value resource,
        Value error,
        DecisionSource source,
        List<AttributeRecord> contributingAttributes) implements CompiledDocument {

    public static AuditableAuthorizationDecision simpleDecision(Decision decision, DecisionSource source) {
        return new AuditableAuthorizationDecision(decision, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED, null,
                source, null);
    }

    public static AuditableAuthorizationDecision tracedSimpleDecision(Decision decision, DecisionSource source,
            List<AttributeRecord> contributingAttributes) {
        return new AuditableAuthorizationDecision(decision, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED, null,
                source, contributingAttributes);
    }

    public static AuditableAuthorizationDecision ofError(ErrorValue error, DecisionSource source) {
        return new AuditableAuthorizationDecision(Decision.INDETERMINATE, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                Value.UNDEFINED, error, source, null);
    }

    public static AuditableAuthorizationDecision tracedError(ErrorValue error, DecisionSource source,
            List<AttributeRecord> contributingAttributes) {
        return new AuditableAuthorizationDecision(Decision.INDETERMINATE, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                Value.UNDEFINED, error, source, contributingAttributes);
    }

    public static AuditableAuthorizationDecision notApplicable(DecisionSource source) {
        return new AuditableAuthorizationDecision(Decision.NOT_APPLICABLE, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                Value.UNDEFINED, null, source, null);
    }

    public static AuditableAuthorizationDecision tracedNotApplicable(DecisionSource source,
            List<AttributeRecord> contributingAttributes) {
        return new AuditableAuthorizationDecision(Decision.NOT_APPLICABLE, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                Value.UNDEFINED, null, source, contributingAttributes);
    }

    public AuditableAuthorizationDecision with(List<AttributeRecord> moreContributingAttributes) {
        val mergedContributingAttributes = new ArrayList<AttributeRecord>();
        if (contributingAttributes != null) {
            mergedContributingAttributes.addAll(contributingAttributes);
        }
        if (moreContributingAttributes != null) {
            mergedContributingAttributes.addAll(moreContributingAttributes);
        }
        return new AuditableAuthorizationDecision(decision, obligations, advice, resource, error, source,
                mergedContributingAttributes);
    }
}
