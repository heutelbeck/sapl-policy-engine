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

import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.Map;

import io.sapl.api.model.PureOperator;
import io.sapl.api.model.Value;
import lombok.val;

/**
 * A node in a Semantic Multi-Terminal Decision Diagram (SMTDD).
 * <p>
 * Three node types:
 * <ul>
 * <li>{@link Terminal} - leaf with matched formula indices</li>
 * <li>{@link EqualityBranch} - multi-way branch: evaluates a shared
 * operand once, looks up the result in a HashMap of constant values.
 * Collapses N binary equality predicates into one evaluation + lookup.</li>
 * <li>{@link BinaryDecision} - standard ternary decision node for
 * predicates that cannot be grouped</li>
 * </ul>
 */
public sealed interface SmtddNode {

    int TRUE_CHILD  = 0;
    int FALSE_CHILD = 1;
    int ERROR_CHILD = 2;

    /**
     * Renders this SMTDD as an indented ASCII tree.
     *
     * @return multi-line tree representation
     */
    default String toTree() {
        return toTree(null);
    }

    /**
     * Renders this SMTDD as an indented ASCII tree with predicate labels
     * from the given binary variable order.
     *
     * @param binaryOrder variable order for binary decision labels (null uses level
     * numbers)
     * @return multi-line tree representation
     */
    default String toTree(BinaryVariableOrder binaryOrder) {
        val output  = new StringBuilder();
        val nodeIds = new IdentityHashMap<SmtddNode, Integer>();
        renderNode(this, "", true, "", output, nodeIds, binaryOrder);
        return output.toString();
    }

    private static void renderNode(SmtddNode node, String prefix, boolean isLast, String edgeLabel,
            StringBuilder output, Map<SmtddNode, Integer> nodeIds, BinaryVariableOrder binaryOrder) {
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
        case Terminal(var matched)                                                                     -> {
            if (matched.isEmpty()) {
                output.append(prefix).append(connector).append(label).append("#").append(id).append(" EMPTY\n");
            } else {
                output.append(prefix).append(connector).append(label).append("#").append(id).append(" matched=")
                        .append(matched).append('\n');
            }
        }
        case EqualityBranch(var operand, var branches, var defaultChild, var errorChild, var affected) -> {
            output.append(prefix).append(connector).append(label).append("#").append(id).append(" EQ(hash=")
                    .append(operand.semanticHash()).append(", ").append(branches.size()).append(" branches)\n");
            var branchIndex = 0;
            for (val entry : branches.entrySet()) {
                val branchLabel  = "=" + entry.getKey() + ":";
                val branchIsLast = branchIndex == branches.size() - 1 && defaultChild == errorChild;
                renderNode(entry.getValue(), nextPrefix, branchIsLast && defaultChild == SmtddUniqueTable.EMPTY,
                        branchLabel, output, nodeIds, binaryOrder);
                branchIndex++;
            }
            if (defaultChild != SmtddUniqueTable.EMPTY || defaultChild != errorChild) {
                renderNode(defaultChild, nextPrefix, defaultChild == errorChild, "default:", output, nodeIds,
                        binaryOrder);
            }
            if (defaultChild != errorChild) {
                renderNode(errorChild, nextPrefix, true, "error:", output, nodeIds, binaryOrder);
            }
        }
        case BinaryDecision(int level, var trueChild, var falseChild, var errorChild)                  -> {
            val predicateLabel = binaryOrder != null ? "p" + binaryOrder.predicateAt(level).semanticHash()
                    : "level" + level;
            output.append(prefix).append(connector).append(label).append("#").append(id).append(" ")
                    .append(predicateLabel).append("?\n");
            renderNode(trueChild, nextPrefix, false, "T:", output, nodeIds, binaryOrder);
            renderNode(falseChild, nextPrefix, false, "F:", output, nodeIds, binaryOrder);
            renderNode(errorChild, nextPrefix, true, "E:", output, nodeIds, binaryOrder);
        }
        }
    }

    /**
     * Leaf carrying matched formula indices.
     *
     * @param matched formulas satisfied along this path
     */
    record Terminal(BitSet matched) implements SmtddNode {

        boolean isEmpty() {
            return matched.isEmpty();
        }
    }

    /**
     * Multi-way equality branch. Evaluates the shared operand once and
     * looks up the result in a HashMap to select the child node.
     *
     * @param operand the shared PureOperator to evaluate once
     * @param branches constant value to child node mapping
     * @param defaultChild child when the evaluated value matches no constant
     * @param errorChild child when operand evaluation errors
     * @param affectedFormulas formulas referencing this operand (killed on error)
     */
    record EqualityBranch(
            PureOperator operand,
            Map<Value, SmtddNode> branches,
            SmtddNode defaultChild,
            SmtddNode errorChild,
            BitSet affectedFormulas) implements SmtddNode {}

    /**
     * Standard ternary decision node for non-groupable predicates.
     *
     * @param level predicate index in the binary variable order
     * @param trueChild child when predicate is true
     * @param falseChild child when predicate is false
     * @param errorChild child when predicate errors
     */
    record BinaryDecision(int level, SmtddNode trueChild, SmtddNode falseChild, SmtddNode errorChild)
            implements SmtddNode {}

}
