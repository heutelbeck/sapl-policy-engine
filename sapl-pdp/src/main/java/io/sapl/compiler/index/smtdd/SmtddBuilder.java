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
package io.sapl.compiler.index.smtdd;

import static io.sapl.compiler.index.smtdd.SmtddNode.ERROR_CHILD;
import static io.sapl.compiler.index.smtdd.SmtddNode.FALSE_CHILD;
import static io.sapl.compiler.index.smtdd.SmtddNode.TRUE_CHILD;

import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import io.sapl.api.model.BooleanExpression;
import io.sapl.api.model.BooleanExpression.And;
import io.sapl.api.model.BooleanExpression.Atom;
import io.sapl.api.model.BooleanExpression.Constant;
import io.sapl.api.model.BooleanExpression.Not;
import io.sapl.api.model.BooleanExpression.Or;
import io.sapl.api.model.Value;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.index.IndexSizeLimitExceededException;
import io.sapl.compiler.index.smtdd.SemanticVariableOrder.AnalysisResult;
import io.sapl.compiler.index.smtdd.SmtddNode.BinaryDecision;
import io.sapl.compiler.index.smtdd.SmtddNode.EqualityBranch;
import io.sapl.compiler.index.smtdd.SmtddNode.Terminal;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Builds an SMTDD from the semantic analysis result.
 * <p>
 * Construction strategy:
 * <ol>
 * <li>Build per-formula binary BDDs from remaining (non-equality)
 * predicates</li>
 * <li>For each equality group (top-down by discriminating power):
 * route formulas into branches by their constant value.
 * Formulas not constraining this attribute go into all branches.</li>
 * <li>Within each leaf bucket: merge the binary BDDs of formulas in that
 * bucket</li>
 * </ol>
 */
@Slf4j
@UtilityClass
public class SmtddBuilder {

    private static final String ERROR_EQUALITY_BRANCH_IN_BINARY_MERGE = "EqualityBranch encountered in binary merge context";

    /**
     * Builds the SMTDD from analyzed predicates and boolean expressions.
     *
     * @param analysis the semantic analysis result with equality groups and
     * remaining predicates
     * @param expressions the boolean expressions per formula
     * @param maxIndexNodes maximum allowed nodes (0 = unlimited)
     * @return the root SMTDD node
     * @throws IndexSizeLimitExceededException if the node count exceeds the limit
     */
    public static SmtddNode build(AnalysisResult analysis, List<BooleanExpression> expressions, int maxIndexNodes) {

        val equalityGroups = analysis.equalityGroups();
        val formulaCount   = expressions.size();

        // Binary variable order from remaining predicates
        val binaryOrder = buildBinaryVariableOrder(analysis);
        val table       = new SmtddUniqueTable();

        // Build per-formula binary BDDs
        log.debug("SMTDD: building {} per-formula binary BDDs ({} binary predicates)", formulaCount,
                binaryOrder.size());
        val formulaBdds = new SmtddNode[formulaCount];
        for (var i = 0; i < formulaCount; i++) {
            formulaBdds[i] = buildFormulaBdd(table, binaryOrder, expressions.get(i), i);
        }
        log.debug("SMTDD: binary BDDs built ({} nodes in table)", table.size());

        // Build equality branch structure top-down
        val allFormulas = new BitSet(formulaCount);
        allFormulas.set(0, formulaCount);

        val root = buildEqualityLevels(equalityGroups, 0, allFormulas, formulaBdds, table, maxIndexNodes);

        log.debug("SMTDD: construction complete ({} nodes in table)", table.size());
        return root;
    }

    private static SmtddNode buildEqualityLevels(List<EqualityGroup> groups, int groupIndex, BitSet formulasInBucket,
            SmtddNode[] formulaBdds, SmtddUniqueTable table, int maxIndexNodes) {

        if (groupIndex >= groups.size()) {
            return mergeBucket(formulasInBucket, formulaBdds, table, maxIndexNodes);
        }

        val group     = groups.get(groupIndex);
        val compacted = group.compact(formulasInBucket);

        if (compacted.doesNotSplit(formulasInBucket)) {
            return buildEqualityLevels(groups, groupIndex + 1, formulasInBucket, formulaBdds, table, maxIndexNodes);
        }

        log.debug("SMTDD: equality branch (group {}/{}): {} branches", groupIndex + 1, groups.size(),
                compacted.branchFormulas().size());

        val branches = new HashMap<Value, SmtddNode>();
        for (val entry : compacted.branchFormulas().entrySet()) {
            branches.put(entry.getKey(),
                    buildEqualityLevels(groups, groupIndex + 1, entry.getValue(), formulaBdds, table, maxIndexNodes));
        }

        val defaultChild = buildEqualityLevels(groups, groupIndex + 1, compacted.defaultFormulas(), formulaBdds, table,
                maxIndexNodes);

        return new EqualityBranch(group.getSharedOperand(), branches, defaultChild, defaultChild,
                compacted.affectedFormulas());
    }

