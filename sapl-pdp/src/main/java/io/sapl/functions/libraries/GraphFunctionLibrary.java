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
package io.sapl.functions.libraries;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Graph utilities for reachability, transitive closure, and shortest paths.
 * <p>
 * Supports two graph formats:
 * <ul>
 * <li><b>Adjacency list:</b> {@code { nodeId: [neighborIds...] }}</li>
 * <li><b>Entity graph:</b> {@code { nodeId: { edgeKey: [neighborIds...],
 * "attributes": { ... } } }}</li>
 * </ul>
 * Transitive closure uses Tarjan's SCC decomposition (1972) + memoized DAG
 * closure. O(V + E + S) where S = total output size. Functions fold at compile
 * time when the input is a PDP variable.
 */
@UtilityClass
@FunctionLibrary(name = GraphFunctionLibrary.NAME, description = GraphFunctionLibrary.DESCRIPTION, libraryDocumentation = GraphFunctionLibrary.DOCUMENTATION)
public class GraphFunctionLibrary {

    public static final String NAME          = "graph";
    public static final String DESCRIPTION   = "Graph functions: reachability, transitive closure, and shortest paths.";
    public static final String DOCUMENTATION = """
            # Graph Function Library (name: graph)

            Functions for working with graphs represented as JSON objects.

            ## Graph formats

            **Adjacency list:** ``{ "admin": ["manager", "auditor"], "manager": ["viewer"] }``

            **Entity graph:** ``{ "admin": { "children": ["manager"], "attributes": { "permissions": ["approve"] } } }``

            ## Compile-time optimization

            When the graph is a PDP variable, transitive closure functions fold at compile time.
            """;

    private static final String NODE_ID_NULL          = "null";
    private static final String SCHEMA_RETURNS_ARRAY  = """
            { "type": "array" }
            """;
    private static final String SCHEMA_RETURNS_OBJECT = """
            { "type": "object" }
            """;

    /**
     * Single-source BFS reachability. O(V + E).
     *
     * @param graph adjacency list
     * @param initial root node(s) for BFS
     * @return array of reachable node IDs in discovery order
     */
    @Function(name = "reachable", docs = """
            ```graph.reachable(OBJECT graph, STRING|ARRAY initial)```: Single-source BFS reachability.
            Returns array of reachable node IDs in BFS discovery order. O(V + E).
            """, schema = SCHEMA_RETURNS_ARRAY)
    public static Value reachable(ObjectValue graph, Value initial) {
        return toArrayValue(bfs(graph, initial, null, true));
    }

    /**
     * All-pairs transitive closure (adjacency list). O(V + E + S).
     *
     * @param graph adjacency list
     * @return closure where each node maps to array of reachable nodes
     */
    @Function(name = "transitiveClosure", docs = """
            ```graph.transitiveClosure(OBJECT graph)```: All-pairs transitive closure via Tarjan's SCC
            + memoized DAG closure. O(V + E + S).

            ```sapl
            var closed = graph.transitiveClosure(rolesHierarchy);
            "viewer" in closed[(subject.role)];
            ```
            """, schema = SCHEMA_RETURNS_OBJECT)
    public static Value transitiveClosure(ObjectValue graph) {
        return buildClosureArray(graph, null);
    }

    /**
     * All-pairs transitive closure (entity graph). O(V + E + S).
     *
     * @param graph entity graph
     * @param edgeKey field name for neighbor references
     * @return closure where each node maps to array of reachable nodes
     */
    @Function(name = "transitiveClosure", docs = """
            ```graph.transitiveClosure(OBJECT entityGraph, STRING edgeKey)```: All-pairs transitive
            closure of entity graph. Tarjan's SCC + memoized DAG closure. O(V + E + S).

            ```sapl
            var closed = graph.transitiveClosure(roleEntities, "children");
            "viewer" in closed[(subject.role)];
            ```
            """, schema = SCHEMA_RETURNS_OBJECT)
    public static Value transitiveClosure(ObjectValue graph, TextValue edgeKey) {
        return buildClosureArray(graph, edgeKey.value());
    }

