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

    /**
     * Creates a policy index for the given documents using the strategy and
     * compiler flags from the compilation context.
     *
     * @param documents the compiled documents to index
     * @param ctx the compilation context containing the indexing strategy
     * @return a policy index
     */
    public static PolicyIndex createIndex(List<CompiledDocument> documents, CompilationContext ctx) {
        return switch (ctx.getIndexingStrategy()) {
        case NAIVE     -> NaivePolicyIndex.create(documents);
        case CANONICAL -> CanonicalPolicyIndex.create(documents);
        case AUTO      -> autoSelect(documents, ctx);
        };
    }

    private static PolicyIndex autoSelect(List<CompiledDocument> documents, CompilationContext ctx) {
        val flags = ctx.getCompilerFlags();
        if (documents.size() < flags.minPoliciesForCanonical()) {
            return NaivePolicyIndex.create(documents);
        }
        val canonical = CanonicalPolicyIndex.create(documents);
        if (canonical.averageFormulasPerPredicate() < flags.minSharingForCanonical()) {
            return NaivePolicyIndex.create(documents);
        }
        return canonical;
    }

}