    private static SmtddNode mergeBucket(BitSet formulasInBucket, SmtddNode[] formulaBdds, SmtddUniqueTable table,
            int maxIndexNodes) {
        if (formulasInBucket.isEmpty()) {
            return SmtddUniqueTable.EMPTY;
        }

        log.debug("SMTDD: merging bucket with {} formulas", formulasInBucket.cardinality());

        val       cache  = new IdentityHashMap<SmtddNode, IdentityHashMap<SmtddNode, SmtddNode>>();
        SmtddNode merged = null;
        for (var formulaIndex = formulasInBucket.nextSetBit(0); formulaIndex >= 0; formulaIndex = formulasInBucket
                .nextSetBit(formulaIndex + 1)) {
            if (merged == null) {
                merged = formulaBdds[formulaIndex];
            } else {
                merged = merge(table, merged, formulaBdds[formulaIndex], cache);
                if (maxIndexNodes > 0 && table.size() > maxIndexNodes) {
                    throw new IndexSizeLimitExceededException(table.size(), maxIndexNodes);
                }
            }
        }
        return merged;
    }

    private static SmtddNode merge(SmtddUniqueTable table, SmtddNode left, SmtddNode right,
            Map<SmtddNode, IdentityHashMap<SmtddNode, SmtddNode>> cache) {
        if (left == right) {
            return left;
        }
        if (left == SmtddUniqueTable.EMPTY) {
            return right;
        }
        if (right == SmtddUniqueTable.EMPTY) {
            return left;
        }

        val innerMap = cache.get(left);
        if (innerMap != null) {
            val cached = innerMap.get(right);
            if (cached != null) {
                return cached;
            }
        }

        val result = computeMerge(table, left, right, cache);
        cache.computeIfAbsent(left, k -> new IdentityHashMap<>()).put(right, result);
        return result;
    }

    private static SmtddNode computeMerge(SmtddUniqueTable table, SmtddNode left, SmtddNode right,
            Map<SmtddNode, IdentityHashMap<SmtddNode, SmtddNode>> cache) {
        if (left instanceof Terminal(BitSet matchedLeft) && right instanceof Terminal(BitSet matchedRight)) {
            val combined = (BitSet) matchedLeft.clone();
            combined.or(matchedRight);
            return table.terminal(combined);
        }

        val topLevel      = Math.min(levelOf(left), levelOf(right));
        val leftChildren  = childrenAt(left, topLevel);
        val rightChildren = childrenAt(right, topLevel);

        val trueChild  = merge(table, leftChildren[TRUE_CHILD], rightChildren[TRUE_CHILD], cache);
        val falseChild = merge(table, leftChildren[FALSE_CHILD], rightChildren[FALSE_CHILD], cache);
        val errorChild = merge(table, leftChildren[ERROR_CHILD], rightChildren[ERROR_CHILD], cache);

        return table.binaryDecision(topLevel, trueChild, falseChild, errorChild);
    }

    private static SmtddNode buildFormulaBdd(SmtddUniqueTable table, BinaryVariableOrder order,
            BooleanExpression expression, int formulaIndex) {
        val formulaTag = new BitSet();
        formulaTag.set(formulaIndex);
        val matched = table.terminal(formulaTag);
        return buildBdd(table, order, expression, matched);
    }

    private static SmtddNode buildBdd(SmtddUniqueTable table, BinaryVariableOrder order, BooleanExpression expression,
            Terminal matched) {
        return switch (expression) {
        case Constant(var value) -> value ? matched : SmtddUniqueTable.EMPTY;
        case Atom(var predicate) -> {
            val level = order.levelOf(predicate);
            if (level < 0) {
                // Predicate not in binary order (it's an equality predicate handled by
                // EqualityBranch)
                // In the correct branch, this predicate is always true -> matched
                yield matched;
            }
            yield table.binaryDecision(level, matched, SmtddUniqueTable.EMPTY, SmtddUniqueTable.EMPTY);
        }
        case Not(var operand)    -> buildBddNot(table, order, operand, matched);
        case Or(var operands)    -> {
            var result = buildBdd(table, order, operands.getFirst(), matched);
            for (var i = 1; i < operands.size(); i++) {
                result = or(table, result, buildBdd(table, order, operands.get(i), matched));
            }
            yield result;
        }
        case And(var operands)   -> {
            var result = buildBdd(table, order, operands.getFirst(), matched);
            for (var i = 1; i < operands.size(); i++) {
                result = and(table, result, buildBdd(table, order, operands.get(i), matched));
            }
            yield result;
        }
        };
    }

