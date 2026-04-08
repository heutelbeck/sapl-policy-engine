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

@DisplayName("DisjunctiveFormula")
class DisjunctiveFormulaTests {

    @Nested
    @DisplayName("constants")
    class ConstantTests {

        @Test
        @DisplayName("TRUE is always satisfied")
        void whenTrueThenSatisfied() {
            assertThat(DisjunctiveFormula.TRUE).satisfies(f -> {
                assertThat(f.isTrue()).isTrue();
                assertThat(f.isFalse()).isFalse();
            });
        }

        @Test
        @DisplayName("FALSE is never satisfied")
        void whenFalseThenNotSatisfied() {
            assertThat(DisjunctiveFormula.FALSE).satisfies(f -> {
                assertThat(f.isFalse()).isTrue();
                assertThat(f.isTrue()).isFalse();
            });
        }
    }

    @Nested
    @DisplayName("set semantics for equality")
    class SetSemanticsTests {

        @Test
        @DisplayName("formulas with same clauses in different order are equal")
        void whenSameClausesDifferentOrderThenEqual() {
            var c1 = new ConjunctiveClause(positiveLiteral(1L));
            var c2 = new ConjunctiveClause(positiveLiteral(2L));
            var a  = new DisjunctiveFormula(List.of(c1, c2));
            var b  = new DisjunctiveFormula(List.of(c2, c1));
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        @DisplayName("formulas with different clauses are not equal")
        void whenDifferentClausesThenNotEqual() {
            var a = new DisjunctiveFormula(new ConjunctiveClause(positiveLiteral(1L)));
            var b = new DisjunctiveFormula(new ConjunctiveClause(positiveLiteral(2L)));
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("formula is equal to itself")
        void whenSameInstanceThenEqual() {
            var f = new DisjunctiveFormula(new ConjunctiveClause(positiveLiteral(1L)));
            assertThat(f).isEqualTo(f);
        }

        @Test
        @DisplayName("formula is not equal to non-formula")
        void whenComparedToOtherTypeThenNotEqual() {
            assertThat(DisjunctiveFormula.TRUE).isNotEqualTo("not a formula");
        }
    }

    @Nested
    @DisplayName("reduction")
    class ReductionTests {

        @Test
        @DisplayName("duplicate clauses are removed")
        void whenDuplicateClausesThenReduced() {
            var clause  = new ConjunctiveClause(positiveLiteral(1L));
            var formula = new DisjunctiveFormula(List.of(clause, clause));
            assertThat(formula.reduce().clauses()).hasSize(1);
        }

        @Test
        @DisplayName("subsumed clause is removed: (p1) subsumes (p1 AND p2)")
        void whenSubsumedClauseThenRemoved() {
            var small   = new ConjunctiveClause(List.of(positiveLiteral(1L)));
            var large   = new ConjunctiveClause(List.of(positiveLiteral(1L), positiveLiteral(2L)));
            var formula = new DisjunctiveFormula(List.of(small, large));
            var reduced = formula.reduce();
            assertThat(reduced.clauses()).containsExactly(small);
        }

        @Test
        @DisplayName("unsatisfiable clause (p AND !p) is removed")
        void whenUnsatisfiableClauseThenRemoved() {
            var satisfiable   = new ConjunctiveClause(List.of(positiveLiteral(1L)));
            var unsatisfiable = new ConjunctiveClause(List.of(positiveLiteral(2L), negativeLiteral(2L)));
            var formula       = new DisjunctiveFormula(List.of(satisfiable, unsatisfiable));
            var reduced       = formula.reduce();
            assertThat(reduced.clauses()).containsExactly(satisfiable);
        }

        @Test
        @DisplayName("formula with only unsatisfiable clauses reduces to FALSE")
        void whenAllUnsatisfiableThenFalse() {
            var unsatisfiable = new ConjunctiveClause(List.of(positiveLiteral(1L), negativeLiteral(1L)));
            var formula       = new DisjunctiveFormula(List.of(unsatisfiable));
            assertThat(formula.reduce()).isEqualTo(DisjunctiveFormula.FALSE);
        }

        @Test
        @DisplayName("non-subsumed clauses are preserved")
        void whenNoSubsumptionThenAllPreserved() {
            var c1      = new ConjunctiveClause(positiveLiteral(1L));
            var c2      = new ConjunctiveClause(positiveLiteral(2L));
            var formula = new DisjunctiveFormula(List.of(c1, c2));
            assertThat(formula.reduce().clauses()).hasSize(2);
        }

        @Test
        @DisplayName("FALSE reduces to itself")
        void whenFalseThenReducesToFalse() {
            assertThat(DisjunctiveFormula.FALSE.reduce()).isEqualTo(DisjunctiveFormula.FALSE);
        }

        @Test
        @DisplayName("TRUE reduces to itself")
        void whenTrueThenReducesToTrue() {
            assertThat(DisjunctiveFormula.TRUE.reduce()).isEqualTo(DisjunctiveFormula.TRUE);
        }
    }

    @Nested
    @DisplayName("immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("clauses list is immutable")
        void whenCreatedThenImmutable() {
            assertThat(DisjunctiveFormula.TRUE.clauses()).isUnmodifiable();
        }
    }

}
