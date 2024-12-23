/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.pip.time;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import reactor.test.StepVerifier;

class TimePolicyInformationPointTests {

    @Test
    void contextIsAbleToLoadTimePolicyInformationPoint() {
        final var sut = new TimePolicyInformationPoint(mock(Clock.class));
        assertDoesNotThrow(() -> new AnnotationAttributeContext(() -> List.of(sut), List::of));
    }

    @Test
    void now_EmitsUpdates() {
        final var now        = Instant.parse("2021-11-08T13:00:00Z");
        final var nowPlusOne = Instant.parse("2021-11-08T13:00:01Z");
        final var nowPlusTwo = Instant.parse("2021-11-08T13:00:02Z");
        final var clock      = mock(Clock.class);
        when(clock.instant()).thenReturn(now, nowPlusOne, nowPlusTwo);
        final var sut = new TimePolicyInformationPoint(clock);
        StepVerifier.withVirtualTime(sut::now).expectNext(Val.of(now.toString())).thenAwait(Duration.ofSeconds(2))
                .expectNext(Val.of(nowPlusOne.toString()), Val.of(nowPlusTwo.toString())).thenCancel().verify();
    }

    @Test
    void now_zeroDelay_Fails() {
        final var clock = mock(Clock.class);
        final var sut   = new TimePolicyInformationPoint(clock);
        StepVerifier.withVirtualTime(() -> sut.now(Val.of(0L))).expectError(PolicyEvaluationException.class).verify();
    }

    @Test
    void systemTimeZone_isRetrieved() {
        final var sut = new TimePolicyInformationPoint(mock(Clock.class)).systemTimeZone().next();
        StepVerifier.create(sut).expectNextMatches(n -> ZoneId.of(n.getText()) != null).verifyComplete();
    }

    @Test
    void nowIsAfterTest() {
        final var startingTime = Instant.parse("2021-11-08T13:00:00Z");
        final var checkpoint   = Val.of("2021-11-08T14:30:00Z");
        final var clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        final var sut = new TimePolicyInformationPoint(clock);
        StepVerifier.withVirtualTime(() -> sut.nowIsAfter(checkpoint)).expectNext(Val.FALSE)
                .thenAwait(Duration.ofMinutes(91L)).expectNext(Val.TRUE).verifyComplete();
    }

    @Test
    void localTimeIsAlwaysAfterMidnightTest() {
        final var startingTime = Instant.parse("2021-11-08T13:00:00Z");
        final var checkpoint   = Val.of(LocalTime.MIN.toString());
        final var clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        final var sut = new TimePolicyInformationPoint(clock);
        StepVerifier.withVirtualTime(() -> sut.localTimeIsAfter(checkpoint)).expectNext(Val.TRUE).verifyComplete();
    }

    @Test
    void localTimeIsNeverAfterMaxTimeTest() {
        final var startingTime = Instant.parse("2021-11-08T13:00:00Z");
        final var checkpoint   = Val.of(LocalTime.MAX.toString());
        final var clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        final var sut = new TimePolicyInformationPoint(clock);
        StepVerifier.withVirtualTime(() -> sut.localTimeIsAfter(checkpoint)).expectNext(Val.FALSE).verifyComplete();
    }

    @Test
    void localTimeIsNeverBetweenForNullSizeIntervalTest() {
        final var startingTime = Instant.parse("2021-11-08T13:00:00Z");
        final var start        = Val.of("12:00");
        final var end          = Val.of("12:00");
        final var clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        final var sut = new TimePolicyInformationPoint(clock);
        StepVerifier.withVirtualTime(() -> sut.localTimeIsBetween(start, end)).expectNext(Val.FALSE).verifyComplete();
    }

    @Test
    void localTimeIsAlwaysBetweenForMinMaxIntervalTest() {
        final var startingTime = Instant.parse("2021-11-08T13:00:00Z");
        final var start        = Val.of(LocalTime.MIN.toString());
        final var end          = Val.of(LocalTime.MAX.toString());
        final var clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        final var sut = new TimePolicyInformationPoint(clock);
        StepVerifier.withVirtualTime(() -> sut.localTimeIsBetween(start, end)).expectNext(Val.TRUE).verifyComplete();
    }

