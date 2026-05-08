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
package io.sapl.attributes.libraries.vnext;

import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.Stream;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.attributes.libraries.vnext.util.Streams;
import io.sapl.attributes.libraries.vnext.util.TimeScheduler;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.function.Supplier;

@RequiredArgsConstructor
@PolicyInformationPoint(name = TimePolicyInformationPoint.NAME, description = TimePolicyInformationPoint.DESCRIPTION)
public class TimePolicyInformationPoint {

    public static final String  NAME                      = "time";
    public static final String  DESCRIPTION               = """
            Policy Information Point and attributes for retrieving current date and time information and
            basic temporal logic.""";
    private static final String ERROR_CLOCK_RETURNED_NULL = "Clock returned null. The time PIP is misconfigured: a Clock bean must be supplied that always returns a valid Instant.";
    private static final String ERROR_DAY_NAME_INVALID    = "Invalid day of week name: '%s'. Expected one of MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY.";
    private static final String ERROR_DAYS_ARRAY_EMPTY    = "The days array must not be empty.";
    private static final String ERROR_END_DAY_INVALID     = "Invalid end day of week: '%s'. Expected one of MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY.";
    private static final String ERROR_END_MONTH_INVALID   = "Invalid end month number: %d. Expected a value between 1 and 12.";
    private static final String ERROR_INTERVAL_NEGATIVE   = "Time update interval must not be negative.";
    private static final String ERROR_INTERVAL_ZERO       = "Time update interval must not be zero.";
    private static final String ERROR_MONTH_NAME_INVALID  = "Invalid month: '%s'. Expected a month name (e.g., JANUARY) or number (1-12).";
    private static final String ERROR_MONTHS_ARRAY_EMPTY  = "The months array must not be empty.";
    private static final String ERROR_START_DAY_INVALID   = "Invalid start day of week: '%s'. Expected one of MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY.";
    private static final String ERROR_START_MONTH_INVALID = "Invalid start month number: %d. Expected a value between 1 and 12.";
    private static final String ERROR_TIMEZONE_INVALID    = "Invalid timezone: '%s'.";

    private static final NumberValue       DEFAULT_UPDATE_INTERVAL_IN_MS = Value.of(1000L);
    private static final DateTimeFormatter ISO_FORMATTER                 = DateTimeFormatter.ISO_DATE_TIME
            .withZone(ZoneId.from(ZoneOffset.UTC));

    private final Clock         clock;
    private final TimeScheduler scheduler;

    /**
     * Stream of the current ISO-8601 UTC timestamp, emitted once per
     * second.
     *
     * @return a stream of {@link TextValue} timestamps
     */
    @EnvironmentAttribute(docs = """
            ```<time.now>``` is an environment attribute stream and takes no left-hand arguments.
            ```<time.now>```emits the current date and time as an ISO 8601 String at UTC.
            The first time is emitted instantly.
            After that the time is emitted once every second.

            Example:
            ```sapl
            policy "time example"
            permit
               time.dayOfWeek(<time.now>) == "MONDAY";
            ```
            """)
    public Stream<Value> now() {
        return now(DEFAULT_UPDATE_INTERVAL_IN_MS);
    }

    /**
     * Stream of the current ISO-8601 UTC timestamp, emitted once per
     * {@code updateIntervalInMillis}.
     *
     * @param updateIntervalInMillis polling interval in milliseconds; must be
     * positive and non-zero
     * @return a stream of {@link TextValue} timestamps, or an error value if the
     * interval is invalid
     */
    @EnvironmentAttribute(docs = """
            ```<time.now(INTEGER>0 updateIntervalInMillis>``` is an environment attribute stream and takes no left-hand arguments.
            ```<time.now(updateIntervalInMillis>``` emits the current date and time as an ISO 8601 String at UTC.
            The first time is emitted instantly.
            After that the time is emitted once every ```updateIntervalInMillis`` milliseconds.

            Example:
            ```sapl
            policy "time example"
            permit
               time.dayOfWeek(<time.now(time.durationOfMinutes(5)>) == "MONDAY";
            ```
            """)
    public Stream<Value> now(NumberValue updateIntervalInMillis) {
        return guarded(() -> {
            val interval = numberValueMsToNonZeroPositiveDuration(updateIntervalInMillis);
            return Streams.scheduledPoll(interval, instantSupplier(), clock, scheduler);
        });
    }

    /**
     * Stream of the JVM default time zone identifier, re-emitted only
     * when it changes (checked every five minutes).
     *
     * @return a stream of {@link TextValue} zone identifiers
     */
    @EnvironmentAttribute(docs = """
            ```<time.systemTimeZone>``` is an environment attribute stream and takes no left-hand arguments.
            ```<time.systemTimeZone>``` emits the PDP's system time zone code.
            The zone is initially emitted instantly. After that the attribute verifies if the time zone changed every five
            minutes and emits an update, if the time zone changed.

            Example: The expression ```<time.systemTimeZone>``` will emit ```"US/Pacific"``` if the PDP's host default
            time zone is set this way and will not emit anything if no changes are made.
            """)
    public Stream<Value> systemTimeZone() {
        return Streams.distinctUntilChanged(Streams.scheduledPoll(Duration.ofMinutes(5L),
                () -> Value.of(ZoneId.systemDefault().toString()), clock, scheduler));
    }

