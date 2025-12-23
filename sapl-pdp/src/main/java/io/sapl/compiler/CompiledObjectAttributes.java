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
package io.sapl.compiler;

import io.sapl.api.model.CompiledExpression;

import java.util.Map;

/**
 * Compiled object literal attributes (key-value pairs) with their evaluation
 * nature.
 *
 * @param nature
 * indicates if all values are constants (VALUE), contain pure expressions
 * (PURE), or contain streams
 * (STREAM)
 * @param isSubscriptionScoped
 * true if any attribute value depends on subscription variables
 * @param attributes
 * map of attribute names to compiled value expressions
 */
public record CompiledObjectAttributes(
        Nature nature,
        boolean isSubscriptionScoped,
        Map<String, CompiledExpression> attributes) {
    public static final CompiledObjectAttributes EMPTY_ATTRIBUTES = new CompiledObjectAttributes(Nature.VALUE, false,
            Map.of());
}
