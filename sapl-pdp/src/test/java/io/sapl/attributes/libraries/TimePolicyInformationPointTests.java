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
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.InMemoryAttributeRepository;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("TimePolicyInformationPoint")
class TimePolicyInformationPointTests {

    private static Clock clockAt(String instantIso) {
        return clockAt(instantIso, ZoneOffset.UTC);
    }

    private static Clock clockAt(String instantIso, ZoneId zone) {
        val clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.parse(instantIso));
        when(clock.getZone()).thenReturn(zone);
        return clock;
    }

    @Nested
    @DisplayName("now")
    class Now {

        @Test
        @DisplayName("emits updates at default interval")
        void whenNowThenEmitsUpdates() {
            val now        = Instant.parse("2021-11-08T13:00:00Z");
            val nowPlusOne = Instant.parse("2021-11-08T13:00:01Z");
            val nowPlusTwo = Instant.parse("2021-11-08T13:00:02Z");
            val clock      = mock(Clock.class);
            when(clock.instant()).thenReturn(now, nowPlusOne, nowPlusTwo);
            val sut = new TimePolicyInformationPoint(clock);
            StepVerifier.withVirtualTime(sut::now).expectNext(Value.of(now.toString())).thenAwait(Duration.ofSeconds(2))
                    .expectNext(Value.of(nowPlusOne.toString()), Value.of(nowPlusTwo.toString())).thenCancel().verify();
        }

        @Test
        @DisplayName("returns error for zero delay")
        void whenNowWithZeroDelayThenFails() {
            val clock = mock(Clock.class);
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.now(Value.of(0L)))
                    .expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
        }
    }

    @Nested
    @DisplayName("systemTimeZone")
    class SystemTimeZone {

        @Test
        @DisplayName("retrieves system timezone")
        void whenSystemTimeZoneThenIsRetrieved() {
            val sut = new TimePolicyInformationPoint(mock(Clock.class)).systemTimeZone().next();
            StepVerifier.create(sut).expectNextMatches(TextValue.class::isInstance).verifyComplete();
        }
    }

    @Nested
    @DisplayName("nowIsAfter and nowIsBefore")
    class NowIsAfterAndBefore {

        @Test
        @DisplayName("nowIsAfter emits false then true when checkpoint is in the future")
        void whenNowIsAfterThenEmitsCorrectValues() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.nowIsAfter(Value.of("2021-11-08T14:30:00Z")))
                    .expectNext(Value.FALSE).thenAwait(Duration.ofMinutes(91L)).expectNext(Value.TRUE).verifyComplete();
        }

        @Test
        @DisplayName("nowIsAfter returns true when checkpoint is in the past")
        void whenNowIsAlwaysAfterCheckpointThenReturnsTrue() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.nowIsAfter(Value.of("2021-11-01T14:30:00Z")))
                    .expectNext(Value.TRUE).verifyComplete();
        }

        @Test
        @DisplayName("nowIsBefore transitions from true to false")
        void whenNowIsBeforeThenTransitionsCorrectly() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.nowIsBefore(Value.of("2021-11-08T14:30:00Z")))
                    .expectNext(Value.TRUE).thenAwait(Duration.ofMinutes(91L)).expectNext(Value.FALSE).verifyComplete();
        }
    }

    @Nested
    @DisplayName("nowIsBetween")
    class NowIsBetweenTests {

        @Test
        @DisplayName("returns false when now is after interval")
        void whenNowIsBetweenStartAfterIntervalThenReturnsFalse() {
            val clock = clockAt("2021-11-08T13:00:40Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier
                    .<Value>withVirtualTime(
                            () -> sut.nowIsBetween(Value.of("2021-11-08T13:00:05Z"), Value.of("2021-11-08T13:00:10Z")))
                    .expectNext(Value.FALSE).verifyComplete();
        }

        @Test
        @DisplayName("transitions from true to false when now is inside interval")
        void whenNowIsBetweenStartBetweenThenTransitionsCorrectly() {
            val clock = clockAt("2021-11-08T13:00:05Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier
                    .<Value>withVirtualTime(
                            () -> sut.nowIsBetween(Value.of("2021-11-08T13:00:00Z"), Value.of("2021-11-08T13:00:10Z")))
                    .expectNext(Value.TRUE).thenAwait(Duration.ofSeconds(5L)).expectNext(Value.FALSE).verifyComplete();
        }

        @Test
        @DisplayName("transitions false-true-false when now is before interval")
        void whenNowIsBetweenStartBeforeThenTransitionsCorrectly() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier
                    .<Value>withVirtualTime(
                            () -> sut.nowIsBetween(Value.of("2021-11-08T13:00:05Z"), Value.of("2021-11-08T13:00:10Z")))
                    .expectNext(Value.FALSE).thenAwait(Duration.ofSeconds(5L)).expectNext(Value.TRUE)
                    .thenAwait(Duration.ofSeconds(5L)).expectNext(Value.FALSE).verifyComplete();
        }
    }

    @Nested
    @DisplayName("localTimeIsAfter")
    class LocalTimeIsAfterTests {

        @Test
        @DisplayName("always true when checkpoint is midnight")
        void whenLocalTimeIsAfterMidnightThenAlwaysTrue() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsAfter(Value.of(LocalTime.MIN.toString())))
                    .expectNext(Value.TRUE).verifyComplete();
        }

        @Test
        @DisplayName("never true when checkpoint is max time")
        void whenLocalTimeIsAfterMaxTimeThenNeverTrue() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsAfter(Value.of(LocalTime.MAX.toString())))
                    .expectNext(Value.FALSE).verifyComplete();
        }

        @Test
        @DisplayName("emits correct sequence when starting after checkpoint")
        void whenStartingTimeIsAfterCheckpointThenEmitsCorrectSequence() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            // @formatter:off
            StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsAfter(Value.of("12:00")))
                .expectNext(Value.TRUE)
                .thenAwait(Duration.ofHours(11L)).expectNext(Value.FALSE)
                .thenAwait(Duration.ofHours(12L)).expectNext(Value.TRUE)
                .thenAwait(Duration.ofHours(12L)).expectNext(Value.FALSE)
                .thenAwait(Duration.ofHours(12L)).expectNext(Value.TRUE)
                .thenAwait(Duration.ofHours(12L)).expectNext(Value.FALSE)
                .thenCancel().verify();
            // @formatter:on
        }

        @Test
        @DisplayName("emits correct sequence when starting before checkpoint")
        void whenStartingTimeIsBeforeCheckpointThenEmitsCorrectSequence() {
            val clock = clockAt("2021-11-08T11:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            // @formatter:off
            StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsAfter(Value.of("12:00")))
                .expectNext(Value.FALSE)
                .thenAwait(Duration.ofHours(1L)).expectNext(Value.TRUE)
                .thenAwait(Duration.ofHours(12L)).expectNext(Value.FALSE)
                .thenAwait(Duration.ofHours(12L)).expectNext(Value.TRUE)
                .thenAwait(Duration.ofHours(12L)).expectNext(Value.FALSE)
                .thenAwait(Duration.ofHours(12L)).expectNext(Value.TRUE)
                .thenAwait(Duration.ofHours(12L)).expectNext(Value.FALSE)
                .thenCancel().verify();
            // @formatter:on
        }

        @Test
        @DisplayName("with timezone produces different result than UTC")
        void whenTimezoneProducesDifferentResultThanUtc() {
            // 23:00 UTC in Europe/Paris (CET, UTC+1) = 00:00 next day
            // localTimeIsAfter("22:00") at UTC: 23:00 > 22:00 -> TRUE
            // localTimeIsAfter("22:00") at Europe/Paris: 00:00 < 22:00 -> FALSE
            val clock = clockAt("2021-11-08T23:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsAfter(Value.of("22:00"))).expectNext(Value.TRUE)
                    .thenCancel().verify();
            StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsAfter(Value.of("22:00"), Value.of("Europe/Paris")))
                    .expectNext(Value.FALSE).thenCancel().verify();
        }

        @ParameterizedTest
        @ValueSource(strings = { "Invalid/Zone", "Not_A_Zone" })
        @DisplayName("returns error for invalid timezone")
        void whenInvalidTimezoneThenReturnsError(String invalidZone) {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsAfter(Value.of("12:00"), Value.of(invalidZone)))
                    .expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
        }
    }

    @Nested
    @DisplayName("localTimeIsBefore")
    class LocalTimeIsBeforeTests {

        @Test
        @DisplayName("emits correct sequence when starting before checkpoint")
        void whenStartingBeforeCheckpointThenEmitsCorrectSequence() {
            val clock = clockAt("2021-11-08T11:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            // @formatter:off
            StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsBefore(Value.of("12:00")))
                .expectNext(Value.TRUE)
                .thenAwait(Duration.ofHours(1L)).expectNext(Value.FALSE)
                .thenAwait(Duration.ofHours(12L)).expectNext(Value.TRUE)
                .thenAwait(Duration.ofHours(12L)).expectNext(Value.FALSE)
                .thenAwait(Duration.ofHours(12L)).expectNext(Value.TRUE)
                .thenAwait(Duration.ofHours(12L)).expectNext(Value.FALSE)
                .thenAwait(Duration.ofHours(12L)).expectNext(Value.TRUE)
                .thenCancel().verify();
            // @formatter:on
        }

        @Test
        @DisplayName("with timezone produces different result than UTC")
        void whenTimezoneProducesDifferentResultThanUtc() {
            // 23:00 UTC in Europe/Paris = 00:00 next day
            // localTimeIsBefore("22:00") at UTC: 23:00 NOT before 22:00 -> FALSE
            // localTimeIsBefore("22:00") at Europe/Paris: 00:00 IS before 22:00 -> TRUE
            val clock = clockAt("2021-11-08T23:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsBefore(Value.of("22:00"))).expectNext(Value.FALSE)
                    .thenCancel().verify();
            StepVerifier
                    .<Value>withVirtualTime(() -> sut.localTimeIsBefore(Value.of("22:00"), Value.of("Europe/Paris")))
                    .expectNext(Value.TRUE).thenCancel().verify();
        }

        @ParameterizedTest
        @ValueSource(strings = { "Invalid/Zone", "Not_A_Zone" })
        @DisplayName("returns error for invalid timezone")
        void whenInvalidTimezoneThenReturnsError(String invalidZone) {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsBefore(Value.of("12:00"), Value.of(invalidZone)))
                    .expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
        }
    }

    @Nested
    @DisplayName("localTimeIsBetween")
    class LocalTimeIsBetweenTests {

        @Test
        @DisplayName("returns false for zero-size interval")
        void whenNullSizeIntervalThenNeverTrue() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsBetween(Value.of("12:00"), Value.of("12:00")))
                    .expectNext(Value.FALSE).verifyComplete();
        }

        @Test
        @DisplayName("always true for MIN-MAX interval")
        void whenMinMaxIntervalThenAlwaysTrue() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsBetween(Value.of(LocalTime.MIN.toString()),
                    Value.of(LocalTime.MAX.toString()))).expectNext(Value.TRUE).verifyComplete();
        }

        @Test
        @DisplayName("always true for MAX-MIN interval")
        void whenMaxMinIntervalThenAlwaysTrue() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsBetween(Value.of(LocalTime.MAX.toString()),
                    Value.of(LocalTime.MIN.toString()))).expectNext(Value.TRUE).verifyComplete();
        }

        @Test
        @DisplayName("returns true when start is MIN and now is inside interval")
        void whenIntervalStartsAtMinThenReturnsTrue() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier
                    .<Value>withVirtualTime(
                            () -> sut.localTimeIsBetween(Value.of(LocalTime.MIN.toString()), Value.of("22:00")))
                    .expectNext(Value.TRUE).thenCancel().verify();
        }

        @Test
        @DisplayName("returns true when start is MAX (wrapping interval)")
        void whenIntervalStartsAtMaxThenReturnsTrue() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier
                    .<Value>withVirtualTime(
                            () -> sut.localTimeIsBetween(Value.of(LocalTime.MAX.toString()), Value.of("14:00")))
                    .expectNext(Value.TRUE).thenCancel().verify();
        }

        @Test
        @DisplayName("emits correct sequence when starting before interval")
        void whenStartingBeforeIntervalThenEmitsCorrectSequence() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            // @formatter:off
            StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsBetween(Value.of("14:00:00"), Value.of("15:00")))
                .expectNext(Value.FALSE)
                .thenAwait(Duration.ofHours(1L)).expectNext(Value.TRUE)
                .thenAwait(Duration.ofHours(1L)).expectNext(Value.FALSE)
                .thenAwait(Duration.ofHours(23L)).expectNext(Value.TRUE)
                .thenAwait(Duration.ofHours(1L)).expectNext(Value.FALSE)
                .thenAwait(Duration.ofHours(23L)).expectNext(Value.TRUE)
                .thenAwait(Duration.ofHours(1L)).expectNext(Value.FALSE)
                .thenAwait(Duration.ofHours(23L)).expectNext(Value.TRUE)
                .thenCancel().verify();
            // @formatter:on
        }

        @Test
        @DisplayName("emits correct sequence when interval wraps around midnight")
        void whenReversedIntervalThenEmitsCorrectSequence() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            // @formatter:off
            StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsBetween(Value.of("15:00"), Value.of("14:00:00")))
                .expectNext(Value.TRUE)
                .thenAwait(Duration.ofHours(1L)).expectNext(Value.FALSE)
                .thenAwait(Duration.ofHours(1L)).expectNext(Value.TRUE)
                .thenAwait(Duration.ofHours(23L)).expectNext(Value.FALSE)
                .thenAwait(Duration.ofHours(1L)).expectNext(Value.TRUE)
                .thenAwait(Duration.ofHours(23L)).expectNext(Value.FALSE)
                .thenAwait(Duration.ofHours(1L)).expectNext(Value.TRUE)
                .thenAwait(Duration.ofHours(23L)).expectNext(Value.FALSE)
                .thenCancel().verify();
            // @formatter:on
        }

        @Test
        @DisplayName("emits correct sequence when starting inside interval")
        void whenStartingInsideIntervalThenEmitsCorrectSequence() {
            val clock = clockAt("2021-11-08T15:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            // @formatter:off
            StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsBetween(Value.of("14:00:00"), Value.of("16:00")))
                .expectNext(Value.TRUE)
                .thenAwait(Duration.ofHours(1L)).expectNext(Value.FALSE)
                .thenAwait(Duration.ofHours(22L)).expectNext(Value.TRUE)
                .thenAwait(Duration.ofHours(2L)).expectNext(Value.FALSE)
                .thenAwait(Duration.ofHours(22L)).expectNext(Value.TRUE)
                .thenAwait(Duration.ofHours(2L)).expectNext(Value.FALSE)
                .thenAwait(Duration.ofHours(22L)).expectNext(Value.TRUE)
                .thenCancel().verify();
            // @formatter:on
        }

        @Test
        @DisplayName("emits correct sequence when starting after interval")
        void whenStartingAfterIntervalThenEmitsCorrectSequence() {
            val clock = clockAt("2021-11-08T18:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            // @formatter:off
            StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsBetween(Value.of("14:00:00"), Value.of("16:00")))
                .expectNext(Value.FALSE)
                .thenAwait(Duration.ofHours(20L)).expectNext(Value.TRUE)
                .thenAwait(Duration.ofHours(2L)).expectNext(Value.FALSE)
                .thenAwait(Duration.ofHours(22L)).expectNext(Value.TRUE)
                .thenAwait(Duration.ofHours(2L)).expectNext(Value.FALSE)
                .thenAwait(Duration.ofHours(22L)).expectNext(Value.TRUE)
                .thenAwait(Duration.ofHours(2L)).expectNext(Value.FALSE)
                .thenAwait(Duration.ofHours(22L)).expectNext(Value.TRUE)
                .thenCancel().verify();
            // @formatter:on
        }

        @Test
        @DisplayName("with timezone override")
        void whenTimezoneOverrideThenUsesCorrectZone() {
            // 22:30 UTC = 23:30 in Europe/Paris (CET)
            // Between 23:00-23:59 in Europe/Paris -> true
            // Between 23:00-23:59 in UTC -> false (22:30 is before 23:00)
            val clock = clockAt("2021-11-08T22:30:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsBetween(Value.of("23:00"), Value.of("23:59")))
                    .expectNext(Value.FALSE).thenCancel().verify();
            StepVerifier.<Value>withVirtualTime(
                    () -> sut.localTimeIsBetween(Value.of("23:00"), Value.of("23:59"), Value.of("Europe/Paris")))
                    .expectNext(Value.TRUE).thenCancel().verify();
        }

        @ParameterizedTest
        @ValueSource(strings = { "Invalid/Zone", "Not_A_Zone" })
        @DisplayName("returns error for invalid timezone")
        void whenInvalidTimezoneThenReturnsError(String invalidZone) {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier
                    .<Value>withVirtualTime(
                            () -> sut.localTimeIsBetween(Value.of("12:00"), Value.of("14:00"), Value.of(invalidZone)))
                    .expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
        }
    }

    @Nested
    @DisplayName("weekdayIn")
    class WeekdayInTests {

        @Test
        @DisplayName("returns true when current day is in set")
        void whenCurrentDayInSetThenTrue() {
            // 2021-11-08 is a Monday
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier
                    .<Value>withVirtualTime(
                            () -> sut.weekdayIn(Value.ofArray(Value.of("MONDAY"), Value.of("WEDNESDAY"))))
                    .expectNext(Value.TRUE).thenCancel().verify();
        }

        @Test
        @DisplayName("returns false when current day is not in set")
        void whenCurrentDayNotInSetThenFalse() {
            // 2021-11-08 is a Monday
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier
                    .<Value>withVirtualTime(
                            () -> sut.weekdayIn(Value.ofArray(Value.of("TUESDAY"), Value.of("WEDNESDAY"))))
                    .expectNext(Value.FALSE).thenCancel().verify();
        }

        @Test
        @DisplayName("always true when all 7 days in set")
        void whenAllDaysInSetThenAlwaysTrue() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(
                    () -> sut.weekdayIn(Value.ofArray(Value.of("MONDAY"), Value.of("TUESDAY"), Value.of("WEDNESDAY"),
                            Value.of("THURSDAY"), Value.of("FRIDAY"), Value.of("SATURDAY"), Value.of("SUNDAY"))))
                    .expectNext(Value.TRUE).verifyComplete();
        }

        @Test
        @DisplayName("returns error for empty array")
        void whenEmptyArrayThenError() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.weekdayIn(Value.EMPTY_ARRAY))
                    .expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
        }

        @Test
        @DisplayName("returns error for invalid day name")
        void whenInvalidDayNameThenError() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.weekdayIn(Value.ofArray(Value.of("NOTADAY"))))
                    .expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
        }

        @Test
        @DisplayName("transitions at midnight boundary")
        void whenMidnightBoundaryThenTransitions() {
            // 2021-11-08 Monday, 23:00 UTC. Monday is in set, Tuesday is not.
            val clock = clockAt("2021-11-08T23:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.weekdayIn(Value.ofArray(Value.of("MONDAY"))))
                    .expectNext(Value.TRUE).thenAwait(Duration.ofHours(1L)).expectNext(Value.FALSE).thenCancel()
                    .verify();
        }

        @Test
        @DisplayName("skips consecutive same-state days")
        void whenConsecutiveSameStateDaysThenSkips() {
            // 2021-11-08 Monday. Set: MON, TUE, WED. Thursday is first non-member.
            // Should emit TRUE and then after 3 days (to Thursday midnight) emit FALSE.
            val clock = clockAt("2021-11-08T00:00:01Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier
                    .<Value>withVirtualTime(() -> sut
                            .weekdayIn(Value.ofArray(Value.of("MONDAY"), Value.of("TUESDAY"), Value.of("WEDNESDAY"))))
                    .expectNext(Value.TRUE).thenAwait(Duration.ofDays(3).minusSeconds(1)).expectNext(Value.FALSE)
                    .thenCancel().verify();
        }

        @Test
        @DisplayName("with timezone override changes day evaluation")
        void whenTimezoneOverrideThenUsesCorrectDay() {
            // 2021-11-08 Monday 23:30 UTC = 2021-11-09 Tuesday 00:30 in Europe/Paris
            val clock = clockAt("2021-11-08T23:30:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            // At UTC it's Monday -> in set
            StepVerifier.<Value>withVirtualTime(() -> sut.weekdayIn(Value.ofArray(Value.of("MONDAY"))))
                    .expectNext(Value.TRUE).thenCancel().verify();
            // At Europe/Paris it's Tuesday -> not in set
            StepVerifier
                    .<Value>withVirtualTime(
                            () -> sut.weekdayIn(Value.ofArray(Value.of("MONDAY")), Value.of("Europe/Paris")))
                    .expectNext(Value.FALSE).thenCancel().verify();
        }
    }

    @Nested
    @DisplayName("dayOfWeekBetween")
    class DayOfWeekBetweenTests {

        @Test
        @DisplayName("non-wrapping range: inside is true")
        void whenInsideNonWrappingRangeThenTrue() {
            // 2021-11-08 Monday, range MON-FRI
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.dayOfWeekBetween(Value.of("MONDAY"), Value.of("FRIDAY")))
                    .expectNext(Value.TRUE).thenCancel().verify();
        }

        @Test
        @DisplayName("non-wrapping range: outside is false")
        void whenOutsideNonWrappingRangeThenFalse() {
            // 2021-11-13 Saturday, range MON-FRI
            val clock = clockAt("2021-11-13T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.dayOfWeekBetween(Value.of("MONDAY"), Value.of("FRIDAY")))
                    .expectNext(Value.FALSE).thenCancel().verify();
        }

        @Test
        @DisplayName("wrapping range FRI-MON: Friday is true")
        void whenWrappingRangeFridayThenTrue() {
            // 2021-11-12 Friday, range FRI-MON
            val clock = clockAt("2021-11-12T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.dayOfWeekBetween(Value.of("FRIDAY"), Value.of("MONDAY")))
                    .expectNext(Value.TRUE).thenCancel().verify();
        }

        @Test
        @DisplayName("wrapping range FRI-MON: Wednesday is false")
        void whenWrappingRangeWednesdayThenFalse() {
            // 2021-11-10 Wednesday, range FRI-MON
            val clock = clockAt("2021-11-10T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.dayOfWeekBetween(Value.of("FRIDAY"), Value.of("MONDAY")))
                    .expectNext(Value.FALSE).thenCancel().verify();
        }

        @Test
        @DisplayName("same start and end means single day")
        void whenSameStartAndEndThenSingleDay() {
            // 2021-11-08 Monday, range MON-MON = just Monday
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.dayOfWeekBetween(Value.of("MONDAY"), Value.of("MONDAY")))
                    .expectNext(Value.TRUE).thenCancel().verify();
        }

        @Test
        @DisplayName("returns error for invalid start day")
        void whenInvalidStartDayThenError() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.dayOfWeekBetween(Value.of("NOTADAY"), Value.of("FRIDAY")))
                    .expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
        }

        @Test
        @DisplayName("returns error for invalid end day")
        void whenInvalidEndDayThenError() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.dayOfWeekBetween(Value.of("MONDAY"), Value.of("NOTADAY")))
                    .expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
        }
    }

    @Nested
    @DisplayName("monthIn")
    class MonthInTests {

        @Test
        @DisplayName("returns true when current month is in set by name")
        void whenCurrentMonthInSetByNameThenTrue() {
            // 2021-11-08 is November
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier
                    .<Value>withVirtualTime(
                            () -> sut.monthIn(Value.ofArray(Value.of("NOVEMBER"), Value.of("DECEMBER"))))
                    .expectNext(Value.TRUE).thenCancel().verify();
        }

        @Test
        @DisplayName("returns true when current month is in set by number")
        void whenCurrentMonthInSetByNumberThenTrue() {
            // 2021-11-08 is November = 11
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.monthIn(Value.ofArray(Value.of(11L), Value.of(12L))))
                    .expectNext(Value.TRUE).thenCancel().verify();
        }

        @Test
        @DisplayName("returns false when current month is not in set")
        void whenCurrentMonthNotInSetThenFalse() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier
                    .<Value>withVirtualTime(() -> sut.monthIn(Value.ofArray(Value.of("JANUARY"), Value.of("FEBRUARY"))))
                    .expectNext(Value.FALSE).thenCancel().verify();
        }

        @Test
        @DisplayName("always true when all 12 months in set")
        void whenAllMonthsInSetThenAlwaysTrue() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.monthIn(
                    Value.ofArray(Value.of(1L), Value.of(2L), Value.of(3L), Value.of(4L), Value.of(5L), Value.of(6L),
                            Value.of(7L), Value.of(8L), Value.of(9L), Value.of(10L), Value.of(11L), Value.of(12L))))
                    .expectNext(Value.TRUE).verifyComplete();
        }

        @Test
        @DisplayName("returns error for empty array")
        void whenEmptyArrayThenError() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.monthIn(Value.EMPTY_ARRAY))
                    .expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
        }

        @Test
        @DisplayName("returns error for invalid month name")
        void whenInvalidMonthNameThenError() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.monthIn(Value.ofArray(Value.of("NOTAMONTH"))))
                    .expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
        }

        @Test
        @DisplayName("returns error for out-of-range month number")
        void whenOutOfRangeMonthNumberThenError() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.monthIn(Value.ofArray(Value.of(13L))))
                    .expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
        }

        @Test
        @DisplayName("mixed names and numbers")
        void whenMixedNamesAndNumbersThenWorks() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.monthIn(Value.ofArray(Value.of("OCTOBER"), Value.of(11L))))
                    .expectNext(Value.TRUE).thenCancel().verify();
        }
    }

    @Nested
    @DisplayName("monthBetween")
    class MonthBetweenTests {

        @Test
        @DisplayName("non-wrapping range: inside is true")
        void whenInsideNonWrappingRangeThenTrue() {
            // November is month 11, range 3-11 (Mar-Nov)
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.monthBetween(Value.of(3L), Value.of(11L)))
                    .expectNext(Value.TRUE).thenCancel().verify();
        }

        @Test
        @DisplayName("non-wrapping range: outside is false")
        void whenOutsideNonWrappingRangeThenFalse() {
            // November is month 11, range 3-9 (Mar-Sep)
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.monthBetween(Value.of(3L), Value.of(9L)))
                    .expectNext(Value.FALSE).thenCancel().verify();
        }

        @Test
        @DisplayName("wrapping range 11-3: November is true")
        void whenWrappingRangeNovemberThenTrue() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.monthBetween(Value.of(11L), Value.of(3L)))
                    .expectNext(Value.TRUE).thenCancel().verify();
        }

        @Test
        @DisplayName("wrapping range 11-3: June is false")
        void whenWrappingRangeJuneThenFalse() {
            val clock = clockAt("2021-06-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.monthBetween(Value.of(11L), Value.of(3L)))
                    .expectNext(Value.FALSE).thenCancel().verify();
        }

        @Test
        @DisplayName("same start and end means single month")
        void whenSameStartAndEndThenSingleMonth() {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.monthBetween(Value.of(11L), Value.of(11L)))
                    .expectNext(Value.TRUE).thenCancel().verify();
        }

        @ParameterizedTest
        @ValueSource(longs = { 0, 13, -1 })
        @DisplayName("returns error for invalid start month")
        void whenInvalidStartMonthThenError(long invalidMonth) {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.monthBetween(Value.of(invalidMonth), Value.of(12L)))
                    .expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
        }

        @ParameterizedTest
        @ValueSource(longs = { 0, 13, -1 })
        @DisplayName("returns error for invalid end month")
        void whenInvalidEndMonthThenError(long invalidMonth) {
            val clock = clockAt("2021-11-08T13:00:00Z");
            val sut   = new TimePolicyInformationPoint(clock);
            StepVerifier.<Value>withVirtualTime(() -> sut.monthBetween(Value.of(1L), Value.of(invalidMonth)))
                    .expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
        }
    }

    @Nested
    @DisplayName("toggle")
    class ToggleTests {

        @Test
        @DisplayName("alternates between true and false")
        void whenToggleThenAlternatesCorrectly() {
            val sut = new TimePolicyInformationPoint(mock(Clock.class));
            StepVerifier.<Value>withVirtualTime(() -> sut.toggle(Value.of(5_000L), Value.of(1_000L)))
                    .expectNext(Value.TRUE).thenAwait(Duration.ofMillis(5_000L)).expectNext(Value.FALSE)
                    .thenAwait(Duration.ofMillis(1_000L)).expectNext(Value.TRUE).thenCancel().verify();
        }
    }

    @Nested
    @DisplayName("broker integration")
    class BrokerIntegrationTests {

        @Test
        @DisplayName("library is available after loading")
        void whenBrokerLoadsTimePipThenLibraryIsAvailable() {
            val repository = new InMemoryAttributeRepository(Clock.systemUTC());
            val broker     = new CachingAttributeBroker(repository);
            val pip        = new TimePolicyInformationPoint(Clock.systemUTC());

            broker.loadPolicyInformationPointLibrary(pip);

            assertThat(broker.getLoadedLibraryNames()).contains("time");
        }

        @Test
        @DisplayName("throws when loading class without annotation")
        void whenLoadLibraryWithoutAnnotationThenThrowsException() {
            val repository = new InMemoryAttributeRepository(Clock.systemUTC());
            val broker     = new CachingAttributeBroker(repository);

            class NotAnnotated {
                @SuppressWarnings("unused")
                public Value someAttribute() {
                    return Value.of("test");
                }
            }

            assertThatThrownBy(() -> broker.loadPolicyInformationPointLibrary(new NotAnnotated()))
                    .hasMessageContaining("must be annotated with @PolicyInformationPoint");
        }

        @Test
        @DisplayName("throws when loading duplicate library")
        void whenLoadDuplicateLibraryThenThrowsException() {
            val repository = new InMemoryAttributeRepository(Clock.systemUTC());
            val broker     = new CachingAttributeBroker(repository);
            val pip        = new TimePolicyInformationPoint(Clock.systemUTC());

            broker.loadPolicyInformationPointLibrary(pip);

            assertThatThrownBy(
                    () -> broker.loadPolicyInformationPointLibrary(new TimePolicyInformationPoint(Clock.systemUTC())))
                    .hasMessageContaining("Library already loaded: time");
        }
    }
}
