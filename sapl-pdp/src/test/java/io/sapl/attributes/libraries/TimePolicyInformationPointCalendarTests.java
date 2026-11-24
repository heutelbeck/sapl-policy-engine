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

import io.sapl.api.model.Poll;
import io.sapl.api.model.Value;
import io.sapl.api.stream.Cancellable;
import io.sapl.api.stream.Stream;
import io.sapl.api.stream.TimeScheduler;
import io.sapl.api.test.stream.MutableClock;
import io.sapl.api.test.stream.StreamAssertions;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(10)
@DisplayName("Calendar predicates follow the displayed time")
class TimePolicyInformationPointCalendarTests {

    @ParameterizedTest(name = "Apia skips Friday: {0} is {1}")
    @CsvSource({ "FRIDAY,false", "SATURDAY,true" })
    @DisplayName("skipping a calendar date does not emit membership of the missing day")
    void whenCalendarSkipsADateThenLiveAndFreshReadersAgree(String day, boolean expected) throws InterruptedException {
        val start     = Instant.parse("2011-12-30T09:59:00Z");
        val boundary  = start.plusSeconds(60);
        val clock     = new GatedClock(start);
        val scheduler = new QueuedScheduler();
        val pip       = new TimePolicyInformationPoint(clock, scheduler);
        val days      = Value.ofArray(Value.of(day));
        val zone      = Value.of("Pacific/Apia");
        try (val live = pip.weekdayIn(days, zone)) {
            StreamAssertions.assertThat(live).withinTimeout(Duration.ofSeconds(2)).awaitsNext(Value.FALSE);
            val timer = scheduler.next();
            assertThat(timer.when()).isEqualTo(boundary);
            clock.current = boundary;
            clock.blockNextRead.set(true);
            timer.task().run();
            assertThat(clock.entered.await(2, TimeUnit.SECONDS)).isTrue();
            // A slow reader may miss an update, but must never receive a false calendar fact.
            if (live.tryNext() instanceof Poll.Value<Value>(var observed)) {
                assertThat(observed).isEqualTo(Value.of(expected));
            }
            clock.release.countDown();
            StreamAssertions.assertThat(live).withinTimeout(Duration.ofSeconds(2)).awaitsNext(Value.of(expected));
            try (val fresh = pip.weekdayIn(days, zone)) {
                StreamAssertions.assertThat(fresh).withinTimeout(Duration.ofSeconds(2)).awaitsNext(Value.of(expected));
            }
        } finally {
            clock.release.countDown();
        }
    }

    @ParameterizedTest(name = "{0} after a clock rewind")
    @ValueSource(strings = { "after", "before", "between", "overnight" })
    @DisplayName("a pending local boundary is re-evaluated at the actual wall-clock time")
    void whenWallClockRewindsBeforePendingBoundaryThenTheValueAndNextDeadlineFollowTheClock(String finder)
            throws InterruptedException {
        val start     = Instant.parse("2025-01-01T12:00:00Z");
        val boundary  = start.plusSeconds(60);
        val clock     = new MutableClock(start);
        val scheduler = new QueuedScheduler();
        val pip       = new TimePolicyInformationPoint(clock, scheduler);
        val initial   = Value.of("before".equals(finder) || "overnight".equals(finder));
        try (val live = localWindow(pip, finder)) {
            StreamAssertions.assertThat(live).withinTimeout(Duration.ofSeconds(2)).awaitsNext(initial);
            val timer = scheduler.next();
            assertThat(timer.when()).isEqualTo(boundary);
            clock.setInstant(start.minusSeconds(30));
            timer.task().run();
            StreamAssertions.assertThat(live).withinTimeout(Duration.ofSeconds(2)).awaitsNext(initial);
            val retry = scheduler.next();
            assertThat(retry.when()).isEqualTo(boundary);
            try (val fresh = localWindow(pip, finder)) {
                StreamAssertions.assertThat(fresh).withinTimeout(Duration.ofSeconds(2)).awaitsNext(initial);
            }
            clock.setInstant(boundary);
            retry.task().run();
            StreamAssertions.assertThat(live).withinTimeout(Duration.ofSeconds(2))
                    .awaitsNext(Value.of(!initial.equals(Value.TRUE)));
        }
    }

