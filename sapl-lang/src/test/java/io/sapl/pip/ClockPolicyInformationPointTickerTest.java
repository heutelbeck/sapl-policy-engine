/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import io.sapl.api.interpreter.Val;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.interpreter.InitializationException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class ClockPolicyInformationPointTickerTest {

	private static final Val SYSTEM_TIMEZONE_VAL = Val.of(ZoneId.systemDefault().toString());

	@Test
	void test_streamingPolicyWithVirtualTime() throws InitializationException {

		final ClockPolicyInformationPoint clockPip = new ClockPolicyInformationPoint();
		StepVerifier.withVirtualTime(() -> clockPip.ticker(Val.UNDEFINED, Collections.emptyMap(), Flux.just(Val.of(2))))
				.expectSubscription().assertNext(val -> assertThat(val.isError(), is(false)))
				.expectNoEvent(Duration.ofSeconds(2)).thenAwait()
				.assertNext(val -> assertThat(val.isError(), is(false))).expectNoEvent(Duration.ofSeconds(2))
				.thenAwait().assertNext(val -> assertThat(val.isError(), is(false)))
				.expectNoEvent(Duration.ofSeconds(2)).thenAwait()
				.assertNext(val -> assertThat(val.isError(), is(false))).expectNoEvent(Duration.ofSeconds(2))
				.thenAwait().assertNext(val -> assertThat(val.isError(), is(false)))
				.expectNoEvent(Duration.ofSeconds(2)).thenAwait()
				.assertNext(val -> assertThat(val.isError(), is(false))).thenCancel().verify();
	}

	@Test
	public void ticker() {
		final ClockPolicyInformationPoint clockPip = new ClockPolicyInformationPoint();
		StepVerifier
				.withVirtualTime(() -> clockPip.ticker(Val.UNDEFINED, Collections.emptyMap(), Flux.just(Val.of(30L))))
				.expectSubscription().expectNoEvent(Duration.ofSeconds(30)).consumeNextWith(node -> {
					/* the first node is provided some nano seconds after its creation */
				}).expectNoEvent(Duration.ofSeconds(30)).consumeNextWith(node -> {
					var localDateTime = LocalDateTime.now();
					var actual = TemporalFunctionLibrary.atLocal(node).get().textValue();
					var expected = localDateTime.truncatedTo(ChronoUnit.SECONDS).toString();
					assertEquals(expected, actual);
				}).expectNoEvent(Duration.ofSeconds(30)).consumeNextWith(node -> {
					var localTime = LocalTime.now();
					var localNode = TemporalFunctionLibrary.atLocal(node);
					var actual = TemporalFunctionLibrary.localTime(localNode).get().textValue();
					var expected = localTime.truncatedTo(ChronoUnit.SECONDS).toString();
					assertEquals(expected, actual);
				}).expectNoEvent(Duration.ofSeconds(30)).consumeNextWith(node -> {
					var localTime = LocalTime.now();
					var localNode = TemporalFunctionLibrary.atLocal(node);
					var actual = TemporalFunctionLibrary.localHour(localNode).get().numberValue();
					var expected = BigDecimal.valueOf(localTime.getHour());
					assertEquals(expected.longValue(), actual.longValue());
				}).expectNoEvent(Duration.ofSeconds(30)).consumeNextWith(node -> {
					var localTime = LocalTime.now();
					var localNode = TemporalFunctionLibrary.atLocal(node);
					var actual = TemporalFunctionLibrary.localMinute(localNode).get().numberValue();
					var expected = BigDecimal.valueOf(localTime.getMinute());
					assertEquals(expected.longValue(), actual.longValue());
				}).expectNoEvent(Duration.ofSeconds(30)).consumeNextWith(node -> {
					var localTime = LocalTime.now();
					var localNode = TemporalFunctionLibrary.atLocal(node);
					var actual = TemporalFunctionLibrary.localSecond(localNode).get().numberValue();
					var expected = BigDecimal.valueOf(localTime.getSecond());
					assertEquals(expected.longValue(), actual.longValue());
				}).thenCancel().verify();
	}

	@Test
	void testConvertToZoneId() {
		var jsonNodeMock = mock(JsonNode.class);
		when(jsonNodeMock.asText()).thenReturn(null);

		var clockPip = new ClockPolicyInformationPoint();
		var now = clockPip.now(Val.of(jsonNodeMock), Collections.emptyMap()).blockFirst();

		assertThat(now, notNullValue());
		assertThat(now.getValType(), is(JsonNodeType.STRING.toString()));
	}

	@Test
	void testClockAfter() throws Exception {
		var clockPip = new ClockPolicyInformationPoint();

		var tomorrowAtMidnight = LocalDate.now().plusDays(1).atStartOfDay();
		var todayJustBeforeMidnight = tomorrowAtMidnight.minus(30, ChronoUnit.SECONDS);
		var time = Val.of("23:59:30");

		AtomicBoolean atomicBoolean = new AtomicBoolean(false);
		try (MockedStatic<LocalTime> mock = mockStatic(LocalTime.class, CALLS_REAL_METHODS)) {

			mock.when(LocalTime::now).thenAnswer((Answer<?>) invocation -> {
				var beforeMidnight = atomicBoolean.get();
				atomicBoolean.set(!beforeMidnight);

				if (beforeMidnight) {
					return todayJustBeforeMidnight.plusSeconds(5L).toLocalTime();
				}
				else {
					return tomorrowAtMidnight.plusSeconds(5L).toLocalTime();
				}
			});

			StepVerifier
					.withVirtualTime(() -> clockPip.after(SYSTEM_TIMEZONE_VAL, Collections.emptyMap(), Flux.just(time)))
					.expectSubscription().thenAwait(Duration.ofDays(1L)).consumeNextWith(val -> {
						// on same day just before midnight
						// -> clock should be slightly after reference time (23:59:30)
						var isAfter = val.getBoolean();
						assertThat(isAfter, is(true));
					}).consumeNextWith(val -> {
						// exactly at midnight
						// -> clock should definitely before reference time (23:59:30)
						var isAfter = val.getBoolean();
						assertThat(isAfter, is(false));
					}).thenAwait(Duration.ofDays(2L)).expectNextCount(4).expectNoEvent(Duration.ofHours(23))
					.thenAwait(Duration.ofDays(1L)).thenCancel().verify();
		}

	}

	@Test
	void testClockBefore() throws Exception {
		var clockPip = new ClockPolicyInformationPoint();

		var tomorrowAtMidnight = LocalDate.now().plusDays(1).atStartOfDay();
		var todayJustBeforeMidnight = tomorrowAtMidnight.minus(30, ChronoUnit.SECONDS);
		var time = Val.of("23:59:30");

		AtomicBoolean atomicBoolean = new AtomicBoolean(false);
		try (MockedStatic<LocalTime> mock = mockStatic(LocalTime.class, CALLS_REAL_METHODS)) {

			mock.when(LocalTime::now).thenAnswer((Answer<?>) invocation -> {
				var beforeMidnight = atomicBoolean.get();
				atomicBoolean.set(!beforeMidnight);

				if (beforeMidnight) {
					return todayJustBeforeMidnight.plusSeconds(5L).toLocalTime();
				}
				else {
					return tomorrowAtMidnight.plusSeconds(5L).toLocalTime();
				}
			});

			StepVerifier
					.withVirtualTime(
							() -> clockPip.before(SYSTEM_TIMEZONE_VAL, Collections.emptyMap(), Flux.just(time)))
					.expectSubscription().thenAwait(Duration.ofDays(1L)).consumeNextWith(val -> {
						// on same day just before midnight
						// -> clock should be slightly after reference time (23:59:30)
						var isAfter = val.getBoolean();
						assertThat(isAfter, is(false));
					}).consumeNextWith(val -> {
						// exactly at midnight
						// -> clock should definitely before reference time (23:59:30)
						var isAfter = val.getBoolean();
						assertThat(isAfter, is(true));
					}).thenAwait(Duration.ofDays(2L)).expectNextCount(4).expectNoEvent(Duration.ofHours(23))
					.thenAwait(Duration.ofDays(1L)).thenCancel().verify();
		}
	}

	@Test
	void timeZoneTest() {
		var clockPip = new ClockPolicyInformationPoint();
		var timeZone = clockPip.timeZone(Val.NULL, Collections.emptyMap()).blockFirst().getText();

		assertThat(timeZone, is(ZoneId.systemDefault().toString()));
	}

	@Test
	void nowTest() {
		var clockPip = new ClockPolicyInformationPoint();
		var now = OffsetDateTime.now().truncatedTo(ChronoUnit.MINUTES);
		var nowInSystemTimeZone = clockPip.now(SYSTEM_TIMEZONE_VAL, Collections.emptyMap()).blockFirst().getText();
		var offsetDateTime = DateTimeFormatter.ISO_DATE_TIME.parse(nowInSystemTimeZone, OffsetDateTime::from)
				.truncatedTo(ChronoUnit.MINUTES);

		assertThat(offsetDateTime.compareTo(now), is(0));
	}

	@Test
	void millisTest() {
		var clockPip = new ClockPolicyInformationPoint();
		var millis = clockPip.millis(SYSTEM_TIMEZONE_VAL, Collections.emptyMap()).blockFirst().get().numberValue()
				.longValue();

		assertThat(millis, is(greaterThanOrEqualTo(Instant.EPOCH.toEpochMilli())));
		assertThat(millis, is(lessThanOrEqualTo(System.currentTimeMillis())));
	}

	@Test
	void timerTest() {
		var clockPip = new ClockPolicyInformationPoint();
		var timerSeconds = 30L;

		StepVerifier
				.withVirtualTime(
						() -> clockPip.timer(Val.NULL, Collections.emptyMap(), Flux.just(Val.of(timerSeconds))))
				.expectSubscription().consumeNextWith(val -> {
					assertThat(val.getBoolean(), is(false));
				}).expectNoEvent(Duration.ofSeconds(timerSeconds)).thenAwait(Duration.ofSeconds(timerSeconds))
				.consumeNextWith(val -> {
					assertThat(val.getBoolean(), is(true));
				}).expectComplete().verify();
	}

}