    /**
     * Stream that emits {@code false} until the {@code checkpoint}
     * instant is reached, then emits {@code true}. Boundary-driven; no
     * polling.
     *
     * @param checkpoint an ISO-8601 UTC instant to compare against
     * @return a stream of {@link BooleanValue}, or an error value if the checkpoint
     * is malformed
     */
    @EnvironmentAttribute(docs = """
            ```<time.nowIsAfter(TEXT checkpoint)>``` is an environment attribute stream and takes no left-hand arguments.
            ```<time.nowIsAfter(checkpoint)>``` ```true```, if the current date time is after the ```checkpoint```
            time (ISO 8601 String at UTC) and ```false```otherwise.
            The attribute immediately emits the comparison result between the current time and the ```checkpoint```.
            If the time at the beginning of the attribute stream was before the ```checkpoint``` and it returned ```false```,
            then immediately after the ```checkpoint``` time is reached it will emit ```true```.
            This *attribute is not polling the clock* and should be used instead of  ```time.after(<time.now>, checkpoint)```,
            which would poll the clock regularly.

            Example:
            ```sapl
            policy "time example"
            permit action == "work";
              <time.nowIsAfter(subject.employmentStart)>;
            ```

            Alternatively, assume that the current time is ```"2021-11-08T13:00:00Z"``` then the expression
            ```<time.nowIsAfter("2021-11-08T14:30:00Z")>``` will immediately return ```false``` and after
            90 minutes it will emit ```true```.""")
    public Stream<Value> nowIsAfter(TextValue checkpoint) {
        return guarded(() -> nowIsAfterStream(textValueToInstant(checkpoint)));
    }

    /**
     * Stream that compares the current local time of day in the clock's
     * configured zone against {@code checkpoint}, toggling at the
     * checkpoint and at midnight.
     *
     * @param checkpoint a local time of day, e.g. {@code "17:00"}
     * @return a stream of {@link BooleanValue}, or an error value if the checkpoint
     * is malformed
     */
    @EnvironmentAttribute(docs = """
            ```<time.localTimeIsAfter(TEXT checkpoint)>``` is an environment attribute stream and takes no left-hand arguments.
            ```<time.localTimeIsAfter(checkpoint)>``` ```true```, if the current time of the day without date is after the
            ```checkpoint``` time (e.g., "17:00") in the clock's configured timezone and ```false``` otherwise.
            This is examined relative to the current day. I.e., the answer toggles at "00:00".
            This *attribute is not polling the clock* and should be used instead of doing manual comparisons against
            ```<time.now>```, which would poll the clock regularly.

            Example:
            ```sapl
            policy "time example"
            permit action == "work";
              <time.localTimeIsAfter(subject.startTimeOfShift)>;
            ```
            """)
    public Stream<Value> localTimeIsAfter(TextValue checkpoint) {
        return guarded(() -> localTimeIsAfterStream(LocalTime.parse(checkpoint.value()), defaultZone()));
    }

    /**
     * Like {@link #localTimeIsAfter(TextValue)} but evaluated in
     * {@code timezone}.
     *
     * @param checkpoint a local time of day, e.g. {@code "17:00"}
     * @param timezone an IANA zone identifier, e.g. {@code "Europe/Berlin"}
     * @return a stream of {@link BooleanValue}, or an error value on bad input
     */
    @EnvironmentAttribute(docs = """
            ```<time.localTimeIsAfter(TEXT checkpoint, TEXT timezone)>``` is an environment attribute stream and takes no
            left-hand arguments.
            ```<time.localTimeIsAfter(checkpoint, timezone)>``` ```true```, if the current time of the day without date is
            after the ```checkpoint``` time (e.g., "17:00") in the given ```timezone``` (e.g., "Europe/Berlin") and
            ```false``` otherwise. This is examined relative to the current day. I.e., the answer toggles at "00:00".

            Example:
            ```sapl
            policy "time example"
            permit action == "work";
              <time.localTimeIsAfter(subject.startTimeOfShift, "Europe/Berlin")>;
            ```
            """)
    public Stream<Value> localTimeIsAfter(TextValue checkpoint, TextValue timezone) {
        return guarded(() -> {
            val zone = resolveZone(timezone);
            return localTimeIsAfterStream(LocalTime.parse(checkpoint.value()), zone);
        });
    }

    /**
     * Logical inverse of {@link #localTimeIsAfter(TextValue)}.
     *
     * @param checkpoint a local time of day, e.g. {@code "17:00"}
     * @return a stream of {@link BooleanValue}, or an error value on bad input
     */
    @EnvironmentAttribute(docs = """
            ```<time.localTimeIsBefore(TEXT checkpoint)>``` is an environment attribute stream and takes no left-hand arguments.
            ```<time.localTimeIsBefore(checkpoint)>``` ```false```, if the current time of the day without date is after the
            ```checkpoint``` time (e.g., "17:00") in the clock's configured timezone and ```true``` otherwise.
            This is examined relative to the current day. I.e., the answer toggles at "00:00".
            This *attribute is not polling the clock* and should be used instead of doing manual comparisons against
            ```<time.now>```, which would poll the clock regularly.

            Example:
            ```sapl
            policy "time example"
            permit action == "work";
              <time.localTimeIsBefore(subject.endTimeOfShift)>;
            ```
            """)
    public Stream<Value> localTimeIsBefore(TextValue checkpoint) {
        return guarded(() -> Streams.map(localTimeIsAfterStream(LocalTime.parse(checkpoint.value()), defaultZone()),
                TimePolicyInformationPoint::negateBoolean));
    }

