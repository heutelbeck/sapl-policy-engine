/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Long;
import io.sapl.api.validation.Text;

@FunctionLibrary(name = TemporalFunctionLibrary.NAME, description = TemporalFunctionLibrary.DESCRIPTION)
public class TemporalFunctionLibrary {

	public static final String NAME = "time";
	public static final String DESCRIPTION = "This library contains temporal functions.";
	private static final String BEFORE_DOC = "Given timeOne and timeTwo are strings representing UTC time in ISO 8601. The function returns true, if timeOne is before timeTwo.";
	private static final String AFTER_DOC = "Assumes, that TIME_A and TIME_B are strings representing UTC time in ISO 8601. Returns true, if TIME_A is after TIME_B.";
	private static final String BETWEEN_DOC = "between(TIME, TIME_A, TIME_B): Assumes, that TIME, TIME_A and TIME_B are strings representing UTC time in ISO 8601. Returns true, if TIME is between TIME_A and TIME_B.";
	private static final String PLUSNANOS_DOC = "plusNanos(TIME, NANOS): Assumes, that TIME is a string representing UTC time in ISO 8601, and NANOS is an integer. Returns a new time by adding the given duration to TIME.";
	private static final String PLUSMILLIS_DOC = "plusMillis(TIME, SECONDS): Assumes, that TIME is a string representing UTC time in ISO 8601, and MILLIS is an integer. Returns a new time by adding the given duration to TIME.";
	private static final String PLUSSECONDS_DOC = "plusSeconds(TIME, SECONDS): Assumes, that TIME is a string representing UTC time in ISO 8601, and SECONDS is an integer. Returns a new time by adding the given duration to TIME.";
	private static final String MINUSNANOS_DOC = "minusNanos(TIME, NANOS): Assumes, that TIME is a string representing UTC time in ISO 8601, and NANOS is an integer. Returns a new time by subtracting the given duration to TIME.";
	private static final String MINUSMILLIS_DOC = "minusMillis(TIME, SECONDS): Assumes, that TIME is a string representing UTC time in ISO 8601, and MILLIS is an integer. Returns a new time by subtracting the given duration to TIME.";
	private static final String MINUSSECONDS_DOC = "minusSeconds(TIME, SECONDS): Assumes, that TIME is a string representing UTC time in ISO 8601, and SECONDS is an integer. Returns a new time by subtracting the given duration to TIME.";
	private static final String DAYOFWEEK_DOC = "Returns the day of the week for the given time. Assumes, that the time is a string representing UTC time in ISO 8601.";
	private static final String LOCAL_DATE_TIME_DOC = "Returns the given date time converted to local date time as a string (without nano seconds). Assumes, that the given date time is a string representing UTC time in ISO 8601.";
	private static final String LOCAL_TIME_DOC = "Returns the given date time converted to local time as a string (without nano seconds). Assumes, that the given date time is a string representing UTC time in ISO 8601.";
	private static final String LOCAL_HOUR_DOC = "Returns the local hour of the given date time as a number. Assumes, that the given date time is a string representing UTC time in ISO 8601.";
	private static final String LOCAL_MINUTE_DOC = "Returns the local minute of the given date time as a number. Assumes, that the given date time is a string representing UTC time in ISO 8601.";
	private static final String LOCAL_SECOND_DOC = "Returns the (local) second of the given date time as a number. Assumes, that the given date time is a string representing UTC time in ISO 8601.";
	private static final String PARAMETER_NOT_AN_ISO_8601_STRING = "Parameter not an ISO 8601 string";

	@Function(docs = BEFORE_DOC)
	public static Val before(@Text Val timeOne, @Text Val timeTwo) {
		try {
			Instant t1 = nodeToInstant(timeOne);
			Instant t2 = nodeToInstant(timeTwo);
			return Val.of(t1.isBefore(t2));
		} catch (DateTimeException e) {
			return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
		}
	}

	@Function(docs = AFTER_DOC)
	public static Val after(@Text Val timeOne, @Text Val timeTwo) {
		try {
			Instant t1 = nodeToInstant(timeOne);
			Instant t2 = nodeToInstant(timeTwo);
			return Val.of(t1.isAfter(t2));
		} catch (DateTimeException e) {
			return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
		}
	}

	@Function(docs = BETWEEN_DOC)
	public static Val between(@Text Val time, @Text Val timeOne, @Text Val timeTwo) {
		try {
			Instant t = nodeToInstant(time);
			Instant t1 = nodeToInstant(timeOne);
			Instant t2 = nodeToInstant(timeTwo);
			boolean result = t.equals(t1) || t.equals(t2) || (t.isBefore(t2) && t.isAfter(t1));
			return Val.of(result);
		} catch (DateTimeException e) {
			return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
		}
	}

