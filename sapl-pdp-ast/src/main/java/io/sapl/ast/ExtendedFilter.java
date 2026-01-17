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
 * Extended filter with path navigation: {@code base |- { @.path : func(args)
 * }}.
 * <p>
 * Extended filters navigate to a target location within the base value, apply
 * the filter function at that location, and reconstruct the structure with the
 * modified value.
 *
 * @param base the expression to filter (root for path navigation)
 * @param target the path to the target location
 * @param name the qualified name of the filter function
 * @param arguments explicit arguments to the filter function (excluding @)
 * @param each true if filter applies to each element at target
 * @param location voterMetadata location
 */
public record ExtendedFilter(
        @NonNull Expression base,
        @NonNull FilterPath target,
        @NonNull QualifiedName name,
        @NonNull List<Expression> arguments,
        boolean each,
        @NonNull SourceLocation location) implements Expression {}
