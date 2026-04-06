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

/**
 * A node in a Multi-Terminal Binary Decision Diagram (MTBDD) extended
 * with ternary edges (true/false/error) for policy indexing.
 * <p>
 * Two kinds of nodes:
 * <ul>
 * <li>{@link Terminal} - a leaf carrying a set of matched formula indices</li>
 * <li>{@link Decision} - an internal node that branches on a predicate,
 * identified by its level in the fixed variable order</li>
 * </ul>
 * Nodes are interned by a {@link UniqueTable}: structurally identical
 * nodes are always the same object, enabling identity-based caching
 * in the Apply algorithm.
 */
public sealed interface MtbddNode {

    /**
     * A leaf node carrying the sets of matched and errored formula
     * indices along this path.
     *
     * @param matched formulas whose applicability is satisfied
     * @param errored formulas whose applicability encountered an error
     */
    record Terminal(BitSet matched, BitSet errored) implements MtbddNode {

        /**
         * @return true if no formulas are matched or errored
         */
        public boolean isEmpty() {
            return matched.isEmpty() && errored.isEmpty();
        }
    }

    /**
     * An internal decision node that branches on the predicate at the
     * given level in the variable order.
     * <p>
     * Invariant: {@code level} is strictly less than the level of any
     * descendant decision node. This is enforced by the unique table
     * and the Apply algorithm.
     *
     * @param level the predicate index in the variable order (0 = root-most)
     * @param trueChild child when the predicate evaluates to true
     * @param falseChild child when the predicate evaluates to false
     * @param errorChild child when the predicate evaluation errors
     */
    record Decision(int level, MtbddNode trueChild, MtbddNode falseChild, MtbddNode errorChild) implements MtbddNode {}

}
