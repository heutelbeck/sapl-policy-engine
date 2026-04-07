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
import java.util.HashMap;
import java.util.Map;

import io.sapl.compiler.index.smtdd.SmtddNode.BinaryDecision;
import io.sapl.compiler.index.smtdd.SmtddNode.Terminal;
import lombok.val;

/**
 * Interns SMTDD Terminal and BinaryDecision nodes for structural sharing.
 * EqualityBranch nodes are few and unique - not interned.
 */
class SmtddUniqueTable {

    private static final BitSet EMPTY_BITSET = new BitSet();

    static final Terminal EMPTY = new Terminal(EMPTY_BITSET);

    private final Map<Terminal, Terminal>             terminals = new HashMap<>();
    private final Map<BinaryDecision, BinaryDecision> decisions = new HashMap<>();

    SmtddUniqueTable() {
        terminals.put(EMPTY, EMPTY);
    }

    Terminal terminal(BitSet matched) {
        if (matched.isEmpty()) {
            return EMPTY;
        }
        val candidate = new Terminal(matched);
        return terminals.computeIfAbsent(candidate, k -> k);
    }

    SmtddNode binaryDecision(int level, SmtddNode trueChild, SmtddNode falseChild, SmtddNode errorChild) {
        if (trueChild == falseChild && falseChild == errorChild) {
            return trueChild;
        }
        val candidate = new BinaryDecision(level, trueChild, falseChild, errorChild);
        return decisions.computeIfAbsent(candidate, k -> k);
    }

    int size() {
        return terminals.size() + decisions.size();
    }

}
