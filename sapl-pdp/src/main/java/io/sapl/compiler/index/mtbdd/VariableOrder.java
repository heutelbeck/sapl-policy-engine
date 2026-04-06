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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import io.sapl.api.model.BooleanExpression;
import io.sapl.api.model.BooleanExpression.And;
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

    private final List<IndexPredicate>         predicates;
    private final Map<IndexPredicate, Integer> predicateToLevel;

    private VariableOrder(List<IndexPredicate> predicates, Map<IndexPredicate, Integer> predicateToLevel) {
        this.predicates       = predicates;
        this.predicateToLevel = predicateToLevel;
    }

    /**
     * Scans the given boolean expressions and builds a variable order
     * sorted by formula frequency (descending).
     *
     * @param expressions the boolean expressions to scan
     * @return the computed variable order
     */
    static VariableOrder fromExpressions(List<BooleanExpression> expressions) {
        val formulaCount = new HashMap<IndexPredicate, Integer>();

        for (val expression : expressions) {
            val predicatesInFormula = new HashSet<IndexPredicate>();
            collectPredicates(expression, predicatesInFormula);
            for (val predicate : predicatesInFormula) {
                formulaCount.merge(predicate, 1, Integer::sum);
            }
        }

        val sorted = new ArrayList<>(formulaCount.entrySet());
        sorted.sort(Map.Entry.<IndexPredicate, Integer>comparingByValue().reversed()
                .thenComparingLong(e -> e.getKey().semanticHash()));

        val predicates       = new ArrayList<IndexPredicate>(sorted.size());
        val predicateToLevel = new HashMap<IndexPredicate, Integer>(sorted.size());
        for (val entry : sorted) {
            predicateToLevel.put(entry.getKey(), predicates.size());
            predicates.add(entry.getKey());
        }

        return new VariableOrder(List.copyOf(predicates), Map.copyOf(predicateToLevel));
    }

    /**
     * @return the level (0-based index) for the given predicate
     * @throws IllegalArgumentException if the predicate is not in the order
     */
    int levelOf(IndexPredicate predicate) {
        val level = predicateToLevel.get(predicate);
        if (level == null) {
            throw new IllegalArgumentException("Predicate not in variable order: " + predicate);
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
