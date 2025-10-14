/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
/*
 * SPDX-License-Identifier: Apache-2.0
 */

package io.sapl.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Array;
import io.sapl.api.validation.JsonObject;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.*;
import java.util.function.Consumer;

/**
 * Graph utilities for reachability, shortest paths, and graph walking.
 */
@UtilityClass
@FunctionLibrary(name = GraphFunctionLibrary.NAME, description = GraphFunctionLibrary.DESCRIPTION, libraryDocumentation = GraphFunctionLibrary.DOCUMENTATION)
public class GraphFunctionLibrary {

    public static final String NAME          = "graph";
    public static final String DESCRIPTION   = "Graph functions: reachability, shortest paths, and graph walking.";
    public static final String DOCUMENTATION = """
            # Graph Function Library (name: graph)

            This library contains small functions for working with graphs and
            with nested JSON values. The goal is to keep policy code simple and
            readable when a decision depends on reachability or on the shape of
            a JSON document.

            ## Design rationale

            Graphs are plain JSON objects, so policies can pass them without
            adapters. Missing nodes are treated like leaves. Unknown roots
            still produce a result. Traversal uses breadth first search with
            a visited set, so cycles do not cause loops.

            ## Graph object format

            A graph is a JSON object. Each key is a node id. Each value is an
            array of neighbor ids. The neighbors are the outgoing edges of the
            node.

            ```json
                {
                  "cso": ["security-architect", "risk-manager", "compliance-officer"],
                  "security-architect": ["secops-lead", "platform-admin"],
                  "secops-lead": ["security-analyst"],
                  "platform-admin": ["site-reliability-engineer"],
                  "risk-manager": ["risk-analyst"],
                  "compliance-officer": ["auditor-internal"],

                  "head-of-payments": ["payments-lead"],
                  "payments-lead": ["payments-engineer"]
                }
            ```

            ## Notes on encoding

            Edges are directed: neighbors are outgoing edges.
            A node is a leaf if it is missing or mapped to [].
            Use strings for node ids if possible to avoid confusion.
            Only arrays are treated as adjacency lists; any other value is
            ignored for expansion.
            Initial roots can be a single id or an array of ids.

            ## Examples (policy snippets)

            SAPL example using reachable:

            ```sapl
                policy "org-reachable-from-cso"
                permit
                where
                  var nodes = graph.reachable(rolesHierarchy, subject.roles);
                  "security-analyst" in nodes
                  & !("payments-engineer" in nodes);
            ```

            SAPL example using reachable_paths:

            ```sapl
                policy "org-paths-from-security-architect"
                permit
                where
                  var paths = graph.reachable_paths(org_roles_graph, ["security-architect"]);
                  ["security-architect","platform-admin","site-reliability-engineer"] in paths;
            ```
            SAPL example using walk:

            ```sapl
                policy "walk-checks"
                permit
                where
                  var pairs = graph.walk({ "a": { "b": 1 }, "c": [2,3] });
                  any e in pairs : e[0] == ["a","b"] & e[1] == 1;
            ```
            """;

    private static final String RETURNS_ARRAY = """
            { "type": "array" }
            """;

