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
package io.sapl.functions;

import static io.sapl.hamcrest.Matchers.val;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.sapl.api.interpreter.Val;

class TemporalFunctionLibraryTest {

	@Test
	void instantiate() {
		assertDoesNotThrow(() -> new TemporalFunctionLibrary());
	}

	@Test
	void nowPlusNanos() {
		var time = timeValOf("2021-11-08T13:00:00Z");
		var added = TemporalFunctionLibrary.plusNanos(time, Val.of(10_000_000_000L));
		assertThat(added, is(val("2021-11-08T13:00:10Z")));
	}

	@Test
	void nowPlusMillis() {
		var time = timeValOf("2021-11-08T13:00:00Z");
		var added = TemporalFunctionLibrary.plusMillis(time, Val.of(10_000L));
		assertThat(added, is(val("2021-11-08T13:00:10Z")));
	}

	@Test
	void nowPlusSeconds() {
		var time = timeValOf("2021-11-08T13:00:00Z");
		var added = TemporalFunctionLibrary.plusSeconds(time, Val.of(10L));
		assertThat(added, is(val("2021-11-08T13:00:10Z")));
	}

	@Test
	void nowMinusNanos() {
		var time = timeValOf("2021-11-08T13:00:00Z");
		var subtracted = TemporalFunctionLibrary.minusNanos(time, Val.of(10_000_000_000L));
		assertThat(subtracted, is(val("2021-11-08T12:59:50Z")));
	}

	@Test
	void nowMinusMillis() {
		var time = timeValOf("2021-11-08T13:00:00Z");
		var subtracted = TemporalFunctionLibrary.minusMillis(time, Val.of(10_000L));
		assertThat(subtracted, is(val("2021-11-08T12:59:50Z")));
	}

	@Test
	void nowMinusSeconds() {
		var time = timeValOf("2021-11-08T13:00:00Z");
		var subtracted = TemporalFunctionLibrary.minusSeconds(time, Val.of(10L));
		assertThat(subtracted, is(val("2021-11-08T12:59:50Z")));
	}

	@Test
	void dayOfWeekTest() {
		assertThat(TemporalFunctionLibrary.dayOfWeek(timeValOf("2021-11-08T13:00:00Z")), is(val("MONDAY")));
	}

	@Test
	void dayOfYearTest() {
		assertThat(TemporalFunctionLibrary.dayOfYear(timeValOf("2021-11-08T13:00:00Z")), is(val(312)));
	}

	@Test
	void weekOfYearTest() {
		assertThat(TemporalFunctionLibrary.weekOfYear(timeValOf("2021-11-08T13:00:00Z")), is(val(45)));
	}

	@Test
	void betweenTest() {
		var today = timeValOf("2021-11-08T13:00:00Z");
		var yesterday = timeValOf("2021-11-07T13:00:00Z");
		var tomorrow = timeValOf("2021-11-09T13:00:00Z");

		assertThat(TemporalFunctionLibrary.between(today, yesterday, tomorrow), is(val(true)));
		assertThat(TemporalFunctionLibrary.between(tomorrow, yesterday, today), is(val(false)));
		assertThat(TemporalFunctionLibrary.between(today, today, today), is(val(true)));
		assertThat(TemporalFunctionLibrary.between(yesterday, yesterday, tomorrow), is(val(true)));
		assertThat(TemporalFunctionLibrary.between(yesterday, today, tomorrow), is(val(false)));
		assertThat(TemporalFunctionLibrary.between(tomorrow, today, tomorrow), is(val(true)));
	}

	@Test
	void timeBetweenTest() {
		var today = timeValOf("2021-11-08T13:00:00Z");
		var tomorrow = timeValOf("2021-11-09T13:00:00Z");

		assertThat(TemporalFunctionLibrary.timeBetween(today, tomorrow, Val.of("DAYS")), is(val(1L)));
		assertThat(TemporalFunctionLibrary.timeBetween(today, tomorrow, Val.of("HOURS")), is(val(24L)));
		assertThat(TemporalFunctionLibrary.timeBetween(tomorrow, today, Val.of("DAYS")), is(val(-1L)));
		assertThat(TemporalFunctionLibrary.timeBetween(tomorrow, today, Val.of("HOURS")), is(val(-24L)));
	}

