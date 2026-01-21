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
package io.sapl.compiler.expressions;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.CompiledExpression;
import io.sapl.compiler.ast.Document;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Mutable context for SAPL compilation. Tracks imports, variable scopes, and
 * constant deduplication during document compilation.
 * <p>
 * Variable scopes: Document-level variables persist across policies in a policy
 * set. Local policy variables are cleared between policies via
 * {@link #resetForNextPolicy()}. The context is reused across documents via
 * {@link #resetForNextDocument()}.
 * <p>
 * Trace level: Controls granularity of trace information gathered during
 * evaluation. This is a compile-time vote affecting generated expressions.
 */
@Getter
@Setter
@ToString
public class CompilationContext {
    String                                  pdpId                    = "defaultPdp";
    String                                  configurationId          = "defaultConfiguration";
    Document                                document;
    String                                  documentSource;
    final FunctionBroker                    functionBroker;
    final AttributeBroker                   attributeBroker;
    private Map<String, CompiledExpression> documentVariablesInScope = new HashMap<>();
    private Set<String>                     localVariableNames       = new HashSet<>();
    private Supplier<String>                timestampSupplier        = () -> String.valueOf(System.currentTimeMillis());

    public CompilationContext(String pdpId,
            String configurationId,
            FunctionBroker functionBroker,
            AttributeBroker attributeBroker) {
        this.pdpId           = pdpId;
        this.configurationId = configurationId;
        this.functionBroker  = functionBroker;
        this.attributeBroker = attributeBroker;
    }

    public CompilationContext(String pdpId,
            String configurationId,
            FunctionBroker functionBroker,
            AttributeBroker attributeBroker,
            Supplier<String> timestampSupplier) {
        this.pdpId             = pdpId;
        this.configurationId   = configurationId;
        this.functionBroker    = functionBroker;
        this.attributeBroker   = attributeBroker;
        this.timestampSupplier = timestampSupplier;
    }

    /**
     * Creates a compilation context with the specified trace level.
     *
     * @param functionBroker the function broker for resolving functions
     * @param attributeBroker the attribute broker for resolving attributes
     */
    public CompilationContext(FunctionBroker functionBroker, AttributeBroker attributeBroker) {
        this.functionBroker  = functionBroker;
        this.attributeBroker = attributeBroker;
    }

    /**
     * Adds a document-level variable (from policy set var declarations). These
     * persist across all policies in the document.
     *
     * @param variableName the variable name
     * @param value the compiled expression for the variable value
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
     * @param variableName the variable name
     * @param value the compiled expression for the variable value
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
     * Retrieves a variable by name from the current scope.
     *
     * @param variableName the variable name
     * @return the compiled expression, or null if not found
     */
    public CompiledExpression getVariable(String variableName) {
        return documentVariablesInScope.get(variableName);
    }

    /**
     * Checks if a variable exists in the current scope.
     *
     * @param variableName the variable name
     * @return true if the variable exists
     */
    public boolean containsVariable(String variableName) {
        return documentVariablesInScope.containsKey(variableName);
    }

}
