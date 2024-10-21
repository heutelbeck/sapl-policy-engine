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
package io.sapl.functions;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.Locale;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Number;
import io.sapl.api.validation.Text;
import lombok.experimental.UtilityClass;

@UtilityClass
@FunctionLibrary(name = TemporalFunctionLibrary.NAME, description = TemporalFunctionLibrary.DESCRIPTION)
public class TemporalFunctionLibrary {

    /**
     * Library name and prefix
     */
    public static final String NAME = "time";

    /**
     * Library description
     */
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

    private static final String LOCAL_DATE_DOC = "Assumes, that TIME is a string representing time in ISO 8601. Returns TIME as LocalDate by dropping any offset and time.  Example: '2007-12-03'";

    private static final String LOCAL_TIME_DOC = "Assumes, that TIME is a string representing time in ISO 8601. Returns TIME as LocalTime by dropping any offset and offset. Example: '10:15:30'";

    private static final String HOUR_OF_DAY = "Returns the hour of the given date time as a number. Assumes, that the given date time is a string representing time in ISO 8601";

    private static final String MINUTE_OF_HOUR = "Returns the minute of the hour of the given date time as a number. Assumes, that the given date time is a string representing time in ISO 8601";

    private static final String SECOND_OF_MINUTE = "Returns the second of the minute of the given date time as a number. Assumes, that the given date time is a string representing time in ISO 8601";

    private static final String DAY_OF_YEAR = "Assumes, that TIME is a string representing UTC time in ISO 8601. Returns the day of the year. [1-366]";

    private static final String WEEK_OF_YEAR = "Assumes, that TIME is a string representing UTC time in ISO 8601. Returns the week of the year. [1-52]";

    private static final String DAY_OF_WEEK = "Assumes, that TIME is a string representing UTC time in ISO 8601. Returns the day of the week. [SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY]";

    private static final String VALID_UTC_DOC = "validUTC(TIME): Returns true, if TIME is a string representing UTC time in ISO 8601 such as '2011-12-03T10:15:30Z'.";

    private static final String DURATION_OF_SECONDS = "durationOfSeconds(SECONDS): Assumes, that SECONDS is a number. Returns the respective value in milliseconds";

    private static final String DURATION_OF_MINUTES = "durationOfSeconds(MINUTES): Assumes, that MINUTES is a number. Returns the respective value in milliseconds";

    private static final String DURATION_OF_HOURS = "durationOfSeconds(HOURS): Assumes, that HOURS is a number. Returns the respective value in milliseconds";

    private static final String DURATION_OF_DAYS = "durationOfSeconds(DAYS): Assumes, that DAYS is a number. Returns the respective value in milliseconds";

    private static final DateTimeFormatter DIN_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private static final DateTimeFormatter US_TIME_FORMATTER = new DateTimeFormatterBuilder().parseCaseInsensitive()
            .appendPattern("hh:mm:ss a").toFormatter(Locale.US);

    /* ######## DURATION ######## */

    @Function(docs = DURATION_OF_SECONDS)
    public static Val durationOfSeconds(@Number Val seconds) {
        return Val.of(seconds.getLong() * 1000);
    }

    @Function(docs = DURATION_OF_MINUTES)
    public static Val durationOfMinutes(@Number Val minutes) {
        return Val.of(minutes.getLong() * 60 * 1000);
    }

    @Function(docs = DURATION_OF_HOURS)
    public static Val durationOfHours(@Number Val hours) {
        return Val.of(hours.getLong() * 60 * 60 * 1000);
    }

    @Function(docs = DURATION_OF_DAYS)
    public static Val durationOfDays(@Number Val days) {
        return Val.of(days.getLong() * 24 * 60 * 60 * 1000);
    }

    /* ######## INSTANT/UTC COMPARISON ######## */

    @Function(docs = BEFORE_DOC)
    public static Val before(@Text Val timeA, @Text Val timeB) {
        return Val.of(instantOf(timeA).isBefore(instantOf(timeB)));
    }

    @Function(docs = AFTER_DOC)
    public static Val after(@Text Val timeA, @Text Val timeB) {
        return Val.of(instantOf(timeA).isAfter(instantOf(timeB)));
    }

    @Function(docs = BETWEEN_DOC)
    public static Val between(@Text Val time, @Text Val intervalStart, @Text Val intervalEnd) {
        final var t     = instantOf(time);
        final var start = instantOf(intervalStart);
        final var end   = instantOf(intervalEnd);

        if (t.equals(start))
            return Val.TRUE;
        else if (t.equals(end))
            return Val.TRUE;
        else
            return Val.of((t.isBefore(end) && t.isAfter(start)));
    }