    /**
     * All-pairs transitive closure with O(1) membership lookup. O(V + E + S).
     *
     * @param graph adjacency list
     * @return closure where each node maps to object with reachable nodes as keys
     */
    @Function(name = "transitiveClosureSet", docs = """
            ```graph.transitiveClosureSet(OBJECT graph)```: Transitive closure with O(1) key lookup.
            Tarjan's SCC + memoized DAG closure. O(V + E + S).

            ```sapl
            var closed = graph.transitiveClosureSet(rolesHierarchy);
            closed[(subject.role)]["viewer"] != undefined;
            ```
            """, schema = SCHEMA_RETURNS_OBJECT)
    public static Value transitiveClosureSet(ObjectValue graph) {
        return buildClosureSet(graph, null);
    }

    /**
     * All-pairs transitive closure with O(1) membership lookup (entity graph).
     * O(V + E + S).
     *
     * @param graph entity graph
     * @param edgeKey field name for neighbor references
     * @return closure where each node maps to object with reachable nodes as keys
     */
    @Function(name = "transitiveClosureSet", docs = """
            ```graph.transitiveClosureSet(OBJECT entityGraph, STRING edgeKey)```: Transitive closure of
            entity graph with O(1) key lookup. Tarjan's SCC + memoized DAG closure. O(V + E + S).

            ```sapl
            var closed = graph.transitiveClosureSet(roleEntities, "children");
            closed[(subject.role)]["viewer"] != undefined;
            ```
            """, schema = SCHEMA_RETURNS_OBJECT)
    public static Value transitiveClosureSet(ObjectValue graph, TextValue edgeKey) {
        return buildClosureSet(graph, edgeKey.value());
    }

    /**
     * All-pairs transitive closure with attribute projection. O(V + E + S).
     *
     * @param graph entity graph
     * @param edgeKey field name for neighbor references
     * @param attrKey field name within attributes to collect
     * @return closure where each node maps to collected attribute values
     */
    @Function(name = "transitiveClosureProjection", docs = """
            ```graph.transitiveClosureProjection(OBJECT entityGraph, STRING edgeKey, STRING attrKey)```:
            Walks edges via Tarjan's SCC + memoized DAG closure, collects a named attribute from all
            reachable nodes. Array attributes are flattened. O(V + E + S).

            ```sapl
            var perms = graph.transitiveClosureProjection(roleEntities, "children", "permissions");
            { "action": action, "type": resource.type } in perms[(subject.role)];
            ```
            """, schema = SCHEMA_RETURNS_OBJECT)
    public static Value transitiveClosureProjection(ObjectValue graph, TextValue edgeKey, TextValue attrKey) {
        val closure = computeAllPairsClosure(graph, edgeKey.value());
        val attr    = attrKey.value();
        val result  = ObjectValue.builder();
        for (val entry : closure.entrySet()) {
            result.put(entry.getKey(), collectAttribute(graph, entry.getValue(), attr));
        }
        return result.build();
    }

    /**
     * Single-source shortest paths via BFS. O(V + E).
     *
     * @param graph adjacency list
     * @param initial root node(s) for BFS
     * @return array of shortest paths from roots to all reachable nodes
     */
    @Function(name = "reachable_paths", docs = """
            ```graph.reachable_paths(OBJECT graph, STRING|ARRAY initial)```: Single-source shortest
            paths via BFS. O(V + E). Returns array of paths (each an array of node IDs).
            """, schema = SCHEMA_RETURNS_ARRAY)
    public static Value reachablePaths(ObjectValue graph, Value initial) {
        val visited      = new LinkedHashSet<String>();
        val queue        = new ArrayDeque<String>();
        val predecessors = new LinkedHashMap<String, String>();
        seedQueue(initial, visited, queue);

        while (!queue.isEmpty()) {
            val current = queue.removeFirst();
            forEachNeighbor(graph, current, null, neighbor -> {
                if (visited.add(neighbor)) {
                    predecessors.put(neighbor, current);
                    queue.addLast(neighbor);
                }
            });
        }

        val pathsBuilder = ArrayValue.builder();
        for (val nodeId : visited) {
            val steps = new ArrayList<String>();
            for (var c = nodeId; c != null; c = predecessors.get(c)) {
                steps.add(c);
            }
            val pathBuilder = ArrayValue.builder();
            for (val step : steps.reversed()) {
                pathBuilder.add(Value.of(step));
            }
            pathsBuilder.add(pathBuilder.build());
        }
        return pathsBuilder.build();
    }

