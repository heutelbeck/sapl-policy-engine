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
package io.sapl.compiler.index;

import java.util.List;

import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.index.canonical.CanonicalPolicyIndex;
import io.sapl.compiler.index.naive.NaivePolicyIndex;
import io.sapl.compiler.index.smtdd.SmtddPolicyIndex;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Creates a {@link PolicyIndex} based on the indexing strategy name
 * from the compiler flags.
 */
@Slf4j
@UtilityClass
public class IndexFactory {

    private static final String ERROR_UNKNOWN_STRATEGY = "Unknown indexing strategy: '%s'. Valid values: AUTO, NAIVE, CANONICAL, SMTDD.";

    /**
     * Available indexing strategies. Internal to the index factory -
     * the public API uses a plain string in compilerOptions.
     */
    enum IndexingStrategy {
        AUTO,
        NAIVE,
        CANONICAL,
        SMTDD
    }

    /**
     * Creates a policy index for the given documents using the strategy
     * name from the compilation context's compiler flags.
     *
     * @param documents the compiled documents to index
     * @param ctx the compilation context containing the indexing strategy
     * @return a policy index
     */
    private static final int    DEFAULT_MIN_POLICIES_FOR_INDEXING = 10;
    private static final int    DEFAULT_MAX_INDEX_NODES           = 500_000;
    private static final String PARAM_MIN_POLICIES_FOR_INDEXING   = "minPoliciesForIndexing";
    private static final String PARAM_MAX_INDEX_NODES             = "maxIndexNodes";

    public static PolicyIndex createIndex(List<CompiledDocument> documents, CompilationContext ctx) {
        val options  = ctx.getCompilerOptions();
        val strategy = parseStrategy(getStringParam(options, "indexing", "AUTO"));
        val maxNodes = getIntParam(options, PARAM_MAX_INDEX_NODES, DEFAULT_MAX_INDEX_NODES);
        return switch (strategy) {
        case NAIVE     -> NaivePolicyIndex.create(documents);
        case CANONICAL -> CanonicalPolicyIndex.create(documents);
        case SMTDD     -> SmtddPolicyIndex.create(documents, maxNodes);
        case AUTO      -> autoSelect(documents, ctx);
        };
    }

    private static IndexingStrategy parseStrategy(String name) {
        try {
            return IndexingStrategy.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new SaplCompilerException(ERROR_UNKNOWN_STRATEGY.formatted(name), e);
        }
    }

    private static PolicyIndex autoSelect(List<CompiledDocument> documents, CompilationContext ctx) {
        val options     = ctx.getCompilerOptions();
        val minPolicies = getIntParam(options, PARAM_MIN_POLICIES_FOR_INDEXING, DEFAULT_MIN_POLICIES_FOR_INDEXING);
        val maxNodes    = getIntParam(options, PARAM_MAX_INDEX_NODES, DEFAULT_MAX_INDEX_NODES);

        if (documents.size() < minPolicies) {
            return NaivePolicyIndex.create(documents);
        }
        try {
            return SmtddPolicyIndex.create(documents, maxNodes);
        } catch (IndexSizeLimitExceededException e) {
            log.warn("SMTDD index exceeded node limit ({}), falling back to canonical index", e.getMessage());
        }
        return CanonicalPolicyIndex.create(documents);
    }

    private static int getIntParam(ObjectValue options, String key, int defaultValue) {
        val value = options.get(key);
        if (value instanceof NumberValue(var number)) {
            return number.intValue();
        }
        return defaultValue;
    }

    private static String getStringParam(ObjectValue options, String key, String defaultValue) {
        val value = options.get(key);
        if (value instanceof TextValue(var text)) {
            return text;
        }
        return defaultValue;
    }

}
