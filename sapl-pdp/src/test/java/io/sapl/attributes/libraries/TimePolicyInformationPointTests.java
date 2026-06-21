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
package io.sapl.attributes.libraries;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Poll;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.test.stream.MutableClock;
import io.sapl.api.test.stream.StreamAssertions;
import io.sapl.api.test.stream.TestTimeScheduler;
import io.sapl.attributes.broker.pip.PolicyInformationPointAttributeBroker;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("TimePolicyInformationPoint")
class TimePolicyInformationPointTests {

    private static Fixture fixtureAt(String instantIso) {
        return fixtureAt(instantIso, ZoneOffset.UTC);
    }

    private static Fixture fixtureAt(String instantIso, ZoneId zone) {
        val instant   = Instant.parse(instantIso);
        val clock     = new MutableClock(instant, zone);
        val scheduler = new TestTimeScheduler(instant);
        val sut       = new TimePolicyInformationPoint(clock, scheduler);
        return new Fixture(clock, scheduler, sut);
    }

    private record Fixture(MutableClock clock, TestTimeScheduler scheduler, TimePolicyInformationPoint sut) {
        /**
         * Advances clock and scheduler together so PIPs that
         * re-compute boundaries from the clock on each cycle
         * (weekdayIn, monthIn, etc.) see consistent state.
         */
        void advanceTo(Instant target) {
            clock.setInstant(target);
            scheduler.advanceTo(target);
        }
    }

