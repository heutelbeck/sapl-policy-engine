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
package io.sapl.ast;

import io.sapl.api.model.SourceLocation;
import lombok.NonNull;

import java.util.List;

/**
 * A single policy.
 *
 * @param name the policy name
 * @param entitlement PERMIT or DENY
 * @param target target expression, or null if policy applies universally
 * @param body policy body with statements and source location
 * @param obligations obligation expressions, empty list if none
 * @param advice advice expressions, empty list if none
 * @param transformation transformation expression, or null if none
 * @param location source location
 */
public record Policy(
        @NonNull String name,
        @NonNull Entitlement entitlement,
        Expression target,
        @NonNull PolicyBody body,
        @NonNull List<Expression> obligations,
        @NonNull List<Expression> advice,
        Expression transformation,
        @NonNull SourceLocation location) implements PolicyElement {
    public Policy {
        obligations = List.copyOf(obligations);
        advice      = List.copyOf(advice);
    }
}
