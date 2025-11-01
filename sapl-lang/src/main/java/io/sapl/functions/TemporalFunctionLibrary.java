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

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Int;
import io.sapl.api.validation.Number;
import io.sapl.api.validation.Text;
import lombok.experimental.UtilityClass;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.Locale;

/**
 * Functions for temporal operations in authorization policies.
 * Handles date-time parsing, comparison, arithmetic, and formatting using
 * ISO 8601 and RFC3339 standards.
 */
@UtilityClass
@FunctionLibrary(name = TemporalFunctionLibrary.NAME, description = TemporalFunctionLibrary.DESCRIPTION, libraryDocumentation = TemporalFunctionLibrary.DOCUMENTATION)
public class TemporalFunctionLibrary {

    public static final String NAME          = "time";
    public static final String DESCRIPTION   = "Functions for temporal operations in authorization policies.";
    public static final String DOCUMENTATION = """
            Temporal functions for working with dates, times, and durations in authorization policies.
            Based on ISO 8601 and DIN 1355 standards.

            ## Date and Time Formats

            ISO 8601 uses YYYY-MM-DD for dates (e.g., "2017-10-28") and HH:mm:ss for times (e.g., "16:14:11").
            Combined format: "2017-10-28T16:14:11".

            DIN 1355 uses DD.MM.YYYY for dates (e.g., "28.10.2017").

            RFC3339 is a strict profile of ISO 8601 requiring timezone information:
            YYYY-MM-DDTHH:MM:SS[.fraction](Z|±HH:MM)

            All functions accept ISO 8601 and RFC3339 timestamps. RFC3339 leap seconds (e.g., "23:59:60Z")
            are normalized to "23:59:59Z" by Java's temporal system.

            ## Timezone Handling

            UTC (Coordinated Universal Time) is timezone zero. Central European Time has a +01:00 offset.
            Functions work with UTC timestamps and support timezone conversions.

            **Examples:**
            ```sapl
            policy "working_hours"
            permit
            where
              var currentTime = time.timeOf(environment.currentDateTime);
              time.after(currentTime, "09:00:00");
              time.before(currentTime, "17:00:00");
            ```

            ```sapl
            policy "age_restriction"
            permit
            where
              var age = time.ageInYears(subject.birthDate, environment.currentDate);
              age >= 18;
            ```
            """;

    private static final DateTimeFormatter DIN_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final DateTimeFormatter US_TIME_FORMATTER       = new DateTimeFormatterBuilder()
            .parseCaseInsensitive().appendPattern("hh:mm:ss a").toFormatter(Locale.US);

    /* ######## DURATION ######## */

    @Function(docs = """
            ```durationOfSeconds(NUMBER seconds)```: Converts seconds to milliseconds for duration values.

            Durations in the temporal library are expressed in milliseconds. Multiplies seconds by 1000.

            **Example:**

            The expression ```time.durationOfSeconds(20.5)``` returns ```20500```.
            """)
    public static Val durationOfSeconds(@Number Val seconds) {
        return Val.of(seconds.getLong() * 1000);
    }

    @Function(docs = """
            ```durationOfMinutes(NUMBER minutes)```: Converts minutes to milliseconds for duration values.

            Multiplies minutes by 60000.

            **Example:**

            The expression ```time.durationOfMinutes(2.5)``` returns ```150000```.""")
    public static Val durationOfMinutes(@Number Val minutes) {
        return Val.of(minutes.getLong() * 60 * 1000);
    }

    @Function(docs = """
            ```durationOfHours(NUMBER hours)```: Converts hours to milliseconds for duration values.

            Multiplies hours by 3600000.

            **Example:**

            The expression ```time.durationOfHours(4.5)``` returns ```16200000```.""")
    public static Val durationOfHours(@Number Val hours) {
        return Val.of(hours.getLong() * 60 * 60 * 1000);
    }

    @Function(docs = """
            ```durationOfDays(NUMBER days)```: Converts days to milliseconds for duration values.

            Multiplies days by 86400000.

            **Example:**

            The expression ```time.durationOfDays(365)``` returns ```31536000000```.""")
    public static Val durationOfDays(@Number Val days) {
        return Val.of(days.getLong() * 24 * 60 * 60 * 1000);
    }

    /* ######## INSTANT/UTC COMPARISON ######## */

    @Function(docs = """
            ```before(TEXT timeA, TEXT timeB)```: Compares two instants and returns true if timeA is before timeB.

            Both parameters must be ISO 8601 strings at UTC.

            **Example:**

            The expression ```time.before("2021-11-08T13:00:00Z", "2021-11-08T13:00:01Z")``` returns ```true```.""")
    public static Val before(@Text Val timeA, @Text Val timeB) {
        return Val.of(instantOf(timeA).isBefore(instantOf(timeB)));
    }

    @Function(docs = """
            ```after(TEXT timeA, TEXT timeB)```: Compares two instants and returns true if timeA is after timeB.

            Both parameters must be ISO 8601 strings at UTC.

            **Example:**

            The expression ```time.after("2021-11-08T13:00:01Z", "2021-11-08T13:00:00Z")``` returns ```true```.""")
    public static Val after(@Text Val timeA, @Text Val timeB) {
        return Val.of(instantOf(timeA).isAfter(instantOf(timeB)));
    }

    @Function(docs = """
            ```between(TEXT time, TEXT intervalStart, TEXT intervalEnd)```: Returns true if time falls within
            the closed interval from intervalStart to intervalEnd.

            All parameters must be ISO 8601 strings at UTC. intervalStart must be before intervalEnd.

            **Example:**

            The expression ```time.between("2021-11-08T13:00:00Z", "2021-11-07T13:00:00Z", "2021-11-09T13:00:00Z")```
            returns ```true```.""")
    public static Val between(@Text Val time, @Text Val intervalStart, @Text Val intervalEnd) {
        final var t     = instantOf(time);
        final var start = instantOf(intervalStart);
        final var end   = instantOf(intervalEnd);

        if (t.equals(start))
            return Val.TRUE;
        else if (t.equals(end))
            return Val.TRUE;
        else
            return Val.of((t.isBefore(end) && t.isAfter(start)));
    }

