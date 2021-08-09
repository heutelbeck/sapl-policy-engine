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
package io.sapl.functions;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Long;
import io.sapl.api.validation.Number;
import io.sapl.api.validation.Text;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.zone.ZoneRulesException;

@FunctionLibrary(name = TemporalFunctionLibrary.NAME, description = TemporalFunctionLibrary.DESCRIPTION)
public class TemporalFunctionLibrary {

    public static final String NAME = "time";
    public static final String DESCRIPTION = "This library contains temporal functions.";

    private static final String BEFORE_DOC = "Assumes, that TIME_A and TIME_B are strings representing UTC time in ISO 8601. Returns true, if TIME_A is before TIME_B.";
    private static final String AFTER_DOC = "Assumes, that TIME_A and TIME_B are strings representing UTC time in ISO 8601. Returns true, if TIME_A is after TIME_B.";
    private static final String BETWEEN_DOC = "between(TIME, TIME_A, TIME_B): Assumes, that TIME, TIME_A and TIME_B are strings representing UTC time in ISO 8601. Returns true, if TIME is between TIME_A and TIME_B.";
    private static final String TIME_BETWEEN_DOC = "timeBetween(TIME_A, TIME_B, UNIT): Assumes, that TIME_A and TIME_B are strings representing UTC time in ISO 8601 and UNIT is a string containing a valid ChronoUnit. Returns the time, between TIME_A and TIME_B as number in the given unit.";

    private static final String PLUS_NANOS_DOC = "plusNanos(TIME, NANOS): Assumes, that TIME is a string representing UTC time in ISO 8601, and NANOS is an long integer. Returns a new time by adding the given duration to TIME.";
    private static final String PLUS_MILLIS_DOC = "plusMillis(TIME, MILLIS): Assumes, that TIME is a string representing UTC time in ISO 8601, and MILLIS is an long integer. Returns a new time by adding the given duration to TIME.";
    private static final String PLUS_SECONDS_DOC = "plusSeconds(TIME, SECONDS): Assumes, that TIME is a string representing UTC time in ISO 8601, and SECONDS is an long integer. Returns a new time by adding the given duration to TIME.";
    private static final String MINUS_NANOS_DOC = "minusNanos(TIME, NANOS): Assumes, that TIME is a string representing UTC time in ISO 8601, and NANOS is an long integer. Returns a new time by subtracting the given duration to TIME.";
    private static final String MINUS_MILLIS_DOC = "minusMillis(TIME, MILLIS): Assumes, that TIME is a string representing UTC time in ISO 8601, and MILLIS is an long integer. Returns a new time by subtracting the given duration to TIME.";
    private static final String MINUS_SECONDS_DOC = "minusSeconds(TIME, SECONDS): Assumes, that TIME is a string representing UTC time in ISO 8601, and SECONDS is an long integer. Returns a new time by subtracting the given duration to TIME.";

    private static final String TO_EPOCH_SECONDS_DOC = "Assumes, that TIME is a string representing UTC time in ISO 8601. Returns the number of seconds from the epoch of 1970-01-01T00:00:00Z.";
    private static final String TO_EPOCH_MILLIS_DOC = "Assumes, that TIME is a string representing UTC time in ISO 8601. Returns the number of milliseconds from the epoch of 1970-01-01T00:00:00Z.";
    private static final String OF_EPOCH_SECONDS_DOC = "Assumes, that SECONDS is a long representing the seconds from the epoch of 1970-01-01T00:00:00Z. Returns UTC time as String in ISO 8601 using.";
    private static final String OF_EPOCH_MILLIS_DOC = "Assumes, that MILLIS is a long representing the milliseconds from the epoch of 1970-01-01T00:00:00Z. Returns UTC time as String in ISO 8601 using.";

    private static final String LOCAL_DATE_TIME_DOC = "Assumes, that TIME is a string representing time in ISO 8601. Returns TIME as LocalDateTime by dropping any offset. Example: '2007-12-03T10:15:30'";
    private static final String LOCAL_DATE_DOC = "Assumes, that TIME is a string representing time in ISO 8601. Returns TIME as LocalDate by dropping any offset and time.  Example: '2007-12-03'";
    private static final String LOCAL_TIME_DOC = "Assumes, that TIME is a string representing time in ISO 8601. Returns TIME as LocalTime by dropping any offset and offset. Example: '10:15:30'";
    private static final String HOUR_OF_DAY = "Returns the hour of the given date time as a number. Assumes, that the given date time is a string representing time in ISO 8601";
    private static final String MINUTE_OF_HOUR = "Returns the minute of the hour of the given date time as a number. Assumes, that the given date time is a string representing time in ISO 8601";
    private static final String SECOND_OF_MINUTE = "Returns the second of the minute of the given date time as a number. Assumes, that the given date time is a string representing time in ISO 8601";

