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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.index.DisjunctiveFormula;
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

        val trueLiteralCount      = new int[numberOfConjunctions];
        val candidateConjunctions = new BitSet(numberOfConjunctions);
        candidateConjunctions.set(0, numberOfConjunctions);

        val remainingFormulasWithConjunction = data.numberOfFormulasWithConjunction().clone();

        val matchedFormulas = new HashSet<Integer>();
        val errorFormulas   = new HashSet<Integer>();
        val errorVotes      = new ArrayList<Vote>();

        for (val predicate : data.predicateOrder()) {
            val p = data.predicateToIndex().get(predicate);

            val relevantConjunctions = (BitSet) data.conjunctionsWithPredicate()[p].clone();
            relevantConjunctions.and(candidateConjunctions);
            if (relevantConjunctions.isEmpty()) {
                continue;
            }

            val evaluationResult = predicate.operator().evaluate(ctx);

            if (evaluationResult instanceof ErrorValue error) {
                handlePredicateError(error, p, data, matchedFormulas, errorFormulas, errorVotes);
                eliminateAllPredicateConjunctions(p, data, candidateConjunctions, remainingFormulasWithConjunction,
                        matchedFormulas);
                continue;
            }

            val predicateTrue = evaluationResult instanceof BooleanValue(var b) && b;

            val satisfied = findSatisfied(p, predicateTrue, data, candidateConjunctions, trueLiteralCount);

            markMatchedFormulas(satisfied, data, matchedFormulas, candidateConjunctions,
                    remainingFormulasWithConjunction);

            val unsatisfied = findUnsatisfied(p, predicateTrue, data, candidateConjunctions);

            candidateConjunctions.andNot(satisfied);
            candidateConjunctions.andNot(unsatisfied);
        }

        return buildResult(matchedFormulas, errorFormulas, data, errorVotes);
    }

    private static BitSet findSatisfied(int p, boolean predicateTrue, CanonicalIndexData data,
            BitSet candidateConjunctions, int[] trueLiteralCount) {
        val satisfied    = new BitSet(data.numberOfConjunctions());
        val trueLiterals = predicateTrue ? data.falseForFalsePredicate()[p] : data.falseForTruePredicate()[p];

        val toIncrement = (BitSet) trueLiterals.clone();
        toIncrement.and(candidateConjunctions);

        for (var c = toIncrement.nextSetBit(0); c >= 0; c = toIncrement.nextSetBit(c + 1)) {
            trueLiteralCount[c]++;
            if (trueLiteralCount[c] == data.numberOfLiteralsInConjunction()[c]) {
                satisfied.set(c);
            }
        }
        return satisfied;
    }

    private static void markMatchedFormulas(BitSet satisfied, CanonicalIndexData data, Set<Integer> matchedFormulas,
            BitSet candidateConjunctions, int[] remainingFormulasWithConjunction) {
        for (var c = satisfied.nextSetBit(0); c >= 0; c = satisfied.nextSetBit(c + 1)) {
            for (val f : data.conjunctionToFormulaIndices()[c]) {
                if (matchedFormulas.add(f)) {
                    orphanSiblingConjunctions(f, data, candidateConjunctions, remainingFormulasWithConjunction);
                }
            }
        }
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

    private static BitSet findUnsatisfied(int p, boolean predicateTrue, CanonicalIndexData data,
            BitSet candidateConjunctions) {
        val unsatisfied = predicateTrue ? (BitSet) data.falseForTruePredicate()[p].clone()
                : (BitSet) data.falseForFalsePredicate()[p].clone();
        unsatisfied.and(candidateConjunctions);
        return unsatisfied;
    }

    private static void handlePredicateError(ErrorValue error, int p, CanonicalIndexData data,
            Set<Integer> matchedFormulas, Set<Integer> errorFormulas, List<Vote> errorVotes) {
        for (val formulaIndex : data.relatedFormulas().get(p)) {
            if (!matchedFormulas.contains(formulaIndex) && errorFormulas.add(formulaIndex)) {
                val formula   = data.formulas().get(formulaIndex);
                val documents = data.formulaToDocuments().get(formula);
                if (documents != null) {
                    for (val document : documents) {
                        errorVotes.add(Vote.error(error, document.metadata()));
                    }
                }
            }
        }
    }

    private static void eliminateAllPredicateConjunctions(int p, CanonicalIndexData data, BitSet candidateConjunctions,
            int[] remainingFormulasWithConjunction, Set<Integer> matchedFormulas) {
        val toRemove = (BitSet) data.conjunctionsWithPredicate()[p].clone();
        toRemove.and(candidateConjunctions);
        candidateConjunctions.andNot(toRemove);

        for (var c = toRemove.nextSetBit(0); c >= 0; c = toRemove.nextSetBit(c + 1)) {
            for (val f : data.conjunctionToFormulaIndices()[c]) {
                if (!matchedFormulas.contains(f)) {
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

    private static PolicyIndexResult buildResult(Set<Integer> matchedFormulas, Set<Integer> errorFormulas,
            CanonicalIndexData data, List<Vote> errorVotes) {
        val matchingDocuments = new ArrayList<CompiledDocument>();
        for (val formulaIndex : matchedFormulas) {
            if (errorFormulas.contains(formulaIndex)) {
                continue;
            }
            val formula   = data.formulas().get(formulaIndex);
            val documents = data.formulaToDocuments().get(formula);
            if (documents != null) {
                matchingDocuments.addAll(documents);
            }
        }
        return new PolicyIndexResult(matchingDocuments, errorVotes);
    }

}