    @Function(docs = """
            ```timeBetween(TEXT timeA, TEXT timeB, TEXT chronoUnit)```: Calculates the time span between
            timeA and timeB in the specified chronoUnit.

            Both time parameters must be ISO 8601 strings at UTC. Valid chronoUnits: NANOS, MICROS, MILLIS,
            SECONDS, MINUTES, HOURS, HALF_DAYS, DAYS, WEEKS, MONTHS, YEARS, DECADES, CENTURIES, MILLENNIA.

            Month duration is estimated as one twelfth of 365.2425 days. Year duration is 365.2425 days.

            **Example:**

            The expression ```time.timeBetween("2001-01-01", "2002-01-01", "YEARS")``` returns ```1```.""")
    public static Val timeBetween(@Text Val timeA, @Text Val timeB, @Text Val chronoUnit) {
        final var unit        = parseChronoUnit(chronoUnit);
        final var instantFrom = instantOf(timeA);
        final var instantTo   = instantOf(timeB);
        try {
            return Val.of(unit.between(instantFrom, instantTo));
        } catch (UnsupportedTemporalTypeException e) {
            final var dateFrom = LocalDate.ofInstant(instantFrom, ZoneId.systemDefault());
            final var dateTo   = LocalDate.ofInstant(instantTo, ZoneId.systemDefault());
            return Val.of(unit.between(dateFrom, dateTo));
        }
    }

    /* ######## DATE ARITHMETIC ######## */

    @Function(docs = """
            ```plusDays(TEXT startTime, INTEGER days)```: Adds the specified number of days to startTime.

            startTime must be an ISO 8601 string at UTC. days must be an integer.

            **Example:**

            The expression ```time.plusDays("2021-11-08T13:00:00Z", 5)```
            returns ```"2021-11-13T13:00:00Z"```.""")
    public static Val plusDays(@Text Val startTime, @Int Val days) {
        final var instant = instantOf(startTime);
        validateTemporalBounds(instant, days.getLong(), ChronoUnit.DAYS, true);
        return Val.of(instant.plus(days.getLong(), ChronoUnit.DAYS).toString());
    }

    @Function(docs = """
            ```plusMonths(TEXT startTime, INTEGER months)```: Adds the specified number of months to startTime.

            startTime must be an ISO 8601 string at UTC. months must be an integer.
            Uses standard calendar rules (e.g., adding 1 month to Jan 31 results in Feb 28/29).

            **Example:**

            The expression ```time.plusMonths("2021-11-08T13:00:00Z", 2)```
            returns ```"2022-01-08T13:00:00Z"```.""")
    public static Val plusMonths(@Text Val startTime, @Int Val months) {
        final var instant = instantOf(startTime);
        final var zdt     = instant.atZone(ZoneOffset.UTC);
        return Val.of(zdt.plusMonths(months.getLong()).toInstant().toString());
    }

    @Function(docs = """
            ```plusYears(TEXT startTime, INTEGER years)```: Adds the specified number of years to startTime.

            startTime must be an ISO 8601 string at UTC. years must be an integer.
            Uses standard calendar rules (e.g., adding 1 year to Feb 29 in a leap year results in Feb 28).

            **Example:**

            The expression ```time.plusYears("2021-11-08T13:00:00Z", 3)```
            returns ```"2024-11-08T13:00:00Z"```.""")
    public static Val plusYears(@Text Val startTime, @Int Val years) {
        final var instant = instantOf(startTime);
        final var zdt     = instant.atZone(ZoneOffset.UTC);
        return Val.of(zdt.plusYears(years.getLong()).toInstant().toString());
    }

    @Function(docs = """
            ```minusDays(TEXT startTime, INTEGER days)```: Subtracts the specified number of days from startTime.

            startTime must be an ISO 8601 string at UTC. days must be an integer.

            **Example:**

            The expression ```time.minusDays("2021-11-08T13:00:00Z", 5)```
            returns ```"2021-11-03T13:00:00Z"```.""")
    public static Val minusDays(@Text Val startTime, @Int Val days) {
        final var instant = instantOf(startTime);
        validateTemporalBounds(instant, days.getLong(), ChronoUnit.DAYS, false);
        return Val.of(instant.minus(days.getLong(), ChronoUnit.DAYS).toString());
    }

    @Function(docs = """
            ```minusMonths(TEXT startTime, INTEGER months)```: Subtracts the specified number of months from startTime.

            startTime must be an ISO 8601 string at UTC. months must be an integer.
            Uses standard calendar rules.

            **Example:**

            The expression ```time.minusMonths("2021-11-08T13:00:00Z", 2)```
            returns ```"2021-09-08T13:00:00Z"```.""")
    public static Val minusMonths(@Text Val startTime, @Int Val months) {
        final var instant = instantOf(startTime);
        final var zdt     = instant.atZone(ZoneOffset.UTC);
        return Val.of(zdt.minusMonths(months.getLong()).toInstant().toString());
    }

    @Function(docs = """
            ```minusYears(TEXT startTime, INTEGER years)```: Subtracts the specified number of years from startTime.

            startTime must be an ISO 8601 string at UTC. years must be an integer.
            Uses standard calendar rules.

            **Example:**

            The expression ```time.minusYears("2021-11-08T13:00:00Z", 3)```
            returns ```"2018-11-08T13:00:00Z"```.""")
    public static Val minusYears(@Text Val startTime, @Int Val years) {
        final var instant = instantOf(startTime);
        final var zdt     = instant.atZone(ZoneOffset.UTC);
        return Val.of(zdt.minusYears(years.getLong()).toInstant().toString());
    }

    /* ######## INSTANT/UTC MANIPULATION ######## */

