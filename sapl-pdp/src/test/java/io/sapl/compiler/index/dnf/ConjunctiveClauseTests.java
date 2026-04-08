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
package io.sapl.compiler.index.dnf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.sapl.compiler.index.IndexTestFixtures.negativeLiteral;
import static io.sapl.compiler.index.IndexTestFixtures.positiveLiteral;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConjunctiveClause")
class ConjunctiveClauseTests {

    @Nested
    @DisplayName("set semantics for equality")
    class SetSemanticsTests {

        @Test
        @DisplayName("clauses with same literals in different order are equal")
        void whenSameLiteralsDifferentOrderThenEqual() {
            var a = new ConjunctiveClause(List.of(positiveLiteral(1L), positiveLiteral(2L)));
            var b = new ConjunctiveClause(List.of(positiveLiteral(2L), positiveLiteral(1L)));
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        @DisplayName("clauses with different literals are not equal")
        void whenDifferentLiteralsThenNotEqual() {
            var a = new ConjunctiveClause(List.of(positiveLiteral(1L), positiveLiteral(2L)));
            var b = new ConjunctiveClause(List.of(positiveLiteral(1L), positiveLiteral(3L)));
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("clause is not equal to non-clause")
        void whenComparedToOtherTypeThenNotEqual() {
            assertThat(new ConjunctiveClause(positiveLiteral(1L))).isNotEqualTo("not a clause");
        }
    }

    @Nested
    @DisplayName("subset checking")
    class SubsetTests {

        @Test
        @DisplayName("single-literal clause is subset of two-literal clause containing it")
        void whenSubsetThenTrue() {
            var small = new ConjunctiveClause(List.of(positiveLiteral(1L)));
            var large = new ConjunctiveClause(List.of(positiveLiteral(1L), positiveLiteral(2L)));
            assertThat(small.isSubsetOf(large)).isTrue();
        }

        @Test
        @DisplayName("clause is not subset of clause with different literals")
        void whenNotSubsetThenFalse() {
            var a = new ConjunctiveClause(List.of(positiveLiteral(1L)));
            var b = new ConjunctiveClause(List.of(positiveLiteral(2L)));
            assertThat(a.isSubsetOf(b)).isFalse();
        }

        @Test
        @DisplayName("clause is subset of itself")
        void whenSameClauseThenSubset() {
            var clause = new ConjunctiveClause(List.of(positiveLiteral(1L), negativeLiteral(2L)));
            assertThat(clause.isSubsetOf(clause)).isTrue();
        }
    }

    @Nested
    @DisplayName("unsatisfiability detection")
    class UnsatisfiabilityTests {

        @Test
        @DisplayName("clause with p and !p is unsatisfiable")
        void whenComplementaryLiteralsThenUnsatisfiable() {
            var clause = new ConjunctiveClause(List.of(positiveLiteral(1L), negativeLiteral(1L)));
            assertThat(clause.isUnsatisfiable()).isTrue();
        }

        @Test
        @DisplayName("clause with only positive literals is satisfiable")
        void whenNoComplementaryLiteralsThenSatisfiable() {
            var clause = new ConjunctiveClause(List.of(positiveLiteral(1L), positiveLiteral(2L)));
            assertThat(clause.isUnsatisfiable()).isFalse();
        }

        @Test
        @DisplayName("clause with different predicates negated is satisfiable")
        void whenDifferentPredicatesNegatedThenSatisfiable() {
            var clause = new ConjunctiveClause(List.of(positiveLiteral(1L), negativeLiteral(2L)));
            assertThat(clause.isUnsatisfiable()).isFalse();
        }

        @Test
        @DisplayName("empty clause is satisfiable")
        void whenEmptyThenSatisfiable() {
            assertThat(new ConjunctiveClause(List.of()).isUnsatisfiable()).isFalse();
        }
    }

    @Nested
    @DisplayName("size and emptiness")
    class SizeTests {

        @Test
        @DisplayName("empty clause has size 0 and is empty")
        void whenEmptyThenSizeZero() {
            var clause = new ConjunctiveClause(List.of());
            assertThat(clause.size()).isZero();
            assertThat(clause.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("single-literal clause has size 1")
        void whenSingleLiteralThenSizeOne() {
            assertThat(new ConjunctiveClause(positiveLiteral(1L)).size()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("literals list is immutable")
        void whenCreatedThenImmutable() {
            var clause = new ConjunctiveClause(List.of(positiveLiteral(1L)));
            assertThat(clause.literals()).isUnmodifiable();
        }
    }

}
