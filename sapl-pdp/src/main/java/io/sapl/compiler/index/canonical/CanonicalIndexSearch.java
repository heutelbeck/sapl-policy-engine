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
import java.util.BitSet;
import java.util.List;
import java.util.function.Predicate;

import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.index.PolicyIndexResult;
import lombok.experimental.UtilityClass;
import lombok.val;

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
        val errorVotes      = new ArrayList<Vote>();
        val predicates      = data.predicateOrder();
        val originalIndices = data.predicateOriginalIndices();

        for (var i = 0; i < predicates.size(); i++) {
            val p = originalIndices[i];

            if (!intersects(data.conjunctionsWithPredicate()[p], candidateConjunctions)) {
                continue;
            }

            val evaluationResult = predicates.get(i).operator().evaluate(ctx);

            if (evaluationResult instanceof ErrorValue error) {
                handlePredicateError(error, p, data, matchedFormulas, errorFormulas, errorVotes);
                eliminateAllPredicateConjunctions(p, data, candidateConjunctions, remainingFormulasWithConjunction,
                        matchedFormulas, scratch);
                continue;
            }

            val predicateTrue = evaluationResult instanceof BooleanValue(var b) && b;

            findAndMarkSatisfied(p, predicateTrue, data, candidateConjunctions, trueLiteralCount, matchedFormulas,
                    remainingFormulasWithConjunction, scratch);

            eliminateUnsatisfied(p, predicateTrue, data, candidateConjunctions, scratch);
        }

        return buildResult(matchedFormulas, errorFormulas, data, errorVotes);
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
            val p = originalIndices[i];

            if (!intersects(data.conjunctionsWithPredicate()[p], candidateConjunctions)) {
                continue;
            }

            val evaluationResult = predicates.get(i).operator().evaluate(ctx);

            if (evaluationResult instanceof ErrorValue error) {
                if (!yieldErrors(error, p, data, matchedFormulas, errorFormulas, shouldContinue)) {
                    return;
                }
                eliminateAllPredicateConjunctions(p, data, candidateConjunctions, remainingFormulasWithConjunction,
                        matchedFormulas, scratch);
                continue;
            }

            val predicateTrue = evaluationResult instanceof BooleanValue(var b) && b;

            if (!yieldAndMarkSatisfied(p, predicateTrue, data, candidateConjunctions, trueLiteralCount, matchedFormulas,
                    errorFormulas, remainingFormulasWithConjunction, scratch, shouldContinue)) {
                return;
            }

            eliminateUnsatisfied(p, predicateTrue, data, candidateConjunctions, scratch);
        }
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

    private static boolean yieldErrors(ErrorValue error, int p, CanonicalIndexData data, BitSet matchedFormulas,
            BitSet errorFormulas, Predicate<PolicyIndexResult> shouldContinue) {
        for (val formulaIndex : data.relatedFormulas()[p]) {
            if (!matchedFormulas.get(formulaIndex) && !errorFormulas.get(formulaIndex)) {
                errorFormulas.set(formulaIndex);
                val documents = data.formulaDocuments().get(formulaIndex);
                if (!documents.isEmpty()) {
                    val errorVotes = new ArrayList<Vote>(documents.size());
                    for (val doc : documents) {
                        errorVotes.add(Vote.error(error, doc.metadata()));
                    }
                    if (!shouldContinue.test(new PolicyIndexResult(List.of(), errorVotes))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static void handlePredicateError(ErrorValue error, int p, CanonicalIndexData data, BitSet matchedFormulas,
            BitSet errorFormulas, List<Vote> errorVotes) {
        for (val formulaIndex : data.relatedFormulas()[p]) {
            if (!matchedFormulas.get(formulaIndex) && !errorFormulas.get(formulaIndex)) {
                errorFormulas.set(formulaIndex);
                val documents = data.formulaDocuments().get(formulaIndex);
                if (!documents.isEmpty()) {
                    for (val document : documents) {
                        errorVotes.add(Vote.error(error, document.metadata()));
                    }
                }
            }
        }
    }

    private static void eliminateAllPredicateConjunctions(int p, CanonicalIndexData data, BitSet candidateConjunctions,
            int[] remainingFormulasWithConjunction, BitSet matchedFormulas, BitSet scratch) {
        andInto(scratch, data.conjunctionsWithPredicate()[p], candidateConjunctions);
        candidateConjunctions.andNot(scratch);

        for (var c = scratch.nextSetBit(0); c >= 0; c = scratch.nextSetBit(c + 1)) {
            for (val f : data.conjunctionToFormulaIndices()[c]) {
                if (!matchedFormulas.get(f)) {
                    for (val sibling : data.formulaConjunctionIndices()[f]) {
                        remainingFormulasWithConjunction[sibling]--;
                        if (remainingFormulasWithConjunction[sibling] <= 0) {
                            candidateConjunctions.clear(sibling);
                        }
                    }
                }
            }
        }
    }

    private static PolicyIndexResult buildResult(BitSet matchedFormulas, BitSet errorFormulas, CanonicalIndexData data,
            List<Vote> errorVotes) {
        val matchingDocuments = new ArrayList<CompiledDocument>();
        for (var f = matchedFormulas.nextSetBit(0); f >= 0; f = matchedFormulas.nextSetBit(f + 1)) {
            if (errorFormulas.get(f)) {
                continue;
            }
            val documents = data.formulaDocuments().get(f);
            if (!documents.isEmpty()) {
                matchingDocuments.addAll(documents);
            }
        }
        return new PolicyIndexResult(matchingDocuments, errorVotes);
    }

}