    @Function(docs = TIME_BETWEEN_DOC)
    public static Val timeBetween(@Text Val timeA, @Text Val timeB, @Text Val chronoUnit) {
        final var unit        = ChronoUnit.valueOf(chronoUnit.getText().toUpperCase());
        final var instantFrom = instantOf(timeA);
        final var instantTo   = instantOf(timeB);
        try {
            return Val.of(unit.between(instantFrom, instantTo));
        } catch (UnsupportedTemporalTypeException e) {
            final var dateFrom = LocalDate.ofInstant(instantFrom, ZoneId.systemDefault());
            final var dateTo   = LocalDate.ofInstant(instantTo, ZoneId.systemDefault());
            return Val.of(unit.between(dateFrom, dateTo));
        }
    }

    /* ######## INSTANT/UTC MANIPULATION ######## */

    @Function(docs = PLUS_NANOS_DOC)
    public static Val plusNanos(@Text Val startTime, @Number Val nanos) {
        return Val.of(instantOf(startTime).plusNanos(nanos.getLong()).toString());
    }

    @Function(docs = PLUS_MILLIS_DOC)
    public static Val plusMillis(@Text Val startTime, @Number Val millis) {
        return Val.of(instantOf(startTime).plusMillis(millis.getLong()).toString());
    }

    @Function(docs = PLUS_SECONDS_DOC)
    public static Val plusSeconds(@Text Val startTime, @Number Val seconds) {
        return Val.of(instantOf(startTime).plusSeconds(seconds.getLong()).toString());
    }

    @Function(docs = MINUS_NANOS_DOC)
    public static Val minusNanos(@Text Val startTime, @Number Val nanos) {
        return Val.of(instantOf(startTime).minusNanos(nanos.getLong()).toString());
    }

    @Function(docs = MINUS_MILLIS_DOC)
    public static Val minusMillis(@Text Val startTime, @Number Val millis) {
        return Val.of(instantOf(startTime).minusMillis(millis.getLong()).toString());
    }

    @Function(docs = MINUS_SECONDS_DOC)
    public static Val minusSeconds(@Text Val startTime, @Number Val seconds) {
        return Val.of(instantOf(startTime).minusSeconds(seconds.getLong()).toString());
    }

    /* ######## INSTANT/UTC EPOCH ######## */

    @Function(docs = TO_EPOCH_SECONDS_DOC)
    public static Val epochSecond(@Text Val utcDateTime) {
        return Val.of(instantOf(utcDateTime).getEpochSecond());
    }

    @Function(docs = TO_EPOCH_MILLIS_DOC)
    public static Val epochMilli(@Text Val utcDateTime) {
        return Val.of(instantOf(utcDateTime).toEpochMilli());
    }

    @Function(docs = OF_EPOCH_SECONDS_DOC)
    public static Val ofEpochSecond(@Number Val epochSeconds) {
        return Val.of(Instant.ofEpochSecond(epochSeconds.getLong()).toString());
    }

    @Function(docs = OF_EPOCH_MILLIS_DOC)
    public static Val ofEpochMilli(@Number Val epochMillis) {
        return Val.of(Instant.ofEpochMilli(epochMillis.getLong()).toString());
    }

    /* ######## INSTANT/UTC CALENDAR ######## */

