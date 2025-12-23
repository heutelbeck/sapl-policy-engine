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

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.pdp.TraceLevel;
import io.sapl.parser.Document;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * evaluation. This is a compile-time decision affecting generated expressions.
 */
@Getter
@Setter
@ToString
public class CompilationContext {

    /**
     * Represents an import statement in a SAPL document.
     * An import brings a function into scope, optionally with an alias.
     *
     * @param fullyQualifiedName the fully qualified function name (e.g.,
     * "filter.blacken")
     * @param alias the local alias for the function, or null if no alias
     */
    public record ImportInfo(String fullyQualifiedName, String alias) {
        /**
         * Gets the name to use for this import in the local scope.
         * Returns the alias if present, otherwise returns the simple function name
         * (last part of the fully qualified name).
         */
        public String localName() {
            if (alias != null) {
                return alias;
            }
            int lastDot = fullyQualifiedName.lastIndexOf('.');
            return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
        }
    }

    Document                                document;
    String                                  documentSource;
    final FunctionBroker                    functionBroker;
    final AttributeBroker                   attributeBroker;
    final TraceLevel                        traceLevel;
    List<ImportInfo>                        imports                  = new ArrayList<>();
    private Map<String, CompiledExpression> documentVariablesInScope = new HashMap<>();
    private Set<String>                     localVariableNames       = new HashSet<>();

    /**
     * Creates a compilation context with the specified trace level.
     *
     * @param functionBroker the function broker for resolving functions
     * @param attributeBroker the attribute broker for resolving attributes
     * @param traceLevel the trace level controlling trace granularity
     */
    public CompilationContext(FunctionBroker functionBroker, AttributeBroker attributeBroker, TraceLevel traceLevel) {
        this.functionBroker  = functionBroker;
        this.attributeBroker = attributeBroker;
        this.traceLevel      = traceLevel;
    }

    /**
     * Creates a compilation context with STANDARD trace level.
     *
     * @param functionBroker the function broker for resolving functions
     * @param attributeBroker the attribute broker for resolving attributes
     */
    public CompilationContext(FunctionBroker functionBroker, AttributeBroker attributeBroker) {
        this(functionBroker, attributeBroker, TraceLevel.STANDARD);
    }

    /**
     * Adds an import to this context.
     *
     * @param fullyQualifiedName the fully qualified function name
     * @param alias the optional alias, may be null
     */
    public void addImport(String fullyQualifiedName, String alias) {
        imports.add(new ImportInfo(fullyQualifiedName, alias));
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

    /**
     * Checks if coverage tracking is enabled for this compilation.
     * <p>
     * When true, condition expressions should be wrapped to record coverage hits.
     * When false, no coverage overhead should be added.
     *
     * @return true if trace level is COVERAGE
     */
    public boolean isCoverageEnabled() {
        return traceLevel == TraceLevel.COVERAGE;
    }

    /**
     * Resolves a function name to its fully qualified name using imports.
     * If the name matches an import alias or simple name, returns the fully
     * qualified name.
     * Otherwise, returns the original name (which may already be fully qualified).
     *
     * @param functionName the function name to resolve
     * @return the resolved fully qualified function name
     */
    public String resolveFunctionName(String functionName) {
        for (ImportInfo importInfo : imports) {
            if (functionName.equals(importInfo.localName())) {
                return importInfo.fullyQualifiedName();
            }
        }
        return functionName;
    }

}
