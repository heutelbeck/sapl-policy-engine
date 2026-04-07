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
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import io.sapl.api.model.BooleanExpression;
import io.sapl.api.model.BooleanExpression.And;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.api.model.BooleanExpression.Atom;
import io.sapl.api.model.BooleanExpression.Constant;
import io.sapl.api.model.BooleanExpression.Not;
import io.sapl.api.model.BooleanExpression.Or;
import io.sapl.api.model.IndexPredicate;
import lombok.val;

/**
 * Computes a fixed variable order for MTBDD construction by scanning
 * all boolean expressions and ordering predicates by the number of
 * formulas that reference them (descending).
 * <p>
 * Predicates referenced by more formulas are placed earlier (lower
 * level index) in the order. This maximizes early discrimination:
 * evaluating a high-frequency predicate first partitions the most
 * formulas in a single step.
 * <p>
 * The order is computed once and shared by all per-formula MTBDD
 * builds and the Apply algorithm.
 */
class VariableOrder {

    private static final String ERROR_PREDICATE_NOT_IN_ORDER = "Predicate not in variable order: %s";

    private final List<IndexPredicate>         predicates;
    private final Map<IndexPredicate, Integer> predicateToLevel;
    private final BitSet[]                     erroredFormulasPerPredicate;

    private VariableOrder(List<IndexPredicate> predicates,
            Map<IndexPredicate, Integer> predicateToLevel,
            BitSet[] erroredFormulasPerPredicate) {
        this.predicates                  = predicates;
        this.predicateToLevel            = predicateToLevel;
        this.erroredFormulasPerPredicate = erroredFormulasPerPredicate;
    }

    /**
     * Scans the given boolean expressions and builds a variable order
     * sorted by formula frequency (descending).
     *
     * @param expressions the boolean expressions to scan
     * @return the computed variable order
     */
    static VariableOrder fromExpressions(List<BooleanExpression> expressions) {
        val formulaCountPerPredicate = new HashMap<IndexPredicate, Integer>();
        val formulasPerPredicate     = new HashMap<IndexPredicate, BitSet>();

        for (var formulaIndex = 0; formulaIndex < expressions.size(); formulaIndex++) {
            val predicatesInFormula = new HashSet<IndexPredicate>();
            collectPredicates(expressions.get(formulaIndex), predicatesInFormula);
            for (val predicate : predicatesInFormula) {
                formulaCountPerPredicate.merge(predicate, 1, Integer::sum);
                formulasPerPredicate.computeIfAbsent(predicate, k -> new BitSet()).set(formulaIndex);
            }
        }

        val sortedPredicates = new ArrayList<>(formulaCountPerPredicate.keySet());
        sortedPredicates.sort(Comparator.comparingInt((IndexPredicate p) -> -formulaCountPerPredicate.get(p))
                .thenComparingLong(IndexPredicate::semanticHash));

        val numberOfPredicates = sortedPredicates.size();
        val predicateToLevel   = new HashMap<IndexPredicate, Integer>(numberOfPredicates);
        val erroredFormulas    = new BitSet[numberOfPredicates];
        for (var level = 0; level < numberOfPredicates; level++) {
            val predicate = sortedPredicates.get(level);
            predicateToLevel.put(predicate, level);
            erroredFormulas[level] = formulasPerPredicate.get(predicate);
        }

        return new VariableOrder(sortedPredicates, predicateToLevel, erroredFormulas);
    }

    /**
     * @return the level (0-based index) for the given predicate
     * @throws IllegalArgumentException if the predicate is not in the order
     */
    int levelOf(IndexPredicate predicate) {
        val level = predicateToLevel.get(predicate);
        if (level == null) {
            throw new SaplCompilerException(ERROR_PREDICATE_NOT_IN_ORDER.formatted(predicate));
        }
        return level;
    }

    /**
     * @return the predicate at the given level
     */
    IndexPredicate predicateAt(int level) {
        return predicates.get(level);
    }

    /**
     * @return the total number of predicates in the order
     */
    int size() {
        return predicates.size();
    }

    /**
     * @return the ordered predicates (level 0 first)
     */
    List<IndexPredicate> predicates() {
        return predicates;
    }

    /**
     * Returns the set of formula indices that are killed when the
     * predicate at the given level errors during evaluation.
     *
     * @param level the predicate level
     * @return formulas referencing this predicate
     */
    BitSet erroredFormulas(int level) {
        return erroredFormulasPerPredicate[level];
    }

    private static void collectPredicates(BooleanExpression expression, HashSet<IndexPredicate> result) {
        switch (expression) {
        case Constant ignored    -> { /* no predicates */ }
        case Atom(var predicate) -> result.add(predicate);
        case Not(var operand)    -> collectPredicates(operand, result);
        case Or(var operands)    -> operands.forEach(op -> collectPredicates(op, result));
        case And(var operands)   -> operands.forEach(op -> collectPredicates(op, result));
        }
    }

}
