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

import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.*;
import io.sapl.api.pdp.configuration.PdpData;
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
    public static final String OPTION_INDEXING                  = "indexing";
    public static final String OPTION_LOW_LATENCY_MODE          = "lowLatencyMode";
    public static final String OPTION_MAX_INDEX_NODES           = "maxIndexNodes";
    public static final String OPTION_MAX_POLICY_DOCUMENTS      = "maxPolicyDocuments";
    public static final String OPTION_MIN_POLICIES_FOR_INDEXING = "minPoliciesForIndexing";
    public static final String OPTION_UNROLL_IN_OPERATOR        = "unrollInOperator";

    public static final String DEFAULT_INDEXING                  = "AUTO";
    public static final int    DEFAULT_MAX_INDEX_NODES           = 500_000;
    public static final int    DEFAULT_MAX_POLICY_DOCUMENTS      = 10_000;
    public static final int    DEFAULT_MIN_POLICIES_FOR_INDEXING = 10;

    private String                          pdpId                    = "defaultPdp";
    private String                          configurationId          = "defaultConfiguration";
    private Document                        document;
    private String                          documentSource;
    final FunctionBroker                    functionBroker;
    final PdpData                           data;
    private Map<String, CompiledExpression> documentVariablesInScope = new HashMap<>();
    private Set<String>                     localVariableNames       = new HashSet<>();
    private final Map<Value, Value>         valueDedup               = new HashMap<>();
    private Supplier<String>                timestampSupplier        = () -> String.valueOf(System.currentTimeMillis());
    private ObjectValue                     compilerOptions          = Value.EMPTY_OBJECT;
    private Map<Long, Value>                foldingCache             = new HashMap<>();

    public CompilationContext(String pdpId, String configurationId, PdpData data, FunctionBroker functionBroker) {
        this.pdpId           = pdpId;
        this.configurationId = configurationId;
        this.functionBroker  = functionBroker;
        this.data            = data;
    }

    public CompilationContext(String pdpId,
            String configurationId,
            PdpData data,
            FunctionBroker functionBroker,
            Supplier<String> timestampSupplier) {
        this.pdpId             = pdpId;
        this.configurationId   = configurationId;
        this.functionBroker    = functionBroker;
        this.timestampSupplier = timestampSupplier;
        this.data              = data;
    }

    public CompilationContext(PdpData data, FunctionBroker functionBroker) {
        this.functionBroker = functionBroker;
        this.data           = data;
    }

    /**
     * @param functionBroker the function broker for resolving functions
     */
    public CompilationContext(FunctionBroker functionBroker) {
        this.functionBroker = functionBroker;
        this.data           = new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
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
     * Reads a boolean-valued option from any {@link ObjectValue}, returning the
     * supplied default when the key is absent or the stored value is not a
     * {@link BooleanValue}. Forgiving by design: a misconfigured non-boolean
     * value behaves as if the option were not set.
     *
     * @param options the options object to read from
     * @param key the option key
     * @param defaultValue the value returned when the option is absent or
     * malformed
     * @return the option's boolean value, or {@code defaultValue}
     */
    public static boolean booleanOption(ObjectValue options, String key, boolean defaultValue) {
        return options.getOrDefault(key, Value.of(defaultValue)) instanceof BooleanValue(var v) ? v : defaultValue;
    }

    /**
     * Reads an integer-valued option from any {@link ObjectValue}, returning the
     * supplied default when the key is absent or the stored value is not a
     * {@link NumberValue}.
     *
     * @param options the options object to read from
     * @param key the option key
     * @param defaultValue the value returned when the option is absent or
     * malformed
     * @return the option's integer value, or {@code defaultValue}
     */
    public static int intOption(ObjectValue options, String key, int defaultValue) {
        return options.get(key) instanceof NumberValue(var n) ? n.intValue() : defaultValue;
    }

    /**
     * Reads a string-valued option from any {@link ObjectValue}, returning the
     * supplied default when the key is absent or the stored value is not a
     * {@link TextValue}.
     *
     * @param options the options object to read from
     * @param key the option key
     * @param defaultValue the value returned when the option is absent or
     * malformed
     * @return the option's string value, or {@code defaultValue}
     */
    public static String stringOption(ObjectValue options, String key, String defaultValue) {
        return options.get(key) instanceof TextValue(var t) ? t : defaultValue;
    }

    /**
     * Instance shortcut for {@link #booleanOption(ObjectValue, String, boolean)}
     * bound to this context's {@code compilerOptions}.
     *
     * @param key the option key
     * @param defaultValue the value returned when the option is absent or
     * malformed
     * @return the option's boolean value, or {@code defaultValue}
     */
    public boolean booleanCompilerOption(String key, boolean defaultValue) {
        return booleanOption(compilerOptions, key, defaultValue);
    }

    /**
     * Instance shortcut for {@link #intOption(ObjectValue, String, int)} bound
     * to this context's {@code compilerOptions}.
     *
     * @param key the option key
     * @param defaultValue the value returned when the option is absent or
     * malformed
     * @return the option's integer value, or {@code defaultValue}
     */
    public int intCompilerOption(String key, int defaultValue) {
        return intOption(compilerOptions, key, defaultValue);
    }

    /**
     * Instance shortcut for
     * {@link #stringOption(ObjectValue, String, String)} bound to this context's
     * {@code compilerOptions}.
     *
     * @param key the option key
     * @param defaultValue the value returned when the option is absent or
     * malformed
     * @return the option's string value, or {@code defaultValue}
     */
    public String stringCompilerOption(String key, String defaultValue) {
        return stringOption(compilerOptions, key, defaultValue);
    }

    /**
     * Whether the compiler should emit eager operator variants that subscribe
     * all children in parallel for the lowest end-to-end decision latency, at
     * the cost of subscribing to children whose values may turn out to be
     * unneeded (after errors or short-circuit values resolve later children).
     * <p>
     * When {@code true} (default): operators emit eager variants. Per
     * evaluation pass they walk every child to accumulate the maximum
     * subscription set, so the trigger loop can subscribe everything in
     * parallel and converge in a single round.
     * <p>
     * When {@code false}: operators emit lazy variants. Per evaluation pass
     * they short-circuit on the first {@code null} (incomplete) or
     * {@link io.sapl.api.model.ErrorValue} child without subscribing later
     * children. Smaller subscription set per round; convergence may take
     * multiple rounds for independent missing dependencies but never
     * subscribes to children whose values turn out to be unneeded.
     * <p>
     * Observable result is identical across both modes; the difference is
     * the per-pass subscription set size and the number of trigger-loop
     * rounds to reach a stable answer.
     *
     * @return {@code true} for eager subscription (low end-to-end latency),
     * {@code false} for lazy subscription (minimal subscription cost)
     */
    public boolean lowLatencyMode() {
        return booleanCompilerOption(OPTION_LOW_LATENCY_MODE, true);
    }

    /**
     * Whether the {@code in} operator should attempt array-unrolling at compile
     * time when its right-hand operand is an array literal.
     *
     * @return {@code true} to enable in-array unrolling, {@code false} otherwise
     */
    public boolean unrollInOperator() {
        return booleanCompilerOption(OPTION_UNROLL_IN_OPERATOR, false);
    }

    /**
     * The selected policy-indexing strategy name (e.g. {@code "AUTO"},
     * {@code "NAIVE"}, {@code "CANONICAL"}, {@code "SMTDD"}).
     *
     * @return the indexing strategy name
     */
    public String indexing() {
        return stringCompilerOption(OPTION_INDEXING, DEFAULT_INDEXING);
    }

    /**
     * Minimum policy count below which the AUTO indexing strategy stays on the
     * naive index instead of building the full structured index.
     *
     * @return the minimum-policy threshold
     */
    public int minPoliciesForIndexing() {
        return intCompilerOption(OPTION_MIN_POLICIES_FOR_INDEXING, DEFAULT_MIN_POLICIES_FOR_INDEXING);
    }

    /**
     * Maximum SMTDD index-node budget; exceeding this triggers fallback to the
     * canonical index.
     *
     * @return the SMTDD node budget
     */
    public int maxIndexNodes() {
        return intCompilerOption(OPTION_MAX_INDEX_NODES, DEFAULT_MAX_INDEX_NODES);
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
