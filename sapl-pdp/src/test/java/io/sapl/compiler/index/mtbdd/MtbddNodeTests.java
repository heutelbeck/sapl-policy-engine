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

import io.sapl.compiler.index.mtbdd.MtbddNode.Decision;
import io.sapl.compiler.index.mtbdd.MtbddNode.Terminal;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MTBDD node types and unique table")
class MtbddNodeTests {

    private static final boolean PRINT_RESULTS = false;

    @Nested
    @DisplayName("Terminal")
    class TerminalTests {

        @Test
        @DisplayName("empty terminal has no formulas")
        void whenEmptyThenNoFormulas() {
            assertThat(UniqueTable.EMPTY.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("terminal with matched formulas is not empty")
        void whenMatchedSetThenNotEmpty() {
            assertThat(new Terminal(bitsetOf(3, 7)).isEmpty()).isFalse();
        }

        @Test
        @DisplayName("terminals with same bits are equal")
        void whenSameBitsThenEqual() {
            val a = new Terminal(bitsetOf(1, 5));
            val b = new Terminal(bitsetOf(1, 5));
            assertThat(a).isEqualTo(b);
        }
    }

    @Nested
    @DisplayName("Decision")
    class DecisionTests {

        @Test
        @DisplayName("decision node stores level and children")
        void whenCreatedThenFieldsAccessible() {
            val leaf = UniqueTable.EMPTY;
            val node = new Decision(0, leaf, leaf, leaf);
            assertThat(node).satisfies(n -> {
                assertThat(n.level()).isZero();
                assertThat(n.trueChild()).isSameAs(leaf);
                assertThat(n.falseChild()).isSameAs(leaf);
                assertThat(n.errorChild()).isSameAs(leaf);
            });
        }
    }

    @Nested
    @DisplayName("UniqueTable")
    class UniqueTableTests {

        @Test
        @DisplayName("empty terminals are interned to same instance")
        void whenEmptyTerminalThenSameInstance() {
            val table = new UniqueTable();
            assertThat(table.terminal(new BitSet())).isSameAs(UniqueTable.EMPTY);
        }

        @Test
        @DisplayName("terminals with same bits are interned to same instance")
        void whenSameBitsThenSameInstance() {
            val table = new UniqueTable();
            assertThat(table.terminal(bitsetOf(2))).isSameAs(table.terminal(bitsetOf(2)));
        }

        @Test
        @DisplayName("decision nodes with same structure are interned")
        void whenSameStructureThenSameInstance() {
            val table   = new UniqueTable();
            val leaf    = UniqueTable.EMPTY;
            val matched = table.terminal(bitsetOf(0));
            val a       = table.decision(0, matched, leaf, leaf);
            val b       = table.decision(0, matched, leaf, leaf);
            assertThat(a).isSameAs(b);
        }

        @Test
        @DisplayName("redundant decision (all children same) collapses to child")
        void whenAllChildrenSameThenCollapsed() {
            val table  = new UniqueTable();
            val leaf   = table.terminal(bitsetOf(5));
            val result = table.decision(0, leaf, leaf, leaf);
            assertThat(result).isSameAs(leaf);
        }

        @Test
        @DisplayName("non-redundant decision creates a Decision node")
        void whenDifferentChildrenThenDecisionNode() {
            val table   = new UniqueTable();
            val empty   = UniqueTable.EMPTY;
            val matched = table.terminal(bitsetOf(1));
            val result  = table.decision(0, matched, empty, empty);
            assertThat(result).isInstanceOf(Decision.class);
        }

        @Test
        @DisplayName("size counts all interned nodes")
        void whenNodesCreatedThenSizeCounts() {
            val table   = new UniqueTable();
            val matched = table.terminal(bitsetOf(0));
            table.decision(0, matched, UniqueTable.EMPTY, UniqueTable.EMPTY);
            // 1 EMPTY (pre-loaded) + 1 matched terminal + 1 decision = 3
            assertThat(table.size()).isEqualTo(3);
        }

        @Test
        @DisplayName("visual: merged diagram structure")
        void visualMergedStructure() {
            val table = new UniqueTable();
            val empty = UniqueTable.EMPTY;

            // Manually construct a merged diagram for f0=p0, f1=p1
            val matched0  = table.terminal(bitsetOf(0));
            val matched1  = table.terminal(bitsetOf(1));
            val matched01 = table.terminal(bitsetOf(0, 1));

            // p1 subtree (f1 only, reached when p0 is false or error)
            val p1sub = table.decision(1, matched1, empty, empty);

            // Root: p0? T: check p1 for f1, F: check p1 for f1, E: f0 gone, check p1
            val root = table.decision(0, table.decision(1, matched01, matched0, matched0), p1sub, p1sub);

            if (PRINT_RESULTS) {
                System.out.println("\n=== Manually constructed merged diagram ===");
                System.out.println("Formula 0: p0");
                System.out.println("Formula 1: p1");
                System.out.println();
                System.out.println(root.toTree());
            }

            assertThat(root).isInstanceOf(Decision.class);
        }
    }

    private static BitSet bitsetOf(int... indices) {
        val bits = new BitSet();
        for (val i : indices) {
            bits.set(i);
        }
        return bits;
    }

}
