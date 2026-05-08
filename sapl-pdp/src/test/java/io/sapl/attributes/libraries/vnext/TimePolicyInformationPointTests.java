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

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.attributes.libraries.vnext.util.MutableClock;
import io.sapl.attributes.libraries.vnext.util.StreamAssertions;
import io.sapl.attributes.libraries.vnext.util.TestTimeScheduler;
import lombok.val;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

@DisplayName("TimePolicyInformationPoint (vnext)")
class TimePolicyInformationPointTests {

    @Nested
    @DisplayName("now")
    class Now {

        @Test
        @DisplayName("emits the current ISO timestamp immediately and again at each scheduled tick")
        void whenNowThenEmitsUpdates() {
            val t0        = Instant.parse("2021-11-08T13:00:00Z");
            val clock     = new MutableClock(t0, ZoneOffset.UTC);
            val scheduler = new TestTimeScheduler(t0);
            val sut       = new TimePolicyInformationPoint(clock, scheduler);

            try (val stream = sut.now()) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.of("2021-11-08T13:00:00Z"));

                clock.setInstant(t0.plusSeconds(1));
                scheduler.advanceTo(t0.plusSeconds(1));
                StreamAssertions.assertThat(stream).awaitsNext(Value.of("2021-11-08T13:00:01Z"));

                clock.setInstant(t0.plusSeconds(2));
                scheduler.advanceTo(t0.plusSeconds(2));
                StreamAssertions.assertThat(stream).awaitsNext(Value.of("2021-11-08T13:00:02Z"));
            }
        }

        @Test
        @DisplayName("returns error for zero delay")
        void whenNowWithZeroDelayThenFails() {
            val t0        = Instant.parse("2021-11-08T13:00:00Z");
            val clock     = new MutableClock(t0, ZoneOffset.UTC);
            val scheduler = new TestTimeScheduler(t0);
            val sut       = new TimePolicyInformationPoint(clock, scheduler);

            try (val stream = sut.now(Value.of(0L))) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> {
                    if (!(v instanceof ErrorValue)) {
                        throw new AssertionError("Expected ErrorValue, got: " + v);
                    }
                }).awaitsCompletion();
            }
        }

        @Test
        @DisplayName("emits an error value when the clock returns null")
        void whenClockReturnsNullThenEmitsErrorValue() {
            val t0        = Instant.parse("2021-11-08T13:00:00Z");
            val clock     = nullReturningClock();
            val scheduler = new TestTimeScheduler(t0);
            val sut       = new TimePolicyInformationPoint(clock, scheduler);

            try (val stream = sut.now()) {
                StreamAssertions.assertThat(stream).awaitsNext(v -> {
                    if (!(v instanceof ErrorValue err)) {
                        throw new AssertionError("Expected ErrorValue, got: " + v);
                    }
                    if (!err.message().contains("Clock returned null")) {
                        throw new AssertionError(
                                "Expected message containing 'Clock returned null', got: " + err.message());
                    }
                });
            }
        }

        private Clock nullReturningClock() {
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return null;
                }
            };
        }
    }

    @Nested
    @DisplayName("nowIsAfter")
    class NowIsAfter {

        @Test
        @DisplayName("emits false then true when checkpoint is in the future")
        void whenCheckpointInFutureThenEmitsFalseThenTrueAtCheckpoint() {
            val now        = Instant.parse("2021-11-08T13:00:00Z");
            val checkpoint = Instant.parse("2021-11-08T14:30:00Z");
            val clock      = new MutableClock(now, ZoneOffset.UTC);
            val scheduler  = new TestTimeScheduler(now);
            val sut        = new TimePolicyInformationPoint(clock, scheduler);

            try (val stream = sut.nowIsAfter(Value.of(checkpoint.toString()))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.FALSE);
                scheduler.advanceTo(checkpoint);
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE).awaitsCompletion();
            }
        }

        @Test
        @DisplayName("returns true when checkpoint is in the past")
        void whenCheckpointInPastThenEmitsTrueAndCompletes() {
            val now        = Instant.parse("2021-11-08T13:00:00Z");
            val checkpoint = Instant.parse("2021-11-01T14:30:00Z");
            val clock      = new MutableClock(now, ZoneOffset.UTC);
            val scheduler  = new TestTimeScheduler(now);
            val sut        = new TimePolicyInformationPoint(clock, scheduler);

            try (val stream = sut.nowIsAfter(Value.of(checkpoint.toString()))) {
                StreamAssertions.assertThat(stream).awaitsNext(Value.TRUE).awaitsCompletion();
            }
        }

        @Test
        @DisplayName("emits an error when checkpoint is not a valid ISO instant")
        void whenCheckpointMalformedThenEmitsError() {
            val now       = Instant.parse("2021-11-08T13:00:00Z");
            val clock     = new MutableClock(now, ZoneOffset.UTC);
            val scheduler = new TestTimeScheduler(now);
            val sut       = new TimePolicyInformationPoint(clock, scheduler);

            try (val stream = sut.nowIsAfter(Value.of("not-an-instant"))) {
                StreamAssertions.assertThat(stream).withinTimeout(Duration.ofSeconds(2)).awaitsNext(v -> {
                    if (!(v instanceof ErrorValue)) {
                        throw new AssertionError("Expected ErrorValue, got: " + v);
                    }
                }).awaitsCompletion();
            }
        }
    }

    @Nested
    @DisplayName("broker loading")
    class BrokerLoading {

        @Test
        @Disabled("TODO: enable when AttributeMethodSignatureProcessor accepts Stream<Value>")
        @DisplayName("when loaded into broker then registers under time namespace")
        void whenLoadedIntoBrokerThenRegistersUnderTimeNamespace() {
            // Mirror of the original broker-load test. Cannot run until the
            // signature processor accepts Stream<Value> return types.
        }
    }
}
