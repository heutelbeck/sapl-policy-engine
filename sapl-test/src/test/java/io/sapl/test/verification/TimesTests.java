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
package io.sapl.test.verification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static io.sapl.test.verification.Times.atLeast;
import static io.sapl.test.verification.Times.atMost;
import static io.sapl.test.verification.Times.between;
import static io.sapl.test.verification.Times.never;
import static io.sapl.test.verification.Times.once;
import static io.sapl.test.verification.Times.times;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class TimesTests {

    // ========== Exactly Tests ==========

    @Test
    void onceVerifiesExactlyOne() {
        var once = once();

        assertThat(once.verify(1)).isTrue();
        assertThat(once.verify(0)).isFalse();
        assertThat(once.verify(2)).isFalse();
    }

    @Test
    void neverVerifiesZero() {
        var never = never();

        assertThat(never.verify(0)).isTrue();
        assertThat(never.verify(1)).isFalse();
        assertThat(never.verify(5)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 2, 5, 10, 100 })
    void timesVerifiesExactCount(int expected) {
        var times = times(expected);

        assertThat(times.verify(expected)).isTrue();
        assertThat(times.verify(expected - 1)).isFalse();
        assertThat(times.verify(expected + 1)).isFalse();
    }

    @Test
    void timesNegativeThrows() {
        assertThatThrownBy(() -> times(-1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }

    // ========== AtLeast Tests ==========

    @ParameterizedTest
    @MethodSource("atLeastTestCases")
    void atLeastVerifiesMinimum(int minimum, int actual, boolean expected) {
        assertThat(atLeast(minimum).verify(actual)).isEqualTo(expected);
    }

    static Stream<Arguments> atLeastTestCases() {
        return Stream.of(arguments(0, 0, true), arguments(0, 5, true), arguments(1, 0, false), arguments(1, 1, true),
                arguments(1, 5, true), arguments(3, 2, false), arguments(3, 3, true), arguments(3, 10, true));
    }

    @Test
    void atLeastNegativeThrows() {
        assertThatThrownBy(() -> atLeast(-1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }

    // ========== AtMost Tests ==========

    @ParameterizedTest
    @MethodSource("atMostTestCases")
    void atMostVerifiesMaximum(int maximum, int actual, boolean expected) {
        assertThat(atMost(maximum).verify(actual)).isEqualTo(expected);
    }

    static Stream<Arguments> atMostTestCases() {
        return Stream.of(arguments(0, 0, true), arguments(0, 1, false), arguments(1, 0, true), arguments(1, 1, true),
                arguments(1, 2, false), arguments(5, 3, true), arguments(5, 5, true), arguments(5, 6, false));
    }

    @Test
    void atMostNegativeThrows() {
        assertThatThrownBy(() -> atMost(-1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }

    // ========== Between Tests ==========

    @ParameterizedTest
    @MethodSource("betweenTestCases")
    void betweenVerifiesRange(int min, int max, int actual, boolean expected) {
        assertThat(between(min, max).verify(actual)).isEqualTo(expected);
    }

    static Stream<Arguments> betweenTestCases() {
        return Stream.of(arguments(1, 3, 0, false), arguments(1, 3, 1, true), arguments(1, 3, 2, true),
                arguments(1, 3, 3, true), arguments(1, 3, 4, false), arguments(0, 0, 0, true),
                arguments(0, 0, 1, false), arguments(5, 10, 4, false), arguments(5, 10, 7, true),
                arguments(5, 10, 11, false));
    }

    @Test
    void betweenMinNegativeThrows() {
        assertThatThrownBy(() -> between(-1, 5)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }

    @Test
    void betweenMaxLessThanMinThrows() {
        assertThatThrownBy(() -> between(5, 3)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(">= min");
    }

    // ========== Describe Tests ==========

    @Test
    void onceDescribesCorrectly() {
        assertThat(once().describe()).isEqualTo("exactly once");
    }

    @Test
    void neverDescribesCorrectly() {
        assertThat(never().describe()).isEqualTo("never");
    }

    @Test
    void timesDescribesCorrectly() {
        assertThat(times(0).describe()).isEqualTo("never");
        assertThat(times(1).describe()).isEqualTo("exactly once");
        assertThat(times(3).describe()).isEqualTo("exactly 3 times");
        assertThat(times(10).describe()).isEqualTo("exactly 10 times");
    }

    @Test
    void atLeastDescribesCorrectly() {
        assertThat(atLeast(1).describe()).isEqualTo("at least once");
        assertThat(atLeast(3).describe()).isEqualTo("at least 3 times");
    }

    @Test
    void atMostDescribesCorrectly() {
        assertThat(atMost(1).describe()).isEqualTo("at most once");
        assertThat(atMost(5).describe()).isEqualTo("at most 5 times");
    }

    @Test
    void betweenDescribesCorrectly() {
        assertThat(between(1, 3).describe()).isEqualTo("between 1 and 3 times");
        assertThat(between(5, 10).describe()).isEqualTo("between 5 and 10 times");
    }

    // ========== Failure Message Tests ==========

    @Test
    void failureMessageFormatsCorrectly() {
        assertThat(once().failureMessage(0)).isEqualTo("Expected exactly once but was invoked 0 time(s).");
        assertThat(once().failureMessage(5)).isEqualTo("Expected exactly once but was invoked 5 time(s).");
        assertThat(never().failureMessage(3)).isEqualTo("Expected never but was invoked 3 time(s).");
        assertThat(atLeast(2).failureMessage(1)).isEqualTo("Expected at least 2 times but was invoked 1 time(s).");
        assertThat(between(1, 3).failureMessage(5))
                .isEqualTo("Expected between 1 and 3 times but was invoked 5 time(s).");
    }

    // ========== Record Identity Tests ==========

    @Test
    void exactlyRecordHasExpectedValue() {
        var exactly = times(5);

        assertThat(exactly).isInstanceOf(Times.Exactly.class);
        assertThat(((Times.Exactly) exactly).expected()).isEqualTo(5);
    }

    @Test
    void atLeastRecordHasMinimumValue() {
        var minimum = atLeast(3);

        assertThat(minimum).isInstanceOf(Times.AtLeast.class);
        assertThat(((Times.AtLeast) minimum).minimum()).isEqualTo(3);
    }

    @Test
    void atMostRecordHasMaximumValue() {
        var maximum = atMost(7);

        assertThat(maximum).isInstanceOf(Times.AtMost.class);
        assertThat(((Times.AtMost) maximum).maximum()).isEqualTo(7);
    }

    @Test
    void betweenRecordHasMinAndMaxValues() {
        var range = between(2, 8);

        assertThat(range).isInstanceOf(Times.Between.class);
        assertThat(((Times.Between) range).minimum()).isEqualTo(2);
        assertThat(((Times.Between) range).maximum()).isEqualTo(8);
    }
}
