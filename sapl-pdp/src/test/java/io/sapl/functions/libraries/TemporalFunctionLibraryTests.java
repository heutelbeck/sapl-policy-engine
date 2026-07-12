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
package io.sapl.functions.libraries;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("TemporalFunctionLibrary")
class TemporalFunctionLibraryTests {

    private static NumberValue number(String value) {
        return (NumberValue) Value.of(new BigDecimal(value));
    }

    private static TextValue timeValOf(String utcIsoTime) {
        return Value.of(Instant.parse(utcIsoTime).toString());
    }

    @Test
    void whenLoadedIntoBrokerThenNoError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.load(new TemporalFunctionLibrary())).doesNotThrowAnyException();
    }

    @Test
    void whenPlusNanosMillisSecondsThenAddsCorrectTime() {
        val time = timeValOf("2021-11-08T13:00:00Z");
        assertThat(TemporalFunctionLibrary.plusNanos(time, Value.of(10_000_000_000L)))
                .isEqualTo(Value.of("2021-11-08T13:00:10Z"));
        assertThat(TemporalFunctionLibrary.plusMillis(time, Value.of(10_000L)))
                .isEqualTo(Value.of("2021-11-08T13:00:10Z"));
        assertThat(TemporalFunctionLibrary.plusSeconds(time, Value.of(10L)))
                .isEqualTo(Value.of("2021-11-08T13:00:10Z"));
    }

    @Test
    void whenMinusNanosMillisSecondsThenSubtractsCorrectTime() {
        val time = timeValOf("2021-11-08T13:00:00Z");
        assertThat(TemporalFunctionLibrary.minusNanos(time, Value.of(10_000_000_000L)))
                .isEqualTo(Value.of("2021-11-08T12:59:50Z"));
        assertThat(TemporalFunctionLibrary.minusMillis(time, Value.of(10_000L)))
                .isEqualTo(Value.of("2021-11-08T12:59:50Z"));
        assertThat(TemporalFunctionLibrary.minusSeconds(time, Value.of(10L)))
                .isEqualTo(Value.of("2021-11-08T12:59:50Z"));
    }

    @Test
    void whenPlusDaysMonthsYearsThenAddsCorrectDate() {
        val time = timeValOf("2021-11-08T13:00:00Z");
        assertThat(TemporalFunctionLibrary.plusDays(time, Value.of(5))).isEqualTo(Value.of("2021-11-13T13:00:00Z"));
        assertThat(TemporalFunctionLibrary.plusMonths(time, Value.of(2))).isEqualTo(Value.of("2022-01-08T13:00:00Z"));
        assertThat(TemporalFunctionLibrary.plusYears(time, Value.of(3))).isEqualTo(Value.of("2024-11-08T13:00:00Z"));
    }

    @Test
    void whenPlusMonthsOnEdgeCasesThenHandlesCorrectly() {
        assertThat(TemporalFunctionLibrary.plusMonths(timeValOf("2021-01-31T13:00:00Z"), Value.of(1)))
                .isEqualTo(Value.of("2021-02-28T13:00:00Z"));
        assertThat(TemporalFunctionLibrary.plusYears(timeValOf("2020-02-29T13:00:00Z"), Value.of(1)))
                .isEqualTo(Value.of("2021-02-28T13:00:00Z"));
    }

    @Test
    void whenMinusDaysMonthsYearsThenSubtractsCorrectDate() {
        val time = timeValOf("2021-11-08T13:00:00Z");
        assertThat(TemporalFunctionLibrary.minusDays(time, Value.of(5))).isEqualTo(Value.of("2021-11-03T13:00:00Z"));
        assertThat(TemporalFunctionLibrary.minusMonths(time, Value.of(2))).isEqualTo(Value.of("2021-09-08T13:00:00Z"));
        assertThat(TemporalFunctionLibrary.minusYears(time, Value.of(3))).isEqualTo(Value.of("2018-11-08T13:00:00Z"));
    }

    @Test
    void whenMinusMonthsOnEdgeCasesThenHandlesCorrectly() {
        assertThat(TemporalFunctionLibrary.minusMonths(timeValOf("2021-03-31T13:00:00Z"), Value.of(1)))
                .isEqualTo(Value.of("2021-02-28T13:00:00Z"));
        assertThat(TemporalFunctionLibrary.minusDays(timeValOf("2021-01-05T13:00:00Z"), Value.of(10)))
                .isEqualTo(Value.of("2020-12-26T13:00:00Z"));
    }

    @Test
    void whenCalendarFunctionsThenReturnsCorrectValues() {
        val time = timeValOf("2021-11-08T13:00:00Z");
        assertThat(TemporalFunctionLibrary.dayOfWeek(time)).isEqualTo(Value.of("MONDAY"));
        assertThat(TemporalFunctionLibrary.dayOfYear(time)).isEqualTo(Value.of(312));
        assertThat(TemporalFunctionLibrary.weekOfYear(time)).isEqualTo(Value.of(45));
    }

    @Test
    void whenWeekOfYearAtYearBoundaryThenReturnsIsoWeek() {
        assertThat(TemporalFunctionLibrary.weekOfYear(timeValOf("2021-01-01T00:00:00Z"))).isEqualTo(Value.of(53));
        assertThat(TemporalFunctionLibrary.weekOfYear(timeValOf("2020-12-31T00:00:00Z"))).isEqualTo(Value.of(53));
        assertThat(TemporalFunctionLibrary.weekOfYear(timeValOf("2026-01-01T00:00:00Z"))).isEqualTo(Value.of(1));
    }

    @Test
    void whenDurationOfOverflowsThenReturnsError() {
        val huge = Value.of(Long.MAX_VALUE);
        assertThat(TemporalFunctionLibrary.durationOfDays(huge)).isInstanceOf(ErrorValue.class);
        assertThat(TemporalFunctionLibrary.durationOfHours(huge)).isInstanceOf(ErrorValue.class);
        assertThat(TemporalFunctionLibrary.durationOfMinutes(huge)).isInstanceOf(ErrorValue.class);
        assertThat(TemporalFunctionLibrary.durationOfSeconds(huge)).isInstanceOf(ErrorValue.class);
    }

    @Test
    void whenBetweenThenChecksRangeCorrectly() {
        val today     = timeValOf("2021-11-08T13:00:00Z");
        val yesterday = timeValOf("2021-11-07T13:00:00Z");
        val tomorrow  = timeValOf("2021-11-09T13:00:00Z");

        assertThat(TemporalFunctionLibrary.between(today, yesterday, tomorrow)).isEqualTo(Value.TRUE);
        assertThat(TemporalFunctionLibrary.between(tomorrow, yesterday, today)).isEqualTo(Value.FALSE);
        assertThat(TemporalFunctionLibrary.between(today, today, today)).isEqualTo(Value.TRUE);
        assertThat(TemporalFunctionLibrary.between(yesterday, yesterday, tomorrow)).isEqualTo(Value.TRUE);
        assertThat(TemporalFunctionLibrary.between(tomorrow, today, tomorrow)).isEqualTo(Value.TRUE);
    }

    @Test
    void whenTimeBetweenThenCalculatesDifferenceCorrectly() {
        val today    = timeValOf("2021-11-08T13:00:00Z");
        val tomorrow = timeValOf("2021-11-09T13:00:00Z");

        assertThat(TemporalFunctionLibrary.timeBetween(today, tomorrow, Value.of("DAYS"))).isEqualTo(Value.of(1L));
        assertThat(TemporalFunctionLibrary.timeBetween(today, tomorrow, Value.of("HOURS"))).isEqualTo(Value.of(24L));
        assertThat(TemporalFunctionLibrary.timeBetween(tomorrow, today, Value.of("DAYS"))).isEqualTo(Value.of(-1L));
        assertThat(
                TemporalFunctionLibrary.timeBetween(Value.of("2001-01-01"), Value.of("2002-01-01"), Value.of("YEARS")))
                .isEqualTo(Value.of(1L));
    }

    @Test
    void whenTimeBetweenWithInvalidChronoUnitThenReturnsError() {
        assertThat(TemporalFunctionLibrary.timeBetween(timeValOf("2021-11-08T13:00:00Z"),
                timeValOf("2021-11-09T13:00:00Z"), Value.of("INVALID_UNIT"))).isInstanceOf(ErrorValue.class);
    }

    @Test
    void whenBeforeAfterThenComparesCorrectly() {
        val earlier = timeValOf("2021-11-08T13:00:00Z");
        val later   = timeValOf("2021-11-08T13:00:01Z");
        assertThat(TemporalFunctionLibrary.before(earlier, later)).isEqualTo(Value.TRUE);
        assertThat(TemporalFunctionLibrary.before(later, earlier)).isEqualTo(Value.FALSE);
        assertThat(TemporalFunctionLibrary.after(earlier, later)).isEqualTo(Value.FALSE);
        assertThat(TemporalFunctionLibrary.after(later, earlier)).isEqualTo(Value.TRUE);
    }

    @Test
    void whenExtractPartsThenReturnsCorrectComponents() {
        val time = timeValOf("2021-11-08T13:17:23Z");
        assertThat(TemporalFunctionLibrary.dateOf(time)).isEqualTo(Value.of("2021-11-08"));
        assertThat(TemporalFunctionLibrary.timeOf(time)).isEqualTo(Value.of("13:17:23"));
        assertThat(TemporalFunctionLibrary.hourOf(time)).isEqualTo(Value.of(13));
        assertThat(TemporalFunctionLibrary.minuteOf(time)).isEqualTo(Value.of(17));
        assertThat(TemporalFunctionLibrary.secondOf(time)).isEqualTo(Value.of(23));
    }

    @Test
    void whenTimeOfFormattingThenReturnsFormattedTime() {
        assertThat(TemporalFunctionLibrary.timeOf(timeValOf("2021-11-08T13:00:00Z"))).isEqualTo(Value.of("13:00:00"));
        assertThat(TemporalFunctionLibrary.timeOf(timeValOf("2021-11-08T13:00:00.999999999Z")))
                .isEqualTo(Value.of("13:00:00"));
    }

    @Test
    void whenEpochConversionThenConvertsCorrectly() {
        val time = timeValOf("2021-11-08T13:00:00Z");
        assertThat(TemporalFunctionLibrary.epochSecond(time)).isEqualTo(Value.of(1_636_376_400L));
        assertThat(TemporalFunctionLibrary.epochMilli(time)).isEqualTo(Value.of(1_636_376_400_000L));
        assertThat(TemporalFunctionLibrary.ofEpochSecond(Value.of(1_636_376_400L)))
                .isEqualTo(Value.of("2021-11-08T13:00:00Z"));
        assertThat(TemporalFunctionLibrary.ofEpochMilli(Value.of(1_636_376_400_000L)))
                .isEqualTo(Value.of("2021-11-08T13:00:00Z"));
    }

    @Test
    void whenEpochInputExceedsLongRangeThenReturnsError() {
        assertThat(TemporalFunctionLibrary.ofEpochSecond(number("9223372036854775808"))).isInstanceOf(ErrorValue.class);
        assertThat(TemporalFunctionLibrary.ofEpochMilli(number("9223372036854775808"))).isInstanceOf(ErrorValue.class);
    }

    @Test
    void whenDurationConversionThenConvertsToMillis() {
        assertThat(TemporalFunctionLibrary.durationOfSeconds(Value.of(60))).isEqualTo(Value.of(60_000L));
        assertThat(TemporalFunctionLibrary.durationOfMinutes(Value.of(1))).isEqualTo(Value.of(60_000L));
        assertThat(TemporalFunctionLibrary.durationOfHours(Value.of(24))).isEqualTo(Value.of(86_400_000L));
        assertThat(TemporalFunctionLibrary.durationOfDays(Value.of(1))).isEqualTo(Value.of(86_400_000L));
    }

    @Test
    void whenFractionalDurationResolvesToWholeMillisecondsThenConvertsToMillis() {
        assertThat(TemporalFunctionLibrary.durationOfSeconds(number("20.5"))).isEqualTo(Value.of(20_500L));
        assertThat(TemporalFunctionLibrary.durationOfMinutes(number("2.5"))).isEqualTo(Value.of(150_000L));
    }

    @Test
    void whenTemporalIntegerInputIsFractionalThenReturnsError() {
        val time = timeValOf("2021-11-08T13:00:00Z");

        assertThat(TemporalFunctionLibrary.plusSeconds(time, number("1.5"))).isInstanceOf(ErrorValue.class);
        assertThat(TemporalFunctionLibrary.durationToISOCompact(number("1.5"))).isInstanceOf(ErrorValue.class);
    }

    @Test
    void whenValidUtcThenValidatesCorrectly() {
        assertThat(TemporalFunctionLibrary.validUTC(Value.of("2021-11-08T13:00:00Z"))).isEqualTo(Value.TRUE);
        assertThat(TemporalFunctionLibrary.validUTC(Value.of("2021-11-08T13:00:00.123456789Z"))).isEqualTo(Value.TRUE);
        assertThat(TemporalFunctionLibrary.validUTC(Value.of("2021-11-08"))).isEqualTo(Value.TRUE);
        assertThat(TemporalFunctionLibrary.validUTC(Value.of("XXX"))).isEqualTo(Value.FALSE);
        assertThat(TemporalFunctionLibrary.validUTC(Value.NULL)).isEqualTo(Value.FALSE);
    }

    @Test
    void whenValidRfc3339ThenValidatesCorrectly() {
        assertThat(TemporalFunctionLibrary.validRFC3339(Value.of("2021-11-08T13:00:00Z"))).isEqualTo(Value.TRUE);
        assertThat(TemporalFunctionLibrary.validRFC3339(Value.of("2021-11-08T13:00:00+05:00"))).isEqualTo(Value.TRUE);
        assertThat(TemporalFunctionLibrary.validRFC3339(Value.of("2021-11-08T13:00:00"))).isEqualTo(Value.FALSE);
        assertThat(TemporalFunctionLibrary.validRFC3339(Value.of("2021-11-08"))).isEqualTo(Value.FALSE);
        assertThat(TemporalFunctionLibrary.validRFC3339(Value.of(""))).isEqualTo(Value.FALSE);
    }

    @Test
    void whenStartEndOfDayThenReturnsBoundaries() {
        val time = timeValOf("2021-11-08T13:45:30Z");
        assertThat(TemporalFunctionLibrary.startOfDay(time)).isEqualTo(Value.of("2021-11-08T00:00:00Z"));
        assertThat(TemporalFunctionLibrary.endOfDay(time)).isEqualTo(Value.of("2021-11-08T23:59:59.999999999Z"));
    }

    @Test
    void whenStartEndOfWeekThenReturnsBoundaries() {
        assertThat(TemporalFunctionLibrary.startOfWeek(timeValOf("2021-11-08T13:45:30Z")))
                .isEqualTo(Value.of("2021-11-08T00:00:00Z"));
        assertThat(TemporalFunctionLibrary.endOfWeek(timeValOf("2021-11-08T13:45:30Z")))
                .isEqualTo(Value.of("2021-11-14T23:59:59.999999999Z"));
    }

    @Test
    void whenStartEndOfMonthThenReturnsBoundaries() {
        assertThat(TemporalFunctionLibrary.startOfMonth(timeValOf("2021-11-08T13:45:30Z")))
                .isEqualTo(Value.of("2021-11-01T00:00:00Z"));
        assertThat(TemporalFunctionLibrary.endOfMonth(timeValOf("2021-11-08T13:45:30Z")))
                .isEqualTo(Value.of("2021-11-30T23:59:59.999999999Z"));
        assertThat(TemporalFunctionLibrary.endOfMonth(timeValOf("2021-02-15T13:45:30Z")))
                .isEqualTo(Value.of("2021-02-28T23:59:59.999999999Z"));
        assertThat(TemporalFunctionLibrary.endOfMonth(timeValOf("2020-02-15T13:45:30Z")))
                .isEqualTo(Value.of("2020-02-29T23:59:59.999999999Z"));
    }

    @Test
    void whenStartEndOfYearThenReturnsBoundaries() {
        assertThat(TemporalFunctionLibrary.startOfYear(timeValOf("2021-11-08T13:45:30Z")))
                .isEqualTo(Value.of("2021-01-01T00:00:00Z"));
        assertThat(TemporalFunctionLibrary.endOfYear(timeValOf("2021-11-08T13:45:30Z")))
                .isEqualTo(Value.of("2021-12-31T23:59:59.999999999Z"));
    }

    @Test
    void whenTruncateThenRoundsToSpecifiedUnit() {
        val time = timeValOf("2021-11-08T13:45:30.123Z");
        assertThat(TemporalFunctionLibrary.truncateToHour(time)).isEqualTo(Value.of("2021-11-08T13:00:00Z"));
        assertThat(TemporalFunctionLibrary.truncateToDay(time)).isEqualTo(Value.of("2021-11-08T00:00:00Z"));
        assertThat(TemporalFunctionLibrary.truncateToWeek(timeValOf("2021-11-10T13:45:30Z")))
                .isEqualTo(Value.of("2021-11-08T00:00:00Z"));
        assertThat(TemporalFunctionLibrary.truncateToMonth(time)).isEqualTo(Value.of("2021-11-01T00:00:00Z"));
        assertThat(TemporalFunctionLibrary.truncateToYear(time)).isEqualTo(Value.of("2021-01-01T00:00:00Z"));
    }

    @Test
    void whenDurationFromIsoThenParsesCorrectly() {
        assertThat(TemporalFunctionLibrary.durationFromISO(Value.of("P1D"))).isEqualTo(Value.of(86400000L));
        assertThat(TemporalFunctionLibrary.durationFromISO(Value.of("PT2H"))).isEqualTo(Value.of(7200000L));
        assertThat(TemporalFunctionLibrary.durationFromISO(Value.of("PT30M"))).isEqualTo(Value.of(1800000L));
        assertThat(TemporalFunctionLibrary.durationFromISO(Value.of("P1DT2H30M"))).isEqualTo(Value.of(95400000L));
    }

    @Test
    void whenDurationFromIsoWithPeriodThenParsesCorrectly() {
        val oneYear = (long) (365.2425 * 24 * 60 * 60 * 1000);
        assertThat(TemporalFunctionLibrary.durationFromISO(Value.of("P1Y"))).isEqualTo(Value.of(oneYear));

        val oneMonth = (long) (30.436875 * 24 * 60 * 60 * 1000);
        assertThat(TemporalFunctionLibrary.durationFromISO(Value.of("P1M"))).isEqualTo(Value.of(oneMonth));
    }

    @Test
    void whenDurationFromIsoInvalidFormatThenReturnsError() {
        assertThat(TemporalFunctionLibrary.durationFromISO(Value.of("INVALID"))).isInstanceOf(ErrorValue.class);
        assertThat(TemporalFunctionLibrary.durationFromISO(Value.of(""))).isInstanceOf(ErrorValue.class);
    }

    @Test
    void whenDurationToIsoThenFormatsCorrectly() {
        assertThat(TemporalFunctionLibrary.durationToISOCompact(Value.of(7200000L))).isEqualTo(Value.of("PT2H"));
        assertThat(TemporalFunctionLibrary.durationToISOCompact(Value.of(9015000L))).isEqualTo(Value.of("PT2H30M15S"));

        val oneYear = (long) (365.2425 * 24 * 60 * 60 * 1000);
        assertThat(TemporalFunctionLibrary.durationToISOVerbose(Value.of(oneYear))).isEqualTo(Value.of("P1Y"));
        assertThat(TemporalFunctionLibrary.durationToISOVerbose(Value.of(0L))).isEqualTo(Value.of("PT0S"));
    }

    @Test
    void whenToZoneThenConvertsTimezone() {
        assertThat(TemporalFunctionLibrary.toZone(timeValOf("2021-11-08T13:00:00Z"), Value.of("Europe/Berlin")))
                .isEqualTo(Value.of("2021-11-08T14:00:00+01:00"));
        assertThat(TemporalFunctionLibrary.toZone(timeValOf("2021-11-08T13:00:00Z"), Value.of("US/Pacific")))
                .isEqualTo(Value.of("2021-11-08T05:00:00-08:00"));
    }

    @Test
    void whenToOffsetThenAppliesOffset() {
        assertThat(TemporalFunctionLibrary.toOffset(timeValOf("2021-11-08T13:00:00Z"), Value.of("+05:30")))
                .isEqualTo(Value.of("2021-11-08T18:30:00+05:30"));
        assertThat(TemporalFunctionLibrary.toOffset(timeValOf("2021-11-08T13:00:00Z"), Value.of("+00:00")))
                .isEqualTo(Value.of("2021-11-08T13:00:00Z"));
    }

    @Test
    void whenAgeInYearsThenCalculatesCorrectly() {
        assertThat(TemporalFunctionLibrary.ageInYears(Value.of("1990-05-15"), Value.of("2021-11-08")))
                .isEqualTo(Value.of(31));
        assertThat(TemporalFunctionLibrary.ageInYears(Value.of("1990-05-15"), Value.of("2021-05-14")))
                .isEqualTo(Value.of(30));
        assertThat(TemporalFunctionLibrary.ageInYears(Value.of("2000-01-01"), Value.of("2000-12-31")))
                .isEqualTo(Value.of(0));
    }

    @Test
    void whenAgeInMonthsThenCalculatesCorrectly() {
        assertThat(TemporalFunctionLibrary.ageInMonths(Value.of("1990-05-15"), Value.of("1990-08-20")))
                .isEqualTo(Value.of(3L));
        assertThat(TemporalFunctionLibrary.ageInMonths(Value.of("1990-05-15"), Value.of("1991-05-15")))
                .isEqualTo(Value.of(12L));
        assertThat(TemporalFunctionLibrary.ageInMonths(Value.of("2020-01-01"), Value.of("2021-02-01")))
                .isEqualTo(Value.of(13L));
    }

    @Test
    void whenLocalConversionThenConvertsToUtc() {
        val ldt = LocalDateTime.of(2021, Month.NOVEMBER, 8, 13, 0, 0);
        val zdt = ZonedDateTime.of(ldt, ZoneId.systemDefault());
        assertThat(TemporalFunctionLibrary.localIso(Value.of("2021-11-08T13:00:00")))
                .isEqualTo(Value.of(zdt.toInstant().toString()));
        assertThat(TemporalFunctionLibrary.localDin(Value.of("08.11.2021 13:00:00")))
                .isEqualTo(Value.of(zdt.toInstant().toString()));
    }

    @Test
    void whenDateTimeConversionThenConvertsCorrectly() {
        assertThat(TemporalFunctionLibrary.dateTimeAtOffset(Value.of("2021-11-08T13:12:35"), Value.of("+05:00")))
                .isEqualTo(Value.of("2021-11-08T08:12:35Z"));
        assertThat(TemporalFunctionLibrary.dateTimeAtZone(Value.of("2021-11-08T13:12:35"), Value.of("Europe/Berlin")))
                .isEqualTo(Value.of("2021-11-08T12:12:35Z"));
        assertThat(TemporalFunctionLibrary.offsetDateTime(Value.of("2021-11-08T13:12:35+05:00")))
                .isEqualTo(Value.of("2021-11-08T08:12:35Z"));
    }

    @Test
    void whenTimeConversionThenConvertsCorrectly() {
        assertThat(TemporalFunctionLibrary.offsetTime(Value.of("13:12:35+05:00"))).isEqualTo(Value.of("08:12:35"));
        assertThat(TemporalFunctionLibrary.timeAtOffset(Value.of("13:12:35"), Value.of("-05:00")))
                .isEqualTo(Value.of("18:12:35"));
        assertThat(TemporalFunctionLibrary.timeInZone(Value.of("13:12:35"), Value.of("2022-01-14"),
                Value.of("US/Pacific"))).isEqualTo(Value.of("21:12:35"));
        assertThat(TemporalFunctionLibrary.timeAMPM(Value.of("08:12:35 PM"))).isEqualTo(Value.of("20:12:35"));
    }

    @Test
    void whenZoneIdHandlingThenHandlesShortIdsAndEmptyString() {
        val defaultZoneId = ZoneId.of("AET", ZoneId.SHORT_IDS);
        try (MockedStatic<ZoneId> zoneIdMock = Mockito.mockStatic(ZoneId.class, Mockito.CALLS_REAL_METHODS)) {
            zoneIdMock.when(ZoneId::systemDefault).thenReturn(defaultZoneId);
            assertThat(TemporalFunctionLibrary.dateTimeAtZone(Value.of("2021-11-08T13:12:35"), Value.of("")))
                    .isEqualTo(Value.of("2021-11-08T02:12:35Z"));
        }
        assertThat(TemporalFunctionLibrary.dateTimeAtZone(Value.of("2021-11-08T13:12:35"), Value.of("EST")))
                .isEqualTo(Value.of("2021-11-08T18:12:35Z"));
    }

    @Test
    void whenInvalidInputProvidedThenReturnsError() {
        assertThat(TemporalFunctionLibrary.dateTimeAtOffset(Value.of("2021-11-08T13:12:35"), Value.of("INVALID")))
                .isInstanceOf(ErrorValue.class);
        assertThat(TemporalFunctionLibrary.dateTimeAtZone(Value.of("2021-11-08T13:12:35"), Value.of("Invalid/Zone")))
                .isInstanceOf(ErrorValue.class);
        assertThat(TemporalFunctionLibrary.toZone(timeValOf("2021-11-08T13:00:00Z"), Value.of("Invalid/Zone")))
                .isInstanceOf(ErrorValue.class);
        assertThat(TemporalFunctionLibrary.epochSecond(Value.of(""))).isInstanceOf(ErrorValue.class);
    }

    @Test
    void whenTemporalArithmeticOverflowsThenReturnsError() {
        assertThat(
                TemporalFunctionLibrary.plusSeconds(Value.of("+1000000000-01-01T00:00:00Z"), Value.of(Long.MAX_VALUE)))
                .isInstanceOf(ErrorValue.class);
        assertThat(
                TemporalFunctionLibrary.minusSeconds(Value.of("-1000000000-01-01T00:00:00Z"), Value.of(Long.MAX_VALUE)))
                .isInstanceOf(ErrorValue.class);
    }

    @Test
    void whenDateOnlyParsingThenParsesCorrectly() {
        assertThat(TemporalFunctionLibrary.epochSecond(Value.of("2021-01-01"))).isEqualTo(Value.of(1609459200L));
        assertThat(TemporalFunctionLibrary.before(Value.of("2021-01-01"), Value.of("2021-01-02")))
                .isEqualTo(Value.TRUE);
    }

    @Test
    void whenFractionalSecondsHandlingThenPreservesFractions() {
        assertThat(TemporalFunctionLibrary.plusSeconds(Value.of("2021-11-08T13:00:00.123456789Z"), Value.of(1L)))
                .isEqualTo(Value.of("2021-11-08T13:00:01.123456789Z"));
        assertThat(TemporalFunctionLibrary.offsetDateTime(Value.of("2021-11-08T13:12:35.999999999+05:00")))
                .isEqualTo(Value.of("2021-11-08T08:12:35.999999999Z"));
    }

    @Test
    void whenNullAndInvalidInputsThenHandlesGracefully() {
        val valOfNull = Value.NULL;
        val abc       = Value.of("abc");
        assertThat(TemporalFunctionLibrary.epochSecond(abc)).isInstanceOf(ErrorValue.class);
        assertThat(TemporalFunctionLibrary.validUTC(Value.NULL)).isEqualTo(Value.FALSE);
        assertThat(TemporalFunctionLibrary.validUTC(valOfNull)).isEqualTo(Value.FALSE);
    }

}
