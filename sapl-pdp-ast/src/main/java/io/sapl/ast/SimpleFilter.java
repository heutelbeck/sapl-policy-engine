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
 * Simple filter operation: {@code base |- func(args)} or
 * {@code base |- each func(args)}.
 * <p>
 * The filter function receives the base value (bound to {@code @}) as an
 * implicit first argument. Additional explicit arguments are provided in
 * {@code arguments}.
 *
 * @param base the expression being filtered
 * @param name the qualified name of the filter function
 * @param arguments explicit arguments to the filter function (excluding @)
 * @param each true if filter applies to each element of target array/object
 * @param location metadata location
 */
public record SimpleFilter(
        @NonNull Expression base,
        @NonNull QualifiedName name,
        @NonNull List<Expression> arguments,
        boolean each,
        @NonNull SourceLocation location) implements Expression {}