    /**
     * Like {@link #localTimeIsBefore(TextValue)} but evaluated in
     * {@code timezone}.
     *
     * @param checkpoint a local time of day, e.g. {@code "17:00"}
     * @param timezone an IANA zone identifier, e.g. {@code "America/New_York"}
     * @return a stream of {@link BooleanValue}, or an error value on bad input
     */
    @EnvironmentAttribute(docs = """
            ```<time.localTimeIsBefore(TEXT checkpoint, TEXT timezone)>``` is an environment attribute stream and takes no
            left-hand arguments.
            ```<time.localTimeIsBefore(checkpoint, timezone)>``` ```false```, if the current time of the day without date is
            after the ```checkpoint``` time (e.g., "17:00") in the given ```timezone``` (e.g., "Europe/Berlin") and
            ```true``` otherwise.

            Example:
            ```sapl
            policy "time example"
            permit action == "work";
              <time.localTimeIsBefore(subject.endTimeOfShift, "America/New_York")>;
            ```
            """)
    public Stream<Value> localTimeIsBefore(TextValue checkpoint, TextValue timezone) {
        return guarded(() -> {
            val zone = resolveZone(timezone);
            return Streams.map(localTimeIsAfterStream(LocalTime.parse(checkpoint.value()), zone),
                    TimePolicyInformationPoint::negateBoolean);
        });
    }

    /**
     * Stream that emits {@code true} while the current local time of
     * day is within {@code [startTime, endTime]} in the clock's
     * configured zone, transitioning at each boundary. When
     * {@code startTime > endTime} the interval is treated as wrapping
     * past midnight.
     *
     * @param startTime a local time of day, e.g. {@code "09:00"}
     * @param endTime a local time of day, e.g. {@code "17:00"}
     * @return a stream of {@link BooleanValue}, or an error value on bad input
     */
    @EnvironmentAttribute(docs = """
            ```<time.localTimeIsBetween(TEXT startTime, TEXT endTime)>``` is an environment attribute stream and takes no left-hand
            arguments.
            ```<time.localTimeIsBetween(startTime, endTime)>``` ```true```, if the current time in the clock's configured
            timezone is between the ```startTime``` and the ```endTime``` and ```false``` otherwise.
            If the time of the first parameter is after the time of the second parameter, the interval is considered to be the
            one between the two times, crossing the midnight border of the days.

            Example:
            ```sapl
            policy "time example"
            permit action == "work";
              <time.localTimeIsBetween(subject.shiftStarts, subject.shiftEnds)>;
            ```
            """)
    public Stream<Value> localTimeIsBetween(TextValue startTime, TextValue endTime) {
        return guarded(() -> {
            val localStart = LocalTime.parse(startTime.value());
            val localEnd   = LocalTime.parse(endTime.value());
            return localTimeIsBetweenStream(localStart, localEnd, defaultZone());
        });
    }

    /**
     * Like {@link #localTimeIsBetween(TextValue, TextValue)} but
     * evaluated in {@code timezone}.
     *
     * @param startTime a local time of day, e.g. {@code "09:00"}
     * @param endTime a local time of day, e.g. {@code "17:00"}
     * @param timezone an IANA zone identifier, e.g. {@code "Europe/Berlin"}
     * @return a stream of {@link BooleanValue}, or an error value on bad input
     */
    @EnvironmentAttribute(docs = """
            ```<time.localTimeIsBetween(TEXT startTime, TEXT endTime, TEXT timezone)>``` is an environment attribute stream
            and takes no left-hand arguments.
            ```<time.localTimeIsBetween(startTime, endTime, timezone)>``` ```true```, if the current time in the given
            ```timezone``` (e.g., "Europe/Berlin") is between the ```startTime``` and the ```endTime``` and ```false```
            otherwise. If the time of the first parameter is after the time of the second parameter, the interval is
            considered to be the one between the two times, crossing the midnight border of the days.

            Example:
            ```sapl
            policy "time example"
            permit action == "work";
              <time.localTimeIsBetween(subject.shiftStarts, subject.shiftEnds, "Europe/Berlin")>;
            ```
            """)
    public Stream<Value> localTimeIsBetween(TextValue startTime, TextValue endTime, TextValue timezone) {
        return guarded(() -> {
            val zone       = resolveZone(timezone);
            val localStart = LocalTime.parse(startTime.value());
            val localEnd   = LocalTime.parse(endTime.value());
            return localTimeIsBetweenStream(localStart, localEnd, zone);
        });
    }

    /**
     * Logical inverse of {@link #nowIsAfter(TextValue)}: emits
     * {@code true} until {@code time} is reached, then {@code false}.
     *
     * @param time an ISO-8601 UTC instant
     * @return a stream of {@link BooleanValue}, or an error value if {@code time}
     * is malformed
     */
    @EnvironmentAttribute(docs = """
            ```<time.nowIsBefore(TEXT checkpoint)>``` is an environment attribute stream and takes no left-hand arguments.
            ```<time.nowIsBefore(checkpoint)>``` ```true```, if the current date time is before the ```checkpoint```
            time (ISO 8601 String at UTC) and ```false```otherwise.
            The attribute immediately emits the comparison result between the current time and the ```checkpoint```.
            If the time at the beginning of the attribute stream was before the ```checkpoint``` and it returned ```true```,
            then immediately after the ```checkpoint``` time is reached it will emit ```false```.
            This *attribute is not polling the clock* and should be used instead of  ```time.before(<time.now>, checkpoint)```,
            which would poll the clock regularly.

            Example:
            ```sapl
            policy "time example"
            permit action == "work";
              <time.nowIsBefore(subject.employmentEnds)>;
            ```

            Alternatively, assume that the current time is ```"2021-11-08T13:00:00Z"``` then the expression
            ```<time.nowIsBefore("2021-11-08T14:30:00Z")>``` will immediately return ```true``` and after
            90 minutes it will emit ```false```.""")
    public Stream<Value> nowIsBefore(TextValue time) {
        return guarded(() -> Streams.map(nowIsAfterStream(textValueToInstant(time)),
                TimePolicyInformationPoint::negateBoolean));
    }

