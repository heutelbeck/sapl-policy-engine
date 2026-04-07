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

import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.sapl.compiler.index.IndexTestFixtures.predicate;
import static io.sapl.compiler.index.smtdd.SmtddTestFixtures.stubOperand;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EqualityGroup")
class EqualityGroupTests {

    @Nested
    @DisplayName("compact")
    class CompactTests {

        @Test
        @DisplayName("NE-only group creates explicit branch for excluded value")
        void whenNeOnlyThenExplicitBranchForExcludedValue() {
            // Formula 0: != "x", Formula 1: != "y"
            // No EQ branches at all. Compact should create explicit branches
            // for "x" and "y" where the respective NE formula is absent.
            val group = new EqualityGroup(stubOperand(100L));
            group.addExclude(Value.of("x"), 0, predicate(1L));
            group.addExclude(Value.of("y"), 1, predicate(2L));

            val bucket = new BitSet();
            bucket.set(0);
            bucket.set(1);
            val result = group.compact(bucket);

            // Branch "x": f0 is excluded (!=x is FALSE when value IS x),
            // f1 is included (!=y is TRUE when value is x)
            assertThat(result.branchFormulas()).containsKey(Value.of("x"));
            assertThat(result.branchFormulas().get(Value.of("x")).get(0)).isFalse();
            assertThat(result.branchFormulas().get(Value.of("x")).get(1)).isTrue();

            // Branch "y": f1 is excluded, f0 is included
            assertThat(result.branchFormulas()).containsKey(Value.of("y"));
            assertThat(result.branchFormulas().get(Value.of("y")).get(0)).isTrue();
            assertThat(result.branchFormulas().get(Value.of("y")).get(1)).isFalse();

            // Default: both NE formulas satisfied (unknown value != x and != y)
            assertThat(result.defaultFormulas().get(0)).isTrue();
            assertThat(result.defaultFormulas().get(1)).isTrue();
        }

        @Test
        @DisplayName("mixed EQ and NE: NE formula excluded from its own EQ branch")
        void whenMixedEqAndNeThenNeExcludedFromOwnBranch() {
            // Formula 0: == "a", Formula 1: != "a"
            val group = new EqualityGroup(stubOperand(100L));
            group.addEquals(Value.of("a"), 0, predicate(1L));
            group.addExclude(Value.of("a"), 1, predicate(2L));

            val bucket = new BitSet();
            bucket.set(0);
            bucket.set(1);
            val result = group.compact(bucket);

            // Branch "a": f0 matches (==a), f1 does NOT match (!=a is FALSE)
            assertThat(result.branchFormulas().get(Value.of("a")).get(0)).isTrue();
            assertThat(result.branchFormulas().get(Value.of("a")).get(1)).isFalse();

            // Default: f0 does not match (not ==a), f1 matches (!=a for unknown)
            assertThat(result.defaultFormulas().get(0)).isFalse();
            assertThat(result.defaultFormulas().get(1)).isTrue();
        }

        @Test
        @DisplayName("unconstrained formulas appear in all branches and default")
        void whenUnconstrainedFormulaThenInAllBranches() {
            // Formula 0: == "a", Formula 1: == "b", Formula 2: unconstrained
            val group = new EqualityGroup(stubOperand(100L));
            group.addEquals(Value.of("a"), 0, predicate(1L));
            group.addEquals(Value.of("b"), 1, predicate(2L));

            val bucket = new BitSet();
            bucket.set(0);
            bucket.set(1);
            bucket.set(2); // f2 is not in any EQ or NE
            val result = group.compact(bucket);

            // f2 should be in branch "a", branch "b", and default
            assertThat(result.branchFormulas().get(Value.of("a")).get(2)).isTrue();
            assertThat(result.branchFormulas().get(Value.of("b")).get(2)).isTrue();
            assertThat(result.defaultFormulas().get(2)).isTrue();
        }

        @Test
        @DisplayName("doesNotSplit returns true when all formulas are unconstrained")
        void whenAllUnconstrainedThenDoesNotSplit() {
            val group = new EqualityGroup(stubOperand(100L));
            // Group has constants but none in the current bucket
            group.addEquals(Value.of("a"), 5, predicate(1L));

            val bucket = new BitSet();
            bucket.set(0);
            bucket.set(1);
            val result = group.compact(bucket);

            assertThat(result.doesNotSplit(bucket)).isTrue();
        }

        @Test
        @DisplayName("doesNotSplit returns false when bucket formulas are split across branches")
        void whenFormulasConstrainedThenDoesNotSplitIsFalse() {
            val group = new EqualityGroup(stubOperand(100L));
            group.addEquals(Value.of("a"), 0, predicate(1L));
            group.addEquals(Value.of("b"), 1, predicate(2L));

            val bucket = new BitSet();
            bucket.set(0);
            bucket.set(1);
            val result = group.compact(bucket);

            assertThat(result.doesNotSplit(bucket)).isFalse();
        }

        @Test
        @DisplayName("formulas outside bucket are excluded from compacted result")
        void whenFormulaOutsideBucketThenExcluded() {
            val group = new EqualityGroup(stubOperand(100L));
            group.addEquals(Value.of("a"), 0, predicate(1L));
            group.addEquals(Value.of("b"), 1, predicate(2L));
            group.addEquals(Value.of("c"), 2, predicate(3L));

            // Only formulas 0 and 1 in bucket
            val bucket = new BitSet();
            bucket.set(0);
            bucket.set(1);
            val result = group.compact(bucket);

            // "c" branch should be empty (formula 2 not in bucket)
            assertThat(result.branchFormulas()).doesNotContainKey(Value.of("c"));
        }
    }

    @Nested
    @DisplayName("accumulation")
    class AccumulationTests {

        @Test
        @DisplayName("constantCount counts both EQ and NE distinct constants")
        void whenEqAndNeThenConstantCountIsSum() {
            val group = new EqualityGroup(stubOperand(100L));
            group.addEquals(Value.of("a"), 0, predicate(1L));
            group.addEquals(Value.of("b"), 1, predicate(2L));
            group.addExclude(Value.of("c"), 2, predicate(3L));

            assertThat(group.constantCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("tentativePredicates tracks all added predicates")
        void whenPredicatesAddedThenTracked() {
            val group = new EqualityGroup(stubOperand(100L));
            val pred1 = predicate(1L);
            val pred2 = predicate(2L);
            group.addEquals(Value.of("a"), 0, pred1);
            group.addExclude(Value.of("b"), 1, pred2);

            assertThat(group.getTentativePredicates()).containsExactlyInAnyOrder(pred1, pred2);
        }
    }

}
