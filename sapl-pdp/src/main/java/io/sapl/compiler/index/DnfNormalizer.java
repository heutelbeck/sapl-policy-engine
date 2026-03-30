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
package io.sapl.compiler.index;

import java.util.ArrayList;
import java.util.List;

import io.sapl.compiler.index.BooleanExpression.And;
import io.sapl.compiler.index.BooleanExpression.Atom;
import io.sapl.compiler.index.BooleanExpression.Constant;
import io.sapl.compiler.index.BooleanExpression.Not;
import io.sapl.compiler.index.BooleanExpression.Or;
import lombok.experimental.UtilityClass;

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
        return toDnf(expression).reduce();
    }

    private static DisjunctiveFormula toDnf(BooleanExpression expression) {
        return switch (expression) {
        case Constant(var value) -> value ? DisjunctiveFormula.TRUE : DisjunctiveFormula.FALSE;
        case Atom(var predicate) -> atomToDnf(predicate, false);
        case Not(var operand)    -> negateToDnf(operand);
        case Or(var operands)    -> disjoinToDnf(operands);
        case And(var operands)   -> conjoinToDnf(operands);
        };
    }

    private static DisjunctiveFormula atomToDnf(IndexPredicate predicate, boolean negated) {
        return new DisjunctiveFormula(new ConjunctiveClause(new Literal(predicate, negated)));
    }

    private static DisjunctiveFormula negateToDnf(BooleanExpression operand) {
        return switch (operand) {
        case Constant(var value) -> value ? DisjunctiveFormula.FALSE : DisjunctiveFormula.TRUE;
        case Atom(var predicate) -> atomToDnf(predicate, true);
        case Not(var inner)      -> toDnf(inner);
        case Or(var operands)    -> conjoinToDnf(operands.stream().map(Not::new).toList());
        case And(var operands)   -> disjoinToDnf(operands.stream().map(Not::new).toList());
        };
    }

    private static DisjunctiveFormula disjoinToDnf(List<? extends BooleanExpression> operands) {
        var result = DisjunctiveFormula.FALSE;
        for (var operand : operands) {
            var dnf = toDnf(operand);
            result = disjoin(result, dnf);
        }
        return result;
    }

    private static DisjunctiveFormula conjoinToDnf(List<? extends BooleanExpression> operands) {
        var result = DisjunctiveFormula.TRUE;
        for (var operand : operands) {
            var dnf = toDnf(operand);
            result = distribute(result, dnf);
        }
        return result;
    }

    private static DisjunctiveFormula disjoin(DisjunctiveFormula left, DisjunctiveFormula right) {
        var clauses = new ArrayList<ConjunctiveClause>(left.clauses().size() + right.clauses().size());
        clauses.addAll(left.clauses());
        clauses.addAll(right.clauses());
        return new DisjunctiveFormula(clauses);
    }

    /**
     * Distributes AND over OR: (a OR b) AND (c OR d) = (a AND c) OR (a AND d) OR
     * (b AND c) OR (b AND d).
     */
    private static DisjunctiveFormula distribute(DisjunctiveFormula left, DisjunctiveFormula right) {
        var clauses = new ArrayList<ConjunctiveClause>(left.clauses().size() * right.clauses().size());
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
