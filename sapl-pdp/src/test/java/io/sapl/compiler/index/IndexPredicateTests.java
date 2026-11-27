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

import io.sapl.api.model.IndexPredicate;
import io.sapl.api.model.PureOperator;
import io.sapl.util.SaplTesting;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The index keys predicates by {@link IndexPredicate}. Its equality must verify
 * the operator structure, not just the semantic hash, so that a hash collision
 * cannot collapse two different predicates into one and change which documents
 * the index reports as applicable.
 */
@DisplayName("IndexPredicate identity")
class IndexPredicateTests {

    private static PureOperator compile(String expression) {
        return (PureOperator) SaplTesting.compileExpression(expression);
    }

    @Nested
    @DisplayName("semanticEquals")
    class SemanticEqualsTests {

        @Test
        @DisplayName("structurally equal operators from different source positions are equal")
        void whenSameStructureDifferentLocationThenEqual() {
            val first  = compile("subject.role == \"admin\"");
            val second = compile("      subject.role == \"admin\"");
            assertThat(first.semanticHash()).isEqualTo(second.semanticHash());
            assertThat(first.semanticEquals(second)).isTrue();
        }

        @Test
        @DisplayName("operators differing only in a text constant are not equal")
        void whenDifferentConstantThenNotEqual() {
            val first  = compile("subject.role == \"admin\"");
            val second = compile("subject.role == \"user\"");
            assertThat(first.semanticEquals(second)).isFalse();
        }

        @Test
        @DisplayName("operators differing in operator kind are not equal")
        void whenDifferentOperatorThenNotEqual() {
            val first  = compile("subject.age > 18");
            val second = compile("subject.age < 18");
            assertThat(first.semanticEquals(second)).isFalse();
        }
    }

    @Nested
    @DisplayName("predicate equality")
    class PredicateEqualityTests {

        @Test
        @DisplayName("equal operators with equal hash produce equal predicates")
        void whenEqualOperatorsThenPredicatesEqualAndShareBucket() {
            val operator  = compile("subject.role == \"admin\"");
            val duplicate = compile("subject.role == \"admin\"");
            val predicate = new IndexPredicate(operator.semanticHash(), operator);
            val other     = new IndexPredicate(duplicate.semanticHash(), duplicate);

            assertThat(predicate).isEqualTo(other).hasSameHashCodeAs(other);
        }

        @Test
        @DisplayName("a hash collision between different operators does not make predicates equal")
        void whenHashCollidesButOperatorsDifferThenPredicatesNotEqual() {
            val admin         = compile("subject.role == \"admin\"");
            val user          = compile("subject.role == \"user\"");
            val collidingHash = 42L;
            val predicate     = new IndexPredicate(collidingHash, admin);
            val collision     = new IndexPredicate(collidingHash, user);

            assertThat(predicate).hasSameHashCodeAs(collision).isNotEqualTo(collision);
        }
    }
}
