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

import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.RequiredArgsConstructor;
import lombok.val;
import reactor.core.publisher.Flux;

import java.time.Clock;
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

    private final Clock clock;

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
    public Flux<Value> now() {
        return now(DEFAULT_UPDATE_INTERVAL_IN_MS);
    }

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
    public Flux<Value> now(NumberValue updateIntervalInMillis) {
        try {
            val interval = numberValueMsToNonZeroPositiveDuration(updateIntervalInMillis);
            return instantNow(interval).map(ISO_FORMATTER::format).map(Value::of);
        } catch (IllegalArgumentException e) {
            return Flux.just(Value.error(e.getMessage()));
        }
    }

    @EnvironmentAttribute(docs = """
            ```<time.systemTimeZone>``` is an environment attribute stream and takes no left-hand arguments.
            ```<time.systemTimeZone>``` emits the PDP's system time zone code.
            The zone is initially emitted instantly. After that the attribute verifies if the time zone changed every five
            minutes and emits an update, if the time zone changed.

            Example: The expression ```<time.systemTimeZone>``` will emit ```"US/Pacific"``` if the PDP's host default
            time zone is set this way and will not emit anything if no changes are made.
            """)
    public Flux<Value> systemTimeZone() {
        return poll(Duration.ofMinutes(5L), () -> (Value) Value.of(ZoneId.systemDefault().toString())).distinct();
    }

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
    public Flux<Value> nowIsAfter(TextValue checkpoint) {
        return nowIsAfter(textValueToInstant(checkpoint)).map(Value::of);
    }

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
    public Flux<Value> localTimeIsAfter(TextValue checkpoint) {
        try {
            return localTimeIsAfter(LocalTime.parse(checkpoint.value()), defaultZone()).map(Value::of);
        } catch (Exception e) {
            return Flux.just(Value.error(e.getMessage()));
        }
    }

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
    public Flux<Value> localTimeIsAfter(TextValue checkpoint, TextValue timezone) {
        try {
            val zone = resolveZone(timezone);
            return localTimeIsAfter(LocalTime.parse(checkpoint.value()), zone).map(Value::of);
        } catch (Exception e) {
            return Flux.just(Value.error(e.getMessage()));
        }
    }

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
    public Flux<Value> localTimeIsBefore(TextValue checkpoint) {
        try {
            return localTimeIsAfter(LocalTime.parse(checkpoint.value()), defaultZone()).map(this::negate)
                    .map(Value::of);
        } catch (Exception e) {
            return Flux.just(Value.error(e.getMessage()));
        }
    }

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
    public Flux<Value> localTimeIsBefore(TextValue checkpoint, TextValue timezone) {
        try {
            val zone = resolveZone(timezone);
            return localTimeIsAfter(LocalTime.parse(checkpoint.value()), zone).map(this::negate).map(Value::of);
        } catch (Exception e) {
            return Flux.just(Value.error(e.getMessage()));
        }
    }

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
    public Flux<Value> localTimeIsBetween(TextValue startTime, TextValue endTime) {
        try {
            val localStartTime = LocalTime.parse(startTime.value());
            val localEndTime   = LocalTime.parse(endTime.value());
            return localTimeIsBetween(localStartTime, localEndTime, defaultZone()).map(Value::of);
        } catch (Exception e) {
            return Flux.just(Value.error(e.getMessage()));
        }
    }

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
    public Flux<Value> localTimeIsBetween(TextValue startTime, TextValue endTime, TextValue timezone) {
        try {
            val zone           = resolveZone(timezone);
            val localStartTime = LocalTime.parse(startTime.value());
            val localEndTime   = LocalTime.parse(endTime.value());
            return localTimeIsBetween(localStartTime, localEndTime, zone).map(Value::of);
        } catch (Exception e) {
            return Flux.just(Value.error(e.getMessage()));
        }
    }

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
    public Flux<Value> nowIsBefore(TextValue time) {
        return nowIsBefore(textValueToInstant(time)).map(Value::of);
    }

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
    public Flux<Value> nowIsBetween(TextValue startTime, TextValue endTime) {
        val start = textValueToInstant(startTime);
        val end   = textValueToInstant(endTime);
        return nowIsBetween(start, end).map(Value::of);
    }

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
    public Flux<Value> weekdayIn(ArrayValue days) {
        try {
            val daySet = parseDaySet(days);
            return dayBasedStream(daySet, defaultZone());
        } catch (Exception e) {
            return Flux.just(Value.error(e.getMessage()));
        }
    }

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
    public Flux<Value> weekdayIn(ArrayValue days, TextValue timezone) {
        try {
            val zone   = resolveZone(timezone);
            val daySet = parseDaySet(days);
            return dayBasedStream(daySet, zone);
        } catch (Exception e) {
            return Flux.just(Value.error(e.getMessage()));
        }
    }

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
    public Flux<Value> dayOfWeekBetween(TextValue startDay, TextValue endDay) {
        try {
            val daySet = buildDayRange(startDay, endDay);
            return dayBasedStream(daySet, defaultZone());
        } catch (Exception e) {
            return Flux.just(Value.error(e.getMessage()));
        }
    }

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
    public Flux<Value> dayOfWeekBetween(TextValue startDay, TextValue endDay, TextValue timezone) {
        try {
            val zone   = resolveZone(timezone);
            val daySet = buildDayRange(startDay, endDay);
            return dayBasedStream(daySet, zone);
        } catch (Exception e) {
            return Flux.just(Value.error(e.getMessage()));
        }
    }

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
    public Flux<Value> monthIn(ArrayValue months) {
        try {
            val monthSet = parseMonthSet(months);
            return monthBasedStream(monthSet, defaultZone());
        } catch (Exception e) {
            return Flux.just(Value.error(e.getMessage()));
        }
    }

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
    public Flux<Value> monthIn(ArrayValue months, TextValue timezone) {
        try {
            val zone     = resolveZone(timezone);
            val monthSet = parseMonthSet(months);
            return monthBasedStream(monthSet, zone);
        } catch (Exception e) {
            return Flux.just(Value.error(e.getMessage()));
        }
    }

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
    public Flux<Value> monthBetween(NumberValue startMonth, NumberValue endMonth) {
        try {
            val monthSet = buildMonthRange(startMonth, endMonth);
            return monthBasedStream(monthSet, defaultZone());
        } catch (Exception e) {
            return Flux.just(Value.error(e.getMessage()));
        }
    }

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
    public Flux<Value> monthBetween(NumberValue startMonth, NumberValue endMonth, TextValue timezone) {
        try {
            val zone     = resolveZone(timezone);
            val monthSet = buildMonthRange(startMonth, endMonth);
            return monthBasedStream(monthSet, zone);
        } catch (Exception e) {
            return Flux.just(Value.error(e.getMessage()));
        }
    }

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
    public Flux<Value> toggle(NumberValue trueDurationMs, NumberValue falseDurationMs) {
        return toggle(numberValueMsToNonZeroPositiveDuration(trueDurationMs),
                numberValueMsToNonZeroPositiveDuration(falseDurationMs)).map(Value::of);
    }

    public Flux<Boolean> nowIsBetween(Instant start, Instant end) {
        val now = clock.instant();
        if (now.isAfter(end))
            return Flux.just(Boolean.FALSE);

        if (now.isAfter(start)) {
            val initial  = Flux.just(Boolean.TRUE);
            val eventual = Flux.just(Boolean.FALSE).delayElements(Duration.between(now, end));
            return Flux.concat(initial, eventual);
        }

        val initial         = Flux.just(Boolean.FALSE);
        val duringIsBetween = Flux.just(Boolean.TRUE).delayElements(Duration.between(now, start));
        val eventual        = Flux.just(Boolean.FALSE).delayElements(Duration.between(start, end));

        return Flux.concat(initial, duringIsBetween, eventual);
    }

    private ZoneId defaultZone() {
        return clock.getZone();
    }

    private ZoneId resolveZone(TextValue timezone) {
        try {
            return ZoneId.of(timezone.value());
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format(ERROR_TIMEZONE_INVALID, timezone.value()), e);
        }
    }

    private ZonedDateTime zonedNow(ZoneId zone) {
        return clock.instant().atZone(zone);
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

    private Flux<Instant> instantNow(Duration pollInterval) {
        return poll(pollInterval, clock::instant);
    }

    private <T> Flux<T> poll(Duration pollInterval, Supplier<T> supplier) {
        val first     = Flux.just(supplier.get());
        val following = Flux.just(0).repeat().delayElements(pollInterval).map(tick -> supplier.get());
        return Flux.concat(first, following);
    }

    private Instant textValueToInstant(TextValue value) {
        return Instant.parse(value.value());
    }

    private Flux<Boolean> nowIsAfter(Instant anInstant) {
        return isAfter(anInstant, clock.instant());
    }

    private Flux<Boolean> nowIsBefore(Instant anInstant) {
        return isAfter(anInstant, clock.instant()).map(this::negate);
    }

    private Flux<Boolean> isAfter(Instant instantA, Instant instantB) {
        if (instantB.isAfter(instantA))
            return Flux.just(Boolean.TRUE);
        val initial  = Flux.just(Boolean.FALSE);
        val eventual = Flux.just(Boolean.TRUE).delayElements(Duration.between(instantB, instantA));
        return Flux.concat(initial, eventual);
    }

    private boolean negate(boolean val) {
        return !val;
    }

    private Flux<Boolean> localTimeIsAfter(LocalTime checkpoint, ZoneId zone) {
        val now = zonedNow(zone).toLocalTime();

        if (checkpoint.equals(LocalTime.MIN))
            return Flux.just(Boolean.TRUE);

        if (checkpoint.equals(LocalTime.MAX))
            return Flux.just(Boolean.FALSE);

        if (now.isAfter(checkpoint)) {
            val initial      = Flux.just(Boolean.TRUE);
            val tillMidnight = boolAfterZonedDuration(false, now, LocalTime.MAX, zone);
            val initialDay   = Flux.concat(initial, tillMidnight);
            return Flux.concat(initialDay, afterCheckpointEventsFollowingDays(checkpoint, zone));
        }

        val initial        = Flux.just(Boolean.FALSE);
        val tillCheckpoint = boolAfterZonedDuration(true, now, checkpoint, zone);
        val tillMidnight   = boolAfterZonedDuration(false, checkpoint, LocalTime.MAX, zone);
        val initialDay     = Flux.concat(initial, tillCheckpoint, tillMidnight);
        return Flux.concat(initialDay, afterCheckpointEventsFollowingDays(checkpoint, zone));
    }

    private Flux<Boolean> afterCheckpointEventsFollowingDays(LocalTime checkpoint, ZoneId zone) {
        return Flux.defer(() -> {
            val startOfDay = boolAfterZonedDuration(true, LocalTime.MIN, checkpoint, zone);
            val endOfDay   = boolAfterZonedDuration(false, checkpoint, LocalTime.MAX, zone);
            return Flux.concat(startOfDay, endOfDay);
        }).repeat();
    }

    private Flux<Boolean> boolAfterZonedDuration(boolean value, LocalTime from, LocalTime to, ZoneId zone) {
        return Flux.defer(() -> {
            val today     = LocalDate.now(clock);
            val zonedFrom = from.atDate(today).atZone(zone);
            val zonedTo   = to.atDate(today).atZone(zone);
            val duration  = Duration.between(zonedFrom, zonedTo);
            if (duration.isNegative() || duration.isZero()) {
                return Flux.just(value);
            }
            return Flux.just(value).delayElements(duration);
        });
    }

    private Flux<Boolean> localTimeIsBetween(LocalTime t1, LocalTime t2, ZoneId zone) {
        if (t1.equals(t2))
            return Flux.just(Boolean.FALSE);

        if (t1.equals(LocalTime.MIN) && t2.equals(LocalTime.MAX))
            return Flux.just(Boolean.TRUE);

        if (t1.equals(LocalTime.MAX) && t2.equals(LocalTime.MIN))
            return Flux.just(Boolean.TRUE);

        val intervalWrapsAroundMidnight = t1.isAfter(t2);

        if (intervalWrapsAroundMidnight)
            return localTimeIsBetweenAscending(t2, t1, zone).map(this::negate);

        return localTimeIsBetweenAscending(t1, t2, zone);
    }

    private Flux<Boolean> localTimeIsBetweenAscending(LocalTime start, LocalTime end, ZoneId zone) {
        val now   = zonedNow(zone);
        val local = now.toLocalTime();
        val today = now.toLocalDate();

        Flux<Boolean> initialStates;
        if (local.isBefore(start)) {
            val initial   = Flux.just(Boolean.FALSE);
            val tillStart = boolAfterZonedDuration(true, local, start, zone);
            initialStates = Flux.concat(initial, tillStart);
        } else if (local.isAfter(end)) {
            val initial        = Flux.just(Boolean.FALSE);
            val zonedNow       = local.atDate(today).atZone(zone);
            val zonedMidnight  = LocalTime.MAX.atDate(today).atZone(zone);
            val zonedNextStart = start.atDate(today.plusDays(1)).atZone(zone);
            val timeTillStart  = Duration.between(zonedNow, zonedMidnight)
                    .plus(Duration.between(zonedMidnight, zonedNextStart));
            val tillStart      = Flux.just(Boolean.TRUE).delayElements(timeTillStart);
            initialStates = Flux.concat(initial, tillStart);
        } else {
            val initial        = Flux.just(Boolean.TRUE);
            val tillEnd        = boolAfterZonedDuration(false, local, end, zone);
            val zonedEnd       = end.atDate(today).atZone(zone);
            val zonedMidnight  = LocalTime.MAX.atDate(today).atZone(zone);
            val zonedNextStart = start.atDate(today.plusDays(1)).atZone(zone);
            val timeTillStart  = Duration.between(zonedEnd, zonedMidnight)
                    .plus(Duration.between(zonedMidnight, zonedNextStart));
            val tillStart      = Flux.just(Boolean.TRUE).delayElements(timeTillStart);
            initialStates = Flux.concat(initial, tillEnd, tillStart);
        }

        val tillEnd          = boolAfterZonedDuration(false, start, end, zone);
        val zonedEnd         = end.atDate(today).atZone(zone);
        val zonedMidnight    = LocalTime.MAX.atDate(today).atZone(zone);
        val zonedNextStart   = start.atDate(today.plusDays(1)).atZone(zone);
        val timeTillNextDay  = Duration.between(zonedEnd, zonedMidnight)
                .plus(Duration.between(zonedMidnight, zonedNextStart));
        val tillStartNextDay = Flux.just(Boolean.TRUE).delayElements(timeTillNextDay);
        val repeated         = Flux.concat(tillEnd, tillStartNextDay).repeat();

        return Flux.concat(initialStates, repeated);
    }

    private EnumSet<DayOfWeek> parseDaySet(ArrayValue days) {
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

    private EnumSet<DayOfWeek> buildDayRange(TextValue startDay, TextValue endDay) {
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

    private Flux<Value> dayBasedStream(EnumSet<DayOfWeek> daySet, ZoneId zone) {
        if (daySet.size() == 7) {
            return Flux.just(Value.TRUE);
        }
        return Flux.defer(() -> {
            val   now          = zonedNow(zone);
            val   currentDay   = now.getDayOfWeek();
            val   isIn         = daySet.contains(currentDay);
            val   daysToNext   = daysUntilStateChange(currentDay, daySet, isIn);
            val   nextMidnight = now.toLocalDate().plusDays(daysToNext).atStartOfDay(zone);
            val   delay        = Duration.between(now, nextMidnight);
            Value current      = Value.of(isIn);
            Value next         = Value.of(!isIn);
            if (delay.isNegative() || delay.isZero()) {
                return Flux.just(current);
            }
            return Flux.concat(Flux.just(current), Flux.just(next).delayElements(delay));
        }).repeat();
    }

    private int daysUntilStateChange(DayOfWeek current, EnumSet<DayOfWeek> daySet, boolean currentlyIn) {
        for (int i = 1; i <= 7; i++) {
            val candidate = current.plus(i);
            if (daySet.contains(candidate) != currentlyIn) {
                return i;
            }
        }
        return 7;
    }

    private EnumSet<Month> parseMonthSet(ArrayValue months) {
        if (months.isEmpty()) {
            throw new IllegalArgumentException(ERROR_MONTHS_ARRAY_EMPTY);
        }
        val monthSet = EnumSet.noneOf(Month.class);
        for (val month : months) {
            if (month instanceof TextValue(var name)) {
                try {
                    monthSet.add(Month.valueOf(name.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(String.format(ERROR_MONTH_NAME_INVALID, name), e);
                }
            } else if (month instanceof NumberValue(var number)) {
                val intVal = number.intValue();
                if (intVal < 1 || intVal > 12) {
                    throw new IllegalArgumentException(String.format(ERROR_MONTH_NAME_INVALID, intVal));
                }
                monthSet.add(Month.of(intVal));
            } else {
                throw new IllegalArgumentException(String.format(ERROR_MONTH_NAME_INVALID, month));
            }
        }
        return monthSet;
    }

    private EnumSet<Month> buildMonthRange(NumberValue startMonth, NumberValue endMonth) {
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

    private Flux<Value> monthBasedStream(EnumSet<Month> monthSet, ZoneId zone) {
        if (monthSet.size() == 12) {
            return Flux.just(Value.TRUE);
        }
        return Flux.defer(() -> {
            val   now          = zonedNow(zone);
            val   currentMonth = now.getMonth();
            val   isIn         = monthSet.contains(currentMonth);
            val   monthsToNext = monthsUntilStateChange(currentMonth, monthSet, isIn);
            val   nextBoundary = now.toLocalDate().withDayOfMonth(1).plusMonths(monthsToNext).atStartOfDay(zone);
            val   delay        = Duration.between(now, nextBoundary);
            Value current      = Value.of(isIn);
            Value next         = Value.of(!isIn);
            if (delay.isNegative() || delay.isZero()) {
                return Flux.just(current);
            }
            return Flux.concat(Flux.just(current), Flux.just(next).delayElements(delay));
        }).repeat();
    }

    private int monthsUntilStateChange(Month current, EnumSet<Month> monthSet, boolean currentlyIn) {
        for (int i = 1; i <= 12; i++) {
            val candidate = current.plus(i);
            if (monthSet.contains(candidate) != currentlyIn) {
                return i;
            }
        }
        return 12;
    }

    private Flux<Boolean> toggle(Duration trueDurationMs, Duration falseDurationMs) {
        val initial       = Flux.just(Boolean.TRUE);
        val waitTillFalse = Flux.just(Boolean.FALSE).delayElements(trueDurationMs);
        val waitTillTrue  = Flux.just(Boolean.TRUE).delayElements(falseDurationMs);
        val repeatingTail = Flux.concat(waitTillFalse, waitTillTrue).repeat();
        return Flux.concat(initial, repeatingTail);
    }

}
