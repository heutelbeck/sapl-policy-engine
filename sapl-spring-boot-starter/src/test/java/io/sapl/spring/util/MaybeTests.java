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
package io.sapl.spring.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.sapl.spring.util.Maybe.Absent;
import io.sapl.spring.util.Maybe.Present;
import lombok.val;

/**
 * Tests {@link Maybe}: a three-state container distinguishing {@link Absent}
 * from {@link Present}{@code (null)} so that downstream code can tell
 * "no value at all" apart from "a value that happens to be null".
 * <p>
 * The distinction is load-bearing across the enforcement framework:
 * {@link io.sapl.spring.pep.constraints.EnforcementPlan#execute} starts a
 * {@code VoidSignal} at {@link Absent} so Mappers and Consumers skip, but
 * starts a value-carrying signal at {@link Present} so they fire even when
 * the carried value is {@code null}.
 */
@DisplayName("Maybe<T>")
class MaybeTests {

    @Nested
    @DisplayName("Constructors preserve the absent / present distinction")
    class Construction {

        @Test
        @DisplayName("of(value) wraps a non-null value as Present")
        void whenOfNonNullThenPresentValue() {
            val maybe = Maybe.of("Raistlin");

            assertThat(maybe).isInstanceOfSatisfying(Present.class,
                    present -> assertThat(present.value()).isEqualTo("Raistlin"));
        }

        @Test
        @DisplayName("of(null) wraps null as Present(null), NOT as Absent")
        void whenOfNullThenPresentWithNullValueNotAbsent() {
            val maybe = Maybe.of(null);

            assertThat(maybe).isInstanceOfSatisfying(Present.class, present -> assertThat(present.value()).isNull())
                    .isNotInstanceOf(Absent.class);
        }

        @Test
        @DisplayName("absent() returns an Absent sentinel, NOT a Present(null)")
        void whenAbsentThenAbsentInstanceNotPresent() {
            val maybe = Maybe.absent();

            assertThat(maybe).isInstanceOf(Absent.class).isNotInstanceOf(Present.class);
        }
    }

    @Nested
    @DisplayName("Present(null) and Absent are NOT equal: the framework relies on this")
    class AbsentVersusPresentNullDistinction {

        @Test
        @DisplayName("Present(null) does not equal Absent")
        void whenComparingPresentNullToAbsentThenNotEqual() {
            val presentNull = Maybe.of(null);
            val absent      = Maybe.absent();

            assertThat(presentNull).isNotEqualTo(absent);
            assertThat(absent).isNotEqualTo(presentNull);
        }

        @Test
        @DisplayName("Two Present(null) are equal (record equality on null component)")
        void whenComparingTwoPresentNullThenEqual() {
            val first  = Maybe.of(null);
            val second = Maybe.of(null);

            assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        }

        @Test
        @DisplayName("Two Absent are equal (record equality on the empty record)")
        void whenComparingTwoAbsentThenEqual() {
            val first  = Maybe.absent();
            val second = Maybe.absent();

            assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        }

        @Test
        @DisplayName("Two Present with different non-null values are not equal")
        void whenComparingPresentWithDifferentValuesThenNotEqual() {
            val first  = Maybe.of("Solinari");
            val second = Maybe.of("Lunitari");

            assertThat(first).isNotEqualTo(second);
        }
    }

    @Nested
    @DisplayName("Pattern matching")
    class PatternMatching {

        @Test
        @DisplayName("switch on a sealed Maybe binds Present's value via record-pattern destructuring")
        void whenSwitchOnPresentThenBindsValue() {
            Maybe<String> maybe = Maybe.of("Justarius");

            val outcome = switch (maybe) {
            case Present<String>(String value) -> "got: " + value;
            case Absent<String> ignored        -> "absent";
            };

            assertThat(outcome).isEqualTo("got: Justarius");
        }

        @Test
        @DisplayName("switch on an Absent goes through the absent branch")
        void whenSwitchOnAbsentThenAbsentBranch() {
            Maybe<String> maybe = Maybe.absent();

            val outcome = switch (maybe) {
            case Present<String>(String value) -> "got: " + value;
            case Absent<String> ignored        -> "absent";
            };

            assertThat(outcome).isEqualTo("absent");
        }

        @Test
        @DisplayName("switch on Present(null) goes through the present branch with null bound")
        void whenSwitchOnPresentNullThenPresentBranchWithNullBound() {
            Maybe<String> maybe = Maybe.of(null);

            val outcome = switch (maybe) {
            case Present<String>(String value) -> "value=" + value;
            case Absent<String> ignored        -> "absent";
            };

            assertThat(outcome).isEqualTo("value=null");
        }
    }

    @Nested
    @DisplayName("Generic typing")
    class GenericTyping {

        @Test
        @DisplayName("Maybe is parameterised: a Present<Integer> carries the typed value")
        void whenOfIntegerThenPresentCarriesInteger() {
            Maybe<Integer> maybe = Maybe.of(42);

            assertThat(maybe).isInstanceOfSatisfying(Present.class,
                    present -> assertThat(present.value()).isEqualTo(42));
        }

        @Test
        @DisplayName("absent() narrows to the inferred type at the call site")
        void whenAbsentInferredAsTypedAbsentThenInstanceOfAbsent() {
            Maybe<Integer> maybe = Maybe.absent();

            assertThat(maybe).isInstanceOf(Absent.class);
        }
    }
}
