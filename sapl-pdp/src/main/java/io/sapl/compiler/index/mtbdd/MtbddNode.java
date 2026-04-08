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
import java.util.IdentityHashMap;
import java.util.Map;

import lombok.val;

/**
 * A node in a Multi-Terminal Binary Decision Diagram (MTBDD) extended
 * with ternary edges (true/false/error) for policy indexing.
 * <p>
 * Two kinds of nodes:
 * <ul>
 * <li>{@link Terminal} - a leaf carrying a set of matched formula indices</li>
 * <li>{@link Decision} - an internal node that branches on a predicate,
 * identified by its level in the fixed variable order</li>
 * </ul>
 * Nodes are interned by a {@link UniqueTable}: structurally identical
 * nodes are always the same object, enabling identity-based caching
 * in the Apply algorithm.
 */
sealed interface MtbddNode {

    int TRUE_CHILD  = 0;
    int FALSE_CHILD = 1;
    int ERROR_CHILD = 2;

    /**
     * Renders this MTBDD as an indented ASCII tree with level numbers.
     * Shared nodes (DAG edges) are shown as back-references.
     *
     * @return multi-line tree representation
     */
    default String toTree() {
        return toTree(null);
    }

    /**
     * Renders this MTBDD as an indented ASCII tree with predicate labels
     * from the given variable order.
     *
     * @param order variable order for predicate labels (null uses level numbers)
     * @return multi-line tree representation
     */
    default String toTree(VariableOrder order) {
        val output  = new StringBuilder();
        val nodeIds = new IdentityHashMap<MtbddNode, Integer>();
        renderNode(this, "", true, "", output, nodeIds, order);
        return output.toString();
    }

    private static void renderNode(MtbddNode node, String prefix, boolean isLast, String edgeLabel,
            StringBuilder output, Map<MtbddNode, Integer> nodeIds, VariableOrder order) {
        val connector  = isLast ? "`-- " : "|-- ";
        val nextPrefix = prefix + (isLast ? "    " : "|   ");
        val label      = edgeLabel.isEmpty() ? "" : edgeLabel + " ";

        if (nodeIds.containsKey(node)) {
            output.append(prefix).append(connector).append(label).append("-> #").append(nodeIds.get(node)).append('\n');
            return;
        }

        val id = nodeIds.size();
        nodeIds.put(node, id);

        switch (node) {
        case Terminal(var matched)                                              -> {
            if (matched.isEmpty()) {
                output.append(prefix).append(connector).append(label).append("#").append(id).append(" EMPTY\n");
            } else {
                output.append(prefix).append(connector).append(label).append("#").append(id).append(" matched=")
                        .append(matched).append('\n');
            }
        }
        case Decision(var level, var trueChild, var falseChild, var errorChild) -> {
            val predicateLabel = order != null ? "p" + order.predicateAt(level).semanticHash() : "level" + level;
            output.append(prefix).append(connector).append(label).append("#").append(id).append(" ")
                    .append(predicateLabel).append("?\n");
            renderNode(trueChild, nextPrefix, false, "T:", output, nodeIds, order);
            renderNode(falseChild, nextPrefix, false, "F:", output, nodeIds, order);
            renderNode(errorChild, nextPrefix, true, "E:", output, nodeIds, order);
        }
        }
    }

    /**
     * A leaf node carrying the set of matched formula indices along
     * this path. Error handling is external - the evaluator tracks
     * errored formulas via {@link VariableOrder#erroredFormulas(int)}.
     *
     * @param matched formulas whose applicability is satisfied
     */
    record Terminal(BitSet matched) implements MtbddNode {

        /**
         * @return true if no formulas are matched
         */
        boolean isEmpty() {
            return matched.isEmpty();
        }
    }

    /**
     * An internal decision node that branches on the predicate at the
     * given level in the variable order.
     * <p>
     * Invariant: {@code level} is strictly less than the level of any
     * descendant decision node. This is enforced by the unique table
     * and the Apply algorithm.
     *
     * @param level the predicate index in the variable order (0 = root-most)
     * @param trueChild child when the predicate evaluates to true
     * @param falseChild child when the predicate evaluates to false
     * @param errorChild child when the predicate evaluation errors
     */
    record Decision(int level, MtbddNode trueChild, MtbddNode falseChild, MtbddNode errorChild) implements MtbddNode {}

}
