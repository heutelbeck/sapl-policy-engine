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

/**
 * Mutable context for SAPL compilation. Tracks imports, variable scopes, and
 * constant deduplication during document
 * compilation.
 * <p>
 * Variable scopes: Document-level variables persist across policies in a policy
 * set. Local policy variables are cleared
 * between policies via {@link #resetForNextPolicy()}. The context is reused
 * across documents via
 * {@link #resetForNextDocument()}.
 * <p>
 * Constant deduplication: Identical constant values share the same object
 * instance to reduce memory usage. Disabled
 * when debug information is enabled.
 */
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

    /**
     * Adds all imports from a SAPL document to this context.
     *
     * @param imports
     * the imports to add, may be null
     */
    public void addAllImports(List<Import> imports) {
        if (imports != null) {
            this.imports.addAll(imports);
        }
    }

    /**
     * Adds a document-level variable (from policy set var declarations). These
     * persist across all policies in the
     * document.
     *
     * @param variableName
     * the variable name
     * @param value
     * the compiled expression for the variable value
     *
     * @return true if added, false if variable already exists
     */
    public boolean addGlobalPolicySetVariable(String variableName, CompiledExpression value) {
        if (documentVariablesInScope.containsKey(variableName)) {
            return false;
        }
        documentVariablesInScope.put(variableName, value);
        return true;
    }

    /**
     * Adds a policy-local variable (from where clause). These are cleared between
     * policies.
     *
     * @param variableName
     * the variable name
     * @param value
     * the compiled expression for the variable value
     *
     * @return true if added, false if variable already exists
     */
    public boolean addLocalPolicyVariable(String variableName, CompiledExpression value) {
        if (documentVariablesInScope.containsKey(variableName)) {
            return false;
        }
        documentVariablesInScope.put(variableName, value);
        localVariableNames.add(variableName);
        return true;
    }

    /**
     * Clears all state for compiling a new SAPL document. Call before compiling
     * each document.
     */
    public void resetForNextDocument() {
        imports.clear();
        documentVariablesInScope.clear();
        localVariableNames.clear();
    }

    /**
     * Clears policy-local variables while preserving document-level variables. Call
     * between policies in a policy set.
     */
    public void resetForNextPolicy() {
        for (String localVariable : localVariableNames) {
            documentVariablesInScope.remove(localVariable);
        }
        localVariableNames.clear();
    }

    /**
     * Returns a deduplicated constant value. Identical constants share the same
     * object instance.
     *
     * @param constantValue
     * the constant to deduplicate
     *
     * @return the canonical instance of this constant
     */
    public CompiledExpression dedupe(Value constantValue) {
        if (debugInformationEnabled) {
            return constantValue;
        }
        return constantsCache.computeIfAbsent(constantValue, Function.identity());
    }

    /**
     * Retrieves a variable by name from the current scope.
     *
     * @param variableName
     * the variable name
     *
     * @return the compiled expression, or null if not found
     */
    public CompiledExpression getVariable(String variableName) {
        return documentVariablesInScope.get(variableName);
    }

    /**
     * Checks if a variable exists in the current scope.
     *
     * @param variableName
     * the variable name
     *
     * @return true if the variable exists
     */
    public boolean containsVariable(String variableName) {
        return documentVariablesInScope.containsKey(variableName);
    }
}