    /**
     * Computes the set of nodes reachable from the initial roots in a directed
     * graph.
     *
     * @param graph JSON object mapping node identifiers to arrays of neighbor
     * identifiers
     * @param initial a single node identifier or an array of identifiers to seed
     * the search
     * @return array of unique node identifiers in discovery order
     */
    @Function(name = "reachable", docs = """
            ```graph.reachable(OBJECT graph,STRING|ARRAY initial)```: Computes the reachable nodes in a directed
            graph when starting at the given list of nodes or a single node identifier.

            Computes the set of nodes that can be discovered by traversing directed edges
            in `graph` starting from `initial`. The graph is represented as a JSON object
            that maps node identifiers to arrays of neighbor identifiers.

            ## Parameters

            - graph: JSON object where each key is a node identifier and each value is an array
              of neighbor identifiers. Example structure:

              ```json
              {
                "org_roles_graph": {
                  "cso": ["security-architect", "risk-manager", "compliance-officer"],
                  "security-architect": ["secops-lead", "platform-admin"],
                  "secops-lead": ["security-analyst"],
                  "platform-admin": ["site-reliability-engineer"],
                  "risk-manager": ["risk-analyst"],
                  "compliance-officer": ["auditor-internal"],

                  "head-of-payments": ["payments-lead"],
                  "payments-lead": ["payments-engineer"],

                  "security-analyst": [],
                  "site-reliability-engineer": [],
                  "risk-analyst": [],
                  "auditor-internal": [],
                  "payments-engineer": []
                }
              }
              ```

            - initial: a single node identifier (string or number) or an array of identifiers.

            ## Returns

            - Array of unique node identifiers in the order they were discovered by a breadth-first traversal.

            ## Behavior and Robustness

            - Missing nodes (no key present in `graph`) are treated as leaves and do not expand further.
            - Unknown roots are returned as reachable; they simply yield single-node results if no adjacency exists.
            - Non-array adjacency values are ignored safely.
            - Time complexity is `O(V + E)` with `V` nodes and `E` edges.

            ## Example (policy)

            Given variables containing `org_roles_graph`:

            ```sapl
            policy "org-reachable-from-cso"
            permit
            where
              var r = graph.reachable(org_roles_graph, ["cso"]);
              ("security-analyst" in r) && ("site-reliability-engineer" in r) && !("payments-engineer" in r);
            ```
            """, schema = RETURNS_ARRAY)
    public static Val reachable(@JsonObject Val graph, @Array Val initial) {
        val graphObject    = graph.getObjectNode();
        val traversalState = newTraversalState(initial);

        while (!traversalState.queue().isEmpty()) {
            val currentNodeId = traversalState.queue().removeFirst();
            forEachNeighbor(graphObject, currentNodeId, neighborId -> {
                if (traversalState.visited().add(neighborId)) {
                    traversalState.queue().addLast(neighborId);
                }
            });
        }

        val result = Val.JSON.arrayNode();
        for (val nodeId : traversalState.visited()) {
            result.add(nodeId);
        }
        return Val.of(result);
    }

    /**
     * Computes one shortest path per reachable node starting from the given roots.
     * The path for a root is the single-element array containing just the root.
     *
     * @param graph JSON object mapping node identifiers to arrays of neighbor
     * identifiers
     * @param initial a single node identifier or an array of identifiers to seed
     * the search
     * @return array of paths; each path is an array of node identifiers from a root
     * to a node
     */
    @Function(name = "reachable_paths", docs = """
            ```graph.reachable_paths(graph, initial)```: Builds one shortest path per discovered node by performing
            a breadth-first traversal from the given roots. A root contributes the path `[root]`.
            Each reachable node yields exactly one path (the first encountered by BFS).

            ## Parameters

            - graph: JSON object mapping node identifiers to arrays of neighbor identifiers.
            - initial: a single node identifier or an array of identifiers.

            ## Returns

            - Array of paths. Each path is an array of node identifiers from a root to a reachable node.

            ## Behavior and Robustness

            - Missing nodes are treated as leaves (no expansion).
            - Cycles are handled via a visited set; the first discovered path is kept.
            - Unknown roots produce a single-node path.

            ## Example (policy)

            ```sapl
            policy "org-paths-from-security-architect"
            permit
            where
              var paths = graph.reachable_paths(org_roles_graph, ["security-architect"]);
              ["security-architect","secops-lead","security-analyst"] in paths
              &
              ["security-architect","platform-admin","site-reliability-engineer"] in paths;
            ```
            """, schema = RETURNS_ARRAY)
    public static Val reachablePaths(@JsonObject Val graph, Val initial) {
        val graphObject         = graph.getObjectNode();
        val traversalState      = newTraversalState(initial);
        val predecessorByNodeId = new LinkedHashMap<String, String>();

        while (!traversalState.queue().isEmpty()) {
            val currentNodeId = traversalState.queue().removeFirst();
            forEachNeighbor(graphObject, currentNodeId, neighborId -> {
                if (traversalState.visited().add(neighborId)) {
                    predecessorByNodeId.put(neighborId, currentNodeId);
                    traversalState.queue().addLast(neighborId);
                }
            });
        }

        val paths = Val.JSON.arrayNode();
        for (val nodeId : traversalState.visited()) {
            addPath(paths, nodeId, predecessorByNodeId);
        }
        return Val.of(paths);
    }