    /**
     * Stream that emits {@code true} while the current instant lies
     * inside {@code [startTime, endTime]}, transitioning at each
     * boundary.
     *
     * @param startTime an ISO-8601 UTC instant
     * @param endTime an ISO-8601 UTC instant
     * @return a stream of {@link BooleanValue}, or an error value on bad input
     */
    @EnvironmentAttribute(docs = """
            ```<time.nowIsBetween(TEXT startTime, TEXT endTime)>``` is an environment attribute stream and takes no left-hand
            arguments.
            ```<time.nowIsBetween(startTime, endTime)>``` ```true```, if the current date time is after the ```startTime``` and
            before the ```endTime``` (both ISO 8601 String at UTC) and ```false```otherwise.
            The attribute immediately emits the comparison result between the current time and the provided time interval.
            A new result will be emitted, if the current time crosses any of the interval boundaries.
            This *attribute is not polling the clock* and should be used instead of  manually comparing the interval
            to ```<time.now>```.

            Example:
            ```sapl
            policy "time example"
            permit action == "work";
              <time.nowIsBetween(subject.employmentStarts, subject.employmentEnds)>;
            ```
            """)
    public Stream<Value> nowIsBetween(TextValue startTime, TextValue endTime) {
        return guarded(() -> {
            val start = textValueToInstant(startTime);
            val end   = textValueToInstant(endTime);
            return nowIsBetweenStream(start, end);
        });
    }

    /**
     * Stream that emits {@code true} when today's weekday is contained
     * in {@code days} (in the clock's configured zone), transitioning
     * at each midnight crossing.
     *
     * @param days an array of day-of-week names (e.g., {@code "MONDAY"})
     * @return a stream of {@link BooleanValue}, or an error value on bad input
     */
    @EnvironmentAttribute(docs = """
            ```<time.weekdayIn(ARRAY days)>``` is an environment attribute stream and takes no left-hand arguments.
            ```<time.weekdayIn(days)>``` emits ```true``` if the current day of the week (in the clock's configured timezone)
            is contained in the ```days``` array and ```false``` otherwise. The array contains day names as strings
            (e.g., ```["MONDAY", "WEDNESDAY", "FRIDAY"]```). The attribute automatically re-evaluates at midnight boundaries.

            Example:
            ```sapl
            policy "weekday access"
            permit action == "work";
              <time.weekdayIn(["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"])>;
            ```
            """)
    public Stream<Value> weekdayIn(ArrayValue days) {
        return guarded(() -> dayBasedStream(parseDaySet(days), defaultZone()));
    }

    /**
     * Like {@link #weekdayIn(ArrayValue)} but evaluated in
     * {@code timezone}.
     *
     * @param days an array of day-of-week names
     * @param timezone an IANA zone identifier
     * @return a stream of {@link BooleanValue}, or an error value on bad input
     */
    @EnvironmentAttribute(docs = """
            ```<time.weekdayIn(ARRAY days, TEXT timezone)>``` is an environment attribute stream and takes no left-hand arguments.
            ```<time.weekdayIn(days, timezone)>``` emits ```true``` if the current day of the week in the given
            ```timezone``` (e.g., "Europe/Berlin") is contained in the ```days``` array and ```false``` otherwise.

            Example:
            ```sapl
            policy "weekday access"
            permit action == "work";
              <time.weekdayIn(["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"], "Europe/Berlin")>;
            ```
            """)
    public Stream<Value> weekdayIn(ArrayValue days, TextValue timezone) {
        return guarded(() -> {
            val zone   = resolveZone(timezone);
            val daySet = parseDaySet(days);
            return dayBasedStream(daySet, zone);
        });
    }

    /**
     * Stream that emits {@code true} when today's weekday lies within
     * {@code [startDay, endDay]} (in the clock's configured zone),
     * wrapping past Sunday when {@code startDay > endDay}.
     *
     * @param startDay a day-of-week name
     * @param endDay a day-of-week name
     * @return a stream of {@link BooleanValue}, or an error value on bad input
     */
    @EnvironmentAttribute(docs = """
            ```<time.dayOfWeekBetween(TEXT startDay, TEXT endDay)>``` is an environment attribute stream and takes no left-hand
            arguments.
            ```<time.dayOfWeekBetween(startDay, endDay)>``` emits ```true``` if the current day of the week (in the clock's
            configured timezone) is within the range from ```startDay``` to ```endDay``` (inclusive), and ```false```
            otherwise. The range wraps around: ```("FRIDAY", "MONDAY")``` means Friday through Monday.

            Example:
            ```sapl
            policy "weekend access"
            permit action == "relax";
              <time.dayOfWeekBetween("SATURDAY", "SUNDAY")>;
            ```
            """)
    public Stream<Value> dayOfWeekBetween(TextValue startDay, TextValue endDay) {
        return guarded(() -> dayBasedStream(buildDayRange(startDay, endDay), defaultZone()));
    }

