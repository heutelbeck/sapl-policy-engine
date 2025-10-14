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
package io.sapl.functions;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Array;
import io.sapl.api.validation.Int;
import io.sapl.api.validation.Text;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Function library providing pattern matching capabilities for SAPL policies.
 * Includes glob pattern matching with configurable delimiters and comprehensive
 * regex operations with DoS protection mechanisms.
 * <p>
 * Security: ReDoS protection via pattern analysis. Timeout protection must be
 * implemented at the PDP level for reactive environments.
 */
@Slf4j
@UtilityClass
@FunctionLibrary(name = PatternsFunctionLibrary.NAME, description = PatternsFunctionLibrary.DESCRIPTION)
public class PatternsFunctionLibrary {

    public static final String NAME        = "patterns";
    public static final String DESCRIPTION = "Functions for pattern matching with globs and regular expressions.";

    private static final int    MAX_PATTERN_LENGTH        = 1_000;
    private static final int    MAX_INPUT_LENGTH          = 100_000;
    private static final int    MAX_MATCHES               = 10_000;
    private static final int    MAX_GLOB_RECURSION        = 50;
    private static final int    MAX_ALTERNATIONS          = 100;
    private static final String REGEX_METACHARACTERS      = ".^$*+?()[]{}\\|";
    private static final String CHAR_CLASS_METACHARACTERS = "\\]^[";
    private static final String CHAR_CLASS_SPECIAL_CHARS  = "\\]^-[";
    private static final String GLOB_METACHARACTERS       = "*?[]{}\\-!";

    private static final String ERROR_PATTERN_VALUE_TEXT  = "Pattern and value must be text values";
    private static final String ERROR_PATTERN_TOO_LONG    = "Pattern too long (max %d characters)";
    private static final String ERROR_INPUT_TOO_LONG      = "Input too long (max %d characters)";
    private static final String ERROR_LIMIT_NOT_NUMBER    = "Limit must be a number";
    private static final String ERROR_LIMIT_NEGATIVE      = "Limit must be non-negative";
    private static final String ERROR_REPLACEMENT_TEXT    = "Replacement must be a text value";
    private static final String ERROR_ESCAPE_GLOB_TEXT    = "escapeGlob requires a text value";
    private static final String ERROR_TEMPLATE_ARGS_TEXT  = "All arguments must be text values";
    private static final String ERROR_DELIMITERS_EMPTY    = "Delimiters cannot be empty";
    private static final String ERROR_DELIMITERS_TEXT     = "All delimiters must be text values";
    private static final String ERROR_TEMPLATE_LENGTH     = "Template or value exceeds maximum length";
    private static final String ERROR_TEMPLATE_FORMAT     = "Invalid template format: mismatched or nested delimiters";
    private static final String ERROR_DANGEROUS_PATTERN   = "Invalid or dangerous regex pattern";
    private static final String ERROR_DANGEROUS_TEMPLATE  = "Template contains dangerous regex patterns";
    private static final String ERROR_INVALID_GLOB        = "Invalid glob pattern: %s";
    private static final String ERROR_INVALID_TEMPLATE    = "Invalid regex in template: %s";
    private static final String ERROR_REPLACEMENT_FAILED  = "Replacement failed: %s";
    private static final String ERROR_SPLIT_FAILED        = "Split failed: %s";
    private static final String ERROR_MATCHING_FAILED     = "Pattern matching failed: %s";
    private static final String ERROR_UNCLOSED_CHAR_CLASS = "Unclosed character class starting at position %d";
    private static final String ERROR_UNCLOSED_ALT_GROUP  = "Unclosed alternative group starting at position %d";
    private static final String ERROR_GLOB_TOO_NESTED     = "Glob pattern too deeply nested (max %d levels)";

    private static final char REGEX_ANCHOR_START      = '^';
    private static final char REGEX_ANCHOR_END        = '$';
    private static final String REGEX_ANY_CHAR_MULTIPLE = ".*";
    private static final String REGEX_ANY_CHAR_SINGLE   = ".";
    private static final String REGEX_DOUBLE_BACKSLASH  = "\\\\";
    private static final String REGEX_GROUP_START       = "(?:";
    private static final char REGEX_ALTERNATION       = '|';

