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

import io.sapl.api.model.*;
import io.sapl.api.model.BooleanExpression.*;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.index.IndexReclassification;
import io.sapl.compiler.index.PolicyIndex;
import io.sapl.compiler.index.PolicyMatches;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

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

    private final SmtddNode                      root;
    private final BinaryVariableOrder            binaryOrder;
    private final List<List<CompiledDocument>>   formulaDocuments;
    private final List<CompiledDocument>         alwaysApplicable;
    private final List<PolicyMatches.ErrorMatch> alwaysErrorMatches;

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
        val alwaysApplicable   = new ArrayList<CompiledDocument>();
        val alwaysErrorMatches = new ArrayList<PolicyMatches.ErrorMatch>();

        // Formula lookup tables
        val formulas          = new ArrayList<BooleanExpression>();
        val formulaDocuments  = new ArrayList<List<CompiledDocument>>();
        val formulaPredicates = new ArrayList<List<IndexPredicate>>();

        // Predicates that occur under a disjunction or negation in some formula and
        // therefore cannot be grouped (grouping treats a predicate as a conjunctive
        // constraint, which only holds for predicates in pure conjunctive position).
        val nonGroupablePredicates = new HashSet<IndexPredicate>();

        // Deduplicate formulas and collect predicates
        val alreadySeenFormulas = new HashMap<BooleanExpression, Integer>();
        for (val document : documents) {
            val applicability = document.isApplicable();
            switch (applicability) {
            case BooleanValue(var b) when b -> alwaysApplicable.add(document);
            case BooleanValue ignored       -> { /* constant false, drop */ }
            case ErrorValue error           -> alwaysErrorMatches.add(new PolicyMatches.ErrorMatch(document, error));
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
                    collectNonGroupablePredicates(formula, true, nonGroupablePredicates);
                }
            }
            default                         ->
                throw new SaplCompilerException(ERROR_UNEXPECTED_APPLICABILITY_TYPE.formatted(applicability));
            }
        }

        log.debug("SMTDD index: {} always-applicable, {} always-error, {} unique formulas", alwaysApplicable.size(),
                alwaysErrorMatches.size(), formulas.size());

        // Short circuit if there are no formulas
        if (formulas.isEmpty()) {
            return new SmtddPolicyIndex(SmtddUniqueTable.EMPTY, null, List.of(), alwaysApplicable, alwaysErrorMatches);
        }

        val analysis = SemanticVariableOrder.analyze(formulaPredicates, nonGroupablePredicates);
        log.debug("SMTDD index analysis:\n{}", analysis.toAnalysisReport());

        val root        = SmtddBuilder.build(analysis, formulas, maxIndexNodes);
        val binaryOrder = new BinaryVariableOrder(analysis.remainingPredicates(),
                analysis.formulasPerRemainingPredicate());

        return new SmtddPolicyIndex(root, binaryOrder, formulaDocuments, alwaysApplicable, alwaysErrorMatches);
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

    private static void collectNonGroupablePredicates(BooleanExpression node, boolean conjunctive,
            Set<IndexPredicate> nonGroupable) {
        switch (node) {
        case Constant ignored                      -> { /* no predicate */ }
        case Atom(var predicate) when !conjunctive -> nonGroupable.add(predicate);
        case Atom ignored                          -> { /* conjunctive, groupable */ }
        case Not(var operand)                      -> collectNonGroupablePredicates(operand, false, nonGroupable);
        case Or(var operands)                      ->
            operands.forEach(op -> collectNonGroupablePredicates(op, false, nonGroupable));
        case And(var operands)                     ->
            operands.forEach(op -> collectNonGroupablePredicates(op, conjunctive, nonGroupable));
        }
    }

    private List<CompiledDocument> suspectDocuments(BitSet errored) {
        val suspects = new ArrayList<CompiledDocument>();
        for (var formulaIndex = errored.nextSetBit(0); formulaIndex >= 0; formulaIndex = errored
                .nextSetBit(formulaIndex + 1)) {
            suspects.addAll(formulaDocuments.get(formulaIndex));
        }
        return suspects;
    }

    @Override
    public PolicyMatches matchKleene(EvaluationContext ctx) {
        val trueMatches  = new ArrayList<CompiledDocument>(alwaysApplicable);
        val errorMatches = new ArrayList<>(alwaysErrorMatches);

        if (binaryOrder != null) {
            val result  = SmtddEvaluator.evaluate(root, binaryOrder, ctx);
            val errored = result.errored();

            for (var formulaIndex = result.matched().nextSetBit(0); formulaIndex >= 0; formulaIndex = result.matched()
                    .nextSetBit(formulaIndex + 1)) {
                if (!errored.get(formulaIndex)) {
                    trueMatches.addAll(formulaDocuments.get(formulaIndex));
                }
            }

            IndexReclassification.reclassifySuspectsKleene(suspectDocuments(errored), ctx, trueMatches, errorMatches);
        }

        return new PolicyMatches(trueMatches, errorMatches);
    }

    @Override
    public void matchKleeneWhile(EvaluationContext ctx, Predicate<PolicyMatches> shouldContinue) {
        for (val errorMatch : alwaysErrorMatches) {
            if (!shouldContinue.test(new PolicyMatches(List.of(), List.of(errorMatch)))) {
                return;
            }
        }
        for (val document : alwaysApplicable) {
            if (!shouldContinue.test(new PolicyMatches(List.of(document), List.of()))) {
                return;
            }
        }

        if (binaryOrder == null) {
            return;
        }

        val result  = SmtddEvaluator.evaluate(root, binaryOrder, ctx);
        val errored = result.errored();

        for (var formulaIndex = result.matched().nextSetBit(0); formulaIndex >= 0; formulaIndex = result.matched()
                .nextSetBit(formulaIndex + 1)) {
            if (!errored.get(formulaIndex)
                    && !shouldContinue.test(new PolicyMatches(formulaDocuments.get(formulaIndex), List.of()))) {
                return;
            }
        }

        val suspects = suspectDocuments(errored);
        if (suspects.isEmpty()) {
            return;
        }
        val trueMatches  = new ArrayList<CompiledDocument>();
        val errorMatches = new ArrayList<PolicyMatches.ErrorMatch>();
        IndexReclassification.reclassifySuspectsKleene(suspects, ctx, trueMatches, errorMatches);
        for (val errorMatch : errorMatches) {
            if (!shouldContinue.test(new PolicyMatches(List.of(), List.of(errorMatch)))) {
                return;
            }
        }
        for (val document : trueMatches) {
            if (!shouldContinue.test(new PolicyMatches(List.of(document), List.of()))) {
                return;
            }
        }
    }

}
