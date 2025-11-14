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

import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.Value;
import io.sapl.grammar.sapl.Import;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.*;
import java.util.function.Function;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
public class CompilationContext {
    final FunctionBroker                    functionBroker;
    boolean                                 isInsideTargetExpression = false;
    final boolean                           debugInformationEnabled  = false;
    List<Import>                            imports                  = new ArrayList<>();
    Map<SchemaTarget, List<CompiledSchema>> schemas                  = new EnumMap<>(SchemaTarget.class);
    Map<String, CompiledExpression>         localVariablesInScope    = new HashMap<>();
    Map<Value, Value>                       constants                = new HashMap<>();

    public void addAllImports(List<Import> imports) {
        if (imports != null) {
            this.imports.addAll(imports);
        }
    }

    public void resetForNextDocument() {
        imports.clear();
        localVariablesInScope.clear();
        schemas.clear();
    }

    public CompiledExpression dedupe(Value constantValue) {
        if (debugInformationEnabled) {
            return constantValue;
        }
        return constants.computeIfAbsent(constantValue, Function.identity());
    }

}
