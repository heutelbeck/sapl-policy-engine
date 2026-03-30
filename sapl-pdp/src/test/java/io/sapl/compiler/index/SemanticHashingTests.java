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
package io.sapl.compiler.index;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.sapl.compiler.index.SemanticHashing.commutative;
import static io.sapl.compiler.index.SemanticHashing.kindHash;
import static io.sapl.compiler.index.SemanticHashing.ordered;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SemanticHashing")
class SemanticHashingTests {

    @Nested
    @DisplayName("kindHash")
    class KindHashTests {

        @Test
        @DisplayName("same class produces same hash")
        void whenSameClassThenSameHash() {
            assertThat(kindHash(String.class)).isEqualTo(kindHash(String.class));
        }

        @Test
        @DisplayName("different classes produce different hashes")
        void whenDifferentClassesThenDifferentHash() {
            assertThat(kindHash(String.class)).isNotEqualTo(kindHash(Integer.class));
        }
    }

    @Nested
    @DisplayName("ordered - determinism")
    class OrderedDeterminismTests {

        @Test
        @DisplayName("same kind and children produce same hash")
        void whenSameInputsThenSameHash() {
            assertThat(ordered(1L, 2L, 3L)).isEqualTo(ordered(1L, 2L, 3L));
        }

        @Test
        @DisplayName("no children is deterministic")
        void whenNoChildrenThenDeterministic() {
            assertThat(ordered(42L)).isEqualTo(ordered(42L));
        }

        @Test
        @DisplayName("single child is deterministic")
        void whenSingleChildThenDeterministic() {
            assertThat(ordered(1L, 100L)).isEqualTo(ordered(1L, 100L));
        }
    }

    @Nested
    @DisplayName("ordered - sensitivity to differences")
    class OrderedSensitivityTests {

        @Test
        @DisplayName("different child order produces different hash")
        void whenDifferentChildOrderThenDifferentHash() {
            assertThat(ordered(1L, 2L, 3L)).isNotEqualTo(ordered(1L, 3L, 2L));
        }

        @Test
        @DisplayName("different kind with same children produces different hash")
        void whenDifferentKindThenDifferentHash() {
            assertThat(ordered(1L, 2L, 3L)).isNotEqualTo(ordered(99L, 2L, 3L));
        }

        @Test
        @DisplayName("swapping kind and single child produces different hash")
        void whenKindAndChildSwappedThenDifferentHash() {
            assertThat(ordered(1L, 100L)).isNotEqualTo(ordered(100L, 1L));
        }

        @Test
        @DisplayName("same children, adjacent kinds produce different hashes")
        void whenAdjacentKindsThenDifferentHash() {
            assertThat(ordered(1L, 10L, 20L)).isNotEqualTo(ordered(2L, 10L, 20L));
        }

        @Test
        @DisplayName("same kind, adjacent child values produce different hashes")
        void whenAdjacentChildValuesThenDifferentHash() {
            assertThat(ordered(1L, 10L)).isNotEqualTo(ordered(1L, 11L));
        }

        @Test
        @DisplayName("extra child changes hash")
        void whenExtraChildThenDifferentHash() {
            assertThat(ordered(1L, 2L)).isNotEqualTo(ordered(1L, 2L, 3L));
        }
    }

    @Nested
    @DisplayName("commutative - order independence")
    class CommutativeOrderTests {

        @Test
        @DisplayName("two children in different order produce same hash")
        void whenTwoChildrenReorderedThenSameHash() {
            assertThat(commutative(1L, 2L, 3L)).isEqualTo(commutative(1L, 3L, 2L));
        }

        @Test
        @DisplayName("three children in any permutation produce same hash")
        void whenThreeChildrenAnyPermutationThenSameHash() {
            long expected = commutative(1L, 10L, 20L, 30L);
            assertThat(commutative(1L, 30L, 10L, 20L)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("commutative - sensitivity to differences")
    class CommutativeSensitivityTests {

        @Test
        @DisplayName("different kind produces different hash")
        void whenDifferentKindThenDifferentHash() {
            assertThat(commutative(1L, 2L, 3L)).isNotEqualTo(commutative(99L, 2L, 3L));
        }

        @Test
        @DisplayName("different children produce different hash")
        void whenDifferentChildrenThenDifferentHash() {
            assertThat(commutative(1L, 2L, 3L)).isNotEqualTo(commutative(1L, 2L, 4L));
        }
    }

    @Nested
    @DisplayName("commutative vs ordered interaction")
    class CommutativeVsOrderedTests {

        @Test
        @DisplayName("commutative matches on reorder, ordered does not")
        void whenReorderedThenCommutativeMatchesButOrderedDoesNot() {
            assertThat(commutative(1L, 2L, 3L)).isEqualTo(commutative(1L, 3L, 2L));
            assertThat(ordered(1L, 2L, 3L)).isNotEqualTo(ordered(1L, 3L, 2L));
        }
    }

}
