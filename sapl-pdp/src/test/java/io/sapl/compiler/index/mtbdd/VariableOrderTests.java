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

import java.util.List;

import io.sapl.api.model.BooleanExpression;
import io.sapl.api.model.BooleanExpression.And;
import io.sapl.api.model.BooleanExpression.Constant;
import io.sapl.api.model.BooleanExpression.Not;
import io.sapl.api.model.BooleanExpression.Or;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.sapl.compiler.index.IndexTestFixtures.atom;
import static io.sapl.compiler.index.IndexTestFixtures.predicate;
import static org.assertj.core.api.Assertions.assertThat;
import io.sapl.compiler.expressions.SaplCompilerException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("VariableOrder")
class VariableOrderTests {

    private static final boolean PRINT_RESULTS = false;

    @Nested
    @DisplayName("ordering by formula frequency")
    class FrequencyOrderingTests {

        @Test
        @DisplayName("predicate appearing in more formulas gets lower level")
        void whenHigherFrequencyThenLowerLevel() {
            // p1 appears in 3 formulas, p2 in 2, p3 in 1
            val expressions = List.<BooleanExpression>of(atom(1L),                              // f0: p1
                    new Or(atom(1L), atom(2L)),             // f1: p1 OR p2
                    new And(atom(1L), atom(2L), atom(3L))   // f2: p1 AND p2 AND p3
            );

            val order = VariableOrder.fromExpressions(expressions);

            printOrder("frequency: f0=p1, f1=p1 OR p2, f2=p1 AND p2 AND p3", order);

            assertThat(order.levelOf(predicate(1L))).isZero();       // p1: 3 formulas -> level 0
            assertThat(order.levelOf(predicate(2L))).isEqualTo(1);   // p2: 2 formulas -> level 1
            assertThat(order.levelOf(predicate(3L))).isEqualTo(2);   // p3: 1 formula -> level 2
        }

        @Test
        @DisplayName("predicates with same frequency are ordered by semantic hash")
        void whenSameFrequencyThenOrderedByHash() {
            val expressions = List.<BooleanExpression>of(new Or(atom(10L), atom(20L)),   // f0: p10 OR p20
                    new Or(atom(10L), atom(20L))    // f1: p10 OR p20 (same predicates)
            );

            val order = VariableOrder.fromExpressions(expressions);

            printOrder("tie-breaking: f0=p10 OR p20, f1=p10 OR p20", order);

            // Both appear in 2 formulas, so ordered by hash: 10 < 20
            assertThat(order.levelOf(predicate(10L))).isZero();
            assertThat(order.levelOf(predicate(20L))).isEqualTo(1);
        }

        @Test
        @DisplayName("negated predicates are counted the same as positive")
        void whenNegatedThenSameCount() {
            val expressions = List.<BooleanExpression>of(atom(1L),                  // f0: p1
                    new Not(atom(1L)),         // f1: NOT p1 (still references p1)
                    atom(2L)                   // f2: p2
            );

            val order = VariableOrder.fromExpressions(expressions);

            printOrder("negation: f0=p1, f1=NOT p1, f2=p2", order);

            assertThat(order.levelOf(predicate(1L))).isZero();       // p1: 2 formulas
            assertThat(order.levelOf(predicate(2L))).isEqualTo(1);   // p2: 1 formula
        }

        @Test
        @DisplayName("predicate appearing multiple times in one formula counts once")
        void whenDuplicateInFormulaThenCountedOnce() {
            // p1 appears twice in f0 but should count as 1 formula
            val expressions = List.<BooleanExpression>of(new And(atom(1L), atom(1L)),   // f0: p1 AND p1
                    atom(2L),                      // f1: p2
                    atom(2L)                       // f2: p2
            );

            val order = VariableOrder.fromExpressions(expressions);

            printOrder("dedup: f0=p1 AND p1, f1=p2, f2=p2", order);

            assertThat(order.levelOf(predicate(2L))).isZero();       // p2: 2 formulas
            assertThat(order.levelOf(predicate(1L))).isEqualTo(1);   // p1: 1 formula
        }
    }

    @Nested
    @DisplayName("structure")
    class StructureTests {

        @Test
        @DisplayName("size matches number of unique predicates")
        void whenMultiplePredicatesThenCorrectSize() {
            val expressions = List.<BooleanExpression>of(new Or(atom(1L), atom(2L), atom(3L)));
            val order       = VariableOrder.fromExpressions(expressions);
            assertThat(order.size()).isEqualTo(3);
        }

        @Test
        @DisplayName("predicateAt returns correct predicate for level")
        void whenPredicateAtThenCorrectPredicate() {
            val expressions = List.<BooleanExpression>of(atom(1L),                          // f0: p1
                    new Or(atom(1L), atom(2L))          // f1: p1 OR p2
            );
            val order       = VariableOrder.fromExpressions(expressions);
            assertThat(order.predicateAt(0)).isEqualTo(predicate(1L));
            assertThat(order.predicateAt(1)).isEqualTo(predicate(2L));
        }

        @Test
        @DisplayName("constants produce empty order")
        void whenOnlyConstantsThenEmptyOrder() {
            val expressions = List.<BooleanExpression>of(new Constant(true), new Constant(false));
            val order       = VariableOrder.fromExpressions(expressions);
            assertThat(order.size()).isZero();
        }

        @Test
        @DisplayName("unknown predicate throws")
        void whenUnknownPredicateThenThrows() {
            val order = VariableOrder.fromExpressions(List.of(atom(1L)));
            assertThatThrownBy(() -> order.levelOf(predicate(99L))).isInstanceOf(SaplCompilerException.class);
        }

    }

    @Nested
    @DisplayName("visual: realistic scenario")
    class VisualTests {

        @Test
        @DisplayName("hospital-like ordering with shared and unique predicates")
        void whenHospitalLikeThenSharedPredicatesFirst() {
            // Simulate: role check (p1) in many policies, dept check (p2) in several,
            // specific resource checks (p3-p6) in few
            val expressions = List.<BooleanExpression>of(new And(atom(1L), atom(3L)),               // f0: role AND
                                                                                                    // resource-A
                    new And(atom(1L), atom(4L)),               // f1: role AND resource-B
                    new And(atom(1L), atom(2L), atom(5L)),     // f2: role AND dept AND resource-C
                    new And(atom(1L), atom(2L), atom(6L)),     // f3: role AND dept AND resource-D
                    new And(atom(2L), atom(6L))                // f4: dept AND resource-D
            );

            val order = VariableOrder.fromExpressions(expressions);

            printOrder("hospital-like: role(p1) x4, dept(p2) x3, resource-D(p6) x2, rest x1", order);

            // p1 (role): 4 formulas -> level 0
            // p2 (dept): 3 formulas -> level 1
            // p6 (resource-D): 2 formulas -> level 2
            // p3, p4, p5: 1 formula each -> levels 3-5 (by hash)
            assertThat(order.levelOf(predicate(1L))).isZero();
            assertThat(order.levelOf(predicate(2L))).isEqualTo(1);
            assertThat(order.levelOf(predicate(6L))).isEqualTo(2);
        }
    }

    private static void printOrder(String header, VariableOrder order) {
        if (!PRINT_RESULTS) {
            return;
        }
        System.out.println("\n=== " + header + " ===");
        for (var level = 0; level < order.size(); level++) {
            val predicate = order.predicateAt(level);
            System.out.printf("  level %d: p%d (hash=%d)%n", level, predicate.semanticHash(), predicate.semanticHash());
        }
    }

}