	@Test
	void beforeTest() {
		assertThat(TemporalFunctionLibrary.before(timeValOf("2021-11-08T13:00:00Z"), timeValOf("2021-11-08T13:00:01Z")),
				is(val(true)));
		assertThat(TemporalFunctionLibrary.before(timeValOf("2021-11-08T13:00:01Z"), timeValOf("2021-11-08T13:00:00Z")),
				is(val(false)));
	}

	@Test
	void afterTest() {
		assertThat(TemporalFunctionLibrary.after(timeValOf("2021-11-08T13:00:00Z"), timeValOf("2021-11-08T13:00:01Z")),
				is(val(false)));
		assertThat(TemporalFunctionLibrary.after(timeValOf("2021-11-08T13:00:01Z"), timeValOf("2021-11-08T13:00:00Z")),
				is(val(true)));
	}

	@Test
	void dateOfTest() {
		assertThat(TemporalFunctionLibrary.dateOf(timeValOf("2021-11-08T13:00:00Z")), is(val("2021-11-08")));
	}

	@Test
	void timeOfTest() {
		assertThat(TemporalFunctionLibrary.timeOf(timeValOf("2021-11-08T13:00:00Z")), is(val("13:00")));
	}

	@Test
	void hourOfTest() {
		assertThat(TemporalFunctionLibrary.hourOf(timeValOf("2021-11-08T13:00:00Z")), is(val(13)));
	}

	@Test
	void minuteOfTest() {
		assertThat(TemporalFunctionLibrary.minuteOf(timeValOf("2021-11-08T13:00:00Z")), is(val(0)));
	}

	@Test
	void secondOfTest() {
		assertThat(TemporalFunctionLibrary.secondOf(timeValOf("2021-11-08T13:00:23Z")), is(val(23)));
	}

	@Test
	void epocSecondTest() {
		assertThat(TemporalFunctionLibrary.epochSecond(timeValOf("2021-11-08T13:00:00Z")), is(val(1_636_376_400L)));
	}

	@Test
	void ofEpocSecondTest() {
		assertThat(TemporalFunctionLibrary.ofEpochSecond(Val.of(1_636_376_400L)), is(val("2021-11-08T13:00:00Z")));
	}

	@Test
	void epocMilliTest() {
		assertThat(TemporalFunctionLibrary.epochMilli(timeValOf("2021-11-08T13:00:00Z")), is(val(1_636_376_400_000L)));
	}

	@Test
	void ofEpocMilliTest() {
		assertThat(TemporalFunctionLibrary.ofEpochMilli(Val.of(1_636_376_400_000L)), is(val("2021-11-08T13:00:00Z")));
	}

	@Test
	void durationOfSecondsTest() {
		assertThat(TemporalFunctionLibrary.durationOfSeconds(Val.of(60)), is(val(60_000L)));
	}

	@Test
	void durationOfMinutes() {
		assertThat(TemporalFunctionLibrary.durationOfMinutes(Val.of(1)), is(val(60_000L)));
	}

	@Test
	void durationOfHours() {
		assertThat(TemporalFunctionLibrary.durationOfHours(Val.of(24)), is(val(86_400_000L)));
	}

	@Test
	void durationOfDays() {
		assertThat(TemporalFunctionLibrary.durationOfDays(Val.of(1)), is(val(86_400_000L)));
	}

	@Test
	void validUtcTest() {
		assertThat(TemporalFunctionLibrary.validUTC(Val.of("2021-11-08T13:00:00Z")), is(val(true)));
		assertThat(TemporalFunctionLibrary.validUTC(Val.of("XXX")), is(val(false)));
	}

	@Test
	void localToIsoTest() {
		LocalDateTime ldt = LocalDateTime.of(2021, 11, 8, 13, 0, 0);
		ZonedDateTime zdt = ZonedDateTime.of(ldt, ZoneId.systemDefault());

		assertThat(TemporalFunctionLibrary.localIso(Val.of("2021-11-08T13:00:00")),
				is(val(zdt.toInstant().toString())));
	}

