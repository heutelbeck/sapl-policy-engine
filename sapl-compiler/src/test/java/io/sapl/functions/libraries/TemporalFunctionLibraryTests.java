/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.functions.libraries;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class TemporalFunctionLibraryTests {

    private static TextValue timeValOf(String utcIsoTime) {
        return Value.of(Instant.parse(utcIsoTime).toString());
    }

    @Test
    void when_loadedIntoBroker_then_noError() {
        val functionBroker = new DefaultFunctionBroker();
        assertDoesNotThrow(() -> functionBroker.loadStaticFunctionLibrary(TemporalFunctionLibrary.class));
    }

    /* ######## INSTANT MANIPULATION TESTS ######## */

    @Test
    void plusNanosMillisSecondsTest() {
        final var time = timeValOf("2021-11-08T13:00:00Z");
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.plusNanos(time, Value.of(10_000_000_000L)))
                .isEqualTo(Value.of("2021-11-08T13:00:10Z"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.plusMillis(time, Value.of(10_000L)))
                .isEqualTo(Value.of("2021-11-08T13:00:10Z"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.plusSeconds(time, Value.of(10L)))
                .isEqualTo(Value.of("2021-11-08T13:00:10Z"));
    }

    @Test
    void minusNanosMillisSecondsTest() {
        final var time = timeValOf("2021-11-08T13:00:00Z");
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.minusNanos(time, Value.of(10_000_000_000L)))
                .isEqualTo(Value.of("2021-11-08T12:59:50Z"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.minusMillis(time, Value.of(10_000L)))
                .isEqualTo(Value.of("2021-11-08T12:59:50Z"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.minusSeconds(time, Value.of(10L)))
                .isEqualTo(Value.of("2021-11-08T12:59:50Z"));
    }

    /* ######## DATE ARITHMETIC TESTS ######## */

    @Test
    void plusDaysMonthsYearsTest() {
        final var time = timeValOf("2021-11-08T13:00:00Z");
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.plusDays(time, Value.of(5)))
                .isEqualTo(Value.of("2021-11-13T13:00:00Z"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.plusMonths(time, Value.of(2)))
                .isEqualTo(Value.of("2022-01-08T13:00:00Z"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.plusYears(time, Value.of(3)))
                .isEqualTo(Value.of("2024-11-08T13:00:00Z"));
    }

    @Test
    void plusMonthsEdgeCases() {
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.plusMonths(timeValOf("2021-01-31T13:00:00Z"),
                Value.of(1))).isEqualTo(Value.of("2021-02-28T13:00:00Z"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.plusYears(timeValOf("2020-02-29T13:00:00Z"),
                Value.of(1))).isEqualTo(Value.of("2021-02-28T13:00:00Z"));
    }

    @Test
    void minusDaysMonthsYearsTest() {
        final var time = timeValOf("2021-11-08T13:00:00Z");
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.minusDays(time, Value.of(5)))
                .isEqualTo(Value.of("2021-11-03T13:00:00Z"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.minusMonths(time, Value.of(2)))
                .isEqualTo(Value.of("2021-09-08T13:00:00Z"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.minusYears(time, Value.of(3)))
                .isEqualTo(Value.of("2018-11-08T13:00:00Z"));
    }

    @Test
    void minusMonthsEdgeCases() {
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.minusMonths(timeValOf("2021-03-31T13:00:00Z"),
                Value.of(1))).isEqualTo(Value.of("2021-02-28T13:00:00Z"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.minusDays(timeValOf("2021-01-05T13:00:00Z"),
                Value.of(10))).isEqualTo(Value.of("2020-12-26T13:00:00Z"));
    }

    /* ######## CALENDAR TESTS ######## */

    @Test
    void calendarFunctionsTest() {
        final var time = timeValOf("2021-11-08T13:00:00Z");
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.dayOfWeek(time)).isEqualTo(Value.of("MONDAY"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.dayOfYear(time)).isEqualTo(Value.of(312));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.weekOfYear(time)).isEqualTo(Value.of(45));
    }

    @Test
    void betweenTest() {
        final var today     = timeValOf("2021-11-08T13:00:00Z");
        final var yesterday = timeValOf("2021-11-07T13:00:00Z");
        final var tomorrow  = timeValOf("2021-11-09T13:00:00Z");

        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.between(today, yesterday, tomorrow))
                .isEqualTo(Value.TRUE);
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.between(tomorrow, yesterday, today))
                .isEqualTo(Value.FALSE);
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.between(today, today, today))
                .isEqualTo(Value.TRUE);
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.between(yesterday, yesterday, tomorrow))
                .isEqualTo(Value.TRUE);
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.between(tomorrow, today, tomorrow))
                .isEqualTo(Value.TRUE);
    }

    @Test
    void timeBetweenTest() {
        final var today    = timeValOf("2021-11-08T13:00:00Z");
        final var tomorrow = timeValOf("2021-11-09T13:00:00Z");

        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.timeBetween(today, tomorrow, Value.of("DAYS")))
                .isEqualTo(Value.of(1L));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.timeBetween(today, tomorrow, Value.of("HOURS")))
                .isEqualTo(Value.of(24L));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.timeBetween(tomorrow, today, Value.of("DAYS")))
                .isEqualTo(Value.of(-1L));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.timeBetween(Value.of("2001-01-01"),
                Value.of("2002-01-01"), Value.of("YEARS"))).isEqualTo(Value.of(1L));
    }

    @Test
    void timeBetweenWithInvalidChronoUnit() {
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.timeBetween(timeValOf("2021-11-08T13:00:00Z"),
                timeValOf("2021-11-09T13:00:00Z"), Value.of("INVALID_UNIT"))).isInstanceOf(ErrorValue.class);
    }

    @Test
    void beforeAfterTest() {
        final var earlier = timeValOf("2021-11-08T13:00:00Z");
        final var later   = timeValOf("2021-11-08T13:00:01Z");
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.before(earlier, later)).isEqualTo(Value.TRUE);
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.before(later, earlier)).isEqualTo(Value.FALSE);
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.after(earlier, later)).isEqualTo(Value.FALSE);
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.after(later, earlier)).isEqualTo(Value.TRUE);
    }

    /* ######## EXTRACT PARTS TESTS ######## */

    @Test
    void extractPartsTest() {
        final var time = timeValOf("2021-11-08T13:17:23Z");
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.dateOf(time)).isEqualTo(Value.of("2021-11-08"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.timeOf(time)).isEqualTo(Value.of("13:17:23"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.hourOf(time)).isEqualTo(Value.of(13));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.minuteOf(time)).isEqualTo(Value.of(17));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.secondOf(time)).isEqualTo(Value.of(23));
    }

    @Test
    void timeOfFormattingTest() {
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.timeOf(timeValOf("2021-11-08T13:00:00Z")))
                .isEqualTo(Value.of("13:00:00"));
        assertThat(
                io.sapl.functions.libraries.TemporalFunctionLibrary.timeOf(timeValOf("2021-11-08T13:00:00.999999999Z")))
                .isEqualTo(Value.of("13:00:00"));
    }

    /* ######## EPOCH TESTS ######## */

    @Test
    void epochConversionTest() {
        final var time = timeValOf("2021-11-08T13:00:00Z");
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.epochSecond(time))
                .isEqualTo(Value.of(1_636_376_400L));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.epochMilli(time))
                .isEqualTo(Value.of(1_636_376_400_000L));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.ofEpochSecond(Value.of(1_636_376_400L)))
                .isEqualTo(Value.of("2021-11-08T13:00:00Z"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.ofEpochMilli(Value.of(1_636_376_400_000L)))
                .isEqualTo(Value.of("2021-11-08T13:00:00Z"));
    }

    /* ######## DURATION TESTS ######## */

    @Test
    void durationConversionTest() {
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.durationOfSeconds(Value.of(60)))
                .isEqualTo(Value.of(60_000L));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.durationOfMinutes(Value.of(1)))
                .isEqualTo(Value.of(60_000L));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.durationOfHours(Value.of(24)))
                .isEqualTo(Value.of(86_400_000L));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.durationOfDays(Value.of(1)))
                .isEqualTo(Value.of(86_400_000L));
    }

    /* ######## VALIDATION TESTS ######## */

    @Test
    void validUtcTest() {
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.validUTC(Value.of("2021-11-08T13:00:00Z")))
                .isEqualTo(Value.TRUE);
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary
                .validUTC(Value.of("2021-11-08T13:00:00.123456789Z"))).isEqualTo(Value.TRUE);
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.validUTC(Value.of("2021-11-08")))
                .isEqualTo(Value.TRUE);
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.validUTC(Value.of("XXX")))
                .isEqualTo(Value.FALSE);
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.validUTC(Value.NULL)).isEqualTo(Value.FALSE);
    }

    @Test
    void validRFC3339Test() {
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.validRFC3339(Value.of("2021-11-08T13:00:00Z")))
                .isEqualTo(Value.TRUE);
        assertThat(
                io.sapl.functions.libraries.TemporalFunctionLibrary.validRFC3339(Value.of("2021-11-08T13:00:00+05:00")))
                .isEqualTo(Value.TRUE);
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.validRFC3339(Value.of("2021-11-08T13:00:00")))
                .isEqualTo(Value.FALSE);
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.validRFC3339(Value.of("2021-11-08")))
                .isEqualTo(Value.FALSE);
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.validRFC3339(Value.of("")))
                .isEqualTo(Value.FALSE);
    }

    /* ######## TEMPORAL BOUNDS TESTS ######## */

    @Test
    void startEndOfDayTest() {
        final var time = timeValOf("2021-11-08T13:45:30Z");
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.startOfDay(time))
                .isEqualTo(Value.of("2021-11-08T00:00:00Z"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.endOfDay(time))
                .isEqualTo(Value.of("2021-11-08T23:59:59.999999999Z"));
    }

    @Test
    void startEndOfWeekTest() {
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.startOfWeek(timeValOf("2021-11-08T13:45:30Z")))
                .isEqualTo(Value.of("2021-11-08T00:00:00Z"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.endOfWeek(timeValOf("2021-11-08T13:45:30Z")))
                .isEqualTo(Value.of("2021-11-14T23:59:59.999999999Z"));
    }

    @Test
    void startEndOfMonthTest() {
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.startOfMonth(timeValOf("2021-11-08T13:45:30Z")))
                .isEqualTo(Value.of("2021-11-01T00:00:00Z"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.endOfMonth(timeValOf("2021-11-08T13:45:30Z")))
                .isEqualTo(Value.of("2021-11-30T23:59:59.999999999Z"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.endOfMonth(timeValOf("2021-02-15T13:45:30Z")))
                .isEqualTo(Value.of("2021-02-28T23:59:59.999999999Z"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.endOfMonth(timeValOf("2020-02-15T13:45:30Z")))
                .isEqualTo(Value.of("2020-02-29T23:59:59.999999999Z"));
    }

    @Test
    void startEndOfYearTest() {
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.startOfYear(timeValOf("2021-11-08T13:45:30Z")))
                .isEqualTo(Value.of("2021-01-01T00:00:00Z"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.endOfYear(timeValOf("2021-11-08T13:45:30Z")))
                .isEqualTo(Value.of("2021-12-31T23:59:59.999999999Z"));
    }

    /* ######## TEMPORAL ROUNDING TESTS ######## */

    @Test
    void truncateTest() {
        final var time = timeValOf("2021-11-08T13:45:30.123Z");
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.truncateToHour(time))
                .isEqualTo(Value.of("2021-11-08T13:00:00Z"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.truncateToDay(time))
                .isEqualTo(Value.of("2021-11-08T00:00:00Z"));
        assertThat(
                io.sapl.functions.libraries.TemporalFunctionLibrary.truncateToWeek(timeValOf("2021-11-10T13:45:30Z")))
                .isEqualTo(Value.of("2021-11-08T00:00:00Z"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.truncateToMonth(time))
                .isEqualTo(Value.of("2021-11-01T00:00:00Z"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.truncateToYear(time))
                .isEqualTo(Value.of("2021-01-01T00:00:00Z"));
    }

    /* ######## ISO DURATION TESTS ######## */

    @Test
    void durationFromISOTest() {
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.durationFromISO(Value.of("P1D")))
                .isEqualTo(Value.of(86400000L));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.durationFromISO(Value.of("PT2H")))
                .isEqualTo(Value.of(7200000L));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.durationFromISO(Value.of("PT30M")))
                .isEqualTo(Value.of(1800000L));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.durationFromISO(Value.of("P1DT2H30M")))
                .isEqualTo(Value.of(95400000L));
    }

    @Test
    void durationFromISOWithPeriodTest() {
        final var oneYear = (long) (365.2425 * 24 * 60 * 60 * 1000);
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.durationFromISO(Value.of("P1Y")))
                .isEqualTo(Value.of(oneYear));

        final var oneMonth = (long) (30.436875 * 24 * 60 * 60 * 1000);
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.durationFromISO(Value.of("P1M")))
                .isEqualTo(Value.of(oneMonth));
    }

    @Test
    void durationFromISOInvalidFormat() {
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.durationFromISO(Value.of("INVALID")))
                .isInstanceOf(ErrorValue.class);
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.durationFromISO(Value.of("")))
                .isInstanceOf(ErrorValue.class);
    }

    @Test
    void durationToISOTest() {
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.durationToISOCompact(Value.of(7200000L)))
                .isEqualTo(Value.of("PT2H"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.durationToISOCompact(Value.of(9015000L)))
                .isEqualTo(Value.of("PT2H30M15S"));

        final var oneYear = (long) (365.2425 * 24 * 60 * 60 * 1000);
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.durationToISOVerbose(Value.of(oneYear)))
                .isEqualTo(Value.of("P1Y"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.durationToISOVerbose(Value.of(0L)))
                .isEqualTo(Value.of("PT0S"));
    }

    /* ######## TIMEZONE CONVERSION TESTS ######## */

    @Test
    void toZoneTest() {
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.toZone(timeValOf("2021-11-08T13:00:00Z"),
                Value.of("Europe/Berlin"))).isEqualTo(Value.of("2021-11-08T14:00:00+01:00"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.toZone(timeValOf("2021-11-08T13:00:00Z"),
                Value.of("US/Pacific"))).isEqualTo(Value.of("2021-11-08T05:00:00-08:00"));
    }

    @Test
    void toOffsetTest() {
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.toOffset(timeValOf("2021-11-08T13:00:00Z"),
                Value.of("+05:30"))).isEqualTo(Value.of("2021-11-08T18:30:00+05:30"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.toOffset(timeValOf("2021-11-08T13:00:00Z"),
                Value.of("+00:00"))).isEqualTo(Value.of("2021-11-08T13:00:00Z"));
    }

    /* ######## AGE CALCULATION TESTS ######## */

    @Test
    void ageInYearsTest() {
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.ageInYears(Value.of("1990-05-15"),
                Value.of("2021-11-08"))).isEqualTo(Value.of(31));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.ageInYears(Value.of("1990-05-15"),
                Value.of("2021-05-14"))).isEqualTo(Value.of(30));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.ageInYears(Value.of("2000-01-01"),
                Value.of("2000-12-31"))).isEqualTo(Value.of(0));
    }

    @Test
    void ageInMonthsTest() {
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.ageInMonths(Value.of("1990-05-15"),
                Value.of("1990-08-20"))).isEqualTo(Value.of(3L));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.ageInMonths(Value.of("1990-05-15"),
                Value.of("1991-05-15"))).isEqualTo(Value.of(12L));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.ageInMonths(Value.of("2020-01-01"),
                Value.of("2021-02-01"))).isEqualTo(Value.of(13L));
    }

    /* ######## DATE/TIME CONVERSION TESTS ######## */

    @Test
    void localConversionTest() {
        final var ldt = LocalDateTime.of(2021, 11, 8, 13, 0, 0);
        final var zdt = ZonedDateTime.of(ldt, ZoneId.systemDefault());
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.localIso(Value.of("2021-11-08T13:00:00")))
                .isEqualTo(Value.of(zdt.toInstant().toString()));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.localDin(Value.of("08.11.2021 13:00:00")))
                .isEqualTo(Value.of(zdt.toInstant().toString()));
    }

    @Test
    void dateTimeConversionTest() {
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.dateTimeAtOffset(Value.of("2021-11-08T13:12:35"),
                Value.of("+05:00"))).isEqualTo(Value.of("2021-11-08T08:12:35Z"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.dateTimeAtZone(Value.of("2021-11-08T13:12:35"),
                Value.of("Europe/Berlin"))).isEqualTo(Value.of("2021-11-08T12:12:35Z"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary
                .offsetDateTime(Value.of("2021-11-08T13:12:35+05:00"))).isEqualTo(Value.of("2021-11-08T08:12:35Z"));
    }

    @Test
    void timeConversionTest() {
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.offsetTime(Value.of("13:12:35+05:00")))
                .isEqualTo(Value.of("08:12:35"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.timeAtOffset(Value.of("13:12:35"),
                Value.of("-05:00"))).isEqualTo(Value.of("18:12:35"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.timeInZone(Value.of("13:12:35"),
                Value.of("2022-01-14"), Value.of("US/Pacific"))).isEqualTo(Value.of("21:12:35"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.timeAMPM(Value.of("08:12:35 PM")))
                .isEqualTo(Value.of("20:12:35"));
    }

    @Test
    void zoneIdHandlingTest() {
        final var defaultZoneId = ZoneId.of("AET", ZoneId.SHORT_IDS);
        try (MockedStatic<ZoneId> zoneIdMock = Mockito.mockStatic(ZoneId.class, Mockito.CALLS_REAL_METHODS)) {
            zoneIdMock.when(ZoneId::systemDefault).thenReturn(defaultZoneId);
            assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary
                    .dateTimeAtZone(Value.of("2021-11-08T13:12:35"), Value.of("")))
                    .isEqualTo(Value.of("2021-11-08T02:12:35Z"));
        }
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.dateTimeAtZone(Value.of("2021-11-08T13:12:35"),
                Value.of("EST"))).isEqualTo(Value.of("2021-11-08T18:12:35Z"));
    }

    /* ######## ERROR HANDLING TESTS ######## */

    @Test
    void invalidInputErrorHandlingTest() {
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.dateTimeAtOffset(Value.of("2021-11-08T13:12:35"),
                Value.of("INVALID"))).isInstanceOf(ErrorValue.class);
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.dateTimeAtZone(Value.of("2021-11-08T13:12:35"),
                Value.of("Invalid/Zone"))).isInstanceOf(ErrorValue.class);
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.toZone(timeValOf("2021-11-08T13:00:00Z"),
                Value.of("Invalid/Zone"))).isInstanceOf(ErrorValue.class);
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.epochSecond(Value.of("")))
                .isInstanceOf(ErrorValue.class);
    }

    @Test
    void temporalArithmeticBoundsTest() {
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary
                .plusSeconds(Value.of("+1000000000-01-01T00:00:00Z"), Value.of(Long.MAX_VALUE)))
                .isInstanceOf(ErrorValue.class);
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary
                .minusSeconds(Value.of("-1000000000-01-01T00:00:00Z"), Value.of(Long.MAX_VALUE)))
                .isInstanceOf(ErrorValue.class);
    }

    @Test
    void dateOnlyParsingTest() {
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.epochSecond(Value.of("2021-01-01")))
                .isEqualTo(Value.of(1609459200L));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.before(Value.of("2021-01-01"),
                Value.of("2021-01-02"))).isEqualTo(Value.TRUE);
    }

    @Test
    void fractionalSecondsHandlingTest() {
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary
                .plusSeconds(Value.of("2021-11-08T13:00:00.123456789Z"), Value.of(1L)))
                .isEqualTo(Value.of("2021-11-08T13:00:01.123456789Z"));
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary
                .offsetDateTime(Value.of("2021-11-08T13:12:35.999999999+05:00")))
                .isEqualTo(Value.of("2021-11-08T08:12:35.999999999Z"));
    }

    @Test
    void nullAndInvalidInputsTest() {
        final var valOfNull = Value.NULL;
        final var abc       = Value.of("abc");
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.epochSecond(abc)).isInstanceOf(ErrorValue.class);
        assertThat(io.sapl.functions.libraries.TemporalFunctionLibrary.validUTC(Value.NULL)).isEqualTo(Value.FALSE);
        assertThat(TemporalFunctionLibrary.validUTC(valOfNull)).isEqualTo(Value.FALSE);
    }

}