	@Function(docs = PLUSNANOS_DOC)
	public static Val plusNanos(@Text Val startTime, @Long Val nanos) {
		try {
			Instant time = nodeToInstant(startTime);
			long duration = nanos.get().asLong();
			return Val.of(time.plusNanos(duration).toString());
		} catch (DateTimeException e) {
			return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
		}
	}

	@Function(docs = PLUSMILLIS_DOC)
	public static Val plusMillis(@Text Val startTime, @Long Val millis) {
		try {
			Instant time = nodeToInstant(startTime);
			long duration = millis.get().asLong();
			return Val.of(time.plusMillis(duration).toString());
		} catch (DateTimeException e) {
			return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
		}
	}

	@Function(docs = PLUSSECONDS_DOC)
	public static Val plusSeconds(@Text Val startTime, @Long Val seconds) {
		try {
			Instant time = nodeToInstant(startTime);
			long duration = seconds.get().asLong();
			return Val.of(time.plusSeconds(duration).toString());
		} catch (DateTimeException e) {
			return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
		}
	}

	@Function(docs = MINUSNANOS_DOC)
	public static Val minusNanos(@Text Val startTime, @Long Val nanos) {
		try {
			Instant time = nodeToInstant(startTime);
			long duration = nanos.get().asLong();
			return Val.of(time.minusNanos(duration).toString());
		} catch (DateTimeException e) {
			return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
		}
	}

	@Function(docs = MINUSMILLIS_DOC)
	public static Val minusMillis(@Text Val startTime, @Long Val millis) {
		try {
			Instant time = nodeToInstant(startTime);
			long duration = millis.get().asLong();
			return Val.of(time.minusMillis(duration).toString());
		} catch (DateTimeException e) {
			return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
		}
	}

	@Function(docs = MINUSSECONDS_DOC)
	public static Val minusSeconds(@Text Val startTime, @Long Val seconds) {
		try {
			Instant time = nodeToInstant(startTime);
			long duration = seconds.get().asLong();
			return Val.of(time.minusSeconds(duration).toString());
		} catch (DateTimeException e) {
			return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
		}
	}

	@Function(docs = DAYOFWEEK_DOC)
	public static Val dayOfWeekFrom(@Text Val time) {
		try {
			final Instant instant = nodeToInstant(time);
			final OffsetDateTime utc = instant.atOffset(ZoneOffset.UTC);
			return Val.of(DayOfWeek.from(utc).toString());
		} catch (DateTimeException e) {
			return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
		}
	}

	@Function(docs = LOCAL_DATE_TIME_DOC)
	public static Val localDateTime(@Text Val utcDateTime) {
		try {
			final Instant instant = nodeToInstant(utcDateTime);
			final LocalDateTime localDateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
			return Val.of(localDateTime.truncatedTo(ChronoUnit.SECONDS).toString());
		} catch (DateTimeException e) {
			return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
		}
	}

	@Function(docs = LOCAL_TIME_DOC)
	public static Val localTime(@Text Val utcDateTime) {
		try {
			final Instant instant = nodeToInstant(utcDateTime);
			final LocalTime localTime = instant.atZone(ZoneId.systemDefault()).toLocalTime();
			return Val.of(localTime.truncatedTo(ChronoUnit.SECONDS).toString());
		} catch (DateTimeException e) {
			return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
		}
	}

	@Function(docs = LOCAL_HOUR_DOC)
	public static Val localHour(@Text Val utcDateTime) {
		try {
			final Instant instant = nodeToInstant(utcDateTime);
			final LocalTime localTime = instant.atZone(ZoneId.systemDefault()).toLocalTime();
			return Val.of(localTime.getHour());
		} catch (DateTimeException e) {
			return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
		}
	}

	@Function(docs = LOCAL_MINUTE_DOC)
	public static Val localMinute(@Text Val utcDateTime) {
		try {
			final Instant instant = nodeToInstant(utcDateTime);
			final LocalTime localTime = instant.atZone(ZoneId.systemDefault()).toLocalTime();
			return Val.of(localTime.getMinute());
		} catch (DateTimeException e) {
			return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
		}
	}

	@Function(docs = LOCAL_SECOND_DOC)
	public static Val localSecond(@Text Val utcDateTime) {
		try {
			final Instant instant = nodeToInstant(utcDateTime);
			final LocalTime localTime = instant.atZone(ZoneId.systemDefault()).toLocalTime();
			return Val.of(localTime.getSecond());
		} catch (DateTimeException e) {
			return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
		}
	}

	private static Instant nodeToInstant(Val time) {
		return Instant.parse(time.get().asText());
	}

}