	@Test
	void dinToIsoTest() {
		LocalDateTime ldt = LocalDateTime.of(2021, 11, 8, 13, 0, 0);
		ZonedDateTime zdt = ZonedDateTime.of(ldt, ZoneId.systemDefault());

		assertThat(TemporalFunctionLibrary.localDin(Val.of("08.11.2021 13:00:00")),
				is(val(zdt.toInstant().toString())));
	}

	@Test
	void dateTimeAtOffsetTest() {
		assertThat(TemporalFunctionLibrary.dateTimeAtOffset(Val.of("2021-11-08T13:12:35"), Val.of("+05:00")),
				is(val("2021-11-08T08:12:35Z")));
	}

	@Test
	void dateTimeAtZoneTest() {
		assertThat(TemporalFunctionLibrary.dateTimeAtZone(Val.of("2021-11-08T13:12:35"), Val.of("Europe/Berlin")),
				is(val("2021-11-08T12:12:35Z")));
	}

	@Test
	void offsetDateTimeTest() {
		assertThat(TemporalFunctionLibrary.offsetDateTime(Val.of("2021-11-08T13:12:35+05:00")),
				is(val("2021-11-08T08:12:35Z")));
	}

	@Test
	void offsetTimeTest() {
		assertThat(TemporalFunctionLibrary.offsetTime(Val.of("13:12:35+05:00")), is(val("08:12:35")));
		assertThat(TemporalFunctionLibrary.offsetTime(Val.of("13:12:35-05:00")), is(val("18:12:35")));
	}

	@Test
	void timeAtOffsetTest() {
		assertThat(TemporalFunctionLibrary.timeAtOffset(Val.of("13:12:35"), Val.of("+05:00")), is(val("08:12:35")));
		assertThat(TemporalFunctionLibrary.timeAtOffset(Val.of("13:12:35"), Val.of("-05:00")), is(val("18:12:35")));
	}

	@Test
	void timeAtZoneTest() {
		assertThat(TemporalFunctionLibrary.timeInZone(Val.of("13:12:35"), Val.of("US/Pacific")), is(val("21:12:35")));
		assertThat(TemporalFunctionLibrary.timeInZone(Val.of("13:12:35"), Val.of("Europe/Samara")),
				is(val("09:12:35")));
	}

	@Test
	void timeAMPMTest() {
		assertThat(TemporalFunctionLibrary.timeAMPM(Val.of("08:12:35 am")), is(val("08:12:35")));
		assertThat(TemporalFunctionLibrary.timeAMPM(Val.of("08:12:35 AM")), is(val("08:12:35")));
		assertThat(TemporalFunctionLibrary.timeAMPM(Val.of("08:12:35 pm")), is(val("20:12:35")));
		assertThat(TemporalFunctionLibrary.timeAMPM(Val.of("08:12:35 PM")), is(val("20:12:35")));
	}

	@Test
	void zoneIdOfTest() {
		var defaultZoneId = ZoneId.of("AET", ZoneId.SHORT_IDS);
		try (MockedStatic<ZoneId> zoneIdMock = Mockito.mockStatic(ZoneId.class, Mockito.CALLS_REAL_METHODS)) {
			zoneIdMock.when(() -> ZoneId.systemDefault()).thenReturn(defaultZoneId);
			assertThat(TemporalFunctionLibrary.dateTimeAtZone(Val.of("2021-11-08T13:12:35"), Val.of("")),
					is(val("2021-11-08T02:12:35Z")));
		}
		assertThat(TemporalFunctionLibrary.dateTimeAtZone(Val.of("2021-11-08T13:12:35"), Val.of("EST")),
				is(val("2021-11-08T18:12:35Z")));
		assertThat(TemporalFunctionLibrary.dateTimeAtZone(Val.of("2021-11-08T13:12:35"), Val.of("+06")),
				is(val("2021-11-08T07:12:35Z")));
	}

