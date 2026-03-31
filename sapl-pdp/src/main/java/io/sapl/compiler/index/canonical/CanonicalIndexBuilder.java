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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.index.ConjunctiveClause;
import io.sapl.compiler.index.DisjunctiveFormula;
import io.sapl.api.model.IndexPredicate;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Builds the {@link CanonicalIndexData} structures from a set of DNF formulas
 * and their associated compiled documents.
 * <p>
 * Construction follows Definitions 3.2 through 3.7 of the SACMAT '21 paper:
 * assigns global conjunction indices, computes per-conjunction and
 * per-predicate data, and orders predicates by the evaluation priority
 * heuristic.
 */
@UtilityClass
class CanonicalIndexBuilder {

    /**
     * Builds the canonical index data from formulas mapped to their documents.
     *
     * @param formulaToDocuments mapping from each formula to its compiled documents
     * @return precomputed index data structures
     */
    static CanonicalIndexData build(Map<DisjunctiveFormula, List<CompiledDocument>> formulaToDocuments) {
        val formulas           = new ArrayList<>(formulaToDocuments.keySet());
        val predicateToIndex   = new LinkedHashMap<IndexPredicate, Integer>();
        val conjunctionToIndex = new HashMap<ConjunctiveClause, Integer>();

        val formulaConjunctionIndicesList = new ArrayList<int[]>();

        for (val formula : formulas) {
            val conjunctionIndices = new ArrayList<Integer>();
            for (val clause : formula.clauses()) {
                val clauseIndex = conjunctionToIndex.computeIfAbsent(clause, k -> conjunctionToIndex.size());
                conjunctionIndices.add(clauseIndex);
                for (val literal : clause.literals()) {
                    predicateToIndex.computeIfAbsent(literal.predicate(), k -> predicateToIndex.size());
                }
            }
            formulaConjunctionIndicesList.add(conjunctionIndices.stream().mapToInt(Integer::intValue).toArray());
        }

        val numberOfConjunctions = conjunctionToIndex.size();
        val predicates           = new ArrayList<>(predicateToIndex.keySet());
        val numberOfPredicates   = predicates.size();

        val numberOfLiteralsInConjunction   = buildLiteralCounts(conjunctionToIndex, numberOfConjunctions);
        val numberOfFormulasWithConjunction = buildFormulaCounts(formulaConjunctionIndicesList, numberOfConjunctions);

        val conjunctionToFormulaIndices = buildConjunctionToFormulaIndices(formulaConjunctionIndicesList,
                numberOfConjunctions);
        val formulaConjunctionIndices   = formulaConjunctionIndicesList.toArray(int[][]::new);

        val falseForTruePredicate     = new BitSet[numberOfPredicates];
        val falseForFalsePredicate    = new BitSet[numberOfPredicates];
        val conjunctionsWithPredicate = new BitSet[numberOfPredicates];
        val relatedFormulas           = new ArrayList<Set<Integer>>(numberOfPredicates);

        for (var p = 0; p < numberOfPredicates; p++) {
            falseForTruePredicate[p]     = new BitSet(numberOfConjunctions);
            falseForFalsePredicate[p]    = new BitSet(numberOfConjunctions);
            conjunctionsWithPredicate[p] = new BitSet(numberOfConjunctions);
            relatedFormulas.add(new HashSet<>());
        }

        buildPredicateData(formulas, conjunctionToIndex, predicateToIndex, falseForTruePredicate,
                falseForFalsePredicate, conjunctionsWithPredicate, relatedFormulas);

        val predicateOrder = PredicateOrderStrategy.order(predicates, predicateToIndex, conjunctionToIndex,
                falseForTruePredicate, falseForFalsePredicate, conjunctionsWithPredicate, relatedFormulas);

        return new CanonicalIndexData(predicateOrder, Map.copyOf(predicateToIndex), numberOfConjunctions,
                numberOfLiteralsInConjunction, numberOfFormulasWithConjunction, conjunctionToFormulaIndices,
                formulaConjunctionIndices, falseForTruePredicate, falseForFalsePredicate, conjunctionsWithPredicate,
                relatedFormulas, formulas, formulaToDocuments);
    }

    private static int[] buildLiteralCounts(Map<ConjunctiveClause, Integer> conjunctionToIndex,
            int numberOfConjunctions) {
        val counts = new int[numberOfConjunctions];
        for (val entry : conjunctionToIndex.entrySet()) {
            counts[entry.getValue()] = entry.getKey().size();
        }
        return counts;
    }

    private static int[] buildFormulaCounts(List<int[]> formulaConjunctionIndices, int numberOfConjunctions) {
        val counts = new int[numberOfConjunctions];
        for (val conjunctionIndices : formulaConjunctionIndices) {
            for (val ci : conjunctionIndices) {
                counts[ci]++;
            }
        }
        return counts;
    }

    private static int[][] buildConjunctionToFormulaIndices(List<int[]> formulaConjunctionIndices,
            int numberOfConjunctions) {
        val conjToFormulas = new ArrayList<List<Integer>>(numberOfConjunctions);
        for (var c = 0; c < numberOfConjunctions; c++) {
            conjToFormulas.add(new ArrayList<>());
        }
        for (var f = 0; f < formulaConjunctionIndices.size(); f++) {
            for (val ci : formulaConjunctionIndices.get(f)) {
                conjToFormulas.get(ci).add(f);
            }
        }
        val result = new int[numberOfConjunctions][];
        for (var c = 0; c < numberOfConjunctions; c++) {
            result[c] = conjToFormulas.get(c).stream().mapToInt(Integer::intValue).toArray();
        }
        return result;
    }

    private static void buildPredicateData(List<DisjunctiveFormula> formulas,
            Map<ConjunctiveClause, Integer> conjunctionToIndex, Map<IndexPredicate, Integer> predicateToIndex,
            BitSet[] falseForTruePredicate, BitSet[] falseForFalsePredicate, BitSet[] conjunctionsWithPredicate,
            List<Set<Integer>> relatedFormulas) {
        for (var f = 0; f < formulas.size(); f++) {
            val formula = formulas.get(f);
            for (val clause : formula.clauses()) {
                val ci = conjunctionToIndex.get(clause);
                for (val literal : clause.literals()) {
                    val p = predicateToIndex.get(literal.predicate());
                    conjunctionsWithPredicate[p].set(ci);
                    relatedFormulas.get(p).add(f);
                    if (literal.negated()) {
                        falseForTruePredicate[p].set(ci);
                    } else {
                        falseForFalsePredicate[p].set(ci);
                    }
                }
            }
        }
    }

}
