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
package io.sapl.compiler.index.mtbdd;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import io.sapl.api.model.BooleanExpression;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureOperator;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.index.PolicyIndex;
import io.sapl.compiler.index.PolicyIndexResult;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Policy index based on a Multi-Terminal Binary Decision Diagram (MTBDD)
 * with ternary edges (true/false/error).
 * <p>
 * Construction: each formula's boolean expression is compiled into a
 * small MTBDD, then all per-formula MTBDDs are merged into a single
 * shared diagram using the Apply algorithm with a computed table cache.
 * <p>
 * Evaluation: a single root-to-leaf traversal evaluates one predicate
 * per decision node. The leaf terminal contains the matched formula
 * indices. Errors are tracked externally via the precomputed
 * error-formulas-per-predicate array in the variable order.
 * <p>
 * The diagram is immutable after construction and thread-safe for
 * concurrent evaluation.
 */
@Slf4j
public class MtbddPolicyIndex implements PolicyIndex {

    private static final String ERROR_NON_BOOLEAN_APPLICABILITY = "Non-boolean applicability expression: %s";

    private final MtbddNode                    root;
    private final VariableOrder                order;
    private final List<List<CompiledDocument>> formulaDocuments;
    private final List<CompiledDocument>       alwaysApplicable;
    private final List<Vote>                   alwaysErrorVotes;

    private MtbddPolicyIndex(MtbddNode root,
            VariableOrder order,
            List<List<CompiledDocument>> formulaDocuments,
            List<CompiledDocument> alwaysApplicable,
            List<Vote> alwaysErrorVotes) {
        this.root             = root;
        this.order            = order;
        this.formulaDocuments = formulaDocuments;
        this.alwaysApplicable = alwaysApplicable;
        this.alwaysErrorVotes = alwaysErrorVotes;
    }

    /**
     * Creates an MTBDD index from compiled documents.
     *
     * @param documents the compiled documents to index
     * @return an MTBDD policy index
     */
    public static MtbddPolicyIndex create(List<CompiledDocument> documents) {
        log.debug("MTBDD index: partitioning {} documents", documents.size());
        val alwaysApplicable = new ArrayList<CompiledDocument>();
        val alwaysErrorVotes = new ArrayList<Vote>();
        val expressions      = new ArrayList<BooleanExpression>();
        val formulaDocuments = new ArrayList<List<CompiledDocument>>();

        for (val document : documents) {
            val expression = document.isApplicable();
            switch (expression) {
            case BooleanValue(var b) when b -> alwaysApplicable.add(document);
            case BooleanValue ignored       -> { /* constant false, drop */ }
            case ErrorValue error           -> alwaysErrorVotes.add(Vote.error(error, document.metadata()));
            case PureOperator pureOp        -> {
                val boolExpr     = pureOp.booleanExpression();
                val formulaIndex = expressions.size();
                // Group documents by formula: identical expressions share one formula slot
                var found = false;
                for (var i = 0; i < expressions.size(); i++) {
                    if (expressions.get(i).equals(boolExpr)) {
                        formulaDocuments.get(i).add(document);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    expressions.add(boolExpr);
                    val docs = new ArrayList<CompiledDocument>();
                    docs.add(document);
                    formulaDocuments.add(docs);
                }
            }
            default                         ->
                throw new SaplCompilerException(ERROR_NON_BOOLEAN_APPLICABILITY.formatted(expression));
            }
        }

        log.debug("MTBDD index: {} always-applicable, {} always-error, {} unique formulas", alwaysApplicable.size(),
                alwaysErrorVotes.size(), expressions.size());

        if (expressions.isEmpty()) {
            return new MtbddPolicyIndex(UniqueTable.EMPTY, null, List.of(), alwaysApplicable, alwaysErrorVotes);
        }

        log.debug("MTBDD index: computing variable order...");
        val variableOrder = VariableOrder.fromExpressions(expressions);
        log.debug("MTBDD index: {} unique predicates", variableOrder.size());

        val table      = new UniqueTable();
        val totalCount = expressions.size();

        log.debug("MTBDD index: building formula 1/{} ...", totalCount);
        var merged = MtbddBuilder.buildFormula(table, variableOrder, expressions.getFirst(), 0);
        log.debug("MTBDD index: formula 1/{} done ({} nodes)", totalCount, table.size());

        for (var i = 1; i < totalCount; i++) {
            log.debug("MTBDD index: build+merge formula {}/{} ...", i + 1, totalCount);
            val formulaBdd = MtbddBuilder.buildFormula(table, variableOrder, expressions.get(i), i);
            log.debug("MTBDD index: formula {}/{} built ({} nodes), merging...", i + 1, totalCount, table.size());
            merged = MtbddMerger.merge(table, merged, formulaBdd);
            log.debug("MTBDD index: formula {}/{} merged ({} nodes)", i + 1, totalCount, table.size());
        }

        log.debug("MTBDD index: construction complete. {} unique nodes", table.size());

        return new MtbddPolicyIndex(merged, variableOrder, formulaDocuments, alwaysApplicable, alwaysErrorVotes);
    }

    @Override
    public PolicyIndexResult match(EvaluationContext ctx) {
        val matchingDocuments = new ArrayList<CompiledDocument>(alwaysApplicable);
        val errorVotes        = new ArrayList<>(alwaysErrorVotes);

        if (order != null) {
            val result = MtbddEvaluator.evaluate(root, order, ctx);

            for (var f = result.matched().nextSetBit(0); f >= 0; f = result.matched().nextSetBit(f + 1)) {
                matchingDocuments.addAll(formulaDocuments.get(f));
            }

            if (result.firstError() != null) {
                for (var f = result.errored().nextSetBit(0); f >= 0; f = result.errored().nextSetBit(f + 1)) {
                    for (val doc : formulaDocuments.get(f)) {
                        errorVotes.add(Vote.error(result.firstError(), doc.metadata()));
                    }
                }
            }
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

        if (order == null) {
            return;
        }

        val result = MtbddEvaluator.evaluate(root, order, ctx);

        for (var f = result.matched().nextSetBit(0); f >= 0; f = result.matched().nextSetBit(f + 1)) {
            val docs = formulaDocuments.get(f);
            if (!shouldContinue.test(new PolicyIndexResult(docs, List.of()))) {
                return;
            }
        }

        if (result.firstError() != null) {
            for (var f = result.errored().nextSetBit(0); f >= 0; f = result.errored().nextSetBit(f + 1)) {
                val votes = new ArrayList<Vote>();
                for (val doc : formulaDocuments.get(f)) {
                    votes.add(Vote.error(result.firstError(), doc.metadata()));
                }
                if (!shouldContinue.test(new PolicyIndexResult(List.of(), votes))) {
                    return;
                }
            }
        }
    }

}
