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
package io.sapl.compiler.index.kleene;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.IntFunction;

import lombok.val;

/**
 * Test-only reference for the plain Kleene decision diagram in the index paper.
 * Document identities are positions in the input formula list. Predicate IDs
 * have an explicit total order and no interpreted meaning. Terminals classify
 * documents without carrying diagnostic payloads. No production index code is
 * used for construction or lookup.
 */
final class KleeneDdIndex {

    enum Truth {
        TRUE,
        FALSE,
        ERROR;

        Truth not() {
            return switch (this) {
            case TRUE  -> FALSE;
            case FALSE -> TRUE;
            case ERROR -> ERROR;
            };
        }

        Truth and(Truth other) {
            if (this == FALSE || other == FALSE) {
                return FALSE;
            }
            return this == ERROR || other == ERROR ? ERROR : TRUE;
        }

        Truth or(Truth other) {
            if (this == TRUE || other == TRUE) {
                return TRUE;
            }
            return this == ERROR || other == ERROR ? ERROR : FALSE;
        }
    }

    sealed interface Formula {
    }

    record Constant(Truth value) implements Formula {
        Constant {
            Objects.requireNonNull(value);
        }
    }

    record Atom(int predicate) implements Formula {}

    record Not(Formula operand) implements Formula {
        Not {
            Objects.requireNonNull(operand);
        }
    }

    record And(List<Formula> operands) implements Formula {
        And {
            operands = List.copyOf(operands);
        }
    }

    record Or(List<Formula> operands) implements Formula {
        Or {
            operands = List.copyOf(operands);
        }
    }

    record Matches(Set<Integer> trueMatches, Set<Integer> errorMatches) {
        Matches {
            trueMatches  = Set.copyOf(trueMatches);
            errorMatches = Set.copyOf(errorMatches);
        }

        private Matches union(Matches other) {
            val matched = new HashSet<>(trueMatches);
            val errored = new HashSet<>(errorMatches);
            matched.addAll(other.trueMatches);
            errored.addAll(other.errorMatches);
            return new Matches(matched, errored);
        }
    }

    /**
     * Reachable nodes belong to the final index. Allocated nodes count all unique
     * terminals and decisions created during construction, including temporary
     * formula diagrams and earlier index roots. This is not a byte-memory bound.
     */
    record Statistics(int decisionNodes, int terminals, int allocatedNodes) {}

    private sealed interface Node<V> {
        int id();

        default int level() {
            return this instanceof Decision<V> decision ? decision.level() : Integer.MAX_VALUE;
        }
    }

    private record Terminal<V>(int id, V value) implements Node<V> {}

    private record Decision<V>(int id, int level, Node<V> whenTrue, Node<V> whenFalse, Node<V> whenError)
            implements Node<V> {
        Node<V> child(Truth outcome) {
            return switch (outcome) {
            case TRUE  -> whenTrue;
            case FALSE -> whenFalse;
            case ERROR -> whenError;
            };
        }
    }

    // Keys use node IDs so hashing never recursively unfolds a shared graph.
    private record DecisionKey(int level, int whenTrue, int whenFalse, int whenError) {}

    private record NodePair(int left, int right) {}

    private static final class NodeBudget {
        private final int limit;
        private int       allocated;

        private NodeBudget(int limit) {
            if (limit < 1) {
                throw new IllegalArgumentException("Node limit must be positive.");
            }
            this.limit = limit;
        }

        private int allocate() {
            if (allocated == limit) {
                throw new IllegalStateException("Kleene DD node limit reached: " + limit);
            }
            return allocated++;
        }
    }

    private static final class Table<V> {
        private final NodeBudget                    budget;
        private final Map<V, Terminal<V>>           terminals = new HashMap<>();
        private final Map<DecisionKey, Decision<V>> decisions = new HashMap<>();

        private Table(NodeBudget budget) {
            this.budget = budget;
        }

        private Terminal<V> leaf(V value) {
            return terminals.computeIfAbsent(value, ignored -> new Terminal<>(budget.allocate(), value));
        }

        private Node<V> make(int level, Node<V> whenTrue, Node<V> whenFalse, Node<V> whenError) {
            if (whenTrue == whenFalse && whenFalse == whenError) {
                return whenTrue;
            }
            val key = new DecisionKey(level, whenTrue.id(), whenFalse.id(), whenError.id());
            return decisions.computeIfAbsent(key,
                    ignored -> new Decision<>(budget.allocate(), level, whenTrue, whenFalse, whenError));
        }

        private Node<V> apply(BinaryOperator<V> operation, Node<V> left, Node<V> right) {
            return apply(operation, left, right, new HashMap<>());
        }

        private Node<V> apply(BinaryOperator<V> operation, Node<V> left, Node<V> right, Map<NodePair, Node<V>> memo) {
            val key    = new NodePair(left.id(), right.id());
            val cached = memo.get(key);
            if (cached != null) {
                return cached;
            }
            Node<V> result;
            if (left instanceof Terminal<V> first && right instanceof Terminal<V> second) {
                result = leaf(operation.apply(first.value(), second.value()));
            } else {
                val level     = Math.min(left.level(), right.level());
                val whenTrue  = apply(operation, at(left, level, Truth.TRUE), at(right, level, Truth.TRUE), memo);
                val whenFalse = apply(operation, at(left, level, Truth.FALSE), at(right, level, Truth.FALSE), memo);
                val whenError = apply(operation, at(left, level, Truth.ERROR), at(right, level, Truth.ERROR), memo);
                result = make(level, whenTrue, whenFalse, whenError);
            }
            memo.put(key, result);
            return result;
        }

