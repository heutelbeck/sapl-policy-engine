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
import io.sapl.api.value.Value;
import io.sapl.grammar.sapl.Import;
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Data
@ToString
public class CompilationContext {
    List<Import>                            imports          = new ArrayList<>();
    Map<SchemaTarget, List<CompiledSchema>> schemas          = new HashMap<>();
    Map<String, CompiledExpression>         variablesInScope = new HashMap<>();
    Map<Value, Value>                       constants        = new HashMap<>();

    public void addAllImports(List<Import> imports) {
        if (imports != null) {
            this.imports.addAll(imports);
        }
    }

    public void resetForNextDocument() {
        imports.clear();
        variablesInScope.clear();
        schemas.clear();
    }

    public CompiledExpression dedupe(Value constantValue) {
        return constants.computeIfAbsent(constantValue, Function.identity());
    }
}