    /**
     * Generates `[path, value]` pairs for all leaf values in the input JSON.
     * The path consists of object keys and array indices.
     *
     * @param input any JSON value
     * @return array of pairs where each pair is `[path, value]`
     */
    @Function(name = "walk", docs = """
            ```graph.walk(OBJECT x)```

            Recursively enumerates all leaf values contained in `x`, returning pairs
            `[path, value]` where `path` is an array of keys and indices leading to the leaf.

            ## Parameters

            - graph: any JSON value (object, array, or primitive).

            ## Returns

            - Array of pairs. Each pair is represented as a two-element array: `[path, value]`.

            ## Example (policy)

            ```sapl
            policy "walk-checks"
            permit
            where
              var pairs = graph.walk({ "a": { "b": 1 }, "c": [2, 3] });
              any e in pairs : e[0] == ["a","b"] & e[1] == 1;
              any e in pairs : e[0] == ["c",0]  & e[1] == 2;
              any e in pairs : e[0] == ["c",1]  & e[1] == 3;
            ```
            """, schema = RETURNS_ARRAY)
    public static Val walk(Val input) {
        val outputPairs = Val.JSON.arrayNode();
        appendWalk(outputPairs, JsonNodeFactory.instance.arrayNode(), input.get());
        return Val.of(outputPairs);
    }

    private static TraversalState newTraversalState(Val initial) {
        val visited = new LinkedHashSet<String>();
        val queue   = new ArrayDeque<String>();

        if (initial.isArray()) {
            val iterator = initial.getArrayNode().elements();
            while (iterator.hasNext()) {
                val rootId = nodeIdOf(iterator.next());
                if (visited.add(rootId)) {
                    queue.addLast(rootId);
                }
            }
            return new TraversalState(queue, visited);
        }

        if (!initial.isUndefined() && !initial.isNull()) {
            val rootId = nodeIdOf(initial.get());
            if (visited.add(rootId)) {
                queue.addLast(rootId);
            }
        }
        return new TraversalState(queue, visited);
    }

    private static void forEachNeighbor(ObjectNode graphObject, String nodeId, Consumer<String> neighborConsumer) {
        val neighborsNode = graphObject.get(nodeId);
        if (Objects.isNull(neighborsNode) || !neighborsNode.isArray()) {
            return;
        }
        val iterator = neighborsNode.elements();
        while (iterator.hasNext()) {
            neighborConsumer.accept(nodeIdOf(iterator.next()));
        }
    }

    private static String nodeIdOf(JsonNode idNode) {
        if (idNode == null || idNode.isMissingNode() || idNode.isNull()) {
            return "null";
        }
        if (idNode.isTextual() || idNode.isNumber() || idNode.isBoolean()) {
            return idNode.asText();
        }
        return idNode.toString();
    }

    private static void addPath(ArrayNode out, String targetNodeId, LinkedHashMap<String, String> predecessorByNodeId) {
        val reversed = new ArrayList<String>();
        var current  = targetNodeId;
        while (current != null) {
            reversed.addFirst(current);
            current = predecessorByNodeId.get(current);
        }
        val path = Val.JSON.arrayNode();
        for (val step : reversed) {
            path.add(step);
        }
        out.add(path);
    }

    private static void appendWalk(ArrayNode out, ArrayNode path, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            val pair = Val.JSON.arrayNode();
            pair.add(path.deepCopy());
            pair.addNull();
            out.add(pair);
            return;
        }
        if (node.isObject()) {
            val properties = node.properties();
            for (val property : properties) {
                val next = path.deepCopy();
                next.add(property.getKey());
                appendWalk(out, next, property.getValue());
            }
            return;
        }
        if (node.isArray()) {
            val iterator = node.elements();
            var index    = 0;
            while (iterator.hasNext()) {
                val next = path.deepCopy();
                next.add(index++);
                appendWalk(out, next, iterator.next());
            }
            return;
        }
        val pair = Val.JSON.arrayNode();
        pair.add(path.deepCopy());
        pair.add(node.deepCopy());
        out.add(pair);
    }

    private record TraversalState(ArrayDeque<String> queue, LinkedHashSet<String> visited) {}
}