    /**
     * Like {@link #dayOfWeekBetween(TextValue, TextValue)} but
     * evaluated in {@code timezone}.
     *
     * @param startDay a day-of-week name
     * @param endDay a day-of-week name
     * @param timezone an IANA zone identifier
     * @return a stream of {@link BooleanValue}, or an error value on bad input
     */
    @EnvironmentAttribute(docs = """
            ```<time.dayOfWeekBetween(TEXT startDay, TEXT endDay, TEXT timezone)>``` is an environment attribute stream and
            takes no left-hand arguments.
            ```<time.dayOfWeekBetween(startDay, endDay, timezone)>``` emits ```true``` if the current day of the week in the
            given ```timezone``` is within the range from ```startDay``` to ```endDay``` (inclusive), and ```false```
            otherwise. The range wraps around: ```("FRIDAY", "MONDAY")``` means Friday through Monday.

            Example:
            ```sapl
            policy "weekend access"
            permit action == "relax";
              <time.dayOfWeekBetween("SATURDAY", "SUNDAY", "America/New_York")>;
            ```
            """)
    public Stream<Value> dayOfWeekBetween(TextValue startDay, TextValue endDay, TextValue timezone) {
        return guarded(() -> {
            val zone   = resolveZone(timezone);
            val daySet = buildDayRange(startDay, endDay);
            return dayBasedStream(daySet, zone);
        });
    }

    /**
     * Stream that emits {@code true} when the current month is in
     * {@code months} (in the clock's configured zone), transitioning
     * at month boundaries.
     *
     * @param months an array of month names or 1-based numbers
     * @return a stream of {@link BooleanValue}, or an error value on bad input
     */
    @EnvironmentAttribute(docs = """
            ```<time.monthIn(ARRAY months)>``` is an environment attribute stream and takes no left-hand arguments.
            ```<time.monthIn(months)>``` emits ```true``` if the current month (in the clock's configured timezone) is
            contained in the ```months``` array and ```false``` otherwise. The array accepts month names
            (e.g., ```"JANUARY"```) or numbers (e.g., ```1``` for January). The attribute automatically re-evaluates
            at month boundaries.

            Example:
            ```sapl
            policy "summer access"
            permit action == "vacation";
              <time.monthIn(["JUNE", "JULY", "AUGUST"])>;
            ```
            """)
    public Stream<Value> monthIn(ArrayValue months) {
        return guarded(() -> monthBasedStream(parseMonthSet(months), defaultZone()));
    }

    /**
     * Like {@link #monthIn(ArrayValue)} but evaluated in
     * {@code timezone}.
     *
     * @param months an array of month names or 1-based numbers
     * @param timezone an IANA zone identifier
     * @return a stream of {@link BooleanValue}, or an error value on bad input
     */
    @EnvironmentAttribute(docs = """
            ```<time.monthIn(ARRAY months, TEXT timezone)>``` is an environment attribute stream and takes no left-hand arguments.
            ```<time.monthIn(months, timezone)>``` emits ```true``` if the current month in the given ```timezone```
            (e.g., "Europe/Berlin") is contained in the ```months``` array and ```false``` otherwise.

            Example:
            ```sapl
            policy "summer access"
            permit action == "vacation";
              <time.monthIn(["JUNE", "JULY", "AUGUST"], "Europe/Berlin")>;
            ```
            """)
    public Stream<Value> monthIn(ArrayValue months, TextValue timezone) {
        return guarded(() -> {
            val zone     = resolveZone(timezone);
            val monthSet = parseMonthSet(months);
            return monthBasedStream(monthSet, zone);
        });
    }

    /**
     * Stream that emits {@code true} when the current month lies
     * within {@code [startMonth, endMonth]} (1=January, 12=December),
     * wrapping past December when {@code startMonth > endMonth}.
     *
     * @param startMonth 1-based month number
     * @param endMonth 1-based month number
     * @return a stream of {@link BooleanValue}, or an error value on bad input
     */
    @EnvironmentAttribute(docs = """
            ```<time.monthBetween(INTEGER startMonth, INTEGER endMonth)>``` is an environment attribute stream and takes no
            left-hand arguments.
            ```<time.monthBetween(startMonth, endMonth)>``` emits ```true``` if the current month (in the clock's configured
            timezone) is within the range from ```startMonth``` to ```endMonth``` (inclusive, 1=January, 12=December), and
            ```false``` otherwise. The range wraps around: ```(11, 3)``` means November through March.

            Example:
            ```sapl
            policy "winter access"
            permit action == "heating";
              <time.monthBetween(11, 3)>;
            ```
            """)
    public Stream<Value> monthBetween(NumberValue startMonth, NumberValue endMonth) {
        return guarded(() -> monthBasedStream(buildMonthRange(startMonth, endMonth), defaultZone()));
    }

