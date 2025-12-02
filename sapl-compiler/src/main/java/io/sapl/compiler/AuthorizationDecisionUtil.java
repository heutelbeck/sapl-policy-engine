/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.compiler;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import lombok.experimental.UtilityClass;

import java.util.List;

/**
 * Utility for constructing and extracting authorization decision objects.
 * <p>
 * Decision objects are {@link ObjectValue} instances with fields: decision,
 * obligations, advice, resource. This class
 * centralizes field name constants and provides methods for building and
 * extracting decision components.
 */
@UtilityClass
public class AuthorizationDecisionUtil {

    public static final String FIELD_ADVICE      = "advice";
    public static final String FIELD_DECISION    = "decision";
    public static final String FIELD_OBLIGATIONS = "obligations";
    public static final String FIELD_RESOURCE    = "resource";

    public static final Value NOT_APPLICABLE = buildDecision(Decision.NOT_APPLICABLE, List.of(), List.of(),
            Value.UNDEFINED);
    public static final Value INDETERMINATE  = buildDecision(Decision.INDETERMINATE, List.of(), List.of(),
            Value.UNDEFINED);
    public static final Value DENY           = buildDecision(Decision.DENY, List.of(), List.of(), Value.UNDEFINED);
    public static final Value PERMIT         = buildDecision(Decision.PERMIT, List.of(), List.of(), Value.UNDEFINED);

    /**
     * Builds a decision object containing the authorization decision and associated
     * constraints.
     *
     * @param decision
     * the authorization decision (PERMIT, DENY, INDETERMINATE, NOT_APPLICABLE)
     * @param obligations
     * obligations that must be fulfilled for the decision to be valid
     * @param advice
     * advice that should be considered but is not mandatory
     * @param resource
     * optional resource transformation result, or UNDEFINED if no transformation
     *
     * @return an ObjectValue with fields: decision, obligations, advice, resource
     */
    public static Value buildDecision(Decision decision, List<Value> obligations, List<Value> advice, Value resource) {
        return ObjectValue.builder().put(FIELD_DECISION, Value.of(decision.name()))
                .put(FIELD_OBLIGATIONS, ArrayValue.builder().addAll(obligations).build())
                .put(FIELD_ADVICE, ArrayValue.builder().addAll(advice).build()).put(FIELD_RESOURCE, resource).build();
    }

    /**
     * Extracts the Decision enum from a decision object. Returns null if the value
     * is not a valid decision object.
     *
     * @param decisionValue
     * the value to extract the decision from
     *
     * @return the Decision enum, or null if invalid structure or unknown decision
     */
    public static Decision extractDecision(Value decisionValue) {
        if (!(decisionValue instanceof ObjectValue objectValue)) {
            return null;
        }
        var decisionAttribute = objectValue.get(FIELD_DECISION);
        if (!(decisionAttribute instanceof TextValue textValue)) {
            return null;
        }
        try {
            return Decision.valueOf(textValue.value());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
