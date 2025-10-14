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

    private static final int MAX_PATTERN_LENGTH = 1_000;
    private static final int MAX_INPUT_LENGTH   = 100_000;
    private static final int MAX_MATCHES        = 10_000;
    private static final int MAX_GLOB_RECURSION = 50;

    @Function(docs = """
            ```patterns.matchGlob(TEXT pattern, TEXT value, TEXT... delimiters)```: Matches a string
            against a glob pattern with configurable delimiters.

            This function provides flexible pattern matching where delimiter behavior can be customized.
            When delimiters are specified, the single wildcard '*' respects delimiter boundaries while
            the double wildcard '**' can match across them.

            **Supported Pattern Elements:**
            - `*` - Matches any sequence of characters within a delimiter-bounded segment
            - `**` - Matches any sequence including across delimiter boundaries
            - `?` - Matches exactly one character
            - `[abc]` - Matches one character from the set (a, b, or c)
            - `[!abc]` - Matches one character NOT in the set
            - `[a-z]` - Matches one character in the range a through z
            - `[!a-z]` - Matches one character NOT in the range
            - `{cat,dog,bird}` - Matches any of the alternative patterns
            - `\\` - Escapes the next character to treat it literally

            **Delimiter Behavior:**
            - No delimiters provided: Uses default delimiter "." (e.g., for domain names)
            - One or more delimiters: Uses specified delimiters as boundaries
            - Use matchGlobWithoutDelimiters for patterns without any delimiter concept

            **Examples:**
            ```sapl
            policy "demonstrate_glob_matching"
            permit
            where
              var matchesDomain = patterns.matchGlob("*.github.com", "api.github.com");
              // Returns: true

              var matchesPath = patterns.matchGlob("user:*:read", "user:admin:read", ":");
              // Returns: true

              var matchesClass = patterns.matchGlob("[0-9]*.txt", "5file.txt");
              // Returns: true
            ```
            """)
    public static Val matchGlob(@Text Val pattern, @Text Val value, Val... delimiters) {
        val error = validateInputs(pattern, value);
        if (error != null)
            return error;

        val delimiterList = extractDelimiters(delimiters, List.of("."));
        return matchGlobImpl(pattern.getText(), value.getText(), delimiterList);
    }

    @Function(docs = """
            ```patterns.matchGlobWithoutDelimiters(TEXT pattern, TEXT value)```: Matches a string
            against a glob pattern without delimiter boundaries.

            This function treats the entire string as a single segment, allowing wildcards to match
            any characters without restriction.

            **Examples:**
            ```sapl
            policy "demonstrate_glob_without_delimiters"
            permit
            where
              var matchesAll = patterns.matchGlobWithoutDelimiters("*hub.com", "api.cdn.github.com");
              // Returns: true
            ```
            """)
    public static Val matchGlobWithoutDelimiters(@Text Val pattern, @Text Val value) {
        val error = validateInputs(pattern, value);
        if (error != null)
            return error;

        return matchGlobImpl(pattern.getText(), value.getText(), null);
    }

    @Function(docs = """
            ```patterns.escapeGlob(TEXT text)```: Escapes glob metacharacters to treat them as literals.

            This function is essential for safely incorporating untrusted input into glob patterns.

            **Escaped Characters:** * ? [ ] { } \\ - !

            **Examples:**
            ```sapl
            policy "demonstrate_glob_escaping"
            permit
            where
              var userName = "alice*bob";
              var safeName = patterns.escapeGlob(userName);
              // Result: "alice\\*bob"
            ```
            """)
    public static Val escapeGlob(@Text Val text) {
        if (!text.isTextual()) {
            return Val.error("escapeGlob requires a text value");
        }

        val input  = text.getText();
        val result = new StringBuilder(input.length() * 2);

        for (val character : input.toCharArray()) {
            if (isGlobMetachar(character)) {
                result.append('\\');
            }
            result.append(character);
        }

        return Val.of(result.toString());
    }

    @Function(docs = """
            ```patterns.isValidRegex(TEXT pattern)```: Validates if a string is a valid Java regular expression.

            **Examples:**
            ```sapl
            policy "demonstrate_regex_validation"
            permit
            where
              var validPattern = patterns.isValidRegex("[a-z]+");
              // Returns: true
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

            Returns an array containing all non-overlapping matches.

            **Examples:**
            ```sapl
            policy "demonstrate_find_matches"
            permit
            where
              var emails = patterns.findMatches("[a-z]+@[a-z]+\\.com",
                  "Contact: alice@example.com or bob@test.com");
              // Returns: ["alice@example.com", "bob@test.com"]
            ```
            """)
    public static Val findMatches(@Text Val pattern, @Text Val value) {
        return findMatchesWithLimit(pattern, value, MAX_MATCHES);
    }

    @Function(docs = """
            ```patterns.findMatches(TEXT pattern, TEXT value, INT limit)```: Finds up to limit matches.

            **Examples:**
            ```sapl
            var firstTwo = patterns.findMatches("\\d+", "1 2 3 4 5", 2);
            // Returns: ["1", "2"]
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

            Returns a nested array where each element contains the full match and captured groups.

            **Examples:**
            ```sapl
            var emailParts = patterns.findAllSubmatch("([a-z]+)@([a-z]+)\\.com",
                "alice@example.com bob@test.com");
            // Returns: [["alice@example.com", "alice", "example"], ["bob@test.com", "bob", "test"]]
            ```
            """)
    public static Val findAllSubmatch(@Text Val pattern, @Text Val value) {
        return findAllSubmatchWithLimit(pattern, value, MAX_MATCHES);
    }

    @Function(docs = """
            ```patterns.findAllSubmatch(TEXT pattern, TEXT value, INT limit)```: Finds up to limit matches
            with capturing groups.
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

            **Examples:**
            ```sapl
            var redacted = patterns.replaceAll("alice@example.com", "[a-z]+@[a-z]+\\.com", "[REDACTED]");
            // Returns: "[REDACTED]"
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

            **Examples:**
            ```sapl
            var parts = patterns.split(",\\s*", "apple, banana, cherry");
            // Returns: ["apple", "banana", "cherry"]
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

            **Examples:**
            ```sapl
            var matches = patterns.matchTemplate("user-{\\d+}-profile", "user-12345-profile", "{", "}");
            // Returns: true
            ```
            """)
    public static Val matchTemplate(@Text Val template, @Text Val value, @Text Val delimiterStart,
                                    @Text Val delimiterEnd) {
        if (!template.isTextual() || !value.isTextual() || !delimiterStart.isTextual() || !delimiterEnd.isTextual()) {
            return Val.error("All arguments must be text values");
        }

        val templateText = template.getText();
        val valueText    = value.getText();
        val startDelim   = delimiterStart.getText();
        val endDelim     = delimiterEnd.getText();

        if (startDelim.isEmpty() || endDelim.isEmpty()) {
            return Val.error("Delimiters cannot be empty");
        }

        if (templateText.length() > MAX_PATTERN_LENGTH || valueText.length() > MAX_INPUT_LENGTH) {
            return Val.error("Template or value exceeds maximum length");
        }

        val regexPattern = buildTemplateRegex(templateText, startDelim, endDelim);
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

    private static boolean isGlobMetachar(char c) {
        return c == '*' || c == '?' || c == '[' || c == ']' || c == '{' || c == '}' || c == '\\' || c == '-'
                || c == '!';
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

    private static Val matchGlobImpl(String pattern, String value, List<String> delimiters) {
        try {
            val regex = globToRegex(pattern, delimiters, 0);
            return Val.of(Pattern.compile(regex).matcher(value).matches());
        } catch (IllegalStateException e) {
            return Val.error(e.getMessage());
        } catch (PatternSyntaxException e) {
            return Val.error("Invalid glob pattern: " + e.getMessage());
        }
    }

    private static String globToRegex(String glob, List<String> delimiters, int depth) {
        if (depth > MAX_GLOB_RECURSION) {
            throw new IllegalStateException("Glob pattern too deeply nested (max " + MAX_GLOB_RECURSION + " levels)");
        }

        val regex = new StringBuilder("^");
        int pos   = 0;

        while (pos < glob.length()) {
            val currentChar = glob.charAt(pos);
            val handler     = switch (currentChar) {
                case '\\' -> handleEscape(glob, pos);
                case '*'  -> handleWildcard(glob, pos, delimiters);
                case '?'  -> handleSingleChar(pos, delimiters);
                case '['  -> handleCharClass(glob, pos);
                case '{'  -> handleAlternatives(glob, pos, delimiters, depth);
                default   -> handleLiteral(glob, pos);
            };

            regex.append(handler.text);
            pos = handler.nextPos;
        }

        return regex.append('$').toString();
    }

    private static GlobPart handleEscape(String glob, int pos) {
        if (pos + 1 < glob.length()) {
            val escaped = glob.substring(pos + 1, pos + 2);
            return new GlobPart(Pattern.quote(escaped), pos + 2);
        }
        return new GlobPart(Pattern.quote("\\"), pos + 1);
    }

    private static GlobPart handleWildcard(String glob, int pos, List<String> delimiters) {
        if (pos + 1 < glob.length() && glob.charAt(pos + 1) == '*') {
            return new GlobPart(".*", pos + 2);
        }
        return new GlobPart(delimiterAwareMatch(delimiters, true), pos + 1);
    }

    private static GlobPart handleSingleChar(int pos, List<String> delimiters) {
        return new GlobPart(delimiterAwareMatch(delimiters, false), pos + 1);
    }

    private static GlobPart handleCharClass(String glob, int pos) {
        int endBracket = findClosingBracket(glob, pos);
        if (endBracket == -1) {
            return new GlobPart("\\[", pos + 1);
        }

        val charClassContent = glob.substring(pos + 1, endBracket);
        val processed        = new StringBuilder("[");

        int contentPos = 0;
        if (charClassContent.startsWith("!")) {
            processed.append('^');
            contentPos = 1;
        }

        while (contentPos < charClassContent.length()) {
            if (charClassContent.charAt(contentPos) == '\\' && contentPos + 1 < charClassContent.length()) {
                val escapedChar = charClassContent.charAt(contentPos + 1);
                if ("\\]^[".indexOf(escapedChar) >= 0) {
                    processed.append('\\');
                }
                processed.append(escapedChar);
                contentPos += 2;
            } else {
                val currentChar = charClassContent.charAt(contentPos);
                boolean isDash = currentChar == '-';
                boolean isRange = isDash && contentPos > 0 && contentPos < charClassContent.length() - 1;

                if ("\\]^[".indexOf(currentChar) >= 0 || (isDash && !isRange)) {
                    processed.append('\\');
                }
                processed.append(currentChar);
                contentPos++;
            }
        }

        processed.append(']');
        return new GlobPart(processed.toString(), endBracket + 1);
    }

    private static int findClosingBracket(String pattern, int startPos) {
        int pos = startPos + 1;

        while (pos < pattern.length()) {
            if (pattern.charAt(pos) == '\\' && pos + 1 < pattern.length()) {
                pos += 2;
            } else if (pattern.charAt(pos) == ']') {
                return pos;
            } else {
                pos++;
            }
        }

        return -1;
    }

    private static GlobPart handleAlternatives(String glob, int pos, List<String> delimiters, int depth) {
        val closeBrace = findClosingBrace(glob, pos);
        if (closeBrace == -1) {
            return new GlobPart("\\{", pos + 1);
        }

        val alternatives = splitAlternatives(glob.substring(pos + 1, closeBrace));
        val regex        = new StringBuilder("(?:");

        for (int i = 0; i < alternatives.size(); i++) {
            if (i > 0)
                regex.append('|');
            val altRegex = globToRegex(alternatives.get(i), delimiters, depth + 1);
            regex.append(altRegex, 1, altRegex.length() - 1);
        }

        return new GlobPart(regex.append(')').toString(), closeBrace + 1);
    }

    private static GlobPart handleLiteral(String glob, int pos) {
        int codePoint = glob.codePointAt(pos);
        int charCount = Character.charCount(codePoint);
        val literal   = glob.substring(pos, pos + charCount);

        val escaped = new StringBuilder();
        for (val c : literal.toCharArray()) {
            if (".^$*+?()[]{}\\|".indexOf(c) >= 0) {
                escaped.append('\\');
            }
            escaped.append(c);
        }

        return new GlobPart(escaped.toString(), pos + charCount);
    }

    private static String delimiterAwareMatch(List<String> delimiters, boolean multiple) {
        if (delimiters == null || delimiters.isEmpty()) {
            return multiple ? ".*" : ".";
        }

        val chars = new StringBuilder("[^");
        for (val delim : delimiters) {
            for (val c : delim.toCharArray()) {
                if ("\\]^-[".indexOf(c) >= 0) {
                    chars.append('\\');
                }
                chars.append(c);
            }
        }
        chars.append(']');
        return multiple ? chars.append('*').toString() : chars.toString();
    }

    private static int findClosingBrace(String pattern, int startPos) {
        int depth = 1;
        int pos   = startPos + 1;

        while (pos < pattern.length()) {
            if (pattern.charAt(pos) == '\\' && pos + 1 < pattern.length()) {
                pos += 2;
            } else if (pattern.charAt(pos) == '{') {
                depth++;
                pos++;
            } else if (pattern.charAt(pos) == '}') {
                depth--;
                if (depth == 0) {
                    return pos;
                }
                pos++;
            } else {
                pos++;
            }
        }

        return -1;
    }

    private static List<String> splitAlternatives(String alternatives) {
        val result  = new ArrayList<String>();
        val current = new StringBuilder();
        int pos     = 0;
        int depth   = 0;

        while (pos < alternatives.length()) {
            val c = alternatives.charAt(pos);

            if (c == '\\' && pos + 1 < alternatives.length()) {
                current.append(c);
                current.append(alternatives.charAt(pos + 1));
                pos += 2;
            } else if (c == '{') {
                depth++;
                current.append(c);
                pos++;
            } else if (c == '}') {
                depth--;
                current.append(c);
                pos++;
            } else if (c == ',' && depth == 0) {
                result.add(current.toString());
                current.setLength(0);
                pos++;
            } else {
                current.append(c);
                pos++;
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
        return pattern.matches(".*\\([^)]*[*+]\\)[*+].*") || pattern.split("\\|").length > 100;
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

    private static String buildTemplateRegex(String template, String startDelim, String endDelim) {
        val result = new StringBuilder();
        int pos    = 0;

        while (pos < template.length()) {
            val startIndex = template.indexOf(startDelim, pos);

            if (startIndex == -1) {
                result.append(template.substring(pos));
                return result.toString();
            }

            if (startIndex > pos) {
                result.append(template.substring(pos, startIndex));
            }

            val endIndex = template.indexOf(endDelim, startIndex + startDelim.length());
            if (endIndex == -1) {
                return null;
            }

            result.append('(').append(template, startIndex + startDelim.length(), endIndex).append(')');
            pos = endIndex + endDelim.length();
        }

        return result.toString();
    }

    private record GlobPart(String text, int nextPos) {
    }
}