    private static final String AT_ZONED_DT_DOC = "toZonedDateTime(TIME, ZONE_ID): Assumes, that TIME is a string representing time in ISO 8601 and ZONE a string representing a valid zone id from the IANA Time Zone Database. Returns a ZonedDateTime formed from TIME at the specified TIMEZONE, as string in ISO 8601";
    private static final String AT_OFFSET_DT_DOC = "toOffsetDateTime(TIME, OFFSET_ID): Assumes, that TIME is a string representing time in ISO 8601 and OFFSET a ISO-8601 formatted string. Returns a OffsetDateTime formed from TIME at the specified OFFSET, as string in ISO 8601";
    private static final String At_LOCAL_DT_DOC = "toLocalDateTime(TIME): Assumes, that TIME is a string representing time in ISO 8601. Returns a LocalDateTime formed from TIME at the system's default zone, as string in ISO 8601";

    private static final String DAY_OF_YEAR = "Assumes, that TIME is a string representing UTC time in ISO 8601. Returns the day of the year. [1-366]";
    private static final String WEEK_OF_YEAR = "Assumes, that TIME is a string representing UTC time in ISO 8601. Returns the week of the year. [1-52]";
    private static final String DAY_OF_WEEK = "Assumes, that TIME is a string representing UTC time in ISO 8601. Returns the day of the week. [SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY]";

    private static final String VALID_UTC_DOC = "validUTC(TIME): Returns true, if TIME is a string representing UTC time in ISO 8601 such as '2011-12-03T10:15:30Z'.";
    private static final String VALID_ISO_DOC = "validISO(TIME): Returns true, if TIME is a string representing time in ISO 8601 (if available,with the offset and zone ), such as '2011-12-03T10:15:30', '2011-12-03T10:15:30+01:00' or '2011-12-03T10:15:30+01:00[Europe/Paris]'.";

    private static final String PARAMETER_NOT_AN_ISO_8601_STRING = "Parameter not an ISO 8601 string";
    private static final String PARAMETER_NOT_A_CHRONO_UNIT_STRING = "Parameter not an Chrono Unit string";
    private static final String PARAMETER_NOT_AN_ISO_8601_OFFSET_STRING = "Parameter not a zone id from the IANA Time Zone Database";
    private static final String PARAMETER_NOT_AN_IANA_ZONE_STRING = "Parameter not an ISO-8601 formatted offset";

    /* ######## INSTANT/UTC COMPARISON ######## */

    @Function(docs = BEFORE_DOC)
    public static Val before(@Text Val timeA, @Text Val timeB) {
        try {
            Instant t1 = nodeToInstant(timeA);
            Instant t2 = nodeToInstant(timeB);
            return Val.of(t1.isBefore(t2));
        } catch (DateTimeException e) {
            return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
        }
    }

    @Function(docs = AFTER_DOC)
    public static Val after(@Text Val timeA, @Text Val timeB) {
        try {
            Instant t1 = nodeToInstant(timeA);
            Instant t2 = nodeToInstant(timeB);
            return Val.of(t1.isAfter(t2));
        } catch (DateTimeException e) {
            return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
        }
    }

    @Function(docs = BETWEEN_DOC)
    public static Val between(@Text Val time, @Text Val timeA, @Text Val timeB) {
        try {
            Instant t = nodeToInstant(time);
            Instant t1 = nodeToInstant(timeA);
            Instant t2 = nodeToInstant(timeB);

            if (t.equals(t1))
                return Val.TRUE;
            else if (t.equals(t2))
                return Val.TRUE;
            else
                return Val.of((t.isBefore(t2) && t.isAfter(t1)));
        } catch (DateTimeException e) {
            return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
        }
    }

    @Function(docs = TIME_BETWEEN_DOC)
    public static Val timeBetween(@Text Val timeA, @Text Val timeB, @Text Val chronoUnit) {
        try {
            var unit = ChronoUnit.valueOf(chronoUnit.getText().toUpperCase());
            final Instant instantFrom = nodeToInstant(timeA);
            final Instant instantTo = nodeToInstant(timeB);
            return Val.of(instantFrom.until(instantTo, unit));
        } catch (IllegalArgumentException e) {
            return Val.error(PARAMETER_NOT_A_CHRONO_UNIT_STRING, e.getMessage());
        } catch (DateTimeException e) {
            return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
        }
    }

    /* ######## INSTANT/UTC MANIPULATION ######## */

    @Function(docs = PLUS_NANOS_DOC)
    public static Val plusNanos(@Text Val startTime, @Number Val nanos) {
        try {
            requireValue(nanos, Val::isNumber);
            Instant time = nodeToInstant(startTime);
            long duration = nanos.get().numberValue().longValue();
            return Val.of(time.plusNanos(duration).toString());
        } catch (IllegalArgumentException e) {
            return Val.error(e);
        } catch (DateTimeException e) {
            return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
        }
    }

