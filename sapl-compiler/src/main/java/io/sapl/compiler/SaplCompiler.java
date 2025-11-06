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

import io.sapl.api.value.CompiledExpression;
import io.sapl.grammar.sapl.*;

import java.lang.Object;
import java.util.Map;

public class SaplCompiler {

    public record Schema(Value schema, boolean enforced, Object compiledSchema) {
        public Schema(Value schema, boolean enforced) {
            this(schema, enforced, null);
        }
    }

    public record CompilationContext(
            Object imports,
            Object schemas,
            Map<String, CompiledExpression> variablesInScope) {}

    public CompiledDocument compile(SAPL document) {
        // 1. Load imports into context

        // 2. Load schemata into context
        // Schema expressions must evaluate to constant values !
        // ID must be one of subject, action, resource or environment

        return null;
    }

    public CompiledExpression compile(Expression expression) {
        return switch (expression) {
        case Or or             -> null;
        case EagerOr eagerOr   -> null;
        case And and           -> null;
        case EagerAnd eagerAnd -> null;
        case Not not           -> null;
        case Equals equals     -> null;
        case Less less         -> null;
        default                -> null;
        };
    }
}
