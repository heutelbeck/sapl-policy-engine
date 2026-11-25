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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.nimbusds.jwt.JWTClaimsSet;

import io.sapl.api.model.Poll;
import io.sapl.api.model.Value;
import io.sapl.api.stream.Stream;
import io.sapl.api.test.stream.MutableClock;
import io.sapl.api.test.stream.TestTimeScheduler;

/** Exercises the time phase with ordinary already decoded claims, independently of signature and transport handling. */
@DisplayName("Decoded JWT validity timelines")
@Timeout(5)
class JWTValidityTimelineTests {
    private static final Duration FUTURE = Duration.ofDays(150L * 365);

    @SuppressWarnings("unchecked")
    private static Stream<Value> timeline(JWTClaimsSet claims, MutableClock clock, TestTimeScheduler scheduler) {
        var pip = new JWTPolicyInformationPoint(null, clock, scheduler);
        try {
            var method = JWTPolicyInformationPoint.class.getDeclaredMethod("validateTime", JWTClaimsSet.class,
                    long.class, long.class);
            method.setAccessible(true);
            return (Stream<Value>) method.invoke(pip, claims, 0L, 0L);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("The decoded-claims time phase must be callable.", e);
        }
    }

    @Test
    @DisplayName("Maturity and expiry remain scheduled beyond 100 years")
    void whenMaturityAndExpiryAreFarAheadThenBothTransitionsAreScheduled() throws InterruptedException {
        var clock     = new MutableClock(Instant.EPOCH);
        var scheduler = new TestTimeScheduler(Instant.EPOCH);
        var start     = Instant.EPOCH.plus(FUTURE);
        var end       = start.plusSeconds(3600);
        var claims    = new JWTClaimsSet.Builder().notBeforeTime(Date.from(start)).expirationTime(Date.from(end))
                .build();
        try (var states = timeline(claims, clock, scheduler)) {
            assertThat(states.awaitNext()).isEqualTo(Value.of("IMMATURE"));
            assertThat(scheduler.pendingCount()).isEqualTo(2);
            clock.setInstant(start);
            scheduler.advanceTo(start);
            assertThat(states.awaitNext()).isEqualTo(Value.of("VALID"));
            assertThat(scheduler.pendingCount()).isOne();
            clock.setInstant(end);
            scheduler.advanceTo(end);
            assertThat(states.awaitNext()).isEqualTo(Value.of("EXPIRED"));
            assertThat(states.awaitNext()).isNull();
            assertThat(scheduler.pendingCount()).isZero();
        }
    }

    @Test
    @DisplayName("A far expiry remains after an ordinary maturity transition")
    void whenExpiryIsFarAfterShortMaturityThenTheExpiryRemainsScheduled() throws InterruptedException {
        var clock     = new MutableClock(Instant.EPOCH);
        var scheduler = new TestTimeScheduler(Instant.EPOCH);
        var start     = Instant.EPOCH.plusSeconds(2);
        var end       = Instant.EPOCH.plus(FUTURE);
        var claims    = new JWTClaimsSet.Builder().notBeforeTime(Date.from(start)).expirationTime(Date.from(end))
                .build();
        try (var states = timeline(claims, clock, scheduler)) {
            assertThat(states.awaitNext()).isEqualTo(Value.of("IMMATURE"));
            assertThat(scheduler.pendingCount()).isEqualTo(2);
            clock.setInstant(start);
            scheduler.advanceTo(start);
            assertThat(states.awaitNext()).isEqualTo(Value.of("VALID"));
            assertThat(scheduler.pendingCount()).isOne();
            clock.setInstant(end);
            scheduler.advanceTo(end);
            assertThat(states.awaitNext()).isEqualTo(Value.of("EXPIRED"));
            assertThat(states.awaitNext()).isNull();
        }
    }

    @Test
    @DisplayName("A far expiry remains pending until its boundary")
    void whenExpiryIsFarAheadThenItStaysPendingUntilItsBoundary() throws InterruptedException {
        var clock     = new MutableClock(Instant.EPOCH);
        var scheduler = new TestTimeScheduler(Instant.EPOCH);
        var end       = Instant.EPOCH.plus(FUTURE);
        var claims    = new JWTClaimsSet.Builder().expirationTime(Date.from(end)).build();
        try (var states = timeline(claims, clock, scheduler)) {
            assertThat(states.awaitNext()).isEqualTo(Value.of("VALID"));
            assertThat(scheduler.pendingCount()).isOne();
            clock.setInstant(end);
            scheduler.advanceTo(end);
            assertThat(states.awaitNext()).isEqualTo(Value.of("EXPIRED"));
            assertThat(states.awaitNext()).isNull();
        }
    }

    @Test
    @DisplayName("Closing cancels every pending validity timer")
    void whenTheConsumerClosesBeforeTheDeadlineThenAllPendingTimersAreCancelled() throws InterruptedException {
        var clock     = new MutableClock(Instant.EPOCH);
        var scheduler = new TestTimeScheduler(Instant.EPOCH);
        var start     = Instant.EPOCH.plus(FUTURE);
        var claims    = new JWTClaimsSet.Builder().notBeforeTime(Date.from(start))
                .expirationTime(Date.from(start.plusSeconds(3600))).build();
        var states    = timeline(claims, clock, scheduler);
        try {
            assertThat(states.awaitNext()).isEqualTo(Value.of("IMMATURE"));
            assertThat(scheduler.pendingCount()).isEqualTo(2);
            states.close();
            assertThat(scheduler.pendingCount()).isZero();
            clock.setInstant(start.plusSeconds(3600));
            scheduler.advanceTo(clock.instant());
            assertThat(states.tryNext()).isInstanceOf(Poll.Done.class);
        } finally {
            states.close();
        }
    }

    @Test
    @DisplayName("A timeline without future transitions completes normally")
    void whenNoFutureTransitionExistsThenTheSingleStateCompletesNormally() throws InterruptedException {
        var clock     = new MutableClock(Instant.EPOCH);
        var scheduler = new TestTimeScheduler(Instant.EPOCH);
        try (var states = timeline(new JWTClaimsSet.Builder().build(), clock, scheduler)) {
            assertThat(states.awaitNext()).isEqualTo(Value.of("VALID"));
            assertThat(states.awaitNext()).isNull();
            assertThat(scheduler.pendingCount()).isZero();
        }
    }
}
