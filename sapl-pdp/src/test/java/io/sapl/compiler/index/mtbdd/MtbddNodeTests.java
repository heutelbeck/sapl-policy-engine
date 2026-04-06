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
            assertThat(new Terminal(bitsetOf(3, 7), new BitSet()).isEmpty()).isFalse();
        }

        @Test
        @DisplayName("terminal with only errored formulas is not empty")
        void whenErroredSetThenNotEmpty() {
            assertThat(new Terminal(new BitSet(), bitsetOf(2)).isEmpty()).isFalse();
        }

        @Test
        @DisplayName("terminals with same matched and errored are equal")
        void whenSameBitsThenEqual() {
            val a = new Terminal(bitsetOf(1, 5), bitsetOf(3));
            val b = new Terminal(bitsetOf(1, 5), bitsetOf(3));
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
            assertThat(table.terminal(new BitSet(), new BitSet())).isSameAs(UniqueTable.EMPTY);
        }

        @Test
        @DisplayName("terminals with same bits are interned to same instance")
        void whenSameBitsThenSameInstance() {
            val table = new UniqueTable();
            assertThat(table.terminal(bitsetOf(2), new BitSet())).isSameAs(table.terminal(bitsetOf(2), new BitSet()));
        }

        @Test
        @DisplayName("decision nodes with same structure are interned")
        void whenSameStructureThenSameInstance() {
            val table   = new UniqueTable();
            val leaf    = UniqueTable.EMPTY;
            val matched = table.terminal(bitsetOf(0), new BitSet());
            val a       = table.decision(0, matched, leaf, leaf);
            val b       = table.decision(0, matched, leaf, leaf);
            assertThat(a).isSameAs(b);
        }

        @Test
        @DisplayName("redundant decision (all children same) collapses to child")
        void whenAllChildrenSameThenCollapsed() {
            val table  = new UniqueTable();
            val leaf   = table.terminal(bitsetOf(5), new BitSet());
            val result = table.decision(0, leaf, leaf, leaf);
            assertThat(result).isSameAs(leaf);
        }

        @Test
        @DisplayName("non-redundant decision creates a Decision node")
        void whenDifferentChildrenThenDecisionNode() {
            val table   = new UniqueTable();
            val empty   = UniqueTable.EMPTY;
            val matched = table.terminal(bitsetOf(1), new BitSet());
            val result  = table.decision(0, matched, empty, empty);
            assertThat(result).isInstanceOf(Decision.class);
        }

        @Test
        @DisplayName("size counts all interned nodes")
        void whenNodesCreatedThenSizeCounts() {
            val table   = new UniqueTable();
            val matched = table.terminal(bitsetOf(0), new BitSet());
            table.decision(0, matched, UniqueTable.EMPTY, UniqueTable.EMPTY);
            // 1 EMPTY (pre-loaded) + 1 matched terminal + 1 decision = 3
            assertThat(table.size()).isEqualTo(3);
        }

        @Test
        @DisplayName("visual: two formulas with error propagation")
        void visualWithErrors() {
            val table  = new UniqueTable();
            val empty  = UniqueTable.EMPTY;
            val noBits = new BitSet();

            // Formula 0: p0
            // Formula 1: p0 OR p1
            // Error semantics: if a predicate errors, all formulas referencing it get
            // errored

            // Terminals
            val matched01 = table.terminal(bitsetOf(0, 1), noBits);      // both matched
            val matched1  = table.terminal(bitsetOf(1), noBits);          // only f1 matched
            val errored0  = table.terminal(noBits, bitsetOf(0));          // f0 errored
            val errored01 = table.terminal(noBits, bitsetOf(0, 1));       // both errored
            val m1e0      = table.terminal(bitsetOf(1), bitsetOf(0));     // f1 matched, f0 errored

            // p1? subtree (only formula 1 references p1)
            // Reached when p0 is false (f0 already eliminated)
            val p1afterF = table.decision(1, matched1, empty, table.terminal(noBits, bitsetOf(1)));

            // p1? subtree when p0 errored (f0 errored, f1 still decidable via p1)
            val p1afterE = table.decision(1, m1e0, errored0, errored01);

            // Root: p0?
            val root = table.decision(0, matched01, p1afterF, p1afterE);

            System.out.println("\n=== MTBDD with error propagation ===");
            System.out.println("Formula 0: p0");
            System.out.println("Formula 1: p0 OR p1");
            System.out.println();
            System.out.println(MtbddPrinter.print(root));

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
