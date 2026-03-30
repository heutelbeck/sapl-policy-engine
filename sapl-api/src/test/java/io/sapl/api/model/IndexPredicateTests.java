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
package io.sapl.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IndexPredicate")
class IndexPredicateTests {

    @Test
    @DisplayName("predicates with same hash are equal regardless of operator instance")
    void whenSameHashThenEqual() {
        var a = predicate(42L);
        var b = predicate(42L);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    @DisplayName("predicates with different hashes are not equal")
    void whenDifferentHashThenNotEqual() {
        assertThat(predicate(1L)).isNotEqualTo(predicate(2L));
    }

    @Test
    @DisplayName("predicate is equal to itself")
    void whenSameInstanceThenEqual() {
        var p = predicate(99L);
        assertThat(p).isEqualTo(p);
    }

    @Test
    @DisplayName("predicate is not equal to non-predicate")
    void whenComparedToOtherTypeThenNotEqual() {
        assertThat(predicate(1L)).isNotEqualTo("not a predicate");
    }

    private static IndexPredicate predicate(long hash) {
        return new IndexPredicate(hash, new PureOperator() {
            @Override
            public Value evaluate(EvaluationContext ctx) {
                return Value.TRUE;
            }

            @Override
            public SourceLocation location() {
                return new SourceLocation("test", "", 0, 0, 1, 1, 1, 1);
            }

            @Override
            public boolean isDependingOnSubscription() {
                return false;
            }

            @Override
            public long semanticHash() {
                return hash;
            }
        });
    }

}