    @Test
    void localTimeIntervalStartsAtMinButNotTillMaxTest() {
        final var startingTime = Instant.parse("2021-11-08T13:00:00Z");
        final var start        = Val.of(LocalTime.MIN.toString());
        final var end          = Val.of("22:00");
        final var clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        final var sut = new TimePolicyInformationPoint(clock);
        StepVerifier.withVirtualTime(() -> sut.localTimeIsBetween(start, end)).expectNext(Val.TRUE).thenCancel()
                .verify();
    }

    @Test
    void localTimeIntervalStartsAtMaxButNotTillMinTest() {
        final var startingTime = Instant.parse("2021-11-08T13:00:00Z");
        final var start        = Val.of(LocalTime.MAX.toString());
        final var end          = Val.of("14:00");
        final var clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        final var sut = new TimePolicyInformationPoint(clock);
        StepVerifier.withVirtualTime(() -> sut.localTimeIsBetween(start, end)).expectNext(Val.TRUE).thenCancel()
                .verify();
    }

    @Test
    void localTimeIsAlwaysBetweenForMaxMinIntervalTest() {
        final var startingTime = Instant.parse("2021-11-08T13:00:00Z");
        final var start        = Val.of(LocalTime.MAX.toString());
        final var end          = Val.of(LocalTime.MIN.toString());
        final var clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        final var sut = new TimePolicyInformationPoint(clock);
        StepVerifier.withVirtualTime(() -> sut.localTimeIsBetween(start, end)).expectNext(Val.TRUE).verifyComplete();
    }

    @Test
    void localTimeBetweenStartingBeforeIntervalTest() {
        final var startingTime = Instant.parse("2021-11-08T13:00:00Z");
        final var start        = Val.of("14:00:00");
        final var end          = Val.of("15:00");
        final var clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        final var sut = new TimePolicyInformationPoint(clock);
        // @formatter:off
		StepVerifier.withVirtualTime(() -> sut.localTimeIsBetween(start, end))
			.expectNext(Val.FALSE)
			.thenAwait(Duration.ofHours(1L)).expectNext(Val.TRUE)
			.thenAwait(Duration.ofHours(1L)).expectNext(Val.FALSE)
			.thenAwait(Duration.ofHours(23L)).expectNext(Val.TRUE)
			.thenAwait(Duration.ofHours(1L)).expectNext(Val.FALSE)
			.thenAwait(Duration.ofHours(23L)).expectNext(Val.TRUE)
			.thenAwait(Duration.ofHours(1L)).expectNext(Val.FALSE)
			.thenAwait(Duration.ofHours(23L)).expectNext(Val.TRUE)
			.thenCancel().verify();
		// @formatter:on
    }

    @Test
    void localTimeBetweenStartingBeforeIntervalReversedTest() {
        final var startingTime = Instant.parse("2021-11-08T13:00:00Z");
        final var start        = Val.of("15:00");
        final var end          = Val.of("14:00:00");
        final var clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        final var sut = new TimePolicyInformationPoint(clock);
        // @formatter:off
		StepVerifier.withVirtualTime(() -> sut.localTimeIsBetween(start, end))
			.expectNext(Val.TRUE)
			.thenAwait(Duration.ofHours(1L)).expectNext(Val.FALSE)
			.thenAwait(Duration.ofHours(1L)).expectNext(Val.TRUE)
			.thenAwait(Duration.ofHours(23L)).expectNext(Val.FALSE)
			.thenAwait(Duration.ofHours(1L)).expectNext(Val.TRUE)
			.thenAwait(Duration.ofHours(23L)).expectNext(Val.FALSE)
			.thenAwait(Duration.ofHours(1L)).expectNext(Val.TRUE)
			.thenAwait(Duration.ofHours(23L)).expectNext(Val.FALSE)
			.thenCancel().verify();
		// @formatter:on
    }

