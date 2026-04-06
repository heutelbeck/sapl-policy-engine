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

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.index.dnf.DisjunctiveFormula;
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
 * @param predicateOriginalIndices maps priority position to original predicate
 * index for bitmask array lookups
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
 * @param formulaDocuments documents per formula, indexed by formula index
 */
record CanonicalIndexData(
        List<IndexPredicate> predicateOrder,
        int[] predicateOriginalIndices,
        int numberOfConjunctions,
        int[] numberOfLiteralsInConjunction,
        int[] numberOfFormulasWithConjunction,
        int[][] conjunctionToFormulaIndices,
        int[][] formulaConjunctionIndices,
        BitSet[] falseForTruePredicate,
        BitSet[] falseForFalsePredicate,
        BitSet[] conjunctionsWithPredicate,
        int[][] relatedFormulas,
        List<DisjunctiveFormula> formulas,
        List<List<CompiledDocument>> formulaDocuments) {

    @Override
    public int hashCode() {
        return Objects.hash(predicateOrder, Arrays.hashCode(predicateOriginalIndices), numberOfConjunctions,
                Arrays.hashCode(numberOfLiteralsInConjunction), Arrays.hashCode(numberOfFormulasWithConjunction),
                Arrays.deepHashCode(conjunctionToFormulaIndices), Arrays.deepHashCode(formulaConjunctionIndices),
                Arrays.hashCode(falseForTruePredicate), Arrays.hashCode(falseForFalsePredicate),
                Arrays.hashCode(conjunctionsWithPredicate), Arrays.deepHashCode(relatedFormulas), formulas,
                formulaDocuments);
    }

    @Override
    public boolean equals(Object o) {
        return this == o
                || (o instanceof CanonicalIndexData(var oPredOrder, var oPredOrigIdx, var oNumConj, var oNumLitsInConj, var oNumFormulasWithConj, var oConjToFormIdx, var oFormConjIdx, var oFalseForTrue, var oFalseForFalse, var oConjWithPred, var oRelFormulas, var oFormulas, var oFormDocs)
                        && Objects.equals(predicateOrder, oPredOrder)
                        && Arrays.equals(predicateOriginalIndices, oPredOrigIdx) && numberOfConjunctions == oNumConj
                        && Arrays.equals(numberOfLiteralsInConjunction, oNumLitsInConj)
                        && Arrays.equals(numberOfFormulasWithConjunction, oNumFormulasWithConj)
                        && Arrays.deepEquals(conjunctionToFormulaIndices, oConjToFormIdx)
                        && Arrays.deepEquals(formulaConjunctionIndices, oFormConjIdx)
                        && Arrays.equals(falseForTruePredicate, oFalseForTrue)
                        && Arrays.equals(falseForFalsePredicate, oFalseForFalse)
                        && Arrays.equals(conjunctionsWithPredicate, oConjWithPred)
                        && Arrays.deepEquals(relatedFormulas, oRelFormulas) && Objects.equals(formulas, oFormulas)
                        && Objects.equals(formulaDocuments, oFormDocs));
    }

    /**
     * Average number of formulas per predicate. Values close to 1.0 indicate
     * no predicate sharing (each predicate is unique to one formula). Higher
     * values indicate shared predicates where the canonical index benefits.
     *
     * @return average formulas per predicate
     */
    double averageFormulasPerPredicate() {
        if (relatedFormulas.length == 0) {
            return 0.0;
        }
        var total = 0;
        for (var rf : relatedFormulas) {
            total += rf.length;
        }
        return (double) total / relatedFormulas.length;
    }
}
