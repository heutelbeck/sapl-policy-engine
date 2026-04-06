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

import java.util.BitSet;

import io.sapl.api.model.BooleanExpression;
import io.sapl.api.model.BooleanExpression.And;
import io.sapl.api.model.BooleanExpression.Atom;
import io.sapl.api.model.BooleanExpression.Constant;
import io.sapl.api.model.BooleanExpression.Not;
import io.sapl.api.model.BooleanExpression.Or;
import io.sapl.compiler.index.mtbdd.MtbddNode.Decision;
import io.sapl.compiler.index.mtbdd.MtbddNode.Terminal;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Builds individual MTBDD diagrams from {@link BooleanExpression} trees.
 * <p>
 * Each formula is converted into a small MTBDD where every path from
 * root to leaf represents one combination of predicate outcomes. Leaf
 * terminals carry the formula index in either the matched or errored
 * set depending on the path.
 * <p>
 * Error semantics match the canonical index: if any predicate in a
 * formula errors, the formula is immediately errored. The error edge
 * of every decision node is a terminal - no further predicates are
 * checked for this formula on an error path.
 * <p>
 * All nodes are interned via the shared {@link UniqueTable} so that
 * structurally identical subtrees are the same object.
 */
@UtilityClass
class MtbddBuilder {

    /**
     * Builds an MTBDD for a single formula.
     * <p>
     * Creates two reusable leaf terminals for this formula's index:
     * one for "matched" paths and one for "errored" paths. These are
     * passed through the recursion so every atom in the expression
     * can reference them without re-creating BitSets.
     *
     * @param table the shared unique table for node interning
     * @param order the fixed variable order
     * @param expression the boolean expression for this formula
     * @param formulaIndex the formula's index (used in terminal BitSets)
     * @return the root node of the per-formula MTBDD
     */
    static MtbddNode buildFormula(UniqueTable table, VariableOrder order, BooleanExpression expression,
            int formulaIndex) {
        // Tag this formula with its index so it can be identified after merging
        val formulaTag = new BitSet();
        formulaTag.set(formulaIndex);

        val matched = table.terminal(formulaTag);

        return build(table, order, expression, matched);
    }

    /**
     * Recursively converts a boolean expression into an MTBDD.
     * <p>
     * Error edges go to EMPTY: this formula is not matched on error paths.
     * The evaluator handles error tracking externally via the precomputed
     * erroredFormulas array in VariableOrder.
     */
    private static MtbddNode build(UniqueTable table, VariableOrder order, BooleanExpression expression,
            Terminal matched) {
        return switch (expression) {
        case Constant(var value) -> value ? matched : UniqueTable.EMPTY;

        // Error edge -> EMPTY: formula not matched, evaluator records the error
        // separately
        case Atom(var predicate) -> {
            val level = order.levelOf(predicate);
            yield table.decision(level, matched, UniqueTable.EMPTY, UniqueTable.EMPTY);
        }

        case Not(var operand) -> buildNot(table, order, operand, matched);

        case Or(var operands) -> {
            var result = build(table, order, operands.getFirst(), matched);
            for (var i = 1; i < operands.size(); i++) {
                result = or(table, result, build(table, order, operands.get(i), matched));
            }
            yield result;
        }

        case And(var operands) -> {
            var result = build(table, order, operands.getFirst(), matched);
            for (var i = 1; i < operands.size(); i++) {
                result = and(table, result, build(table, order, operands.get(i), matched));
            }
            yield result;
        }
        };
    }