    @Test
    void localTimeBetweenStartingInsideOfIntervalTest() {
        final var startingTime = Instant.parse("2021-11-08T15:00:00Z");
        final var start        = Val.of("14:00:00");
        final var end          = Val.of("16:00");
        final var clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        final var sut = new TimePolicyInformationPoint(clock);
        // @formatter:off
		StepVerifier.withVirtualTime(() -> sut.localTimeIsBetween(start, end))
			.expectNext(Val.TRUE)
			.thenAwait(Duration.ofHours(1L)).expectNext(Val.FALSE)
			.thenAwait(Duration.ofHours(22L)).expectNext(Val.TRUE)
			.thenAwait(Duration.ofHours(2L)).expectNext(Val.FALSE)
			.thenAwait(Duration.ofHours(22L)).expectNext(Val.TRUE)
			.thenAwait(Duration.ofHours(2L)).expectNext(Val.FALSE)
			.thenAwait(Duration.ofHours(22L)).expectNext(Val.TRUE)
			.thenCancel().verify();
		// @formatter:on
    }

    @Test
    void localTimeBetweenStartingAfterIntervalTest() {
        final var startingTime = Instant.parse("2021-11-08T18:00:00Z");
        final var start        = Val.of("14:00:00");
        final var end          = Val.of("16:00");
        final var clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        final var sut = new TimePolicyInformationPoint(clock);
        // @formatter:off
		StepVerifier.withVirtualTime(() -> sut.localTimeIsBetween(start, end))
			.expectNext(Val.FALSE)
			.thenAwait(Duration.ofHours(20L)).expectNext(Val.TRUE)
			.thenAwait(Duration.ofHours(2L)).expectNext(Val.FALSE)
			.thenAwait(Duration.ofHours(22L)).expectNext(Val.TRUE)
			.thenAwait(Duration.ofHours(2L)).expectNext(Val.FALSE)
			.thenAwait(Duration.ofHours(22L)).expectNext(Val.TRUE)
			.thenAwait(Duration.ofHours(2L)).expectNext(Val.FALSE)
			.thenAwait(Duration.ofHours(22L)).expectNext(Val.TRUE)
			.thenCancel().verify();
		// @formatter:on
    }

    @Test
    void startingTimeIsAfterStartingAfterTest() {
        final var startingTime = Instant.parse("2021-11-08T13:00:00Z");
        final var checkpoint   = Val.of("12:00");
        final var clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        final var sut = new TimePolicyInformationPoint(clock);
        // @formatter:off
		StepVerifier.withVirtualTime(() -> sut.localTimeIsAfter(checkpoint))
			.expectNext(Val.TRUE)
			.thenAwait(Duration.ofHours(11L)).expectNext(Val.FALSE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Val.TRUE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Val.FALSE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Val.TRUE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Val.FALSE)
			.thenCancel().verify();
		// @formatter:on
    }

    @Test
    void startingTimeIsBeforeStartingAfterTest() {
        final var startingTime = Instant.parse("2021-11-08T11:00:00Z");
        final var checkpoint   = Val.of("12:00");
        final var clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        final var sut = new TimePolicyInformationPoint(clock);
        // @formatter:off
		StepVerifier.withVirtualTime(() -> sut.localTimeIsAfter(checkpoint))
			.expectNext(Val.FALSE)
			.thenAwait(Duration.ofHours(1L)).expectNext(Val.TRUE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Val.FALSE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Val.TRUE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Val.FALSE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Val.TRUE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Val.FALSE)
			.thenCancel().verify();
		// @formatter:on
    }

