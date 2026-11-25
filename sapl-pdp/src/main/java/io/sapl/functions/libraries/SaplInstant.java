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
package io.sapl.functions.libraries;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;
import lombok.val;

/** Strict certificate timestamps and historical ISO calendar timestamps for policy time inputs. */
@UtilityClass
public class SaplInstant {
    private static final String ERROR_INVALID_LEAP_SECOND = "A leap second requires the last minute of the UTC day.";

    private static final DateTimeFormatter DATE                 = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4).appendLiteral('-').appendValue(ChronoField.MONTH_OF_YEAR, 2)
            .appendLiteral('-').appendValue(ChronoField.DAY_OF_MONTH, 2).toFormatter(Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter POLICY_DATE          = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4, 4, SignStyle.NORMAL).appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral('-').appendValue(ChronoField.DAY_OF_MONTH, 2)
            .toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter TIMESTAMP            = timestampFormatter(DATE);
    private static final DateTimeFormatter POLICY_TIMESTAMP     = timestampFormatter(POLICY_DATE);
    private static final Pattern           NEGATIVE_POLICY_YEAR = Pattern.compile("-(?:[0-9]{4}|00[0-9]{4})(?=-)");

    private static final String  ERROR_TIMESTAMP     = "Invalid timestamp. Expected an ISO 8601 calendar date with a time and UTC offset.";
    private static final Pattern BASIC_CHECKPOINT    = checkpointNotation("", "");
    private static final Pattern EXTENDED_CHECKPOINT = checkpointNotation("-", ":");

    private static Pattern checkpointNotation(String date, String time) {
        return Pattern.compile(("([0-9]{4}|-(?:[0-9]{4}|00[0-9]{4}))%1$s([0-9]{2})%1$s([0-9]{2})T([0-9]{2})"
                + "(?:%2$s([0-9]{2})(?:%2$s([0-9]{2}))?)?(?:[.,]([0-9]{1,9}))?(Z|[+-][0-9]{2}(?:%2$s[0-9]{2})?)")
                .formatted(date, time), Pattern.CASE_INSENSITIVE);
    }

    /**
     * Parses an absolute time-PIP checkpoint expressed as a calendar date, time and UTC offset.
     *
     * Negative years -9999 through -0001 accept four or six digits. Offsets can move a written boundary
     * date outside that year range on the UTC timeline.
     *
     * @param text a basic or extended ISO calendar timestamp with a year from -9999 through 9999
     * @return the represented instant, with up to nanosecond precision
     * @throws DateTimeParseException if the input is outside the supported ISO calendar format
     */
    public static Instant parseCheckpoint(String text) {
        val extended = text.length() > 4 && text.charAt(4) == '-' || text.startsWith("-")
                && (text.length() > 5 && text.charAt(5) == '-' || text.length() > 7 && text.charAt(7) == '-');
        val notation = extended ? EXTENDED_CHECKPOINT : BASIC_CHECKPOINT;
        val parts    = notation.matcher(text);
        if (!parts.matches()) {
            throw new DateTimeParseException(ERROR_TIMESTAMP, text, 0);
        }
        val        hour   = Integer.parseInt(parts.group(4));
        val        minute = parts.group(5) == null ? 0 : Integer.parseInt(parts.group(5));
        val        second = parts.group(6) == null ? 0 : Integer.parseInt(parts.group(6));
        val        digits = parts.group(7);
        final long unit;
        if (parts.group(5) == null) {
            unit = 3600L;
        } else if (parts.group(6) == null) {
            unit = 60L;
        } else {
            unit = 1L;
        }
        val fraction = digits == null ? 0L : Long.parseLong((digits + "000000000").substring(0, 9)) * unit;
        val endOfDay = hour == 24;
        if (endOfDay && (minute != 0 || second != 0 || fraction != 0)) {
            throw new DateTimeParseException(ERROR_TIMESTAMP, text, 0);
        }
        var offset = parts.group(8);
        if (offset.length() == 3) {
            offset += ":00";
        } else if (offset.length() == 5) {
            offset = offset.substring(0, 3) + ":" + offset.substring(3);
        }
        val normalized = String.format(Locale.ROOT, "%s-%s-%sT%02d:%02d:%02d.%09d%s", parts.group(1), parts.group(2),
                parts.group(3), endOfDay ? 0 : hour, minute + fraction / 1_000_000_000L / 60,
                second + fraction / 1_000_000_000L % 60, fraction % 1_000_000_000L, offset);
        try {
            val instant = parsePolicy(normalized);
            return endOfDay ? instant.plusSeconds(86_400L) : instant;
        } catch (DateTimeParseException invalid) {
            throw new DateTimeParseException(ERROR_TIMESTAMP, text, 0, invalid);
        }
    }

    private static DateTimeFormatter timestampFormatter(DateTimeFormatter date) {
        return new DateTimeFormatterBuilder().parseCaseInsensitive().append(date).appendLiteral('T')
                .appendPattern("HH:mm:ss").optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
                .optionalEnd().appendOffset("+HH:MM", "Z").toFormatter(Locale.ROOT)
                .withResolverStyle(ResolverStyle.STRICT);
    }

    static Instant parse(String text) {
        return parseTimestamp(text, TIMESTAMP, 17);
    }

    private static Instant parsePolicy(String text) {
        val normalized = normalizePolicyYear(text);
        return parseTimestamp(normalized, POLICY_TIMESTAMP, normalized.startsWith("-") ? 18 : 17);
    }

    private static Instant parseTimestamp(String text, DateTimeFormatter formatter, int secondsPosition) {
        val          leap = text.startsWith("60", secondsPosition);
        final String normalized;
        if (leap) {
            normalized = text.substring(0, secondsPosition) + "59" + text.substring(secondsPosition + 2);
        } else {
            normalized = text;
        }
        val instant = OffsetDateTime.parse(normalized, formatter).toInstant();
        if (leap && Math.floorMod(instant.getEpochSecond(), 86_400) != 86_399) {
            throw new DateTimeParseException(ERROR_INVALID_LEAP_SECOND, text, secondsPosition);
        }
        return instant;
    }

    private static String normalizePolicyYear(String text) {
        if (!text.startsWith("-")) {
            return text;
        }
        if (text.length() > 38) {
            throw new DateTimeParseException(ERROR_TIMESTAMP, text, 0);
        }
        val year = NEGATIVE_POLICY_YEAR.matcher(text);
        if (!year.lookingAt() || Integer.parseInt(text.substring(1, year.end())) == 0) {
            throw new DateTimeParseException(ERROR_TIMESTAMP, text, 0);
        }
        return year.end() == 7 ? "-" + text.substring(3) : text;
    }

    static Instant parseOrDate(String text) {
        if (text.length() == 10 || text.length() == 11 || text.length() == 13) {
            return LocalDate.parse(normalizePolicyYear(text), POLICY_DATE).atStartOfDay().toInstant(ZoneOffset.UTC);
        }
        return parsePolicy(text);
    }
}
