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
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.CompilerFlags;
import io.sapl.api.pdp.PdpData;
import io.sapl.compiler.document.Document;
import io.sapl.compiler.util.DummyEvaluationContextFactory;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

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
@Slf4j
@Getter
@Setter
@ToString
public class CompilationContext {
    private String                          pdpId                    = "defaultPdp";
    private String                          configurationId          = "defaultConfiguration";
    private Document                        document;
    private String                          documentSource;
    final FunctionBroker                    functionBroker;
    final AttributeBroker                   attributeBroker;
    final PdpData                           data;
    private Map<String, CompiledExpression> documentVariablesInScope = new HashMap<>();
    private Set<String>                     localVariableNames       = new HashSet<>();
    private final Map<Value, Value>         valueDedup               = new HashMap<>();
    private Supplier<String>                timestampSupplier        = () -> String.valueOf(System.currentTimeMillis());
    private CompilerFlags                   compilerFlags            = CompilerFlags.defaults();
    private Map<Long, Value>                foldingCache             = new HashMap<>();

    public CompilationContext(String pdpId,
            String configurationId,
            PdpData data,
            FunctionBroker functionBroker,
            AttributeBroker attributeBroker) {
        this.pdpId           = pdpId;
        this.configurationId = configurationId;
        this.functionBroker  = functionBroker;
        this.attributeBroker = attributeBroker;
        this.data            = data;
    }

    public CompilationContext(String pdpId,
            String configurationId,
            PdpData data,
            FunctionBroker functionBroker,
            AttributeBroker attributeBroker,
            Supplier<String> timestampSupplier) {
        this.pdpId             = pdpId;
        this.configurationId   = configurationId;
        this.functionBroker    = functionBroker;
        this.attributeBroker   = attributeBroker;
        this.timestampSupplier = timestampSupplier;
        this.data              = data;
    }

    public CompilationContext(PdpData data, FunctionBroker functionBroker, AttributeBroker attributeBroker) {
        this.functionBroker  = functionBroker;
        this.attributeBroker = attributeBroker;
        this.data            = data;
    }

    /**
     * @param functionBroker the function broker for resolving functions
     * @param attributeBroker the attribute broker for resolving attributes
     */
    public CompilationContext(FunctionBroker functionBroker, AttributeBroker attributeBroker) {
        this.functionBroker  = functionBroker;
        this.attributeBroker = attributeBroker;
        this.data            = new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
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
     * Central compile-time optimization: folds, caches, and deduplicates.
     * <ul>
     * <li>Value: deduplicates via identity map</li>
     * <li>StreamOperator: returns as-is</li>
     * <li>PureOperator depending on subscription or relative context:
     * returns as-is (cannot fold outside its context)</li>
     * <li>PureOperator that is foldable: checks semantic hash cache,
     * evaluates on miss, caches and deduplicates result</li>
     * </ul>
     *
     * @param expression the compiled expression
     * @return the folded/cached/deduped expression
     */
    public CompiledExpression foldCacheDedupe(CompiledExpression expression) {
        return switch (expression) {
        case Value value       -> dedupeValue(value);
        case StreamOperator so -> so;
        case PureOperator po   -> canFold(po) ? cacheOrFold(po) : po;
        };
    }

    private static boolean canFold(PureOperator po) {
        return !po.isDependingOnSubscription() && !po.isRelativeExpression();
    }

    private Value dedupeValue(Value value) {
        val existing = valueDedup.putIfAbsent(value, value);
        return existing != null ? existing : value;
    }

    /**
     * Evaluates a foldable PureOperator at compile time, caching the result
     * by semantic hash. Subsequent calls with the same semantic hash return
     * the cached result without re-evaluation.
     *
     * @param po the pure operator to fold (must not depend on subscription
     * or relative context)
     * @return the folded Value
     */
    private Value cacheOrFold(PureOperator po) {
        val hash     = po.semanticHash();
        val cacheHit = foldingCache.get(hash);
        if (cacheHit != null) {
            return cacheHit;
        }
        val foldingContext = DummyEvaluationContextFactory.dummyContext(this);
        val result         = dedupeValue(po.evaluate(foldingContext));
        foldingCache.put(hash, result);
        return result;
    }

    /**
     * Retrieves a variable by name from the current scope.
     *
     * @param variableName the variable name
     * @return the compiled expression, or null if not found
     */
    public CompiledExpression getVariable(String variableName) {
        val inScopeVariable = documentVariablesInScope.get(variableName);
        if (inScopeVariable != null) {
            return inScopeVariable;
        }
        val variable = data.variables().get(variableName);
        if (variable != null) {
            return variable;
        }
        log.debug(
                "While compiling, a policy was detected trying to access the undefined environment variable {} this will always evaluate to 'undefined'",
                variableName);
        return Value.UNDEFINED;
    }

}
