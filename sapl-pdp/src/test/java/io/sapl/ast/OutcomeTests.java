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
package io.sapl.ast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Outcome")
class OutcomeTests {

    @ParameterizedTest(name = "{0} contains {1}")
    @MethodSource("containsTrueCases")
    void whenOutcomeIncludesEffect_thenContainsReturnsTrue(Outcome outcome, Effect effect) {
        assertThat(outcome.contains(effect)).isTrue();
    }

    @ParameterizedTest(name = "{0} does not contain {1}")
    @MethodSource("containsFalseCases")
    void whenOutcomeExcludesEffect_thenContainsReturnsFalse(Outcome outcome, Effect effect) {
        assertThat(outcome.contains(effect)).isFalse();
    }

    static Stream<Arguments> containsTrueCases() {
        return Stream.of(arguments(Outcome.PERMIT, Effect.PERMIT), arguments(Outcome.DENY, Effect.DENY),
                arguments(Outcome.SUSPEND, Effect.SUSPEND), arguments(Outcome.PERMIT_OR_DENY, Effect.PERMIT),
                arguments(Outcome.PERMIT_OR_DENY, Effect.DENY), arguments(Outcome.PERMIT_OR_SUSPEND, Effect.PERMIT),
                arguments(Outcome.PERMIT_OR_SUSPEND, Effect.SUSPEND), arguments(Outcome.DENY_OR_SUSPEND, Effect.DENY),
                arguments(Outcome.DENY_OR_SUSPEND, Effect.SUSPEND),
                arguments(Outcome.PERMIT_OR_DENY_OR_SUSPEND, Effect.PERMIT),
                arguments(Outcome.PERMIT_OR_DENY_OR_SUSPEND, Effect.DENY),
                arguments(Outcome.PERMIT_OR_DENY_OR_SUSPEND, Effect.SUSPEND));
    }

    static Stream<Arguments> containsFalseCases() {
        return Stream.of(arguments(Outcome.PERMIT, Effect.DENY), arguments(Outcome.PERMIT, Effect.SUSPEND),
                arguments(Outcome.DENY, Effect.PERMIT), arguments(Outcome.DENY, Effect.SUSPEND),
                arguments(Outcome.SUSPEND, Effect.PERMIT), arguments(Outcome.SUSPEND, Effect.DENY),
                arguments(Outcome.PERMIT_OR_DENY, Effect.SUSPEND), arguments(Outcome.PERMIT_OR_SUSPEND, Effect.DENY),
                arguments(Outcome.DENY_OR_SUSPEND, Effect.PERMIT));
    }

    @ParameterizedTest(name = "combine({0}, {1}) = {2}")
    @MethodSource("combineCases")
    void whenCombiningOutcomes_thenResultIsTheUnion(Outcome a, Outcome b, Outcome expected) {
        assertThat(Outcome.combine(a, b)).isEqualTo(expected);
    }

    static Stream<Arguments> combineCases() {
        return Stream.of(arguments(Outcome.PERMIT, Outcome.PERMIT, Outcome.PERMIT),
                arguments(Outcome.PERMIT, Outcome.DENY, Outcome.PERMIT_OR_DENY),
                arguments(Outcome.PERMIT, Outcome.SUSPEND, Outcome.PERMIT_OR_SUSPEND),
                arguments(Outcome.DENY, Outcome.SUSPEND, Outcome.DENY_OR_SUSPEND),
                arguments(Outcome.PERMIT_OR_DENY, Outcome.SUSPEND, Outcome.PERMIT_OR_DENY_OR_SUSPEND),
                arguments(Outcome.PERMIT_OR_SUSPEND, Outcome.DENY, Outcome.PERMIT_OR_DENY_OR_SUSPEND),
                arguments(Outcome.DENY_OR_SUSPEND, Outcome.PERMIT, Outcome.PERMIT_OR_DENY_OR_SUSPEND),
                arguments(Outcome.PERMIT_OR_DENY_OR_SUSPEND, Outcome.PERMIT, Outcome.PERMIT_OR_DENY_OR_SUSPEND));
    }

    @Test
    @DisplayName("combine is commutative")
    void whenCombineCalledInBothOrders_thenResultsMatch() {
        for (var a : Outcome.values()) {
            for (var b : Outcome.values()) {
                assertThat(Outcome.combine(a, b)).isEqualTo(Outcome.combine(b, a));
            }
        }
    }

    @Test
    @DisplayName("combine is idempotent")
    void whenCombineCalledWithSameOutcome_thenResultIsThatOutcome() {
        for (var o : Outcome.values()) {
            assertThat(Outcome.combine(o, o)).isEqualTo(o);
        }
    }

    @ParameterizedTest(name = "{0}.isAmbiguous() = {1}")
    @MethodSource("ambiguityCases")
    void whenIsAmbiguousCalled_thenReturnsTrueForMultiEffectOutcomes(Outcome outcome, boolean expected) {
        assertThat(outcome.isAmbiguous()).isEqualTo(expected);
    }

    static Stream<Arguments> ambiguityCases() {
        return Stream.of(arguments(Outcome.PERMIT, false), arguments(Outcome.DENY, false),
                arguments(Outcome.SUSPEND, false), arguments(Outcome.PERMIT_OR_DENY, true),
                arguments(Outcome.PERMIT_OR_SUSPEND, true), arguments(Outcome.DENY_OR_SUSPEND, true),
                arguments(Outcome.PERMIT_OR_DENY_OR_SUSPEND, true));
    }

}
