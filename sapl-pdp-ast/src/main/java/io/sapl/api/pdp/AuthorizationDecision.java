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
        @NonNull Value resource) {
    public static final AuthorizationDecision PERMIT = new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY,
            Value.EMPTY_ARRAY, Value.UNDEFINED);
    public static final AuthorizationDecision DENY = new AuthorizationDecision(Decision.DENY, Value.EMPTY_ARRAY,
            Value.EMPTY_ARRAY, Value.UNDEFINED);
    public static final AuthorizationDecision INDETERMINATE = new AuthorizationDecision(Decision.INDETERMINATE,
            Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED);
    public static final AuthorizationDecision NOT_APPLICABLE = new AuthorizationDecision(Decision.NOT_APPLICABLE,
            Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED);

}
