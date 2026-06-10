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

import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.index.canonical.CanonicalPolicyIndex;
import io.sapl.compiler.index.naive.NaivePolicyIndex;
import io.sapl.compiler.index.smtdd.SmtddPolicyIndex;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.List;

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
    public static PolicyIndex createIndex(List<CompiledDocument> documents, CompilationContext ctx) {
        val strategy = parseStrategy(ctx.indexing());
        return switch (strategy) {
        case NAIVE     -> NaivePolicyIndex.create(documents);
        case CANONICAL -> CanonicalPolicyIndex.create(documents);
        case SMTDD     -> SmtddPolicyIndex.create(documents, ctx.maxIndexNodes());
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
        if (documents.size() < ctx.minPoliciesForIndexing()) {
            return NaivePolicyIndex.create(documents);
        }
        try {
            return SmtddPolicyIndex.create(documents, ctx.maxIndexNodes());
        } catch (IndexSizeLimitExceededException e) {
            log.warn("SMTDD index exceeded node limit ({}), falling back to canonical index", e.getMessage());
        }
        return CanonicalPolicyIndex.create(documents);
    }

}
