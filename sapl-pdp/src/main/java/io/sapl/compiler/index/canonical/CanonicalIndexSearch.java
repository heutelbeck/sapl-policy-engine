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

import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.index.IndexReclassification;
import io.sapl.compiler.index.PolicyIndexResult;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * Implements the count-and-eliminate search algorithm (Algorithm 1) from the
 * SACMAT '21 paper.
 * <p>
 * For each predicate in priority order:
 * <ol>
 * <li>Skip if no remaining candidate conjunctions reference it</li>
 * <li>Evaluate the predicate once against the context</li>
 * <li>Increment true-literal counts for satisfied literals; mark conjunctions
 * as satisfied when all literals are true</li>
 * <li>Mark formulas of satisfied conjunctions as matched; decrement remaining
 * formula counts for sibling conjunctions (orphan detection)</li>
 * <li>Identify unsatisfiable conjunctions from bitmasks</li>
 * <li>Remove satisfied, unsatisfied, and orphaned conjunctions from
 * candidates</li>
 * </ol>
 */
@UtilityClass
class CanonicalIndexSearch {

    /**
     * Executes the count-and-eliminate algorithm on the precomputed index data.
     *
     * @param data the precomputed index data structures
     * @param ctx the evaluation context for predicate evaluation
     * @return matching documents and error votes
     */
    static PolicyIndexResult search(CanonicalIndexData data, EvaluationContext ctx) {
        val numberOfConjunctions = data.numberOfConjunctions();
        val numberOfFormulas     = data.formulas().size();

        val trueLiteralCount      = new int[numberOfConjunctions];
        val candidateConjunctions = new BitSet(numberOfConjunctions);
        candidateConjunctions.set(0, numberOfConjunctions);
        val scratch = new BitSet(numberOfConjunctions);

        val remainingFormulasWithConjunction = data.numberOfFormulasWithConjunction().clone();

        val matchedFormulas = new BitSet(numberOfFormulas);
        val errorFormulas   = new BitSet(numberOfFormulas);
        val predicates      = data.predicateOrder();
        val originalIndices = data.predicateOriginalIndices();

        for (var i = 0; i < predicates.size(); i++) {
            if (candidateConjunctions.isEmpty()) {
                break;
            }

            val p = originalIndices[i];

            if (!intersects(data.conjunctionsWithPredicate()[p], candidateConjunctions)) {
                continue;
            }

            val evaluationResult = predicates.get(i).operator().evaluate(ctx);

            // A non-boolean predicate result (error, or any non-boolean now that the
            // body type-check is gone) is treated as a type error: route the formula to
            // the suspect path and reconcile it through Kleene naive.
            if (!(evaluationResult instanceof BooleanValue(var b))) {
                eliminateAllPredicateConjunctions(p, data, candidateConjunctions, remainingFormulasWithConjunction,
                        matchedFormulas, errorFormulas, scratch);
                continue;
            }

            val predicateTrue = b;

            findAndMarkSatisfied(p, predicateTrue, data, candidateConjunctions, trueLiteralCount, matchedFormulas,
                    remainingFormulasWithConjunction, scratch);

            eliminateUnsatisfied(p, predicateTrue, data, candidateConjunctions, scratch);
        }

        return buildResult(matchedFormulas, errorFormulas, data, ctx);
    }