    /**
     * Tarjan's SCC (1972) + condensation to DAG + bottom-up memoized closure.
     * O(V + E + S) where S = total output size.
     */
    private static Map<String, Set<String>> computeAllPairsClosure(ObjectValue graph, String edgeKey) {
        val nodeIds    = new ArrayList<>(graph.keySet());
        val adjacency  = buildAdjacency(graph, edgeKey, nodeIds);
        val tarjan     = new TarjanState();
        val sccs       = tarjan.findSccs(nodeIds, adjacency);
        val nodeToScc  = mapNodesToScc(sccs);
        val dagAdj     = buildDagAdjacency(sccs, adjacency, nodeToScc);
        val dagClosure = memoizedDagClosure(sccs, dagAdj);

        val result = new HashMap<String, Set<String>>();
        for (var i = 0; i < sccs.size(); i++) {
            val reachable = dagClosure.get(i);
            for (val nodeId : sccs.get(i)) {
                result.put(nodeId, reachable);
            }
        }
        return result;
    }

    private static Map<String, Set<String>> buildAdjacency(ObjectValue graph, String edgeKey, List<String> nodeIds) {
        val adjacency = new HashMap<String, Set<String>>();
        for (val nodeId : nodeIds) {
            val neighbors = new HashSet<String>();
            forEachNeighbor(graph, nodeId, edgeKey, neighbors::add);
            adjacency.put(nodeId, neighbors);
        }
        return adjacency;
    }

    /**
     * Tarjan's SCC algorithm state. Encapsulates index, lowlink, stack, and
     * counter to avoid passing 8 parameters through recursive DFS.
     */
    private static class TarjanState {
        private final Map<String, Integer> index   = new HashMap<>();
        private final Map<String, Integer> lowlink = new HashMap<>();
        private final Set<String>          onStack = new HashSet<>();
        private final ArrayDeque<String>   stack   = new ArrayDeque<>();
        private final List<Set<String>>    sccs    = new ArrayList<>();
        private int                        counter = 0;

        /**
         * Finds all SCCs via Tarjan's algorithm (1972). O(V + E). Returns SCCs
         * in reverse topological order.
         */
        List<Set<String>> findSccs(List<String> nodeIds, Map<String, Set<String>> adjacency) {
            for (val nodeId : nodeIds) {
                if (!index.containsKey(nodeId)) {
                    dfs(nodeId, adjacency);
                }
            }
            for (val neighbors : adjacency.values()) {
                for (val neighbor : neighbors) {
                    if (!index.containsKey(neighbor)) {
                        dfs(neighbor, adjacency);
                    }
                }
            }
            return sccs;
        }

        private void dfs(String node, Map<String, Set<String>> adjacency) {
            val idx = counter++;
            index.put(node, idx);
            lowlink.put(node, idx);
            stack.push(node);
            onStack.add(node);

            for (val neighbor : adjacency.getOrDefault(node, Set.of())) {
                if (!index.containsKey(neighbor)) {
                    dfs(neighbor, adjacency);
                    lowlink.put(node, Math.min(lowlink.get(node), lowlink.get(neighbor)));
                } else if (onStack.contains(neighbor)) {
                    lowlink.put(node, Math.min(lowlink.get(node), index.get(neighbor)));
                }
            }

            if (lowlink.get(node).equals(index.get(node))) {
                val    scc = new HashSet<String>();
                String w;
                do {
                    w = stack.pop();
                    onStack.remove(w);
                    scc.add(w);
                } while (!w.equals(node));
                sccs.add(scc);
            }
        }
    }

    private static Map<String, Integer> mapNodesToScc(List<Set<String>> sccs) {
        val nodeToScc = new HashMap<String, Integer>();
        for (var i = 0; i < sccs.size(); i++) {
            for (val nodeId : sccs.get(i)) {
                nodeToScc.put(nodeId, i);
            }
        }
        return nodeToScc;
    }

    private static Map<Integer, Set<Integer>> buildDagAdjacency(List<Set<String>> sccs,
            Map<String, Set<String>> adjacency, Map<String, Integer> nodeToScc) {
        val dagAdj = new HashMap<Integer, Set<Integer>>();
        for (var i = 0; i < sccs.size(); i++) {
            val dagNeighbors = new HashSet<Integer>();
            for (val nodeId : sccs.get(i)) {
                for (val neighbor : adjacency.getOrDefault(nodeId, Set.of())) {
                    val neighborScc = nodeToScc.get(neighbor);
                    if (neighborScc != null && neighborScc != i) {
                        dagNeighbors.add(neighborScc);
                    }
                }
            }
            dagAdj.put(i, dagNeighbors);
        }
        return dagAdj;
    }