    private static SmtddNode buildBddNot(SmtddUniqueTable table, BinaryVariableOrder order, BooleanExpression operand,
            Terminal matched) {
        return switch (operand) {
        case Constant(var value) -> value ? SmtddUniqueTable.EMPTY : matched;
        case Atom(var predicate) -> {
            val level = order.levelOf(predicate);
            if (level < 0) {
                // Negated equality predicate in correct branch -> always false
                yield SmtddUniqueTable.EMPTY;
            }
            yield table.binaryDecision(level, SmtddUniqueTable.EMPTY, matched, SmtddUniqueTable.EMPTY);
        }
        case Not(var inner)      -> buildBdd(table, order, inner, matched);
        case Or(var operands)    -> {
            var result = buildBddNot(table, order, operands.getFirst(), matched);
            for (var i = 1; i < operands.size(); i++) {
                result = and(table, result, buildBddNot(table, order, operands.get(i), matched));
            }
            yield result;
        }
        case And(var operands)   -> {
            var result = buildBddNot(table, order, operands.getFirst(), matched);
            for (var i = 1; i < operands.size(); i++) {
                result = or(table, result, buildBddNot(table, order, operands.get(i), matched));
            }
            yield result;
        }
        };
    }

    private static SmtddNode or(SmtddUniqueTable table, SmtddNode left, SmtddNode right) {
        if (left == right) {
            return left;
        }
        if (left == SmtddUniqueTable.EMPTY) {
            return right;
        }
        if (right == SmtddUniqueTable.EMPTY) {
            return left;
        }
        if (left instanceof Terminal t && !t.isEmpty()) {
            return left;
        }
        if (right instanceof Terminal t && !t.isEmpty()) {
            return right;
        }

        val topLevel      = Math.min(levelOf(left), levelOf(right));
        val leftChildren  = childrenAt(left, topLevel);
        val rightChildren = childrenAt(right, topLevel);

        return table.binaryDecision(topLevel, or(table, leftChildren[TRUE_CHILD], rightChildren[TRUE_CHILD]),
                or(table, leftChildren[FALSE_CHILD], rightChildren[FALSE_CHILD]),
                or(table, leftChildren[ERROR_CHILD], rightChildren[ERROR_CHILD]));
    }

    private static SmtddNode and(SmtddUniqueTable table, SmtddNode left, SmtddNode right) {
        if (left == right) {
            return left;
        }
        if (left == SmtddUniqueTable.EMPTY || right == SmtddUniqueTable.EMPTY) {
            return SmtddUniqueTable.EMPTY;
        }
        if (left instanceof Terminal t && !t.isEmpty()) {
            return right;
        }
        if (right instanceof Terminal t && !t.isEmpty()) {
            return left;
        }

        val topLevel      = Math.min(levelOf(left), levelOf(right));
        val leftChildren  = childrenAt(left, topLevel);
        val rightChildren = childrenAt(right, topLevel);

        return table.binaryDecision(topLevel, and(table, leftChildren[TRUE_CHILD], rightChildren[TRUE_CHILD]),
                and(table, leftChildren[FALSE_CHILD], rightChildren[FALSE_CHILD]),
                and(table, leftChildren[ERROR_CHILD], rightChildren[ERROR_CHILD]));
    }

    private static BinaryVariableOrder buildBinaryVariableOrder(AnalysisResult analysis) {
        val predicates           = analysis.remainingPredicates();
        val formulasPerPredicate = analysis.formulasPerRemainingPredicate();
        return new BinaryVariableOrder(predicates, formulasPerPredicate);
    }

    private static int levelOf(SmtddNode node) {
        return switch (node) {
        case Terminal ignored        -> Integer.MAX_VALUE;
        case BinaryDecision decision -> decision.level();
        case EqualityBranch ignored  -> throw new SaplCompilerException(ERROR_EQUALITY_BRANCH_IN_BINARY_MERGE);
        };
    }

    private static SmtddNode[] childrenAt(SmtddNode node, int level) {
        if (node instanceof BinaryDecision(int nodeLevel, SmtddNode trueChild, SmtddNode falseChild, SmtddNode errorChild)
                && nodeLevel == level) {
            return new SmtddNode[] { trueChild, falseChild, errorChild };
        }
        return new SmtddNode[] { node, node, node };
    }

}