    @Function(docs = """
            ```patterns.matchGlob(TEXT pattern, TEXT value, ARRAY delimiters)```: Matches a string
            against a glob pattern with configurable delimiters.

            Glob patterns provide hierarchical matching where wildcards respect segment boundaries.
            The single wildcard `*` matches zero or more characters within a segment (between delimiters),
            while the double wildcard `**` matches across segment boundaries. When no delimiters are
            specified or an empty array is provided, the default delimiter `.` is used, suitable for
            domain names and hierarchical identifiers.

            **Pattern Elements:**
            - `*` - Matches zero or more characters within a delimiter-bounded segment
            - `**` - Matches zero or more characters including across delimiter boundaries
            - `?` - Matches exactly one character
            - `[abc]` or `[a-z]` - Matches one character from the set or range
            - `[!abc]` or `[!a-z]` - Matches one character NOT in the set or range (negation)
            - `{cat,dog,bird}` - Matches any of the alternative patterns
            - `\\` - Escapes the next character (e.g., `\\*` matches literal asterisk)

            **Parameters:**
            - `pattern` - The glob pattern to match against
            - `value` - The string value to test
            - `delimiters` - Array of delimiter strings (optional, defaults to `["."]`)

            **Limitations:**
            - Maximum pattern length: 1,000 characters
            - Maximum input length: 100,000 characters
            - Maximum nesting depth: 50 levels
            - All delimiters must be non-empty text values
            - Returns error for malformed patterns (unclosed brackets, braces, etc.)

            **Returns:** Boolean value indicating match success, or error for invalid inputs.
            """)
    public static Val matchGlob(@Text Val pattern, @Text Val value, @Array Val delimiters) {
        val error = validateInputs(pattern, value);
        if (error != null)
            return error;

        val delimiterError = validateDelimiters(delimiters);
        if (delimiterError != null)
            return delimiterError;

        val delimiterList = extractDelimiters(delimiters, List.of("."));
        return matchGlobImplementation(pattern.getText(), value.getText(), delimiterList);
    }

    @Function(docs = """
            ```patterns.matchGlobWithoutDelimiters(TEXT pattern, TEXT value)```: Matches a string
            against a glob pattern without delimiter boundaries.

            Treats the entire input as a single segment, allowing wildcards to match any characters
            without restriction. Both `*` and `**` behave identically in this mode. Useful for matching
            flat strings without hierarchical structure where segment boundaries are not relevant.

            **Limitations:**
            - Maximum pattern length: 1,000 characters
            - Maximum input length: 100,000 characters
            - Maximum nesting depth: 50 levels

            **Returns:** Boolean value indicating match success, or error for invalid inputs.
            """)
    public static Val matchGlobWithoutDelimiters(@Text Val pattern, @Text Val value) {
        val error = validateInputs(pattern, value);
        if (error != null)
            return error;

        return matchGlobImplementation(pattern.getText(), value.getText(), null);
    }

    @Function(docs = """
            ```patterns.escapeGlob(TEXT text)```: Escapes glob metacharacters to treat them as literals.

            Prepends backslash to all glob special characters (`*?[]{}\\-!`) to prevent pattern interpretation.
            Essential for safely incorporating untrusted input into glob patterns, preventing pattern injection
            attacks where malicious input could match unintended values.

            **Returns:** Text with all glob metacharacters escaped, or error for non-text input.
            """)
    public static Val escapeGlob(@Text Val text) {
        if (!text.isTextual()) {
            return Val.error(ERROR_ESCAPE_GLOB_TEXT);
        }

        return Val.of(escapeGlobCharacters(text.getText()));
    }

    @Function(docs = """
            ```patterns.isValidRegex(TEXT pattern)```: Validates if a string is a valid Java regular expression.

            **Returns:** `true` if pattern is valid and within length limits, `false` otherwise.
            """)
    public static Val isValidRegex(@Text Val pattern) {
        if (!pattern.isTextual() || pattern.getText().length() > MAX_PATTERN_LENGTH) {
            return Val.of(false);
        }

        try {
            Pattern.compile(pattern.getText());
            return Val.of(true);
        } catch (PatternSyntaxException e) {
            return Val.of(false);
        }
    }

