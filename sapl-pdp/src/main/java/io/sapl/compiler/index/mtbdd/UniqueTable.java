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
import lombok.val;

/**
 * Interns MTBDD nodes so that structurally identical nodes are the
 * same object. This enables identity-based caching in Apply and
 * guarantees the diagram is canonical for a given variable order.
 * <p>
 * Two decision nodes with the same (level, trueChild, falseChild,
 * errorChild) will always return the same instance. Two terminals
 * with the same formula set will always return the same instance.
 * <p>
 * Additionally, a decision node whose three children are all the
 * same is redundant and is collapsed to the child directly.
 */
class UniqueTable {

    private static final BitSet EMPTY_BITSET = new BitSet();

    /** Shared empty terminal: no formulas matched. */
    static final Terminal EMPTY = new Terminal(EMPTY_BITSET);

    private final Map<Terminal, Terminal> terminals = new HashMap<>();
    private final Map<Decision, Decision> decisions = new HashMap<>();

    UniqueTable() {
        terminals.put(EMPTY, EMPTY);
    }

    /**
     * Returns the canonical terminal for the given matched formula set.
     *
     * @param matched formulas satisfied along this path
     * @return the interned terminal node
     */
    Terminal terminal(BitSet matched) {
        if (matched.isEmpty()) {
            return EMPTY;
        }
        val candidate = new Terminal(matched);
        return terminals.computeIfAbsent(candidate, k -> k);
    }

    /**
     * Returns the canonical decision node for the given parameters.
     * If all three children are the same node, the decision is
     * redundant and the child is returned directly (no new node).
     *
     * @param level the predicate level in the variable order
     * @param trueChild child for predicate = true
     * @param falseChild child for predicate = false
     * @param errorChild child for predicate = error
     * @return the interned decision node, or the child if redundant
     */
    MtbddNode decision(int level, MtbddNode trueChild, MtbddNode falseChild, MtbddNode errorChild) {
        if (trueChild == falseChild && falseChild == errorChild) {
            return trueChild;
        }
        val candidate = new Decision(level, trueChild, falseChild, errorChild);
        return decisions.computeIfAbsent(candidate, k -> k);
    }

    /**
     * @return total number of interned nodes (terminals + decisions)
     */
    int size() {
        return terminals.size() + decisions.size();
    }

}