    /**
     * Incremental search: yields matches and errors to the callback after each
     * predicate evaluation. Stops when the callback returns false.
     *
     * @param data the precomputed index data structures
     * @param ctx the evaluation context for predicate evaluation
     * @param shouldContinue called with incremental results after each predicate
     * step; returns false to stop
     */
    static void searchWhile(CanonicalIndexData data, EvaluationContext ctx,
            Predicate<PolicyIndexResult> shouldContinue) {
        val numberOfConjunctions = data.numberOfConjunctions();
        val numberOfFormulas     = data.formulas().size();

        val trueLiteralCount      = new int[numberOfConjunctions];
        val candidateConjunctions = new BitSet(numberOfConjunctions);
        candidateConjunctions.set(0, numberOfConjunctions);
        val scratch = new BitSet(numberOfConjunctions);

        val remainingFormulasWithConjunction = data.numberOfFormulasWithConjunction().clone();

        val matchedFormulas = new BitSet(numberOfFormulas);
        val errorFormulas   = new BitSet(numberOfFormulas);
        val predicates      = data.predicateOrder();
        val originalIndices = data.predicateOriginalIndices();

        for (var i = 0; i < predicates.size(); i++) {
            if (candidateConjunctions.isEmpty()) {
                break;
            }

            val p = originalIndices[i];

            if (!intersects(data.conjunctionsWithPredicate()[p], candidateConjunctions)) {
                continue;
            }

            val evaluationResult = predicates.get(i).operator().evaluate(ctx);

            // A non-boolean predicate result (error, or any non-boolean now that the
            // body type-check is gone) is treated as a type error: route the formula to
            // the suspect path and reconcile it through Kleene naive.
            if (!(evaluationResult instanceof BooleanValue(var b))) {
                eliminateAllPredicateConjunctions(p, data, candidateConjunctions, remainingFormulasWithConjunction,
                        matchedFormulas, errorFormulas, scratch);
                continue;
            }

            val predicateTrue = b;

            if (!yieldAndMarkSatisfied(p, predicateTrue, data, candidateConjunctions, trueLiteralCount, matchedFormulas,
                    errorFormulas, remainingFormulasWithConjunction, scratch, shouldContinue)) {
                return;
            }

            eliminateUnsatisfied(p, predicateTrue, data, candidateConjunctions, scratch);
        }

        // Error-suspect formulas are not yielded during the walk: a sibling clause may
        // still dominate. Reconcile them through Kleene naive after the walk completes.
        yieldReclassifiedSuspects(errorFormulas, data, ctx, shouldContinue);
    }

    private static boolean intersects(BitSet a, BitSet b) {
        return a.intersects(b);
    }

    private static void andInto(BitSet scratch, BitSet source, BitSet mask) {
        scratch.clear();
        scratch.or(source);
        scratch.and(mask);
    }

    private static void findAndMarkSatisfied(int p, boolean predicateTrue, CanonicalIndexData data,
            BitSet candidateConjunctions, int[] trueLiteralCount, BitSet matchedFormulas,
            int[] remainingFormulasWithConjunction, BitSet scratch) {
        val trueLiterals = predicateTrue ? data.falseForFalsePredicate()[p] : data.falseForTruePredicate()[p];
        andInto(scratch, trueLiterals, candidateConjunctions);

        for (var c = scratch.nextSetBit(0); c >= 0; c = scratch.nextSetBit(c + 1)) {
            trueLiteralCount[c]++;
            if (trueLiteralCount[c] == data.numberOfLiteralsInConjunction()[c]) {
                candidateConjunctions.clear(c);
                for (val f : data.conjunctionToFormulaIndices()[c]) {
                    if (!matchedFormulas.get(f)) {
                        matchedFormulas.set(f);
                        orphanSiblingConjunctions(f, data, candidateConjunctions, remainingFormulasWithConjunction);
                    }
                }
            }
        }
    }