    @Function(docs = """
            ```patterns.findMatches(TEXT pattern, TEXT value)```: Finds all matches of a regex pattern.

            **DoS Protection:**
            - Maximum pattern length: 1,000 characters
            - Maximum input length: 100,000 characters
            - Maximum matches returned: 10,000
            - Rejects patterns with nested quantifiers like `(a+)+`
            - Rejects patterns with excessive alternations

            **Returns:** Array of matched strings (empty if no matches), or error for invalid/dangerous patterns.
            """)
    public static Val findMatches(@Text Val pattern, @Text Val value) {
        return findMatchesWithLimit(pattern, value, MAX_MATCHES);
    }

    @Function(docs = """
            ```patterns.findMatchesLimited(TEXT pattern, TEXT value, INT limit)```: Finds up to limit matches.

            Identical to findMatches but stops after finding the specified number of matches. The limit
            is capped at 10,000 maximum. Negative limits return an error.

            **Returns:** Array of matched strings with at most the specified number of elements.
            """)
    public static Val findMatchesLimited(@Text Val pattern, @Text Val value, @Int Val limit) {
        if (!limit.isNumber()) {
            return Val.error(ERROR_LIMIT_NOT_NUMBER);
        }

        val limitValue = limit.get().asInt();
        if (limitValue < 0) {
            return Val.error(ERROR_LIMIT_NEGATIVE);
        }

        return findMatchesWithLimit(pattern, value, Math.min(limitValue, MAX_MATCHES));
    }

    @Function(docs = """
            ```patterns.findAllSubmatch(TEXT pattern, TEXT value)```: Finds all matches with capturing groups.

            Returns matches with their capturing groups. Each match is represented as an array where index 0
            contains the full match and subsequent indices contain captured groups.

            **Returns:** Nested array of matches and their captured groups.
            """)
    public static Val findAllSubmatch(@Text Val pattern, @Text Val value) {
        return findAllSubmatchWithLimit(pattern, value, MAX_MATCHES);
    }

    @Function(docs = """
            ```patterns.findAllSubmatchLimited(TEXT pattern, TEXT value, INT limit)```: Finds up to limit matches
            with capturing groups.

            **Returns:** Nested array of at most the specified number of matches with their captured groups.
            """)
    public static Val findAllSubmatchLimited(@Text Val pattern, @Text Val value, @Int Val limit) {
        if (!limit.isNumber()) {
            return Val.error(ERROR_LIMIT_NOT_NUMBER);
        }

        val limitValue = limit.get().asInt();
        if (limitValue < 0) {
            return Val.error(ERROR_LIMIT_NEGATIVE);
        }

        return findAllSubmatchWithLimit(pattern, value, Math.min(limitValue, MAX_MATCHES));
    }

    @Function(docs = """
            ```patterns.replaceAll(TEXT value, TEXT pattern, TEXT replacement)```: Replaces all
            occurrences of a regex pattern.

            **Returns:** Text with all pattern occurrences replaced, or error for invalid patterns.
            """)
    public static Val replaceAll(@Text Val value, @Text Val pattern, @Text Val replacement) {
        val error = validateInputs(pattern, value);
        if (error != null)
            return error;

        if (!replacement.isTextual()) {
            return Val.error(ERROR_REPLACEMENT_TEXT);
        }

        val compiledPattern = compileRegex(pattern.getText());
        if (compiledPattern == null)
            return Val.error(ERROR_DANGEROUS_PATTERN);

        try {
            val result = compiledPattern.matcher(value.getText()).replaceAll(replacement.getText());
            return Val.of(result);
        } catch (Exception e) {
            return Val.error(String.format(ERROR_REPLACEMENT_FAILED, e.getMessage()));
        }
    }

    @Function(docs = """
            ```patterns.split(TEXT pattern, TEXT value)```: Splits a string by a regex pattern.

            **Returns:** Array of string segments, or error for invalid patterns.
            """)
    public static Val split(@Text Val pattern, @Text Val value) {
        val error = validateInputs(pattern, value);
        if (error != null)
            return error;

        val compiledPattern = compileRegex(pattern.getText());
        if (compiledPattern == null)
            return Val.error(ERROR_DANGEROUS_PATTERN);

        try {
            val parts      = compiledPattern.split(value.getText(), MAX_MATCHES);
            val resultNode = JsonNodeFactory.instance.arrayNode();
            for (val part : parts) {
                resultNode.add(part);
            }
            return Val.of(resultNode);
        } catch (Exception e) {
            return Val.error(String.format(ERROR_SPLIT_FAILED, e.getMessage()));
        }
    }