    @Function(docs = """
            ```plusNanos(TEXT startTime, INTEGER nanos)```: Adds the specified number of nanoseconds to startTime.

            startTime must be an ISO 8601 string at UTC. nanos must be an integer.

            **Example:**

            The expression ```time.plusNanos("2021-11-08T13:00:00Z", 10000000000)```
            returns ```"2021-11-08T13:00:10Z"```.""")
    public static Val plusNanos(@Text Val startTime, @Int Val nanos) {
        final var instant = instantOf(startTime);
        validateTemporalBounds(instant, nanos.getLong(), ChronoUnit.NANOS, true);
        return Val.of(instant.plusNanos(nanos.getLong()).toString());
    }

    @Function(docs = """
            ```plusMillis(TEXT startTime, INTEGER millis)```: Adds the specified number of milliseconds to startTime.

            startTime must be an ISO 8601 string at UTC. millis must be an integer.

            **Example:**

            The expression ```time.plusMillis("2021-11-08T13:00:00Z", 10000)```
            returns ```"2021-11-08T13:00:10Z"```.""")
    public static Val plusMillis(@Text Val startTime, @Int Val millis) {
        final var instant = instantOf(startTime);
        validateTemporalBounds(instant, millis.getLong(), ChronoUnit.MILLIS, true);
        return Val.of(instant.plusMillis(millis.getLong()).toString());
    }

    @Function(docs = """
            ```plusSeconds(TEXT startTime, INTEGER seconds)```: Adds the specified number of seconds to startTime.

            startTime must be an ISO 8601 string at UTC. seconds must be an integer.

            **Example:**

            The expression ```time.plusSeconds("2021-11-08T13:00:00Z", 10)```
            returns ```"2021-11-08T13:00:10Z"```.""")
    public static Val plusSeconds(@Text Val startTime, @Int Val seconds) {
        final var instant = instantOf(startTime);
        validateTemporalBounds(instant, seconds.getLong(), ChronoUnit.SECONDS, true);
        return Val.of(instant.plusSeconds(seconds.getLong()).toString());
    }

    @Function(docs = """
            ```minusNanos(TEXT startTime, INTEGER nanos)```: Subtracts the specified number of nanoseconds from startTime.

            startTime must be an ISO 8601 string at UTC. nanos must be an integer.

            **Example:**

            The expression ```time.minusNanos("2021-11-08T13:00:00Z", 10000000000)```
            returns ```"2021-11-08T12:59:50Z"```.""")
    public static Val minusNanos(@Text Val startTime, @Int Val nanos) {
        final var instant = instantOf(startTime);
        validateTemporalBounds(instant, nanos.getLong(), ChronoUnit.NANOS, false);
        return Val.of(instant.minusNanos(nanos.getLong()).toString());
    }

    @Function(docs = """
            ```minusMillis(TEXT startTime, INTEGER millis)```: Subtracts the specified number of milliseconds from startTime.

            startTime must be an ISO 8601 string at UTC. millis must be an integer.

            **Example:**

            The expression ```time.minusMillis("2021-11-08T13:00:00Z", 10000)```
            returns ```"2021-11-08T12:59:50Z"```.""")
    public static Val minusMillis(@Text Val startTime, @Int Val millis) {
        final var instant = instantOf(startTime);
        validateTemporalBounds(instant, millis.getLong(), ChronoUnit.MILLIS, false);
        return Val.of(instant.minusMillis(millis.getLong()).toString());
    }

    @Function(docs = """
            ```minusSeconds(TEXT startTime, INTEGER seconds)```: Subtracts the specified number of seconds from startTime.

            startTime must be an ISO 8601 string at UTC. seconds must be an integer.

            **Example:**

            The expression ```time.minusSeconds("2021-11-08T13:00:00Z", 10)```
            returns ```"2021-11-08T12:59:50Z"```.""")
    public static Val minusSeconds(@Text Val startTime, @Int Val seconds) {
        final var instant = instantOf(startTime);
        validateTemporalBounds(instant, seconds.getLong(), ChronoUnit.SECONDS, false);
        return Val.of(instant.minusSeconds(seconds.getLong()).toString());
    }

    /* ######## INSTANT/UTC EPOCH ######## */

    @Function(docs = """
            ```epochSecond(TEXT utcDateTime)```: Converts an ISO 8601 UTC timestamp to seconds since
            the epoch (1970-01-01T00:00:00Z).

            **Example:**

            The expression ```time.epochSecond("2021-11-08T13:00:00Z")``` returns ```1636376400```.""")
    public static Val epochSecond(@Text Val utcDateTime) {
        return Val.of(instantOf(utcDateTime).getEpochSecond());
    }

    @Function(docs = """
            ```epochMilli(TEXT utcDateTime)```: Converts an ISO 8601 UTC timestamp to milliseconds since
            the epoch (1970-01-01T00:00:00Z).

            **Example:**

            The expression ```time.epochMilli("2021-11-08T13:00:00Z")``` returns ```1636376400000```.""")
    public static Val epochMilli(@Text Val utcDateTime) {
        return Val.of(instantOf(utcDateTime).toEpochMilli());
    }

    @Function(docs = """
            ```ofEpochSecond(INTEGER epochSeconds)```: Converts seconds since the epoch to an ISO 8601 UTC timestamp.

            **Example:**

            The expression ```time.ofEpochSecond(1636376400)``` returns ```"2021-11-08T13:00:00Z"```.""")
    public static Val ofEpochSecond(@Int Val epochSeconds) {
        return Val.of(Instant.ofEpochSecond(epochSeconds.getLong()).toString());
    }

    @Function(docs = """
            ```ofEpochMilli(INTEGER epochMillis)```: Converts milliseconds since the epoch to an ISO 8601 UTC timestamp.

            **Example:**

            The expression ```time.ofEpochMilli(1636376400000)``` returns ```"2021-11-08T13:00:00Z"```.""")
    public static Val ofEpochMilli(@Int Val epochMillis) {
        return Val.of(Instant.ofEpochMilli(epochMillis.getLong()).toString());
    }

    /* ######## INSTANT/UTC CALENDAR ######## */

