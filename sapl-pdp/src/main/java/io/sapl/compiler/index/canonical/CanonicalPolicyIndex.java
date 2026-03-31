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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureOperator;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.index.DnfNormalizer;
import io.sapl.compiler.index.DisjunctiveFormula;
import io.sapl.compiler.index.PolicyIndex;
import io.sapl.compiler.index.PolicyIndexResult;
import lombok.val;

/**
 * Canonical policy index implementation using the count-and-eliminate algorithm
 * from the SACMAT '21 paper.
 * <p>
 * Documents are partitioned at build time:
 * <ul>
 * <li>Constant true applicability: always included in results without
 * evaluation</li>
 * <li>Constant error applicability: always produce error votes</li>
 * <li>PureOperator applicability: indexed via DNF for efficient lookup</li>
 * <li>Constant false or other: dropped (never applicable)</li>
 * </ul>
 */
public class CanonicalPolicyIndex implements PolicyIndex {

    private static final String ERROR_NON_BOOLEAN_APPLICABILITY = "Document applicability is a non-boolean constant value: %s. This violates the compilation contract.";

    private final CanonicalIndexData     indexData;
    private final List<CompiledDocument> alwaysApplicable;
    private final List<Vote>             alwaysErrorVotes;

    private CanonicalPolicyIndex(CanonicalIndexData indexData,
            List<CompiledDocument> alwaysApplicable,
            List<Vote> alwaysErrorVotes) {
        this.indexData        = indexData;
        this.alwaysApplicable = List.copyOf(alwaysApplicable);
        this.alwaysErrorVotes = List.copyOf(alwaysErrorVotes);
    }

    /**
     * Creates an index from compiled documents. Partitions documents by
     * applicability type, extracts boolean expressions from pure operators,
     * normalizes to DNF, and builds the index data structures.
     *
     * @param documents the compiled documents to index
     * @return a canonical policy index
     */
    public static CanonicalPolicyIndex create(List<CompiledDocument> documents) {
        val formulaToDocuments = new HashMap<DisjunctiveFormula, List<CompiledDocument>>();
        val alwaysApplicable   = new ArrayList<CompiledDocument>();
        val alwaysErrorVotes   = new ArrayList<Vote>();

        for (val document : documents) {
            val expression = document.isApplicable();
            switch (expression) {
            case BooleanValue(var b) when b -> alwaysApplicable.add(document);
            case BooleanValue ignored       -> { /* constant false, drop */ }
            case ErrorValue error           -> alwaysErrorVotes.add(Vote.error(error, document.metadata()));
            case PureOperator pureOp        -> {
                val formula = DnfNormalizer.normalize(pureOp.booleanExpression());
                formulaToDocuments.computeIfAbsent(formula, k -> new ArrayList<>()).add(document);
            }
            default                         ->
                throw new IllegalStateException(ERROR_NON_BOOLEAN_APPLICABILITY.formatted(expression));
            }
        }

        val indexData = formulaToDocuments.isEmpty() ? null : CanonicalIndexBuilder.build(formulaToDocuments);
        return new CanonicalPolicyIndex(indexData, alwaysApplicable, alwaysErrorVotes);
    }

    static CanonicalPolicyIndex createFromFormulas(Map<DisjunctiveFormula, List<CompiledDocument>> formulaToDocuments) {
        return new CanonicalPolicyIndex(CanonicalIndexBuilder.build(formulaToDocuments), List.of(), List.of());
    }

    @Override
    public PolicyIndexResult match(EvaluationContext ctx) {
        val matchingDocuments = new ArrayList<>(alwaysApplicable);
        val errorVotes        = new ArrayList<>(alwaysErrorVotes);

        if (indexData != null) {
            val indexResult = CanonicalIndexSearch.search(indexData, ctx);
            matchingDocuments.addAll(indexResult.matchingDocuments());
            errorVotes.addAll(indexResult.errorVotes());
        }

        return new PolicyIndexResult(matchingDocuments, errorVotes);
    }

    @Override
    public void matchWhile(EvaluationContext ctx, Predicate<PolicyIndexResult> shouldContinue) {
        for (val errorVote : alwaysErrorVotes) {
            if (!shouldContinue.test(new PolicyIndexResult(List.of(), List.of(errorVote)))) {
                return;
            }
        }
        for (val document : alwaysApplicable) {
            if (!shouldContinue.test(new PolicyIndexResult(List.of(document), List.of()))) {
                return;
            }
        }
        if (indexData != null) {
            CanonicalIndexSearch.searchWhile(indexData, ctx, shouldContinue);
        }
    }

}
