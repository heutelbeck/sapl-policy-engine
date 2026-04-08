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

import io.sapl.api.model.Value;
import io.sapl.compiler.index.smtdd.SmtddNode.BinaryDecision;
import io.sapl.compiler.index.smtdd.SmtddNode.EqualityBranch;
import io.sapl.compiler.index.smtdd.SmtddNode.Terminal;

import static io.sapl.compiler.index.smtdd.SmtddTestFixtures.stubOperand;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SMTDD node types and unique table")
class SmtddNodeAndUniqueTableTests {

    @Nested
    @DisplayName("Terminal")
    class TerminalTests {

        @Test
        @DisplayName("empty terminal reports isEmpty true")
        void whenEmptyThenIsEmpty() {
            assertThat(SmtddUniqueTable.EMPTY.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("terminal with bits set reports isEmpty false")
        void whenBitsSetThenNotEmpty() {
            val bits = new BitSet();
            bits.set(3);
            assertThat(new Terminal(bits).isEmpty()).isFalse();
        }

        @Test
        @DisplayName("terminals with same bits are equal")
        void whenSameBitsThenEqual() {
            val bitsA = new BitSet();
            bitsA.set(1);
            val bitsB = new BitSet();
            bitsB.set(1);
            assertThat(new Terminal(bitsA)).isEqualTo(new Terminal(bitsB));
        }
    }

    @Nested
    @DisplayName("UniqueTable")
    class UniqueTableTests {

        @Test
        @DisplayName("empty terminal interned to shared instance")
        void whenEmptyTerminalThenSameInstance() {
            val table = new SmtddUniqueTable();
            assertThat(table.terminal(new BitSet())).isSameAs(SmtddUniqueTable.EMPTY);
        }

        @Test
        @DisplayName("terminals with same bits interned to same instance")
        void whenSameBitsThenSameInstance() {
            val table = new SmtddUniqueTable();
            val bitsA = new BitSet();
            bitsA.set(5);
            val bitsB = new BitSet();
            bitsB.set(5);
            assertThat(table.terminal(bitsA)).isSameAs(table.terminal(bitsB));
        }

        @Test
        @DisplayName("decision nodes with same structure interned")
        void whenSameDecisionThenSameInstance() {
            val table   = new SmtddUniqueTable();
            val matched = table.terminal(bitsetOf(0));
            val empty   = SmtddUniqueTable.EMPTY;
            val first   = table.binaryDecision(0, matched, empty, empty);
            val second  = table.binaryDecision(0, matched, empty, empty);
            assertThat(first).isSameAs(second);
        }

        @Test
        @DisplayName("redundant decision collapses to child")
        void whenAllChildrenSameThenCollapsed() {
            val table   = new SmtddUniqueTable();
            val matched = table.terminal(bitsetOf(0));
            val result  = table.binaryDecision(0, matched, matched, matched);
            assertThat(result).isSameAs(matched);
        }

        @Test
        @DisplayName("non-redundant decision creates BinaryDecision")
        void whenDifferentChildrenThenDecisionNode() {
            val table   = new SmtddUniqueTable();
            val matched = table.terminal(bitsetOf(0));
            val result  = table.binaryDecision(0, matched, SmtddUniqueTable.EMPTY, SmtddUniqueTable.EMPTY);
            assertThat(result).isInstanceOf(BinaryDecision.class);
        }

        @Test
        @DisplayName("size counts all interned nodes")
        void whenNodesCreatedThenSizeCorrect() {
            val table   = new SmtddUniqueTable();
            val matched = table.terminal(bitsetOf(0));
            table.binaryDecision(0, matched, SmtddUniqueTable.EMPTY, SmtddUniqueTable.EMPTY);
            // 1 EMPTY (pre-loaded) + 1 matched + 1 decision = 3
            assertThat(table.size()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("toTree rendering")
    class TreeRenderingTests {

        @Test
        @DisplayName("empty terminal renders as EMPTY")
        void whenEmptyThenRendersEmpty() {
            assertThat(SmtddUniqueTable.EMPTY.toTree()).contains("EMPTY");
        }

        @Test
        @DisplayName("binary decision renders with edge labels")
        void whenBinaryDecisionThenRendersEdges() {
            val table   = new SmtddUniqueTable();
            val matched = table.terminal(bitsetOf(0));
            val node    = table.binaryDecision(0, matched, SmtddUniqueTable.EMPTY, SmtddUniqueTable.EMPTY);
            val tree    = node.toTree();
            assertThat(tree).contains("T:").contains("F:").contains("E:");
        }

        @Test
        @DisplayName("equality branch renders constant labels, skips empty default")
        void whenEqualityBranchEmptyDefaultThenNoDefaultLabel() {
            val table    = new SmtddUniqueTable();
            val matchedA = table.terminal(bitsetOf(0));
            val matchedB = table.terminal(bitsetOf(1));
            val branches = new HashMap<Value, SmtddNode>();
            branches.put(Value.of("alpha"), matchedA);
            branches.put(Value.of("beta"), matchedB);
            val node = new EqualityBranch(stubOperand(42L), branches, SmtddUniqueTable.EMPTY, SmtddUniqueTable.EMPTY,
                    bitsetOf(0, 1));
            val tree = node.toTree();
            assertThat(tree).contains("EQ(").contains("alpha").contains("beta").doesNotContain("default:");
        }

        @Test
        @DisplayName("equality branch with non-empty default renders default label")
        void whenNonEmptyDefaultThenRendersDefaultLabel() {
            val table      = new SmtddUniqueTable();
            val matchedA   = table.terminal(bitsetOf(0));
            val matchedDef = table.terminal(bitsetOf(2));
            val branches   = new HashMap<Value, SmtddNode>();
            branches.put(Value.of("x"), matchedA);
            branches.put(Value.of("y"), SmtddUniqueTable.EMPTY);
            val node = new EqualityBranch(stubOperand(42L), branches, matchedDef, matchedDef, bitsetOf(0));
            assertThat(node.toTree()).contains("default:");
        }

        @Test
        @DisplayName("shared nodes render as back-references")
        void whenSharedNodesThenBackReferences() {
            val table  = new SmtddUniqueTable();
            val shared = table.terminal(bitsetOf(0));
            val node   = table.binaryDecision(0, shared, shared, SmtddUniqueTable.EMPTY);
            val tree   = node.toTree();
            assertThat(tree).contains("-> #");
        }

        @Test
        @DisplayName("matched terminal renders formula indices")
        void whenMatchedTerminalThenRendersIndices() {
            val table = new SmtddUniqueTable();
            val node  = table.terminal(bitsetOf(3, 7));
            assertThat(node.toTree()).contains("matched=").contains("3").contains("7");
        }
    }

    private static BitSet bitsetOf(int... indices) {
        val bits = new BitSet();
        for (val index : indices) {
            bits.set(index);
        }
        return bits;
    }

}
