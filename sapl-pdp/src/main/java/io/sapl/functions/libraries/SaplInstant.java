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
import java.time.temporal.ChronoField;
import java.util.Locale;

import lombok.experimental.UtilityClass;
import lombok.val;

/** The instant profile shared by time functions and certificate validity checks. */
@UtilityClass
class SaplInstant {
    private static final String ERROR_INVALID_LEAP_SECOND = "A leap second requires the last minute of the UTC day.";

    private static final DateTimeFormatter DATE      = new DateTimeFormatterBuilder().appendValue(ChronoField.YEAR, 4)
            .appendLiteral('-').appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH, 2).toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter TIMESTAMP = new DateTimeFormatterBuilder().parseCaseInsensitive()
            .append(DATE).appendLiteral('T').appendPattern("HH:mm:ss").optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true).optionalEnd().appendOffset("+HH:MM", "Z")
            .toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    static Instant parse(String text) {
        val          leap = text.length() <= 35 && text.startsWith("60", 17);
        final String normalized;
        if (leap) {
            normalized = text.substring(0, 17) + "59" + text.substring(19);
        } else {
            normalized = text;
        }
        val instant = OffsetDateTime.parse(normalized, TIMESTAMP).toInstant();
        if (leap && Math.floorMod(instant.getEpochSecond(), 86_400) != 86_399) {
            throw new DateTimeParseException(ERROR_INVALID_LEAP_SECOND, text, 17);
        }
        return instant;
    }

    static Instant parseOrDate(String text) {
        if (text.length() == 10) {
            return LocalDate.parse(text, DATE).atStartOfDay().toInstant(ZoneOffset.UTC);
        }
        return parse(text);
    }
}