	@Test
	void should_return_error_for_invalid_time_arguments() {
		assertThrowsForTwoArgs(TemporalFunctionLibrary::before);
		assertThrowsForTwoArgs(TemporalFunctionLibrary::after);
		assertThrowsForTwoArgs(TemporalFunctionLibrary::plusNanos);
		assertThrowsForTwoArgs(TemporalFunctionLibrary::plusMillis);
		assertThrowsForTwoArgs(TemporalFunctionLibrary::plusSeconds);
		assertThrowsForTwoArgs(TemporalFunctionLibrary::minusNanos);
		assertThrowsForTwoArgs(TemporalFunctionLibrary::minusMillis);
		assertThrowsForTwoArgs(TemporalFunctionLibrary::minusSeconds);

		assertErrorValIsReturnedOneArg(TemporalFunctionLibrary::epochSecond);
		assertErrorValIsReturnedOneArg(TemporalFunctionLibrary::epochMilli);
		assertErrorValIsReturnedOneArg(TemporalFunctionLibrary::ofEpochSecond);
		assertErrorValIsReturnedOneArg(TemporalFunctionLibrary::ofEpochMilli);

		assertErrorValIsReturnedOneArg(TemporalFunctionLibrary::weekOfYear);
		assertErrorValIsReturnedOneArg(TemporalFunctionLibrary::dayOfYear);
		assertErrorValIsReturnedOneArg(TemporalFunctionLibrary::dayOfWeek);

		assertErrorValIsReturnedOneArg(TemporalFunctionLibrary::dateOf);
		assertErrorValIsReturnedOneArg(TemporalFunctionLibrary::timeOf);
		assertErrorValIsReturnedOneArg(TemporalFunctionLibrary::hourOf);
		assertErrorValIsReturnedOneArg(TemporalFunctionLibrary::minuteOf);
		assertErrorValIsReturnedOneArg(TemporalFunctionLibrary::secondOf);

		assertThrows(Exception.class,
				() -> TemporalFunctionLibrary.between(Val.NULL, Val.of((BigDecimal) null), Val.of((String) null)));
		assertThrows(Exception.class,
				() -> TemporalFunctionLibrary.between(Val.UNDEFINED, Val.UNDEFINED, Val.UNDEFINED));
		assertThrows(Exception.class,
				() -> TemporalFunctionLibrary.between(Val.of("abc"), Val.of("def"), Val.of("ghi")));

		assertThrows(Exception.class,
				() -> TemporalFunctionLibrary.timeBetween(Val.NULL, Val.of((BigDecimal) null), Val.of((String) null)));
		assertThrows(Exception.class,
				() -> TemporalFunctionLibrary.timeBetween(Val.UNDEFINED, Val.UNDEFINED, Val.UNDEFINED));
		assertThrows(Exception.class,
				() -> TemporalFunctionLibrary.timeBetween(Val.of("abc"), Val.of("def"), Val.of("ghi")));

		assertThat(TemporalFunctionLibrary.validUTC(Val.NULL).getBoolean(), is(false));
		assertThat(TemporalFunctionLibrary.validUTC(Val.of((String) null)).getBoolean(), is(false));
		assertThat(TemporalFunctionLibrary.validUTC(Val.UNDEFINED).getBoolean(), is(false));
		assertThat(TemporalFunctionLibrary.validUTC(Val.of("abc")).getBoolean(), is(false));
	}

	private void assertErrorValIsReturnedOneArg(Function<Val, Val> function) {
		assertThrows(Exception.class, () -> function.apply(Val.NULL));
		assertThrows(Exception.class, () -> function.apply(Val.of((String) null)));
		assertThrows(Exception.class, () -> function.apply(Val.UNDEFINED));
		assertThrows(Exception.class, () -> function.apply(Val.of("abc")));
	}

	private void assertThrowsForTwoArgs(BiFunction<Val, Val, Val> function) {
		assertThrows(Exception.class, () -> function.apply(Val.NULL, Val.of((String) null)));
		assertThrows(Exception.class, () -> function.apply(Val.UNDEFINED, Val.UNDEFINED));
		assertThrows(Exception.class, () -> function.apply(Val.of("abc"), Val.of("def")));
	}

	private static Val timeValOf(String utcIsoTime) {
		return Val.of(Instant.parse(utcIsoTime).toString());
	}

}
