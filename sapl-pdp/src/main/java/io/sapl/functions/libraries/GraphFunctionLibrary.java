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
import lombok.val;

import java.util.*;
import java.util.function.Consumer;

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

            ## Performance

            When the graph is a PDP configuration variable, a transitive-closure call is a constant
            expression, so the compiler evaluates it once at compile time and reuses the result for
            every decision instead of recomputing it per request. For a large graph, or a graph
            supplied at runtime, use `reachable` (single-source) or `isReachable` (single-pair),
            which do not materialize a closure.

            ## Limits

            The transitive closure functions are capped at 1000000 output entries and return an error
            value above that. This limit applies because the input may originate from the authorization
            subscription or from policy information points, which are not vetted to the same degree as
            the policies and variables shipped with the PDP configuration.
            """;

    private static final String NODE_ID_NULL           = "null";
    private static final String SCHEMA_RETURNS_ARRAY   = """
            { "type": "array" }
            """;
    private static final String SCHEMA_RETURNS_OBJECT  = """
            { "type": "object" }
            """;
    private static final String SCHEMA_RETURNS_BOOLEAN = """
            { "type": "boolean" }
            """;

    private static final String ERROR_CLOSURE_TOO_LARGE = "Transitive closure exceeds the maximum of %d entries.";

    private static final int MAX_CLOSURE_ENTRIES = 1_000_000;

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

    @Function(name = "isReachable", docs = """
            ```graph.isReachable(OBJECT graph, STRING|ARRAY from, STRING to)```: Single-pair reachability.
            Returns true if `to` is reachable from `from`, where a node reaches itself. The search stops
            as soon as the target is found. O(V + E) worst case with constant output, and constant memory
            beyond the visited set. Prefer this for a ReBAC check on a large graph instead of materializing
            a full transitive closure.

            ```sapl
            graph.isReachable(rolesHierarchy, subject.role, "viewer");
            ```
            """, schema = SCHEMA_RETURNS_BOOLEAN)
    public static Value isReachable(ObjectValue graph, Value from, Value to) {
        return Value.of(reachesTarget(graph, from, null, nodeIdOf(to)));
    }

    /**
     * All-pairs transitive closure (adjacency list). O(V + E + S).
     *
     * @param graph adjacency list
     * @return closure where each node maps to array of reachable nodes
     */
    @Function(name = "transitiveClosure", docs = """
            ```graph.transitiveClosure(OBJECT graph)```: All-pairs transitive closure via Tarjan's SCC
            + memoized DAG closure. O(V + E + S). Traversal is iterative, so deep graphs do not
            cause a stack overflow. The output size S grows with reachability and can reach
            O(V^2) for densely connected graphs.

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
        final Map<String, Set<String>> closure;
        try {
            closure = computeAllPairsClosure(graph, edgeKey.value());
        } catch (IllegalArgumentException e) {
            return Value.error(e.getMessage());
        }
        val attr   = attrKey.value();
        val result = ObjectValue.builder();
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
            paths via BFS. O(V + E). Returns array of paths (each an array of node IDs). The total
            emitted path steps are capped at 1000000 and an error value is returned above that, since
            paths can grow to O(V^2) on a large or runtime-supplied graph.
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
        val steps        = new ArrayList<String>();
        var emittedSteps = 0L;
        for (val nodeId : visited) {
            steps.clear();
            for (var c = nodeId; c != null; c = predecessors.get(c)) {
                steps.add(c);
            }
            // Cap total path steps, they can grow O(V^2) on untrusted graphs.
            emittedSteps += steps.size();
            if (emittedSteps > MAX_CLOSURE_ENTRIES) {
                return Value.error(ERROR_CLOSURE_TOO_LARGE.formatted(MAX_CLOSURE_ENTRIES));
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
        private final Map<String, Integer> index       = new HashMap<>();
        private final Map<String, Integer> lowlink     = new HashMap<>();
        private final Set<String>          activeInDfs = new HashSet<>();
        private final ArrayDeque<String>   stack       = new ArrayDeque<>();
        private final List<Set<String>>    sccs        = new ArrayList<>();
        private int                        counter     = 0;

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

        private void dfs(String startNode, Map<String, Set<String>> adjacency) {
            val work = new ArrayDeque<Frame>();
            visit(startNode);
            work.push(new Frame(startNode, adjacency.getOrDefault(startNode, Set.of()).iterator()));

            while (!work.isEmpty()) {
                val frame     = work.peek();
                val node      = frame.node;
                var descended = false;

                while (frame.neighbors.hasNext()) {
                    val neighbor = frame.neighbors.next();
                    if (!index.containsKey(neighbor)) {
                        visit(neighbor);
                        work.push(new Frame(neighbor, adjacency.getOrDefault(neighbor, Set.of()).iterator()));
                        descended = true;
                        break;
                    } else if (activeInDfs.contains(neighbor)) {
                        lowlink.put(node, Math.min(lowlink.get(node), index.get(neighbor)));
                    }
                }
                if (descended) {
                    continue;
                }

                if (lowlink.get(node).equals(index.get(node))) {
                    collectScc(node);
                }

                work.pop();
                val parent = work.peek();
                if (parent != null) {
                    lowlink.put(parent.node, Math.min(lowlink.get(parent.node), lowlink.get(node)));
                }
            }
        }

        private void visit(String node) {
            val idx = counter++;
            index.put(node, idx);
            lowlink.put(node, idx);
            stack.push(node);
            activeInDfs.add(node);
        }

        private void collectScc(String root) {
            val    scc = new HashSet<String>();
            String w;
            do {
                w = stack.pop();
                activeInDfs.remove(w);
                scc.add(w);
            } while (!w.equals(root));
            sccs.add(scc);
        }

        private static final class Frame {
            private final String           node;
            private final Iterator<String> neighbors;

            private Frame(String node, Iterator<String> neighbors) {
                this.node      = node;
                this.neighbors = neighbors;
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
        val closures      = new ArrayList<Set<String>>(sccs.size());
        var outputEntries = 0L;
        for (var i = 0; i < sccs.size(); i++) {
            val reachable = new HashSet<>(sccs.get(i));
            for (val childScc : dagAdj.getOrDefault(i, Set.of())) {
                reachable.addAll(closures.get(childScc));
            }
            closures.add(reachable);
            // Bound the materialized output (sum over nodes of their reachable-set size)
            // and
            // abort before the quadratic blow-up allocates, rather than after.
            outputEntries += (long) sccs.get(i).size() * reachable.size();
            if (outputEntries > MAX_CLOSURE_ENTRIES) {
                throw new IllegalArgumentException(ERROR_CLOSURE_TOO_LARGE.formatted(MAX_CLOSURE_ENTRIES));
            }
        }
        return closures;
    }

    private static boolean reachesTarget(ObjectValue graph, Value from, String edgeKey, String target) {
        val visited = new HashSet<String>();
        val queue   = new ArrayDeque<String>();
        seedQueue(from, visited, queue);
        if (visited.contains(target)) {
            return true;
        }
        while (!queue.isEmpty()) {
            val current = queue.removeFirst();
            val found   = new boolean[1];
            forEachNeighbor(graph, current, edgeKey, neighbor -> {
                if (visited.add(neighbor)) {
                    queue.addLast(neighbor);
                }
                if (neighbor.equals(target)) {
                    found[0] = true;
                }
            });
            if (found[0]) {
                return true;
            }
        }
        return false;
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

    private static void forEachNeighbor(ObjectValue graph, String nodeId, String edgeKey, Consumer<String> consumer) {
        val nodeValue = graph.get(nodeId);
        if (nodeValue == null) {
            return;
        }
        Value edgesValue;
        if (edgeKey == null) {
            edgesValue = nodeValue;
        } else if (nodeValue instanceof ObjectValue obj) {
            edgesValue = obj.get(edgeKey);
        } else {
            edgesValue = null;
        }
        if (edgesValue instanceof ArrayValue edgesArray) {
            for (val neighbor : edgesArray) {
                consumer.accept(nodeIdOf(neighbor));
            }
        }
    }

    private static Value buildClosureArray(ObjectValue graph, String edgeKey) {
        final Map<String, Set<String>> closure;
        try {
            closure = computeAllPairsClosure(graph, edgeKey);
        } catch (IllegalArgumentException e) {
            return Value.error(e.getMessage());
        }
        val result = ObjectValue.builder();
        for (val entry : graph.entrySet()) {
            result.put(entry.getKey(), toArrayValue(closure.getOrDefault(entry.getKey(), Set.of())));
        }
        return result.build();
    }

    private static Value buildClosureSet(ObjectValue graph, String edgeKey) {
        final Map<String, Set<String>> closure;
        try {
            closure = computeAllPairsClosure(graph, edgeKey);
        } catch (IllegalArgumentException e) {
            return Value.error(e.getMessage());
        }
        val result = ObjectValue.builder();
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
            val attrValue = resolveAttribute(graph, nodeId, attrKey);
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

    private static Value resolveAttribute(ObjectValue graph, String nodeId, String attrKey) {
        val nodeValue = graph.get(nodeId);
        if (!(nodeValue instanceof ObjectValue nodeObj)) {
            return null;
        }
        val attrs = nodeObj.get("attributes");
        if (!(attrs instanceof ObjectValue attrsObj)) {
            return null;
        }
        return attrsObj.get(attrKey);
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
        } else {
            val rootId = nodeIdOf(initial);
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
