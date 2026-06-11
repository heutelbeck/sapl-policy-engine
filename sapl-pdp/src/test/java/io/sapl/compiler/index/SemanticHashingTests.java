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

import io.sapl.api.model.Value;
import io.sapl.ast.BinaryOperatorType;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static io.sapl.compiler.index.SemanticHashing.*;
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

    @Nested
    @DisplayName("textHash")
    class TextHashTests {

        @Test
        @DisplayName("strings colliding under String.hashCode do not collide")
        void whenStringsCollideUnderJavaHashCodeThenTextHashDiffers() {
            // "Aa" and "BB" share the same 32-bit String.hashCode (2112).
            assertThat("Aa".hashCode()).isEqualTo("BB".hashCode());
            assertThat(textHash("Aa")).isNotEqualTo(textHash("BB"));
        }

        @Test
        @DisplayName("equal strings produce equal hash")
        void whenEqualStringsThenEqualHash() {
            assertThat(textHash("alice")).isEqualTo(textHash("alice"));
        }

        @Test
        @DisplayName("null hashes to a fixed constant")
        void whenNullThenFixedConstant() {
            assertThat(textHash(null)).isEqualTo(textHash(null));
        }
    }

    @Nested
    @DisplayName("valueHash - consistency with value equality")
    class ValueHashConsistencyTests {

        @Test
        @DisplayName("numerically equal numbers of different scale produce equal hash")
        void whenNumbersNumericallyEqualThenEqualHash() {
            val oneTenth     = Value.of(new BigDecimal("1.0"));
            val oneHundredth = Value.of(new BigDecimal("1.00"));
            assertThat(oneTenth).isEqualTo(oneHundredth);
            assertThat(valueHash(oneTenth)).isEqualTo(valueHash(oneHundredth));
        }

        @Test
        @DisplayName("equal text values produce equal hash")
        void whenEqualTextThenEqualHash() {
            assertThat(valueHash(Value.of("alice"))).isEqualTo(valueHash(Value.of("alice")));
        }

        @Test
        @DisplayName("equal objects with different key order produce equal hash")
        void whenObjectsDifferByKeyOrderThenEqualHash() {
            val first  = Value.ofJson("{\"a\":1,\"b\":2}");
            val second = Value.ofJson("{\"b\":2,\"a\":1}");
            assertThat(first).isEqualTo(second);
            assertThat(valueHash(first)).isEqualTo(valueHash(second));
        }
    }

    @Nested
    @DisplayName("valueHash - sensitivity to differences")
    class ValueHashSensitivityTests {

        @Test
        @DisplayName("text values colliding under String.hashCode do not collide")
        void whenTextValuesCollideUnderJavaHashCodeThenValueHashDiffers() {
            assertThat(valueHash(Value.of("Aa"))).isNotEqualTo(valueHash(Value.of("BB")));
        }

        @Test
        @DisplayName("distinct numbers produce distinct hash")
        void whenDistinctNumbersThenDistinctHash() {
            assertThat(valueHash(Value.of(1L))).isNotEqualTo(valueHash(Value.of(2L)));
        }

        @Test
        @DisplayName("a text value and a number with the same lexical form do not collide")
        void whenTextAndNumberShareLexicalFormThenDistinctHash() {
            assertThat(valueHash(Value.of("1"))).isNotEqualTo(valueHash(Value.of(1L)));
        }

        @Test
        @DisplayName("array element order changes hash")
        void whenArrayOrderDiffersThenDistinctHash() {
            assertThat(valueHash(Value.ofJson("[1,2]"))).isNotEqualTo(valueHash(Value.ofJson("[2,1]")));
        }
    }

    @Nested
    @DisplayName("binaryOp")
    class BinaryOpTests {

        @Test
        @DisplayName("commutative operator produces same hash regardless of operand order")
        void whenCommutativeOpThenOrderIndependent() {
            assertThat(binaryOp(BinaryOperatorType.EQ, 1L, 2L)).isEqualTo(binaryOp(BinaryOperatorType.EQ, 2L, 1L));
        }

        @Test
        @DisplayName("non-commutative operator produces different hash for different order")
        void whenNonCommutativeOpThenOrderDependent() {
            assertThat(binaryOp(BinaryOperatorType.LT, 1L, 2L)).isNotEqualTo(binaryOp(BinaryOperatorType.LT, 2L, 1L));
        }

        @Test
        @DisplayName("different operators produce different hashes")
        void whenDifferentOpsThenDifferentHash() {
            assertThat(binaryOp(BinaryOperatorType.EQ, 1L, 2L)).isNotEqualTo(binaryOp(BinaryOperatorType.NE, 1L, 2L));
        }
    }

}
