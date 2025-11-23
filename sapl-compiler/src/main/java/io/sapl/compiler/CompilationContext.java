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

import io.sapl.api.attributes.AttributeBroker;
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
    final AttributeBroker                   attributeBroker;
    final boolean                           debugInformationEnabled  = false;
    List<Import>                            imports                  = new ArrayList<>();
    private Map<String, CompiledExpression> documentVariablesInScope = new HashMap<>();
    Map<Value, Value>                       constantsCache           = new HashMap<>();
    private Set<String>                     localVariableNames       = new HashSet<>();

    public void addAllImports(List<Import> imports) {
        if (imports != null) {
            this.imports.addAll(imports);
        }
    }

    public boolean addGlobalPolicySetVariable(String variableName, CompiledExpression value) {
        if (documentVariablesInScope.containsKey(variableName)) {
            return false;
        }
        documentVariablesInScope.put(variableName, value);
        return true;
    }

    public boolean addLocalPolicyVariable(String variableName, CompiledExpression value) {
        if (documentVariablesInScope.containsKey(variableName)) {
            return false;
        }
        documentVariablesInScope.put(variableName, value);
        return true;
    }

    public void resetForNextDocument() {
        imports.clear();
        documentVariablesInScope.clear();
        localVariableNames.clear();
    }

    public void resetForNextPolicy() {
        for (String localVariable : localVariableNames) {
            documentVariablesInScope.remove(localVariable);
        }
        localVariableNames.clear();
    }

    public CompiledExpression dedupe(Value constantValue) {
        if (debugInformationEnabled) {
            return constantValue;
        }
        return constantsCache.computeIfAbsent(constantValue, Function.identity());
    }

    public CompiledExpression getVariable(String variableName) {
        return documentVariablesInScope.get(variableName);
    }

    public boolean containsVariable(String variableName) {
        return documentVariablesInScope.containsKey(variableName);
    }
}
