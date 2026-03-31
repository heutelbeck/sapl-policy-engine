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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.Value;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.document.Vote;
import lombok.val;

/**
 * Baseline policy index that evaluates each document's applicability linearly.
 * Equivalent to the pre-index behavior of the combining algorithm compilers.
 * <p>
 * Used as a comparison baseline for the canonical index and as the default for
 * small policy sets where indexing overhead is not justified.
 */
public class NaivePolicyIndex implements PolicyIndex {

    private final List<CompiledDocument> documents;

    private NaivePolicyIndex(List<CompiledDocument> documents) {
        this.documents = List.copyOf(documents);
    }

    /**
     * Creates a naive index over the given documents.
     *
     * @param documents the compiled documents to evaluate linearly
     * @return a naive policy index
     */
    public static NaivePolicyIndex create(List<CompiledDocument> documents) {
        return new NaivePolicyIndex(documents);
    }

    @Override
    public PolicyIndexResult match(EvaluationContext ctx) {
        val matchingDocuments = new ArrayList<CompiledDocument>();
        val errorVotes        = new ArrayList<Vote>();
        for (val document : documents) {
            val isApplicable = evaluateApplicability(document, ctx);
            if (isApplicable instanceof ErrorValue error) {
                errorVotes.add(Vote.error(error, document.metadata()));
            } else if (isApplicable instanceof BooleanValue(var b) && b) {
                matchingDocuments.add(document);
            }
        }
        return new PolicyIndexResult(matchingDocuments, errorVotes);
    }

    @Override
    public void matchWhile(EvaluationContext ctx, Predicate<PolicyIndexResult> shouldContinue) {
        for (val document : documents) {
            val isApplicable = evaluateApplicability(document, ctx);
            if (isApplicable instanceof ErrorValue error && !shouldContinue
                    .test(new PolicyIndexResult(List.of(), List.of(Vote.error(error, document.metadata()))))) {
                return;
            } else if (isApplicable instanceof BooleanValue(var b) && b
                    && !shouldContinue.test(new PolicyIndexResult(List.of(document), List.of()))) {
                return;
            }
        }
    }

    private static Value evaluateApplicability(CompiledDocument document, EvaluationContext ctx) {
        val expression = document.isApplicable();
        if (expression instanceof Value v) {
            return v;
        }
        return ((PureOperator) expression).evaluate(ctx);
    }

}
