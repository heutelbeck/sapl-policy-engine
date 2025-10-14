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
import io.sapl.api.validation.Int;
import io.sapl.api.validation.Text;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Function library providing pattern matching capabilities for SAPL policies.
 * Includes glob pattern matching with configurable delimiters and comprehensive
 * regex operations with DoS protection mechanisms.
 */
@UtilityClass
@FunctionLibrary(name = PatternsFunctionLibrary.NAME, description = PatternsFunctionLibrary.DESCRIPTION)
public class PatternsFunctionLibrary {

    public static final String NAME        = "patterns";
    public static final String DESCRIPTION = "Functions for pattern matching with globs and regular expressions.";

    private static final int    MAX_PATTERN_LENGTH         = 1_000;
    private static final int    MAX_INPUT_LENGTH           = 100_000;
    private static final int    MAX_MATCHES                = 10_000;
    private static final int    MAX_GLOB_RECURSION         = 50;
    private static final String REGEX_METACHARACTERS       = ".^$*+?()[]{}\\|";
    private static final String CHAR_CLASS_METACHARACTERS  = "\\]^[";
    private static final String GLOB_METACHARACTERS        = "*?[]{}\\-!";

    @Function(docs = """
            ```patterns.matchGlob(TEXT pattern, TEXT value, TEXT... delimiters)```: Matches a string
            against a glob pattern with configurable delimiters.

            Glob patterns provide hierarchical matching where wildcards respect segment boundaries.
            The single wildcard `*` matches zero or more characters within a segment (between delimiters),
            while the double wildcard `**` matches across segment boundaries. When no delimiters are
            specified, the default delimiter `.` is used, suitable for domain names and hierarchical identifiers.

            **Pattern Elements:**
            - `*` - Matches zero or more characters within a delimiter-bounded segment
            - `**` - Matches zero or more characters including across delimiter boundaries
            - `?` - Matches exactly one character
            - `[abc]` or `[a-z]` - Matches one character from the set or range
            - `[!abc]` or `[!a-z]` - Matches one character NOT in the set or range (negation)
            - `{cat,dog,bird}` - Matches any of the alternative patterns
            - `\\` - Escapes the next character (e.g., `\\*` matches literal asterisk)

            **Limitations:**
            - Maximum pattern length: 1,000 characters
            - Maximum input length: 100,000 characters
            - Maximum nesting depth: 50 levels
            - Returns error for malformed patterns (unclosed brackets, braces, etc.)

            **Returns:** Boolean value indicating match success, or error for invalid inputs.

            **Examples:**
            ```sapl
            policy "demonstrate_glob_matching"
            permit
            where
              // Default delimiter '.' - single wildcard respects boundaries
              var domainMatch = patterns.matchGlob("*.github.com", "api.github.com");
              // true - matches single segment before .github.com

              var domainNoMatch = patterns.matchGlob("*.github.com", "api.cdn.github.com");
              // false - single * cannot cross delimiter

              var domainDoubleMatch = patterns.matchGlob("**.github.com", "api.cdn.github.com");
              // true - double ** crosses delimiters

              // Custom delimiter ':' for permission paths
              var pathMatch = patterns.matchGlob("user:*:read", "user:admin:read", ":");
              // true

              // Character classes for specific formats
              var fileMatch = patterns.matchGlob("[0-9]*.txt", "5file.txt");
              // true - starts with digit

              // Alternatives for multiple extensions
              var extensionMatch = patterns.matchGlob("*.{jpg,png,gif}", "photo.jpg");
              // true

              // Escaping special characters
              var literalMatch = patterns.matchGlob("file\\*.txt", "file*.txt");
              // true - asterisk is literal, not wildcard
            ```
            """)
    public static Val matchGlob(@Text Val pattern, @Text Val value, Val... delimiters) {
        val error = validateInputs(pattern, value);
        if (error != null)
            return error;

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

            **Examples:**
            ```sapl
            policy "demonstrate_glob_without_delimiters"
            permit
            where
              // Single * matches across dots without delimiter boundaries
              var matchesDomain = patterns.matchGlobWithoutDelimiters("*hub.com", "api.cdn.github.com");
              // true - whereas matchGlob with default delimiter would return false

              // Flat identifier matching
              var matchesPath = patterns.matchGlobWithoutDelimiters("user:*:read", "user:admin:data:extra:read");
              // true

              // Character classes and alternatives function normally
              var matchesExtension = patterns.matchGlobWithoutDelimiters("*.{txt,log}", "debug.log");
              // true
            ```
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

            **Behavior:**
            - Non-text values return an error
            - Empty strings return empty strings
            - Strings without metacharacters return unchanged content (new allocation)

            **Returns:** Text with all glob metacharacters escaped, or error for non-text input.

            **Examples:**
            ```sapl
            policy "demonstrate_glob_escaping"
            permit
            where
              // Escape user input to prevent pattern injection
              var userName = "alice*bob";
              var safeName = patterns.escapeGlob(userName);
              // "alice\\*bob"

              // Use escaped value in pattern
              var pattern = safeName + ":*:read";
              var matches = patterns.matchGlob(pattern, "alice*bob:data:read", ":");
              // true - literal asterisk in username

              // Without escaping, different behavior occurs
              var unsafeMatch = patterns.matchGlob("alice*bob:data:read", "aliceXXXbob:data:read", ":");
              // true - asterisk interpreted as wildcard

              // Multiple special characters
              var complex = "file[1-9].{txt}";
              var escaped = patterns.escapeGlob(complex);
              // "file\\[1\\-9\\].\\{txt\\}"
            ```
            """)
    public static Val escapeGlob(@Text Val text) {
        if (!text.isTextual()) {
            return Val.error("escapeGlob requires a text value");
        }

        return Val.of(escapeCharacters(text.getText()));
    }

    @Function(docs = """
            ```patterns.isValidRegex(TEXT pattern)```: Validates if a string is a valid Java regular expression.

            This function checks if a pattern can be compiled as a Java regex without errors.
            It uses Java's standard Pattern class for validation.

            **Validation Checks:**
            - Pattern syntax correctness according to Java regex rules
            - Maximum pattern length (1,000 characters)
            - Compiles successfully without PatternSyntaxException

            **Returns:**
            - `true` if pattern is valid and within length limits
            - `false` if pattern is invalid, too long, or not a text value

            **Note:** This function only validates syntax, not pattern safety or performance.
            Patterns can be syntactically valid but still cause performance issues (ReDoS).

            **Examples:**
            ```sapl
            policy "demonstrate_regex_validation"
            permit
            where
              // Valid patterns return true
              var validSimple = patterns.isValidRegex("[a-z]+");
              // Returns: true

              var validComplex = patterns.isValidRegex("(?<name>\\w+)@(?<domain>[\\w.]+)");
              // Returns: true

              // Invalid patterns return false
              var invalidBracket = patterns.isValidRegex("[a-z");
              // Returns: false (unclosed bracket)

              var invalidEscape = patterns.isValidRegex("\\");
              // Returns: false (incomplete escape)

              // Too long patterns return false
              var tooLong = patterns.isValidRegex("a".repeat(1001));
              // Returns: false

              // Non-text values return false
              var notText = patterns.isValidRegex(123);
              // Returns: false
            ```
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

            Scans the input text and returns all non-overlapping matches. Matching proceeds left-to-right,
            and each character participates in at most one match.

            **DoS Protection:**
            - Maximum pattern length: 1,000 characters
            - Maximum input length: 100,000 characters
            - Maximum matches returned: 10,000
            - Rejects patterns with nested quantifiers like `(a+)+`
            - Rejects patterns with more than 100 alternations

            **Returns:** Array of matched strings (empty if no matches), or error for invalid/dangerous patterns.

            **Examples:**
            ```sapl
            policy "demonstrate_find_matches"
            permit
            where
              // Extract all email addresses
              var emails = patterns.findMatches("[a-z]+@[a-z]+\\.com",
                  "Contact: alice@example.com or bob@test.com");
              // ["alice@example.com", "bob@test.com"]

              // Extract all numbers
              var digits = patterns.findMatches("\\d+", "Room 123, Floor 4, Building 56");
              // ["123", "4", "56"]

              // No matches
              var noMatch = patterns.findMatches("\\d+", "no numbers here");
              // []

              // Non-overlapping matches
              var words = patterns.findMatches("\\w+", "hello world");
              // ["hello", "world"]

              // Invalid pattern
              var invalid = patterns.findMatches("[unclosed", "test");
              // error

              // Dangerous pattern rejected
              var dangerous = patterns.findMatches("(a+)+", "aaaaaaaaaa");
              // error
            ```
            """)
    public static Val findMatches(@Text Val pattern, @Text Val value) {
        return findMatchesWithLimit(pattern, value, MAX_MATCHES);
    }

    @Function(docs = """
            ```patterns.findMatches(TEXT pattern, TEXT value, INT limit)```: Finds up to limit matches.

            Identical to findMatches but stops after finding the specified number of matches. The limit
            is capped at 10,000 maximum. Negative limits return an error.

            **Returns:** Array of matched strings with at most the specified number of elements.

            **Examples:**
            ```sapl
            policy "demonstrate_limited_matches"
            permit
            where
              // Get only first 2 matches
              var firstTwo = patterns.findMatches("\\d+", "1 2 3 4 5", 2);
              // ["1", "2"]

              // Limit larger than available matches
              var allMatches = patterns.findMatches("\\d+", "1 2", 5);
              // ["1", "2"]

              // Zero limit
              var none = patterns.findMatches("\\d+", "1 2 3", 0);
              // []

              // Limit above maximum is capped
              var capped = patterns.findMatches(".", "a".repeat(20000), 15000);
              // array with 10000 elements
            ```
            """)
    public static Val findMatches(@Text Val pattern, @Text Val value, @Int Val limit) {
        if (!limit.isNumber()) {
            return Val.error("Limit must be a number");
        }

        val limitValue = limit.get().asInt();
        if (limitValue < 0) {
            return Val.error("Limit must be non-negative");
        }

        return findMatchesWithLimit(pattern, value, Math.min(limitValue, MAX_MATCHES));
    }

    @Function(docs = """
            ```patterns.findAllSubmatch(TEXT pattern, TEXT value)```: Finds all matches with capturing groups.

            Returns matches with their capturing groups. Each match is represented as an array where index 0
            contains the full match and subsequent indices contain captured groups. Non-participating optional
            groups appear as null values.

            **Capturing Groups:**
            - Index 0: full match
            - Index 1+: captured groups in order
            - Named groups are accessed by position (names not preserved)

            **Limitations:**
            - Maximum pattern length: 1,000 characters
            - Maximum input length: 100,000 characters
            - Maximum matches returned: 10,000
            - Same DoS protections as findMatches

            **Returns:** Nested array of matches and their captured groups.

            **Examples:**
            ```sapl
            policy "demonstrate_submatch"
            permit
            where
              // Extract email components
              var emailParts = patterns.findAllSubmatch("([a-z]+)@([a-z]+)\\.com",
                  "alice@example.com bob@test.com");
              // [
              //   ["alice@example.com", "alice", "example"],
              //   ["bob@test.com", "bob", "test"]
              // ]

              // Optional groups
              var optional = patterns.findAllSubmatch("(\\d+)-(\\d+)?", "10-20 30- 40");
              // [
              //   ["10-20", "10", "20"],
              //   ["30-", "30", null],
              //   ...
              // ]

              // No groups
              var noGroups = patterns.findAllSubmatch("\\d+", "10 20 30");
              // [["10"], ["20"], ["30"]]

              // Access specific groups
              var firstMatch = emailParts[0];
              var username = firstMatch[1];
              var domain = firstMatch[2];
            ```
            """)
    public static Val findAllSubmatch(@Text Val pattern, @Text Val value) {
        return findAllSubmatchWithLimit(pattern, value, MAX_MATCHES);
    }

    @Function(docs = """
            ```patterns.findAllSubmatch(TEXT pattern, TEXT value, INT limit)```: Finds up to limit matches
            with capturing groups.

            Identical to findAllSubmatch but stops after the specified number of matches. The limit is capped
            at 10,000 maximum. Negative limits return an error.

            **Returns:** Nested array of at most the specified number of matches with their captured groups.

            **Examples:**
            ```sapl
            policy "demonstrate_limited_submatch"
            permit
            where
              // Get only first match with groups
              var firstEmail = patterns.findAllSubmatch("([a-z]+)@([a-z]+)\\.com",
                  "alice@example.com bob@test.com jane@company.com", 1);
              // [["alice@example.com", "alice", "example"]]

              // Limit processing on large inputs
              var limited = patterns.findAllSubmatch("(\\d+)-(\\d+)",
                  "many number pairs in a very long string...", 5);
              // at most 5 matches
            ```
            """)
    public static Val findAllSubmatch(@Text Val pattern, @Text Val value, @Int Val limit) {
        if (!limit.isNumber()) {
            return Val.error("Limit must be a number");
        }

        val limitValue = limit.get().asInt();
        if (limitValue < 0) {
            return Val.error("Limit must be non-negative");
        }

        return findAllSubmatchWithLimit(pattern, value, Math.min(limitValue, MAX_MATCHES));
    }

    @Function(docs = """
            ```patterns.replaceAll(TEXT value, TEXT pattern, TEXT replacement)```: Replaces all
            occurrences of a regex pattern.

            Finds all matches of the pattern and replaces them with the replacement string.
            Supports backreferences to captured groups in the replacement.

            **Backreferences:**
            - `$0` - full matched text
            - `$1`, `$2`, etc. - captured groups by position
            - `$` - literal dollar sign
            - `\\` - escape character in replacement

            **Limitations:**
            - Maximum pattern length: 1,000 characters
            - Maximum input length: 100,000 characters
            - Same DoS protections as findMatches
            - Replacement must be text (not a number or other type)

            **Examples:**
            ```sapl
            policy "demonstrate_replace_all"
            permit
            where
              // Simple replacement
              var redacted = patterns.replaceAll("alice@example.com, bob@test.com",
                  "[a-z]+@[a-z]+\\.com", "[REDACTED]");
              // Returns: "[REDACTED], [REDACTED]"

              // Swap with backreferences
              var swapped = patterns.replaceAll("John Doe, Jane Smith",
                  "(\\w+) (\\w+)", "$2, $1");
              // Returns: "Doe, John, Smith, Jane"

              // Normalize whitespace
              var normalized = patterns.replaceAll("  hello   world  ",
                  "\\s+", " ");
              // Returns: " hello world "

              // Remove matches (replace with empty string)
              var removed = patterns.replaceAll("abc123def456",
                  "\\d+", "");
              // Returns: "abcdef"

              // No matches returns original
              var unchanged = patterns.replaceAll("no numbers",
                  "\\d+", "X");
              // Returns: "no numbers"

              // Literal dollar sign
              var withDollar = patterns.replaceAll("price: 100",
                  "\\d+", "$50");
              // Returns: "price: $50"
            ```
            """)
    public static Val replaceAll(@Text Val value, @Text Val pattern, @Text Val replacement) {
        val error = validateInputs(pattern, value);
        if (error != null)
            return error;

        if (!replacement.isTextual()) {
            return Val.error("Replacement must be a text value");
        }

        val compiledPattern = compileRegex(pattern.getText());
        if (compiledPattern == null)
            return Val.error("Invalid regex pattern");

        try {
            return Val.of(compiledPattern.matcher(value.getText()).replaceAll(replacement.getText()));
        } catch (Exception e) {
            return Val.error("Replacement failed: " + e.getMessage());
        }
    }

    @Function(docs = """
            ```patterns.split(TEXT pattern, TEXT value)```: Splits a string by a regex pattern.

            Splits the input wherever the pattern matches, returning an array of the segments. Matched
            delimiters are removed from the result. Leading, trailing, or consecutive delimiters create
            empty strings in the result.

            **Behavior:**
            - Maximum splits: 10,000 (remaining text stays together)
            - Empty matches are skipped
            - Leading/trailing delimiters create empty strings
            - Pattern content does not appear in result

            **Limitations:**
            - Maximum pattern length: 1,000 characters
            - Maximum input length: 100,000 characters
            - Same DoS protections as findMatches

            **Returns:** Array of string segments, or error for invalid patterns.

            **Examples:**
            ```sapl
            policy "demonstrate_split"
            permit
            where
              // CSV splitting with optional whitespace
              var parts = patterns.split(",\\s*", "apple, banana,cherry,  date");
              // ["apple", "banana", "cherry", "date"]

              // Split on any whitespace
              var words = patterns.split("\\s+", "hello   world  test");
              // ["hello", "world", "test"]

              // Multiple delimiter types
              var mixed = patterns.split("[|:]", "name:John|age:30|city:NYC");
              // ["name", "John", "age", "30", "city", "NYC"]

              // No matches
              var noSplit = patterns.split(",", "noseparator");
              // ["noseparator"]

              // Leading delimiter
              var leading = patterns.split(",", ",a,b");
              // ["", "a", "b"]

              // Trailing delimiter
              var trailing = patterns.split(",", "a,b,");
              // ["a", "b", ""]

              // Consecutive delimiters
              var consecutive = patterns.split(",", "a,,b");
              // ["a", "", "b"]
            ```
            """)
    public static Val split(@Text Val pattern, @Text Val value) {
        val error = validateInputs(pattern, value);
        if (error != null)
            return error;

        val compiledPattern = compileRegex(pattern.getText());
        if (compiledPattern == null)
            return Val.error("Invalid regex pattern");

        val parts      = compiledPattern.split(value.getText(), MAX_MATCHES);
        val resultNode = JsonNodeFactory.instance.arrayNode();
        for (val part : parts) {
            resultNode.add(part);
        }

        return Val.of(resultNode);
    }

    @Function(docs = """
            ```patterns.matchTemplate(TEXT template, TEXT value, TEXT delimiterStart, TEXT delimiterEnd)```:
            Matches a string against a template with embedded regex patterns.

            Templates combine literal text with regex patterns. Text outside delimiters is matched literally
            (special regex characters have no special meaning). Text inside delimiters is treated as full
            Java regex patterns. Delimiters must be balanced (each opening has matching closing delimiter).

            **Template Syntax:**
            - Literal text: matched character-by-character
            - `{delimiterStart}regex{delimiterEnd}`: regex pattern at this position
            - Delimiters: any non-empty strings

            **Behavior:**
            - Literal portions ignore regex special characters
            - Regex portions use full Java regex syntax
            - Nested delimiters not supported (returns error)

            **Limitations:**
            - Maximum template length: 1,000 characters
            - Maximum value length: 100,000 characters
            - Delimiters cannot be empty
            - Same DoS protections for embedded regex patterns

            **Returns:** Boolean indicating match success, or error for malformed templates.

            **Examples:**
            ```sapl
            policy "demonstrate_template_matching"
            permit
            where
              // URL path validation
              var urlMatch = patterns.matchTemplate("/api/{\\w+}/users/{\\d+}",
                  "/api/v2/users/42", "{", "}");
              // true

              // User ID format
              var idMatch = patterns.matchTemplate("user-{\\d+}-profile",
                  "user-12345-profile", "{", "}");
              // true

              // Email validation (@ and . are literal outside delimiters)
              var emailMatch = patterns.matchTemplate("{[a-z]+}@{[a-z]+}\\.com",
                  "admin@example.com", "{", "}");
              // true

              // Custom delimiters
              var customMatch = patterns.matchTemplate("/api/<\\w+>/resource",
                  "/api/v1/resource", "<", ">");
              // true

              // Mismatch
              var noMatch = patterns.matchTemplate("prefix-{\\d+}-suffix",
                  "prefix-abc-suffix", "{", "}");
              // false

              // Unclosed delimiter
              var error = patterns.matchTemplate("test{pattern",
                  "test123", "{", "}");
              // error

              // Multiple patterns
              var dateMatch = patterns.matchTemplate("{\\d{4}}-{\\d{2}}-{\\d{2}}",
                  "2025-01-15", "{", "}");
              // true
            ```
            """)
    public static Val matchTemplate(@Text Val template, @Text Val value, @Text Val delimiterStart,
                                    @Text Val delimiterEnd) {
        if (!template.isTextual() || !value.isTextual() || !delimiterStart.isTextual() || !delimiterEnd.isTextual()) {
            return Val.error("All arguments must be text values");
        }

        val templateText      = template.getText();
        val valueText         = value.getText();
        val startDelimiter    = delimiterStart.getText();
        val endDelimiter      = delimiterEnd.getText();

        if (startDelimiter.isEmpty() || endDelimiter.isEmpty()) {
            return Val.error("Delimiters cannot be empty");
        }

        if (templateText.length() > MAX_PATTERN_LENGTH || valueText.length() > MAX_INPUT_LENGTH) {
            return Val.error("Template or value exceeds maximum length");
        }

        val regexPattern = buildTemplateRegex(templateText, startDelimiter, endDelimiter);
        if (regexPattern == null) {
            return Val.error("Invalid template format: mismatched or nested delimiters");
        }

        if (isDangerousPattern(regexPattern)) {
            return Val.error("Template contains dangerous regex patterns");
        }

        try {
            return Val.of(Pattern.compile(regexPattern).matcher(valueText).matches());
        } catch (PatternSyntaxException e) {
            return Val.error("Invalid regex in template: " + e.getMessage());
        }
    }

    private static String escapeCharacters(String input) {
        val result = new StringBuilder(input.length() * 2);
        for (val character : input.toCharArray()) {
            if (PatternsFunctionLibrary.GLOB_METACHARACTERS.indexOf(character) >= 0) {
                result.append('\\');
            }
            result.append(character);
        }
        return result.toString();
    }

    private static Val validateInputs(Val pattern, Val value) {
        if (!pattern.isTextual() || !value.isTextual()) {
            return Val.error("Pattern and value must be text values");
        }

        if (pattern.getText().length() > MAX_PATTERN_LENGTH) {
            return Val.error("Pattern too long (max " + MAX_PATTERN_LENGTH + " characters)");
        }

        if (value.getText().length() > MAX_INPUT_LENGTH) {
            return Val.error("Input too long (max " + MAX_INPUT_LENGTH + " characters)");
        }

        return null;
    }

    private static List<String> extractDelimiters(Val[] delimiters, List<String> defaultValue) {
        if (delimiters.length == 0) {
            return defaultValue;
        }

        val result          = new ArrayList<String>(delimiters.length);
        boolean hasNonEmpty = false;

        for (val delimiter : delimiters) {
            if (delimiter.isTextual() && !delimiter.getText().isEmpty()) {
                result.add(delimiter.getText());
                hasNonEmpty = true;
            }
        }

        return hasNonEmpty ? result : null;
    }

    private static Val matchGlobImplementation(String pattern, String value, List<String> delimiters) {
        try {
            val regex = convertGlobToRegex(pattern, delimiters, 0);
            return Val.of(Pattern.compile(regex).matcher(value).matches());
        } catch (IllegalStateException e) {
            return Val.error(e.getMessage());
        } catch (PatternSyntaxException e) {
            return Val.error("Invalid glob pattern: " + e.getMessage());
        }
    }

    private static String convertGlobToRegex(String glob, List<String> delimiters, int recursionDepth) {
        if (recursionDepth > MAX_GLOB_RECURSION) {
            throw new IllegalStateException("Glob pattern too deeply nested (max " + MAX_GLOB_RECURSION + " levels)");
        }

        val regex = new StringBuilder("^");
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

        return regex.append('$').toString();
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
        return new GlobConversionResult("\\\\", position + 1);
    }

    private static GlobConversionResult processWildcard(String glob, int position, List<String> delimiters) {
        if (position + 1 < glob.length() && glob.charAt(position + 1) == '*') {
            return new GlobConversionResult(".*", position + 2);
        }
        return new GlobConversionResult(buildDelimiterAwarePattern(delimiters, true), position + 1);
    }

    private static GlobConversionResult processSingleCharacterWildcard(int position, List<String> delimiters) {
        return new GlobConversionResult(buildDelimiterAwarePattern(delimiters, false), position + 1);
    }

    private static GlobConversionResult processCharacterClass(String glob, int position) {
        int closingBracket = findClosingBracket(glob, position);
        if (closingBracket == -1) {
            return new GlobConversionResult("\\[", position + 1);
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
            return new GlobConversionResult("\\{", position + 1);
        }

        val alternatives = splitAlternatives(glob.substring(position + 1, closingBrace));
        val regex        = new StringBuilder("(?:");

        for (int i = 0; i < alternatives.size(); i++) {
            if (i > 0) {
                regex.append('|');
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

    private static String buildDelimiterAwarePattern(List<String> delimiters, boolean allowMultiple) {
        if (delimiters == null || delimiters.isEmpty()) {
            return allowMultiple ? ".*" : ".";
        }

        val negatedCharClass = new StringBuilder("[^");
        for (val delimiter : delimiters) {
            for (val character : delimiter.toCharArray()) {
                if ("\\]^-[".indexOf(character) >= 0) {
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
        val result           = new ArrayList<String>();
        val current          = new StringBuilder();
        int position         = 0;
        int nestingDepth     = 0;

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
            return null;
        }

        try {
            return Pattern.compile(patternText);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    private static boolean isDangerousPattern(String pattern) {
        if (pattern.split("\\|").length > 100) {
            return true;
        }

        if (pattern.matches(".*\\([^)]*[*+]\\)[*+].*")) {
            return true;
        }

        return pattern.matches(".*\\([^)]*\\|[^)]*\\)[*+].*");
    }

    private static Val findMatchesWithLimit(Val pattern, Val value, int limit) {
        val error = validateInputs(pattern, value);
        if (error != null)
            return error;

        val compiledPattern = compileRegex(pattern.getText());
        if (compiledPattern == null)
            return Val.error("Invalid or dangerous regex pattern");

        val matcher = compiledPattern.matcher(value.getText());
        val matches = JsonNodeFactory.instance.arrayNode();

        int count = 0;
        while (matcher.find() && count < limit) {
            matches.add(matcher.group());
            count++;
        }

        return Val.of(matches);
    }

    private static Val findAllSubmatchWithLimit(Val pattern, Val value, int limit) {
        val error = validateInputs(pattern, value);
        if (error != null)
            return error;

        val compiledPattern = compileRegex(pattern.getText());
        if (compiledPattern == null)
            return Val.error("Invalid or dangerous regex pattern");

        val matcher = compiledPattern.matcher(value.getText());
        val results = JsonNodeFactory.instance.arrayNode();

        int count = 0;
        while (matcher.find() && count < limit) {
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
    }

    private static String buildTemplateRegex(String template, String startDelimiter, String endDelimiter) {
        val result = new StringBuilder();
        int position = 0;

        while (position < template.length()) {
            val startIndex = template.indexOf(startDelimiter, position);

            if (startIndex == -1) {
                result.append(template, position, template.length());
                return result.toString();
            }

            if (startIndex > position) {
                result.append(template, position, startIndex);
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

    private record GlobConversionResult(String regexFragment, int nextPosition) {
    }
}