    @Function(docs = PLUS_MILLIS_DOC)
    public static Val plusMillis(@Text Val startTime, @Number Val millis) {
        try {
            requireValue(millis, Val::isNumber);
            Instant time = nodeToInstant(startTime);
            long duration = millis.get().numberValue().longValue();
            return Val.of(time.plusMillis(duration).toString());
        } catch (IllegalArgumentException e) {
            return Val.error(e);
        } catch (DateTimeException e) {
            return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
        }
    }

    @Function(docs = PLUS_SECONDS_DOC)
    public static Val plusSeconds(@Text Val startTime, @Number Val seconds) {
        try {
            requireValue(seconds, Val::isNumber);
            Instant time = nodeToInstant(startTime);
            long duration = seconds.get().numberValue().longValue();
            return Val.of(time.plusSeconds(duration).toString());
        } catch (IllegalArgumentException e) {
            return Val.error(e);
        } catch (DateTimeException e) {
            return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
        }
    }

    @Function(docs = MINUS_NANOS_DOC)
    public static Val minusNanos(@Text Val startTime, @Number Val nanos) {
        try {
            requireValue(nanos, Val::isNumber);
            Instant time = nodeToInstant(startTime);
            long duration = nanos.get().numberValue().longValue();
            return Val.of(time.minusNanos(duration).toString());
        } catch (IllegalArgumentException e) {
            return Val.error(e);
        } catch (DateTimeException e) {
            return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
        }
    }

    @Function(docs = MINUS_MILLIS_DOC)
    public static Val minusMillis(@Text Val startTime, @Number Val millis) {
        try {
            requireValue(millis, Val::isNumber);
            Instant time = nodeToInstant(startTime);
            long duration = millis.get().numberValue().longValue();
            return Val.of(time.minusMillis(duration).toString());
        } catch (IllegalArgumentException e) {
            return Val.error(e);
        } catch (DateTimeException e) {
            return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
        }
    }

    @Function(docs = MINUS_SECONDS_DOC)
    public static Val minusSeconds(@Text Val startTime, @Number Val seconds) {
        try {
            requireValue(seconds, Val::isNumber);
            Instant time = nodeToInstant(startTime);
            long duration = seconds.get().numberValue().longValue();
            return Val.of(time.minusSeconds(duration).toString());
        } catch (IllegalArgumentException e) {
            return Val.error(e);
        } catch (DateTimeException e) {
            return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
        }
    }

    /* ######## INSTANT/UTC EPOCH ######## */

    @Function(docs = TO_EPOCH_SECONDS_DOC)
    public static Val toEpochSecond(@Text Val utcDateTime) {
        try {
            final Instant instant = nodeToInstant(utcDateTime);
            return Val.of(instant.getEpochSecond());
        } catch (DateTimeException e) {
            return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
        }
    }

    @Function(docs = TO_EPOCH_MILLIS_DOC)
    public static Val toEpochMillis(@Text Val utcDateTime) {
        try {
            final Instant instant = nodeToInstant(utcDateTime);
            return Val.of(instant.toEpochMilli());
        } catch (DateTimeException e) {
            return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
        }
    }

    @Function(docs = OF_EPOCH_SECONDS_DOC)
    public static Val ofEpochSeconds(@Long Val epochSeconds) {
        try {
            requireValue(epochSeconds, Val::isLong);
            return Val.of(Instant.ofEpochMilli(epochSeconds.get().asLong()).toString());
        } catch (DateTimeException | IllegalArgumentException e) {
            return Val.error(e);
        }
    }

    @Function(docs = OF_EPOCH_MILLIS_DOC)
    public static Val ofEpochMillis(@Long Val epochMillis) {
        try {
            requireValue(epochMillis, Val::isLong);
            return Val.of(Instant.ofEpochMilli(epochMillis.get().asLong()).toString());
        } catch (DateTimeException | IllegalArgumentException e) {
            return Val.error(e);
        }
    }

    /* ######## INSTANT/UTC CALENDAR ######## */