    /**
     * Like {@link #monthBetween(NumberValue, NumberValue)} but
     * evaluated in {@code timezone}.
     *
     * @param startMonth 1-based month number
     * @param endMonth 1-based month number
     * @param timezone an IANA zone identifier
     * @return a stream of {@link BooleanValue}, or an error value on bad input
     */
    @EnvironmentAttribute(docs = """
            ```<time.monthBetween(INTEGER startMonth, INTEGER endMonth, TEXT timezone)>``` is an environment attribute stream
            and takes no left-hand arguments.
            ```<time.monthBetween(startMonth, endMonth, timezone)>``` emits ```true``` if the current month in the given
            ```timezone``` is within the range from ```startMonth``` to ```endMonth``` (inclusive, 1=January, 12=December),
            and ```false``` otherwise. The range wraps around: ```(11, 3)``` means November through March.

            Example:
            ```sapl
            policy "winter access"
            permit action == "heating";
              <time.monthBetween(11, 3, "Europe/Berlin")>;
            ```
            """)
    public Stream<Value> monthBetween(NumberValue startMonth, NumberValue endMonth, TextValue timezone) {
        return guarded(() -> {
            val zone     = resolveZone(timezone);
            val monthSet = buildMonthRange(startMonth, endMonth);
            return monthBasedStream(monthSet, zone);
        });
    }

    /**
     * Stream that toggles between {@code true} for
     * {@code trueDurationMs} milliseconds and {@code false} for
     * {@code falseDurationMs} milliseconds, repeating indefinitely.
     *
     * @param trueDurationMs duration of the {@code true} phase, in milliseconds
     * @param falseDurationMs duration of the {@code false} phase, in milliseconds
     * @return a stream of {@link BooleanValue}, or an error value if a duration is
     * invalid
     */
    @EnvironmentAttribute(docs = """
            ```<time.toggle(INTEGER>0 trueDurationMs, INTEGER>0 falseDurationMs)>``` is an environment attribute
            stream and takes no left-hand arguments.
            ```<time.toggle(trueDurationMs, falseDurationMs)>``` emits a periodically toggling Boolean signal.
            Will be ```true``` immediately for the first duration ```trueDurationMs``` (in milliseconds)
            and then ```false``` for the second duration ```falseDurationMs``` (in milliseconds).
            This will repeat periodically.
            Note, that the cycle will completely reset if the durations are updated.
            The attribute will forget its state in this case.

            Example:
            ```sapl
            policy "time example"
            permit action == "read";
              <time.toggle(1000, 2000)>;
            ```

            This policy will toggle between ```PERMIT``` and ```NOT_APPLICABLE```, where
            ```PERMIT``` will be the result for one second and ```NOT_APPLICABLE```
            will be the result for two seconds.
            """)
    public Stream<Value> toggle(NumberValue trueDurationMs, NumberValue falseDurationMs) {
        return guarded(() -> {
            val trueDuration  = numberValueMsToNonZeroPositiveDuration(trueDurationMs);
            val falseDuration = numberValueMsToNonZeroPositiveDuration(falseDurationMs);
            return toggleStream(trueDuration, falseDuration);
        });
    }

    private static Stream<Value> guarded(Supplier<Stream<Value>> body) {
        try {
            return body.get();
        } catch (RuntimeException e) {
            return Streams.error(e.getMessage());
        }
    }

    private Stream<Value> nowIsAfterStream(Instant checkpoint) {
        val now = clock.instant();
        if (now.isAfter(checkpoint)) {
            return Streams.just(Value.TRUE);
        }
        return Streams.concat(Streams.just(Value.FALSE), Streams.scheduledAt(Value.TRUE, checkpoint, scheduler));
    }

    private Stream<Value> nowIsBetweenStream(Instant start, Instant end) {
        val now = clock.instant();
        if (now.isAfter(end)) {
            return Streams.just(Value.FALSE);
        }
        if (now.isAfter(start)) {
            return Streams.concat(Streams.just(Value.TRUE), Streams.scheduledAt(Value.FALSE, end, scheduler));
        }
        return Streams.concat(Streams.just(Value.FALSE), Streams.scheduledAt(Value.TRUE, start, scheduler),
                Streams.scheduledAt(Value.FALSE, end, scheduler));
    }

    private Stream<Value> localTimeIsAfterStream(LocalTime checkpoint, ZoneId zone) {
        if (checkpoint.equals(LocalTime.MIN)) {
            return Streams.just(Value.TRUE);
        }
        if (checkpoint.equals(LocalTime.MAX)) {
            return Streams.just(Value.FALSE);
        }

        val now = zonedNow(zone).toLocalTime();
        if (now.isAfter(checkpoint)) {
            val tillMidnight = boolAtZonedBoundary(Value.FALSE, LocalTime.MAX, zone);
            return Streams.concat(Streams.just(Value.TRUE), tillMidnight,
                    afterCheckpointFollowingDays(checkpoint, zone));
        }
        val tillCheckpoint = boolAtZonedBoundary(Value.TRUE, checkpoint, zone);
        val tillMidnight   = boolAtZonedBoundary(Value.FALSE, LocalTime.MAX, zone);
        return Streams.concat(Streams.just(Value.FALSE), tillCheckpoint, tillMidnight,
                afterCheckpointFollowingDays(checkpoint, zone));
    }

    private Stream<Value> afterCheckpointFollowingDays(LocalTime checkpoint, ZoneId zone) {
        return Streams.repeat(() -> {
            val startOfDay = boolAtZonedBoundary(Value.TRUE, checkpoint, zone);
            val endOfDay   = boolAtZonedBoundary(Value.FALSE, LocalTime.MAX, zone);
            return Streams.concat(startOfDay, endOfDay);
        });
    }