    @Function(docs = WEEK_OF_YEAR)
    public static Val weekOfYear(@Text Val isoDateTime) {
        return Val
                .of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText()).get(ChronoField.ALIGNED_WEEK_OF_YEAR));
    }

    @Function(docs = DAY_OF_YEAR)
    public static Val dayOfYear(@Text Val isoDateTime) {
        return Val.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText()).get(ChronoField.DAY_OF_YEAR));
    }

    @Function(docs = DAY_OF_WEEK)
    public static Val dayOfWeek(@Text Val isoDateTime) {
        return Val.of(DayOfWeek.from(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), LocalDateTime::from))
                .toString());
    }

    /* ######## VALIDATION ######## */

    @Function(docs = VALID_UTC_DOC)
    public static Val validUTC(@Text Val utcDateTime) {
        try {
            instantOf(utcDateTime);
            return Val.TRUE;
        } catch (DateTimeParseException e) {
            return Val.FALSE;
        }
    }

    /* ######## DATE CONVERSION ######## */

    @Function(docs = "Parses the given string as local date time (ISO) and converts it from system time zone to the respective time in UTC.")
    public static Val localIso(@Text Val localDateTime) {
        return Val.of(localDateTimeToInstant(
                parseLocalDateTime(localDateTime.getText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                ZoneId.systemDefault()).toString());
    }

    @Function(docs = "Parses the given string as local date time (DIN) and converts it from system time zone to the respective time in UTC.")
    public static Val localDin(@Text Val dinDateTime) {
        return Val.of(localDateTimeToInstant(parseLocalDateTime(dinDateTime.getText(), DIN_DATE_TIME_FORMATTER),
                ZoneId.systemDefault()).toString());
    }

    @Function(docs = "Parses the given string as local date time (ISO) and converts it from the given offset to the respective time in UTC.")
    public static Val dateTimeAtOffset(@Text Val localDateTime, @Text Val offsetId) {
        final var ldt = DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(localDateTime.getText(), LocalDateTime::from);
        final var odt = OffsetDateTime.of(ldt, ZoneOffset.of(offsetId.getText()));

        return Val.of(odt.withOffsetSameInstant(ZoneOffset.UTC).toInstant().toString());
    }

    @Function(docs = "Parses the given string as local date time (ISO) and converts it from the given time zone to the respective time in UTC.")
    public static Val dateTimeAtZone(@Text Val localDateTime, @Text Val zoneId) {
        final var ldt = DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(localDateTime.getText(), LocalDateTime::from);
        final var zdt = ZonedDateTime.of(ldt, zoneIdOf(zoneId));

        return Val.of(zdt.withZoneSameInstant(ZoneId.of("UTC")).toInstant().toString());
    }

    @Function(docs = "Parses the given string as an ISO date time with offset and converts it to the respective date time in UTC.")
    public static Val offsetDateTime(@Text Val isoDateTime) {
        final var offsetDateTime = DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), OffsetDateTime::from);
        return Val.of(offsetDateTime.withOffsetSameInstant(ZoneOffset.UTC).toInstant().toString());
    }

    /* ######## TIME CONVERSION ######## */

    @Function(docs = "Parses the given string as ISO time with offset and converts it to the respective time in UTC.")
    public static Val offsetTime(@Text Val isoTime) {
        OffsetTime offsetTime = DateTimeFormatter.ISO_TIME.parse(isoTime.getText(), OffsetTime::from);
        return Val.of(offsetTime.withOffsetSameInstant(ZoneOffset.UTC).toLocalTime().toString());
    }

    @Function(docs = "Parses the given string as local time at the given offset and converts it to the respective time in UTC.")
    public static Val timeAtOffset(@Text Val localTime, @Text Val offsetId) {
        LocalTime lt     = DateTimeFormatter.ISO_LOCAL_TIME.parse(localTime.getText(), LocalTime::from);
        final var offset = ZoneOffset.of(offsetId.getText());
        return Val.of(OffsetTime.of(lt, offset).withOffsetSameInstant(ZoneOffset.UTC).toLocalTime().toString());
    }

    @Function(docs = "Parses the given string as local time in the given zone and converts it to the respective time in UTC.")
    public static Val timeInZone(@Text Val localTime, @Text Val localDate, @Text Val zoneId) {
        final var zone = zoneIdOf(zoneId);
        LocalTime lt   = DateTimeFormatter.ISO_LOCAL_TIME.parse(localTime.getText(), LocalTime::from);

        ZonedDateTime zonedDateTime = ZonedDateTime.of(lt.atDate(LocalDate.parse(localDate.getText())), zone);
        return Val.of(zonedDateTime.withZoneSameInstant(ZoneId.of("UTC")).toLocalTime().toString());
    }

    // "08:30am", "09:30pm"
    @Function(docs = "Parses the given string as local time in AM/PM-format and converts it to 24-hour format.")
    public static Val timeAMPM(@Text Val timeInAMPM) {
        LocalTime lt = US_TIME_FORMATTER.parse(timeInAMPM.getText(), LocalTime::from);

        return Val.of(lt.toString());
    }

    /* ######## EXTRACT PARTS ######## */

    @Function(docs = LOCAL_DATE_DOC)
    public static Val dateOf(@Text Val isoDateTime) {
        return Val.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), LocalDate::from).toString());
    }

    @Function(docs = LOCAL_TIME_DOC)
    public static Val timeOf(@Text Val isoDateTime) {
        return Val.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), LocalTime::from)
                .truncatedTo(ChronoUnit.SECONDS).toString());
    }

    @Function(docs = HOUR_OF_DAY)
    public static Val hourOf(@Text Val isoDateTime) {
        return Val.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), LocalTime::from).getHour());
    }

    @Function(docs = MINUTE_OF_HOUR)
    public static Val minuteOf(@Text Val isoDateTime) {
        return Val.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), LocalTime::from).getMinute());
    }

    @Function(docs = SECOND_OF_MINUTE)
    public static Val secondOf(@Text Val isoDateTime) {
        return Val.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), LocalTime::from).getSecond());
    }

    private static Instant instantOf(Val time) {
        final var text = time.getText();
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            return LocalDate.parse(text).atStartOfDay().toInstant(ZoneOffset.UTC);
        }
    }

    private static ZoneId zoneIdOf(Val zone) {
        final var zoneIdStr = zone.getText().trim();
        if (zoneIdStr.isBlank())
            return ZoneId.systemDefault();

        if (ZoneId.SHORT_IDS.containsKey(zoneIdStr))
            return ZoneId.of(zoneIdStr, ZoneId.SHORT_IDS);

        return ZoneId.of(zoneIdStr);
    }

    private static Instant localDateTimeToInstant(LocalDateTime ldt, ZoneId zoneId) {
        return ldt.atZone(zoneId).toInstant();
    }

    private static LocalDateTime parseLocalDateTime(String localDateTimeString, DateTimeFormatter dtf) {
        return dtf.parse(localDateTimeString, LocalDateTime::from);
    }

}
