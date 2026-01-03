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

import java.util.List;

import io.sapl.api.model.SourceLocation;
import lombok.NonNull;

/**
 * Environment attribute access: {@code <time.now>} or {@code |<time.now>}
 * <p>
 * For relative attributes applied to expressions (e.g.,
 * {@code subject.<role>}), use {@link AttributeStep} as a step on the
 * base expression instead.
 *
 * @param name qualified attribute name
 * @param arguments attribute arguments, empty list if none
 * @param options attribute finder options expression, or null if none
 * @param head true for head attribute finder (|<), false for normal (<)
 * @param location source location
 */
public record EnvironmentAttribute(
        @NonNull QualifiedName name,
        @NonNull List<Expression> arguments,
        Expression options,
        boolean head,
        @NonNull SourceLocation location) implements Expression {

    public EnvironmentAttribute {
        arguments = List.copyOf(arguments);
    }

    @Override
    public Nature nature() {
        return Nature.STREAM;
    }

}
