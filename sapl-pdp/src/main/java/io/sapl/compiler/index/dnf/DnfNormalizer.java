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
package io.sapl.compiler.index.dnf;

import io.sapl.api.model.BooleanExpression;
import io.sapl.api.model.BooleanExpression.*;
import io.sapl.api.model.IndexPredicate;
import io.sapl.compiler.index.IndexSizeLimitExceededException;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts a {@link BooleanExpression} tree into Disjunctive Normal Form
 * (DNF). The result is a {@link DisjunctiveFormula} suitable for use in the
 * canonical policy index.
 * <p>
 * The conversion applies:
 * <ul>
 * <li>De Morgan's laws to push negation to literals</li>
 * <li>Distribution of AND over OR to achieve DNF</li>
 * <li>Double negation elimination</li>
 * <li>Constant propagation (true/false absorption)</li>
 * </ul>
 * The resulting formula is reduced (unsatisfiable, duplicate, and subsumed
 * clauses removed).
 */
@UtilityClass
public class DnfNormalizer {

    /**
     * Normalizes a boolean expression to DNF.
     *
     * @param expression the boolean expression tree
     * @return the equivalent formula in disjunctive normal form, reduced
     */
    public static DisjunctiveFormula normalize(BooleanExpression expression) {
        return normalize(expression, Integer.MAX_VALUE);
    }

    /**
     * Normalizes a boolean expression to DNF, bounding the intermediate clause
     * count so a pathological AND-of-ORs cannot explode the heap at compile time.
     *
     * @param expression the boolean expression tree
     * @param maxClauses the maximum number of conjunctive clauses permitted in any
     * intermediate formula
     * @return the equivalent formula in disjunctive normal form, reduced
     * @throws IndexSizeLimitExceededException if a distribution would exceed
     * {@code maxClauses}; the check runs before the product is materialized, so the
     * exponential intermediate is never allocated
     */
    public static DisjunctiveFormula normalize(BooleanExpression expression, int maxClauses) {
        return toDnf(expression, maxClauses).reduce();
    }

    private static DisjunctiveFormula toDnf(BooleanExpression expression, int maxClauses) {
        return switch (expression) {
        case Constant(var value) -> value ? DisjunctiveFormula.TRUE : DisjunctiveFormula.FALSE;
        case Atom(var predicate) -> atomToDnf(predicate, false);
        case Not(var operand)    -> negateToDnf(operand, maxClauses);
        case Or(var operands)    -> disjoinToDnf(operands, maxClauses);
        case And(var operands)   -> conjoinToDnf(operands, maxClauses);
        };
    }

    private static DisjunctiveFormula atomToDnf(IndexPredicate predicate, boolean negated) {
        return new DisjunctiveFormula(new ConjunctiveClause(new Literal(predicate, negated)));
    }

    private static DisjunctiveFormula negateToDnf(BooleanExpression operand, int maxClauses) {
        return switch (operand) {
        case Constant(var value) -> value ? DisjunctiveFormula.FALSE : DisjunctiveFormula.TRUE;
        case Atom(var predicate) -> atomToDnf(predicate, true);
        case Not(var inner)      -> toDnf(inner, maxClauses);
        case Or(var operands)    -> conjoinToDnf(operands.stream().map(Not::new).toList(), maxClauses);
        case And(var operands)   -> disjoinToDnf(operands.stream().map(Not::new).toList(), maxClauses);
        };
    }

    private static DisjunctiveFormula disjoinToDnf(List<? extends BooleanExpression> operands, int maxClauses) {
        var result = DisjunctiveFormula.FALSE;
        for (var operand : operands) {
            var dnf = toDnf(operand, maxClauses);
            result = disjoin(result, dnf, maxClauses);
        }
        return result;
    }

    private static DisjunctiveFormula conjoinToDnf(List<? extends BooleanExpression> operands, int maxClauses) {
        var result = DisjunctiveFormula.TRUE;
        for (var operand : operands) {
            var dnf = toDnf(operand, maxClauses);
            result = distribute(result, dnf, maxClauses);
        }
        return result;
    }

    private static DisjunctiveFormula disjoin(DisjunctiveFormula left, DisjunctiveFormula right, int maxClauses) {
        var total = left.clauses().size() + right.clauses().size();
        if (total > maxClauses) {
            throw new IndexSizeLimitExceededException(total, maxClauses);
        }
        var clauses = new ArrayList<ConjunctiveClause>(total);
        clauses.addAll(left.clauses());
        clauses.addAll(right.clauses());
        return new DisjunctiveFormula(clauses);
    }

    /**
     * Distributes AND over OR: (a OR b) AND (c OR d) = (a AND c) OR (a AND d) OR
     * (b AND c) OR (b AND d). The clause count of the result is the product of the
     * operand clause counts, which is exponential across a chain of conjunctions,
     * so the product is bounded before it is built.
     */
    private static DisjunctiveFormula distribute(DisjunctiveFormula left, DisjunctiveFormula right, int maxClauses) {
        var product = (long) left.clauses().size() * right.clauses().size();
        if (product > maxClauses) {
            throw new IndexSizeLimitExceededException((int) Math.min(product, Integer.MAX_VALUE), maxClauses);
        }
        var clauses = new ArrayList<ConjunctiveClause>((int) product);
        for (var leftClause : left.clauses()) {
            for (var rightClause : right.clauses()) {
                clauses.add(mergeClause(leftClause, rightClause));
            }
        }
        return new DisjunctiveFormula(clauses);
    }

    private static ConjunctiveClause mergeClause(ConjunctiveClause a, ConjunctiveClause b) {
        var literals = new ArrayList<Literal>(a.size() + b.size());
        literals.addAll(a.literals());
        literals.addAll(b.literals());
        return new ConjunctiveClause(literals);
    }

}