    /**
     * Builds the MTBDD for the negation of an expression.
     * True/false swapped for atoms, De Morgan for compound expressions.
     */
    private static MtbddNode buildNot(UniqueTable table, VariableOrder order, BooleanExpression operand,
            Terminal matched) {
        return switch (operand) {
        case Constant(var value) -> value ? UniqueTable.EMPTY : matched;

        case Atom(var predicate) -> {
            val level = order.levelOf(predicate);
            yield table.decision(level, UniqueTable.EMPTY, matched, UniqueTable.EMPTY);
        }

        case Not(var inner) -> build(table, order, inner, matched);

        // De Morgan: NOT(a OR b) = NOT(a) AND NOT(b)
        case Or(var operands) -> {
            var result = buildNot(table, order, operands.getFirst(), matched);
            for (var i = 1; i < operands.size(); i++) {
                result = and(table, result, buildNot(table, order, operands.get(i), matched));
            }
            yield result;
        }

        // De Morgan: NOT(a AND b) = NOT(a) OR NOT(b)
        case And(var operands) -> {
            var result = buildNot(table, order, operands.getFirst(), matched);
            for (var i = 1; i < operands.size(); i++) {
                result = or(table, result, buildNot(table, order, operands.get(i), matched));
            }
            yield result;
        }
        };
    }

    /**
     * Combines two sub-expression MTBDDs with OR semantics for a single formula.
     * <p>
     * Non-empty terminal absorbs: formula is already satisfied on this path.
     * Only used during per-formula construction.
     */
    private static MtbddNode or(UniqueTable table, MtbddNode left, MtbddNode right) {
        if (left == right) {
            return left;
        }
        if (left == UniqueTable.EMPTY) {
            return right;
        }
        if (right == UniqueTable.EMPTY) {
            return left;
        }
        // Non-empty terminal absorbs: OR satisfied, no further checks needed
        if (left instanceof Terminal t && !t.isEmpty()) {
            return left;
        }
        if (right instanceof Terminal t && !t.isEmpty()) {
            return right;
        }

        val topLevel      = Math.min(levelOf(left), levelOf(right));
        val leftChildren  = childrenAt(left, topLevel);
        val rightChildren = childrenAt(right, topLevel);

        return table.decision(topLevel, or(table, leftChildren[0], rightChildren[0]),
                or(table, leftChildren[1], rightChildren[1]), or(table, leftChildren[2], rightChildren[2]));
    }

    /**
     * Combines two sub-expression MTBDDs with AND semantics for a single formula.
     * <p>
     * EMPTY absorbs (AND with unsatisfied = unsatisfied).
     * Non-empty terminal is identity (AND with satisfied = the other side decides).
     * Only used during per-formula construction.
     */
    private static MtbddNode and(UniqueTable table, MtbddNode left, MtbddNode right) {
        if (left == right) {
            return left;
        }
        if (left == UniqueTable.EMPTY || right == UniqueTable.EMPTY) {
            return UniqueTable.EMPTY;
        }
        // Non-empty terminal is identity for AND: the other side decides
        if (left instanceof Terminal t && !t.isEmpty()) {
            return right;
        }
        if (right instanceof Terminal t && !t.isEmpty()) {
            return left;
        }

        val topLevel      = Math.min(levelOf(left), levelOf(right));
        val leftChildren  = childrenAt(left, topLevel);
        val rightChildren = childrenAt(right, topLevel);

        return table.decision(topLevel, and(table, leftChildren[0], rightChildren[0]),
                and(table, leftChildren[1], rightChildren[1]), and(table, leftChildren[2], rightChildren[2]));
    }

    private static int levelOf(MtbddNode node) {
        return switch (node) {
        case Terminal ignored -> Integer.MAX_VALUE;
        case Decision d       -> d.level();
        };
    }

    /**
     * Extracts children at the given level. If the node's level is
     * deeper, this variable doesn't affect it - returns the node
     * itself for all three edges.
     */
    private static MtbddNode[] childrenAt(MtbddNode node, int level) {
        if (node instanceof Decision(int nodeLevel, MtbddNode trueChild, MtbddNode falseChild, MtbddNode errorChild)
                && nodeLevel == level) {
            return new MtbddNode[] { trueChild, falseChild, errorChild };
        }
        return new MtbddNode[] { node, node, node };
    }

}