    @Function(docs = """
            ```weekOfYear(TEXT utcDateTime)```: Returns the calendar week number (1-52) for the given date.

            utcDateTime must be an ISO 8601 string at UTC.

            **Example:**

            The expression ```time.weekOfYear("2021-11-08T13:00:00Z")``` returns ```45```.""")
    public static Val weekOfYear(@Text Val isoDateTime) {
        return Val
                .of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText()).get(ChronoField.ALIGNED_WEEK_OF_YEAR));
    }

    @Function(docs = """
            ```dayOfYear(TEXT utcDateTime)```: Returns the day of the year (1-365) for the given date.

            utcDateTime must be an ISO 8601 string at UTC.

            **Example:**

            The expression ```time.dayOfYear("2021-11-08T13:00:00Z")``` returns ```312```.""")
    public static Val dayOfYear(@Text Val isoDateTime) {
        return Val.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText()).get(ChronoField.DAY_OF_YEAR));
    }

    @Function(docs = """
            ```dayOfWeek(TEXT utcDateTime)```: Returns the name of the weekday for the given date.

            Returns one of: SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY.
            utcDateTime must be an ISO 8601 string at UTC.

            **Example:**

            The expression ```time.dayOfWeek("2021-11-08T13:00:00Z")``` returns ```"MONDAY"```.""")
    public static Val dayOfWeek(@Text Val isoDateTime) {
        return Val.of(DayOfWeek.from(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), LocalDateTime::from))
                .toString());
    }

    /* ######## VALIDATION ######## */

    @Function(docs = """
            ```validUTC(TEXT utcDateTime)```: Returns true if the value is a valid ISO 8601 UTC timestamp.

            **Example:**

            The expression ```time.validUTC("2021-11-08T13:00:00Z")``` returns ```true```.
            The expression ```time.validUTC("20111-000:00Z")``` returns ```false```.""")
    public static Val validUTC(@Text Val utcDateTime) {
        try {
            instantOf(utcDateTime);
            return Val.TRUE;
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return Val.FALSE;
        }
    }

    @Function(docs = """
            ```validRFC3339(TEXT timestamp)```: Returns true if the value is a valid RFC3339 timestamp.

            RFC3339 requires a timezone designator (Z or offset like +05:00).

            **Example:**

            The expression ```time.validRFC3339("2021-11-08T13:00:00Z")``` returns ```true```.
            The expression ```time.validRFC3339("2021-11-08T13:00:00")``` returns ```false``` (missing timezone).
            The expression ```time.validRFC3339("2021-11-08")``` returns ```false``` (date only, no time).""")
    public static Val validRFC3339(@Text Val timestamp) {
        try {
            if (timestamp == null || timestamp.isTextual() && timestamp.getText() == null) {
                return Val.FALSE;
            }

            final var text = timestamp.getText();
            if (text.isBlank()) {
                return Val.FALSE;
            }

            Instant.parse(text);
            if (!text.contains("T") || (!text.endsWith("Z") && !text.matches(".*[+-]\\d{2}:\\d{2}$"))) {
                return Val.FALSE;
            }

            return Val.TRUE;
        } catch (DateTimeParseException e) {
            return Val.FALSE;
        }
    }

    /* ######## TEMPORAL BOUNDS ######## */

    @Function(docs = """
            ```startOfDay(TEXT dateTime)```: Returns the start of the day (00:00:00.000) for the given date-time at UTC.

            **Example:**

            The expression ```time.startOfDay("2021-11-08T13:45:30Z")``` returns ```"2021-11-08T00:00:00Z"```.""")
    public static Val startOfDay(@Text Val dateTime) {
        final var instant = instantOf(dateTime);
        final var date    = LocalDate.ofInstant(instant, ZoneOffset.UTC);
        return Val.of(date.atStartOfDay(ZoneOffset.UTC).toInstant().toString());
    }

    @Function(docs = """
            ```endOfDay(TEXT dateTime)```: Returns the end of the day (23:59:59.999999999) for the given date-time at UTC.

            **Example:**

            The expression ```time.endOfDay("2021-11-08T13:45:30Z")``` returns ```"2021-11-08T23:59:59.999999999Z"```.""")
    public static Val endOfDay(@Text Val dateTime) {
        final var instant = instantOf(dateTime);
        final var date    = LocalDate.ofInstant(instant, ZoneOffset.UTC);
        return Val.of(date.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant().toString());
    }

    @Function(docs = """
            ```startOfWeek(TEXT dateTime)```: Returns the start of the week (Monday 00:00:00.000) for the given date-time at UTC.

            Weeks start on Monday per ISO 8601.

            **Example:**

            The expression ```time.startOfWeek("2021-11-08T13:45:30Z")``` returns ```"2021-11-08T00:00:00Z"```
            (November 8, 2021 was a Monday).""")
    public static Val startOfWeek(@Text Val dateTime) {
        final var instant = instantOf(dateTime);
        final var date    = LocalDate.ofInstant(instant, ZoneOffset.UTC);
        final var monday  = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return Val.of(monday.atStartOfDay(ZoneOffset.UTC).toInstant().toString());
    }

    @Function(docs = """
            ```endOfWeek(TEXT dateTime)```: Returns the end of the week (Sunday 23:59:59.999999999) for the given date-time at UTC.

            Weeks end on Sunday per ISO 8601.

            **Example:**

            The expression ```time.endOfWeek("2021-11-08T13:45:30Z")``` returns ```"2021-11-14T23:59:59.999999999Z"```.""")
    public static Val endOfWeek(@Text Val dateTime) {
        final var instant = instantOf(dateTime);
        final var date    = LocalDate.ofInstant(instant, ZoneOffset.UTC);
        final var sunday  = date.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return Val.of(sunday.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant().toString());
    }

    @Function(docs = """
            ```startOfMonth(TEXT dateTime)```: Returns the start of the month (first day at 00:00:00.000) for the given date-time at UTC.

            **Example:**

            The expression ```time.startOfMonth("2021-11-08T13:45:30Z")``` returns ```"2021-11-01T00:00:00Z"```.""")
    public static Val startOfMonth(@Text Val dateTime) {
        final var instant = instantOf(dateTime);
        final var date    = LocalDate.ofInstant(instant, ZoneOffset.UTC);
        return Val.of(date.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant().toString());
    }

    @Function(docs = """
            ```endOfMonth(TEXT dateTime)```: Returns the end of the month (last day at 23:59:59.999999999) for the given date-time at UTC.

            **Example:**

            The expression ```time.endOfMonth("2021-11-08T13:45:30Z")``` returns ```"2021-11-30T23:59:59.999999999Z"```.""")
    public static Val endOfMonth(@Text Val dateTime) {
        final var instant = instantOf(dateTime);
        final var date    = LocalDate.ofInstant(instant, ZoneOffset.UTC);
        final var lastDay = date.withDayOfMonth(date.lengthOfMonth());
        return Val.of(lastDay.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant().toString());
    }

    @Function(docs = """
            ```startOfYear(TEXT dateTime)```: Returns the start of the year (January 1 at 00:00:00.000) for the given date-time at UTC.

            **Example:**

            The expression ```time.startOfYear("2021-11-08T13:45:30Z")``` returns ```"2021-01-01T00:00:00Z"```.""")
    public static Val startOfYear(@Text Val dateTime) {
        final var instant = instantOf(dateTime);
        final var date    = LocalDate.ofInstant(instant, ZoneOffset.UTC);
        return Val.of(date.withDayOfYear(1).atStartOfDay(ZoneOffset.UTC).toInstant().toString());
    }

    @Function(docs = """
            ```endOfYear(TEXT dateTime)```: Returns the end of the year (December 31 at 23:59:59.999999999) for the given date-time at UTC.

            **Example:**

            The expression ```time.endOfYear("2021-11-08T13:45:30Z")``` returns ```"2021-12-31T23:59:59.999999999Z"```.""")
    public static Val endOfYear(@Text Val dateTime) {
        final var instant = instantOf(dateTime);
        final var date    = LocalDate.ofInstant(instant, ZoneOffset.UTC);
        final var lastDay = date.withDayOfYear(date.lengthOfYear());
        return Val.of(lastDay.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant().toString());
    }

    /* ######## TEMPORAL ROUNDING ######## */

    @Function(docs = """
            ```truncateToHour(TEXT dateTime)```: Truncates the date-time to the hour, setting minutes, seconds, and nanoseconds to zero.

            **Example:**

            The expression ```time.truncateToHour("2021-11-08T13:45:30.123Z")``` returns ```"2021-11-08T13:00:00Z"```.""")
    public static Val truncateToHour(@Text Val dateTime) {
        final var instant = instantOf(dateTime);
        return Val.of(instant.truncatedTo(ChronoUnit.HOURS).toString());
    }

    @Function(docs = """
            ```truncateToDay(TEXT dateTime)```: Truncates the date-time to the day, setting time to 00:00:00.000.

            **Example:**

            The expression ```time.truncateToDay("2021-11-08T13:45:30Z")``` returns ```"2021-11-08T00:00:00Z"```.""")
    public static Val truncateToDay(@Text Val dateTime) {
        return startOfDay(dateTime);
    }

    @Function(docs = """
            ```truncateToWeek(TEXT dateTime)```: Truncates the date-time to the start of the week (Monday 00:00:00.000).

            **Example:**

            The expression ```time.truncateToWeek("2021-11-08T13:45:30Z")``` returns ```"2021-11-08T00:00:00Z"```.""")
    public static Val truncateToWeek(@Text Val dateTime) {
        return startOfWeek(dateTime);
    }

    @Function(docs = """
            ```truncateToMonth(TEXT dateTime)```: Truncates the date-time to the start of the month (first day at 00:00:00.000).

            **Example:**

            The expression ```time.truncateToMonth("2021-11-08T13:45:30Z")``` returns ```"2021-11-01T00:00:00Z"```.""")
    public static Val truncateToMonth(@Text Val dateTime) {
        return startOfMonth(dateTime);
    }

    @Function(docs = """
            ```truncateToYear(TEXT dateTime)```: Truncates the date-time to the start of the year (January 1 at 00:00:00.000).

            **Example:**

            The expression ```time.truncateToYear("2021-11-08T13:45:30Z")``` returns ```"2021-01-01T00:00:00Z"```.""")
    public static Val truncateToYear(@Text Val dateTime) {
        return startOfYear(dateTime);
    }

    /* ######## VALIDATION ######## */

    @Function(docs = """
            ```localIso(TEXT localDateTime)```: Parses an ISO 8601 date-time string without timezone offset using
            the PDP's system default timezone.

            **Example:**

            With system default timezone Europe/Berlin, the expression
            ```time.localIso("2021-11-08T13:00:00")``` returns ```"2021-11-08T12:00:00Z"```.""")
    public static Val localIso(@Text Val localDateTime) {
        return Val.of(localDateTimeToInstant(
                parseLocalDateTime(localDateTime.getText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                ZoneId.systemDefault()).toString());
    }

    @Function(docs = """
            ```localDin(TEXT dinDateTime)```: Parses a DIN date-time string without timezone offset using
            the PDP's system default timezone. Returns an ISO 8601 string.

            **Example:**

            With system default timezone Europe/Berlin, the expression
            ```time.localDin("08.11.2021 13:00:00")``` returns ```"2021-11-08T12:00:00Z"```.""")
    public static Val localDin(@Text Val dinDateTime) {
        return Val.of(localDateTimeToInstant(parseLocalDateTime(dinDateTime.getText(), DIN_DATE_TIME_FORMATTER),
                ZoneId.systemDefault()).toString());
    }

    @Function(docs = """
            ```dateTimeAtOffset(TEXT localDateTime, TEXT offsetId)```: Parses a local date-time string and
            combines it with an offset, then converts to an ISO 8601 instant at UTC.

            **Example:**

            The expression ```time.dateTimeAtOffset("2021-11-08T13:12:35", "+05:00")```
            returns ```"2021-11-08T08:12:35Z"```.""")
    public static Val dateTimeAtOffset(@Text Val localDateTime, @Text Val offsetId) {
        final var ldt = DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(localDateTime.getText(), LocalDateTime::from);
        final var odt = OffsetDateTime.of(ldt, parseZoneOffset(offsetId));
        return Val.of(odt.withOffsetSameInstant(ZoneOffset.UTC).toInstant().toString());
    }

    @Function(docs = """
            ```dateTimeAtZone(TEXT localDateTime, TEXT zoneId)```: Parses an ISO 8601 date-time string and
            returns the matching ISO 8601 instant at UTC for the provided timezone.

            If zoneId is empty or blank, uses system default timezone.
            See [timezone database](https://en.wikipedia.org/wiki/List_of_tz_database_time_zones) for valid zoneId values.

            **Example:**

            The expression ```time.dateTimeAtZone("2021-11-08T13:12:35", "Europe/Berlin")```
            returns ```"2021-11-08T12:12:35Z"```.""")
    public static Val dateTimeAtZone(@Text Val localDateTime, @Text Val zoneId) {
        final var ldt = DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(localDateTime.getText(), LocalDateTime::from);
        final var zdt = ZonedDateTime.of(ldt, zoneIdOf(zoneId));
        return Val.of(zdt.withZoneSameInstant(ZoneId.of("UTC")).toInstant().toString());
    }

    @Function(docs = """
            ```offsetDateTime(TEXT isoDateTime)```: Parses an ISO 8601 date-time with offset and
            returns the matching ISO 8601 instant at UTC.

            **Example:**

            The expression ```time.offsetDateTime("2021-11-08T13:12:35+05:00")```
            returns ```"2021-11-08T08:12:35Z"```.""")
    public static Val offsetDateTime(@Text Val isoDateTime) {
        final var offsetDateTime = DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), OffsetDateTime::from);
        return Val.of(offsetDateTime.withOffsetSameInstant(ZoneOffset.UTC).toInstant().toString());
    }

    /* ######## TIME CONVERSION ######## */

    @Function(docs = """
            ```offsetTime(TEXT isoTime)```: Parses an ISO 8601 time with offset and
            returns the matching time at UTC.

            **Example:**

            The expression ```time.offsetTime("13:12:35-05:00")``` returns ```"18:12:35"```.""")
    public static Val offsetTime(@Text Val isoTime) {
        final var offsetTime = DateTimeFormatter.ISO_TIME.parse(isoTime.getText(), OffsetTime::from);
        return Val.of(offsetTime.withOffsetSameInstant(ZoneOffset.UTC).toLocalTime().toString());
    }

    @Function(docs = """
            ```timeAtOffset(TEXT localTime, TEXT offsetId)```: Parses a time with a separate offset parameter and
            returns the matching time at UTC.

            **Example:**

            The expression ```time.timeAtOffset("13:12:35", "-05:00")``` returns ```"18:12:35"```.""")
    public static Val timeAtOffset(@Text Val localTime, @Text Val offsetId) {
        final var lt     = DateTimeFormatter.ISO_LOCAL_TIME.parse(localTime.getText(), LocalTime::from);
        final var offset = parseZoneOffset(offsetId);
        return Val.of(OffsetTime.of(lt, offset).withOffsetSameInstant(ZoneOffset.UTC).toLocalTime().toString());
    }

    @Function(docs = """
            ```timeInZone(TEXT localTime, TEXT localDate, TEXT zoneId)```: Parses a time and date with a separate
            timezone parameter and returns the matching time at UTC.

            **Example:**

            The expression ```time.timeInZone("13:12:35", "2022-01-14", "US/Pacific")``` returns ```"21:12:35"```.""")
    public static Val timeInZone(@Text Val localTime, @Text Val localDate, @Text Val zoneId) {
        final var zone          = zoneIdOf(zoneId);
        final var lt            = DateTimeFormatter.ISO_LOCAL_TIME.parse(localTime.getText(), LocalTime::from);
        final var zonedDateTime = ZonedDateTime.of(lt.atDate(LocalDate.parse(localDate.getText())), zone);
        return Val.of(zonedDateTime.withZoneSameInstant(ZoneId.of("UTC")).toLocalTime().toString());
    }

    @Function(docs = """
            ```timeAMPM(TEXT timeInAMPM)```: Parses a time string in AM/PM format and converts it to 24-hour format.

            **Example:**

            The expression ```time.timeAMPM("08:12:35 PM")``` returns ```"20:12:35"```.""")
    public static Val timeAMPM(@Text Val timeInAMPM) {
        final var lt = US_TIME_FORMATTER.parse(timeInAMPM.getText(), LocalTime::from);
        return Val.of(lt.toString());
    }

    /* ######## EXTRACT PARTS ######## */

    @Function(docs = """
            ```dateOf(TEXT isoDateTime)```: Returns the date part of an ISO 8601 string.

            **Example:**

            The expression ```time.dateOf("2021-11-08T13:00:00Z")``` returns ```"2021-11-08"```.""")
    public static Val dateOf(@Text Val isoDateTime) {
        return Val.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), LocalDate::from).toString());
    }

    @Function(docs = """
            ```timeOf(TEXT isoDateTime)```: Returns the local time of an ISO 8601 string, truncated to seconds.

            **Example:**

            The expression ```time.timeOf("2021-11-08T13:00:00Z")``` returns ```"13:00:00"```.""")
    public static Val timeOf(@Text Val isoDateTime) {
        final var time = DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), LocalTime::from)
                .truncatedTo(ChronoUnit.SECONDS);
        return Val.of(time.format(DateTimeFormatter.ISO_LOCAL_TIME));
    }

    @Function(docs = """
            ```hourOf(TEXT isoDateTime)```: Returns the hour of an ISO 8601 string.

            **Example:**

            The expression ```time.hourOf("2021-11-08T13:17:23Z")``` returns ```13```.""")
    public static Val hourOf(@Text Val isoDateTime) {
        return Val.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), LocalTime::from).getHour());
    }

    @Function(docs = """
            ```minuteOf(TEXT isoDateTime)```: Returns the minute of an ISO 8601 string.

            **Example:**

            The expression ```time.minuteOf("2021-11-08T13:17:23Z")``` returns ```17```.""")
    public static Val minuteOf(@Text Val isoDateTime) {
        return Val.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), LocalTime::from).getMinute());
    }

    @Function(docs = """
            ```secondOf(TEXT isoDateTime)```: Returns the second of an ISO 8601 string.

            **Example:**

            The expression ```time.secondOf("2021-11-08T13:00:23Z")``` returns ```23```.""")
    public static Val secondOf(@Text Val isoDateTime) {
        return Val.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), LocalTime::from).getSecond());
    }

    /* ######## ISO DURATION ######## */

    @Function(docs = """
            ```durationFromISO(TEXT isoDuration)```: Parses an ISO 8601 duration string and returns the duration
            in milliseconds.

            Format: P[n]Y[n]M[n]DT[n]H[n]M[n]S. Years (Y) and Months (M) are approximated:
            1 year = 365.2425 days, 1 month = 30.436875 days.

            **Examples:**

            The expression ```time.durationFromISO("P1D")``` returns ```86400000``` (1 day in milliseconds).
            The expression ```time.durationFromISO("PT2H30M")``` returns ```9000000``` (2.5 hours in milliseconds).
            The expression ```time.durationFromISO("P1Y2M3DT4H5M6S")``` returns duration in milliseconds.""")
    public static Val durationFromISO(@Text Val isoDuration) {
        if (isoDuration == null || isoDuration.isTextual() && isoDuration.getText() == null) {
            throw new IllegalArgumentException("ISO duration parameter cannot be null");
        }

        final var durationStr = isoDuration.getText();
        if (durationStr.isBlank()) {
            throw new IllegalArgumentException("ISO duration parameter cannot be blank");
        }

        try {
            var totalMillis = 0L;

            final var hasPeriodComponents = durationStr.contains("Y")
                    || (durationStr.contains("M") && !durationStr.contains("T"));

            if (hasPeriodComponents) {
                final var period = Period.parse(durationStr);
                totalMillis += (long) (period.getYears() * 365.2425 * 24 * 60 * 60 * 1000);
                totalMillis += (long) (period.getMonths() * 30.436875 * 24 * 60 * 60 * 1000);
                totalMillis += period.getDays() * 24L * 60 * 60 * 1000;

                if (durationStr.contains("T")) {
                    final var timePartStart = durationStr.indexOf('T');
                    final var timePart      = "PT" + durationStr.substring(timePartStart + 1);
                    final var duration      = Duration.parse(timePart);
                    totalMillis += duration.toMillis();
                }
            } else {
                final var duration = Duration.parse(durationStr);
                totalMillis = duration.toMillis();
            }

            return Val.of(totalMillis);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Invalid ISO 8601 duration format: '" + durationStr + "'", e);
        }
    }

    @Function(docs = """
            ```durationToISOCompact(NUMBER milliseconds)```: Converts a duration in milliseconds to a compact
            ISO 8601 duration string.

            Uses only time-based units (days, hours, minutes, seconds) for precision.

            **Examples:**

            The expression ```time.durationToISOCompact(86400000)``` returns ```"P1D"```.
            The expression ```time.durationToISOCompact(9000000)``` returns ```"PT2H30M"```.
            The expression ```time.durationToISOCompact(90061000)``` returns ```"P1DT1H1M1S"```.""")
    public static Val durationToISOCompact(@Number Val milliseconds) {
        final var duration = Duration.ofMillis(milliseconds.getLong());
        return Val.of(duration.toString());
    }

    @Function(docs = """
            ```durationToISOVerbose(NUMBER milliseconds)```: Converts a duration in milliseconds to a verbose
            ISO 8601 duration string with approximate years and months.

            Uses approximation: 1 year = 365.2425 days, 1 month = 30.436875 days.

            **Examples:**

            The expression ```time.durationToISOVerbose(31536000000)``` returns approximately ```"P1Y"```.
            The expression ```time.durationToISOVerbose(86400000)``` returns ```"P1D"```.""")
    public static Val durationToISOVerbose(@Number Val milliseconds) {
        var remainingMillis = milliseconds.getLong();

        final var millisPerYear  = (long) (365.2425 * 24 * 60 * 60 * 1000);
        final var millisPerMonth = (long) (30.436875 * 24 * 60 * 60 * 1000);

        final var years = remainingMillis / millisPerYear;
        remainingMillis %= millisPerYear;

        final var months = remainingMillis / millisPerMonth;
        remainingMillis %= millisPerMonth;

        final var duration = Duration.ofMillis(remainingMillis);
        final var days     = duration.toDays();
        final var hours    = duration.toHoursPart();
        final var minutes  = duration.toMinutesPart();
        final var seconds  = duration.toSecondsPart();

        final var result = new StringBuilder("P");
        if (years > 0)
            result.append(years).append('Y');
        if (months > 0)
            result.append(months).append('M');
        if (days > 0)
            result.append(days).append('D');

        if (hours > 0 || minutes > 0 || seconds > 0) {
            result.append('T');
            if (hours > 0)
                result.append(hours).append('H');
            if (minutes > 0)
                result.append(minutes).append('M');
            if (seconds > 0)
                result.append(seconds).append('S');
        }

        if (result.length() == 1)
            result.append("T0S");

        return Val.of(result.toString());
    }

    /* ######## TIMEZONE CONVERSION FROM UTC ######## */

    @Function(docs = """
            ```toZone(TEXT utcTime, TEXT zoneId)```: Converts a UTC timestamp to a specific timezone, returning
            an ISO 8601 timestamp with offset.

            **Example:**

            The expression ```time.toZone("2021-11-08T13:00:00Z", "Europe/Berlin")```
            returns ```"2021-11-08T14:00:00+01:00"```.""")
    public static Val toZone(@Text Val utcTime, @Text Val zoneId) {
        final var instant = instantOf(utcTime);
        final var zone    = zoneIdOf(zoneId);
        final var zdt     = instant.atZone(zone);
        return Val.of(zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    @Function(docs = """
            ```toOffset(TEXT utcTime, TEXT offsetId)```: Converts a UTC timestamp to a specific offset, returning
            an ISO 8601 timestamp with that offset.

            **Example:**

            The expression ```time.toOffset("2021-11-08T13:00:00Z", "+05:30")```
            returns ```"2021-11-08T18:30:00+05:30"```.""")
    public static Val toOffset(@Text Val utcTime, @Text Val offsetId) {
        final var instant = instantOf(utcTime);
        final var offset  = parseZoneOffset(offsetId);
        final var odt     = instant.atOffset(offset);
        return Val.of(odt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    /* ######## AGE CALCULATION ######## */

    @Function(docs = """
            ```ageInYears(TEXT birthDate, TEXT currentDate)```: Calculates the age in complete years between
            birthDate and currentDate.

            Both dates must be ISO 8601 strings.

            **Example:**

            The expression ```time.ageInYears("1990-05-15", "2021-11-08")``` returns ```31```.""")
    public static Val ageInYears(@Text Val birthDate, @Text Val currentDate) {
        final var birth   = LocalDate.ofInstant(instantOf(birthDate), ZoneOffset.UTC);
        final var current = LocalDate.ofInstant(instantOf(currentDate), ZoneOffset.UTC);
        return Val.of(Period.between(birth, current).getYears());
    }

    @Function(docs = """
            ```ageInMonths(TEXT birthDate, TEXT currentDate)```: Calculates the age in complete months between
            birthDate and currentDate.

            Both dates must be ISO 8601 strings.

            **Example:**

            The expression ```time.ageInMonths("1990-05-15", "1990-08-20")``` returns ```3```.""")
    public static Val ageInMonths(@Text Val birthDate, @Text Val currentDate) {
        final var birth   = LocalDate.ofInstant(instantOf(birthDate), ZoneOffset.UTC);
        final var current = LocalDate.ofInstant(instantOf(currentDate), ZoneOffset.UTC);
        final var period  = Period.between(birth, current);
        return Val.of(period.getYears() * 12L + period.getMonths());
    }

    /* ######## HELPER METHODS ######## */

    /**
     * Parses time parameter to an Instant. Accepts ISO 8601 instant or date format.
     */
    private static Instant instantOf(Val time) {
        if (time == null || time.isTextual() && time.getText() == null) {
            throw new IllegalArgumentException("Time parameter cannot be null");
        }

        final var text = time.getText();
        if (text.isBlank()) {
            throw new IllegalArgumentException("Time parameter cannot be blank");
        }

        try {
            return Instant.parse(text);
        } catch (DateTimeParseException instantException) {
            try {
                return LocalDate.parse(text).atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException dateException) {
                throw new IllegalArgumentException(
                        "Unable to parse time parameter '" + text + "'. Expected ISO 8601 instant or date format.",
                        instantException);
            }
        }
    }

    /**
     * Parses zone parameter to a ZoneId. Returns system default if parameter is
     * blank.
     */
    private static ZoneId zoneIdOf(Val zone) {
        if (zone == null || zone.isTextual() && zone.getText() == null) {
            throw new IllegalArgumentException("Zone parameter cannot be null");
        }

        final var zoneIdStr = zone.getText().trim();
        if (zoneIdStr.isBlank())
            return ZoneId.systemDefault();

        try {
            if (ZoneId.SHORT_IDS.containsKey(zoneIdStr))
                return ZoneId.of(zoneIdStr, ZoneId.SHORT_IDS);

            return ZoneId.of(zoneIdStr);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Invalid zone ID: '" + zoneIdStr + "'", e);
        }
    }

    /**
     * Parses offset parameter to a ZoneOffset.
     */
    private static ZoneOffset parseZoneOffset(Val offset) {
        if (offset == null || offset.isTextual() && offset.getText() == null) {
            throw new IllegalArgumentException("Offset parameter cannot be null");
        }

        final var offsetStr = offset.getText();
        if (offsetStr.isBlank()) {
            throw new IllegalArgumentException("Offset parameter cannot be blank");
        }

        try {
            return ZoneOffset.of(offsetStr);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Invalid zone offset: '" + offsetStr + "'", e);
        }
    }

    /**
     * Parses chronoUnit parameter to a ChronoUnit.
     */
    private static ChronoUnit parseChronoUnit(Val unit) {
        if (unit == null || unit.isTextual() && unit.getText() == null) {
            throw new IllegalArgumentException("ChronoUnit parameter cannot be null");
        }

        final var unitStr = unit.getText().trim().toUpperCase();
        if (unitStr.isBlank()) {
            throw new IllegalArgumentException("ChronoUnit parameter cannot be blank");
        }

        try {
            return ChronoUnit.valueOf(unitStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid ChronoUnit: '" + unitStr
                    + "'. Valid values are: NANOS, MICROS, MILLIS, SECONDS, MINUTES, HOURS, HALF_DAYS, DAYS, WEEKS, MONTHS, YEARS, DECADES, CENTURIES, MILLENNIA",
                    e);
        }
    }

    /**
     * Validates temporal arithmetic bounds to prevent overflow.
     */
    private static void validateTemporalBounds(Instant instant, long amount, ChronoUnit unit, boolean isAddition) {
        try {
            if (isAddition) {
                instant.plus(amount, unit);
            } else {
                instant.minus(amount, unit);
            }
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "Temporal arithmetic out of bounds: " + (isAddition ? "adding" : "subtracting") + " " + amount + " "
                            + unit + " to/from " + instant + " would cause overflow",
                    e);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Temporal arithmetic out of bounds: "
                    + (isAddition ? "adding" : "subtracting") + " " + amount + " " + unit + " to/from " + instant, e);
        }
    }

    /**
     * Converts LocalDateTime to Instant using specified ZoneId.
     */
    private static Instant localDateTimeToInstant(LocalDateTime ldt, ZoneId zoneId) {
        return ldt.atZone(zoneId).toInstant();
    }

    /**
     * Parses local date-time string using specified formatter.
     */
    private static LocalDateTime parseLocalDateTime(String localDateTimeString, DateTimeFormatter dtf) {
        return dtf.parse(localDateTimeString, LocalDateTime::from);
    }

}
