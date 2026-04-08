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
package io.sapl.compiler.index.smtdd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;

import io.sapl.api.model.BooleanExpression;
import io.sapl.api.model.BooleanExpression.And;
import io.sapl.api.model.BooleanExpression.Atom;
import io.sapl.api.model.BooleanExpression.Constant;
import io.sapl.api.model.BooleanExpression.Not;
import io.sapl.api.model.BooleanExpression.Or;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.IndexPredicate;
import io.sapl.api.model.PureOperator;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.index.PolicyIndex;
import io.sapl.compiler.index.PolicyIndexResult;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Policy index based on a Semantic Multi-Terminal BDD (SMTDD).
 * <p>
 * Equality predicates sharing a common operand are collapsed into
 * multi-way HashMap branch nodes. Remaining predicates use standard
 * binary decision nodes. This avoids the exponential blowup of pure
 * MTBDD merge for workloads with many disjoint equality predicates.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class SmtddPolicyIndex implements PolicyIndex {

    private static final String ERROR_UNEXPECTED_APPLICABILITY_TYPE = "Unexpected applicability expression type (streaming operators cannot be indexed): %s";

    private final SmtddNode                    root;
    private final BinaryVariableOrder          binaryOrder;
    private final List<List<CompiledDocument>> formulaDocuments;
    private final List<CompiledDocument>       alwaysApplicable;
    private final List<Vote>                   alwaysErrorVotes;

    /**
     * Creates an SMTDD index from compiled documents.
     *
     * @param documents the compiled documents to index
     * @param maxIndexNodes maximum allowed nodes (0 = unlimited)
     * @return an SMTDD policy index
     * @throws io.sapl.compiler.index.IndexSizeLimitExceededException if node limit
     * exceeded
     */
    public static SmtddPolicyIndex create(List<CompiledDocument> documents, int maxIndexNodes) {
        log.debug("SMTDD index: partitioning {} documents", documents.size());
        // Predetermined results due to constant applicability
        val alwaysApplicable = new ArrayList<CompiledDocument>();
        val alwaysErrorVotes = new ArrayList<Vote>();

        // Formula lookup tables
        val formulas          = new ArrayList<BooleanExpression>();
        val formulaDocuments  = new ArrayList<List<CompiledDocument>>();
        val formulaPredicates = new ArrayList<List<IndexPredicate>>();

        // Deduplicate formulas and collect predicates
        val alreadySeenFormulas = new HashMap<BooleanExpression, Integer>();
        for (val document : documents) {
            val applicability = document.isApplicable();
            switch (applicability) {
            case BooleanValue(var b) when b -> alwaysApplicable.add(document);
            case BooleanValue ignored       -> { /* constant false, drop */ }
            case ErrorValue error           -> alwaysErrorVotes.add(Vote.error(error, document.metadata()));
            case PureOperator pureOp        -> {
                val formula            = pureOp.booleanExpression();
                val alreadySeenAtIndex = alreadySeenFormulas.get(formula);
                if (alreadySeenAtIndex != null) {
                    // If we have seen this formula before, we add the document to the existing list
                    // of documents.
                    formulaDocuments.get(alreadySeenAtIndex).add(document);
                } else {
                    val newFormulaIndex = formulas.size();
                    alreadySeenFormulas.put(formula, newFormulaIndex);
                    formulas.add(formula);
                    formulaDocuments.add(new ArrayList<>(List.of(document)));
                    formulaPredicates.add(collectPredicates(formula));
                }
            }
            default                         ->
                throw new SaplCompilerException(ERROR_UNEXPECTED_APPLICABILITY_TYPE.formatted(applicability));
            }
        }

        log.debug("SMTDD index: {} always-applicable, {} always-error, {} unique formulas", alwaysApplicable.size(),
                alwaysErrorVotes.size(), formulas.size());

        // Short circuit if there are no formulas
        if (formulas.isEmpty()) {
            return new SmtddPolicyIndex(SmtddUniqueTable.EMPTY, null, List.of(), alwaysApplicable, alwaysErrorVotes);
        }

        val analysis = SemanticVariableOrder.analyze(formulaPredicates);
        log.debug("SMTDD index analysis:\n{}", analysis.toAnalysisReport());

        val root        = SmtddBuilder.build(analysis, formulas, maxIndexNodes);
        val binaryOrder = new BinaryVariableOrder(analysis.remainingPredicates(),
                analysis.formulasPerRemainingPredicate());

        return new SmtddPolicyIndex(root, binaryOrder, formulaDocuments, alwaysApplicable, alwaysErrorVotes);
    }

    private static List<IndexPredicate> collectPredicates(BooleanExpression expression) {
        val result = new ArrayList<IndexPredicate>();
        collectPredicatesRecursive(expression, result);
        return result;
    }

    private static void collectPredicatesRecursive(BooleanExpression node, List<IndexPredicate> result) {
        switch (node) {
        case Constant ignored                                     -> {}
        case Atom(var predicate) when !result.contains(predicate) -> result.add(predicate);
        case Atom ignored                                         -> { /* already collected */ }
        case Not(var operand)                                     -> collectPredicatesRecursive(operand, result);
        case Or(var operands)                                     ->
            operands.forEach(op -> collectPredicatesRecursive(op, result));
        case And(var operands)                                    ->
            operands.forEach(op -> collectPredicatesRecursive(op, result));
        }
    }

    /** {@inheritDoc} */
    @Override
    public PolicyIndexResult match(EvaluationContext ctx) {
        val matchingDocuments = new ArrayList<CompiledDocument>(alwaysApplicable);
        val errorVotes        = new ArrayList<>(alwaysErrorVotes);

        if (binaryOrder != null) {
            val result = SmtddEvaluator.evaluate(root, binaryOrder, ctx);

            for (var formulaIndex = result.matched().nextSetBit(0); formulaIndex >= 0; formulaIndex = result.matched()
                    .nextSetBit(formulaIndex + 1)) {
                matchingDocuments.addAll(formulaDocuments.get(formulaIndex));
            }

            if (result.firstError() != null) {
                for (var formulaIndex = result.errored().nextSetBit(0); formulaIndex >= 0; formulaIndex = result
                        .errored().nextSetBit(formulaIndex + 1)) {
                    for (val doc : formulaDocuments.get(formulaIndex)) {
                        errorVotes.add(Vote.error(result.firstError(), doc.metadata()));
                    }
                }
            }
        }

        return new PolicyIndexResult(matchingDocuments, errorVotes);
    }

    /** {@inheritDoc} */
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

        if (binaryOrder == null) {
            return;
        }

        val result = SmtddEvaluator.evaluate(root, binaryOrder, ctx);

        for (var formulaIndex = result.matched().nextSetBit(0); formulaIndex >= 0; formulaIndex = result.matched()
                .nextSetBit(formulaIndex + 1)) {
            if (!shouldContinue.test(new PolicyIndexResult(formulaDocuments.get(formulaIndex), List.of()))) {
                return;
            }
        }

        if (result.firstError() != null) {
            for (var formulaIndex = result.errored().nextSetBit(0); formulaIndex >= 0; formulaIndex = result.errored()
                    .nextSetBit(formulaIndex + 1)) {
                val votes = new ArrayList<Vote>();
                for (val doc : formulaDocuments.get(formulaIndex)) {
                    votes.add(Vote.error(result.firstError(), doc.metadata()));
                }
                if (!shouldContinue.test(new PolicyIndexResult(List.of(), votes))) {
                    return;
                }
            }
        }
    }

}