    @ParameterizedTest(name = "{0} with {1} under a Turkish JVM locale")
    @CsvSource({ "weekday,friday", "weekday,FRIDAY", "start,friday", "start,FRIDAY", "end,friday", "end,FRIDAY",
            "month,april", "month,APRIL" })
    @ResourceLock("java.util.Locale.default")
    @DisplayName("English calendar names are independent of the JVM locale")
    void whenDefaultLocaleIsTurkishThenEnglishCalendarNamesAreAccepted(String finder, String name) {
        val previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            val now = Instant.parse("2025-04-04T12:00:00Z");
            val pip = new TimePolicyInformationPoint(Clock.fixed(now, ZoneOffset.UTC), new QueuedScheduler());
            try (val stream = namedCalendar(pip, finder, name)) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(2)).awaitsNext(Value.TRUE);
            }
        } finally {
            Locale.setDefault(previous);
        }
    }

    @ParameterizedTest(name = "opening at {0}: initial {1}, due event {2}")
    @CsvSource({ "12:01:00,false,true", "13:00:00,true,false" })
    @DisplayName("opening an absolute window at a boundary separates its initial comparison from its due event")
    void whenOpeningAtAnAbsoluteWindowBoundaryThenTheDueEventUpdatesTheInitialComparison(String time, boolean initial,
            boolean atBoundary) throws InterruptedException {
        val now       = Instant.parse("2025-01-01T" + time + "Z");
        val scheduler = new QueuedScheduler();
        val pip       = new TimePolicyInformationPoint(Clock.fixed(now, ZoneOffset.UTC), scheduler);
        try (val stream = pip.nowIsBetween(Value.of("2025-01-01T12:01:00Z"), Value.of("2025-01-01T13:00:00Z"))) {
            StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(2)).awaitsNext(Value.of(initial));
            val timer = scheduler.next();
            assertThat(timer.when()).isEqualTo(now);
            timer.task().run();
            StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(2)).awaitsNext(Value.of(atBoundary));
        }
    }

    @ParameterizedTest(name = "checkpoint {0}: {1}")
    @CsvFileSource(resources = "/time-checkpoints.tsv", delimiter = '\t')
    @DisplayName("ISO calendar checkpoints schedule the exact instant")
    void whenCalendarCheckpointIsParsedThenItSchedulesTheExpectedInstant(String text, String expected)
            throws InterruptedException {
        val scheduler = new QueuedScheduler();
        if ("error".equals(expected)) {
            val pip = new TimePolicyInformationPoint(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), scheduler);
            try (val stream = pip.nowIsAfter(Value.of(text))) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(2)).awaitsNext(Value
                        .error("Invalid timestamp. Expected an ISO 8601 calendar date with a time and UTC offset."));
            }
            return;
        }
        val parts = expected.split(" ");
        val when  = Instant.ofEpochSecond(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
        val clock = Clock.fixed(when.minusSeconds(1), ZoneOffset.UTC);
        val pip   = new TimePolicyInformationPoint(clock, scheduler);
        try (val stream = pip.nowIsAfter(Value.of(text))) {
            StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(2)).awaitsNext(Value.FALSE);
            assertThat(scheduler.next().when()).isEqualTo(when);
        }
    }

    @Test
    @DisplayName("a window with end-of-day and minute checkpoints changes at the UTC boundaries")
    void whenWindowUsesEndOfDayAndMinutesThenItOpensAtTheUtcBoundary() throws InterruptedException {
        val before    = Instant.parse("2021-11-07T23:59:59Z");
        val scheduler = new QueuedScheduler();
        val pip       = new TimePolicyInformationPoint(Clock.fixed(before, ZoneOffset.UTC), scheduler);
        try (val stream = pip.nowIsBetween(Value.of("2021-11-07T24:00:00Z"), Value.of("2021-11-08T01:01+01:00"))) {
            StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(2)).awaitsNext(Value.FALSE);
            val start = scheduler.next();
            assertThat(start.when()).isEqualTo(before.plusSeconds(1));
            start.task().run();
            StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(2)).awaitsNext(Value.TRUE);
            val end = scheduler.next();
            assertThat(end.when()).isEqualTo(before.plusSeconds(61));
            end.task().run();
            StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(2)).awaitsNext(Value.FALSE);
        }
    }

    private static Stream<Value> namedCalendar(TimePolicyInformationPoint pip, String finder, String name) {
        return switch (finder) {
        case "weekday" -> pip.weekdayIn(Value.ofArray(Value.of(name)));
        case "start"   -> pip.dayOfWeekBetween(Value.of(name), Value.of("SATURDAY"));
        case "end"     -> pip.dayOfWeekBetween(Value.of("THURSDAY"), Value.of(name));
        default        -> pip.monthIn(Value.ofArray(Value.of(name)));
        };
    }

    private static Stream<Value> localWindow(TimePolicyInformationPoint pip, String finder) {
        val start = Value.of("12:01");
        val end   = Value.of("13:00");
        return switch (finder) {
        case "after"   -> pip.localTimeIsAfter(start);
        case "before"  -> pip.localTimeIsBefore(start);
        case "between" -> pip.localTimeIsBetween(start, end);
        default        -> pip.localTimeIsBetween(end, start);
        };
    }

    private record Timer(Instant when, Runnable task) {}

    private static final class QueuedScheduler implements TimeScheduler {
        private final LinkedBlockingQueue<Timer> timers = new LinkedBlockingQueue<>();

        @Override
        public Cancellable scheduleAt(Instant when, Runnable task) {
            val timer = new Timer(when, task);
            timers.add(timer);
            return () -> timers.remove(timer);
        }

        Timer next() throws InterruptedException {
            val timer = timers.poll(2, TimeUnit.SECONDS);
            assertThat(timer).as("next pending calendar boundary").isNotNull();
            return timer;
        }
    }

    private static final class GatedClock extends Clock {
        private final AtomicBoolean  blockNextRead = new AtomicBoolean();
        private final CountDownLatch entered       = new CountDownLatch(1);
        private final CountDownLatch release       = new CountDownLatch(1);
        private volatile Instant     current;

        GatedClock(Instant initial) {
            current = initial;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(current, zone);
        }

        @Override
        public Instant instant() {
            if (blockNextRead.compareAndSet(true, false)) {
                entered.countDown();
                try {
                    if (!release.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Calendar clock read was not released");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            }
            return current;
        }
    }
}
