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

/**
 * Represents the result of a policy evaluation by the PDP.
 * <p>
 * Contains the authorization decision along with optional obligations, advice,
 * and a potentially transformed resource.
 *
 * @param decision the authorization decision (PERMIT, DENY, INDETERMINATE,
 * NOT_APPLICABLE)
 * @param obligations constraints that must be fulfilled for the decision to be
 * valid
 * @param advice optional recommendations that should be considered
 * @param resource potentially transformed resource, or UNDEFINED if unchanged
 */
public record AuthorizationDecision(
        @NonNull Decision decision,
        @NonNull ArrayValue obligations,
        @NonNull ArrayValue advice,
        @NonNull Value resource) {

    /**
     * Singleton for a simple PERMIT decision without obligations, advice, or
     * resource transformation.
     */
    public static final AuthorizationDecision PERMIT = new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY,
            Value.EMPTY_ARRAY, Value.UNDEFINED);

    /**
     * Singleton for a simple DENY decision without obligations, advice, or resource
     * transformation.
     */
    public static final AuthorizationDecision DENY = new AuthorizationDecision(Decision.DENY, Value.EMPTY_ARRAY,
            Value.EMPTY_ARRAY, Value.UNDEFINED);

    /**
     * Singleton for an INDETERMINATE decision, indicating policy evaluation could
     * not reach a conclusion.
     */
    public static final AuthorizationDecision INDETERMINATE = new AuthorizationDecision(Decision.INDETERMINATE,
            Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED);

    /**
     * Singleton for a NOT_APPLICABLE decision, indicating no policies matched the
     * subscription.
     */
    public static final AuthorizationDecision NOT_APPLICABLE = new AuthorizationDecision(Decision.NOT_APPLICABLE,
            Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED);

}