    @Function(docs = """
            ```patterns.matchTemplate(TEXT template, TEXT value, TEXT delimiterStart, TEXT delimiterEnd)```:
            Matches a string against a template with embedded regex patterns.

            Templates combine literal text with regex patterns. Text outside delimiters is matched literally
            (with support for backslash escape sequences). Text inside delimiters is treated as full Java
            regex patterns.

            In literal portions, use backslash to escape special characters (e.g., `\\*` for literal asterisk,
            `\\\\` for literal backslash).

            **Returns:** Boolean indicating match success, or error for malformed templates.
            """)
    public static Val matchTemplate(@Text Val template, @Text Val value, @Text Val delimiterStart,
            @Text Val delimiterEnd) {
        if (!template.isTextual() || !value.isTextual() || !delimiterStart.isTextual() || !delimiterEnd.isTextual()) {
            return Val.error(ERROR_TEMPLATE_ARGS_TEXT);
        }

        val templateText   = template.getText();
        val valueText      = value.getText();
        val startDelimiter = delimiterStart.getText();
        val endDelimiter   = delimiterEnd.getText();

        if (startDelimiter.isEmpty() || endDelimiter.isEmpty()) {
            return Val.error(ERROR_DELIMITERS_EMPTY);
        }

        if (templateText.length() > MAX_PATTERN_LENGTH || valueText.length() > MAX_INPUT_LENGTH) {
            return Val.error(ERROR_TEMPLATE_LENGTH);
        }

        val regexPattern = buildTemplateRegex(templateText, startDelimiter, endDelimiter);
        if (regexPattern == null) {
            return Val.error(ERROR_TEMPLATE_FORMAT);
        }

        if (isDangerousPattern(regexPattern)) {
            logSecurityEvent("Dangerous pattern in template rejected", regexPattern);
            return Val.error(ERROR_DANGEROUS_TEMPLATE);
        }

        try {
            val pattern = Pattern.compile(regexPattern);
            return Val.of(pattern.matcher(valueText).matches());
        } catch (PatternSyntaxException e) {
            return Val.error(String.format(ERROR_INVALID_TEMPLATE, e.getMessage()));
        }
    }

    private static String escapeGlobCharacters(String input) {
        val result = new StringBuilder(input.length() * 2);
        for (val character : input.toCharArray()) {
            if (GLOB_METACHARACTERS.indexOf(character) >= 0) {
                result.append('\\');
            }
            result.append(character);
        }
        return result.toString();
    }

    private static String processTemplateEscapes(String input) {
        val result   = new StringBuilder(input.length());
        int position = 0;

        while (position < input.length()) {
            if (input.charAt(position) == '\\' && position + 1 < input.length()) {
                result.append(input.charAt(position + 1));
                position += 2;
            } else {
                result.append(input.charAt(position));
                position++;
            }
        }

        return result.toString();
    }

    private static String escapeRegexCharacters(String input) {
        val result = new StringBuilder(input.length() * 2);
        for (val character : input.toCharArray()) {
            if (REGEX_METACHARACTERS.indexOf(character) >= 0) {
                result.append('\\');
            }
            result.append(character);
        }
        return result.toString();
    }

    private static Val validateInputs(Val pattern, Val value) {
        if (!pattern.isTextual() || !value.isTextual()) {
            return Val.error(ERROR_PATTERN_VALUE_TEXT);
        }

        if (pattern.getText().length() > MAX_PATTERN_LENGTH) {
            return Val.error(String.format(ERROR_PATTERN_TOO_LONG, MAX_PATTERN_LENGTH));
        }

        if (value.getText().length() > MAX_INPUT_LENGTH) {
            return Val.error(String.format(ERROR_INPUT_TOO_LONG, MAX_INPUT_LENGTH));
        }

        return null;
    }

    private static Val validateDelimiters(Val delimiters) {
        if (delimiters.isUndefined() || !delimiters.isArray()) {
            return null;
        }

        val arrayNode = delimiters.getArrayNode();
        for (int i = 0; i < arrayNode.size(); i++) {
            val element = Val.of(arrayNode.get(i));
            if (!element.isTextual()) {
                return Val.error(ERROR_DELIMITERS_TEXT);
            }
        }

        return null;
    }

