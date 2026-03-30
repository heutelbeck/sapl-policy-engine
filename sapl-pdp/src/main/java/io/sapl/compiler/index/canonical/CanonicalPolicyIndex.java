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
package io.sapl.compiler.index.canonical;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.Value;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.index.DisjunctiveFormula;
import io.sapl.compiler.index.PolicyIndex;
import io.sapl.compiler.index.PolicyIndexResult;
import lombok.val;

/**
 * Canonical policy index implementation using the count-and-eliminate algorithm
 * from the SACMAT '21 paper.
 * <p>
 * Holds both indexed formulas (searched via {@link CanonicalIndexSearch}) and a
 * fallback list of non-indexable documents that are evaluated linearly. In the
 * worst case (nothing indexable), this degrades to exactly the pre-index linear
 * scan behavior.
 */
public class CanonicalPolicyIndex implements PolicyIndex {

    private final CanonicalIndexData     indexData;
    private final List<CompiledDocument> fallbackDocuments;

    private CanonicalPolicyIndex(CanonicalIndexData indexData, List<CompiledDocument> fallbackDocuments) {
        this.indexData         = indexData;
        this.fallbackDocuments = List.copyOf(fallbackDocuments);
    }

    /**
     * Creates an index from indexed formulas and fallback documents.
     *
     * @param formulaToDocuments mapping from DNF formulas to their documents
     * @param fallbackDocuments documents that could not be indexed
     * @return a policy index
     */
    public static CanonicalPolicyIndex create(Map<DisjunctiveFormula, List<CompiledDocument>> formulaToDocuments,
            List<CompiledDocument> fallbackDocuments) {
        val indexData = formulaToDocuments.isEmpty() ? null : CanonicalIndexBuilder.build(formulaToDocuments);
        return new CanonicalPolicyIndex(indexData, fallbackDocuments);
    }

    @Override
    public PolicyIndexResult match(EvaluationContext ctx) {
        val matchingDocuments = new ArrayList<CompiledDocument>();
        val errorVotes        = new ArrayList<Vote>();

        if (indexData != null) {
            val indexResult = CanonicalIndexSearch.search(indexData, ctx);
            matchingDocuments.addAll(indexResult.matchingDocuments());
            errorVotes.addAll(indexResult.errorVotes());
        }

        evaluateFallbackDocuments(ctx, matchingDocuments, errorVotes);

        return new PolicyIndexResult(matchingDocuments, errorVotes);
    }

    private void evaluateFallbackDocuments(EvaluationContext ctx, List<CompiledDocument> matchingDocuments,
            List<Vote> errorVotes) {
        for (val document : fallbackDocuments) {
            val isApplicable = evaluateApplicability(document, ctx);
            if (isApplicable instanceof ErrorValue error) {
                errorVotes.add(Vote.error(error, document.metadata()));
            } else if (isApplicable instanceof BooleanValue(var b) && b) {
                matchingDocuments.add(document);
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