    private static void awaitsErrorAndCompletes(io.sapl.api.stream.Stream<Value> stream) {
        StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(2)).awaitsNext(v -> {
            if (!(v instanceof ErrorValue)) {
                throw new AssertionError("Expected ErrorValue, got: " + v);
            }
        }).awaitsCompletion();
    }

    static Stream<Arguments> monthBetweenRangeArgs() {
        return Stream.of(arguments("2021-11-08T13:00:00Z", 3L, 11L, Value.TRUE, "non-wrapping range: inside is true"),
                arguments("2021-11-08T13:00:00Z", 3L, 9L, Value.FALSE, "non-wrapping range: outside is false"),
                arguments("2021-11-08T13:00:00Z", 11L, 3L, Value.TRUE, "wrapping range 11-3: November is true"),
                arguments("2021-06-08T13:00:00Z", 11L, 3L, Value.FALSE, "wrapping range 11-3: June is false"),
                arguments("2021-11-08T13:00:00Z", 11L, 11L, Value.TRUE, "same start and end means single month"));
    }

    @Nested
    @DisplayName("now")
    class Now {

        @Test
        @DisplayName("emits the current ISO timestamp immediately and again at each scheduled tick")
        void whenNowThenEmitsUpdates() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.now()) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.of("2021-11-08T13:00:00Z"));
                f.advanceTo(Instant.parse("2021-11-08T13:00:01Z"));
                StreamAssertions.assertThat(stream).awaitsNext(Value.of("2021-11-08T13:00:01Z"));
                f.advanceTo(Instant.parse("2021-11-08T13:00:02Z"));
                StreamAssertions.assertThat(stream).awaitsNext(Value.of("2021-11-08T13:00:02Z"));
            }
        }

        @Test
        @DisplayName("returns error for zero delay")
        void whenNowWithZeroDelayThenFails() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.now(Value.of(0L))) {
                awaitsErrorAndCompletes(stream);
            }
        }

        @Test
        @DisplayName("emits an error value when the clock returns null")
        void whenClockReturnsNullThenEmitsErrorValue() {
            val instant   = Instant.parse("2021-11-08T13:00:00Z");
            val clock     = nullReturningClock();
            val scheduler = new TestTimeScheduler(instant);
            val sut       = new TimePolicyInformationPoint(clock, scheduler);

            try (val stream = sut.now()) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> {
                    if (!(v instanceof ErrorValue err)) {
                        throw new AssertionError("Expected ErrorValue, got: " + v);
                    }
                    if (!err.message().contains("Clock returned null")) {
                        throw new AssertionError(
                                "Expected message containing 'Clock returned null', got: " + err.message());
                    }
                });
            }
        }
    }

    @Nested
    @DisplayName("systemTimeZone")
    class SystemTimeZone {

        @Test
        @DisplayName("emits a TextValue describing the JVM default timezone")
        void whenSystemTimeZoneThenEmitsTextValue() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.systemTimeZone()) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> {
                    if (!(v instanceof TextValue)) {
                        throw new AssertionError("Expected TextValue, got: " + v);
                    }
                });
            }
        }
    }

    @Nested
    @DisplayName("nowIsAfter")
    class NowIsAfter {

        @Test
        @DisplayName("emits false then true when checkpoint is in the future")
        void whenCheckpointInFutureThenEmitsFalseThenTrueAtCheckpoint() {
            val f          = fixtureAt("2021-11-08T13:00:00Z");
            val checkpoint = Instant.parse("2021-11-08T14:30:00Z");
            try (val stream = f.sut.nowIsAfter(Value.of(checkpoint.toString()))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE);
                f.advanceTo(checkpoint);
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE).awaitsCompletion();
            }
        }

        @Test
        @DisplayName("returns true when checkpoint is in the past")
        void whenCheckpointInPastThenEmitsTrueAndCompletes() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.nowIsAfter(Value.of("2021-11-01T14:30:00Z"))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE).awaitsCompletion();
            }
        }

        @Test
        @DisplayName("emits an error when checkpoint is not a valid ISO instant")
        void whenCheckpointMalformedThenEmitsError() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.nowIsAfter(Value.of("not-an-instant"))) {
                awaitsErrorAndCompletes(stream);
            }
        }
    }

    @Nested
    @DisplayName("nowIsBefore")
    class NowIsBefore {

        @Test
        @DisplayName("emits true then false when checkpoint is in the future")
        void whenCheckpointInFutureThenEmitsTrueThenFalseAtCheckpoint() {
            val f          = fixtureAt("2021-11-08T13:00:00Z");
            val checkpoint = Instant.parse("2021-11-08T14:30:00Z");
            try (val stream = f.sut.nowIsBefore(Value.of(checkpoint.toString()))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
                f.advanceTo(checkpoint);
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE).awaitsCompletion();
            }
        }

        @Test
        @DisplayName("returns false when checkpoint is in the past")
        void whenCheckpointInPastThenEmitsFalse() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.nowIsBefore(Value.of("2021-11-01T14:30:00Z"))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE).awaitsCompletion();
            }
        }
    }

    @Nested
    @DisplayName("nowIsBetween")
    class NowIsBetween {

        @Test
        @DisplayName("returns false when now is after the interval")
        void whenAfterIntervalThenEmitsFalse() {
            val f = fixtureAt("2021-11-08T13:00:40Z");
            try (val stream = f.sut.nowIsBetween(Value.of("2021-11-08T13:00:05Z"), Value.of("2021-11-08T13:00:10Z"))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE).awaitsCompletion();
            }
        }

        @Test
        @DisplayName("transitions from true to false when now is inside the interval")
        void whenInsideIntervalThenEmitsTrueThenFalseAtEnd() {
            val f   = fixtureAt("2021-11-08T13:00:05Z");
            val end = Instant.parse("2021-11-08T13:00:10Z");
            try (val stream = f.sut.nowIsBetween(Value.of("2021-11-08T13:00:00Z"), Value.of(end.toString()))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
                f.advanceTo(end);
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE).awaitsCompletion();
            }
        }

        @Test
        @DisplayName("transitions false-true-false when now is before the interval")
        void whenBeforeIntervalThenEmitsAllThreePhases() {
            val f     = fixtureAt("2021-11-08T13:00:00Z");
            val start = Instant.parse("2021-11-08T13:00:05Z");
            val end   = Instant.parse("2021-11-08T13:00:10Z");
            try (val stream = f.sut.nowIsBetween(Value.of(start.toString()), Value.of(end.toString()))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE);
                f.advanceTo(start);
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
                f.advanceTo(end);
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE).awaitsCompletion();
            }
        }
    }

    @Nested
    @DisplayName("localTimeIsAfter")
    class LocalTimeIsAfter {

        @Test
        @DisplayName("always true when checkpoint is midnight (LocalTime.MIN)")
        void whenCheckpointMinThenAlwaysTrue() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.localTimeIsAfter(Value.of(LocalTime.MIN.toString()))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE).awaitsCompletion();
            }
        }

        @Test
        @DisplayName("never true when checkpoint is end of day (LocalTime.MAX)")
        void whenCheckpointMaxThenNeverTrue() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.localTimeIsAfter(Value.of(LocalTime.MAX.toString()))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE).awaitsCompletion();
            }
        }

        @Test
        @DisplayName("emits TRUE when starting after checkpoint, transitions FALSE at midnight")
        void whenStartingAfterCheckpointThenEmitsTrueThenFalseAtMidnight() {
            val f        = fixtureAt("2021-11-08T13:00:00Z");
            val midnight = Instant.parse("2021-11-09T00:00:00Z");
            try (val stream = f.sut.localTimeIsAfter(Value.of("12:00"))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
                f.advanceTo(midnight);
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE);
            }
        }

        @Test
        @DisplayName("emits FALSE when starting before checkpoint, transitions TRUE at checkpoint")
        void whenStartingBeforeCheckpointThenEmitsFalseThenTrueAtCheckpoint() {
            val f          = fixtureAt("2021-11-08T11:00:00Z");
            val checkpoint = Instant.parse("2021-11-08T12:00:00Z");
            try (val stream = f.sut.localTimeIsAfter(Value.of("12:00"))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE);
                f.advanceTo(checkpoint);
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
            }
        }

        @Test
        @DisplayName("with timezone produces different result than UTC")
        void whenTimezoneOverrideProducesDifferentResultThanUtc() {
            val f = fixtureAt("2021-11-08T23:00:00Z");
            try (val streamUtc = f.sut.localTimeIsAfter(Value.of("22:00"))) {
                StreamAssertions.assertThat(streamUtc).awaitsNext(Value.TRUE);
            }
            try (val streamParis = f.sut.localTimeIsAfter(Value.of("22:00"), Value.of("Europe/Paris"))) {
                StreamAssertions.assertThat(streamParis).awaitsNext(Value.FALSE);
            }
        }

        @ParameterizedTest
        @DisplayName("returns error for invalid timezone")
        @ValueSource(strings = { "Invalid/Zone", "Not_A_Zone" })
        void whenInvalidTimezoneThenReturnsError(String invalidZone) {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.localTimeIsAfter(Value.of("12:00"), Value.of(invalidZone))) {
                awaitsErrorAndCompletes(stream);
            }
        }

        @Test
        @DisplayName("a midnight crossing between the two clock reads still schedules FALSE for the end of the starting day, not a day later")
        void whenClockCrossesMidnightBetweenReadsThenFalseFiresAtEndOfStartingDay() {
            // Time-of-day and the midnight-boundary date must come from one clock read,
            // else a tick past midnight pushes FALSE a day late.
            val startOfReadWindow = Instant.parse("2021-11-08T23:59:59.999Z");
            val afterMidnight     = Instant.parse("2021-11-09T00:00:00Z");
            val clock             = mock(Clock.class);
            when(clock.getZone()).thenReturn(ZoneOffset.UTC);
            // First read decides time of day, second read supplies the boundary date, which
            // must not be a later day.
            when(clock.instant()).thenReturn(startOfReadWindow, afterMidnight);
            val scheduler = new TestTimeScheduler(startOfReadWindow);
            val sut       = new TimePolicyInformationPoint(clock, scheduler);

            try (val stream = sut.localTimeIsAfter(Value.of("17:00"))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
                // Past the end of the starting day but before the next day ends, so FALSE must
                // already be due.
                scheduler.advanceTo(Instant.parse("2021-11-09T12:00:00Z"));
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE);
            }
        }
    }

    @Nested
    @DisplayName("localTimeIsBefore")
    class LocalTimeIsBefore {

        @Test
        @DisplayName("emits TRUE when starting before checkpoint, transitions FALSE at checkpoint")
        void whenStartingBeforeCheckpointThenEmitsTrueThenFalseAtCheckpoint() {
            val f          = fixtureAt("2021-11-08T11:00:00Z");
            val checkpoint = Instant.parse("2021-11-08T12:00:00Z");
            try (val stream = f.sut.localTimeIsBefore(Value.of("12:00"))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
                f.advanceTo(checkpoint);
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE);
            }
        }

        @Test
        @DisplayName("with timezone produces different result than UTC")
        void whenTimezoneOverrideProducesDifferentResultThanUtc() {
            val f = fixtureAt("2021-11-08T23:00:00Z");
            try (val streamUtc = f.sut.localTimeIsBefore(Value.of("22:00"))) {
                StreamAssertions.assertThat(streamUtc).awaitsNext(Value.FALSE);
            }
            try (val streamParis = f.sut.localTimeIsBefore(Value.of("22:00"), Value.of("Europe/Paris"))) {
                StreamAssertions.assertThat(streamParis).awaitsNext(Value.TRUE);
            }
        }

        @ParameterizedTest
        @DisplayName("returns error for invalid timezone")
        @ValueSource(strings = { "Invalid/Zone", "Not_A_Zone" })
        void whenInvalidTimezoneThenReturnsError(String invalidZone) {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.localTimeIsBefore(Value.of("12:00"), Value.of(invalidZone))) {
                awaitsErrorAndCompletes(stream);
            }
        }
    }

    @Nested
    @DisplayName("localTimeIsBetween")
    class LocalTimeIsBetween {

        @Test
        @DisplayName("returns false for zero-size interval")
        void whenZeroSizeIntervalThenAlwaysFalse() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.localTimeIsBetween(Value.of("12:00"), Value.of("12:00"))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE).awaitsCompletion();
            }
        }

        @Test
        @DisplayName("always true for MIN-MAX interval")
        void whenMinMaxIntervalThenAlwaysTrue() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.localTimeIsBetween(Value.of(LocalTime.MIN.toString()),
                    Value.of(LocalTime.MAX.toString()))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE).awaitsCompletion();
            }
        }

        @Test
        @DisplayName("always true for MAX-MIN interval (wraps full day)")
        void whenMaxMinIntervalThenAlwaysTrue() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.localTimeIsBetween(Value.of(LocalTime.MAX.toString()),
                    Value.of(LocalTime.MIN.toString()))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE).awaitsCompletion();
            }
        }

        @Test
        @DisplayName("emits FALSE then TRUE then FALSE when starting before interval")
        void whenStartingBeforeIntervalThenEmitsFalseTrueFalse() {
            val f     = fixtureAt("2021-11-08T13:00:00Z");
            val start = Instant.parse("2021-11-08T14:00:00Z");
            val end   = Instant.parse("2021-11-08T15:00:00Z");
            try (val stream = f.sut.localTimeIsBetween(Value.of("14:00:00"), Value.of("15:00"))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE);
                f.advanceTo(start);
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
                f.advanceTo(end);
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE);
            }
        }

        @Test
        @DisplayName("emits TRUE then FALSE when starting inside interval")
        void whenStartingInsideIntervalThenEmitsTrueThenFalse() {
            val f   = fixtureAt("2021-11-08T15:00:00Z");
            val end = Instant.parse("2021-11-08T16:00:00Z");
            try (val stream = f.sut.localTimeIsBetween(Value.of("14:00:00"), Value.of("16:00"))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
                f.advanceTo(end);
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE);
            }
        }

        @Test
        @DisplayName("emits FALSE then TRUE when starting after interval (next day)")
        void whenStartingAfterIntervalThenEmitsFalseThenTrueNextDay() {
            val f         = fixtureAt("2021-11-08T18:00:00Z");
            val nextStart = Instant.parse("2021-11-09T14:00:00Z");
            try (val stream = f.sut.localTimeIsBetween(Value.of("14:00:00"), Value.of("16:00"))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE);
                f.advanceTo(nextStart);
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
            }
        }

        @Test
        @DisplayName("schedules only the next boundary, never a past-due one, while waiting")
        void whenStartingAfterIntervalThenSchedulesOnlyTheNextBoundary() {
            val f = fixtureAt("2021-11-08T18:00:00Z");
            try (val stream = f.sut.localTimeIsBetween(Value.of("14:00:00"), Value.of("16:00"))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE);
                // Lazy repeat queues only the next-day start boundary. An eager loop would also
                // queue a past-due
                // end-of-today boundary whose stray emission overwrites the legitimate next
                // transition.
                await().during(Duration.ofMillis(100)).atMost(Duration.ofMillis(300))
                        .until(() -> f.scheduler.pendingCount() == 1);
            }
        }

        @Test
        @DisplayName("emits TRUE for wrapping interval when starting outside the gap")
        void whenWrappingIntervalAndStartingOutsideGapThenEmitsTrue() {
            // Interval 15:00-14:00 (wraps midnight) at 13:00 is inside the interval
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.localTimeIsBetween(Value.of("15:00"), Value.of("14:00:00"))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
            }
        }

        @Test
        @DisplayName("with timezone override")
        void whenTimezoneOverrideThenUsesCorrectZone() {
            val f = fixtureAt("2021-11-08T22:30:00Z");
            try (val streamUtc = f.sut.localTimeIsBetween(Value.of("23:00"), Value.of("23:59"))) {
                StreamAssertions.assertThat(streamUtc).awaitsNext(Value.FALSE);
            }
            try (val streamParis = f.sut.localTimeIsBetween(Value.of("23:00"), Value.of("23:59"),
                    Value.of("Europe/Paris"))) {
                StreamAssertions.assertThat(streamParis).awaitsNext(Value.TRUE);
            }
        }

        @ParameterizedTest
        @DisplayName("returns error for invalid timezone")
        @ValueSource(strings = { "Invalid/Zone", "Not_A_Zone" })
        void whenInvalidTimezoneThenReturnsError(String invalidZone) {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.localTimeIsBetween(Value.of("12:00"), Value.of("14:00"), Value.of(invalidZone))) {
                awaitsErrorAndCompletes(stream);
            }
        }
    }

    @Nested
    @DisplayName("weekdayIn")
    class WeekdayIn {

        @Test
        @DisplayName("returns true when current day is in the set")
        void whenCurrentDayInSetThenTrue() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.weekdayIn(Value.ofArray(Value.of("MONDAY"), Value.of("WEDNESDAY")))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
            }
        }

        @Test
        @DisplayName("returns false when current day is not in the set")
        void whenCurrentDayNotInSetThenFalse() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.weekdayIn(Value.ofArray(Value.of("TUESDAY"), Value.of("WEDNESDAY")))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE);
            }
        }

        @Test
        @DisplayName("always true when all 7 days are in the set")
        void whenAllDaysInSetThenAlwaysTrue() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut
                    .weekdayIn(Value.ofArray(Value.of("MONDAY"), Value.of("TUESDAY"), Value.of("WEDNESDAY"),
                            Value.of("THURSDAY"), Value.of("FRIDAY"), Value.of("SATURDAY"), Value.of("SUNDAY")))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE).awaitsCompletion();
            }
        }

        @Test
        @DisplayName("returns error for empty array")
        void whenEmptyArrayThenError() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.weekdayIn(Value.EMPTY_ARRAY)) {
                awaitsErrorAndCompletes(stream);
            }
        }

        @Test
        @DisplayName("returns error for invalid day name")
        void whenInvalidDayNameThenError() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.weekdayIn(Value.ofArray(Value.of("NOTADAY")))) {
                awaitsErrorAndCompletes(stream);
            }
        }

        @Test
        @DisplayName("transitions at midnight when day passes out of the set")
        void whenMidnightBoundaryThenTransitions() {
            val f        = fixtureAt("2021-11-08T23:00:00Z");
            val midnight = Instant.parse("2021-11-09T00:00:00Z");
            try (val stream = f.sut.weekdayIn(Value.ofArray(Value.of("MONDAY")))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
                f.advanceTo(midnight);
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE);
            }
        }

        @Test
        @DisplayName("with timezone override changes day evaluation")
        void whenTimezoneOverrideThenUsesCorrectDay() {
            val f = fixtureAt("2021-11-08T23:30:00Z");
            try (val streamUtc = f.sut.weekdayIn(Value.ofArray(Value.of("MONDAY")))) {
                StreamAssertions.assertThat(streamUtc).awaitsNext(Value.TRUE);
            }
            try (val streamParis = f.sut.weekdayIn(Value.ofArray(Value.of("MONDAY")), Value.of("Europe/Paris"))) {
                StreamAssertions.assertThat(streamParis).awaitsNext(Value.FALSE);
            }
        }
    }

    @Nested
    @DisplayName("dayOfWeekBetween")
    class DayOfWeekBetween {

        @Test
        @DisplayName("non-wrapping range: inside is true")
        void whenInsideNonWrappingRangeThenTrue() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.dayOfWeekBetween(Value.of("MONDAY"), Value.of("FRIDAY"))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
            }
        }

        @Test
        @DisplayName("non-wrapping range: outside is false")
        void whenOutsideNonWrappingRangeThenFalse() {
            val f = fixtureAt("2021-11-13T13:00:00Z");
            try (val stream = f.sut.dayOfWeekBetween(Value.of("MONDAY"), Value.of("FRIDAY"))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE);
            }
        }

        @Test
        @DisplayName("wrapping range FRI-MON: Friday is true")
        void whenWrappingRangeAndDayInsideThenTrue() {
            val f = fixtureAt("2021-11-12T13:00:00Z");
            try (val stream = f.sut.dayOfWeekBetween(Value.of("FRIDAY"), Value.of("MONDAY"))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
            }
        }

        @Test
        @DisplayName("wrapping range FRI-MON: Wednesday is false")
        void whenWrappingRangeAndDayOutsideThenFalse() {
            val f = fixtureAt("2021-11-10T13:00:00Z");
            try (val stream = f.sut.dayOfWeekBetween(Value.of("FRIDAY"), Value.of("MONDAY"))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE);
            }
        }

        @Test
        @DisplayName("same start and end means single day")
        void whenSameStartAndEndThenSingleDay() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.dayOfWeekBetween(Value.of("MONDAY"), Value.of("MONDAY"))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
            }
        }

        @Test
        @DisplayName("returns error for invalid start day")
        void whenInvalidStartDayThenError() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.dayOfWeekBetween(Value.of("NOTADAY"), Value.of("FRIDAY"))) {
                awaitsErrorAndCompletes(stream);
            }
        }

        @Test
        @DisplayName("returns error for invalid end day")
        void whenInvalidEndDayThenError() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.dayOfWeekBetween(Value.of("MONDAY"), Value.of("NOTADAY"))) {
                awaitsErrorAndCompletes(stream);
            }
        }
    }

    @Nested
    @DisplayName("monthIn")
    class MonthIn {

        @Test
        @DisplayName("returns true when current month is in the set by name")
        void whenCurrentMonthInSetByNameThenTrue() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.monthIn(Value.ofArray(Value.of("NOVEMBER"), Value.of("DECEMBER")))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
            }
        }

        @Test
        @DisplayName("returns true when current month is in the set by number")
        void whenCurrentMonthInSetByNumberThenTrue() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.monthIn(Value.ofArray(Value.of(11L), Value.of(12L)))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
            }
        }

        @Test
        @DisplayName("returns false when current month is not in the set")
        void whenCurrentMonthNotInSetThenFalse() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.monthIn(Value.ofArray(Value.of("JANUARY"), Value.of("FEBRUARY")))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE);
            }
        }

        @Test
        @DisplayName("always true when all 12 months are in the set")
        void whenAllMonthsInSetThenAlwaysTrue() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.monthIn(
                    Value.ofArray(Value.of(1L), Value.of(2L), Value.of(3L), Value.of(4L), Value.of(5L), Value.of(6L),
                            Value.of(7L), Value.of(8L), Value.of(9L), Value.of(10L), Value.of(11L), Value.of(12L)))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE).awaitsCompletion();
            }
        }

        @Test
        @DisplayName("returns error for empty array")
        void whenEmptyArrayThenError() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.monthIn(Value.EMPTY_ARRAY)) {
                awaitsErrorAndCompletes(stream);
            }
        }

        @Test
        @DisplayName("returns error for invalid month name")
        void whenInvalidMonthNameThenError() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.monthIn(Value.ofArray(Value.of("NOTAMONTH")))) {
                awaitsErrorAndCompletes(stream);
            }
        }

        @Test
        @DisplayName("returns error for out-of-range month number")
        void whenOutOfRangeMonthNumberThenError() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.monthIn(Value.ofArray(Value.of(13L)))) {
                awaitsErrorAndCompletes(stream);
            }
        }

        @Test
        @DisplayName("supports mixed names and numbers in the same set")
        void whenMixedNamesAndNumbersThenWorks() {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.monthIn(Value.ofArray(Value.of("OCTOBER"), Value.of(11L)))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
            }
        }
    }

    @Nested
    @DisplayName("monthBetween")
    class MonthBetween {

        @ParameterizedTest(name = "{4}")
        @MethodSource("io.sapl.attributes.libraries.TimePolicyInformationPointTests#monthBetweenRangeArgs")
        void whenMonthBetweenRangeThenExpected(String clockTime, long start, long end, Value expected,
                String description) {
            val f = fixtureAt(clockTime);
            try (val stream = f.sut.monthBetween(Value.of(start), Value.of(end))) {
                StreamAssertions.assertThat(stream).awaitsNext(expected);
            }
        }

        @ParameterizedTest
        @ValueSource(longs = { 0L, 13L, -1L })
        @DisplayName("returns error for invalid start month")
        void whenInvalidStartMonthThenError(long invalidMonth) {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.monthBetween(Value.of(invalidMonth), Value.of(12L))) {
                awaitsErrorAndCompletes(stream);
            }
        }

        @ParameterizedTest
        @ValueSource(longs = { 0L, 13L, -1L })
        @DisplayName("returns error for invalid end month")
        void whenInvalidEndMonthThenError(long invalidMonth) {
            val f = fixtureAt("2021-11-08T13:00:00Z");
            try (val stream = f.sut.monthBetween(Value.of(1L), Value.of(invalidMonth))) {
                awaitsErrorAndCompletes(stream);
            }
        }
    }

    @Nested
    @DisplayName("toggle")
    class Toggle {

        @Test
        @DisplayName("alternates between true and false at scheduled boundaries")
        void whenToggleThenAlternatesCorrectly() {
            val t0         = Instant.parse("2021-11-08T12:00:00Z");
            val firstFalse = t0.plusMillis(5_000L);
            val nextTrue   = firstFalse.plusMillis(1_000L);
            val clock      = new MutableClock(t0);
            val scheduler  = new TestTimeScheduler(t0);
            val sut        = new TimePolicyInformationPoint(clock, scheduler);
            try (val stream = sut.toggle(Value.of(5_000L), Value.of(1_000L))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
                clock.setInstant(firstFalse);
                scheduler.advanceTo(firstFalse);
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE);
                clock.setInstant(nextTrue);
                scheduler.advanceTo(nextTrue);
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
            }
        }
    }

    @Nested
    @DisplayName("clock jumps (suspend/resume, NTP correction)")
    class ClockJumps {

        @Test
        @DisplayName("nowIsBetween survives a forward jump over the whole interval and settles on false")
        void whenClockJumpsPastEntireIntervalThenSettlesOnFalse() {
            // Resumed after the whole window: must settle on FALSE, not stay TRUE.
            // A transient TRUE for the skipped window may or may not be observed.
            val f       = fixtureAt("2021-11-08T13:00:00Z");
            val pastEnd = Instant.parse("2021-11-08T20:00:00Z");
            val stream  = f.sut.nowIsBetween(Value.of("2021-11-08T14:00:00Z"), Value.of("2021-11-08T15:00:00Z"));
            StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE);
            f.advanceTo(pastEnd);
            val emitted = StreamAssertions.assertThat(stream).drain();
            assertThat(emitted).endsWith(Value.FALSE);
        }

        @Test
        @DisplayName("nowIsAfter fires true after a forward jump far past the checkpoint")
        void whenClockJumpsFarPastCheckpointThenEmitsTrueAndCompletes() {
            val f       = fixtureAt("2021-11-08T13:00:00Z");
            val wayPast = Instant.parse("2021-11-15T13:00:00Z");
            try (val stream = f.sut.nowIsAfter(Value.of("2021-11-08T14:00:00Z"))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE);
                f.advanceTo(wayPast);
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE).awaitsCompletion();
            }
        }

        @Test
        @DisplayName("nowIsAfter ignores a backward clock step (NTP correction) and still fires exactly once at the scheduled boundary")
        void whenClockSteppedBackwardThenNoPrematureTransitionAndFiresOnceAtBoundary() {
            // A clock rewind while the boundary is pending must not fire it early.
            val f          = fixtureAt("2021-11-08T13:00:00Z");
            val checkpoint = Instant.parse("2021-11-08T14:00:00Z");
            try (val stream = f.sut.nowIsAfter(Value.of(checkpoint.toString()))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE);
                f.clock.setInstant(Instant.parse("2021-11-08T12:00:00Z"));
                assertThat(stream.tryNext()).isInstanceOf(Poll.Empty.class);
                f.scheduler.advanceTo(checkpoint);
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE).awaitsCompletion();
            }
        }

        @Test
        @DisplayName("localTimeIsAfter converges to the correct value after a multi-day forward jump, without hanging")
        void whenClockJumpsSeveralDaysForwardThenConvergesToCorrectValue() {
            // Resumed days later before the checkpoint: must settle FALSE, not block.
            val f                = fixtureAt("2021-11-08T13:00:00Z");
            val daysLaterMorning = Instant.parse("2021-11-11T09:00:00Z");
            try (val stream = f.sut.localTimeIsAfter(Value.of("12:00"))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE);
                f.advanceTo(daysLaterMorning);
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE);
            }
        }
    }

    @Nested
    @DisplayName("broker registration")
    class StoreRegistration {

        @Test
        @DisplayName("loads under the time namespace without errors")
        void whenLoadedIntoStoreThenRegistersUnderTimeNamespace() {
            try (val broker = new PolicyInformationPointAttributeBroker()) {
                val now    = Instant.parse("2025-06-15T12:00:00Z");
                val handle = broker
                        .load(new TimePolicyInformationPoint(new MutableClock(now), new TestTimeScheduler(now)));

                assertThat(handle.pipName()).isEqualTo(TimePolicyInformationPoint.NAME);
                assertThat(handle.isLoaded()).isTrue();
                assertThat(broker.catalog()).containsExactly(handle);
            }
        }
    }

    private static Clock nullReturningClock() {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return null;
            }
        };
    }
}