    /**
     * Bottom-up memoized closure on the condensed DAG. Tarjan returns SCCs in
     * reverse topological order, so forward iteration visits children before
     * parents. O(S) where S = total output size.
     */
    private static List<Set<String>> memoizedDagClosure(List<Set<String>> sccs, Map<Integer, Set<Integer>> dagAdj) {
        val closures = new ArrayList<Set<String>>(sccs.size());
        for (var i = 0; i < sccs.size(); i++) {
            val reachable = new HashSet<>(sccs.get(i));
            for (val childScc : dagAdj.getOrDefault(i, Set.of())) {
                reachable.addAll(closures.get(childScc));
            }
            closures.add(reachable);
        }
        return closures;
    }

    private static Set<String> bfs(ObjectValue graph, Value initial, String edgeKey, boolean ordered) {
        Set<String> visited = ordered ? new LinkedHashSet<>() : new HashSet<>();
        val         queue   = new ArrayDeque<String>();
        seedQueue(initial, visited, queue);
        while (!queue.isEmpty()) {
            val current = queue.removeFirst();
            forEachNeighbor(graph, current, edgeKey, neighbor -> {
                if (visited.add(neighbor)) {
                    queue.addLast(neighbor);
                }
            });
        }
        return visited;
    }

    private static void forEachNeighbor(ObjectValue graph, String nodeId, String edgeKey,
            java.util.function.Consumer<String> consumer) {
        val nodeValue = graph.get(nodeId);
        if (nodeValue == null) {
            return;
        }
        val edgesValue = edgeKey == null ? nodeValue : (nodeValue instanceof ObjectValue obj ? obj.get(edgeKey) : null);
        if (edgesValue instanceof ArrayValue edgesArray) {
            for (val neighbor : edgesArray) {
                consumer.accept(nodeIdOf(neighbor));
            }
        }
    }

    private static Value buildClosureArray(ObjectValue graph, String edgeKey) {
        val closure = computeAllPairsClosure(graph, edgeKey);
        val result  = ObjectValue.builder();
        for (val entry : graph.entrySet()) {
            result.put(entry.getKey(), toArrayValue(closure.getOrDefault(entry.getKey(), Set.of())));
        }
        return result.build();
    }

    private static Value buildClosureSet(ObjectValue graph, String edgeKey) {
        val closure = computeAllPairsClosure(graph, edgeKey);
        val result  = ObjectValue.builder();
        for (val entry : graph.entrySet()) {
            val setBuilder = ObjectValue.builder();
            for (val nodeId : closure.getOrDefault(entry.getKey(), Set.of())) {
                setBuilder.put(nodeId, Value.TRUE);
            }
            result.put(entry.getKey(), setBuilder.build());
        }
        return result.build();
    }

    private static Value collectAttribute(ObjectValue graph, Set<String> reachableIds, String attrKey) {
        val collected = new ArrayList<Value>();
        for (val nodeId : reachableIds) {
            val nodeValue = graph.get(nodeId);
            if (!(nodeValue instanceof ObjectValue nodeObj)) {
                continue;
            }
            val attrs = nodeObj.get("attributes");
            if (!(attrs instanceof ObjectValue attrsObj)) {
                continue;
            }
            val attrValue = attrsObj.get(attrKey);
            if (attrValue instanceof ArrayValue arrayAttr) {
                for (val element : arrayAttr) {
                    collected.add(element);
                }
            } else if (attrValue != null) {
                collected.add(attrValue);
            }
        }
        return Value.ofArray(collected.toArray(Value[]::new));
    }

    private static ArrayValue toArrayValue(Set<String> nodeIds) {
        val builder = ArrayValue.builder();
        for (val nodeId : nodeIds) {
            builder.add(Value.of(nodeId));
        }
        return builder.build();
    }

    private static void seedQueue(Value initial, Set<String> visited, ArrayDeque<String> queue) {
        if (initial instanceof ArrayValue arrayValue) {
            for (val element : arrayValue) {
                val rootId = nodeIdOf(element);
                if (visited.add(rootId)) {
                    queue.addLast(rootId);
                }
            }
        } else if (initial instanceof TextValue textValue) {
            val rootId = textValue.value();
            if (visited.add(rootId)) {
                queue.addLast(rootId);
            }
        }
    }

    private static String nodeIdOf(Value value) {
        if (value == null || value == Value.UNDEFINED || value == Value.NULL) {
            return NODE_ID_NULL;
        }
        return value instanceof TextValue textValue ? textValue.value() : value.toString();
    }

}
