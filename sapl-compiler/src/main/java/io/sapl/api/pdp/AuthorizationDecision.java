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
package io.sapl.api.pdp;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.NonNull;
import lombok.val;

import java.util.List;

public record AuthorizationDecision(
        @NonNull Decision decision,
        @NonNull List<Value> obligations,
        @NonNull List<Value> advice,
        @NonNull Value resource) {
    public static final AuthorizationDecision PERMIT = new AuthorizationDecision(Decision.PERMIT, List.of(), List.of(),
            Value.UNDEFINED);
    public static final AuthorizationDecision DENY = new AuthorizationDecision(Decision.DENY, List.of(), List.of(),
            Value.UNDEFINED);
    public static final AuthorizationDecision INDETERMINATE = new AuthorizationDecision(Decision.INDETERMINATE,
            List.of(), List.of(), Value.UNDEFINED);
    public static final AuthorizationDecision NOT_APPLICABLE = new AuthorizationDecision(Decision.NOT_APPLICABLE,
            List.of(), List.of(), Value.UNDEFINED);

    /**
     * Creates an AuthorizationDecision from a Value object.
     * <p>
     * The Value must be an ObjectValue with the following structure:
     * <ul>
     * <li>"decision" - TextValue containing "PERMIT", "DENY", "INDETERMINATE", or
     * "NOT_APPLICABLE"</li>
     * <li>"obligations" - ArrayValue of constraint Values</li>
     * <li>"advice" - ArrayValue of advice Values</li>
     * <li>"resource" - Value representing the resource transformation</li>
     * </ul>
     *
     * @param decisionObject
     * the decision object value
     *
     * @return the AuthorizationDecision
     *
     * @throws IllegalArgumentException
     * if the value is not a valid decision object
     */
    public static AuthorizationDecision of(Value decisionObject) {
        if (!(decisionObject instanceof ObjectValue obj)) {
            throw new IllegalArgumentException("Decision value must be an ObjectValue, but was: " + decisionObject);
        }

        val decisionField = obj.get("decision");
        if (!(decisionField instanceof TextValue decisionText)) {
            throw new IllegalArgumentException("Decision field must be a TextValue, but was: " + decisionField);
        }

        val decision = Decision.valueOf(decisionText.value());

        var obligationsField = obj.get("obligations");
        if (obligationsField == null) {
            obligationsField = Value.EMPTY_ARRAY;
        }
        if (!(obligationsField instanceof ArrayValue obligationsArray)) {
            throw new IllegalArgumentException("Obligations field must be an ArrayValue, but was: " + obligationsField);
        }

        var adviceField = obj.get("advice");
        if (adviceField == null) {
            adviceField = Value.EMPTY_ARRAY;
        }
        if (!(adviceField instanceof ArrayValue adviceArray)) {
            throw new IllegalArgumentException("Advice field must be an ArrayValue, but was: " + adviceField);
        }

        var resource = obj.get("resource");
        if (resource == null) {
            resource = Value.UNDEFINED;
        }

        return new AuthorizationDecision(decision, obligationsArray, adviceArray, resource);
    }
}
