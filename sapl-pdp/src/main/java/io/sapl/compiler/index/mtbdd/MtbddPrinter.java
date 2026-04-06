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

import java.util.IdentityHashMap;
import java.util.Map;

import io.sapl.compiler.index.mtbdd.MtbddNode.Decision;
import io.sapl.compiler.index.mtbdd.MtbddNode.Terminal;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Renders an MTBDD as an ASCII tree for terminal inspection.
 * Shared nodes (DAG edges) are shown as references to previously
 * printed node IDs.
 */
@UtilityClass
public class MtbddPrinter {

    /**
     * Renders the MTBDD rooted at the given node as a multi-line
     * ASCII string suitable for terminal output.
     *
     * @param root the root node to print
     * @return the rendered tree string
     */
    public static String print(MtbddNode root) {
        val sb      = new StringBuilder();
        val nodeIds = new IdentityHashMap<MtbddNode, Integer>();
        printNode(root, "", true, "", sb, nodeIds);
        return sb.toString();
    }

    private static void printNode(MtbddNode node, String prefix, boolean isLast, String edgeLabel, StringBuilder sb,
            Map<MtbddNode, Integer> nodeIds) {
        val connector  = isLast ? "`-- " : "|-- ";
        val nextPrefix = prefix + (isLast ? "    " : "|   ");
        val label      = edgeLabel.isEmpty() ? "" : edgeLabel + " ";

        if (nodeIds.containsKey(node)) {
            sb.append(prefix).append(connector).append(label).append("-> #").append(nodeIds.get(node)).append('\n');
            return;
        }

        val id = nodeIds.size();
        nodeIds.put(node, id);

        switch (node) {
        case Terminal(var matched, var errored)                                 -> {
            if (matched.isEmpty() && errored.isEmpty()) {
                sb.append(prefix).append(connector).append(label).append("#").append(id).append(" EMPTY\n");
            } else {
                sb.append(prefix).append(connector).append(label).append("#").append(id);
                if (!matched.isEmpty()) {
                    sb.append(" matched=").append(matched);
                }
                if (!errored.isEmpty()) {
                    sb.append(" errored=").append(errored);
                }
                sb.append('\n');
            }
        }
        case Decision(var level, var trueChild, var falseChild, var errorChild) -> {
            sb.append(prefix).append(connector).append(label).append("#").append(id).append(" p").append(level)
                    .append("?\n");
            printNode(trueChild, nextPrefix, false, "T:", sb, nodeIds);
            printNode(falseChild, nextPrefix, false, "F:", sb, nodeIds);
            printNode(errorChild, nextPrefix, true, "E:", sb, nodeIds);
        }
        }
    }

}
