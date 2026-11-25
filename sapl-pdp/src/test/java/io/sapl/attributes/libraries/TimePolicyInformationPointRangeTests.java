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

import io.sapl.api.model.Value;
import io.sapl.api.stream.Stream;
import io.sapl.api.test.stream.MutableClock;
import io.sapl.api.test.stream.StreamAssertions;
import io.sapl.api.test.stream.TestTimeScheduler;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

@Timeout(10)
@DisplayName("Running time attributes cover the complete four-digit-year range")
class TimePolicyInformationPointRangeTests {

    @ParameterizedTest(name = "{0} advances to {1}")
    @CsvSource({ "9998-12-31T23:59:59Z,9999-01-01T00:00:00Z", "9999-12-30T21:59:59Z,9999-12-30T22:00:00Z",
            "9999-12-30T23:59:59Z,9999-12-31T00:00:00Z", "9999-12-31T23:59:58Z,9999-12-31T23:59:59Z" })
    @DisplayName("ongoing now subscriptions emit through the last calendar date")
    void whenClockCrossesIntoTheLastDayThenNowContinuesToEmit(String start, String expected) {
        val first     = Instant.parse(start);
        val after     = first.plusSeconds(1);
        val clock     = new MutableClock(first);
        val scheduler = new TestTimeScheduler(first);
        val pip       = new TimePolicyInformationPoint(clock, scheduler);
        try (val stream = pip.now()) {
            StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(2)).awaitsNext(Value.of(start));
            clock.setInstant(after);
            scheduler.advanceTo(after);
            StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(2)).awaitsNext(Value.of(expected));
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "0000-01-01T00:00:00Z", "9999-12-31T23:59:59.999999999Z" })
    @DisplayName("now preserves the complete instant at the supported endpoints")
    void whenClockIsAtSupportedEndpointThenNowPreservesTheCompleteInstant(String text) {
        val at  = Instant.parse(text);
        val pip = new TimePolicyInformationPoint(new MutableClock(at), new TestTimeScheduler(at));
        try (val stream = pip.now()) {
            StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(2)).awaitsNext(Value.of(text));
        }
    }

    @ParameterizedTest(name = "{0} in {1} from {2}")
    @CsvSource({ "after,UTC,9999-12-31T11:59:59Z,9999-12-31T12:00:00Z,false",
            "before,UTC,9999-12-31T11:59:59Z,9999-12-31T12:00:00Z,true",
            "between,UTC,9999-12-31T11:59:59Z,9999-12-31T12:00:00Z,false",
            "weekday,UTC,9999-12-30T23:59:59Z,9999-12-31T00:00:00Z,false",
            "day-range,UTC,9999-12-30T23:59:59Z,9999-12-31T00:00:00Z,false",
            "month,UTC,9998-12-31T23:59:59Z,9999-01-01T00:00:00Z,true",
            "month-range,UTC,9998-12-31T23:59:59Z,9999-01-01T00:00:00Z,true",
            "weekday,+18:00,9999-12-30T05:59:59Z,9999-12-30T06:00:00Z,false",
            "weekday,-18:00,9999-12-31T17:59:59Z,9999-12-31T18:00:00Z,false",
            "weekday,Pacific/Kiritimati,9999-12-30T09:59:59Z,9999-12-30T10:00:00Z,false",
            "weekday,Pacific/Pago_Pago,9999-12-31T10:59:59Z,9999-12-31T11:00:00Z,false",
            "weekday,default,9999-12-30T22:59:59Z,9999-12-30T23:00:00Z,false" })
    @DisplayName("existing and fresh local readers agree across boundaries in the last year")
    void whenLocalBoundaryIsInTheLastYearThenExistingAndFreshReadersAgree(String kind, String zone, String start,
            String boundary, boolean initial) {
        val first     = Instant.parse(start);
        val after     = Instant.parse(boundary);
        val clockZone = ZoneId.of("default".equals(zone) ? "Europe/Berlin" : "UTC");
        val clock     = new MutableClock(first, clockZone);
        val scheduler = new TestTimeScheduler(first);
        val pip       = new TimePolicyInformationPoint(clock, scheduler);
        try (val stream = calendar(pip, kind, zone)) {
            StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(2)).awaitsNext(Value.of(initial));
            clock.setInstant(after);
            scheduler.advanceTo(after);
            StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(2)).awaitsNext(Value.of(!initial));
            try (val fresh = calendar(pip, kind, zone)) {
                StreamAssertions.assertThat(fresh).withinTimeout(Duration.ofSeconds(2)).awaitsNext(Value.of(!initial));
            }
        }
    }

    @Test
    @DisplayName("the lower endpoint supplies its actual weekday")
    void whenClockIsAtYearZeroThenCalendarMembershipIsAvailable() {
        val at  = Instant.parse("0000-01-01T00:00:00Z");
        val pip = new TimePolicyInformationPoint(new MutableClock(at), new TestTimeScheduler(at));
        try (val stream = pip.weekdayIn(Value.ofArray(Value.of("SATURDAY")))) {
            StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(2)).awaitsNext(Value.TRUE);
        }
    }

    private static Stream<Value> calendar(TimePolicyInformationPoint pip, String kind, String zone) {
        if ("default".equals(zone)) {
            return pip.weekdayIn(Value.ofArray(Value.of("FRIDAY")));
        }
        val selectedZone = Value.of(zone);
        return switch (kind) {
        case "after"       -> pip.localTimeIsAfter(Value.of("12:00"), selectedZone);
        case "before"      -> pip.localTimeIsBefore(Value.of("12:00"), selectedZone);
        case "between"     -> pip.localTimeIsBetween(Value.of("12:00"), Value.of("13:00"), selectedZone);
        case "weekday"     -> pip.weekdayIn(Value.ofArray(Value.of("FRIDAY")), selectedZone);
        case "day-range"   -> pip.dayOfWeekBetween(Value.of("FRIDAY"), Value.of("FRIDAY"), selectedZone);
        case "month"       -> pip.monthIn(Value.ofArray(Value.of("DECEMBER")), selectedZone);
        case "month-range" -> pip.monthBetween(Value.of(12), Value.of(12), selectedZone);
        default            -> throw new IllegalArgumentException("Unknown test finder.");
        };
    }

    @ParameterizedTest(name = "{0} from {1}")
    @CsvSource({ "-18:00,+10000-01-01T16:59:59Z,+10000-01-01T17:00:00Z,23:00",
            "America/Adak,+10000-01-01T08:59:59Z,+10000-01-01T09:00:00Z,23:00",
            "+18:00,-0001-12-31T06:00:00Z,-0001-12-31T06:01:00Z,00:01" })
    @DisplayName("local endpoints remain available across the adjacent UTC dates")
    void whenUtcDateIsOutsideTheRangeThenAnInRangeLocalDateStillChanges(String zone, String start, String boundary,
            String checkpoint) {
        val at           = Instant.parse(start);
        val after        = Instant.parse(boundary);
        val clock        = new MutableClock(at);
        val scheduler    = new TestTimeScheduler(at);
        val pip          = new TimePolicyInformationPoint(clock, scheduler);
        val text         = Value.of(checkpoint);
        val selectedZone = Value.of(zone);
        try (val stream = pip.localTimeIsAfter(text, selectedZone)) {
            StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(2)).awaitsNext(Value.FALSE);
            clock.setInstant(after);
            scheduler.advanceTo(after);
            StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(2)).awaitsNext(Value.TRUE);
            try (val fresh = pip.localTimeIsAfter(text, selectedZone)) {
                StreamAssertions.assertThat(fresh).withinTimeout(Duration.ofSeconds(2)).awaitsNext(Value.TRUE);
            }
        }
    }
}
