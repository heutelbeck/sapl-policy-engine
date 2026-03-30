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
import org.junit.jupiter.api.Test;

import static io.sapl.compiler.index.IndexTestFixtures.negativeLiteral;
import static io.sapl.compiler.index.IndexTestFixtures.positiveLiteral;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Literal")
class LiteralTests {

    @Test
    @DisplayName("positive and negative literal of same predicate are not equal")
    void whenDifferentNegationThenNotEqual() {
        assertThat(positiveLiteral(1L)).isNotEqualTo(negativeLiteral(1L));
    }

    @Test
    @DisplayName("two positive literals of same predicate are equal")
    void whenSamePredicateAndNegationThenEqual() {
        assertThat(positiveLiteral(1L)).isEqualTo(positiveLiteral(1L)).hasSameHashCodeAs(positiveLiteral(1L));
    }

    @Test
    @DisplayName("negating a positive literal produces a negative literal")
    void whenNegateThenFlipped() {
        assertThat(positiveLiteral(1L).negate()).isEqualTo(negativeLiteral(1L));
    }

    @Test
    @DisplayName("double negation returns to original")
    void whenDoubleNegateThenOriginal() {
        assertThat(positiveLiteral(1L).negate().negate()).isEqualTo(positiveLiteral(1L));
    }

    @Test
    @DisplayName("literals of different predicates are not equal")
    void whenDifferentPredicatesThenNotEqual() {
        assertThat(positiveLiteral(1L)).isNotEqualTo(positiveLiteral(2L));
    }

}
