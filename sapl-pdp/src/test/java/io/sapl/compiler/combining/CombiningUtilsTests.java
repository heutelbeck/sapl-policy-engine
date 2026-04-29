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
package io.sapl.compiler.combining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.pdp.Decision;
import io.sapl.ast.Outcome;

@DisplayName("CombiningUtils")
class CombiningUtilsTests {

    @Nested
    @DisplayName("decisionToOutcome")
    class DecisionToOutcome {

        @ParameterizedTest(name = "Decision.{0} -> Outcome.{1}")
        @MethodSource("concreteDecisionCases")
        void whenConcreteDecision_thenMapsToCorrespondingOutcome(Decision decision, Outcome expected) {
            assertThat(CombiningUtils.decisionToOutcome(decision)).isEqualTo(expected);
        }

        static Stream<Arguments> concreteDecisionCases() {
            return Stream.of(arguments(Decision.PERMIT, Outcome.PERMIT), arguments(Decision.DENY, Outcome.DENY),
                    arguments(Decision.SUSPEND, Outcome.SUSPEND));
        }

        @ParameterizedTest(name = "Decision.{0} throws")
        @MethodSource("nonConcreteDecisionCases")
        void whenNonConcreteDecision_thenThrowsIllegalArgumentException(Decision decision) {
            assertThatThrownBy(() -> CombiningUtils.decisionToOutcome(decision))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(decision.toString());
        }

        static Stream<Arguments> nonConcreteDecisionCases() {
            return Stream.of(arguments(Decision.INDETERMINATE), arguments(Decision.NOT_APPLICABLE));
        }
    }

    @Nested
    @DisplayName("combineOutcomes")
    class CombineOutcomes {

        @Test
        @DisplayName("null left returns right")
        void whenLeftIsNull_thenReturnsRight() {
            assertThat(CombiningUtils.combineOutcomes(null, Outcome.PERMIT)).isEqualTo(Outcome.PERMIT);
        }

        @Test
        @DisplayName("null right returns left")
        void whenRightIsNull_thenReturnsLeft() {
            assertThat(CombiningUtils.combineOutcomes(Outcome.DENY, null)).isEqualTo(Outcome.DENY);
        }

        @Test
        @DisplayName("both null returns null")
        void whenBothNull_thenReturnsNull() {
            assertThat(CombiningUtils.combineOutcomes(null, null)).isNull();
        }

        @ParameterizedTest(name = "combineOutcomes({0}, {1}) = {2}")
        @MethodSource("unionCases")
        void whenBothNonNull_thenReturnsUnion(Outcome a, Outcome b, Outcome expected) {
            assertThat(CombiningUtils.combineOutcomes(a, b)).isEqualTo(expected);
        }

        static Stream<Arguments> unionCases() {
            return Stream.of(arguments(Outcome.PERMIT, Outcome.PERMIT, Outcome.PERMIT),
                    arguments(Outcome.PERMIT, Outcome.DENY, Outcome.PERMIT_OR_DENY),
                    arguments(Outcome.PERMIT, Outcome.SUSPEND, Outcome.PERMIT_OR_SUSPEND),
                    arguments(Outcome.DENY, Outcome.SUSPEND, Outcome.DENY_OR_SUSPEND),
                    arguments(Outcome.PERMIT_OR_DENY, Outcome.SUSPEND, Outcome.PERMIT_OR_DENY_OR_SUSPEND));
        }
    }

}