    private static boolean yieldAndMarkSatisfied(int p, boolean predicateTrue, CanonicalIndexData data,
            BitSet candidateConjunctions, int[] trueLiteralCount, BitSet matchedFormulas, BitSet errorFormulas,
            int[] remainingFormulasWithConjunction, BitSet scratch, Predicate<PolicyIndexResult> shouldContinue) {
        val trueLiterals = predicateTrue ? data.falseForFalsePredicate()[p] : data.falseForTruePredicate()[p];
        andInto(scratch, trueLiterals, candidateConjunctions);

        for (var c = scratch.nextSetBit(0); c >= 0; c = scratch.nextSetBit(c + 1)) {
            trueLiteralCount[c]++;
            if (trueLiteralCount[c] == data.numberOfLiteralsInConjunction()[c]) {
                candidateConjunctions.clear(c);
                for (val f : data.conjunctionToFormulaIndices()[c]) {
                    if (!errorFormulas.get(f) && !matchedFormulas.get(f)) {
                        matchedFormulas.set(f);
                        orphanSiblingConjunctions(f, data, candidateConjunctions, remainingFormulasWithConjunction);
                        val documents = data.formulaDocuments().get(f);
                        if (!shouldContinue.test(new PolicyIndexResult(documents, List.of()))) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private static void eliminateUnsatisfied(int p, boolean predicateTrue, CanonicalIndexData data,
            BitSet candidateConjunctions, BitSet scratch) {
        val unsatisfied = predicateTrue ? data.falseForTruePredicate()[p] : data.falseForFalsePredicate()[p];
        andInto(scratch, unsatisfied, candidateConjunctions);
        candidateConjunctions.andNot(scratch);
    }

    private static void orphanSiblingConjunctions(int formulaIndex, CanonicalIndexData data,
            BitSet candidateConjunctions, int[] remainingFormulasWithConjunction) {
        for (val sibling : data.formulaConjunctionIndices()[formulaIndex]) {
            remainingFormulasWithConjunction[sibling]--;
            if (remainingFormulasWithConjunction[sibling] <= 0) {
                candidateConjunctions.clear(sibling);
            }
        }
    }

    private static void yieldReclassifiedSuspects(BitSet errorFormulas, CanonicalIndexData data, EvaluationContext ctx,
            Predicate<PolicyIndexResult> shouldContinue) {
        val suspects = suspectDocuments(errorFormulas, data);
        if (suspects.isEmpty()) {
            return;
        }
        val matching = new ArrayList<CompiledDocument>();
        val errors   = new ArrayList<Vote>();
        IndexReclassification.reclassifySuspects(suspects, ctx, matching, errors);
        for (val vote : errors) {
            if (!shouldContinue.test(new PolicyIndexResult(List.of(), List.of(vote)))) {
                return;
            }
        }
        for (val document : matching) {
            if (!shouldContinue.test(new PolicyIndexResult(List.of(document), List.of()))) {
                return;
            }
        }
    }

    private static List<CompiledDocument> suspectDocuments(BitSet errorFormulas, CanonicalIndexData data) {
        val suspects = new ArrayList<CompiledDocument>();
        for (var f = errorFormulas.nextSetBit(0); f >= 0; f = errorFormulas.nextSetBit(f + 1)) {
            suspects.addAll(data.formulaDocuments().get(f));
        }
        return suspects;
    }

    private static void eliminateAllPredicateConjunctions(int p, CanonicalIndexData data, BitSet candidateConjunctions,
            int[] remainingFormulasWithConjunction, BitSet matchedFormulas, BitSet errorFormulas, BitSet scratch) {
        andInto(scratch, data.conjunctionsWithPredicate()[p], candidateConjunctions);
        candidateConjunctions.andNot(scratch);

        for (var c = scratch.nextSetBit(0); c >= 0; c = scratch.nextSetBit(c + 1)) {
            markConjunctionFormulas(c, data, matchedFormulas, errorFormulas);
            for (val f : data.conjunctionToFormulaIndices()[c]) {
                if (!matchedFormulas.get(f)) {
                    for (val sibling : data.formulaConjunctionIndices()[f]) {
                        remainingFormulasWithConjunction[sibling]--;
                        if (remainingFormulasWithConjunction[sibling] <= 0) {
                            candidateConjunctions.clear(sibling);
                            markConjunctionFormulas(sibling, data, matchedFormulas, errorFormulas);
                        }
                    }
                }
            }
        }
    }

    /**
     * Flags every unmatched formula that contains the given conjunction as an
     * error-suspect. A conjunction cleared on the error path (the errored
     * predicate's own conjunctions and any orphaned siblings) leaves the
     * affected formulas unresolved by the index, so they are reconciled through
     * Kleene naive evaluation rather than silently dropped.
     */
    private static void markConjunctionFormulas(int conjunction, CanonicalIndexData data, BitSet matchedFormulas,
            BitSet errorFormulas) {
        for (val formulaIndex : data.conjunctionToFormulaIndices()[conjunction]) {
            if (!matchedFormulas.get(formulaIndex)) {
                errorFormulas.set(formulaIndex);
            }
        }
    }

    private static PolicyIndexResult buildResult(BitSet matchedFormulas, BitSet errorFormulas, CanonicalIndexData data,
            EvaluationContext ctx) {
        val matchingDocuments = new ArrayList<CompiledDocument>();
        for (var f = matchedFormulas.nextSetBit(0); f >= 0; f = matchedFormulas.nextSetBit(f + 1)) {
            if (errorFormulas.get(f)) {
                continue;
            }
            matchingDocuments.addAll(data.formulaDocuments().get(f));
        }
        val errorVotes = new ArrayList<Vote>();
        IndexReclassification.reclassifySuspects(suspectDocuments(errorFormulas, data), ctx, matchingDocuments,
                errorVotes);
        return new PolicyIndexResult(matchingDocuments, errorVotes);
    }

}
