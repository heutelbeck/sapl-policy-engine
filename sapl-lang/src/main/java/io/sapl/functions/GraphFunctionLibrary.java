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
package io.sapl.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Array;
import io.sapl.api.validation.JsonObject;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.function.Consumer;

/**
 * Graph utilities for reachability and shortest paths.
 */
@UtilityClass
@FunctionLibrary(name = GraphFunctionLibrary.NAME, description = GraphFunctionLibrary.DESCRIPTION, libraryDocumentation = GraphFunctionLibrary.DOCUMENTATION)
public class GraphFunctionLibrary {

    public static final String NAME          = "graph";
    public static final String DESCRIPTION   = "Graph functions: reachability and shortest paths.";
    public static final String DOCUMENTATION = """
            # Graph Function Library (name: graph)

            This library provides functions for working with graphs represented as JSON objects.
            Use these functions when authorization decisions depend on reachability or path analysis
            in hierarchical structures such as role hierarchies, organizational charts, or resource trees.

            ## Design rationale

            Graphs are plain JSON objects, so policies can pass them without adapters. Missing nodes
            are treated like leaves. Unknown roots still produce a result. Traversal uses breadth-first
            search with a visited set, so cycles do not cause loops.

            ## Graph object format

            A graph is a JSON object where each key is a node identifier and each value is an array
            of neighbor identifiers. The neighbors represent the outgoing edges of the node.

            Example using hierarchical role inheritance (each role grants permissions of subordinate roles):

            ```json
            {
              "system-admin" : [ "db-admin", "security-admin", "app-admin" ],
              "db-admin" : [ "db-operator", "backup-operator" ],
              "security-admin" : [ "security-analyst", "audit-viewer" ],
              "app-admin" : [ "app-operator", "app-viewer" ],
              "db-operator" : [ "db-viewer" ],
              "backup-operator" : [ "backup-viewer" ],
              "security-analyst" : [ "log-viewer" ],
              "audit-viewer" : [],
              "app-operator" : [ "app-viewer" ],
              "app-viewer" : [],
              "db-viewer" : [],
              "backup-viewer" : [],
              "log-viewer" : []
            }
            ```

            ## Notes on encoding

            Edges are directed: neighbors are outgoing edges.
            A node is a leaf if it is missing or mapped to an empty array.
            Use strings for node ids to avoid confusion.
            Only arrays are treated as adjacency lists; any other value is ignored for expansion.
            Initial roots can be a single id or an array of ids.

            ## Example (reachable)

            Check which roles are effectively granted when a user has the system-admin role:

            ```sapl
            policy "evaluate-effective-permissions"
            permit
            where
              var effectiveRoles = graph.reachable(roleHierarchy, subject.assignedRoles);
              "db-viewer" in effectiveRoles;
            ```

            ## Example (reachable_paths)

            Audit the delegation chain from a high-privilege role to a specific permission:

            ```sapl
            policy "audit-permission-delegation"
            permit
            where
              var delegationPaths = graph.reachable_paths(roleHierarchy, ["system-admin"]);
              ["system-admin","db-admin","db-operator","db-viewer"] in delegationPaths;
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
            ```graph.reachable(OBJECT graph, STRING|ARRAY initial)```: Computes the reachable nodes in a
            directed graph when starting at the given list of nodes or a single node identifier.

            Performs breadth-first search to discover all nodes that can be reached by following
            directed edges in the graph.

            ## Parameters

            - graph: JSON object where each key is a node identifier and each value is an array of
              neighbor identifiers (see library documentation for structure)
            - initial: a single node identifier or an array of identifiers

            ## Returns

            - Array of unique node identifiers in the order they were discovered by breadth-first traversal

            ## Behavior

            - Missing nodes are treated as leaves and do not expand further
            - Unknown roots are returned as reachable; they yield single-node results if no adjacency exists
            - Non-array adjacency values are ignored
            - Cycles are handled via visited set
            - Time complexity is O(V + E) with V nodes and E edges

            ## Example

            Using the roleHierarchy graph from library documentation, determine all roles a user
            effectively has through inheritance:

            ```sapl
            policy "require-elevated-access"
            permit
            where
              var effectiveRoles = graph.reachable(roleHierarchy, subject.assignedRoles);
              "security-analyst" in effectiveRoles;
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
            ```graph.reachable_paths(OBJECT graph, STRING|ARRAY initial)```: Builds one shortest path per
            discovered node by performing breadth-first traversal from the given roots.

            Each root contributes a single-element path containing just the root. Each reachable node
            yields exactly one path (the first encountered by BFS).

            ## Parameters

            - graph: JSON object mapping node identifiers to arrays of neighbor identifiers
            - initial: a single node identifier or an array of identifiers

            ## Returns

            - Array of paths where each path is an array of node identifiers from a root to a reachable node

            ## Behavior

            - Missing nodes are treated as leaves (no expansion)
            - Cycles are handled via visited set; the first discovered path is kept
            - Unknown roots produce a single-node path

            ## Example

            Using the roleHierarchy graph from library documentation, verify the delegation chain
            for audit purposes:

            ```sapl
            policy "audit-role-delegation-path"
            permit
            where
              var delegationPaths = graph.reachable_paths(roleHierarchy, subject.primaryRole);
              ["db-admin","db-operator","db-viewer"] in delegationPaths;
            ```
            """, schema = RETURNS_ARRAY)
    public static Val reachablePaths(@JsonObject Val graph, @Array Val initial) {
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
        if (neighborsNode == null || !neighborsNode.isArray()) {
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
        val pathSteps = new ArrayList<String>();
        var current   = targetNodeId;
        while (current != null) {
            pathSteps.add(current);
            current = predecessorByNodeId.get(current);
        }

        val path = Val.JSON.arrayNode();
        for (val step : pathSteps.reversed()) {
            path.add(step);
        }
        out.add(path);
    }

    /**
     * Maintains BFS traversal state with discovery queue and visited tracking.
     */
    private record TraversalState(ArrayDeque<String> queue, LinkedHashSet<String> visited) {}
}
