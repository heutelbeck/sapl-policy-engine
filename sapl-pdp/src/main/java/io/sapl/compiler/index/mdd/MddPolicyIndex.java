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
package io.sapl.compiler.index.mdd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.IndexPredicate;
import io.sapl.api.model.PureOperator;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.index.PolicyIndex;
import io.sapl.compiler.index.PolicyIndexResult;
import io.sapl.compiler.index.dnf.DisjunctiveFormula;
import io.sapl.compiler.index.dnf.DnfNormalizer;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Policy index based on a Multi-Valued Decision Diagram (MDD).
 * <p>
 * The diagram is a ternary decision DAG (true/false/error edges per node)
 * built at compile time from the DNF formulas of all indexed policies.
 * Evaluation is a single root-to-leaf traversal, evaluating one predicate
 * per node and collecting matched/errored documents at each node along
 * the path.
 * <p>
 * The traversal cost is O(predicates evaluated to reach a leaf), which
 * is typically much less than the total number of predicates. Structurally
 * identical subtrees are shared (DAG, not tree), keeping memory bounded.
 * <p>
 * The diagram is immutable and thread-safe for concurrent evaluation.
 */
@Slf4j
public class MddPolicyIndex implements PolicyIndex {

    private static final String ERROR_NON_BOOLEAN_APPLICABILITY = "Non-boolean applicability expression: %s";

    private final MddNode                root;
    private final List<CompiledDocument> alwaysApplicable;
    private final List<Vote>             alwaysErrorVotes;

    private MddPolicyIndex(MddNode root, List<CompiledDocument> alwaysApplicable, List<Vote> alwaysErrorVotes) {
        this.root             = root;
        this.alwaysApplicable = alwaysApplicable;
        this.alwaysErrorVotes = alwaysErrorVotes;
    }

    /**
     * Creates an MDD index from compiled documents. Partitions documents by
     * applicability type, extracts boolean expressions from pure operators,
     * normalizes to DNF, and builds the decision diagram.
     *
     * @param documents the compiled documents to index
     * @return an MDD policy index
     */
    public static MddPolicyIndex create(List<CompiledDocument> documents) {
        log.info("MDD index: partitioning {} documents", documents.size());
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

        log.info("MDD index: {} always-applicable, {} always-error, {} formula-based", alwaysApplicable.size(),
                alwaysErrorVotes.size(), formulaToDocuments.size());

        if (formulaToDocuments.isEmpty()) {
            log.info("MDD index: no formulas, returning empty leaf");
            return new MddPolicyIndex(MddNode.EMPTY_LEAF, alwaysApplicable, alwaysErrorVotes);
        }

        val formulas         = new ArrayList<>(formulaToDocuments.keySet());
        val formulaDocuments = formulas.stream().map(formulaToDocuments::get).toList();
        val predicateOrder   = extractPredicateOrder(formulas);
        log.info("MDD index: {} formulas, {} unique predicates. Building DAG...", formulas.size(),
                predicateOrder.size());
        val root = MddIndexBuilder.build(predicateOrder, formulas, formulaDocuments);
        log.info("MDD index: DAG construction complete");
        return new MddPolicyIndex(root, alwaysApplicable, alwaysErrorVotes);
    }

    /**
     * Creates an MDD index from pre-extracted formulas (for testing).
     *
     * @param predicateOrder predicates ordered by evaluation priority
     * @param formulas the DNF applicability formulas
     * @param formulaDocuments documents per formula
     * @return the MDD policy index
     */
    static MddPolicyIndex createFromFormulas(List<IndexPredicate> predicateOrder, List<DisjunctiveFormula> formulas,
            List<List<CompiledDocument>> formulaDocuments) {
        val root = MddIndexBuilder.build(predicateOrder, formulas, formulaDocuments);
        return new MddPolicyIndex(root, List.of(), List.of());
    }

    private static List<IndexPredicate> extractPredicateOrder(List<DisjunctiveFormula> formulas) {
        val predicates = new LinkedHashSet<IndexPredicate>();
        for (val formula : formulas) {
            for (val clause : formula.clauses()) {
                for (val literal : clause.literals()) {
                    predicates.add(literal.predicate());
                }
            }
        }
        return new ArrayList<>(predicates);
    }

    @Override
    public PolicyIndexResult match(EvaluationContext ctx) {
        val matchingDocuments = new ArrayList<CompiledDocument>(alwaysApplicable);
        val errorVotes        = new ArrayList<>(alwaysErrorVotes);

        var node = root;
        while (!node.isLeaf()) {
            matchingDocuments.addAll(node.matchedDocuments());

            val result = node.predicate().operator().evaluate(ctx);

            if (result instanceof ErrorValue error) {
                for (val doc : node.errorDocuments()) {
                    errorVotes.add(Vote.error(error, doc.metadata()));
                }
                node = node.errorChild();
            } else if (result instanceof BooleanValue(var b) && b) {
                node = node.trueChild();
            } else {
                node = node.falseChild();
            }
        }

        matchingDocuments.addAll(node.matchedDocuments());

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
        var node = root;
        while (!node.isLeaf()) {
            if (!node.matchedDocuments().isEmpty()
                    && !shouldContinue.test(new PolicyIndexResult(node.matchedDocuments(), List.of()))) {
                return;
            }

            val result = node.predicate().operator().evaluate(ctx);

            if (result instanceof ErrorValue error) {
                if (!node.errorDocuments().isEmpty()) {
                    val errorVotes = new ArrayList<Vote>();
                    for (val doc : node.errorDocuments()) {
                        errorVotes.add(Vote.error(error, doc.metadata()));
                    }
                    if (!shouldContinue.test(new PolicyIndexResult(List.of(), errorVotes))) {
                        return;
                    }
                }
                node = node.errorChild();
            } else if (result instanceof BooleanValue(var b) && b) {
                node = node.trueChild();
            } else {
                node = node.falseChild();
            }
        }

        if (!node.matchedDocuments().isEmpty()) {
            shouldContinue.test(new PolicyIndexResult(node.matchedDocuments(), List.of()));
        }
    }

    /**
     * @return the root node for testing and inspection
     */
    MddNode root() {
        return root;
    }

}
