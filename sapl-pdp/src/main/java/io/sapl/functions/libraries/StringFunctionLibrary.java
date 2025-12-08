/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.Value;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.TextValue;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Functions for string manipulation in authorization policies.
 * Provides simple, efficient operations for common string handling tasks like
 * case normalization, whitespace cleanup, substring extraction, and literal
 * replacements. These functions complement the patterns library by offering
 * straightforward alternatives for cases where pattern matching is unnecessary.
 */
@UtilityClass
@FunctionLibrary(name = StringFunctionLibrary.NAME, description = StringFunctionLibrary.DESCRIPTION)
public class StringFunctionLibrary {

    private static final String ERROR_NUMBER_VALUE_OUT_OF_RANGE = "NumberValue out of range.";

    final BigDecimal minLong = BigDecimal.valueOf(Long.MIN_VALUE);
    final BigDecimal maxLong = BigDecimal.valueOf(Long.MAX_VALUE);
    final BigDecimal minInt  = BigDecimal.valueOf(Integer.MIN_VALUE);
    final BigDecimal maxInt  = BigDecimal.valueOf(Integer.MAX_VALUE);

    public static final String NAME        = "string";
    public static final String DESCRIPTION = "Functions for string manipulation in authorization policies.";

    private static final int MAX_REPEAT_COUNT = 10_000;

    private static final String RETURNS_STRING = """
            {
                "type": "string"
            }
            """;

    private static final String RETURNS_BOOLEAN = """
            {
                "type": "boolean"
            }
            """;

    private static final String RETURNS_NUMBER = """
            {
                "type": "integer"
            }
            """;

    @Function(docs = """
            ```toLowerCase(TEXT str)```: Converts all characters to lowercase using the default locale.

            Useful for normalizing identifiers, roles, or resource names to enable case-insensitive
            comparisons in authorization policies.

            **Examples:**
            ```sapl
            policy "normalize_role"
            permit
            where
              string.toLowerCase(subject.role) == "administrator";
            ```

            ```sapl
            policy "case_insensitive_path"
            permit
            where
              var normalizedPath = string.toLowerCase(resource.path);
              normalizedPath in ["/api/public", "/api/health"];
            ```
            """, schema = RETURNS_STRING)
    public static Value toLowerCase(TextValue str) {
        return Value.of(str.value().toLowerCase());
    }

    @Function(docs = """
            ```toUpperCase(TEXT str)```: Converts all characters to uppercase using the default locale.

            Useful for normalizing identifiers or ensuring consistent comparison format in
            authorization policies.

            **Examples:**
            ```sapl
            policy "normalize_department"
            permit
            where
              string.toUpperCase(subject.department) == "ENGINEERING";
            ```

            ```sapl
            policy "uppercase_code"
            permit
            where
              var code = string.toUpperCase(resource.code);
              code in ["ADMIN", "SUPER", "ROOT"];
            ```
            """, schema = RETURNS_STRING)
    public static Value toUpperCase(TextValue str) {
        return Value.of(str.value().toUpperCase());
    }

