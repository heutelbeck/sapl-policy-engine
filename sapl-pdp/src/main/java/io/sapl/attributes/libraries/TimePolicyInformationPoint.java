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
package io.sapl.attributes.libraries;

import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.function.Supplier;

import static java.time.temporal.ChronoUnit.MILLIS;

@RequiredArgsConstructor
@PolicyInformationPoint(name = TimePolicyInformationPoint.NAME, description = TimePolicyInformationPoint.DESCRIPTION)
public class TimePolicyInformationPoint {

    public static final String NAME        = "time";
    public static final String DESCRIPTION = """
            Policy Information Point and attributes for retrieving current date and time information and
            basic temporal logic.""";

    private static final NumberValue       DEFAULT_UPDATE_INTERVAL_IN_MS = Value.of(1000L);
    private static final DateTimeFormatter ISO_FORMATTER                 = DateTimeFormatter.ISO_DATE_TIME
            .withZone(ZoneId.from(ZoneOffset.UTC));

    private final Clock clock;

    @EnvironmentAttribute(docs = """
            ```<now>``` is an environment attribute stream and takes no left-hand arguments.
            ```<now>```emits the current date and time as an ISO 8601 String at UTC.
            The first time is emitted instantly.
            After that the time is emitted once every second.

            Example:
            ```sapl
            policy "time example"
            permit
            where
               time.dayOfWeek(<now>) == "MONDAY";
            ```
            """)
    public Flux<Value> now() {
        return now(DEFAULT_UPDATE_INTERVAL_IN_MS);
    }

    @EnvironmentAttribute(docs = """
            ```<now(INTEGER>0 updateIntervalInMillis>``` is an environment attribute stream and takes no left-hand arguments.
            ```<now(updateIntervalInMillis>``` emits the current date and time as an ISO 8601 String at UTC.
            The first time is emitted instantly.
            After that the time is emitted once every ```updateIntervalInMillis`` milliseconds.

            Example:
            ```sapl
            policy "time example"
            permit
            where
               time.dayOfWeek(<now(time.durationOfMinutes(5)>) == "MONDAY";
            ```
            """)
    public Flux<Value> now(NumberValue updateIntervalInMillis) {
        try {
            final var interval = numberValueMsToNonZeroPositiveDuration(updateIntervalInMillis);
            return instantNow(interval).map(ISO_FORMATTER::format).map(Value::of);
        } catch (IllegalArgumentException e) {
            return Flux.just(Value.error(e.getMessage()));
        }
    }

