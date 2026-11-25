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

import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.stream.Stream;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

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

    @ParameterizedTest(name = "{0} is {1} milliseconds")
    @MethodSource("isoDurations")
    void whenIsoDurationIsValidThenReturnsExactMilliseconds(String duration, long expected) {
        assertThat(TemporalFunctionLibrary.durationFromISO(Value.of(duration))).isEqualTo(Value.of(expected));
    }

    static Stream<Arguments> isoDurations() {
        return Stream.of(arguments("P1D", 86_400_000L), arguments("PT2H", 7_200_000L), arguments("PT30M", 1_800_000L),
                arguments("P1DT2H30M", 95_400_000L), arguments("P1Y", 31_556_952_000L),
                arguments("P1M", 2_629_746_000L), arguments("P1Y2M3DT4H5M6S", 37_090_350_000L),
                arguments("p2w", 1_209_600_000L), arguments("P1MT1M", 2_629_806_000L), arguments("-PT0,1259S", -125L),
                arguments("PT0.01S", 10L), arguments("PT0.0000000001S", 0L),
                arguments("PT9223372036854775.807S", Long.MAX_VALUE),
                arguments("-PT9223372036854775.808S", Long.MIN_VALUE));
    }

    @ParameterizedTest(name = "Reject duration [{0}]")
    @ValueSource(strings = { "", "INVALID", "P", "PT", "P1DT", "P1W1D", "P1D1M", "PT1M1M", "-P-1Y", "PT1H-30M", "+PT1S",
            "PT0.S", "PT1.5M", "PT9223372036854775.808S", "-PT9223372036854775.809S" })
    void whenIsoDurationIsInvalidThenReturnsError(String duration) {
        assertThat(TemporalFunctionLibrary.durationFromISO(Value.of(duration))).isInstanceOf(ErrorValue.class);
    }

    @ParameterizedTest(name = "Compact duration for {0} milliseconds is {1}")
    @MethodSource("compactDurations")
    void whenFormattingMillisecondsThenCompactDurationCanBeReadAgain(long millis, String expected) {
        val compact = TemporalFunctionLibrary.durationToISOCompact(Value.of(millis));
        assertThat(compact).isEqualTo(Value.of(expected));
        assertThat(TemporalFunctionLibrary.durationFromISO((TextValue) compact)).isEqualTo(Value.of(millis));
    }

    static Stream<Arguments> compactDurations() {
        return Stream.of(arguments(-500L, "-PT0.5S"), arguments(-90_061_001L, "-PT25H1M1.001S"),
                arguments(3_602_000L, "PT1H2S"), arguments(Long.MIN_VALUE, "-PT2562047788015H12M55.808S"),
                arguments(Long.MAX_VALUE, "PT2562047788015H12M55.807S"));
    }

    @Test
    void whenDurationToIsoThenFormatsCorrectly() {
        assertThat(TemporalFunctionLibrary.durationToISOCompact(Value.of(7200000L))).isEqualTo(Value.of("PT2H"));
        assertThat(TemporalFunctionLibrary.durationToISOCompact(Value.of(9015000L))).isEqualTo(Value.of("PT2H30M15S"));

        val oneYear = 31_556_952_000L;
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
        assertThat(TemporalFunctionLibrary.localIso(Value.of("2021-11-08T13:00:00")))
                .isEqualTo(Value.of("2021-11-08T13:00:00Z"));
        assertThat(TemporalFunctionLibrary.localDin(Value.of("08.11.2021 13:00:00")))
                .isEqualTo(Value.of("2021-11-08T13:00:00Z"));
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
    void whenDefaultZoneDiffersThenFunctionsUseUtc() {
        val broker = new DefaultFunctionBroker();
        broker.load(new TemporalFunctionLibrary());
        val configuredZone = ZoneId.of("Asia/Tokyo");
        val calls          = List.of(new FunctionInvocation("time.localIso", List.of(Value.of("2021-11-08T13:12:35"))),
                new FunctionInvocation("time.localDin", List.of(Value.of("08.11.2021 13:12:35"))),
                new FunctionInvocation("time.dateTimeAtZone", List.of(Value.of("2021-11-08T13:12:35"), Value.of(""))),
                new FunctionInvocation("time.timeInZone",
                        List.of(Value.of("13:12:35"), Value.of("2021-11-08"), Value.of(""))),
                new FunctionInvocation("time.toZone", List.of(Value.of("2021-11-08T13:12:35Z"), Value.of(""))),
                new FunctionInvocation("time.timeBetween", List.of(Value.of("2021-01-01T23:30:00Z"),
                        Value.of("2021-01-08T00:30:00Z"), Value.of("WEEKS"))));
        try (MockedStatic<ZoneId> zoneIdMock = Mockito.mockStatic(ZoneId.class, Mockito.CALLS_REAL_METHODS)) {
            zoneIdMock.when(ZoneId::systemDefault).thenReturn(configuredZone);
            assertThat(calls.stream().map(broker::evaluateFunction).toList()).containsExactly(
                    Value.of("2021-11-08T13:12:35Z"), Value.of("2021-11-08T13:12:35Z"),
                    Value.of("2021-11-08T13:12:35Z"), Value.of("13:12:35"), Value.of("2021-11-08T13:12:35Z"),
                    Value.of(1L));
        }
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

    static Stream<Arguments> instantForms() {
        return Stream.of(arguments("2000-02-29t01:02:03z", true, true),
                arguments("2024-02-29T23:59:59.1-18:00", true, true),
                arguments("2024-02-29T23:59:59.123456789+18:00", true, true),
                arguments("2017-01-01T00:59:60.123456789+01:00", true, true),
                arguments("0000-01-01T00:00:00+18:00", true, true), arguments("9999-12-31T23:59:59-18:00", true, true),
                arguments("2024-01-01T00:00:00+01", false, false), arguments("2024-01-01T00:00:00+0100", false, false),
                arguments("2024-01-01T00:00:00+01:00:30", false, false),
                arguments("2024-01-01T00:00:00+18:01", false, false),
                arguments("2024-01-01T00:00:00-19:00", false, false),
                arguments("2024-01-01T00:00:00+01:60", false, false), arguments("2024-01-01T00:00:00.Z", false, false),
                arguments("2024-01-01T00:00:00.1234567890Z", false, false),
                arguments("2024-01-01T00:00Z", false, false), arguments("2024-01-01T24:00:00Z", false, false),
                arguments("2023-02-29T00:00:00Z", false, false), arguments("1900-02-29T00:00:00Z", false, false),
                arguments("2024-04-31T00:00:00Z", false, false), arguments("2016-12-31T23:59:60+01:00", false, false),
                arguments("2024-01-01 00:00:00Z", false, false), arguments("+10000-01-01T00:00:00Z", false, false),
                arguments("-0001-01-01T00:00:00Z", false, true), arguments("0000-02-29", false, true),
                arguments("2000-02-29", false, true), arguments("9999-12-31", false, true));
    }

    @MethodSource("instantForms")
    @ParameterizedTest(name = "{0}")
    @DisplayName("Timestamp validity follows the SAPL instant profile")
    void whenTimestampHasProfileFormThenValidityIsPortable(String text, boolean fullTimestamp, boolean instantOrDate) {
        val input = Value.of(text);
        assertThat(TemporalFunctionLibrary.validRFC3339(input)).isEqualTo(Value.of(fullTimestamp));
        assertThat(TemporalFunctionLibrary.validUTC(input)).isEqualTo(Value.of(instantOrDate));
    }

    @Test
    @DisplayName("UTC leap seconds preserve fractional precision across offsets")
    void whenLeapSecondIsWrittenWithOffsetThenComparisonsPreserveNanoseconds() {
        val shifted = Value.of("2017-01-01T00:59:60.123456789+01:00");
        val clamped = Value.of("2016-12-31T23:59:59.123456789Z");
        val later   = Value.of("2016-12-31T23:59:59.123456790Z");
        assertThat(TemporalFunctionLibrary.before(shifted, later)).isEqualTo(Value.TRUE);
        assertThat(TemporalFunctionLibrary.after(later, shifted)).isEqualTo(Value.TRUE);
        assertThat(TemporalFunctionLibrary.between(shifted, clamped, later)).isEqualTo(Value.TRUE);
        assertThat(TemporalFunctionLibrary.timeBetween(clamped, shifted, Value.of("NANOS"))).isEqualTo(Value.of(0));
        assertThat(TemporalFunctionLibrary.timeBetween(shifted, later, Value.of("NANOS"))).isEqualTo(Value.of(1));
    }
}