    @Test
    void startingTimeIBeforeStartingBeforeTest() {
        final var startingTime = Instant.parse("2021-11-08T11:00:00Z");
        final var checkpoint   = Val.of("12:00");
        final var clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        final var sut = new TimePolicyInformationPoint(clock);
        // @formatter:off
		StepVerifier.withVirtualTime(() -> sut.localTimeIsBefore(checkpoint))
			.expectNext(Val.TRUE)
			.thenAwait(Duration.ofHours(1L)).expectNext(Val.FALSE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Val.TRUE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Val.FALSE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Val.TRUE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Val.FALSE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Val.TRUE)
			.thenCancel().verify();
		// @formatter:on
    }

    @Test
    void nowIsBetweenStartAfterIntervalTest() {
        final var startingTime  = Instant.parse("2021-11-08T13:00:40Z");
        final var intervalStart = Val.of("2021-11-08T13:00:05Z");
        final var intervalEnd   = Val.of("2021-11-08T13:00:10Z");

        final var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        final var sut = new TimePolicyInformationPoint(clock);

        StepVerifier.withVirtualTime(() -> sut.nowIsBetween(intervalStart, intervalEnd)).expectNext(Val.FALSE)
                .verifyComplete();
    }

    @Test
    void nowIsBetweenStartBetweenTest() {
        final var startingTime  = Instant.parse("2021-11-08T13:00:05Z");
        final var intervalStart = Val.of("2021-11-08T13:00:00Z");
        final var intervalEnd   = Val.of("2021-11-08T13:00:10Z");

        final var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        final var sut = new TimePolicyInformationPoint(clock);
        StepVerifier.withVirtualTime(() -> sut.nowIsBetween(intervalStart, intervalEnd)).expectNext(Val.TRUE)
                .thenAwait(Duration.ofSeconds(5L)).expectNext(Val.FALSE).verifyComplete();
    }

    @Test
    void nowIsBetweenStartBeforeTest() {
        final var startingTime  = Instant.parse("2021-11-08T13:00:00Z");
        final var intervalStart = Val.of("2021-11-08T13:00:05Z");
        final var intervalEnd   = Val.of("2021-11-08T13:00:10Z");

        final var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        final var sut = new TimePolicyInformationPoint(clock);
        StepVerifier.withVirtualTime(() -> sut.nowIsBetween(intervalStart, intervalEnd)).expectNext(Val.FALSE)
                .thenAwait(Duration.ofSeconds(5L)).expectNext(Val.TRUE).thenAwait(Duration.ofSeconds(5L))
                .expectNext(Val.FALSE).verifyComplete();
    }

    @Test
    void nowIsAfterTestAlwaysAfter() {
        final var now        = Instant.parse("2021-11-08T13:00:00Z");
        final var checkpoint = Val.of("2021-11-01T14:30:00Z");
        final var clock      = mock(Clock.class);
        when(clock.instant()).thenReturn(now);
        final var sut = new TimePolicyInformationPoint(clock);
        StepVerifier.withVirtualTime(() -> sut.nowIsAfter(checkpoint)).expectNext(Val.TRUE).verifyComplete();
    }

    @Test
    void nowIsBeforeTest() {
        final var now        = Instant.parse("2021-11-08T13:00:00Z");
        final var checkpoint = Val.of("2021-11-08T14:30:00Z");
        final var clock      = mock(Clock.class);
        when(clock.instant()).thenReturn(now);
        final var sut = new TimePolicyInformationPoint(clock);
        StepVerifier.withVirtualTime(() -> sut.nowIsBefore(checkpoint)).expectNext(Val.TRUE)
                .thenAwait(Duration.ofMinutes(91L)).expectNext(Val.FALSE).verifyComplete();
    }

    @Test
    void toggleTest() {
        final var sut = new TimePolicyInformationPoint(mock(Clock.class));
        StepVerifier.withVirtualTime(() -> sut.toggle(Val.of(5_000L), Val.of(1_000L))).expectNext(Val.TRUE)
                .thenAwait(Duration.ofMillis(5_0000L)).expectNext(Val.FALSE).thenAwait(Duration.ofMillis(1_000L))
                .expectNext(Val.TRUE).thenCancel().verify();
    }

}
