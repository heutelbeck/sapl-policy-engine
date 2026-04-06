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
import java.util.HashMap;
import java.util.Map;

import io.sapl.compiler.index.mtbdd.MtbddNode.Decision;
import io.sapl.compiler.index.mtbdd.MtbddNode.Terminal;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Merges multiple per-formula MTBDDs into a single combined diagram.
 * <p>
 * Cross-formula merge uses union semantics: each formula's result is
 * independent. A terminal in the merged diagram carries BitSets with
 * bits from multiple formulas.
 * <p>
 * The merge uses a computed table (cache) keyed by node identity pairs
 * to avoid recomputing the same (nodeA, nodeB) combination. This is
 * the Apply algorithm that makes MTBDD merging polynomial in the
 * diagram sizes rather than exponential in the number of variables.
 */
@UtilityClass
class MtbddMerger {

    /**
     * Merges two MTBDDs with union semantics.
     * <p>
     * For each path through the merged diagram, the terminal contains
     * the union of matched formulas from both inputs and the union of
     * errored formulas from both inputs.
     * <p>
     * No absorption: formulas in different inputs are independent.
     * Formula 0 being matched does not suppress formula 1's error edges.
     *
     * @param table the shared unique table
     * @param left first MTBDD
     * @param right second MTBDD
     * @return merged MTBDD
     */
    static MtbddNode merge(UniqueTable table, MtbddNode left, MtbddNode right) {
        val cache = new HashMap<Long, MtbddNode>();
        return mergeRecursive(table, left, right, cache);
    }

    private static MtbddNode mergeRecursive(UniqueTable table, MtbddNode left, MtbddNode right,
            Map<Long, MtbddNode> cache) {
        if (left == right) {
            return left;
        }
        if (left == UniqueTable.EMPTY) {
            return right;
        }
        if (right == UniqueTable.EMPTY) {
            return left;
        }

        // Check cache: have we merged this exact pair before?
        val cacheKey = cacheKey(left, right);
        val cached   = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        val result = computeMerge(table, left, right, cache);
        cache.put(cacheKey, result);
        return result;
    }

    private static MtbddNode computeMerge(UniqueTable table, MtbddNode left, MtbddNode right,
            Map<Long, MtbddNode> cache) {
        // Both terminals: union of matched formula sets
        if (left instanceof Terminal(BitSet matchedLeft) && right instanceof Terminal(BitSet matchedRight)) {
            val combined = (BitSet) matchedLeft.clone();
            combined.or(matchedRight);
            return table.terminal(combined);
        }

        // At least one decision node - recurse on the topmost variable
        val topLevel      = Math.min(levelOf(left), levelOf(right));
        val leftChildren  = childrenAt(left, topLevel);
        val rightChildren = childrenAt(right, topLevel);

        val trueChild  = mergeRecursive(table, leftChildren[0], rightChildren[0], cache);
        val falseChild = mergeRecursive(table, leftChildren[1], rightChildren[1], cache);
        val errorChild = mergeRecursive(table, leftChildren[2], rightChildren[2], cache);

        return table.decision(topLevel, trueChild, falseChild, errorChild);
    }

    /**
     * Cache key from identity hash codes of both nodes. Since nodes are
     * interned, identity hash is stable and unique per structural node.
     * Packing two 32-bit hashes into one long avoids a Pair object allocation.
     */
    private static long cacheKey(MtbddNode left, MtbddNode right) {
        return ((long) System.identityHashCode(left) << 32) | (System.identityHashCode(right) & 0xFFFFFFFFL);
    }

    private static int levelOf(MtbddNode node) {
        return switch (node) {
        case Terminal ignored -> Integer.MAX_VALUE;
        case Decision d       -> d.level();
        };
    }

    private static MtbddNode[] childrenAt(MtbddNode node, int level) {
        if (node instanceof Decision(int nodeLevel, MtbddNode trueChild, MtbddNode falseChild, MtbddNode errorChild)
                && nodeLevel == level) {
            return new MtbddNode[] { trueChild, falseChild, errorChild };
        }
        return new MtbddNode[] { node, node, node };
    }

}