    private static List<String> extractDelimiters(Val delimiters, List<String> defaultValue) {
        if (delimiters.isUndefined() || !delimiters.isArray()) {
            return defaultValue;
        }

        val arrayNode = delimiters.getArrayNode();
        if (arrayNode.isEmpty()) {
            return defaultValue;
        }

        val     result      = new ArrayList<String>(arrayNode.size());
        boolean hasNonEmpty = false;

        for (int i = 0; i < arrayNode.size(); i++) {
            val element = Val.of(arrayNode.get(i));
            if (element.isTextual() && !element.getText().isEmpty()) {
                result.add(element.getText());
                hasNonEmpty = true;
            }
        }

        return hasNonEmpty ? result : null;
    }

    private static Val matchGlobImplementation(String pattern, String value, List<String> delimiters) {
        try {
            val regex           = convertGlobToRegex(pattern, delimiters, 0);
            val compiledPattern = Pattern.compile(regex);
            return Val.of(compiledPattern.matcher(value).matches());
        } catch (IllegalStateException e) {
            return Val.error(e.getMessage());
        } catch (PatternSyntaxException e) {
            return Val.error(String.format(ERROR_INVALID_GLOB, e.getMessage()));
        }
    }

    private static String convertGlobToRegex(String glob, List<String> delimiters, int recursionDepth) {
        if (recursionDepth > MAX_GLOB_RECURSION) {
            throw new IllegalStateException(ERROR_GLOB_TOO_NESTED.formatted(MAX_GLOB_RECURSION));
        }

        val regex    = new StringBuilder(REGEX_ANCHOR_START);
        int position = 0;

        while (position < glob.length()) {
            val handler = switch (glob.charAt(position)) {
            case '\\' -> processEscapeSequence(glob, position);
            case '*'  -> processWildcard(glob, position, delimiters);
            case '?'  -> processSingleCharacterWildcard(position, delimiters);
            case '['  -> processCharacterClass(glob, position);
            case '{'  -> processAlternatives(glob, position, delimiters, recursionDepth);
            default   -> processLiteralCharacter(glob, position);
            };

            regex.append(handler.regexFragment);
            position = handler.nextPosition;
        }

        return regex.append(REGEX_ANCHOR_END).toString();
    }

    private static GlobConversionResult processEscapeSequence(String glob, int position) {
        if (position + 1 < glob.length()) {
            val escapedCharacter = glob.charAt(position + 1);
            val result           = new StringBuilder();
            if (REGEX_METACHARACTERS.indexOf(escapedCharacter) >= 0) {
                result.append('\\');
            }
            result.append(escapedCharacter);
            return new GlobConversionResult(result.toString(), position + 2);
        }
        return new GlobConversionResult(REGEX_DOUBLE_BACKSLASH, position + 1);
    }

    private static GlobConversionResult processWildcard(String glob, int position, List<String> delimiters) {
        if (position + 1 < glob.length() && glob.charAt(position + 1) == '*') {
            return new GlobConversionResult(REGEX_ANY_CHAR_MULTIPLE, position + 2);
        }
        return new GlobConversionResult(buildDelimiterAwarePattern(delimiters, true), position + 1);
    }

    private static GlobConversionResult processSingleCharacterWildcard(int position, List<String> delimiters) {
        return new GlobConversionResult(buildDelimiterAwarePattern(delimiters, false), position + 1);
    }

    private static GlobConversionResult processCharacterClass(String glob, int position) {
        int closingBracket = findClosingBracket(glob, position);
        if (closingBracket == -1) {
            throw new IllegalStateException(ERROR_UNCLOSED_CHAR_CLASS.formatted(position));
        }

        val content   = glob.substring(position + 1, closingBracket);
        val processed = new StringBuilder("[");

        int contentPosition = 0;
        if (content.startsWith("!")) {
            processed.append('^');
            contentPosition = 1;
        }

        while (contentPosition < content.length()) {
            if (content.charAt(contentPosition) == '\\' && contentPosition + 1 < content.length()) {
                val escapedChar = content.charAt(contentPosition + 1);
                if (CHAR_CLASS_METACHARACTERS.indexOf(escapedChar) >= 0) {
                    processed.append('\\');
                }
                processed.append(escapedChar);
                contentPosition += 2;
            } else {
                val currentChar = content.charAt(contentPosition);
                if (needsEscapeInCharacterClass(currentChar, contentPosition, content.length())) {
                    processed.append('\\');
                }
                processed.append(currentChar);
                contentPosition++;
            }
        }

        processed.append(']');
        return new GlobConversionResult(processed.toString(), closingBracket + 1);
    }