    private Duration numberValueMsToNonZeroPositiveDuration(NumberValue value) {
        final var duration = Duration.ofMillis(value.value().longValue());
        if (duration.isZero()) {
            throw new IllegalArgumentException("Time update interval must not be zero.");
        }
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Time update interval must not be negative.");
        }
        return duration;
    }

    private Flux<Instant> instantNow(Duration pollInterval) {
        return poll(pollInterval, clock::instant);
    }

    private <T> Flux<T> poll(Duration pollInterval, Supplier<T> supplier) {
        final var first     = Flux.just(supplier.get());
        final var following = Flux.just(0).repeat().delayElements(pollInterval).map(tick -> supplier.get());
        return Flux.concat(first, following);
    }

    @EnvironmentAttribute(docs = """
            ```<systemTimeZone>``` is an environment attribute stream and takes no left-hand arguments.
            ```<systemTimeZone>``` emits the PDP's system time zone code.
            The zone is initially emitted instantly. After that the attribute verifies if the time zone changed every five
            minutes and emits an update, if the time zone changed.

            Example: The expression ```<systemTimeZone>``` will emit ```"US/Pacific"``` if the PDP's host default
            time zone is set this way and will not emit anything if no changes are made.
            """)
    public Flux<Value> systemTimeZone() {
        return poll(Duration.ofMinutes(5L), () -> (Value) Value.of(ZoneId.systemDefault().toString())).distinct();
    }

    @EnvironmentAttribute(docs = """
            ```<nowIsAfter(TEXT checkpoint)>``` is an environment attribute stream and takes no left-hand arguments.
            ```<nowIsAfter(checkpoint)>``` ```true```, if the current date time is after the ```checkpoint```
            time (ISO 8601 String at UTC) and ```false```otherwise.
            The attribute immediately emits the comparison result between the current time and the ```checkpoint```.
            If the time at the beginning of the attribute stream was before the ```checkpoint``` and it returned ```false```,
            then immediately after the ```checkpoint``` time is reached it will emit ```true```.
            This *attribute is not polling the clock* and should be used instead of  ```time.after(<time.now>, checkpoint)```,
            which would poll the clock regularly.

            Example:
            ```sapl
            policy "time example"
            permit action == "work"
            where
              <time.nowIsAfter(subject.employmentStart)>;
            ```

            Alternatively, assume that the current time is ```"2021-11-08T13:00:00Z"``` then the expression
            ```<time.nowIsAfter("2021-11-08T14:30:00Z")>``` will immediately return ```false``` and after
            90 minutes it will emit ```true```.""")
    public Flux<Value> nowIsAfter(TextValue checkpoint) {
        return nowIsAfter(textValueToInstant(checkpoint)).map(Value::of);
    }

    @EnvironmentAttribute(docs = """
            ```<localTimeIsAfter(TEXT checkpoint)>``` is an environment attribute stream and takes no left-hand arguments.
            ```<localTimeIsAfter(checkpoint)>``` ```true```, if the current time of the day without date is after the
            ```checkpoint``` time (e.g., "17:00") at UTC and ```false```otherwise. This is examined relative to the current
            day. I.e., the answer toggles at "00:00".
            The attribute immediately emits the comparison result between the current time and the ```checkpoint```.
            If the time at the beginning of the attribute stream was before the ```checkpoint``` and it returned ```false```,
            then immediately after the ```checkpoint``` time is reached it will emit ```true```.
            This *attribute is not polling the clock* and should be used instead of doing manual comparisons against
            ```<time.now>```, which would poll the clock regularly.

            Example:
            ```sapl
            policy "time example"
            permit action == "work"
            where
              <time.nowIsAfter(subject.startTimeOfShift)>;
            ```

            Alternatively, assume that the current time is ```"2021-11-08T13:00:00Z"``` then the expression
            ```<time.localTimeIsAfter("14:00")>``` will immediately return ```false``` and after
            one hour it will emit ```true```.""")
    public Flux<Value> localTimeIsAfter(TextValue checkpoint) {
        return localTimeIsAfter(LocalTime.parse(checkpoint.value())).map(Value::of);
    }

    private Flux<Boolean> localTimeIsAfter(LocalTime checkpoint) {
        return localTimeIsAfter(localTimeUtc(), checkpoint);
    }

    private LocalTime localTimeUtc() {
        return LocalTime.from(clock.instant().atZone(ZoneId.of("UTC")));
    }

    private Flux<Boolean> localTimeIsAfter(LocalTime now, LocalTime checkpoint) {

        if (checkpoint.equals(LocalTime.MIN))
            return Flux.just(Boolean.TRUE);

        if (checkpoint.equals(LocalTime.MAX))
            return Flux.just(Boolean.FALSE);

        if (now.isAfter(checkpoint)) {
            final var initial      = Flux.just(Boolean.TRUE);
            final var tillMidnight = boolAfterTimeDifference(false, now, LocalTime.MAX);
            final var initialDay   = Flux.concat(initial, tillMidnight);
            return Flux.concat(initialDay, afterCheckpointEventsFollowingDays(checkpoint));
        }

        final var initial        = Flux.just(Boolean.FALSE);
        final var tillCheckpoint = boolAfterTimeDifference(true, now, checkpoint);
        final var tillMidnight   = boolAfterTimeDifference(false, checkpoint, LocalTime.MAX);
        final var initialDay     = Flux.concat(initial, tillCheckpoint, tillMidnight);
        return Flux.concat(initialDay, afterCheckpointEventsFollowingDays(checkpoint));

    }

    private Flux<Boolean> afterCheckpointEventsFollowingDays(LocalTime checkpoint) {
        final var startOfDay = boolAfterTimeDifference(true, LocalTime.MIN, checkpoint);
        final var endOfDay   = boolAfterTimeDifference(false, checkpoint, LocalTime.MAX);
        return Flux.concat(startOfDay, endOfDay).repeat();
    }

    @EnvironmentAttribute(docs = """
            ```<localTimeIsBetween(TEXT startTime, TEXT endTime)>``` is an environment attribute stream and takes no left-hand
            arguments.
            ```<localTimeIsBetween(startTime, endTime)>``` ```true```, if the current time at UTC between the ```startTime```
            and the ```endTime``` (both ISO 8601 String at UTC) and ```false```otherwise.
            The attribute immediately emits the comparison result between the current time and the provided time interval.
            A new result will be emitted, if the current time crosses any of the interval boundaries.
            This *attribute is not polling the clock* and should be used instead of  manually comparing the interval
            to ```<time.now>```.
            If the time of the first parameter is after the time of the second parameter, the interval ist considered to be the
            one between the to times, crossing the midnight border of the days.

            Example:
            ```sapl
            policy "time example"
            permit action == "work"
            where
              <time.localTimeIsBetween(subject.shiftStarts, subject.shiftEnds)>;
            ```
            """)
    public Flux<Value> localTimeIsBetween(TextValue startTime, TextValue endTime) {
        final var localStartTime = LocalTime.parse(startTime.value());
        final var localEndTime   = LocalTime.parse(endTime.value());
        return nowIsBetween(localStartTime, localEndTime).map(Value::of);
    }

    private Flux<Boolean> nowIsBetween(LocalTime t1, LocalTime t2) {

        if (t1.equals(t2))
            return Flux.just(Boolean.FALSE);

        if (t1.equals(LocalTime.MIN) && t2.equals(LocalTime.MAX))
            return Flux.just(Boolean.TRUE);

        if (t1.equals(LocalTime.MAX) && t2.equals(LocalTime.MIN))
            return Flux.just(Boolean.TRUE);

        final var intervalWrapsAroundMidnight = t1.isAfter(t2);

        if (intervalWrapsAroundMidnight)
            return nowIsBetweenAscendingTimes(t2, t1).map(this::negate);

        return nowIsBetweenAscendingTimes(t1, t2);
    }

    private boolean negate(boolean val) {
        return !val;
    }

    private Flux<Boolean> nowIsBetweenAscendingTimes(LocalTime start, LocalTime end) {
        final var now = localTimeUtc();

        Flux<Boolean> initialStates;
        if (now.isBefore(start)) {
            final var initial   = Flux.just(Boolean.FALSE);
            final var tillStart = boolAfterTimeDifference(true, now, start);
            initialStates = Flux.concat(initial, tillStart);
        } else if (now.isAfter(end)) {
            final var initial                = Flux.just(Boolean.FALSE);
            final var timeTillIntervalStarts = Duration
                    .ofMillis(MILLIS.between(now, LocalTime.MAX) + MILLIS.between(LocalTime.MIN, start));
            final var tillStart              = Flux.just(Boolean.TRUE).delayElements(timeTillIntervalStarts);
            initialStates = Flux.concat(initial, tillStart);
        } else {
            final var initial                = Flux.just(Boolean.TRUE);
            final var tillIntervalEnd        = boolAfterTimeDifference(false, now, end);
            final var timeTillIntervalStarts = Duration
                    .ofMillis(MILLIS.between(end, LocalTime.MAX) + MILLIS.between(LocalTime.MIN, start));
            final var tillStart              = Flux.just(Boolean.TRUE).delayElements(timeTillIntervalStarts);
            initialStates = Flux.concat(initial, tillIntervalEnd, tillStart);
        }

        final var tillEnd          = boolAfterTimeDifference(false, start, end);
        final var tillStartNextDay = boolAfterTimeDifference(true, start, end);
        final var repeated         = Flux.concat(tillEnd, tillStartNextDay).repeat();

        return Flux.concat(initialStates, repeated);
    }

    private Flux<Boolean> boolAfterTimeDifference(boolean val, Temporal start, Temporal end) {
        return Flux.just(val).delayElements(Duration.ofMillis(MILLIS.between(start, end)));
    }

    @EnvironmentAttribute(docs = """
            ```<localTimeIsBefore(TEXT checkpoint)>``` is an environment attribute stream and takes no left-hand arguments.
            ```<localTimeIsBefore(checkpoint)>``` ```false```, if the current time of the day without date is after the
            ```checkpoint``` time (e.g., "17:00") at UTC and ```true```otherwise. This is examined relative to the current
            day. I.e., the answer toggles at "00:00".
            The attribute immediately emits the comparison result between the current time and the ```checkpoint```.
            If the time at the beginning of the attribute stream was before the ```checkpoint``` and it returned ```true```,
            then immediately after the ```checkpoint``` time is reached it will emit ```false```.
            This *attribute is not polling the clock* and should be used instead of doing manual comparisons against
            ```<time.now>```, which would poll the clock regularly.

            Example:
            ```sapl
            policy "time example"
            permit action == "work"
            where
              <time.localTimeIsBefore(subject.endTimeOfShift)>;
            ```

            Alternatively, assume that the current time is ```"2021-11-08T13:00:00Z"``` then the expression
            ```<time.localTimeIsBefore("14:00")>``` will immediately return ```true``` and after
            one hour it will emit ```false```.""")
    public Flux<Value> localTimeIsBefore(TextValue checkpoint) {
        return localTimeIsAfter(LocalTime.parse(checkpoint.value())).map(this::negate).map(Value::of);
    }

    @EnvironmentAttribute(docs = """
            ```<nowIsBefore(TEXT checkpoint)>``` is an environment attribute stream and takes no left-hand arguments.
            ```<nowIsBefore(checkpoint)>``` ```true```, if the current date time is before the ```checkpoint```
            time (ISO 8601 String at UTC) and ```false```otherwise.
            The attribute immediately emits the comparison result between the current time and the ```checkpoint```.
            If the time at the beginning of the attribute stream was before the ```checkpoint``` and it returned ```true```,
            then immediately after the ```checkpoint``` time is reached it will emit ```false```.
            This *attribute is not polling the clock* and should be used instead of  ```time.before(<time.now>, checkpoint)```,
            which would poll the clock regularly.

            Example:
            ```sapl
            policy "time example"
            permit action == "work"
            where
              <time.nowIsBefore(subject.employmentEnds)>;
            ```

            Alternatively, assume that the current time is ```"2021-11-08T13:00:00Z"``` then the expression
            ```<time.nowIsBefore("2021-11-08T14:30:00Z")>``` will immediately return ```true``` and after
            90 minutes it will emit ```false```.""")
    public Flux<Value> nowIsBefore(TextValue time) {
        return nowIsBefore(textValueToInstant(time)).map(Value::of);
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
        final var initial  = Flux.just(Boolean.FALSE);
        final var eventual = Flux.just(Boolean.TRUE).delayElements(Duration.between(instantB, instantA));
        return Flux.concat(initial, eventual);
    }

    @EnvironmentAttribute(docs = """
            ```<nowIsBetween(TEXT startTime, TEXT endTime)>``` is an environment attribute stream and takes no left-hand
            arguments.
            ```<nowIsBetween(startTime, endTime)>``` ```true```, if the current date time is after the ```startTime``` and
            before the ```endTime``` (both ISO 8601 String at UTC) and ```false```otherwise.
            The attribute immediately emits the comparison result between the current time and the provided time interval.
            A new result will be emitted, if the current time crosses any of the interval boundaries.
            This *attribute is not polling the clock* and should be used instead of  manually comparing the interval
            to ```<time.now>```.

            Example:
            ```sapl
            policy "time example"
            permit action == "work"
            where
              <time.nowIsBetween(subject.employmentStarts, subject.employmentEnds)>;
            ```
            """)
    public Flux<Value> nowIsBetween(TextValue startTime, TextValue endTime) {
        final var start = textValueToInstant(startTime);
        final var end   = textValueToInstant(endTime);
        return nowIsBetween(start, end).map(Value::of);
    }

    public Flux<Boolean> nowIsBetween(Instant start, Instant end) {
        final var now = clock.instant();
        if (now.isAfter(end))
            return Flux.just(Boolean.FALSE);

        if (now.isAfter(start)) {
            final var initial  = Flux.just(Boolean.TRUE);
            final var eventual = Flux.just(Boolean.FALSE).delayElements(Duration.between(now, end));
            return Flux.concat(initial, eventual);
        }

        final var initial         = Flux.just(Boolean.FALSE);
        final var duringIsBetween = Flux.just(Boolean.TRUE).delayElements(Duration.between(now, start));
        final var eventual        = Flux.just(Boolean.FALSE).delayElements(Duration.between(start, end));

        return Flux.concat(initial, duringIsBetween, eventual);
    }

    @EnvironmentAttribute(docs = """
            ```<toggle(INTEGER>0 trueDurationMs, INTEGER>0 falseDurationMs)>``` is an environment attribute
            stream and takes no left-hand arguments.
            ```<toggle(trueDurationMs, falseDurationMs)>``` emits a periodically toggling Boolean signal.
            Will be ```true``` immediately for the first duration ```trueDurationMs``` (in milliseconds)
            and then ```false``` for the second duration ```falseDurationMs``` (in milliseconds).
            This will repeat periodically.
            Note, that the cycle will completely reset if the durations are updated.
            The attribute will forget its state in this case.

            Example:
            ```sapl
            policy "time example"
            permit action == "read"
            where
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

    private Flux<Boolean> toggle(Duration trueDurationMs, Duration falseDurationMs) {
        final var initial       = Flux.just(Boolean.TRUE);
        final var waitTillFalse = Flux.just(Boolean.FALSE).delayElements(trueDurationMs);
        final var waitTillTrue  = Flux.just(Boolean.TRUE).delayElements(falseDurationMs);
        final var repeatingTail = Flux.concat(waitTillFalse, waitTillTrue).repeat();
        return Flux.concat(initial, repeatingTail);
    }

}
