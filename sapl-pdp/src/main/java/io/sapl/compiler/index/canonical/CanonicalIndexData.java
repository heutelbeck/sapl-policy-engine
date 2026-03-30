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

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.index.DisjunctiveFormula;
import io.sapl.api.model.IndexPredicate;

/**
 * Precomputed data structures for the canonical policy index, built at compile
 * time from the DNF formulas of all indexed pure policies. Based on Definitions
 * 3.2 through 3.7 of the SACMAT '21 paper.
 * <p>
 * The index assigns global integer indices to conjunctions (across all
 * formulas)
 * and to formulas. Predicates are ordered by a priority heuristic for optimal
 * evaluation order.
 *
 * @param predicateOrder ordered list of predicates by evaluation priority
 * @param predicateToIndex maps each predicate to its array index for bitmask
 * lookups
 * @param numberOfConjunctions total number of conjunctions across all formulas
 * @param numberOfLiteralsInConjunction literal count per conjunction (Def 3.2)
 * @param numberOfFormulasWithConjunction how many formulas contain each
 * conjunction (Def 3.4)
 * @param conjunctionToFormulaIndices for each conjunction, the formula indices
 * containing it (derived from Def 3.5)
 * @param formulaConjunctionIndices for each formula, the conjunction indices in
 * it
 * @param falseForTruePredicate per-predicate bitmask: conjunctions containing a
 * negated literal for this predicate, becoming unsatisfiable when true
 * (Def 3.3)
 * @param falseForFalsePredicate per-predicate bitmask: conjunctions containing
 * a
 * positive literal for this predicate, becoming unsatisfiable when false
 * (Def 3.3)
 * @param conjunctionsWithPredicate per-predicate bitmask: conjunctions that
 * reference this predicate (Def 3.6)
 * @param relatedFormulas per-predicate: formula indices that reference this
 * predicate (Def 3.7)
 * @param formulas all indexed formulas in order
 * @param formulaToDocuments maps each formula to its compiled documents
 */
record CanonicalIndexData(
        List<IndexPredicate> predicateOrder,
        Map<IndexPredicate, Integer> predicateToIndex,
        int numberOfConjunctions,
        int[] numberOfLiteralsInConjunction,
        int[] numberOfFormulasWithConjunction,
        int[][] conjunctionToFormulaIndices,
        int[][] formulaConjunctionIndices,
        BitSet[] falseForTruePredicate,
        BitSet[] falseForFalsePredicate,
        BitSet[] conjunctionsWithPredicate,
        List<Set<Integer>> relatedFormulas,
        List<DisjunctiveFormula> formulas,
        Map<DisjunctiveFormula, List<CompiledDocument>> formulaToDocuments) {}