    private static boolean needsEscapeInCharacterClass(char character, int position, int contentLength) {
        if (CHAR_CLASS_METACHARACTERS.indexOf(character) >= 0) {
            return true;
        }
        if (character == '-') {
            boolean isRange = position > 0 && position < contentLength - 1;
            return !isRange;
        }
        return false;
    }

    private static GlobConversionResult processAlternatives(String glob, int position, List<String> delimiters,
            int recursionDepth) {
        int closingBrace = findClosingBrace(glob, position);
        if (closingBrace == -1) {
            throw new IllegalStateException(ERROR_UNCLOSED_ALT_GROUP.formatted(position));
        }

        val alternatives = splitAlternatives(glob.substring(position + 1, closingBrace));
        val regex        = new StringBuilder(REGEX_GROUP_START);

        for (int i = 0; i < alternatives.size(); i++) {
            if (i > 0) {
                regex.append(REGEX_ALTERNATION);
            }
            val alternativeRegex = convertGlobToRegex(alternatives.get(i), delimiters, recursionDepth + 1);
            regex.append(alternativeRegex, 1, alternativeRegex.length() - 1);
        }

        regex.append(')');
        return new GlobConversionResult(regex.toString(), closingBrace + 1);
    }

    private static GlobConversionResult processLiteralCharacter(String glob, int position) {
        int codePoint        = glob.codePointAt(position);
        int characterCount   = Character.charCount(codePoint);
        val literalCharacter = glob.substring(position, position + characterCount);
        val result           = new StringBuilder();

        for (val character : literalCharacter.toCharArray()) {
            if (REGEX_METACHARACTERS.indexOf(character) >= 0) {
                result.append('\\');
            }
            result.append(character);
        }

        return new GlobConversionResult(result.toString(), position + characterCount);
    }

    private static String buildDelimiterAwarePattern(Collection<String> delimiters, boolean allowMultiple) {
        if (delimiters == null || delimiters.isEmpty()) {
            return allowMultiple ? REGEX_ANY_CHAR_MULTIPLE : REGEX_ANY_CHAR_SINGLE;
        }

        val negatedCharClass = new StringBuilder("[^");
        for (val delimiter : delimiters) {
            for (val character : delimiter.toCharArray()) {
                if (CHAR_CLASS_SPECIAL_CHARS.indexOf(character) >= 0) {
                    negatedCharClass.append('\\');
                }
                negatedCharClass.append(character);
            }
        }
        negatedCharClass.append(']');

        return allowMultiple ? negatedCharClass.append('*').toString() : negatedCharClass.toString();
    }

    private static int findClosingBrace(String pattern, int startPosition) {
        int depth    = 1;
        int position = startPosition + 1;

        while (position < pattern.length()) {
            if (pattern.charAt(position) == '\\' && position + 1 < pattern.length()) {
                position += 2;
            } else if (pattern.charAt(position) == '{') {
                depth++;
                position++;
            } else if (pattern.charAt(position) == '}') {
                depth--;
                if (depth == 0) {
                    return position;
                }
                position++;
            } else {
                position++;
            }
        }

        return -1;
    }

    private static int findClosingBracket(String pattern, int startPosition) {
        int position = startPosition + 1;

        while (position < pattern.length()) {
            if (pattern.charAt(position) == '\\' && position + 1 < pattern.length()) {
                position += 2;
            } else if (pattern.charAt(position) == ']') {
                return position;
            } else {
                position++;
            }
        }

        return -1;
    }

    private static List<String> splitAlternatives(String alternatives) {
        val result       = new ArrayList<String>();
        val current      = new StringBuilder();
        int position     = 0;
        int nestingDepth = 0;

        while (position < alternatives.length()) {
            val character = alternatives.charAt(position);

            if (character == '\\' && position + 1 < alternatives.length()) {
                current.append(character).append(alternatives.charAt(position + 1));
                position += 2;
            } else if (character == '{') {
                nestingDepth++;
                current.append(character);
                position++;
            } else if (character == '}') {
                nestingDepth--;
                current.append(character);
                position++;
            } else if (character == ',' && nestingDepth == 0) {
                result.add(current.toString());
                current.setLength(0);
                position++;
            } else {
                current.append(character);
                position++;
            }
        }

        result.add(current.toString());
        return result;
    }