    private Stream<Value> boolAtZonedBoundary(Value value, LocalTime to, ZoneId zone) {
        val today         = LocalDate.now(clock);
        val zonedTo       = to.atDate(today).atZone(zone);
        val targetInstant = zonedTo.toInstant();
        return Streams.scheduledAt(value, targetInstant, scheduler);
    }

    private Stream<Value> localTimeIsBetweenStream(LocalTime t1, LocalTime t2, ZoneId zone) {
        if (t1.equals(t2)) {
            return Streams.just(Value.FALSE);
        }
        if (t1.equals(LocalTime.MIN) && t2.equals(LocalTime.MAX)) {
            return Streams.just(Value.TRUE);
        }
        if (t1.equals(LocalTime.MAX) && t2.equals(LocalTime.MIN)) {
            return Streams.just(Value.TRUE);
        }
        val intervalWrapsAroundMidnight = t1.isAfter(t2);
        if (intervalWrapsAroundMidnight) {
            return Streams.map(localTimeIsBetweenAscending(t2, t1, zone), TimePolicyInformationPoint::negateBoolean);
        }
        return localTimeIsBetweenAscending(t1, t2, zone);
    }

    private Stream<Value> localTimeIsBetweenAscending(LocalTime start, LocalTime end, ZoneId zone) {
        val now      = zonedNow(zone);
        val local    = now.toLocalTime();
        val today    = now.toLocalDate();
        val tomorrow = today.plusDays(1);

        Stream<Value> initialStates;
        if (local.isBefore(start)) {
            val startInstant = start.atDate(today).atZone(zone).toInstant();
            initialStates = Streams.concat(Streams.just(Value.FALSE),
                    Streams.scheduledAt(Value.TRUE, startInstant, scheduler));
        } else if (local.isAfter(end)) {
            val nextStartInstant = start.atDate(tomorrow).atZone(zone).toInstant();
            initialStates = Streams.concat(Streams.just(Value.FALSE),
                    Streams.scheduledAt(Value.TRUE, nextStartInstant, scheduler));
        } else {
            val endInstant       = end.atDate(today).atZone(zone).toInstant();
            val nextStartInstant = start.atDate(tomorrow).atZone(zone).toInstant();
            initialStates = Streams.concat(Streams.just(Value.TRUE),
                    Streams.scheduledAt(Value.FALSE, endInstant, scheduler),
                    Streams.scheduledAt(Value.TRUE, nextStartInstant, scheduler));
        }

        val repeated = Streams.repeat(() -> {
            val now2      = zonedNow(zone);
            val today2    = now2.toLocalDate();
            val zonedEnd  = end.atDate(today2).atZone(zone).toInstant();
            val nextStart = start.atDate(today2.plusDays(1)).atZone(zone).toInstant();
            return Streams.concat(Streams.scheduledAt(Value.FALSE, zonedEnd, scheduler),
                    Streams.scheduledAt(Value.TRUE, nextStart, scheduler));
        });

        return Streams.concat(initialStates, repeated);
    }

    private Stream<Value> dayBasedStream(EnumSet<DayOfWeek> daySet, ZoneId zone) {
        if (daySet.size() == 7) {
            return Streams.just(Value.TRUE);
        }
        return Streams.repeat(() -> {
            val now          = zonedNow(zone);
            val currentDay   = now.getDayOfWeek();
            val isIn         = daySet.contains(currentDay);
            val daysToNext   = daysUntilStateChange(currentDay, daySet, isIn);
            val nextBoundary = now.toLocalDate().plusDays(daysToNext).atStartOfDay(zone);
            val nextInstant  = nextBoundary.toInstant();
            val current      = Value.of(isIn);
            val next         = Value.of(!isIn);
            return Streams.concat(Streams.just(current), Streams.scheduledAt(next, nextInstant, scheduler));
        });
    }

    private Stream<Value> monthBasedStream(EnumSet<Month> monthSet, ZoneId zone) {
        if (monthSet.size() == 12) {
            return Streams.just(Value.TRUE);
        }
        return Streams.repeat(() -> {
            val now          = zonedNow(zone);
            val currentMonth = now.getMonth();
            val isIn         = monthSet.contains(currentMonth);
            val monthsToNext = monthsUntilStateChange(currentMonth, monthSet, isIn);
            val nextBoundary = now.toLocalDate().withDayOfMonth(1).plusMonths(monthsToNext).atStartOfDay(zone);
            val nextInstant  = nextBoundary.toInstant();
            val current      = Value.of(isIn);
            val next         = Value.of(!isIn);
            return Streams.concat(Streams.just(current), Streams.scheduledAt(next, nextInstant, scheduler));
        });
    }

    private Stream<Value> toggleStream(Duration trueDuration, Duration falseDuration) {
        val firstFalseAt = clock.instant().plus(trueDuration);
        return Streams.concat(Streams.just(Value.TRUE), Streams.scheduledAt(Value.FALSE, firstFalseAt, scheduler),
                toggleTail(firstFalseAt, trueDuration, falseDuration));
    }

    private Stream<Value> toggleTail(Instant lastBoundary, Duration trueDuration, Duration falseDuration) {
        return Streams.repeat(new Supplier<>() {
            private Instant boundary = lastBoundary;

            @Override
            public Stream<Value> get() {
                val nextTrue  = boundary.plus(falseDuration);
                val nextFalse = nextTrue.plus(trueDuration);
                boundary = nextFalse;
                return Streams.concat(Streams.scheduledAt(Value.TRUE, nextTrue, scheduler),
                        Streams.scheduledAt(Value.FALSE, nextFalse, scheduler));
            }
        });
    }

