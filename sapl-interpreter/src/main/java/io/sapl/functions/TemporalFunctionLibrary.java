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

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionException;
import io.sapl.api.functions.FunctionLibrary;
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

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Function(docs = BEFORE_DOC)
	public static JsonNode before(@Text JsonNode timeOne, @Text JsonNode timeTwo) throws FunctionException {
		Instant t1 = nodeToInstant(timeOne);
		Instant t2 = nodeToInstant(timeTwo);
		return JSON.booleanNode(t1.isBefore(t2));
	}

	@Function(docs = AFTER_DOC)
	public static JsonNode after(@Text JsonNode timeOne, @Text JsonNode timeTwo) throws FunctionException {
		Instant t1 = nodeToInstant(timeOne);
		Instant t2 = nodeToInstant(timeTwo);
		return JSON.booleanNode(t1.isAfter(t2));
	}

	@Function(docs = BETWEEN_DOC)
	public static JsonNode between(@Text JsonNode time, @Text JsonNode timeOne, @Text JsonNode timeTwo)
			throws FunctionException {
		Instant t = nodeToInstant(time);
		Instant t1 = nodeToInstant(timeOne);
		Instant t2 = nodeToInstant(timeTwo);
		boolean result = t.equals(t1) || t.equals(t2) || (t.isBefore(t2) && t.isAfter(t1));
		return JSON.booleanNode(result);
	}

	@Function(docs = PLUSNANOS_DOC)
	public static JsonNode plusNanos(@Text JsonNode startTime, @Long JsonNode nanos) throws FunctionException {
		Instant time = nodeToInstant(startTime);
		long duration = nanos.asLong();
		return JSON.textNode(time.plusNanos(duration).toString());
	}

	@Function(docs = PLUSMILLIS_DOC)
	public static JsonNode plusMillis(@Text JsonNode startTime, @Long JsonNode millis) throws FunctionException {
		Instant time = nodeToInstant(startTime);
		long duration = millis.asLong();
		return JSON.textNode(time.plusMillis(duration).toString());
	}

	@Function(docs = PLUSSECONDS_DOC)
	public static JsonNode plusSeconds(@Text JsonNode startTime, @Long JsonNode seconds) throws FunctionException {
		Instant time = nodeToInstant(startTime);
		long duration = seconds.asLong();
		return JSON.textNode(time.plusSeconds(duration).toString());
	}

	@Function(docs = MINUSNANOS_DOC)
	public static JsonNode minusNanos(@Text JsonNode startTime, @Long JsonNode nanos) throws FunctionException {
		Instant time = nodeToInstant(startTime);
		long duration = nanos.asLong();
		return JSON.textNode(time.minusNanos(duration).toString());
	}

	@Function(docs = MINUSMILLIS_DOC)
	public static JsonNode minusMillis(@Text JsonNode startTime, @Long JsonNode millis) throws FunctionException {
		Instant time = nodeToInstant(startTime);
		long duration = millis.asLong();
		return JSON.textNode(time.minusMillis(duration).toString());
	}

	@Function(docs = MINUSSECONDS_DOC)
	public static JsonNode minusSeconds(@Text JsonNode startTime, @Long JsonNode seconds) throws FunctionException {
		Instant time = nodeToInstant(startTime);
		long duration = seconds.asLong();
		return JSON.textNode(time.minusSeconds(duration).toString());
	}

	@Function(docs = DAYOFWEEK_DOC)
	public static JsonNode dayOfWeekFrom(@Text JsonNode time) throws FunctionException {
		final Instant instant = nodeToInstant(time);
		final OffsetDateTime utc = instant.atOffset(ZoneOffset.UTC);
		return JSON.textNode(DayOfWeek.from(utc).toString());
	}

	@Function(docs = LOCAL_DATE_TIME_DOC)
	public static JsonNode localDateTime(@Text JsonNode utcDateTime) throws FunctionException {
		final Instant instant = nodeToInstant(utcDateTime);
		final LocalDateTime localDateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
		return JSON.textNode(localDateTime.truncatedTo(ChronoUnit.SECONDS).toString());
	}

	@Function(docs = LOCAL_TIME_DOC)
	public static JsonNode localTime(@Text JsonNode utcDateTime) throws FunctionException {
		final Instant instant = nodeToInstant(utcDateTime);
		final LocalTime localTime = instant.atZone(ZoneId.systemDefault()).toLocalTime();
		return JSON.textNode(localTime.truncatedTo(ChronoUnit.SECONDS).toString());
	}

	@Function(docs = LOCAL_HOUR_DOC)
	public static JsonNode localHour(@Text JsonNode utcDateTime) throws FunctionException {
		final Instant instant = nodeToInstant(utcDateTime);
		final LocalTime localTime = instant.atZone(ZoneId.systemDefault()).toLocalTime();
		return JSON.numberNode(BigDecimal.valueOf(localTime.getHour()));
	}

	@Function(docs = LOCAL_MINUTE_DOC)
	public static JsonNode localMinute(@Text JsonNode utcDateTime) throws FunctionException {
		final Instant instant = nodeToInstant(utcDateTime);
		final LocalTime localTime = instant.atZone(ZoneId.systemDefault()).toLocalTime();
		return JSON.numberNode(BigDecimal.valueOf(localTime.getMinute()));
	}

	@Function(docs = LOCAL_SECOND_DOC)
	public static JsonNode localSecond(@Text JsonNode utcDateTime) throws FunctionException {
		final Instant instant = nodeToInstant(utcDateTime);
		final LocalTime localTime = instant.atZone(ZoneId.systemDefault()).toLocalTime();
		return JSON.numberNode(BigDecimal.valueOf(localTime.getSecond()));
	}

	private static Instant nodeToInstant(JsonNode time) throws FunctionException {
		try {
			return Instant.parse(time.asText());
		}
		catch (DateTimeParseException e) {
			throw new FunctionException(PARAMETER_NOT_AN_ISO_8601_STRING, e);
		}
	}

}