    @Function(docs = WEEK_OF_YEAR)
    public static Val weekOfYear(@Text Val isoDateTime) {
        try {
            return Val.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText()).get(ChronoField.ALIGNED_WEEK_OF_YEAR));
        } catch (DateTimeException e) {
            return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
        }
    }

    @Function(docs = DAY_OF_YEAR)
    public static Val dayOfYear(@Text Val isoDateTime) {
        try {
            return Val.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText()).get(ChronoField.DAY_OF_YEAR));
        } catch (DateTimeException e) {
            return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
        }
    }

    @Function(docs = DAY_OF_WEEK)
    public static Val dayOfWeek(@Text Val isoDateTime) {
        try {
            return Val.of(DayOfWeek.from(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), LocalDateTime::from)).toString());
        } catch (DateTimeException e) {
            return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
        }
    }

    /* ######## VALIDATION ######## */

    @Function(docs = VALID_UTC_DOC)
    public static Val validUTC(@Text Val utcDateTime) {
        try {
            nodeToInstant(utcDateTime);
            return Val.of(true);
        } catch (DateTimeException e) {
            return Val.of(false);
        }
    }

    @Function(docs = VALID_ISO_DOC)
    public static Val validISO(@Text Val isoDateTime) {
        try {
            DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText());
            return Val.of(true);
        } catch (DateTimeParseException e) {
            return Val.of(false);
        }
    }

    /* ######## CONVERSION ######## */

    @Function(docs = AT_ZONED_DT_DOC)
    public static Val atZone(@Text Val isoDateTime, @Text Val zoneId) {
        try {
            var zone = ZoneId.of(zoneId.getText());
            final Instant instant = nodeToInstant(isoDateTime);
            return Val.of(instant.atZone(zone).toString());
        } catch (ZoneRulesException e) {
            return Val.error(PARAMETER_NOT_AN_IANA_ZONE_STRING, e.getMessage());
        } catch (DateTimeException e) {
            return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
        }
    }

    @Function(docs = AT_OFFSET_DT_DOC)
    public static Val atOffset(@Text Val isoDateTime, @Text Val offsetId) {
        try {
            var offset = ZoneOffset.of(offsetId.getText());
            final Instant instant = nodeToInstant(isoDateTime);
            return Val.of(instant.atOffset(offset).toString());
        } catch (ZoneRulesException e) {
            return Val.error(PARAMETER_NOT_AN_ISO_8601_OFFSET_STRING, e.getMessage());
        } catch (DateTimeException e) {
            return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
        }
    }

    @Function(docs = At_LOCAL_DT_DOC)
    public static Val atLocal(@Text Val isoDateTime) {
        try {
            final Instant instant = nodeToInstant(isoDateTime);
            final LocalDateTime localDateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
            return Val.of(localDateTime.truncatedTo(ChronoUnit.SECONDS).toString());
        } catch (DateTimeException e) {
            return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
        }
    }

    /* ######## EXTRACT PARTS ######## */

    @Function(docs = LOCAL_DATE_TIME_DOC)
    public static Val localDateTime(@Text Val isoDateTime) {
        try {
            return Val.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), LocalDateTime::from).truncatedTo(ChronoUnit.SECONDS)
                    .toString());
        } catch (DateTimeException e) {
            return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
        }
    }

    @Function(docs = LOCAL_DATE_DOC)
    public static Val localDate(@Text Val isoDateTime) {
        try {
            return Val.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), LocalDate::from).toString());
        } catch (DateTimeException e) {
            return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
        }
    }

    @Function(docs = LOCAL_TIME_DOC)
    public static Val localTime(@Text Val isoDateTime) {
        try {
            return Val.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), LocalTime::from).truncatedTo(ChronoUnit.SECONDS).toString());
        } catch (DateTimeException e) {
            return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
        }
    }

    @Function(docs = HOUR_OF_DAY)
    public static Val localHour(@Text Val isoDateTime) {
        try {
            return Val.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), LocalTime::from).getHour());
        } catch (DateTimeException e) {
            return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
        }
    }

    @Function(docs = MINUTE_OF_HOUR)
    public static Val localMinute(@Text Val isoDateTime) {
        try {
            return Val.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), LocalTime::from).getMinute());
        } catch (DateTimeException e) {
            return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
        }
    }

    @Function(docs = SECOND_OF_MINUTE)
    public static Val localSecond(@Text Val isoDateTime) {
        try {
            return Val.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), LocalTime::from).getSecond());
        } catch (DateTimeException e) {
            return Val.error(PARAMETER_NOT_AN_ISO_8601_STRING, e.getMessage());
        }
    }

    private static void requireValue(Val nodeNotToBeNull, java.util.function.Function<Val, Boolean> validator) {
        if (nodeNotToBeNull == null || nodeNotToBeNull.isNull() || nodeNotToBeNull.isUndefined() || nodeNotToBeNull.get().isNull())
            throw new IllegalArgumentException("argument is null or undefined");

        if (!validator.apply(nodeNotToBeNull))
            throw new IllegalArgumentException("argument validation failed");
    }

    private static Instant nodeToInstant(Val time) {
        if (time.isNull() || time.isUndefined()) throw new DateTimeException("provided time value is null or undefined");

        return Instant.parse(time.get().asText());
    }

}