    private Supplier<Value> instantSupplier() {
        return () -> {
            val instant = clock.instant();
            if (instant == null) {
                throw new IllegalStateException(ERROR_CLOCK_RETURNED_NULL);
            }
            return Value.of(ISO_FORMATTER.format(instant));
        };
    }

    private Duration numberValueMsToNonZeroPositiveDuration(NumberValue value) {
        val duration = Duration.ofMillis(value.value().longValue());
        if (duration.isZero()) {
            throw new IllegalArgumentException(ERROR_INTERVAL_ZERO);
        }
        if (duration.isNegative()) {
            throw new IllegalArgumentException(ERROR_INTERVAL_NEGATIVE);
        }
        return duration;
    }

    private static Value negateBoolean(Value v) {
        if (v instanceof BooleanValue(var b)) {
            return Value.of(!b);
        }
        return v;
    }

    private static Instant textValueToInstant(TextValue value) {
        return Instant.parse(value.value());
    }

    private ZoneId defaultZone() {
        return clock.getZone();
    }

    private ZoneId resolveZone(TextValue timezone) {
        try {
            return ZoneId.of(timezone.value());
        } catch (DateTimeException e) {
            throw new IllegalArgumentException(String.format(ERROR_TIMEZONE_INVALID, timezone.value()), e);
        }
    }

    private ZonedDateTime zonedNow(ZoneId zone) {
        return clock.instant().atZone(zone);
    }

    private static EnumSet<DayOfWeek> parseDaySet(ArrayValue days) {
        if (days.isEmpty()) {
            throw new IllegalArgumentException(ERROR_DAYS_ARRAY_EMPTY);
        }
        val daySet = EnumSet.noneOf(DayOfWeek.class);
        for (val day : days) {
            if (!(day instanceof TextValue(var name))) {
                throw new IllegalArgumentException(String.format(ERROR_DAY_NAME_INVALID, day));
            }
            try {
                daySet.add(DayOfWeek.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(String.format(ERROR_DAY_NAME_INVALID, name), e);
            }
        }
        return daySet;
    }

    private static EnumSet<DayOfWeek> buildDayRange(TextValue startDay, TextValue endDay) {
        DayOfWeek start;
        try {
            start = DayOfWeek.valueOf(startDay.value().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format(ERROR_START_DAY_INVALID, startDay.value()), e);
        }
        DayOfWeek end;
        try {
            end = DayOfWeek.valueOf(endDay.value().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format(ERROR_END_DAY_INVALID, endDay.value()), e);
        }
        val daySet  = EnumSet.noneOf(DayOfWeek.class);
        var current = start;
        while (true) {
            daySet.add(current);
            if (current == end) {
                break;
            }
            current = current.plus(1);
        }
        return daySet;
    }

    private static int daysUntilStateChange(DayOfWeek current, EnumSet<DayOfWeek> daySet, boolean currentlyIn) {
        for (int i = 1; i <= 7; i++) {
            val candidate = current.plus(i);
            if (daySet.contains(candidate) != currentlyIn) {
                return i;
            }
        }
        return 7;
    }

    private static EnumSet<Month> parseMonthSet(ArrayValue months) {
        if (months.isEmpty()) {
            throw new IllegalArgumentException(ERROR_MONTHS_ARRAY_EMPTY);
        }
        val monthSet = EnumSet.noneOf(Month.class);
        for (val month : months) {
            switch (month) {
            case TextValue(var name)     -> {
                try {
                    monthSet.add(Month.valueOf(name.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(String.format(ERROR_MONTH_NAME_INVALID, name), e);
                }
            }
            case NumberValue(var number) -> {
                val intVal = number.intValue();
                if (intVal < 1 || intVal > 12) {
                    throw new IllegalArgumentException(String.format(ERROR_MONTH_NAME_INVALID, intVal));
                }
                monthSet.add(Month.of(intVal));
            }
            default                      ->
                throw new IllegalArgumentException(String.format(ERROR_MONTH_NAME_INVALID, month));
            }
        }
        return monthSet;
    }

    private static EnumSet<Month> buildMonthRange(NumberValue startMonth, NumberValue endMonth) {
        val startVal = startMonth.value().intValue();
        if (startVal < 1 || startVal > 12) {
            throw new IllegalArgumentException(String.format(ERROR_START_MONTH_INVALID, startVal));
        }
        val endVal = endMonth.value().intValue();
        if (endVal < 1 || endVal > 12) {
            throw new IllegalArgumentException(String.format(ERROR_END_MONTH_INVALID, endVal));
        }
        val monthSet = EnumSet.noneOf(Month.class);
        var current  = Month.of(startVal);
        val target   = Month.of(endVal);
        while (true) {
            monthSet.add(current);
            if (current == target) {
                break;
            }
            current = current.plus(1);
        }
        return monthSet;
    }

    private static int monthsUntilStateChange(Month current, EnumSet<Month> monthSet, boolean currentlyIn) {
        for (int i = 1; i <= 12; i++) {
            val candidate = current.plus(i);
            if (monthSet.contains(candidate) != currentlyIn) {
                return i;
            }
        }
        return 12;
    }
}
