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

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.Locale;

/**
 * Functions for temporal operations in authorization policies. Handles
 * date-time parsing, comparison, arithmetic, and
 * formatting using ISO 8601 and RFC3339 standards.
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

    /* Error message constants - categorized by input type */

    // Timestamp parsing (ISO 8601 instant format)
    private static final String ERROR_INVALID_TIMESTAMP       = "Invalid timestamp: %s.";
    private static final String ERROR_INVALID_TIMESTAMPS      = "Invalid timestamp(s) in comparison: %s, %s.";
    private static final String ERROR_INVALID_TIMESTAMP_RANGE = "Invalid timestamp(s) in range check: %s in [%s, %s].";
    private static final String ERROR_INVALID_CHRONO_UNIT     = "Invalid timestamps or chrono unit: %s, %s, %s.";

    // Temporal arithmetic (timestamp + amount)
    private static final String ERROR_TEMPORAL_ARITHMETIC = "Temporal arithmetic failed for %s with amount %s.";

    // ISO datetime parsing (with time component)
    private static final String ERROR_INVALID_ISO_DATETIME = "Invalid ISO datetime: %s.";

    // Local datetime parsing (without timezone)
    private static final String ERROR_INVALID_LOCAL_DATETIME = "Invalid local datetime: %s.";
    private static final String ERROR_INVALID_DIN_DATETIME   = "Invalid DIN datetime (expected dd.MM.yyyy HH:mm:ss): %s.";

    // Time format parsing
    private static final String ERROR_INVALID_TIME      = "Invalid time: %s.";
    private static final String ERROR_INVALID_AMPM_TIME = "Invalid AM/PM time (expected hh:mm:ss AM/PM): %s.";

    // Date format parsing
    private static final String ERROR_INVALID_DATES = "Invalid date(s): %s, %s.";

    // Timezone and offset errors
    private static final String ERROR_INVALID_TIMEZONE    = "Invalid timezone %s for datetime %s.";
    private static final String ERROR_INVALID_OFFSET      = "Invalid offset %s for datetime %s.";
    private static final String ERROR_INVALID_OFFSET_TIME = "Invalid offset %s for time %s.";
    private static final String ERROR_INVALID_ZONE_TIME   = "Invalid timezone %s for time %s on date %s.";

    // Duration errors
    private static final String ERROR_ISO_DURATION_BLANK      = "ISO duration parameter cannot be blank.";
    private static final String ERROR_INVALID_ISO_DURATION    = "Invalid ISO duration: %s.";
    private static final String ERROR_INVALID_DURATION_MILLIS = "Invalid duration milliseconds: %s.";

    // Epoch conversion
    private static final String ERROR_INVALID_EPOCH_SECONDS = "Invalid epoch seconds: %s.";
    private static final String ERROR_INVALID_EPOCH_MILLIS  = "Invalid epoch milliseconds: %s.";

    /* ######## DURATION ######## */

    @Function(docs = """
            ```durationOfSeconds(NUMBER seconds)```: Converts seconds to milliseconds for duration values.

            Durations in the temporal library are expressed in milliseconds. Multiplies seconds by 1000.

            **Example:**

            The expression ```time.durationOfSeconds(20.5)``` returns ```20500```.
            """)
    public static Value durationOfSeconds(NumberValue seconds) {
        return Value.of(seconds.value().longValue() * 1000);
    }

    @Function(docs = """
            ```durationOfMinutes(NUMBER minutes)```: Converts minutes to milliseconds for duration values.

            Multiplies minutes by 60000.

            **Example:**

            The expression ```time.durationOfMinutes(2.5)``` returns ```150000```.""")
    public static Value durationOfMinutes(NumberValue minutes) {
        return Value.of(minutes.value().longValue() * 60 * 1000);
    }

    @Function(docs = """
            ```durationOfHours(NUMBER hours)```: Converts hours to milliseconds for duration values.

            Multiplies hours by 3600000.

            **Example:**

            The expression ```time.durationOfHours(4.5)``` returns ```16200000```.""")
    public static Value durationOfHours(NumberValue hours) {
        return Value.of(hours.value().longValue() * 60 * 60 * 1000);
    }

    @Function(docs = """
            ```durationOfDays(NUMBER days)```: Converts days to milliseconds for duration values.

            Multiplies days by 86400000.

            **Example:**

            The expression ```time.durationOfDays(365)``` returns ```31536000000```.""")
    public static Value durationOfDays(NumberValue days) {
        return Value.of(days.value().longValue() * 24 * 60 * 60 * 1000);
    }

    /* ######## INSTANT/UTC COMPARISON ######## */

    @Function(docs = """
            ```before(TEXT timeA, TEXT timeB)```: Compares two instants and returns true if timeA is before timeB.

            Both parameters must be ISO 8601 strings at UTC.

            **Example:**

            The expression ```time.before("2021-11-08T13:00:00Z", "2021-11-08T13:00:01Z")``` returns ```true```.""")
    public static Value before(TextValue timeA, TextValue timeB) {
        try {
            return Value.of(instantOf(timeA).isBefore(instantOf(timeB)));
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_TIMESTAMPS.formatted(timeA, timeB), e);
        }
    }

    @Function(docs = """
            ```after(TEXT timeA, TEXT timeB)```: Compares two instants and returns true if timeA is after timeB.

            Both parameters must be ISO 8601 strings at UTC.

            **Example:**

            The expression ```time.after("2021-11-08T13:00:01Z", "2021-11-08T13:00:00Z")``` returns ```true```.""")
    public static Value after(TextValue timeA, TextValue timeB) {
        try {
            return Value.of(instantOf(timeA).isAfter(instantOf(timeB)));
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_TIMESTAMPS.formatted(timeA, timeB), e);
        }
    }

    @Function(docs = """
            ```between(TEXT time, TEXT intervalStart, TEXT intervalEnd)```: Returns true if time falls within
            the closed interval from intervalStart to intervalEnd.

            All parameters must be ISO 8601 strings at UTC. intervalStart must be before intervalEnd.

            **Example:**

            The expression ```time.between("2021-11-08T13:00:00Z", "2021-11-07T13:00:00Z", "2021-11-09T13:00:00Z")```
            returns ```true```.""")
    public static Value between(TextValue time, TextValue intervalStart, TextValue intervalEnd) {
        try {
            val t     = instantOf(time);
            val start = instantOf(intervalStart);
            val end   = instantOf(intervalEnd);

            if (t.equals(start))
                return Value.TRUE;
            else if (t.equals(end))
                return Value.TRUE;
            else
                return Value.of((t.isBefore(end) && t.isAfter(start)));
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_TIMESTAMP_RANGE.formatted(time, intervalStart, intervalEnd), e);
        }
    }

    @Function(docs = """
            ```timeBetween(TEXT timeA, TEXT timeB, TEXT chronoUnit)```: Calculates the time span between
            timeA and timeB in the specified chronoUnit.

            Both time parameters must be ISO 8601 strings at UTC. Valid chronoUnits: NANOS, MICROS, MILLIS,
            SECONDS, MINUTES, HOURS, HALF_DAYS, DAYS, WEEKS, MONTHS, YEARS, DECADES, CENTURIES, MILLENNIA.

            Month duration is estimated as one twelfth of 365.2425 days. Year duration is 365.2425 days.

            **Example:**

            The expression ```time.timeBetween("2001-01-01", "2002-01-01", "YEARS")``` returns ```1```.""")
    public static Value timeBetween(TextValue timeA, TextValue timeB, TextValue chronoUnit) {
        try {
            val unit        = parseChronoUnit(chronoUnit);
            val instantFrom = instantOf(timeA);
            val instantTo   = instantOf(timeB);
            return Value.of(calculateTimeBetween(instantFrom, instantTo, unit));
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_CHRONO_UNIT.formatted(timeA, timeB, chronoUnit), e);
        }
    }

    private static ChronoUnit parseChronoUnit(TextValue chronoUnit) {
        val unitStr = chronoUnit.value().trim().toUpperCase();
        if (unitStr.isBlank()) {
            throw new IllegalArgumentException("ChronoUnit parameter cannot be blank.");
        }
        try {
            return ChronoUnit.valueOf(unitStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid ChronoUnit: '" + unitStr
                    + "'. Valid values are: NANOS, MICROS, MILLIS, SECONDS, MINUTES, HOURS, HALF_DAYS, DAYS, WEEKS, MONTHS, YEARS, DECADES, CENTURIES, MILLENNIA.",
                    e);
        }
    }

    private static long calculateTimeBetween(Instant instantFrom, Instant instantTo, ChronoUnit unit) {
        try {
            return unit.between(instantFrom, instantTo);
        } catch (UnsupportedTemporalTypeException e) {
            val dateFrom = LocalDate.ofInstant(instantFrom, ZoneId.systemDefault());
            val dateTo   = LocalDate.ofInstant(instantTo, ZoneId.systemDefault());
            return unit.between(dateFrom, dateTo);
        }
    }

    /* ######## DATE ARITHMETIC ######## */

    @Function(docs = """
            ```plusDays(TEXT startTime, INTEGER days)```: Adds the specified number of days to startTime.

            startTime must be an ISO 8601 string at UTC. days must be an integer.

            **Example:**

            The expression ```time.plusDays("2021-11-08T13:00:00Z", 5)```
            returns ```"2021-11-13T13:00:00Z"```.""")
    public static Value plusDays(TextValue startTime, NumberValue days) {
        try {
            val instant = instantOf(startTime);
            validateTemporalBounds(instant, days.value().longValue(), ChronoUnit.DAYS, true);
            return Value.of(instant.plus(days.value().longValue(), ChronoUnit.DAYS).toString());
        } catch (Exception e) {
            return Value.error(ERROR_TEMPORAL_ARITHMETIC.formatted(startTime, days), e);
        }
    }

    @Function(docs = """
            ```plusMonths(TEXT startTime, INTEGER months)```: Adds the specified number of months to startTime.

            startTime must be an ISO 8601 string at UTC. months must be an integer.
            Uses standard calendar rules (e.g., adding 1 month to Jan 31 results in Feb 28/29).

            **Example:**

            The expression ```time.plusMonths("2021-11-08T13:00:00Z", 2)```
            returns ```"2022-01-08T13:00:00Z"```.""")
    public static Value plusMonths(TextValue startTime, NumberValue months) {
        try {
            val instant = instantOf(startTime);
            val zdt     = instant.atZone(ZoneOffset.UTC);
            return Value.of(zdt.plusMonths(months.value().longValue()).toInstant().toString());
        } catch (Exception e) {
            return Value.error(ERROR_TEMPORAL_ARITHMETIC.formatted(startTime, months), e);
        }
    }

    @Function(docs = """
            ```plusYears(TEXT startTime, INTEGER years)```: Adds the specified number of years to startTime.

            startTime must be an ISO 8601 string at UTC. years must be an integer.
            Uses standard calendar rules (e.g., adding 1 year to Feb 29 in a leap year results in Feb 28).

            **Example:**

            The expression ```time.plusYears("2021-11-08T13:00:00Z", 3)```
            returns ```"2024-11-08T13:00:00Z"```.""")
    public static Value plusYears(TextValue startTime, NumberValue years) {
        try {
            val instant = instantOf(startTime);
            val zdt     = instant.atZone(ZoneOffset.UTC);
            return Value.of(zdt.plusYears(years.value().longValue()).toInstant().toString());
        } catch (Exception e) {
            return Value.error(ERROR_TEMPORAL_ARITHMETIC.formatted(startTime, years), e);
        }
    }

    @Function(docs = """
            ```minusDays(TEXT startTime, INTEGER days)```: Subtracts the specified number of days from startTime.

            startTime must be an ISO 8601 string at UTC. days must be an integer.

            **Example:**

            The expression ```time.minusDays("2021-11-08T13:00:00Z", 5)```
            returns ```"2021-11-03T13:00:00Z"```.""")
    public static Value minusDays(TextValue startTime, NumberValue days) {
        try {
            val instant = instantOf(startTime);
            validateTemporalBounds(instant, days.value().longValue(), ChronoUnit.DAYS, false);
            return Value.of(instant.minus(days.value().longValue(), ChronoUnit.DAYS).toString());
        } catch (Exception e) {
            return Value.error(ERROR_TEMPORAL_ARITHMETIC.formatted(startTime, days), e);
        }
    }

    @Function(docs = """
            ```minusMonths(TEXT startTime, INTEGER months)```: Subtracts the specified number of months from startTime.

            startTime must be an ISO 8601 string at UTC. months must be an integer.
            Uses standard calendar rules.

            **Example:**

            The expression ```time.minusMonths("2021-11-08T13:00:00Z", 2)```
            returns ```"2021-09-08T13:00:00Z"```.""")
    public static Value minusMonths(TextValue startTime, NumberValue months) {
        try {
            val instant = instantOf(startTime);
            val zdt     = instant.atZone(ZoneOffset.UTC);
            return Value.of(zdt.minusMonths(months.value().longValue()).toInstant().toString());
        } catch (Exception e) {
            return Value.error(ERROR_TEMPORAL_ARITHMETIC.formatted(startTime, months), e);
        }
    }

    @Function(docs = """
            ```minusYears(TEXT startTime, INTEGER years)```: Subtracts the specified number of years from startTime.

            startTime must be an ISO 8601 string at UTC. years must be an integer.
            Uses standard calendar rules.

            **Example:**

            The expression ```time.minusYears("2021-11-08T13:00:00Z", 3)```
            returns ```"2018-11-08T13:00:00Z"```.""")
    public static Value minusYears(TextValue startTime, NumberValue years) {
        try {
            val instant = instantOf(startTime);
            val zdt     = instant.atZone(ZoneOffset.UTC);
            return Value.of(zdt.minusYears(years.value().longValue()).toInstant().toString());
        } catch (Exception e) {
            return Value.error(ERROR_TEMPORAL_ARITHMETIC.formatted(startTime, years), e);
        }
    }

    /* ######## INSTANT/UTC MANIPULATION ######## */

    @Function(docs = """
            ```plusNanos(TEXT startTime, INTEGER nanos)```: Adds the specified number of nanoseconds to startTime.

            startTime must be an ISO 8601 string at UTC. nanos must be an integer.

            **Example:**

            The expression ```time.plusNanos("2021-11-08T13:00:00Z", 10000000000)```
            returns ```"2021-11-08T13:00:10Z"```.""")
    public static Value plusNanos(TextValue startTime, NumberValue nanos) {
        try {
            val instant = instantOf(startTime);
            validateTemporalBounds(instant, nanos.value().longValue(), ChronoUnit.NANOS, true);
            return Value.of(instant.plusNanos(nanos.value().longValue()).toString());
        } catch (Exception e) {
            return Value.error(ERROR_TEMPORAL_ARITHMETIC.formatted(startTime, nanos), e);
        }
    }

    @Function(docs = """
            ```plusMillis(TEXT startTime, INTEGER millis)```: Adds the specified number of milliseconds to startTime.

            startTime must be an ISO 8601 string at UTC. millis must be an integer.

            **Example:**

            The expression ```time.plusMillis("2021-11-08T13:00:00Z", 10000)```
            returns ```"2021-11-08T13:00:10Z"```.""")
    public static Value plusMillis(TextValue startTime, NumberValue millis) {
        try {
            val instant = instantOf(startTime);
            validateTemporalBounds(instant, millis.value().longValue(), ChronoUnit.MILLIS, true);
            return Value.of(instant.plusMillis(millis.value().longValue()).toString());
        } catch (Exception e) {
            return Value.error(ERROR_TEMPORAL_ARITHMETIC.formatted(startTime, millis), e);
        }
    }

    @Function(docs = """
            ```plusSeconds(TEXT startTime, INTEGER seconds)```: Adds the specified number of seconds to startTime.

            startTime must be an ISO 8601 string at UTC. seconds must be an integer.

            **Example:**

            The expression ```time.plusSeconds("2021-11-08T13:00:00Z", 10)```
            returns ```"2021-11-08T13:00:10Z"```.""")
    public static Value plusSeconds(TextValue startTime, NumberValue seconds) {
        try {
            val instant = instantOf(startTime);
            validateTemporalBounds(instant, seconds.value().longValue(), ChronoUnit.SECONDS, true);
            return Value.of(instant.plusSeconds(seconds.value().longValue()).toString());
        } catch (Exception e) {
            return Value.error(ERROR_TEMPORAL_ARITHMETIC.formatted(startTime, seconds), e);
        }
    }

    @Function(docs = """
            ```minusNanos(TEXT startTime, INTEGER nanos)```: Subtracts the specified number of nanoseconds from startTime.

            startTime must be an ISO 8601 string at UTC. nanos must be an integer.

            **Example:**

            The expression ```time.minusNanos("2021-11-08T13:00:00Z", 10000000000)```
            returns ```"2021-11-08T12:59:50Z"```.""")
    public static Value minusNanos(TextValue startTime, NumberValue nanos) {
        try {
            val instant = instantOf(startTime);
            validateTemporalBounds(instant, nanos.value().longValue(), ChronoUnit.NANOS, false);
            return Value.of(instant.minusNanos(nanos.value().longValue()).toString());
        } catch (Exception e) {
            return Value.error(ERROR_TEMPORAL_ARITHMETIC.formatted(startTime, nanos), e);
        }
    }

    @Function(docs = """
            ```minusMillis(TEXT startTime, INTEGER millis)```: Subtracts the specified number of milliseconds from startTime.

            startTime must be an ISO 8601 string at UTC. millis must be an integer.

            **Example:**

            The expression ```time.minusMillis("2021-11-08T13:00:00Z", 10000)```
            returns ```"2021-11-08T12:59:50Z"```.""")
    public static Value minusMillis(TextValue startTime, NumberValue millis) {
        try {
            val instant = instantOf(startTime);
            validateTemporalBounds(instant, millis.value().longValue(), ChronoUnit.MILLIS, false);
            return Value.of(instant.minusMillis(millis.value().longValue()).toString());
        } catch (Exception e) {
            return Value.error(ERROR_TEMPORAL_ARITHMETIC.formatted(startTime, millis), e);
        }
    }

    @Function(docs = """
            ```minusSeconds(TEXT startTime, INTEGER seconds)```: Subtracts the specified number of seconds from startTime.

            startTime must be an ISO 8601 string at UTC. seconds must be an integer.

            **Example:**

            The expression ```time.minusSeconds("2021-11-08T13:00:00Z", 10)```
            returns ```"2021-11-08T12:59:50Z"```.""")
    public static Value minusSeconds(TextValue startTime, NumberValue seconds) {
        try {
            val instant = instantOf(startTime);
            validateTemporalBounds(instant, seconds.value().longValue(), ChronoUnit.SECONDS, false);
            return Value.of(instant.minusSeconds(seconds.value().longValue()).toString());
        } catch (Exception e) {
            return Value.error(ERROR_TEMPORAL_ARITHMETIC.formatted(startTime, seconds), e);
        }
    }

    /* ######## INSTANT/UTC EPOCH ######## */

    @Function(docs = """
            ```epochSecond(TEXT utcDateTime)```: Converts an ISO 8601 UTC timestamp to seconds since
            the epoch (1970-01-01T00:00:00Z).

            **Example:**

            The expression ```time.epochSecond("2021-11-08T13:00:00Z")``` returns ```1636376400```.""")
    public static Value epochSecond(TextValue utcDateTime) {
        try {
            return Value.of(instantOf(utcDateTime).getEpochSecond());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_TIMESTAMP.formatted(utcDateTime), e);
        }
    }

    @Function(docs = """
            ```epochMilli(TEXT utcDateTime)```: Converts an ISO 8601 UTC timestamp to milliseconds since
            the epoch (1970-01-01T00:00:00Z).

            **Example:**

            The expression ```time.epochMilli("2021-11-08T13:00:00Z")``` returns ```1636376400000```.""")
    public static Value epochMilli(TextValue utcDateTime) {
        try {
            return Value.of(instantOf(utcDateTime).toEpochMilli());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_TIMESTAMP.formatted(utcDateTime), e);
        }
    }

    @Function(docs = """
            ```ofEpochSecond(INTEGER epochSeconds)```: Converts seconds since the epoch to an ISO 8601 UTC timestamp.

            **Example:**

            The expression ```time.ofEpochSecond(1636376400)``` returns ```"2021-11-08T13:00:00Z"```.""")
    public static Value ofEpochSecond(NumberValue epochSeconds) {
        try {
            return Value.of(Instant.ofEpochSecond(epochSeconds.value().longValue()).toString());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_EPOCH_SECONDS.formatted(epochSeconds), e);
        }
    }

    @Function(docs = """
            ```ofEpochMilli(INTEGER epochMillis)```: Converts milliseconds since the epoch to an ISO 8601 UTC timestamp.

            **Example:**

            The expression ```time.ofEpochMilli(1636376400000)``` returns ```"2021-11-08T13:00:00Z"```.""")
    public static Value ofEpochMilli(NumberValue epochMillis) {
        try {
            return Value.of(Instant.ofEpochMilli(epochMillis.value().longValue()).toString());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_EPOCH_MILLIS.formatted(epochMillis), e);
        }
    }

    /* ######## INSTANT/UTC CALENDAR ######## */

    @Function(docs = """
            ```weekOfYear(TEXT utcDateTime)```: Returns the calendar week number (1-52) for the given date.

            utcDateTime must be an ISO 8601 string at UTC.

            **Example:**

            The expression ```time.weekOfYear("2021-11-08T13:00:00Z")``` returns ```45```.""")
    public static Value weekOfYear(TextValue isoDateTime) {
        try {
            return Value.of(
                    DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.value()).get(ChronoField.ALIGNED_WEEK_OF_YEAR));
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_ISO_DATETIME.formatted(isoDateTime), e);
        }
    }

    @Function(docs = """
            ```dayOfYear(TEXT utcDateTime)```: Returns the day of the year (1-365) for the given date.

            utcDateTime must be an ISO 8601 string at UTC.

            **Example:**

            The expression ```time.dayOfYear("2021-11-08T13:00:00Z")``` returns ```312```.""")
    public static Value dayOfYear(TextValue isoDateTime) {
        try {
            return Value.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.value()).get(ChronoField.DAY_OF_YEAR));
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_ISO_DATETIME.formatted(isoDateTime), e);
        }
    }

    @Function(docs = """
            ```dayOfWeek(TEXT utcDateTime)```: Returns the name of the weekday for the given date.

            Returns one of: SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY.
            utcDateTime must be an ISO 8601 string at UTC.

            **Example:**

            The expression ```time.dayOfWeek("2021-11-08T13:00:00Z")``` returns ```"MONDAY"```.""")
    public static Value dayOfWeek(TextValue isoDateTime) {
        try {
            return Value.of(DayOfWeek
                    .from(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.value(), LocalDateTime::from)).toString());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_ISO_DATETIME.formatted(isoDateTime), e);
        }
    }

    /* ######## VALIDATION ######## */

    @Function(docs = """
            ```validUTC(TEXT utcDateTime)```: Returns true if the value is a valid ISO 8601 UTC timestamp.

            **Example:**

            The expression ```time.validUTC("2021-11-08T13:00:00Z")``` returns ```true```.
            The expression ```time.validUTC("20111-000:00Z")``` returns ```false```.""")
    public static Value validUTC(Value utcDateTime) {
        if (!(utcDateTime instanceof TextValue textValue)) {
            return Value.FALSE;
        }
        try {
            instantOf(textValue);
            return Value.TRUE;
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return Value.FALSE;
        }
    }

    @Function(docs = """
            ```validRFC3339(TEXT timestamp)```: Returns true if the value is a valid RFC3339 timestamp.

            RFC3339 requires a timezone designator (Z or offset like +05:00).

            **Example:**

            The expression ```time.validRFC3339("2021-11-08T13:00:00Z")``` returns ```true```.
            The expression ```time.validRFC3339("2021-11-08T13:00:00")``` returns ```false``` (missing timezone).
            The expression ```time.validRFC3339("2021-11-08")``` returns ```false``` (date only, no time).""")
    public static Value validRFC3339(Value timestamp) {
        if (!(timestamp instanceof TextValue textValue)) {
            return Value.FALSE;
        }
        try {
            val text = textValue.value();
            if (text.isBlank()) {
                return Value.FALSE;
            }

            Instant.parse(text);
            if (!text.contains("T") || (!text.endsWith("Z") && !text.matches(".*[+-]\\d{2}:\\d{2}$"))) {
                return Value.FALSE;
            }

            return Value.TRUE;
        } catch (DateTimeParseException e) {
            return Value.FALSE;
        }
    }

    /* ######## TEMPORAL BOUNDS ######## */

    @Function(docs = """
            ```startOfDay(TEXT dateTime)```: Returns the start of the day (00:00:00.000) for the given date-time at UTC.

            **Example:**

            The expression ```time.startOfDay("2021-11-08T13:45:30Z")``` returns ```"2021-11-08T00:00:00Z"```.""")
    public static Value startOfDay(TextValue dateTime) {
        try {
            val instant = instantOf(dateTime);
            val date    = LocalDate.ofInstant(instant, ZoneOffset.UTC);
            return Value.of(date.atStartOfDay(ZoneOffset.UTC).toInstant().toString());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_TIMESTAMP.formatted(dateTime), e);
        }
    }

    @Function(docs = """
            ```endOfDay(TEXT dateTime)```: Returns the end of the day (23:59:59.999999999) for the given date-time at UTC.

            **Example:**

            The expression ```time.endOfDay("2021-11-08T13:45:30Z")``` returns ```"2021-11-08T23:59:59.999999999Z"```.""")
    public static Value endOfDay(TextValue dateTime) {
        try {
            val instant = instantOf(dateTime);
            val date    = LocalDate.ofInstant(instant, ZoneOffset.UTC);
            return Value.of(date.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant().toString());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_TIMESTAMP.formatted(dateTime), e);
        }
    }

    @Function(docs = """
            ```startOfWeek(TEXT dateTime)```: Returns the start of the week (Monday 00:00:00.000) for the given date-time at UTC.

            Weeks start on Monday per ISO 8601.

            **Example:**

            The expression ```time.startOfWeek("2021-11-08T13:45:30Z")``` returns ```"2021-11-08T00:00:00Z"```
            (November 8, 2021 was a Monday).""")
    public static Value startOfWeek(TextValue dateTime) {
        try {
            val instant = instantOf(dateTime);
            val date    = LocalDate.ofInstant(instant, ZoneOffset.UTC);
            val monday  = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return Value.of(monday.atStartOfDay(ZoneOffset.UTC).toInstant().toString());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_TIMESTAMP.formatted(dateTime), e);
        }
    }

    @Function(docs = """
            ```endOfWeek(TEXT dateTime)```: Returns the end of the week (Sunday 23:59:59.999999999) for the given date-time at UTC.

            Weeks end on Sunday per ISO 8601.

            **Example:**

            The expression ```time.endOfWeek("2021-11-08T13:45:30Z")``` returns ```"2021-11-14T23:59:59.999999999Z"```.""")
    public static Value endOfWeek(TextValue dateTime) {
        try {
            val instant = instantOf(dateTime);
            val date    = LocalDate.ofInstant(instant, ZoneOffset.UTC);
            val sunday  = date.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            return Value.of(sunday.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant().toString());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_TIMESTAMP.formatted(dateTime), e);
        }
    }

    @Function(docs = """
            ```startOfMonth(TEXT dateTime)```: Returns the start of the month (first day at 00:00:00.000) for the given date-time at UTC.

            **Example:**

            The expression ```time.startOfMonth("2021-11-08T13:45:30Z")``` returns ```"2021-11-01T00:00:00Z"```.""")
    public static Value startOfMonth(TextValue dateTime) {
        try {
            val instant = instantOf(dateTime);
            val date    = LocalDate.ofInstant(instant, ZoneOffset.UTC);
            return Value.of(date.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant().toString());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_TIMESTAMP.formatted(dateTime), e);
        }
    }

    @Function(docs = """
            ```endOfMonth(TEXT dateTime)```: Returns the end of the month (last day at 23:59:59.999999999) for the given date-time at UTC.

            **Example:**

            The expression ```time.endOfMonth("2021-11-08T13:45:30Z")``` returns ```"2021-11-30T23:59:59.999999999Z"```.""")
    public static Value endOfMonth(TextValue dateTime) {
        try {
            val instant = instantOf(dateTime);
            val date    = LocalDate.ofInstant(instant, ZoneOffset.UTC);
            val lastDay = date.withDayOfMonth(date.lengthOfMonth());
            return Value.of(lastDay.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant().toString());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_TIMESTAMP.formatted(dateTime), e);
        }
    }

    @Function(docs = """
            ```startOfYear(TEXT dateTime)```: Returns the start of the year (January 1 at 00:00:00.000) for the given date-time at UTC.

            **Example:**

            The expression ```time.startOfYear("2021-11-08T13:45:30Z")``` returns ```"2021-01-01T00:00:00Z"```.""")
    public static Value startOfYear(TextValue dateTime) {
        try {
            val instant = instantOf(dateTime);
            val date    = LocalDate.ofInstant(instant, ZoneOffset.UTC);
            return Value.of(date.withDayOfYear(1).atStartOfDay(ZoneOffset.UTC).toInstant().toString());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_TIMESTAMP.formatted(dateTime), e);
        }
    }

    @Function(docs = """
            ```endOfYear(TEXT dateTime)```: Returns the end of the year (December 31 at 23:59:59.999999999) for the given date-time at UTC.

            **Example:**

            The expression ```time.endOfYear("2021-11-08T13:45:30Z")``` returns ```"2021-12-31T23:59:59.999999999Z"```.""")
    public static Value endOfYear(TextValue dateTime) {
        try {
            val instant = instantOf(dateTime);
            val date    = LocalDate.ofInstant(instant, ZoneOffset.UTC);
            val lastDay = date.withDayOfYear(date.lengthOfYear());
            return Value.of(lastDay.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant().toString());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_TIMESTAMP.formatted(dateTime), e);
        }
    }

    /* ######## TEMPORAL ROUNDING ######## */

    @Function(docs = """
            ```truncateToHour(TEXT dateTime)```: Truncates the date-time to the hour, setting minutes, seconds, and nanoseconds to zero.

            **Example:**

            The expression ```time.truncateToHour("2021-11-08T13:45:30.123Z")``` returns ```"2021-11-08T13:00:00Z"```.""")
    public static Value truncateToHour(TextValue dateTime) {
        try {
            val instant = instantOf(dateTime);
            return Value.of(instant.truncatedTo(ChronoUnit.HOURS).toString());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_TIMESTAMP.formatted(dateTime), e);
        }
    }

    @Function(docs = """
            ```truncateToDay(TEXT dateTime)```: Truncates the date-time to the day, setting time to 00:00:00.000.

            **Example:**

            The expression ```time.truncateToDay("2021-11-08T13:45:30Z")``` returns ```"2021-11-08T00:00:00Z"```.""")
    public static Value truncateToDay(TextValue dateTime) {
        try {
            return startOfDay(dateTime);
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_TIMESTAMP.formatted(dateTime), e);
        }
    }

    @Function(docs = """
            ```truncateToWeek(TEXT dateTime)```: Truncates the date-time to the start of the week (Monday 00:00:00.000).

            **Example:**

            The expression ```time.truncateToWeek("2021-11-08T13:45:30Z")``` returns ```"2021-11-08T00:00:00Z"```.""")
    public static Value truncateToWeek(TextValue dateTime) {
        try {
            return startOfWeek(dateTime);
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_TIMESTAMP.formatted(dateTime), e);
        }
    }

    @Function(docs = """
            ```truncateToMonth(TEXT dateTime)```: Truncates the date-time to the start of the month (first day at 00:00:00.000).

            **Example:**

            The expression ```time.truncateToMonth("2021-11-08T13:45:30Z")``` returns ```"2021-11-01T00:00:00Z"```.""")
    public static Value truncateToMonth(TextValue dateTime) {
        try {
            return startOfMonth(dateTime);
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_TIMESTAMP.formatted(dateTime), e);
        }
    }

    @Function(docs = """
            ```truncateToYear(TEXT dateTime)```: Truncates the date-time to the start of the year (January 1 at 00:00:00.000).

            **Example:**

            The expression ```time.truncateToYear("2021-11-08T13:45:30Z")``` returns ```"2021-01-01T00:00:00Z"```.""")
    public static Value truncateToYear(TextValue dateTime) {
        try {
            return startOfYear(dateTime);
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_TIMESTAMP.formatted(dateTime), e);
        }
    }

    /* ######## VALIDATION ######## */

    @Function(docs = """
            ```localIso(TEXT localDateTime)```: Parses an ISO 8601 date-time string without timezone offset using
            the PDP's system default timezone.

            **Example:**

            With system default timezone Europe/Berlin, the expression
            ```time.localIso("2021-11-08T13:00:00")``` returns ```"2021-11-08T12:00:00Z"```.""")
    public static Value localIso(TextValue localDateTime) {
        try {
            return Value.of(localDateTimeToInstant(
                    parseLocalDateTime(localDateTime.value(), DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    ZoneId.systemDefault()).toString());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_LOCAL_DATETIME.formatted(localDateTime), e);
        }
    }

    @Function(docs = """
            ```localDin(TEXT dinDateTime)```: Parses a DIN date-time string without timezone offset using
            the PDP's system default timezone. Returns an ISO 8601 string.

            **Example:**

            With system default timezone Europe/Berlin, the expression
            ```time.localDin("08.11.2021 13:00:00")``` returns ```"2021-11-08T12:00:00Z"```.""")
    public static Value localDin(TextValue dinDateTime) {
        try {
            return Value.of(localDateTimeToInstant(parseLocalDateTime(dinDateTime.value(), DIN_DATE_TIME_FORMATTER),
                    ZoneId.systemDefault()).toString());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_DIN_DATETIME.formatted(dinDateTime), e);
        }
    }

    @Function(docs = """
            ```dateTimeAtOffset(TEXT localDateTime, TEXT offsetId)```: Parses a local date-time string and
            combines it with an offset, then converts to an ISO 8601 instant at UTC.

            **Example:**

            The expression ```time.dateTimeAtOffset("2021-11-08T13:12:35", "+05:00")```
            returns ```"2021-11-08T08:12:35Z"```.""")
    public static Value dateTimeAtOffset(TextValue localDateTime, TextValue offsetId) {
        try {
            val ldt = DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(localDateTime.value(), LocalDateTime::from);
            val odt = OffsetDateTime.of(ldt, parseZoneOffset(offsetId));
            return Value.of(odt.withOffsetSameInstant(ZoneOffset.UTC).toInstant().toString());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_OFFSET.formatted(offsetId, localDateTime), e);
        }
    }

    @Function(docs = """
            ```dateTimeAtZone(TEXT localDateTime, TEXT zoneId)```: Parses an ISO 8601 date-time string and
            returns the matching ISO 8601 instant at UTC for the provided timezone.

            If zoneId is empty or blank, uses system default timezone.
            See [timezone database](https://en.wikipedia.org/wiki/List_of_tz_database_time_zones) for valid zoneId values.

            **Example:**

            The expression ```time.dateTimeAtZone("2021-11-08T13:12:35", "Europe/Berlin")```
            returns ```"2021-11-08T12:12:35Z"```.""")
    public static Value dateTimeAtZone(TextValue localDateTime, TextValue zoneId) {
        try {
            val ldt = DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(localDateTime.value(), LocalDateTime::from);
            val zdt = ZonedDateTime.of(ldt, zoneIdOf(zoneId));
            return Value.of(zdt.withZoneSameInstant(ZoneId.of("UTC")).toInstant().toString());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_TIMEZONE.formatted(zoneId, localDateTime), e);
        }
    }

    @Function(docs = """
            ```offsetDateTime(TEXT isoDateTime)```: Parses an ISO 8601 date-time with offset and
            returns the matching ISO 8601 instant at UTC.

            **Example:**

            The expression ```time.offsetDateTime("2021-11-08T13:12:35+05:00")```
            returns ```"2021-11-08T08:12:35Z"```.""")
    public static Value offsetDateTime(TextValue isoDateTime) {
        try {
            val offsetDateTime = DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.value(), OffsetDateTime::from);
            return Value.of(offsetDateTime.withOffsetSameInstant(ZoneOffset.UTC).toInstant().toString());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_ISO_DATETIME.formatted(isoDateTime), e);
        }
    }

    /* ######## TIME CONVERSION ######## */

    @Function(docs = """
            ```offsetTime(TEXT isoTime)```: Parses an ISO 8601 time with offset and
            returns the matching time at UTC.

            **Example:**

            The expression ```time.offsetTime("13:12:35-05:00")``` returns ```"18:12:35"```.""")
    public static Value offsetTime(TextValue isoTime) {
        try {
            val offsetTime = DateTimeFormatter.ISO_TIME.parse(isoTime.value(), OffsetTime::from);
            return Value.of(offsetTime.withOffsetSameInstant(ZoneOffset.UTC).toLocalTime().toString());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_TIME.formatted(isoTime), e);
        }
    }

    @Function(docs = """
            ```timeAtOffset(TEXT localTime, TEXT offsetId)```: Parses a time with a separate offset parameter and
            returns the matching time at UTC.

            **Example:**

            The expression ```time.timeAtOffset("13:12:35", "-05:00")``` returns ```"18:12:35"```.""")
    public static Value timeAtOffset(TextValue localTime, TextValue offsetId) {
        try {
            val lt     = DateTimeFormatter.ISO_LOCAL_TIME.parse(localTime.value(), LocalTime::from);
            val offset = parseZoneOffset(offsetId);
            return Value.of(OffsetTime.of(lt, offset).withOffsetSameInstant(ZoneOffset.UTC).toLocalTime().toString());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_OFFSET_TIME.formatted(offsetId, localTime), e);
        }
    }

    @Function(docs = """
            ```timeInZone(TEXT localTime, TEXT localDate, TEXT zoneId)```: Parses a time and date with a separate
            timezone parameter and returns the matching time at UTC.

            **Example:**

            The expression ```time.timeInZone("13:12:35", "2022-01-14", "US/Pacific")``` returns ```"21:12:35"```.""")
    public static Value timeInZone(TextValue localTime, TextValue localDate, TextValue zoneId) {
        try {
            val zone          = zoneIdOf(zoneId);
            val lt            = DateTimeFormatter.ISO_LOCAL_TIME.parse(localTime.value(), LocalTime::from);
            val zonedDateTime = ZonedDateTime.of(lt.atDate(LocalDate.parse(localDate.value())), zone);
            return Value.of(zonedDateTime.withZoneSameInstant(ZoneId.of("UTC")).toLocalTime().toString());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_ZONE_TIME.formatted(zoneId, localTime, localDate), e);
        }
    }

    @Function(docs = """
            ```timeAMPM(TEXT timeInAMPM)```: Parses a time string in AM/PM format and converts it to 24-hour format.

            **Example:**

            The expression ```time.timeAMPM("08:12:35 PM")``` returns ```"20:12:35"```.""")
    public static Value timeAMPM(TextValue timeInAMPM) {
        try {
            val lt = US_TIME_FORMATTER.parse(timeInAMPM.value(), LocalTime::from);
            return Value.of(lt.toString());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_AMPM_TIME.formatted(timeInAMPM), e);
        }
    }

    /* ######## EXTRACT PARTS ######## */

    @Function(docs = """
            ```dateOf(TEXT isoDateTime)```: Returns the date part of an ISO 8601 string.

            **Example:**

            The expression ```time.dateOf("2021-11-08T13:00:00Z")``` returns ```"2021-11-08"```.""")
    public static Value dateOf(TextValue isoDateTime) {
        try {
            return Value.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.value(), LocalDate::from).toString());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_ISO_DATETIME.formatted(isoDateTime), e);
        }
    }

    @Function(docs = """
            ```timeOf(TEXT isoDateTime)```: Returns the local time of an ISO 8601 string, truncated to seconds.

            **Example:**

            The expression ```time.timeOf("2021-11-08T13:00:00Z")``` returns ```"13:00:00"```.""")
    public static Value timeOf(TextValue isoDateTime) {
        try {
            val time = DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.value(), LocalTime::from)
                    .truncatedTo(ChronoUnit.SECONDS);
            return Value.of(time.format(DateTimeFormatter.ISO_LOCAL_TIME));
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_ISO_DATETIME.formatted(isoDateTime), e);
        }
    }

    @Function(docs = """
            ```hourOf(TEXT isoDateTime)```: Returns the hour of an ISO 8601 string.

            **Example:**

            The expression ```time.hourOf("2021-11-08T13:17:23Z")``` returns ```13```.""")
    public static Value hourOf(TextValue isoDateTime) {
        try {
            return Value.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.value(), LocalTime::from).getHour());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_ISO_DATETIME.formatted(isoDateTime), e);
        }
    }

    @Function(docs = """
            ```minuteOf(TEXT isoDateTime)```: Returns the minute of an ISO 8601 string.

            **Example:**

            The expression ```time.minuteOf("2021-11-08T13:17:23Z")``` returns ```17```.""")
    public static Value minuteOf(TextValue isoDateTime) {
        try {
            return Value.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.value(), LocalTime::from).getMinute());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_ISO_DATETIME.formatted(isoDateTime), e);
        }
    }

    @Function(docs = """
            ```secondOf(TEXT isoDateTime)```: Returns the second of an ISO 8601 string.

            **Example:**

            The expression ```time.secondOf("2021-11-08T13:00:23Z")``` returns ```23```.""")
    public static Value secondOf(TextValue isoDateTime) {
        try {
            return Value.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.value(), LocalTime::from).getSecond());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_ISO_DATETIME.formatted(isoDateTime), e);
        }
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
    public static Value durationFromISO(TextValue isoDuration) {
        try {
            if (isoDuration.value().isBlank()) {
                return Value.error(ERROR_ISO_DURATION_BLANK);
            }

            val durationStr = isoDuration.value();
            var totalMillis = 0L;

            val hasPeriodComponents = durationStr.contains("Y")
                    || (durationStr.contains("M") && !durationStr.contains("T"));

            if (hasPeriodComponents) {
                val period = Period.parse(durationStr);
                totalMillis += (long) (period.getYears() * 365.2425 * 24 * 60 * 60 * 1000);
                totalMillis += (long) (period.getMonths() * 30.436875 * 24 * 60 * 60 * 1000);
                totalMillis += period.getDays() * 24L * 60 * 60 * 1000;

                if (durationStr.contains("T")) {
                    val timePartStart = durationStr.indexOf('T');
                    val timePart      = "PT" + durationStr.substring(timePartStart + 1);
                    val duration      = Duration.parse(timePart);
                    totalMillis += duration.toMillis();
                }
            } else {
                val duration = Duration.parse(durationStr);
                totalMillis = duration.toMillis();
            }

            return Value.of(totalMillis);
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_ISO_DURATION.formatted(isoDuration), e);
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
    public static Value durationToISOCompact(NumberValue milliseconds) {
        try {
            val duration = Duration.ofMillis(milliseconds.value().longValue());
            return Value.of(duration.toString());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_DURATION_MILLIS.formatted(milliseconds), e);
        }
    }

    @Function(docs = """
            ```durationToISOVerbose(NUMBER milliseconds)```: Converts a duration in milliseconds to a verbose
            ISO 8601 duration string with approximate years and months.

            Uses approximation: 1 year = 365.2425 days, 1 month = 30.436875 days.

            **Examples:**

            The expression ```time.durationToISOVerbose(31536000000)``` returns approximately ```"P1Y"```.
            The expression ```time.durationToISOVerbose(86400000)``` returns ```"P1D"```.""")
    public static Value durationToISOVerbose(NumberValue milliseconds) {
        try {
            var remainingMillis = milliseconds.value().longValue();

            val millisPerYear  = (long) (365.2425 * 24 * 60 * 60 * 1000);
            val millisPerMonth = (long) (30.436875 * 24 * 60 * 60 * 1000);

            val years = remainingMillis / millisPerYear;
            remainingMillis %= millisPerYear;

            val months = remainingMillis / millisPerMonth;
            remainingMillis %= millisPerMonth;

            val duration = Duration.ofMillis(remainingMillis);
            val days     = duration.toDays();
            val hours    = duration.toHoursPart();
            val minutes  = duration.toMinutesPart();
            val seconds  = duration.toSecondsPart();

            val result = new StringBuilder("P");
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

            return Value.of(result.toString());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_DURATION_MILLIS.formatted(milliseconds), e);
        }
    }

    /* ######## TIMEZONE CONVERSION FROM UTC ######## */

    @Function(docs = """
            ```toZone(TEXT utcTime, TEXT zoneId)```: Converts a UTC timestamp to a specific timezone, returning
            an ISO 8601 timestamp with offset.

            **Example:**

            The expression ```time.toZone("2021-11-08T13:00:00Z", "Europe/Berlin")```
            returns ```"2021-11-08T14:00:00+01:00"```.""")
    public static Value toZone(TextValue utcTime, TextValue zoneId) {
        try {
            val instant = instantOf(utcTime);
            val zone    = zoneIdOf(zoneId);
            val zdt     = instant.atZone(zone);
            return Value.of(zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_TIMEZONE.formatted(zoneId, utcTime), e);
        }
    }

    @Function(docs = """
            ```toOffset(TEXT utcTime, TEXT offsetId)```: Converts a UTC timestamp to a specific offset, returning
            an ISO 8601 timestamp with that offset.

            **Example:**

            The expression ```time.toOffset("2021-11-08T13:00:00Z", "+05:30")```
            returns ```"2021-11-08T18:30:00+05:30"```.""")
    public static Value toOffset(TextValue utcTime, TextValue offsetId) {
        try {
            val instant = instantOf(utcTime);
            val offset  = parseZoneOffset(offsetId);
            val odt     = instant.atOffset(offset);
            return Value.of(odt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_OFFSET.formatted(offsetId, utcTime), e);
        }
    }

    /* ######## AGE CALCULATION ######## */

    @Function(docs = """
            ```ageInYears(TEXT birthDate, TEXT currentDate)```: Calculates the age in complete years between
            birthDate and currentDate.

            Both dates must be ISO 8601 strings.

            **Example:**

            The expression ```time.ageInYears("1990-05-15", "2021-11-08")``` returns ```31```.""")
    public static Value ageInYears(TextValue birthDate, TextValue currentDate) {
        try {
            val birth   = LocalDate.ofInstant(instantOf(birthDate), ZoneOffset.UTC);
            val current = LocalDate.ofInstant(instantOf(currentDate), ZoneOffset.UTC);
            return Value.of(Period.between(birth, current).getYears());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_DATES.formatted(birthDate, currentDate), e);
        }
    }

    @Function(docs = """
            ```ageInMonths(TEXT birthDate, TEXT currentDate)```: Calculates the age in complete months between
            birthDate and currentDate.

            Both dates must be ISO 8601 strings.

            **Example:**

            The expression ```time.ageInMonths("1990-05-15", "1990-08-20")``` returns ```3```.""")
    public static Value ageInMonths(TextValue birthDate, TextValue currentDate) {
        try {
            val birth   = LocalDate.ofInstant(instantOf(birthDate), ZoneOffset.UTC);
            val current = LocalDate.ofInstant(instantOf(currentDate), ZoneOffset.UTC);
            val period  = Period.between(birth, current);
            return Value.of(period.getYears() * 12L + period.getMonths());
        } catch (Exception e) {
            return Value.error(ERROR_INVALID_DATES.formatted(birthDate, currentDate), e);
        }
    }

    /* ######## HELPER METHODS ######## */

    /**
     * Parses time parameter to an Instant. Accepts ISO 8601 instant or date format.
     */
    private static Instant instantOf(TextValue time) {
        val text = time.value();
        if (text.isBlank()) {
            throw new IllegalArgumentException("Time parameter cannot be blank.");
        }
        return parseInstantOrDate(text);
    }

    private static Instant parseInstantOrDate(String text) {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException instantException) {
            return parseAsDateFallback(text, instantException);
        }
    }

    private static Instant parseAsDateFallback(String text, DateTimeParseException originalException) {
        try {
            return LocalDate.parse(text).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException dateException) {
            throw new IllegalArgumentException(
                    "Unable to parse time parameter '" + text + "'. Expected ISO 8601 instant or date format.",
                    originalException);
        }
    }

    /**
     * Parses zone parameter to a ZoneId. Returns system default if parameter is
     * blank.
     */
    private static ZoneId zoneIdOf(TextValue zone) {
        val zoneIdStr = zone.value().trim();
        if (zoneIdStr.isBlank())
            return ZoneId.systemDefault();

        try {
            if (ZoneId.SHORT_IDS.containsKey(zoneIdStr))
                return ZoneId.of(zoneIdStr, ZoneId.SHORT_IDS);

            return ZoneId.of(zoneIdStr);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Invalid zone ID: '" + zoneIdStr + "'.", e);
        }
    }

    /**
     * Parses offset parameter to a ZoneOffset.
     */
    private static ZoneOffset parseZoneOffset(TextValue offset) {
        val offsetStr = offset.value();
        if (offsetStr.isBlank()) {
            throw new IllegalArgumentException("Offset parameter cannot be blank.");
        }

        try {
            return ZoneOffset.of(offsetStr);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Invalid zone offset: '" + offsetStr + "'.", e);
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
