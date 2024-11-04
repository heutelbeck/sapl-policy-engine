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

    public static final String NAME        = "time";
    public static final String DESCRIPTION = """

            This library contains temporal functions. It relies on [ISO 8601](https://www.iso.org/iso-8601-date-and-time-format.html)
            and DIN 1355 standards for time representation. The latter has been officially withdrawn but continues to be used in practice.

            The most used variant format described in ISO 8601 is YYYY-MM-DD, e.g. "2017-10-28" for the 28th of October in the year 2017.
            DIN 1355 describes DD.MM.YYYY, e.g. "28.10.2017". Time format is consistently hh:mm:ss with 24 hours per day, e.g. "16:14:11".
            In ISO 8601 time and date can be joined into one string, e.g. "2017-10-28T16:14:11".

            Coordinated Universal Time [UTC](https://www.ipses.com/eng/in-depth-analysis/standard-of-time-definition/) is not based on the
            time of rotation of the earth. It is time zone zero while central European time has an offset of one hour.
            """;

    private static final DateTimeFormatter DIN_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final DateTimeFormatter US_TIME_FORMATTER       = new DateTimeFormatterBuilder()
            .parseCaseInsensitive().appendPattern("hh:mm:ss a").toFormatter(Locale.US);

    /* ######## DURATION ######## */

    @Function(docs = """
            ```durationOfSeconds(NUMBER seconds)```:
            For the temporal library, a duration is always defined in milliseconds. This function conversts ```seconds```
            to milliseconds, multiplying them by ```1000```.

            **Example:**

            The expression ```time.durationOfSeconds(20.5)``` will return ```20500```.
            """)
    public static Val durationOfSeconds(@Number Val seconds) {
        return Val.of(seconds.getLong() * 1000);
    }

    @Function(docs = """
            ```durationOfMinutes(NUMBER minutes)```:
            For the temporal library, a duration is always defined in milliseconds.
            This function conversts ```minutes``` to milliseconds, multiplying them by ```60000```.

            **Example:**

            The expression ```time.durationOfMinutes(2.5)``` will return ```1500000```.""")
    public static Val durationOfMinutes(@Number Val minutes) {
        return Val.of(minutes.getLong() * 60 * 1000);
    }

    @Function(docs = """
            ```durationOfHours(NUMBER hours)```:
            For the temporal library, a duration is always defined in milliseconds. This function conversts ```hours```
            to milliseconds, multiplying them by ```3600000```.

            **Example:**

            The expression ```time.durationOfHours(4.5)``` will return ```16200000```.""")
    public static Val durationOfHours(@Number Val hours) {
        return Val.of(hours.getLong() * 60 * 60 * 1000);
    }

    @Function(docs = """
            ```durationOfDays(NUMBER days)```:
            For the temporal library, a duration is always defined in milliseconds. This function conversts ```hours```
            to milliseconds, multiplying them by ```86400000```.

            **Example:**

            The expression ```time.durationOfDays(365)``` will return ```31536000000```.""")
    public static Val durationOfDays(@Number Val days) {
        return Val.of(days.getLong() * 24 * 60 * 60 * 1000);
    }

    /* ######## INSTANT/UTC COMPARISON ######## */

    @Function(docs = """
            ```before(TEXT timeA, TEXT timeB)```: This funtion compares two instants. Both, ```timeA``` and ```timeB```
            must be expressed as ISO 8601 strings at UTC.
            The function returns ```true```, if ```timeA``` is before ```timeB```.

            **Example:**

            The expression ```time.before("2021-11-08T13:00:00Z", "2021-11-08T13:00:01Z")``` returns ```true```.""")
    public static Val before(@Text Val timeA, @Text Val timeB) {
        return Val.of(instantOf(timeA).isBefore(instantOf(timeB)));
    }

    @Function(docs = """
            ```after(TEXT timeA, TEXT timeB)```: This funtion compares two instants. Both, ```timeA``` and ```timeB```
            must be expressed as ISO 8601 strings at UTC.
            The function returns ```true```, if ```timeA``` is after ```timeB```.

            **Example:**

            The expression ```time.before("2021-11-08T13:00:00Z", "2021-11-08T13:00:01Z")``` returns ```false```.""")
    public static Val after(@Text Val timeA, @Text Val timeB) {
        return Val.of(instantOf(timeA).isAfter(instantOf(timeB)));
    }

    @Function(docs = """
            ```between(TEXT time, TEXT intervalStart, TEXT intervalEnd)```:
            This funtion tests if ```time``` is inside of the closed interval defined by ```intervalStart``` and
            ```intervalEnd```, where ```intervalStart``` must be before ```intervalEnd```.
            All parameters must be expressed as ISO 8601 strings at UTC.

            The function returns ```true```, if ```time``` is inside of the closed interval defined by ```intervalStart```
            and ```intervalEnd```.

            **Example:**

            The expression ```time.between("2021-11-08T13:00:00Z", "2021-11-07T13:00:00Z", "2021-11-09T13:00:00Z")```
            returns ```true```.""")
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

    @Function(docs = """
            ```timeBetween(TEXT timeA, TEXT timeB, TEXT chronoUnit)```:
            This funtion calculates the timespan between ```timeA``` and ```timeB``` in the given ```chronoUnit```.
            All ```timeA```and ```timeB``` must be expressed as ISO 8601 strings at UTC.
            The ```chronoUnit``` can be one of:
            * NANOS: for nanoseconds.
            * MICROS: for microsecond.
            * MILLIS: for milliseconds.
            * SECONDS: for seconds.
            * MINUTES: for minutes
            * HOURS: for hours
            * HALF_DAYS: for ```12``` hours.
            * DAYS: for days.
            * WEEKS: for ```7``` days.
            * MONTHS: The duration of a month is estimated as one twelfth of ```365.2425``` days.
            * YEARS: The duration of a year is estimated as ```365.2425``` days.
            * DECADES: for ```10``` years.
            * CENTURIES: for ```100``` years.
            * MILLENNIA: for ```1000``` years.

            Example: The expression ```time.timeBetween("2001-01-01", "2002-01-01", "YEARS")``` returns ```1```.""")
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

    @Function(docs = """
            ```plusNanos(TEXT startTime, INTEGER nanos)```:
            This funtion adds ```nanos``` nanoseconds to ```startTime```.
            The parameter ```startTime``` must be expressed as ISO 8601 strings at UTC. And ```nanos``` must be an interger.

            **Example:**

            The expression ```time.plusNanos("2021-11-08T13:00:00Z", 10000000000)```
            returns ```"2021-11-08T13:00:10Z"```.""")
    public static Val plusNanos(@Text Val startTime, @Number Val nanos) {
        return Val.of(instantOf(startTime).plusNanos(nanos.getLong()).toString());
    }

    @Function(docs = """
            ```plusMillis(TEXT startTime, INTEGER millis)```:
            This funtion adds ```millis``` milliseconds to ```startTime```.
            The parameter ```startTime``` must be expressed as ISO 8601 strings at UTC. And ```millis``` must be an interger.

            **Example:**

            The expression ```time.plusMillis("2021-11-08T13:00:00Z", 10000)```
            returns ```"2021-11-08T13:00:10Z"```.""")
    public static Val plusMillis(@Text Val startTime, @Number Val millis) {
        return Val.of(instantOf(startTime).plusMillis(millis.getLong()).toString());
    }

    @Function(docs = """
            ```plusSeconds(TEXT startTime, INTEGER seconds)```:
            This funtion adds ```seconds``` seconds to ```startTime```.
            The parameter ```startTime``` must be expressed as ISO 8601 strings at UTC. And ```seconds``` must be an interger.

            **Example:**

            The expression ```time.plusSeconds("2021-11-08T13:00:00Z", 10)```
            returns ```"2021-11-08T13:00:10Z"```.""")
    public static Val plusSeconds(@Text Val startTime, @Number Val seconds) {
        return Val.of(instantOf(startTime).plusSeconds(seconds.getLong()).toString());
    }

    @Function(docs = """
            ```minusNanos(TEXT startTime, INTEGER nanos)```:
            This funtion substracts ```nanos``` nanoseconds from ```startTime```.
            The parameter ```startTime``` must be expressed as ISO 8601 strings at UTC. And ```nanos``` must be an interger.

            **Example:**

            The expression ```time.minusNanos("2021-11-08T13:00:00Z", 10000000000)```
            returns ```"2021-11-08T12:59:50Z"```.""")
    public static Val minusNanos(@Text Val startTime, @Number Val nanos) {
        return Val.of(instantOf(startTime).minusNanos(nanos.getLong()).toString());
    }

    @Function(docs = """
            ```minusMillis(TEXT startTime, INTEGER millis)```:
            This funtion substracts ```millis``` milliseconds from ```startTime```.
            The parameter ```startTime``` must be expressed as ISO 8601 strings at UTC. And ```millis``` must be an interger.

            **Example:**

            The expression ```time.minusMillis("2021-11-08T13:00:00Z", 10000)```
            returns ```"2021-11-08T12:59:50Z"```.""")
    public static Val minusMillis(@Text Val startTime, @Number Val millis) {
        return Val.of(instantOf(startTime).minusMillis(millis.getLong()).toString());
    }

    @Function(docs = """
            ```minusSeconds(TEXT startTime, INTEGER seconds)```:
            This funtion substracts ```seconds``` seconds from ```startTime```.
            The parameter ```startTime``` must be expressed as ISO 8601 strings at UTC. And ```seconds``` must be an interger.

            **Example:**

            The expression ```time.minusSeconds("2021-11-08T13:00:00Z", 10)```
            returns ```"2021-11-08T12:59:50Z"```.""")
    public static Val minusSeconds(@Text Val startTime, @Number Val seconds) {
        return Val.of(instantOf(startTime).minusSeconds(seconds.getLong()).toString());
    }

    /* ######## INSTANT/UTC EPOCH ######## */

    @Function(docs = """
            ```epochSecond(TEXT utcDateTime)```:
            This funtion converts an ISO 8601 string at UTC ```utcDateTime``` to the offset of this instant to the epoc date
            ```"1970-01-01T00:00:00Z"``` in seconds.

            **Example:**

            The expression ```time.epochSecond("2021-11-08T13:00:00Z")``` returns ```1636376400```.""")
    public static Val epochSecond(@Text Val utcDateTime) {
        return Val.of(instantOf(utcDateTime).getEpochSecond());
    }

    @Function(docs = """
            ```epochMilli(TEXT utcDateTime)```:
            This funtion converts an ISO 8601 string at UTC ```utcDateTime``` to the offset of this instant to the epoc date
            ```"1970-01-01T00:00:00Z"``` in milliseconds.

            **Example:**

            The expression ```time.epochMilli("2021-11-08T13:00:00Z")``` returns ```1636376400000```.""")
    public static Val epochMilli(@Text Val utcDateTime) {
        return Val.of(instantOf(utcDateTime).toEpochMilli());
    }

    @Function(docs = """
            ```epochSecond(INTEGER epochSeconds)```:
            This function converts an offset from the epoc date
            ```"1970-01-01T00:00:00Z"``` in seconds to an instant represented as an ISO 8601 string at UTC.

            **Example:**

            The expression ```time.epochSecond(1636376400)``` returns ```"2021-11-08T13:00:00Z"```.""")
    public static Val ofEpochSecond(@Number Val epochSeconds) {
        return Val.of(Instant.ofEpochSecond(epochSeconds.getLong()).toString());
    }

    @Function(docs = """
            ```ofEpochMilli(INTEGER epochSeconds)```:
            This function converts an offset from the epoc date
            ```"1970-01-01T00:00:00Z"``` in milliseconds to an instant represented as an ISO 8601 string at UTC.

            **Example:**

            The expression ```time.ofEpochMilli(1636376400000)``` returns ```"2021-11-08T13:00:00Z"```.""")
    public static Val ofEpochMilli(@Number Val epochMillis) {
        return Val.of(Instant.ofEpochMilli(epochMillis.getLong()).toString());
    }

    /* ######## INSTANT/UTC CALENDAR ######## */

    @Function(docs = """
            ```weekOfYear(TEXT utcDateTime)```:
            This function returns the number of the calendar week (1-52) of the year for any
            date represented as an ISO 8601 string at UTC.

            **Example:**

            The expression ```time.weekOfYear("2021-11-08T13:00:00Z")``` returns ```45```.""")
    public static Val weekOfYear(@Text Val isoDateTime) {
        return Val
                .of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText()).get(ChronoField.ALIGNED_WEEK_OF_YEAR));
    }

    @Function(docs = """
            ```dayOfYear(TEXT utcDateTime)```:
            This function returns the day (1-365) of the year for any
            date represented as an ISO 8601 string at UTC.

            **Example:**

            The expression ```time.dayOfYear("2021-11-08T13:00:00Z")``` returns ```312```.""")
    public static Val dayOfYear(@Text Val isoDateTime) {
        return Val.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText()).get(ChronoField.DAY_OF_YEAR));
    }

    @Function(docs = """
            ```dayOfWeek(TEXT utcDateTime)```:
            This function returns the name of the day for any date represented as an ISO 8601 string at UTC.
            The function returns one of: ```"SUNDAY"```, ```"MONDAY"```, ```"TUESDAY"```, ```"WEDNESDAY"```,
            ```"THURSDAY"```, ```"FRIDAY"```, ```"SATURDAY"```.

            **Example:**

            The expression ```time.dayOfWeek("2021-11-08T13:00:00Z")``` returns ```"MONDAY"```.""")
    public static Val dayOfWeek(@Text Val isoDateTime) {
        return Val.of(DayOfWeek.from(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), LocalDateTime::from))
                .toString());
    }

    /* ######## VALIDATION ######## */

    @Function(docs = """
            ```validUTC(TEXT utcDateTime)```:
            This function validates if a value is a string in ISO 8601 string at UTC.

            **Example:**

            The expression ```time.validUTC("2021-11-08T13:00:00Z")``` returns ```true```.
            The expression ```time.validUTC("20111-000:00Z")``` returns ```false```.""")
    public static Val validUTC(@Text Val utcDateTime) {
        try {
            instantOf(utcDateTime);
            return Val.TRUE;
        } catch (DateTimeParseException e) {
            return Val.FALSE;
        }
    }

    /* ######## DATE CONVERSION ######## */

    @Function(docs = """
            ```localIso(TEXT localDateTime)```: This function parses a date-time ISO 8601 string without an offset,
            such as ```"2011-12-03T10:15:30"``` while using the PDP's system default time zone.

            **Example:**

            In case the systems default time zone is ```Europe/Berlin``` the expression
            ```time.localIso("2021-11-08T13:00:00")``` returns ```"2021-11-08T12:00:00Z"```.""")
    public static Val localIso(@Text Val localDateTime) {
        return Val.of(localDateTimeToInstant(
                parseLocalDateTime(localDateTime.getText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                ZoneId.systemDefault()).toString());
    }

    @Function(docs = """
            ```localDin(TEXT dinDateTime)```: This function parses a DIN date-time string without an offset,
            such as ```"08.11.2021 13:00:00"``` while using the PDP's system default time zone. It returns an ISO 8601 string.

            **Example:**

            In case the systems default time zone is ```Europe/Berlin``` the expression
            ```time.localDin("08.11.2021 13:00:00")``` returns ```"2021-11-08T12:00:00Z"```.""")
    public static Val localDin(@Text Val dinDateTime) {
        return Val.of(localDateTimeToInstant(parseLocalDateTime(dinDateTime.getText(), DIN_DATE_TIME_FORMATTER),
                ZoneId.systemDefault()).toString());
    }

    @Function(docs = """
            ```localDin(TEXT dinDateTime)```: This function parses a DIN date-time string without an offset,
            such as ```"08.11.2021 13:00:00"``` while using the PDP's system default time zone. It returns an ISO 8601 string.

            **Example:**

            In case the systems default time zone is ```Europe/Berlin``` the expression
            ```time.localDin("08.11.2021 13:00:00")``` returns ```"2021-11-08T12:00:00Z"```.""")
    public static Val dateTimeAtOffset(@Text Val localDateTime, @Text Val offsetId) {
        final var ldt = DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(localDateTime.getText(), LocalDateTime::from);
        final var odt = OffsetDateTime.of(ldt, ZoneOffset.of(offsetId.getText()));
        return Val.of(odt.withOffsetSameInstant(ZoneOffset.UTC).toInstant().toString());
    }

    @Function(docs = """
            ```dateTimeAtZone(TEXT localDateTime, TEXT zoneId)```: This function parses an ISO 8601 date-time string and
            for the provided [```zoneId```](https://en.wikipedia.org/wiki/List_of_tz_database_time_zones)
            returns the matching ISO 8601 instant at UTC.

            **Example:**

            The expression ```time.dateTimeAtZone("2021-11-08T13:12:35", "Europe/Berlin")```
            returns ```"2021-11-08T12:12:35Z"```.""")
    public static Val dateTimeAtZone(@Text Val localDateTime, @Text Val zoneId) {
        final var ldt = DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(localDateTime.getText(), LocalDateTime::from);
        final var zdt = ZonedDateTime.of(ldt, zoneIdOf(zoneId));
        return Val.of(zdt.withZoneSameInstant(ZoneId.of("UTC")).toInstant().toString());
    }

    @Function(docs = """
            ```offsetDateTime(TEXT isoDateTime)```: This function parses an ISO 8601 date-time with an offset
            returns the matching ISO 8601 instant at UTC.

            **Example:**

            The expression ```time.offsetDateTime("2021-11-08T13:12:35+05:00")```
            returns ```"2021-11-08T08:12:35Z"```.""")
    public static Val offsetDateTime(@Text Val isoDateTime) {
        final var offsetDateTime = DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), OffsetDateTime::from);
        return Val.of(offsetDateTime.withOffsetSameInstant(ZoneOffset.UTC).toInstant().toString());
    }

    /* ######## TIME CONVERSION ######## */

    @Function(docs = """
            ```offsetTime(TEXT isoTime)```: This function parses an ISO 8601 time with an offset
            returns the matching time at UTC.

            **Example:**

            The expression ```time.offsetTime("13:12:35-05:00")``` returns ```"18:12:35"```.""")
    public static Val offsetTime(@Text Val isoTime) {
        final var offsetTime = DateTimeFormatter.ISO_TIME.parse(isoTime.getText(), OffsetTime::from);
        return Val.of(offsetTime.withOffsetSameInstant(ZoneOffset.UTC).toLocalTime().toString());
    }

    @Function(docs = """
            ```timeAtOffset(TEXT localTime, TEXT offsetId)```: This function parses a time ```localTime``` with a separate
            ```offsetId``` parameter and returns the matching time at UTC.

            **Example:**

            The expression ```time.timeAtOffset("13:12:35", "-05:00")``` returns ```"18:12:35"```.""")
    public static Val timeAtOffset(@Text Val localTime, @Text Val offsetId) {
        final var lt     = DateTimeFormatter.ISO_LOCAL_TIME.parse(localTime.getText(), LocalTime::from);
        final var offset = ZoneOffset.of(offsetId.getText());
        return Val.of(OffsetTime.of(lt, offset).withOffsetSameInstant(ZoneOffset.UTC).toLocalTime().toString());
    }

    @Function(docs = """
            ```timeInZone(TEXT localTime, TEXT localDate, TEXT offsetId)```: This function parses a time ```localTime``` and
            date ```localDate``` with a separate ```offsetId`` parameter and returns the returns the matching time at UTC.

            **Example:**

            The expression ```time.timeInZone("13:12:35", "2022-01-14", "US/Pacific")``` returns ```"21:12:35"```.""")
    public static Val timeInZone(@Text Val localTime, @Text Val localDate, @Text Val zoneId) {
        final var zone          = zoneIdOf(zoneId);
        final var lt            = DateTimeFormatter.ISO_LOCAL_TIME.parse(localTime.getText(), LocalTime::from);
        final var zonedDateTime = ZonedDateTime.of(lt.atDate(LocalDate.parse(localDate.getText())), zone);
        return Val.of(zonedDateTime.withZoneSameInstant(ZoneId.of("UTC")).toLocalTime().toString());
    }

    @Function(docs = """
            ```timeAMPM(TEXT timeInAMPM)```:
            This function parses the given string ```timeInAMPM``` as local time in AM/PM-format
            and converts it to 24-hour format.

            **Example:**

            The expression ```time.timeAMPM("08:12:35 PM")``` returns ```"20:12:35"```.""")
    public static Val timeAMPM(@Text Val timeInAMPM) {
        final var lt = US_TIME_FORMATTER.parse(timeInAMPM.getText(), LocalTime::from);
        return Val.of(lt.toString());
    }

    /* ######## EXTRACT PARTS ######## */

    @Function(docs = """
            ```dateOf(TEXT isoDateTime)```:
            This function funtion returns the date part of the ISO 8601 string ```isoDateTime```.

            **Example:**

            The expression ```time.dateOf("2021-11-08T13:00:00Z")``` returns ```"2021-11-08"```.""")
    public static Val dateOf(@Text Val isoDateTime) {
        return Val.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), LocalDate::from).toString());
    }

    @Function(docs = """
            ```timeOf(TEXT isoDateTime)```:
            This function funtion returns the local time of the ISO 8601 string ```isoDateTime```, truncated to seconds.

            **Example:**

            The expression ```time.timeOf("2021-11-08T13:00:00Z")``` returns ```"13:00"```.""")
    public static Val timeOf(@Text Val isoDateTime) {
        return Val.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), LocalTime::from)
                .truncatedTo(ChronoUnit.SECONDS).toString());
    }

    @Function(docs = """
            ```hourOf(TEXT isoDateTime)```:
            This function funtion returns the hour of the ISO 8601 string ```isoDateTime```.

            **Example:**

            The expression ```time.hourOf("2021-11-08T13:17:23Z")``` returns ```13```.""")
    public static Val hourOf(@Text Val isoDateTime) {
        return Val.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), LocalTime::from).getHour());
    }

    @Function(docs = """
            ```minuteOf(TEXT isoDateTime)```:
            This function funtion returns the minute of the ISO 8601 string ```isoDateTime```.

            **Example:**

            The expression ```time.minuteOf("2021-11-08T13:17:23Z")``` returns ```17```.""")
    public static Val minuteOf(@Text Val isoDateTime) {
        return Val.of(DateTimeFormatter.ISO_DATE_TIME.parse(isoDateTime.getText(), LocalTime::from).getMinute());
    }

    @Function(docs = """
            ```secondOf(TEXT isoDateTime)```:
            This function funtion returns the second of the ISO 8601 string ```isoDateTime```.

            **Example:**

            The expression ```time.secondOf("2021-11-08T13:00:23Z")``` returns ```23```.""")
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
