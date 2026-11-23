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

import io.sapl.compiler.index.smtdd.SmtddNode.BinaryDecision;
import io.sapl.compiler.index.smtdd.SmtddNode.Terminal;
import lombok.val;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Interns SMTDD Terminal and BinaryDecision nodes for structural sharing.
 * EqualityBranch nodes are few and unique - not interned.
 */
class SmtddUniqueTable {

    private static final BitSet EMPTY_BITSET = new BitSet();

    static final Terminal EMPTY = new Terminal(EMPTY_BITSET);

    private final Map<Terminal, Terminal>             terminals      = new HashMap<>();
    private final Map<BinaryDecision, BinaryDecision> decisions      = new HashMap<>();
    private final BitSet                              positiveLevels = new BitSet();
    private final BitSet                              negativeLevels = new BitSet();

    SmtddUniqueTable() {
        terminals.put(EMPTY, EMPTY);
    }

    /**
     * Returns the canonical terminal for the given matched formulas. Empty
     * bitsets always return the shared {@link #EMPTY} instance. Equal
     * bitsets are interned to the same object for identity-based caching.
     *
     * @param matched formula indices satisfied along this path
     * @return the interned terminal node
     */
    Terminal terminal(BitSet matched) {
        if (matched.isEmpty()) {
            return EMPTY;
        }
        val candidate = new Terminal(matched);
        return terminals.computeIfAbsent(candidate, k -> k);
    }

    SmtddNode literal(int level, Terminal matched, boolean negated) {
        if (negated) {
            negativeLevels.set(level);
            return binaryDecision(level, EMPTY, matched, EMPTY);
        }
        positiveLevels.set(level);
        return binaryDecision(level, matched, EMPTY, EMPTY);
    }

    /**
     * Returns the canonical binary decision node for the given children.
     * Both literal polarities route errors to EMPTY. At a level used in both
     * polarities, even identical children can hide an error. The evaluator records
     * the affected formulas for reclassification only when it visits the decision.
     *
     * @param level predicate index in the binary variable order
     * @param trueChild child when predicate is true
     * @param falseChild child when predicate is false
     * @param errorChild child when predicate errors
     * @return the interned decision node, or the child if redundant
     */
    SmtddNode binaryDecision(int level, SmtddNode trueChild, SmtddNode falseChild, SmtddNode errorChild) {
        if (trueChild == falseChild && falseChild == errorChild
                && !(positiveLevels.get(level) && negativeLevels.get(level))) {
            return trueChild;
        }
        val candidate = new BinaryDecision(level, trueChild, falseChild, errorChild);
        return decisions.computeIfAbsent(candidate, k -> k);
    }

    int size() {
        return terminals.size() + decisions.size();
    }

}
