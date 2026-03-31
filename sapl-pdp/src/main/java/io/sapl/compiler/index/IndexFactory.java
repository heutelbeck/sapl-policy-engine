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

import io.sapl.api.pdp.IndexingStrategy;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.index.canonical.CanonicalPolicyIndex;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Creates a {@link PolicyIndex} based on the configured
 * {@link IndexingStrategy}.
 */
@UtilityClass
public class IndexFactory {

    private static final int    MIN_POLICIES_FOR_CANONICAL = 5;
    private static final double MIN_SHARING_FOR_CANONICAL  = 1.5;

    /**
     * Creates a policy index for the given documents using the strategy from the
     * compilation context.
     *
     * @param documents the compiled documents to index
     * @param ctx the compilation context containing the indexing strategy
     * @return a policy index
     */
    public static PolicyIndex createIndex(List<CompiledDocument> documents, CompilationContext ctx) {
        return createIndex(documents, ctx.getIndexingStrategy());
    }

    /**
     * Creates a policy index for the given documents using the given strategy.
     *
     * @param documents the compiled documents to index
     * @param strategy the indexing strategy
     * @return a policy index
     */
    public static PolicyIndex createIndex(List<CompiledDocument> documents, IndexingStrategy strategy) {
        return switch (strategy) {
        case NAIVE     -> NaivePolicyIndex.create(documents);
        case CANONICAL -> CanonicalPolicyIndex.create(documents);
        case AUTO      -> autoSelect(documents);
        };
    }

    private static PolicyIndex autoSelect(List<CompiledDocument> documents) {
        if (documents.size() < MIN_POLICIES_FOR_CANONICAL) {
            return NaivePolicyIndex.create(documents);
        }
        val canonical = CanonicalPolicyIndex.create(documents);
        if (canonical.averageFormulasPerPredicate() < MIN_SHARING_FOR_CANONICAL) {
            return NaivePolicyIndex.create(documents);
        }
        return canonical;
    }

}