    @Function(docs = """
            ```equalsIgnoreCase(TEXT str1, TEXT str2)```: Compares two strings for equality, ignoring case.

            Provides case-insensitive string comparison for authorization decisions where case
            variations should be treated as equivalent.

            **Examples:**
            ```sapl
            policy "role_check"
            permit
            where
              string.equalsIgnoreCase(subject.role, "Administrator");
            ```

            ```sapl
            policy "resource_type"
            permit
            where
              string.equalsIgnoreCase(resource.type, "DOCUMENT") && action.name == "read";
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value equalsIgnoreCase(TextValue str1, TextValue str2) {
        return Value.of(str1.value().equalsIgnoreCase(str2.value()));
    }

    @Function(docs = """
            ```trim(TEXT str)```: Removes leading and trailing whitespace.

            Essential for cleaning user input before comparison or validation in authorization
            policies. Removes all leading and trailing spaces, tabs, and other whitespace characters.

            **Examples:**
            ```sapl
            policy "clean_username"
            permit
            where
              var cleanUsername = string.trim(subject.name);
              cleanUsername in resource.allowedUsers;
            ```

            ```sapl
            policy "sanitize_path"
            permit
            where
              var cleanPath = string.trim(resource.path);
              string.startsWith(cleanPath, "/api/");
            ```
            """, schema = RETURNS_STRING)
    public static Value trim(TextValue str) {
        return Value.of(str.value().trim());
    }

    @Function(docs = """
            ```trimStart(TEXT str)```: Removes leading whitespace only.

            Useful when trailing whitespace is significant but leading whitespace should be ignored.

            **Examples:**
            ```sapl
            policy "trim_leading"
            permit
            where
              var cleanInput = string.trimStart(resource.input);
              string.startsWith(cleanInput, "valid-prefix");
            ```
            """, schema = RETURNS_STRING)
    public static Value trimStart(TextValue str) {
        return Value.of(str.value().stripLeading());
    }

    @Function(docs = """
            ```trimEnd(TEXT str)```: Removes trailing whitespace only.

            Useful when leading whitespace is significant but trailing whitespace should be ignored.

            **Examples:**
            ```sapl
            policy "trim_trailing"
            permit
            where
              var cleanInput = string.trimEnd(resource.input);
              string.endsWith(cleanInput, "valid-suffix");
            ```
            """, schema = RETURNS_STRING)
    public static Value trimEnd(TextValue str) {
        return Value.of(str.value().stripTrailing());
    }

    @Function(docs = """
            ```isBlank(TEXT str)```: Returns true if the string is empty or contains only whitespace.

            Useful for validating that required fields contain actual content in authorization
            policies.

            **Examples:**
            ```sapl
            policy "require_reason"
            deny
            where
              action.name == "delete";
              string.isBlank(request.reason);
            ```

            ```sapl
            policy "validate_input"
            permit
            where
              !string.isBlank(subject.username);
              !string.isBlank(resource.documentId);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value isBlank(TextValue str) {
        return Value.of(str.value().isBlank());
    }

    @Function(docs = """
            ```contains(TEXT str, TEXT substring)```: Returns true if the string contains the substring.

            Performs literal substring search without pattern matching. Case-sensitive. For simple
            containment checks, this is more efficient and intuitive than regular expressions.

            **Examples:**
            ```sapl
            policy "permission_check"
            permit
            where
              string.contains(subject.permissions, "read:documents");
            ```

            ```sapl
            policy "path_validation"
            permit
            where
              string.contains(resource.path, "/public/") || string.contains(resource.path, "/shared/");
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value contains(TextValue str, TextValue substring) {
        return Value.of(str.value().contains(substring.value()));
    }

    @Function(docs = """
            ```startsWith(TEXT str, TEXT prefix)```: Returns true if the string starts with the prefix.

            Performs literal prefix check without pattern matching. Case-sensitive. Commonly used
            for path-based authorization and hierarchical resource checks.

            **Examples:**
            ```sapl
            policy "api_path"
            permit
            where
              string.startsWith(resource.path, "/api/public");
            ```

            ```sapl
            policy "role_prefix"
            permit
            where
              string.startsWith(subject.role, "ADMIN_");
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value startsWith(TextValue str, TextValue prefix) {
        return Value.of(str.value().startsWith(prefix.value()));
    }

    @Function(docs = """
            ```endsWith(TEXT str, TEXT suffix)```: Returns true if the string ends with the suffix.

            Performs literal suffix check without pattern matching. Case-sensitive. Useful for
            file type validation and domain checks.

            **Examples:**
            ```sapl
            policy "document_type"
            permit
            where
              string.endsWith(resource.filename, ".pdf") || string.endsWith(resource.filename, ".docx");
            ```

            ```sapl
            policy "domain_check"
            permit
            where
              string.endsWith(subject.email, "@company.com");
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value endsWith(TextValue str, TextValue suffix) {
        return Value.of(str.value().endsWith(suffix.value()));
    }

    @Function(docs = """
            ```length(TEXT str)```: Returns the number of characters in the string.

            Useful for validating input length constraints in authorization policies.

            **Examples:**
            ```sapl
            policy "password_length"
            permit
            where
              string.length(request.password) >= 12;
            ```

            ```sapl
            policy "comment_limit"
            permit
            where
              action.name == "comment";
              string.length(request.text) <= 500;
            ```
            """, schema = RETURNS_NUMBER)
    public static Value length(TextValue str) {
        return Value.of(str.value().length());
    }

    @Function(docs = """
            ```isEmpty(TEXT str)```: Returns true if the string has zero length.

            Unlike isBlank, this only checks for zero length and does not consider whitespace.

            **Examples:**
            ```sapl
            policy "optional_field"
            permit
            where
              string.isEmpty(resource.optionalTag) || resource.optionalTag in resource.allowedTags;
            ```

            ```sapl
            policy "require_id"
            deny
            where
              string.isEmpty(resource.id);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Value isEmpty(TextValue str) {
        return Value.of(str.value().isEmpty());
    }

    @Function(docs = """
            ```substring(TEXT str, NUMBER start)```: Extracts substring from start index to end of string.

            Returns the portion of the string beginning at the specified index. Start index is
            zero-based and inclusive. Returns error if start is negative or exceeds string length.

            **Examples:**
            ```sapl
            policy "extract_suffix"
            permit
            where
              var suffix = string.substring(resource.id, 8);
              suffix == subject.tenantId;
            ```

            ```sapl
            policy "skip_prefix"
            permit
            where
              var withoutPrefix = string.substring(resource.path, 5);
              withoutPrefix in resource.allowedPaths;
            ```
            """, schema = RETURNS_STRING)
    public static Value substring(TextValue str, NumberValue start) {
        val text = str.value();

        if (start.value().compareTo(minLong) < 0 || start.value().compareTo(maxLong) > 0) {
            return new ErrorValue(ERROR_NUMBER_VALUE_OUT_OF_RANGE);
        }

        val startIndex = start.value().intValue();

        return extractSubstring(text, startIndex, text.length());
    }

    @Function(docs = """
            ```substringRange(TEXT str, NUMBER start, NUMBER end)```: Extracts substring between indices.

            Returns the portion of the string from start index (inclusive) to end index (exclusive).
            Both indices are zero-based. Returns error if indices are invalid or out of bounds.

            **Examples:**
            ```sapl
            policy "extract_tenant"
            permit
            where
              var tenantId = string.substringRange(resource.id, 0, 8);
              tenantId == subject.tenantId;
            ```

            ```sapl
            policy "middle_segment"
            permit
            where
              var segment = string.substringRange(resource.path, 5, 15);
              segment == "authorized";
            ```
            """, schema = RETURNS_STRING)
    public static Value substringRange(TextValue str, NumberValue start, NumberValue end) {
        val text = str.value();

        if (start.value().compareTo(minLong) < 0 || start.value().compareTo(maxLong) > 0) {
            return new ErrorValue(ERROR_NUMBER_VALUE_OUT_OF_RANGE);
        }

        val startIndex = start.value().intValue();

        if (end.value().compareTo(minLong) < 0 || end.value().compareTo(maxLong) > 0) {
            return new ErrorValue(ERROR_NUMBER_VALUE_OUT_OF_RANGE);
        }

        val endIndex = end.value().intValue();

        return extractSubstring(text, startIndex, endIndex);
    }

    @Function(docs = """
            ```indexOf(TEXT str, TEXT substring)```: Returns the index of the first occurrence of substring.

            Returns the zero-based index of the first occurrence, or -1 if the substring is not found.
            Case-sensitive search.

            **Examples:**
            ```sapl
            policy "find_separator"
            permit
            where
              var separatorPosition = string.indexOf(resource.id, ":");
              separatorPosition > 0;
            ```

            ```sapl
            policy "check_presence"
            permit
            where
              string.indexOf(subject.permissions, "admin") != -1;
            ```
            """, schema = RETURNS_NUMBER)
    public static Value indexOf(TextValue str, TextValue substring) {
        return Value.of(str.value().indexOf(substring.value()));
    }

    @Function(docs = """
            ```lastIndexOf(TEXT str, TEXT substring)```: Returns the index of the last occurrence of substring.

            Returns the zero-based index of the last occurrence, or -1 if the substring is not found.
            Case-sensitive search.

            **Examples:**
            ```sapl
            policy "find_extension"
            permit
            where
              var dotPosition = string.lastIndexOf(resource.filename, ".");
              var extension = string.substring(resource.filename, dotPosition + 1);
              extension in ["pdf", "docx", "txt"];
            ```

            ```sapl
            policy "last_segment"
            permit
            where
              var lastSlash = string.lastIndexOf(resource.path, "/");
              var filename = string.substring(resource.path, lastSlash + 1);
              filename == "allowed.txt";
            ```
            """, schema = RETURNS_NUMBER)
    public static Value lastIndexOf(TextValue str, TextValue substring) {
        return Value.of(str.value().lastIndexOf(substring.value()));
    }

    @Function(docs = """
            ```join(ARRAY elements, TEXT delimiter)```: Concatenates array elements with delimiter.

            Combines all text elements of an array into a single string, inserting the delimiter
            between consecutive elements. Returns error if array contains non-text elements. Empty
            array returns empty string.

            **Examples:**
            ```sapl
            policy "build_permission"
            permit
            where
              var permission = string.join([resource.type, action.name], ":");
              permission in subject.permissions;
            ```

            ```sapl
            policy "format_roles"
            permit
            where
              var roleList = string.join(subject.roles, ",");
              string.contains(roleList, "admin");
            ```
            """, schema = RETURNS_STRING)
    public static Value join(ArrayValue elements, TextValue delimiter) {
        val array         = elements.toArray();
        val delimiterText = delimiter.value();

        if (0 == array.length) {
            return Value.of("");
        }

        val result   = new StringBuilder();
        val iterator = elements.iterator();

        while (iterator.hasNext()) {
            val element = iterator.next();
            if (!(element instanceof TextValue)) {
                return Value.error("All array elements must be text values");
            }

            if (!result.isEmpty()) {
                result.append(delimiterText);
            }
            val text = (TextValue) element;
            result.append(text.value());
        }

        return Value.of(result.toString());
    }

    @Function(docs = """
            ```concat(TEXT...strings)```: Concatenates multiple strings into one.

            Combines all provided strings in order without any delimiter. Accepts variable number
            of string arguments.

            **Examples:**
            ```sapl
            policy "build_path"
            permit
            where
              var fullPath = string.concat("/api/", subject.tenant, "/", resource.type);
              fullPath in resource.allowedPaths;
            ```

            ```sapl
            policy "construct_id"
            permit
            where
              var resourceId = string.concat(subject.tenant, ":", resource.type, ":", resource.id);
              resourceId in subject.accessibleResources;
            ```
            """, schema = RETURNS_STRING)
    public static Value concat(TextValue... strings) {
        val result = new StringBuilder();
        for (val str : strings) {
            result.append(str.value());
        }
        return Value.of(result.toString());
    }

    @Function(docs = """
            ```replace(TEXT str, TEXT target, TEXT replacement)```: Replaces all occurrences of target with replacement.

            Performs literal string replacement without pattern matching. If target is not found,
            returns the original string unchanged. Returns error if target is empty.

            **Examples:**
            ```sapl
            policy "normalize_separators"
            permit
            where
              var normalized = string.replace(resource.path, "\\\\", "/");
              string.startsWith(normalized, "/api/");
            ```

            ```sapl
            policy "remove_prefix"
            permit
            where
              var cleaned = string.replace(subject.role, "ROLE_", "");
              cleaned in ["admin", "user", "guest"];
            ```
            """, schema = RETURNS_STRING)
    public static Value replace(TextValue str, TextValue target, TextValue replacement) {
        val text            = str.value();
        val targetText      = target.value();
        val replacementText = replacement.value();

        if (targetText.isEmpty()) {
            return Value.error("Target string cannot be empty");
        }

        return Value.of(text.replace(targetText, replacementText));
    }

    @Function(docs = """
            ```replaceFirst(TEXT str, TEXT target, TEXT replacement)```: Replaces first occurrence of target.

            Performs literal replacement of only the first occurrence. If target is not found,
            returns the original string unchanged. Returns error if target is empty.

            **Examples:**
            ```sapl
            policy "remove_first_slash"
            permit
            where
              var path = string.replaceFirst(resource.path, "/", "");
              string.startsWith(path, "api");
            ```

            ```sapl
            policy "replace_prefix"
            permit
            where
              var updated = string.replaceFirst(resource.type, "legacy_", "");
              updated in resource.allowedTypes;
            ```
            """, schema = RETURNS_STRING)
    public static Value replaceFirst(TextValue str, TextValue target, TextValue replacement) {
        val text            = str.value();
        val targetText      = target.value();
        val replacementText = replacement.value();

        if (targetText.isEmpty()) {
            return Value.error("Target string cannot be empty");
        }

        return Value.of(text.replaceFirst(Pattern.quote(targetText), replacementText));
    }

    @Function(docs = """
            ```leftPad(TEXT str, NUMBER length, TEXT padChar)```: Pads string on the left to specified length.

            Adds padding characters to the left of the string until it reaches the specified length.
            If the string is already longer than or equal to the target length, returns the original
            string unchanged. Returns error if padChar is not exactly one character.

            **Examples:**
            ```sapl
            policy "format_id"
            permit
            where
              var paddedId = string.leftPad(resource.numericId, 8, "0");
              paddedId == "00001234";
            ```

            ```sapl
            policy "align_code"
            permit
            where
              var aligned = string.leftPad(resource.code, 10, " ");
              string.length(aligned) == 10;
            ```
            """, schema = RETURNS_STRING)
    public static Value leftPad(TextValue str, NumberValue length, TextValue padChar) {
        val text      = str.value();
        val padString = padChar.value();

        if (length.value().compareTo(minLong) < 0 || length.value().compareTo(maxLong) > 0) {
            return new ErrorValue(ERROR_NUMBER_VALUE_OUT_OF_RANGE);
        }

        return padString(text, length.value().intValue(), padString, true);
    }

    @Function(docs = """
            ```rightPad(TEXT str, NUMBER length, TEXT padChar)```: Pads string on the right to specified length.

            Adds padding characters to the right of the string until it reaches the specified length.
            If the string is already longer than or equal to the target length, returns the original
            string unchanged. Returns error if padChar is not exactly one character.

            **Examples:**
            ```sapl
            policy "format_label"
            permit
            where
              var padded = string.rightPad(subject.name, 20, " ");
              string.length(padded) == 20;
            ```

            ```sapl
            policy "align_right"
            permit
            where
              var aligned = string.rightPad(resource.tag, 15, "-");
              string.endsWith(aligned, "-");
            ```
            """, schema = RETURNS_STRING)
    public static Value rightPad(TextValue str, NumberValue length, TextValue padChar) {
        val text      = str.value();
        val padString = padChar.value();

        if (length.value().compareTo(minLong) < 0 || length.value().compareTo(maxLong) > 0) {
            return new ErrorValue(ERROR_NUMBER_VALUE_OUT_OF_RANGE);
        }

        return padString(text, length.value().intValue(), padString, false);
    }

    @Function(docs = """
            ```repeat(TEXT str, NUMBER count)```: Repeats the string the specified number of times.

            Creates a new string by concatenating the original string count times. Returns empty
            string if count is zero. Returns error if count is negative or exceeds 10,000.

            **Examples:**
            ```sapl
            policy "generate_separator"
            permit
            where
              var separator = string.repeat("-", 40);
              string.length(separator) == 40;
            ```

            ```sapl
            policy "build_pattern"
            permit
            where
              var pattern = string.repeat("x", 5);
              pattern == "xxxxx";
            ```
            """, schema = RETURNS_STRING)
    public static Value repeat(TextValue str, NumberValue count) {
        val text = str.value();

        if (count.value().compareTo(minLong) < 0 || count.value().compareTo(maxLong) > 0) {
            return new ErrorValue(ERROR_NUMBER_VALUE_OUT_OF_RANGE);
        }

        val countValue = count.value().intValue();

        if (countValue < 0) {
            return Value.error("Count cannot be negative");
        }

        if (countValue > MAX_REPEAT_COUNT) {
            return Value.error("Count exceeds maximum allowed value of " + MAX_REPEAT_COUNT);
        }

        return Value.of(text.repeat(countValue));
    }

    @Function(docs = """
            ```reverse(TEXT str)```: Reverses the order of characters in the string.

            Creates a new string with all characters in reverse order.

            **Examples:**
            ```sapl
            policy "check_palindrome"
            permit
            where
              string.reverse(resource.code) == resource.code;
            ```

            ```sapl
            policy "reverse_match"
            permit
            where
              var reversed = string.reverse(subject.token);
              reversed == resource.expectedToken;
            ```
            """, schema = RETURNS_STRING)
    public static Value reverse(TextValue str) {
        return Value.of(new StringBuilder(str.value()).reverse().toString());
    }

    /**
     * Extracts substring from text using start and end indices.
     */
    private static Value extractSubstring(String text, int start, int end) {
        if (start < 0 || start > text.length()) {
            return Value.error("Start index out of bounds: " + start);
        }

        if (end < start || end > text.length()) {
            return Value.error("End index out of bounds: " + end);
        }

        return Value.of(text.substring(start, end));
    }

    /**
     * Pads string to specified length with padding character.
     */
    private static Value padString(String text, int targetLength, String padString, boolean padLeft) {
        if (padString.length() != 1) {
            return Value.error("Padding must be exactly one character");
        }

        if (text.length() >= targetLength) {
            return Value.of(text);
        }

        val padChar       = padString.charAt(0);
        val paddingNeeded = targetLength - text.length();
        val result        = new StringBuilder(targetLength);

        if (padLeft) {
            result.append(String.valueOf(padChar).repeat(paddingNeeded));
            result.append(text);
        } else {
            result.append(text);
            result.append(String.valueOf(padChar).repeat(paddingNeeded));
        }

        return Value.of(result.toString());
    }
}
