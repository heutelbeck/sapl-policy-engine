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
package io.sapl.api.pdp;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.Value;
import lombok.NonNull;

public record AuthorizationDecision(
        @NonNull Decision decision,
        @NonNull ArrayValue obligations,
        @NonNull ArrayValue advice,
        @NonNull Value resource,
        @NonNull Value error) implements CompiledDocument {
    public static final AuthorizationDecision PERMIT = new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY,
            Value.EMPTY_ARRAY, Value.UNDEFINED, Value.UNDEFINED);
    public static final AuthorizationDecision DENY = new AuthorizationDecision(Decision.DENY, Value.EMPTY_ARRAY,
            Value.EMPTY_ARRAY, Value.UNDEFINED, Value.UNDEFINED);
    public static final AuthorizationDecision INDETERMINATE = ofError("Unspecified failure.");
    public static final AuthorizationDecision NOT_APPLICABLE = new AuthorizationDecision(Decision.NOT_APPLICABLE,
            Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED, Value.UNDEFINED);

    public static AuthorizationDecision ofError(String errorMessage) {
        return new AuthorizationDecision(Decision.INDETERMINATE, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED,
                Value.error(errorMessage));
    }

    /**
     * Creates an AuthorizationDecision from an ObjectValue representation.
     *
     * @param value the ObjectValue containing decision fields
     * @return the AuthorizationDecision
     * @throws IllegalArgumentException if the value is not a valid
     * AuthorizationDecision
     */
    public static AuthorizationDecision of(Value value) {
        if (!(value instanceof io.sapl.api.model.ObjectValue obj)) {
            throw new IllegalArgumentException("AuthorizationDecision must be an ObjectValue");
        }

        var decisionValue = obj.get("decision");
        if (!(decisionValue instanceof io.sapl.api.model.TextValue tv)) {
            throw new IllegalArgumentException("Decision field must be a TextValue");
        }
        var decision = Decision.valueOf(tv.value());

        var obligationsValue = obj.getOrDefault("obligations", Value.EMPTY_ARRAY);
        if (!(obligationsValue instanceof ArrayValue obligations)) {
            throw new IllegalArgumentException("Obligations field must be an ArrayValue");
        }

        var adviceValue = obj.getOrDefault("advice", Value.EMPTY_ARRAY);
        if (!(adviceValue instanceof ArrayValue advice)) {
            throw new IllegalArgumentException("Advice field must be an ArrayValue");
        }

        var resource = obj.getOrDefault("resource", Value.UNDEFINED);
        var error    = obj.getOrDefault("error", Value.UNDEFINED);

        return new AuthorizationDecision(decision, obligations, advice, resource, error);
    }
}
