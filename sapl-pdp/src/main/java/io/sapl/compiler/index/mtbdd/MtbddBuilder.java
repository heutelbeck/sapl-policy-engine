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
 * Error semantics: when a predicate errors, all formulas referencing
 * that predicate receive an error vote, matching the canonical index
 * behavior.
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
        // Identity tag for this formula - never mutated after creation
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
        // Constant true: formula always satisfied -> matched leaf
        // Constant false: formula never satisfied -> empty leaf (not even in the
        // diagram)
        case Constant(var value) -> value ? matched : UniqueTable.EMPTY;

        // Single predicate: creates a 3-way branch.
        // If predicate is true -> formula satisfied (matched leaf)
        // If predicate is false -> formula not satisfied (empty leaf)
        // If predicate errors -> formula gets error vote (errored leaf)
        case Atom(var predicate) -> {
            val level = order.levelOf(predicate);
            yield table.decision(level, matched, UniqueTable.EMPTY, errored);
        }

        // Negation: delegate to buildNot which inverts true/false semantics
        case Not(var operand) -> buildNot(table, order, operand, matched, errored);

        // Disjunction: build each operand's MTBDD, combine with OR.
        // OR means: formula is satisfied if ANY operand is satisfied.
        // We fold left: ((first OR second) OR third) ...
        case Or(var operands) -> {
            var result = build(table, order, operands.getFirst(), matched, errored);
            for (var i = 1; i < operands.size(); i++) {
                result = or(table, result, build(table, order, operands.get(i), matched, errored));
            }
            yield result;
        }

        // Conjunction: build each operand's MTBDD, combine with AND.
        // AND means: formula is satisfied only if ALL operands are satisfied.
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
     * Key difference from build(): for atoms, true/false are swapped.
     * For compound expressions, De Morgan's laws are applied:
     * NOT(a OR b) = NOT(a) AND NOT(b),
     * NOT(a AND b) = NOT(a) OR NOT(b).
     * Error semantics are unchanged - errors propagate regardless of negation.
     */
    private static MtbddNode buildNot(UniqueTable table, VariableOrder order, BooleanExpression operand,
            Terminal matched, Terminal errored) {
        return switch (operand) {
        // NOT true = false, NOT false = true
        case Constant(var value) -> value ? UniqueTable.EMPTY : matched;

        // NOT(predicate): satisfied when predicate is FALSE (inverted).
        // True -> not satisfied (empty), False -> satisfied (matched), Error -> still
        // errored
        case Atom(var predicate) -> {
            val level = order.levelOf(predicate);
            yield table.decision(level, UniqueTable.EMPTY, matched, errored);
        }

        // Double negation elimination: NOT(NOT(x)) = x
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
     * Combines two MTBDD nodes with OR semantics.
     * <p>
     * Used within a single formula build (e.g., p1 OR p2) and later
     * for merging per-formula MTBDDs into the combined index.
     * <p>
     * OR means: a formula is in the result if it appears in EITHER input.
     * For matched sets: union. For errored sets: union.
     */
    static MtbddNode or(UniqueTable table, MtbddNode a, MtbddNode b) {
        // Same node: OR with itself is identity (interning guarantees == works)
        if (a == b) {
            return a;
        }
        // EMPTY is the identity element for OR: x OR nothing = x
        if (a == UniqueTable.EMPTY) {
            return b;
        }
        if (b == UniqueTable.EMPTY) {
            return a;
        }
        // A matched-only terminal (no errors) absorbs the other side for OR:
        // if these formulas are definitely satisfied, the other operand's
        // error edges for those formulas become irrelevant. In OR, a satisfied
        // operand means the formula is decided - no error vote needed.
        if (a instanceof Terminal(BitSet matchedA, BitSet erroredA) && !matchedA.isEmpty() && erroredA.isEmpty()) {
            return propagateMatch(table, b, matchedA);
        }
        if (b instanceof Terminal(BitSet matchedB, BitSet erroredB) && !matchedB.isEmpty() && erroredB.isEmpty()) {
            return propagateMatch(table, a, matchedB);
        }
        // Both are leaves: combine their formula sets directly.
        // Union of matched, union of errored.
        if (a instanceof Terminal(BitSet matchedA2, BitSet erroredA2)
                && b instanceof Terminal(BitSet matchedB2, BitSet erroredB2)) {
            val combinedMatched = (BitSet) matchedA2.clone();
            combinedMatched.or(matchedB2);
            val combinedErrored = (BitSet) erroredA2.clone();
            combinedErrored.or(erroredB2);
            return table.terminal(combinedMatched, combinedErrored);
        }

        // At least one is a decision node.
        // Find the topmost variable (lowest level number) between the two nodes.
        // Both nodes are walked in sync at this variable level.
        val levelA   = levelOf(a);
        val levelB   = levelOf(b);
        val topLevel = Math.min(levelA, levelB);

        // If a node's level is deeper than topLevel, it doesn't branch here.
        // childrenAt returns [node, node, node] in that case - the node passes
        // through unchanged on all three edges.
        val aChildren = childrenAt(a, topLevel);
        val bChildren = childrenAt(b, topLevel);

        // Recurse on each edge independently: what happens when predicate is
        // true/false/error?
        val trueChild  = or(table, aChildren[0], bChildren[0]);
        val falseChild = or(table, aChildren[1], bChildren[1]);
        val errorChild = or(table, aChildren[2], bChildren[2]);

        return table.decision(topLevel, trueChild, falseChild, errorChild);
    }

    /**
     * Combines two MTBDD nodes with AND semantics.
     * <p>
     * AND means: a formula is matched only if it appears in BOTH inputs.
     * For matched sets: intersection. For errored sets: union (errors
     * propagate from either side).
     */
    static MtbddNode and(UniqueTable table, MtbddNode a, MtbddNode b) {
        // Same node: AND with itself is identity
        if (a == b) {
            return a;
        }
        // EMPTY is the absorbing element for AND: x AND nothing = nothing
        if (a == UniqueTable.EMPTY || b == UniqueTable.EMPTY) {
            return UniqueTable.EMPTY;
        }
        // Both are leaves: intersect matched (both must agree), union errored
        if (a instanceof Terminal(BitSet matchedA, BitSet erroredA)
                && b instanceof Terminal(BitSet matchedB, BitSet erroredB)) {
            val combinedMatched = (BitSet) matchedA.clone();
            combinedMatched.and(matchedB);
            val combinedErrored = (BitSet) erroredA.clone();
            combinedErrored.or(erroredB);
            return table.terminal(combinedMatched, combinedErrored);
        }
        // One side is error-only (matched is empty): the AND can never produce
        // a match from this side, but the errors must propagate into every leaf
        // of the other side's subtree.
        if (a instanceof Terminal(BitSet matchedA, BitSet erroredA) && matchedA.isEmpty()) {
            return propagateError(table, b, erroredA);
        }
        if (b instanceof Terminal(BitSet matchedB, BitSet erroredB) && matchedB.isEmpty()) {
            return propagateError(table, a, erroredB);
        }
        // One side is match-only (no errors): it acts as identity for AND.
        // The other side alone determines the result.
        if (a instanceof Terminal(BitSet matchedA, BitSet erroredA) && erroredA.isEmpty()) {
            return b;
        }
        if (b instanceof Terminal(BitSet matchedB, BitSet erroredB) && erroredB.isEmpty()) {
            return a;
        }

        // Both are decision nodes - same topmost-variable walk as in OR
        val levelA   = levelOf(a);
        val levelB   = levelOf(b);
        val topLevel = Math.min(levelA, levelB);

        val aChildren = childrenAt(a, topLevel);
        val bChildren = childrenAt(b, topLevel);

        val trueChild  = and(table, aChildren[0], bChildren[0]);
        val falseChild = and(table, aChildren[1], bChildren[1]);
        val errorChild = and(table, aChildren[2], bChildren[2]);

        return table.decision(topLevel, trueChild, falseChild, errorChild);
    }

    /**
     * Adds unconditionally matched formula bits into every reachable leaf.
     * <p>
     * Used in OR when one side is definitely satisfied (matched-only terminal).
     * Those formulas are added to every leaf's matched set, and removed from
     * the errored set (a formula satisfied via OR does not need an error vote).
     */
    private static MtbddNode propagateMatch(UniqueTable table, MtbddNode node, BitSet unconditionallyMatched) {
        if (node instanceof Terminal(var matched, var errored)) {
            val newMatched = (BitSet) matched.clone();
            newMatched.or(unconditionallyMatched);
            // Remove these formulas from errored: they're satisfied via the other OR
            // operand
            val newErrored = (BitSet) errored.clone();
            newErrored.andNot(unconditionallyMatched);
            return table.terminal(newMatched, newErrored);
        }
        val d = (MtbddNode.Decision) node;
        return table.decision(d.level(), propagateMatch(table, d.trueChild(), unconditionallyMatched),
                propagateMatch(table, d.falseChild(), unconditionallyMatched),
                propagateMatch(table, d.errorChild(), unconditionallyMatched));
    }

    /**
     * Stamps additional error formula indices onto every reachable leaf.
     * <p>
     * Used when one side of an AND is error-only: the errors from that
     * side must appear at every leaf of the other side's subtree.
     * A matched leaf becomes matched+errored. An empty leaf becomes errored.
     * An already-errored leaf gets the additional error bits.
     */
    private static MtbddNode propagateError(UniqueTable table, MtbddNode node, BitSet extraErrors) {
        // Leaf: add the error bits to whatever was already there
        if (node instanceof Terminal(var matched, var errored)) {
            val newErrored = (BitSet) errored.clone();
            newErrored.or(extraErrors);
            return table.terminal(matched, newErrored);
        }
        // Decision node: recurse into all three children
        val d = (MtbddNode.Decision) node;
        return table.decision(d.level(), propagateError(table, d.trueChild(), extraErrors),
                propagateError(table, d.falseChild(), extraErrors), propagateError(table, d.errorChild(), extraErrors));
    }

    /**
     * Returns the variable level of a node. Terminals are "below"
     * all decision levels, so they return MAX_VALUE.
     */
    private static int levelOf(MtbddNode node) {
        return switch (node) {
        case Terminal ignored     -> Integer.MAX_VALUE;
        case MtbddNode.Decision d -> d.level();
        };
    }

    /**
     * Extracts the three children of a node at the given level.
     * <p>
     * If the node's own level matches, returns its actual children.
     * If the node's level is deeper (or it's a terminal), this variable
     * doesn't affect it - returns [node, node, node] meaning "same
     * result regardless of this predicate's outcome".
     */
    private static MtbddNode[] childrenAt(MtbddNode node, int level) {
        if (node instanceof MtbddNode.Decision(int nodeLevel, MtbddNode trueChild, MtbddNode falseChild, MtbddNode errorChild)
                && nodeLevel == level) {
            return new MtbddNode[] { trueChild, falseChild, errorChild };
        }
        return new MtbddNode[] { node, node, node };
    }

}
