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

import io.sapl.api.model.BooleanExpression;
import io.sapl.api.model.BooleanExpression.And;
import io.sapl.api.model.BooleanExpression.Atom;
import io.sapl.api.model.BooleanExpression.Constant;
import io.sapl.api.model.BooleanExpression.Not;
import io.sapl.api.model.BooleanExpression.Or;
import io.sapl.compiler.index.mtbdd.MtbddNode.Terminal;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.BitSet;

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
        val empty = new BitSet();

        // The two possible leaf outcomes for this formula
        val matched = table.terminal(formulaTag, empty);
        val errored = table.terminal(empty, formulaTag);

        return build(table, order, expression, matched, errored);
    }

    /**
     * Recursively converts a boolean expression into an MTBDD.
     * <p>
     * The matched/errored terminals are the same for all recursion
     * levels within one formula - they identify THIS formula in the
     * combined diagram later.
     */
    private static MtbddNode build(UniqueTable table, VariableOrder order, BooleanExpression expression,
            Terminal matched, Terminal errored) {
        return switch (expression) {
        // Constant true: formula always satisfied
        // Constant false: formula never satisfied
        case Constant(var value) -> value ? matched : UniqueTable.EMPTY;

        // Single predicate: 3-way branch.
        // True -> formula satisfied, False -> formula not satisfied, Error -> formula
        // errored (terminal)
        case Atom(var predicate) -> {
            val level = order.levelOf(predicate);
            yield table.decision(level, matched, UniqueTable.EMPTY, errored);
        }

        // Negation: delegate to buildNot which inverts true/false semantics
        case Not(var operand) -> buildNot(table, order, operand, matched, errored);

        // Disjunction: formula is satisfied if ANY operand is satisfied
        case Or(var operands) -> {
            var result = build(table, order, operands.getFirst(), matched, errored);
            for (var i = 1; i < operands.size(); i++) {
                result = or(table, result, build(table, order, operands.get(i), matched, errored));
            }
            yield result;
        }

        // Conjunction: formula is satisfied only if ALL operands are satisfied
        case And(var operands) -> {
            var result = build(table, order, operands.getFirst(), matched, errored);
            for (var i = 1; i < operands.size(); i++) {
                result = and(table, result, build(table, order, operands.get(i), matched, errored));
            }
            yield result;
        }
        };
    }

    /**
     * Builds the MTBDD for the negation of an expression.
     * <p>
     * For atoms, true/false are swapped.
     * For compound expressions, De Morgan's laws apply.
     * Error semantics are unchanged - errors are terminal regardless of negation.
     */
    private static MtbddNode buildNot(UniqueTable table, VariableOrder order, BooleanExpression operand,
            Terminal matched, Terminal errored) {
        return switch (operand) {
        case Constant(var value) -> value ? UniqueTable.EMPTY : matched;

        // NOT(predicate): satisfied when predicate is FALSE (inverted), error still
        // terminal
        case Atom(var predicate) -> {
            val level = order.levelOf(predicate);
            yield table.decision(level, UniqueTable.EMPTY, matched, errored);
        }

        // Double negation elimination
        case Not(var inner) -> build(table, order, inner, matched, errored);

        // De Morgan: NOT(a OR b) = NOT(a) AND NOT(b)
        case Or(var operands) -> {
            var result = buildNot(table, order, operands.getFirst(), matched, errored);
            for (var i = 1; i < operands.size(); i++) {
                result = and(table, result, buildNot(table, order, operands.get(i), matched, errored));
            }
            yield result;
        }

        // De Morgan: NOT(a AND b) = NOT(a) OR NOT(b)
        case And(var operands) -> {
            var result = buildNot(table, order, operands.getFirst(), matched, errored);
            for (var i = 1; i < operands.size(); i++) {
                result = or(table, result, buildNot(table, order, operands.get(i), matched, errored));
            }
            yield result;
        }
        };
    }

    /**
     * Combines two sub-expression MTBDDs with OR semantics for a single formula.
     * <p>
     * Canonical error semantics: match-only and error-only terminals absorb.
     * A matched formula is decided (no further predicates matter). An errored
     * formula is decided (error kills the formula). Only used during per-formula
     * construction where all BDDs represent the same single formula.
     */
    private static MtbddNode or(UniqueTable table, MtbddNode a, MtbddNode b) {
        if (a == b) {
            return a;
        }
        if (a == UniqueTable.EMPTY) {
            return b;
        }
        if (b == UniqueTable.EMPTY) {
            return a;
        }
        // Match-only terminal absorbs: OR is satisfied, no further checks needed
        if (isMatchOnly(a)) {
            return a;
        }
        if (isMatchOnly(b)) {
            return b;
        }
        // Error-only terminal absorbs: formula is errored, canonical semantics
        if (isErrorOnly(a)) {
            return a;
        }
        if (isErrorOnly(b)) {
            return b;
        }

        // At least one decision node - recurse on the topmost variable
        val topLevel = Math.min(levelOf(a), levelOf(b));
        val ac       = childrenAt(a, topLevel);
        val bc       = childrenAt(b, topLevel);

        return table.decision(topLevel, or(table, ac[0], bc[0]), or(table, ac[1], bc[1]), or(table, ac[2], bc[2]));
    }

    /**
     * Combines two sub-expression MTBDDs with AND semantics for a single formula.
     * <p>
     * Canonical error semantics: EMPTY and error-only terminals absorb.
     * Only used during per-formula construction.
     */
    private static MtbddNode and(UniqueTable table, MtbddNode a, MtbddNode b) {
        if (a == b) {
            return a;
        }
        // EMPTY absorbs: AND with unsatisfied = unsatisfied
        if (a == UniqueTable.EMPTY || b == UniqueTable.EMPTY) {
            return UniqueTable.EMPTY;
        }
        // Error-only terminal absorbs: formula is errored, canonical semantics
        if (isErrorOnly(a)) {
            return a;
        }
        if (isErrorOnly(b)) {
            return b;
        }
        // Match-only terminal is identity for AND: the other side decides
        if (isMatchOnly(a)) {
            return b;
        }
        if (isMatchOnly(b)) {
            return a;
        }

        // At least one decision node - recurse on the topmost variable
        val topLevel = Math.min(levelOf(a), levelOf(b));
        val ac       = childrenAt(a, topLevel);
        val bc       = childrenAt(b, topLevel);

        return table.decision(topLevel, and(table, ac[0], bc[0]), and(table, ac[1], bc[1]), and(table, ac[2], bc[2]));
    }

    private static boolean isMatchOnly(MtbddNode node) {
        return node instanceof Terminal(var matched, var errored) && !matched.isEmpty() && errored.isEmpty();
    }

    private static boolean isErrorOnly(MtbddNode node) {
        return node instanceof Terminal(var matched, var errored) && matched.isEmpty() && !errored.isEmpty();
    }

    private static int levelOf(MtbddNode node) {
        return switch (node) {
        case Terminal ignored     -> Integer.MAX_VALUE;
        case MtbddNode.Decision d -> d.level();
        };
    }

    /**
     * Extracts children at the given level. If the node's level is
     * deeper, this variable doesn't affect it - returns the node
     * itself for all three edges.
     */
    private static MtbddNode[] childrenAt(MtbddNode node, int level) {
        if (node instanceof MtbddNode.Decision(int nodeLevel, MtbddNode trueChild, MtbddNode falseChild, MtbddNode errorChild)
                && nodeLevel == level) {
            return new MtbddNode[] { trueChild, falseChild, errorChild };
        }
        return new MtbddNode[] { node, node, node };
    }

}
