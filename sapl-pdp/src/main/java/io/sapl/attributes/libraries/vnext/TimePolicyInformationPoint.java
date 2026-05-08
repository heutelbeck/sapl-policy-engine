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
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.Stream;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.attributes.libraries.vnext.util.Streams;
import io.sapl.attributes.libraries.vnext.util.TimeScheduler;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

@RequiredArgsConstructor
@PolicyInformationPoint(name = TimePolicyInformationPoint.NAME, description = TimePolicyInformationPoint.DESCRIPTION)
public class TimePolicyInformationPoint {

    public static final String  NAME                      = "time";
    public static final String  DESCRIPTION               = """
            Policy Information Point and attributes for retrieving current date and time information and
            basic temporal logic.""";
    private static final String ERROR_CLOCK_RETURNED_NULL = "Clock returned null. The time PIP is misconfigured: a Clock bean must be supplied that always returns a valid Instant.";
    private static final String ERROR_INTERVAL_NEGATIVE   = "Time update interval must not be negative.";
    private static final String ERROR_INTERVAL_ZERO       = "Time update interval must not be zero.";

    private static final NumberValue       DEFAULT_UPDATE_INTERVAL_IN_MS = Value.of(1000L);
    private static final DateTimeFormatter ISO_FORMATTER                 = DateTimeFormatter.ISO_DATE_TIME
            .withZone(ZoneId.from(ZoneOffset.UTC));

    private final Clock         clock;
    private final TimeScheduler scheduler;

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
        try {
            val interval = numberValueMsToNonZeroPositiveDuration(updateIntervalInMillis);
            return Streams.scheduledPoll(interval, instantSupplier(), clock, scheduler);
        } catch (IllegalArgumentException e) {
            return Streams.error(e.getMessage());
        }
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
    public Stream<Value> nowIsAfter(TextValue checkpoint) {
        try {
            val checkpointInstant = Instant.parse(checkpoint.value());
            val now               = clock.instant();
            if (now.isAfter(checkpointInstant)) {
                return Streams.just(Value.TRUE);
            }
            return Streams.concat(Streams.just(Value.FALSE),
                    Streams.scheduledAt(Value.TRUE, checkpointInstant, scheduler));
        } catch (RuntimeException e) {
            return Streams.error(e.getMessage());
        }
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
}
