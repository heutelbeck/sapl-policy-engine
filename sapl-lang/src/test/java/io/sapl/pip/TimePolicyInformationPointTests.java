/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.pip;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import reactor.test.StepVerifier;

public class TimePolicyInformationPointTests {

	@Test
	public void contextIsAbleToLoadTimePolicyInformationPoint() {
		var sut = new TimePolicyInformationPoint(mock(Clock.class));
		assertDoesNotThrow(() -> new AnnotationAttributeContext(sut));
	}

	@Test
	public void now_EmitsUpdates() {
		var now        = Instant.parse("2021-11-08T13:00:00Z");
		var nowPlusOne = Instant.parse("2021-11-08T13:00:01Z");
		var nowPlusTwo = Instant.parse("2021-11-08T13:00:02Z");
		var clock      = mock(Clock.class);
		when(clock.instant()).thenReturn(now, nowPlusOne, nowPlusTwo);
		var sut = new TimePolicyInformationPoint(clock);
		StepVerifier.withVirtualTime(sut::now).expectNext(Val.of(now.toString()))
				.thenAwait(Duration.ofSeconds(2))
				.expectNext(Val.of(nowPlusOne.toString()), Val.of(nowPlusTwo.toString())).thenCancel().verify();
	}

	@Test
	public void now_zeroDelay_Fails() {
		var clock = mock(Clock.class);
		var sut   = new TimePolicyInformationPoint(clock);
		StepVerifier.withVirtualTime(() -> sut.now(Val.of(0L))).expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void systemTimeZone_isRetrieved() {
		var sut    = new TimePolicyInformationPoint(mock(Clock.class));
		var zoneId = ZoneId.of("UTC");
		try (MockedStatic<ZoneId> mock = mockStatic(ZoneId.class, Mockito.CALLS_REAL_METHODS)) {
			mock.when(ZoneId::systemDefault).thenReturn(zoneId);
			StepVerifier.create(sut.systemTimeZone()).expectNext(Val.of("UTC")).verifyComplete();
		}
	}

	@Test
	public void nowIsAfterTest() {
		var startingTime = Instant.parse("2021-11-08T13:00:00Z");
		var checkpoint   = Val.of("2021-11-08T14:30:00Z");
		var clock        = mock(Clock.class);
		when(clock.instant()).thenReturn(startingTime);
		var sut = new TimePolicyInformationPoint(clock);
		StepVerifier.withVirtualTime(() -> sut.nowIsAfter(checkpoint)).expectNext(Val.FALSE)
				.thenAwait(Duration.ofMinutes(91L)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void localTimeIsAlwaysAfterMidnightTest() {
		var startingTime = Instant.parse("2021-11-08T13:00:00Z");
		var checkpoint   = Val.of(LocalTime.MIN.toString());
		var clock        = mock(Clock.class);
		when(clock.instant()).thenReturn(startingTime);
		var sut = new TimePolicyInformationPoint(clock);
		StepVerifier.withVirtualTime(() -> sut.localTimeIsAfter(checkpoint)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void localTimeIsNeverAfterMaxTimeTest() {
		var startingTime = Instant.parse("2021-11-08T13:00:00Z");
		var checkpoint   = Val.of(LocalTime.MAX.toString());
		var clock        = mock(Clock.class);
		when(clock.instant()).thenReturn(startingTime);
		var sut = new TimePolicyInformationPoint(clock);
		StepVerifier.withVirtualTime(() -> sut.localTimeIsAfter(checkpoint)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void localTimeIsNeverBetweenForNullSizeIntervalTest() {
		var startingTime = Instant.parse("2021-11-08T13:00:00Z");
		var start        = Val.of("12:00");
		var end          = Val.of("12:00");
		var clock        = mock(Clock.class);
		when(clock.instant()).thenReturn(startingTime);
		var sut = new TimePolicyInformationPoint(clock);
		StepVerifier.withVirtualTime(() -> sut.localTimeIsBetween(start, end)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void localTimeIsAlwaysBetweenForMinMaxIntervalTest() {
		var startingTime = Instant.parse("2021-11-08T13:00:00Z");
		var start        = Val.of(LocalTime.MIN.toString());
		var end          = Val.of(LocalTime.MAX.toString());
		var clock        = mock(Clock.class);
		when(clock.instant()).thenReturn(startingTime);
		var sut = new TimePolicyInformationPoint(clock);
		StepVerifier.withVirtualTime(() -> sut.localTimeIsBetween(start, end)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void localTimeIntervalStartsAtMinButNotTillMaxTest() {
		var startingTime = Instant.parse("2021-11-08T13:00:00Z");
		var start        = Val.of(LocalTime.MIN.toString());
		var end          = Val.of("22:00");
		var clock        = mock(Clock.class);
		when(clock.instant()).thenReturn(startingTime);
		var sut = new TimePolicyInformationPoint(clock);
		StepVerifier.withVirtualTime(() -> sut.localTimeIsBetween(start, end)).expectNext(Val.TRUE).thenCancel()
				.verify();
	}

	@Test
	public void localTimeIntervalStartsAtMaxButNotTillMinTest() {
		var startingTime = Instant.parse("2021-11-08T13:00:00Z");
		var start        = Val.of(LocalTime.MAX.toString());
		var end          = Val.of("14:00");
		var clock        = mock(Clock.class);
		when(clock.instant()).thenReturn(startingTime);
		var sut = new TimePolicyInformationPoint(clock);
		StepVerifier.withVirtualTime(() -> sut.localTimeIsBetween(start, end)).expectNext(Val.TRUE).thenCancel()
				.verify();
	}

	@Test
	public void localTimeIsAlwaysBetweenForMaxMinIntervalTest() {
		var startingTime = Instant.parse("2021-11-08T13:00:00Z");
		var start        = Val.of(LocalTime.MAX.toString());
		var end          = Val.of(LocalTime.MIN.toString());
		var clock        = mock(Clock.class);
		when(clock.instant()).thenReturn(startingTime);
		var sut = new TimePolicyInformationPoint(clock);
		StepVerifier.withVirtualTime(() -> sut.localTimeIsBetween(start, end)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void localTimeBetweenStartingBeforeIntervalTest() {
		var startingTime = Instant.parse("2021-11-08T13:00:00Z");
		var start        = Val.of("14:00:00");
		var end          = Val.of("15:00");
		var clock        = mock(Clock.class);
		when(clock.instant()).thenReturn(startingTime);
		var sut = new TimePolicyInformationPoint(clock);
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
	public void localTimeBetweenStartingBeforeIntervalReversedTest() {
		var startingTime = Instant.parse("2021-11-08T13:00:00Z");
		var start        = Val.of("15:00");
		var end          = Val.of("14:00:00");
		var clock        = mock(Clock.class);
		when(clock.instant()).thenReturn(startingTime);
		var sut = new TimePolicyInformationPoint(clock);
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
	public void localTimeBetweenStartingInsideOfIntervalTest() {
		var startingTime = Instant.parse("2021-11-08T15:00:00Z");
		var start        = Val.of("14:00:00");
		var end          = Val.of("16:00");
		var clock        = mock(Clock.class);
		when(clock.instant()).thenReturn(startingTime);
		var sut = new TimePolicyInformationPoint(clock);
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
	public void localTimeBetweenStartingAfterIntervalTest() {
		var startingTime = Instant.parse("2021-11-08T18:00:00Z");
		var start        = Val.of("14:00:00");
		var end          = Val.of("16:00");
		var clock        = mock(Clock.class);
		when(clock.instant()).thenReturn(startingTime);
		var sut = new TimePolicyInformationPoint(clock);
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
	public void startingTimeIsAfterStartingAfterTest() {
		var startingTime = Instant.parse("2021-11-08T13:00:00Z");
		var checkpoint   = Val.of("12:00");
		var clock        = mock(Clock.class);
		when(clock.instant()).thenReturn(startingTime);
		var sut = new TimePolicyInformationPoint(clock);
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
	public void startingTimeIsBeforeStartingAfterTest() {
		var startingTime = Instant.parse("2021-11-08T11:00:00Z");
		var checkpoint   = Val.of("12:00");
		var clock        = mock(Clock.class);
		when(clock.instant()).thenReturn(startingTime);
		var sut = new TimePolicyInformationPoint(clock);
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
	public void startingTimeIBeforeStartingBeforeTest() {
		var startingTime = Instant.parse("2021-11-08T11:00:00Z");
		var checkpoint   = Val.of("12:00");
		var clock        = mock(Clock.class);
		when(clock.instant()).thenReturn(startingTime);
		var sut = new TimePolicyInformationPoint(clock);
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
	public void nowIsBetweenStartAfterIntervalTest() {
		var startingTime  = Instant.parse("2021-11-08T13:00:40Z");
		var intervalStart = Val.of("2021-11-08T13:00:05Z");
		var intervalEnd   = Val.of("2021-11-08T13:00:10Z");

		var clock = mock(Clock.class);
		when(clock.instant()).thenReturn(startingTime);
		var sut = new TimePolicyInformationPoint(clock);

		StepVerifier.withVirtualTime(() -> sut.nowIsBetween(intervalStart, intervalEnd)).expectNext(Val.FALSE)
				.verifyComplete();
	}

	@Test
	public void nowIsBetweenStartBetweenTest() {
		var startingTime  = Instant.parse("2021-11-08T13:00:05Z");
		var intervalStart = Val.of("2021-11-08T13:00:00Z");
		var intervalEnd   = Val.of("2021-11-08T13:00:10Z");

		var clock = mock(Clock.class);
		when(clock.instant()).thenReturn(startingTime);
		var sut = new TimePolicyInformationPoint(clock);
		StepVerifier.withVirtualTime(() -> sut.nowIsBetween(intervalStart, intervalEnd)).expectNext(Val.TRUE)
				.thenAwait(Duration.ofSeconds(5L)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void nowIsBetweenStartBeforeTest() {
		var startingTime  = Instant.parse("2021-11-08T13:00:00Z");
		var intervalStart = Val.of("2021-11-08T13:00:05Z");
		var intervalEnd   = Val.of("2021-11-08T13:00:10Z");

		var clock = mock(Clock.class);
		when(clock.instant()).thenReturn(startingTime);
		var sut = new TimePolicyInformationPoint(clock);
		StepVerifier.withVirtualTime(() -> sut.nowIsBetween(intervalStart, intervalEnd)).expectNext(Val.FALSE)
				.thenAwait(Duration.ofSeconds(5L)).expectNext(Val.TRUE).thenAwait(Duration.ofSeconds(5L))
				.expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void nowIsAfterTestAlwaysAfter() {
		var now        = Instant.parse("2021-11-08T13:00:00Z");
		var checkpoint = Val.of("2021-11-01T14:30:00Z");
		var clock      = mock(Clock.class);
		when(clock.instant()).thenReturn(now);
		var sut = new TimePolicyInformationPoint(clock);
		StepVerifier.withVirtualTime(() -> sut.nowIsAfter(checkpoint)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void nowIsBeforeTest() {
		var now        = Instant.parse("2021-11-08T13:00:00Z");
		var checkpoint = Val.of("2021-11-08T14:30:00Z");
		var clock      = mock(Clock.class);
		when(clock.instant()).thenReturn(now);
		var sut = new TimePolicyInformationPoint(clock);
		StepVerifier.withVirtualTime(() -> sut.nowIsBefore(checkpoint)).expectNext(Val.TRUE)
				.thenAwait(Duration.ofMinutes(91L)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void toggleTest() {
		var sut = new TimePolicyInformationPoint(mock(Clock.class));
		StepVerifier.withVirtualTime(() -> sut.toggle(Val.of(5_000L), Val.of(1_000L))).expectNext(Val.TRUE)
				.thenAwait(Duration.ofMillis(5_0000L)).expectNext(Val.FALSE).thenAwait(Duration.ofMillis(1_000L))
				.expectNext(Val.TRUE).thenCancel().verify();
	}

}
