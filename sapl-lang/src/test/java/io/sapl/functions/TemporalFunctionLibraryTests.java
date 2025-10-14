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
package io.sapl.functions;

import io.sapl.api.interpreter.Val;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.sapl.hamcrest.Matchers.val;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemporalFunctionLibraryTests {

    private static Val timeValOf(String utcIsoTime) {
        return Val.of(Instant.parse(utcIsoTime).toString());
    }

    /* ######## INSTANT MANIPULATION TESTS ######## */

    @Test
    void plusNanosMillisSecondsTest() {
        final var time = timeValOf("2021-11-08T13:00:00Z");
        assertThat(TemporalFunctionLibrary.plusNanos(time, Val.of(10_000_000_000L)), is(val("2021-11-08T13:00:10Z")));
        assertThat(TemporalFunctionLibrary.plusMillis(time, Val.of(10_000L)), is(val("2021-11-08T13:00:10Z")));
        assertThat(TemporalFunctionLibrary.plusSeconds(time, Val.of(10L)), is(val("2021-11-08T13:00:10Z")));
    }

    @Test
    void minusNanosMillisSecondsTest() {
        final var time = timeValOf("2021-11-08T13:00:00Z");
        assertThat(TemporalFunctionLibrary.minusNanos(time, Val.of(10_000_000_000L)), is(val("2021-11-08T12:59:50Z")));
        assertThat(TemporalFunctionLibrary.minusMillis(time, Val.of(10_000L)), is(val("2021-11-08T12:59:50Z")));
        assertThat(TemporalFunctionLibrary.minusSeconds(time, Val.of(10L)), is(val("2021-11-08T12:59:50Z")));
    }

    /* ######## DATE ARITHMETIC TESTS ######## */

    @Test
    void plusDaysMonthsYearsTest() {
        final var time = timeValOf("2021-11-08T13:00:00Z");
        assertThat(TemporalFunctionLibrary.plusDays(time, Val.of(5)), is(val("2021-11-13T13:00:00Z")));
        assertThat(TemporalFunctionLibrary.plusMonths(time, Val.of(2)), is(val("2022-01-08T13:00:00Z")));
        assertThat(TemporalFunctionLibrary.plusYears(time, Val.of(3)), is(val("2024-11-08T13:00:00Z")));
    }

    @Test
    void plusMonthsEdgeCases() {
        assertThat(TemporalFunctionLibrary.plusMonths(timeValOf("2021-01-31T13:00:00Z"), Val.of(1)),
                is(val("2021-02-28T13:00:00Z")));
        assertThat(TemporalFunctionLibrary.plusYears(timeValOf("2020-02-29T13:00:00Z"), Val.of(1)),
                is(val("2021-02-28T13:00:00Z")));
    }

    @Test
    void minusDaysMonthsYearsTest() {
        final var time = timeValOf("2021-11-08T13:00:00Z");
        assertThat(TemporalFunctionLibrary.minusDays(time, Val.of(5)), is(val("2021-11-03T13:00:00Z")));
        assertThat(TemporalFunctionLibrary.minusMonths(time, Val.of(2)), is(val("2021-09-08T13:00:00Z")));
        assertThat(TemporalFunctionLibrary.minusYears(time, Val.of(3)), is(val("2018-11-08T13:00:00Z")));
    }

    @Test
    void minusMonthsEdgeCases() {
        assertThat(TemporalFunctionLibrary.minusMonths(timeValOf("2021-03-31T13:00:00Z"), Val.of(1)),
                is(val("2021-02-28T13:00:00Z")));
        assertThat(TemporalFunctionLibrary.minusDays(timeValOf("2021-01-05T13:00:00Z"), Val.of(10)),
                is(val("2020-12-26T13:00:00Z")));
    }

    /* ######## CALENDAR TESTS ######## */

    @Test
    void calendarFunctionsTest() {
        final var time = timeValOf("2021-11-08T13:00:00Z");
        assertThat(TemporalFunctionLibrary.dayOfWeek(time), is(val("MONDAY")));
        assertThat(TemporalFunctionLibrary.dayOfYear(time), is(val(312)));
        assertThat(TemporalFunctionLibrary.weekOfYear(time), is(val(45)));
    }

    @Test
    void betweenTest() {
        final var today     = timeValOf("2021-11-08T13:00:00Z");
        final var yesterday = timeValOf("2021-11-07T13:00:00Z");
        final var tomorrow  = timeValOf("2021-11-09T13:00:00Z");

        assertThat(TemporalFunctionLibrary.between(today, yesterday, tomorrow), is(val(true)));
        assertThat(TemporalFunctionLibrary.between(tomorrow, yesterday, today), is(val(false)));
        assertThat(TemporalFunctionLibrary.between(today, today, today), is(val(true)));
        assertThat(TemporalFunctionLibrary.between(yesterday, yesterday, tomorrow), is(val(true)));
        assertThat(TemporalFunctionLibrary.between(tomorrow, today, tomorrow), is(val(true)));
    }

    @Test
    void timeBetweenTest() {
        final var today    = timeValOf("2021-11-08T13:00:00Z");
        final var tomorrow = timeValOf("2021-11-09T13:00:00Z");

        assertThat(TemporalFunctionLibrary.timeBetween(today, tomorrow, Val.of("DAYS")), is(val(1L)));
        assertThat(TemporalFunctionLibrary.timeBetween(today, tomorrow, Val.of("HOURS")), is(val(24L)));
        assertThat(TemporalFunctionLibrary.timeBetween(tomorrow, today, Val.of("DAYS")), is(val(-1L)));
        assertThat(TemporalFunctionLibrary.timeBetween(Val.of("2001-01-01"), Val.of("2002-01-01"), Val.of("YEARS")),
                is(val(1L)));
    }

    @Test
    void timeBetweenWithInvalidChronoUnit() {
        assertThrows(IllegalArgumentException.class,
                () -> TemporalFunctionLibrary.timeBetween(timeValOf("2021-11-08T13:00:00Z"),
                        timeValOf("2021-11-09T13:00:00Z"), Val.of("INVALID_UNIT")));
    }

    @Test
    void beforeAfterTest() {
        final var earlier = timeValOf("2021-11-08T13:00:00Z");
        final var later   = timeValOf("2021-11-08T13:00:01Z");
        assertThat(TemporalFunctionLibrary.before(earlier, later), is(val(true)));
        assertThat(TemporalFunctionLibrary.before(later, earlier), is(val(false)));
        assertThat(TemporalFunctionLibrary.after(earlier, later), is(val(false)));
        assertThat(TemporalFunctionLibrary.after(later, earlier), is(val(true)));
    }

    /* ######## EXTRACT PARTS TESTS ######## */

    @Test
    void extractPartsTest() {
        final var time = timeValOf("2021-11-08T13:17:23Z");
        assertThat(TemporalFunctionLibrary.dateOf(time), is(val("2021-11-08")));
        assertThat(TemporalFunctionLibrary.timeOf(time), is(val("13:17:23")));
        assertThat(TemporalFunctionLibrary.hourOf(time), is(val(13)));
        assertThat(TemporalFunctionLibrary.minuteOf(time), is(val(17)));
        assertThat(TemporalFunctionLibrary.secondOf(time), is(val(23)));
    }

    @Test
    void timeOfFormattingTest() {
        assertThat(TemporalFunctionLibrary.timeOf(timeValOf("2021-11-08T13:00:00Z")), is(val("13:00:00")));
        assertThat(TemporalFunctionLibrary.timeOf(timeValOf("2021-11-08T13:00:00.999999999Z")), is(val("13:00:00")));
    }

    /* ######## EPOCH TESTS ######## */

    @Test
    void epochConversionTest() {
        final var time = timeValOf("2021-11-08T13:00:00Z");
        assertThat(TemporalFunctionLibrary.epochSecond(time), is(val(1_636_376_400L)));
        assertThat(TemporalFunctionLibrary.epochMilli(time), is(val(1_636_376_400_000L)));
        assertThat(TemporalFunctionLibrary.ofEpochSecond(Val.of(1_636_376_400L)), is(val("2021-11-08T13:00:00Z")));
        assertThat(TemporalFunctionLibrary.ofEpochMilli(Val.of(1_636_376_400_000L)), is(val("2021-11-08T13:00:00Z")));
    }

    /* ######## DURATION TESTS ######## */

    @Test
    void durationConversionTest() {
        assertThat(TemporalFunctionLibrary.durationOfSeconds(Val.of(60)), is(val(60_000L)));
        assertThat(TemporalFunctionLibrary.durationOfMinutes(Val.of(1)), is(val(60_000L)));
        assertThat(TemporalFunctionLibrary.durationOfHours(Val.of(24)), is(val(86_400_000L)));
        assertThat(TemporalFunctionLibrary.durationOfDays(Val.of(1)), is(val(86_400_000L)));
    }

    /* ######## VALIDATION TESTS ######## */

    @Test
    void validUtcTest() {
        assertThat(TemporalFunctionLibrary.validUTC(Val.of("2021-11-08T13:00:00Z")), is(val(true)));
        assertThat(TemporalFunctionLibrary.validUTC(Val.of("2021-11-08T13:00:00.123456789Z")), is(val(true)));
        assertThat(TemporalFunctionLibrary.validUTC(Val.of("2021-11-08")), is(val(true)));
        assertThat(TemporalFunctionLibrary.validUTC(Val.of("XXX")), is(val(false)));
        assertThat(TemporalFunctionLibrary.validUTC(Val.NULL), is(val(false)));
    }

    @Test
    void validRFC3339Test() {
        assertThat(TemporalFunctionLibrary.validRFC3339(Val.of("2021-11-08T13:00:00Z")), is(val(true)));
        assertThat(TemporalFunctionLibrary.validRFC3339(Val.of("2021-11-08T13:00:00+05:00")), is(val(true)));
        assertThat(TemporalFunctionLibrary.validRFC3339(Val.of("2021-11-08T13:00:00")), is(val(false)));
        assertThat(TemporalFunctionLibrary.validRFC3339(Val.of("2021-11-08")), is(val(false)));
        assertThat(TemporalFunctionLibrary.validRFC3339(Val.of("")), is(val(false)));
    }

    /* ######## TEMPORAL BOUNDS TESTS ######## */

    @Test
    void startEndOfDayTest() {
        final var time = timeValOf("2021-11-08T13:45:30Z");
        assertThat(TemporalFunctionLibrary.startOfDay(time), is(val("2021-11-08T00:00:00Z")));
        assertThat(TemporalFunctionLibrary.endOfDay(time), is(val("2021-11-08T23:59:59.999999999Z")));
    }

    @Test
    void startEndOfWeekTest() {
        assertThat(TemporalFunctionLibrary.startOfWeek(timeValOf("2021-11-08T13:45:30Z")),
                is(val("2021-11-08T00:00:00Z")));
        assertThat(TemporalFunctionLibrary.endOfWeek(timeValOf("2021-11-08T13:45:30Z")),
                is(val("2021-11-14T23:59:59.999999999Z")));
    }

    @Test
    void startEndOfMonthTest() {
        assertThat(TemporalFunctionLibrary.startOfMonth(timeValOf("2021-11-08T13:45:30Z")),
                is(val("2021-11-01T00:00:00Z")));
        assertThat(TemporalFunctionLibrary.endOfMonth(timeValOf("2021-11-08T13:45:30Z")),
                is(val("2021-11-30T23:59:59.999999999Z")));
        assertThat(TemporalFunctionLibrary.endOfMonth(timeValOf("2021-02-15T13:45:30Z")),
                is(val("2021-02-28T23:59:59.999999999Z")));
        assertThat(TemporalFunctionLibrary.endOfMonth(timeValOf("2020-02-15T13:45:30Z")),
                is(val("2020-02-29T23:59:59.999999999Z")));
    }

    @Test
    void startEndOfYearTest() {
        assertThat(TemporalFunctionLibrary.startOfYear(timeValOf("2021-11-08T13:45:30Z")),
                is(val("2021-01-01T00:00:00Z")));
        assertThat(TemporalFunctionLibrary.endOfYear(timeValOf("2021-11-08T13:45:30Z")),
                is(val("2021-12-31T23:59:59.999999999Z")));
    }

    /* ######## TEMPORAL ROUNDING TESTS ######## */

    @Test
    void truncateTest() {
        final var time = timeValOf("2021-11-08T13:45:30.123Z");
        assertThat(TemporalFunctionLibrary.truncateToHour(time), is(val("2021-11-08T13:00:00Z")));
        assertThat(TemporalFunctionLibrary.truncateToDay(time), is(val("2021-11-08T00:00:00Z")));
        assertThat(TemporalFunctionLibrary.truncateToWeek(timeValOf("2021-11-10T13:45:30Z")),
                is(val("2021-11-08T00:00:00Z")));
        assertThat(TemporalFunctionLibrary.truncateToMonth(time), is(val("2021-11-01T00:00:00Z")));
        assertThat(TemporalFunctionLibrary.truncateToYear(time), is(val("2021-01-01T00:00:00Z")));
    }

    /* ######## ISO DURATION TESTS ######## */

    @Test
    void durationFromISOTest() {
        assertThat(TemporalFunctionLibrary.durationFromISO(Val.of("P1D")), is(val(86400000L)));
        assertThat(TemporalFunctionLibrary.durationFromISO(Val.of("PT2H")), is(val(7200000L)));
        assertThat(TemporalFunctionLibrary.durationFromISO(Val.of("PT30M")), is(val(1800000L)));
        assertThat(TemporalFunctionLibrary.durationFromISO(Val.of("P1DT2H30M")), is(val(95400000L)));
    }

    @Test
    void durationFromISOWithPeriodTest() {
        final var oneYear = (long) (365.2425 * 24 * 60 * 60 * 1000);
        assertThat(TemporalFunctionLibrary.durationFromISO(Val.of("P1Y")).getLong(), is(oneYear));

        final var oneMonth = (long) (30.436875 * 24 * 60 * 60 * 1000);
        assertThat(TemporalFunctionLibrary.durationFromISO(Val.of("P1M")).getLong(), is(oneMonth));
    }

    @Test
    void durationFromISOInvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> TemporalFunctionLibrary.durationFromISO(Val.of("INVALID")));
        assertThrows(IllegalArgumentException.class, () -> TemporalFunctionLibrary.durationFromISO(Val.of("")));
    }

    @Test
    void durationToISOTest() {
        assertThat(TemporalFunctionLibrary.durationToISOCompact(Val.of(7200000L)), is(val("PT2H")));
        assertThat(TemporalFunctionLibrary.durationToISOCompact(Val.of(9015000L)), is(val("PT2H30M15S")));

        final var oneYear = (long) (365.2425 * 24 * 60 * 60 * 1000);
        assertThat(TemporalFunctionLibrary.durationToISOVerbose(Val.of(oneYear)), is(val("P1Y")));
        assertThat(TemporalFunctionLibrary.durationToISOVerbose(Val.of(0L)), is(val("PT0S")));
    }

    /* ######## TIMEZONE CONVERSION TESTS ######## */

    @Test
    void toZoneTest() {
        assertThat(TemporalFunctionLibrary.toZone(timeValOf("2021-11-08T13:00:00Z"), Val.of("Europe/Berlin")),
                is(val("2021-11-08T14:00:00+01:00")));
        assertThat(TemporalFunctionLibrary.toZone(timeValOf("2021-11-08T13:00:00Z"), Val.of("US/Pacific")),
                is(val("2021-11-08T05:00:00-08:00")));
    }

    @Test
    void toOffsetTest() {
        assertThat(TemporalFunctionLibrary.toOffset(timeValOf("2021-11-08T13:00:00Z"), Val.of("+05:30")),
                is(val("2021-11-08T18:30:00+05:30")));
        assertThat(TemporalFunctionLibrary.toOffset(timeValOf("2021-11-08T13:00:00Z"), Val.of("+00:00")),
                is(val("2021-11-08T13:00:00Z")));
    }

    /* ######## AGE CALCULATION TESTS ######## */

    @Test
    void ageInYearsTest() {
        assertThat(TemporalFunctionLibrary.ageInYears(Val.of("1990-05-15"), Val.of("2021-11-08")), is(val(31)));
        assertThat(TemporalFunctionLibrary.ageInYears(Val.of("1990-05-15"), Val.of("2021-05-14")), is(val(30)));
        assertThat(TemporalFunctionLibrary.ageInYears(Val.of("2000-01-01"), Val.of("2000-12-31")), is(val(0)));
    }

    @Test
    void ageInMonthsTest() {
        assertThat(TemporalFunctionLibrary.ageInMonths(Val.of("1990-05-15"), Val.of("1990-08-20")), is(val(3L)));
        assertThat(TemporalFunctionLibrary.ageInMonths(Val.of("1990-05-15"), Val.of("1991-05-15")), is(val(12L)));
        assertThat(TemporalFunctionLibrary.ageInMonths(Val.of("2020-01-01"), Val.of("2021-02-01")), is(val(13L)));
    }

    /* ######## DATE/TIME CONVERSION TESTS ######## */

    @Test
    void localConversionTest() {
        final var ldt = LocalDateTime.of(2021, 11, 8, 13, 0, 0);
        final var zdt = ZonedDateTime.of(ldt, ZoneId.systemDefault());
        assertThat(TemporalFunctionLibrary.localIso(Val.of("2021-11-08T13:00:00")),
                is(val(zdt.toInstant().toString())));
        assertThat(TemporalFunctionLibrary.localDin(Val.of("08.11.2021 13:00:00")),
                is(val(zdt.toInstant().toString())));
    }

    @Test
    void dateTimeConversionTest() {
        assertThat(TemporalFunctionLibrary.dateTimeAtOffset(Val.of("2021-11-08T13:12:35"), Val.of("+05:00")),
                is(val("2021-11-08T08:12:35Z")));
        assertThat(TemporalFunctionLibrary.dateTimeAtZone(Val.of("2021-11-08T13:12:35"), Val.of("Europe/Berlin")),
                is(val("2021-11-08T12:12:35Z")));
        assertThat(TemporalFunctionLibrary.offsetDateTime(Val.of("2021-11-08T13:12:35+05:00")),
                is(val("2021-11-08T08:12:35Z")));
    }

    @Test
    void timeConversionTest() {
        assertThat(TemporalFunctionLibrary.offsetTime(Val.of("13:12:35+05:00")), is(val("08:12:35")));
        assertThat(TemporalFunctionLibrary.timeAtOffset(Val.of("13:12:35"), Val.of("-05:00")), is(val("18:12:35")));
        assertThat(TemporalFunctionLibrary.timeInZone(Val.of("13:12:35"), Val.of("2022-01-14"), Val.of("US/Pacific")),
                is(val("21:12:35")));
        assertThat(TemporalFunctionLibrary.timeAMPM(Val.of("08:12:35 PM")), is(val("20:12:35")));
    }

    @Test
    void zoneIdHandlingTest() {
        final var defaultZoneId = ZoneId.of("AET", ZoneId.SHORT_IDS);
        try (MockedStatic<ZoneId> zoneIdMock = Mockito.mockStatic(ZoneId.class, Mockito.CALLS_REAL_METHODS)) {
            zoneIdMock.when(ZoneId::systemDefault).thenReturn(defaultZoneId);
            assertThat(TemporalFunctionLibrary.dateTimeAtZone(Val.of("2021-11-08T13:12:35"), Val.of("")),
                    is(val("2021-11-08T02:12:35Z")));
        }
        assertThat(TemporalFunctionLibrary.dateTimeAtZone(Val.of("2021-11-08T13:12:35"), Val.of("EST")),
                is(val("2021-11-08T18:12:35Z")));
    }

    /* ######## ERROR HANDLING TESTS ######## */

    @Test
    void invalidInputErrorHandlingTest() {
        assertThrows(IllegalArgumentException.class,
                () -> TemporalFunctionLibrary.dateTimeAtOffset(Val.of("2021-11-08T13:12:35"), Val.of("INVALID")));
        assertThrows(IllegalArgumentException.class,
                () -> TemporalFunctionLibrary.dateTimeAtZone(Val.of("2021-11-08T13:12:35"), Val.of("Invalid/Zone")));
        assertThrows(IllegalArgumentException.class,
                () -> TemporalFunctionLibrary.toZone(timeValOf("2021-11-08T13:00:00Z"), Val.of("Invalid/Zone")));
        assertThrows(IllegalArgumentException.class, () -> TemporalFunctionLibrary.epochSecond(Val.of("")));
    }

    @Test
    void temporalArithmeticBoundsTest() {
        assertThrows(IllegalArgumentException.class, () -> TemporalFunctionLibrary
                .plusSeconds(Val.of("+1000000000-01-01T00:00:00Z"), Val.of(Long.MAX_VALUE)));
        assertThrows(IllegalArgumentException.class, () -> TemporalFunctionLibrary
                .minusSeconds(Val.of("-1000000000-01-01T00:00:00Z"), Val.of(Long.MAX_VALUE)));
    }

    @Test
    void dateOnlyParsingTest() {
        assertThat(TemporalFunctionLibrary.epochSecond(Val.of("2021-01-01")), is(val(1609459200L)));
        assertThat(TemporalFunctionLibrary.before(Val.of("2021-01-01"), Val.of("2021-01-02")), is(val(true)));
    }

    @Test
    void fractionalSecondsHandlingTest() {
        assertThat(TemporalFunctionLibrary.plusSeconds(Val.of("2021-11-08T13:00:00.123456789Z"), Val.of(1L)),
                is(val("2021-11-08T13:00:01.123456789Z")));
        assertThat(TemporalFunctionLibrary.offsetDateTime(Val.of("2021-11-08T13:12:35.999999999+05:00")),
                is(val("2021-11-08T08:12:35.999999999Z")));
    }

    @Test
    void nullAndInvalidInputsTest() {
        final var valOfNull = Val.of((String) null);
        final var abc       = Val.of("abc");

        assertThrows(Exception.class, () -> TemporalFunctionLibrary.epochSecond(Val.NULL));
        assertThrows(Exception.class, () -> TemporalFunctionLibrary.epochSecond(valOfNull));
        assertThrows(Exception.class, () -> TemporalFunctionLibrary.epochSecond(abc));
        assertThrows(Exception.class,
                () -> TemporalFunctionLibrary.between(Val.UNDEFINED, Val.UNDEFINED, Val.UNDEFINED));

        assertThat(TemporalFunctionLibrary.validUTC(Val.NULL).getBoolean(), is(false));
        assertThat(TemporalFunctionLibrary.validUTC(valOfNull).getBoolean(), is(false));
    }

}