    private static Pattern compileRegex(String patternText) {
        if (isDangerousPattern(patternText)) {
            logSecurityEvent("Dangerous regex pattern rejected", patternText);
            return null;
        }

        try {
            return Pattern.compile(patternText);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    private static boolean isDangerousPattern(String pattern) {
        if (pattern.split("\\|").length > MAX_ALTERNATIONS) {
            return true;
        }

        if (pattern.matches(".*\\([^)]*[*+]\\)[*+].*")) {
            return true;
        }

        if (pattern.matches(".*\\([^)]*\\|[^)]*\\)[*+].*")) {
            return true;
        }

        if (pattern.matches(".*\\(.*\\*.*\\).*\\*.*")) {
            return true;
        }

        if (pattern.matches(".*\\{\\d+,\\d*}\\{\\d+,\\d*}.*")) {
            return true;
        }

        return pattern.contains(".*.*") || pattern.contains(".+.+");
    }

    private static Val findMatchesWithLimit(Val pattern, Val value, int limit) {
        val error = validateInputs(pattern, value);
        if (error != null)
            return error;

        val compiledPattern = compileRegex(pattern.getText());
        if (compiledPattern == null)
            return Val.error(ERROR_DANGEROUS_PATTERN);

        try {
            val matcher = compiledPattern.matcher(value.getText());
            val matches = JsonNodeFactory.instance.arrayNode();

            int count = 0;
            while (count < limit && matcher.find()) {
                matches.add(matcher.group());
                count++;
            }

            return Val.of(matches);
        } catch (Exception e) {
            return Val.error(String.format(ERROR_MATCHING_FAILED, e.getMessage()));
        }
    }

    private static Val findAllSubmatchWithLimit(Val pattern, Val value, int limit) {
        val error = validateInputs(pattern, value);
        if (error != null)
            return error;

        val compiledPattern = compileRegex(pattern.getText());
        if (compiledPattern == null)
            return Val.error(ERROR_DANGEROUS_PATTERN);

        try {
            val matcher = compiledPattern.matcher(value.getText());
            val results = JsonNodeFactory.instance.arrayNode();

            int count = 0;
            while (count < limit && matcher.find()) {
                val matchArray = JsonNodeFactory.instance.arrayNode();
                matchArray.add(matcher.group());

                for (int i = 1; i <= matcher.groupCount(); i++) {
                    val group = matcher.group(i);
                    if (group != null) {
                        matchArray.add(group);
                    } else {
                        matchArray.addNull();
                    }
                }

                results.add(matchArray);
                count++;
            }

            return Val.of(results);
        } catch (Exception e) {
            return Val.error(String.format(ERROR_MATCHING_FAILED, e.getMessage()));
        }
    }

    private static String buildTemplateRegex(String template, String startDelimiter, String endDelimiter) {
        val result   = new StringBuilder();
        int position = 0;

        while (position < template.length()) {
            val startIndex = template.indexOf(startDelimiter, position);

            if (startIndex == -1) {
                val literalPortion = template.substring(position);
                val processed      = processTemplateEscapes(literalPortion);
                result.append(escapeRegexCharacters(processed));
                return result.toString();
            }

            if (startIndex > position) {
                val literalPortion = template.substring(position, startIndex);
                val processed      = processTemplateEscapes(literalPortion);
                result.append(escapeRegexCharacters(processed));
            }

            val endIndex = template.indexOf(endDelimiter, startIndex + startDelimiter.length());
            if (endIndex == -1) {
                return null;
            }

            result.append('(').append(template, startIndex + startDelimiter.length(), endIndex).append(')');
            position = endIndex + endDelimiter.length();
        }

        return result.toString();
    }

    private static void logSecurityEvent(String event, String details) {
        if (log.isWarnEnabled()) {
            log.warn("SECURITY: {} - {}", event, details);
        }
    }

    private record GlobConversionResult(String regexFragment, int nextPosition) {}
}