        private <W> Node<V> map(Node<W> source, Function<W, V> operation) {
            return map(source, operation, new HashMap<>());
        }

        private <W> Node<V> map(Node<W> source, Function<W, V> operation, Map<Integer, Node<V>> memo) {
            val cached = memo.get(source.id());
            if (cached != null) {
                return cached;
            }
            val result = switch (source) {
            case Terminal<W>(var ignored, var value)                                             ->
                leaf(operation.apply(value));
            case Decision<W>(var ignored, var level, var whenTrue, var whenFalse, var whenError) -> make(level,
                    map(whenTrue, operation, memo), map(whenFalse, operation, memo), map(whenError, operation, memo));
            };
            memo.put(source.id(), result);
            return result;
        }

        private static <V> Node<V> at(Node<V> node, int level, Truth outcome) {
            return node instanceof Decision<V> decision && decision.level() == level ? decision.child(outcome) : node;
        }
    }

    private final Node<Matches> root;
    private final List<Integer> predicateOrder;
    private final Statistics    statistics;

    private KleeneDdIndex(Node<Matches> root, List<Integer> predicateOrder, int allocatedNodes) {
        this.root           = root;
        this.predicateOrder = predicateOrder;
        this.statistics     = countNodes(root, allocatedNodes);
    }

    /** Builds with an explicit allocation limit, checked before each new node. */
    static KleeneDdIndex build(List<Formula> documents, List<Integer> predicateOrder, int maxNodes) {
        val formulas = List.copyOf(documents);
        val order    = List.copyOf(predicateOrder);
        val levels   = new HashMap<Integer, Integer>();
        for (var level = 0; level < order.size(); level++) {
            if (levels.put(order.get(level), level) != null) {
                throw new IllegalArgumentException("Duplicate predicate in order: " + order.get(level));
            }
        }
        val           budget     = new NodeBudget(maxNodes);
        val           truthTable = new Table<Truth>(budget);
        val           indexTable = new Table<Matches>(budget);
        Node<Matches> index      = indexTable.leaf(new Matches(Set.of(), Set.of()));
        for (var document = 0; document < formulas.size(); document++) {
            val identity = document;
            val formula  = compile(formulas.get(document), truthTable, levels);
            val labeled  = indexTable.map(formula, outcome -> label(identity, outcome));
            index = indexTable.apply(Matches::union, index, labeled);
        }
        return new KleeneDdIndex(index, order, budget.allocated);
    }

    Matches lookup(IntFunction<Truth> evaluate) {
        Objects.requireNonNull(evaluate);
        var node = root;
        while (node instanceof Decision<Matches> decision) {
            val outcome = Objects.requireNonNull(evaluate.apply(predicateOrder.get(decision.level())));
            node = decision.child(outcome);
        }
        return ((Terminal<Matches>) node).value();
    }

    Statistics statistics() {
        return statistics;
    }

    private static Node<Truth> compile(Formula formula, Table<Truth> table, Map<Integer, Integer> levels) {
        return switch (formula) {
        case Constant(var value) -> table.leaf(value);
        case Atom(var predicate) -> {
            val level = levels.get(predicate);
            if (level == null) {
                throw new IllegalArgumentException("Predicate missing from order: " + predicate);
            }
            yield table.make(level, table.leaf(Truth.TRUE), table.leaf(Truth.FALSE), table.leaf(Truth.ERROR));
        }
        case Not(var operand)    -> table.map(compile(operand, table, levels), Truth::not);
        case And(var operands)   -> fold(operands, table, levels, Truth.TRUE, Truth::and);
        case Or(var operands)    -> fold(operands, table, levels, Truth.FALSE, Truth::or);
        };
    }

    private static Node<Truth> fold(List<Formula> operands, Table<Truth> table, Map<Integer, Integer> levels,
            Truth identity, BinaryOperator<Truth> operation) {
        Node<Truth> result = table.leaf(identity);
        for (val operand : operands) {
            result = table.apply(operation, result, compile(operand, table, levels));
        }
        return result;
    }

    private static Matches label(int document, Truth outcome) {
        return switch (outcome) {
        case TRUE  -> new Matches(Set.of(document), Set.of());
        case FALSE -> new Matches(Set.of(), Set.of());
        case ERROR -> new Matches(Set.of(), Set.of(document));
        };
    }

    private static Statistics countNodes(Node<Matches> root, int allocated) {
        val visited = new HashSet<Integer>();
        val pending = new ArrayDeque<Node<Matches>>();
        pending.push(root);
        var decisions = 0;
        var terminals = 0;
        while (!pending.isEmpty()) {
            val node = pending.pop();
            if (!visited.add(node.id())) {
                continue;
            }
            if (node instanceof Decision<Matches> decision) {
                decisions++;
                pending.push(decision.whenTrue());
                pending.push(decision.whenFalse());
                pending.push(decision.whenError());
            } else {
                terminals++;
            }
        }
        return new Statistics(decisions, terminals, allocated);
    }
}
