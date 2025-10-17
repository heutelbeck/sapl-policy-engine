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
 * Pattern matching functions for authorization policies.
 * <p>
 * This library provides two pattern matching approaches for different
 * scenarios:
 * <p>
 * <b>Glob Patterns</b> - Hierarchical wildcard matching that respects segment
 * boundaries defined by delimiters. Wildcards match within segments unless
 * explicitly crossing boundaries with double wildcards. Use glob patterns for
 * file paths, domain names, or structured identifiers where hierarchical
 * structure matters. The syntax is simpler and more intuitive for non-technical
 * users than regular expressions.
 * <p>
 * <b>Regular Expressions</b> - Full pattern matching using standard regex
 * syntax following the same construction rules as the Java Pattern class. Use
 * regex when glob patterns are insufficient or when advanced matching features
 * like lookahead, backreferences, or precise quantifiers are needed. Regular
 * expressions are more powerful but require familiarity with regex syntax.
 * <p>
 * For simple literal operations like checking prefixes, suffixes, or
 * substrings,
 * use the string library instead. It is faster and more straightforward for
 * these common cases.
 * <p>
 * <b>Security</b>: All regex functions include protection against Regular
 * Expression Denial of Service attacks. Patterns with dangerous constructs like
 * nested quantifiers or excessive alternations are rejected before evaluation.
 */
@Slf4j
@UtilityClass
@FunctionLibrary(name = PatternsFunctionLibrary.NAME, description = PatternsFunctionLibrary.DESCRIPTION, libraryDocumentation = PatternsFunctionLibrary.DOCUMENTATION)
public class PatternsFunctionLibrary {

    public static final String NAME        = "patterns";
    public static final String DESCRIPTION = "Pattern matching with glob patterns and regular expressions for authorization policies.";

    public static final String DOCUMENTATION = """
            # Pattern Matching in Authorization Policies

            This library provides two complementary approaches for pattern matching in access control decisions.

            ## Glob Patterns

            Glob patterns provide hierarchical wildcard matching that respects segment boundaries. Use them when:
            - Matching file paths, domain names, or other hierarchical identifiers
            - Segment boundaries matter for your matching logic
            - Non-technical users need to write or understand patterns

            Glob patterns support:
            - `*` - Matches zero or more characters within a segment
            - `**` - Matches across segment boundaries
            - `?` - Matches exactly one character
            - `[abc]` or `[a-z]` - Character sets and ranges
            - `[!abc]` - Negated character sets
            - `{cat,dog,bird}` - Alternatives
            - `\\` - Escape character for literal matching

            Delimiters define segment boundaries. When not specified, `.` is used by default.

            ## Regular Expressions

            Regular expressions provide full pattern matching power using standard regex syntax. Construction
            rules follow the Java Pattern class specification. Use them when:
            - Glob patterns cannot express the required matching logic
            - Advanced features like lookahead, backreferences, or precise quantifiers are needed
            - Complex validation rules require regex capabilities

            ## When to Use Simple String Operations

            For literal prefix, suffix, or substring checks, use the string library instead. Functions like
            `string.startsWith` or `string.contains` are faster and clearer for these common cases.

            ## Security

            All regex functions include protection against Regular Expression Denial of Service attacks.
            Patterns containing dangerous constructs like nested quantifiers `(a+)+`, excessive alternations,
            or nested wildcards are rejected before evaluation.
            """;

    private static final int    MAX_PATTERN_LENGTH        = 1_000;
    private static final int    MAX_INPUT_LENGTH          = 100_000;
    private static final int    MAX_MATCHES               = 10_000;
    private static final int    MAX_GLOB_RECURSION        = 50;
    private static final int    MAX_ALTERNATIONS          = 100;
    private static final int    MAX_ALTERNATIVE_GROUPS    = 30;
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
    private static final String ERROR_TOO_MANY_ALT_GROUPS = "Too many alternative groups (max %d groups)";

    private static final String REGEX_ANCHOR_START      = "^";
    private static final char   REGEX_ANCHOR_END        = '$';
    private static final String REGEX_ANY_CHAR_MULTIPLE = ".*";
    private static final String REGEX_ANY_CHAR_SINGLE   = ".";
    private static final String REGEX_DOUBLE_BACKSLASH  = "\\\\";
    private static final String REGEX_GROUP_START       = "(?:";
    private static final char   REGEX_ALTERNATION       = '|';

    /**
     * Pre-compiled patterns for ReDoS detection.
     * These patterns use find() without greedy quantifiers at boundaries to
     * ensure safe detection.
     */
    private static final Pattern NESTED_QUANTIFIERS     = Pattern.compile("\\([^)]*[*+]\\)[*+]");
    private static final Pattern ALTERNATION_WITH_QUANT = Pattern.compile("\\([^)]*\\|[^)]*\\)[*+]");
    private static final Pattern NESTED_WILDCARDS       = Pattern.compile("\\([^)]*\\*[^)]*\\)[^)]*\\*");
    private static final Pattern NESTED_BOUNDED_QUANT   = Pattern.compile("\\{\\d+,\\d*}[^{]*\\{\\d+,\\d*}");

    @Function(docs = """
            ```matchGlob(TEXT pattern, TEXT value, ARRAY delimiters)```: Matches text against a glob pattern.

            Performs hierarchical wildcard matching where wildcards respect segment boundaries defined by
            delimiters. Use this for file paths, domain names, or structured identifiers where hierarchical
            structure matters.

            **When to Use**:
            - File paths: `/api/*/documents` matches `/api/v1/documents` but not `/api/v1/admin/documents`
            - Domain names: `*.example.com` matches `api.example.com` but not `foo.api.example.com`
            - Hierarchical permissions: `user:*:read` matches `user:profile:read` within segments

            **Default Delimiter**: When delimiters array is empty or not provided, `.` is used as the
            delimiter.

            **Examples**:
            ```sapl
            policy "api_paths"
            permit
            where
              patterns.matchGlob("/api/*/users/*", resource.path, ["/"]);
            ```

            ```sapl
            policy "domain_matching"
            permit
            where
              patterns.matchGlob("*.company.com", request.host, ["."]);
            ```

            ```sapl
            policy "permission_hierarchy"
            permit
            where
              patterns.matchGlob("document:*:read", subject.permission, [":"]);
            ```

            ```sapl
            policy "file_extensions"
            permit
            where
              patterns.matchGlob("report.{pdf,docx,xlsx}", resource.filename, ["."]);
            ```

            ```sapl
            policy "cross_segment"
            permit
            where
              patterns.matchGlob("/api/**/admin", resource.path, ["/"]);
            ```
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
            ```matchGlobWithoutDelimiters(TEXT pattern, TEXT value)```: Matches text against a glob pattern
            without segment boundaries.

            Performs flat wildcard matching where all wildcards match any characters without restriction.
            Both `*` and `**` behave identically. Use this for simple filename matching or flat strings without
            hierarchical structure.

            **Examples**:
            ```sapl
            policy "filename_only"
            permit
            where
              patterns.matchGlobWithoutDelimiters("report_*.pdf", resource.filename);
            ```

            ```sapl
            policy "simple_wildcard"
            permit
            where
              patterns.matchGlobWithoutDelimiters("user_*_token", subject.sessionToken);
            ```

            ```sapl
            policy "alternatives"
            permit
            where
              patterns.matchGlobWithoutDelimiters("status_{active,pending}", resource.status);
            ```
            """)
    public static Val matchGlobWithoutDelimiters(@Text Val pattern, @Text Val value) {
        val error = validateInputs(pattern, value);
        if (error != null)
            return error;

        return matchGlobImplementation(pattern.getText(), value.getText(), null);
    }

    @Function(docs = """
            ```escapeGlob(TEXT text)```: Escapes all glob metacharacters to treat input as literal text.

            Prepends backslash to glob special characters. Essential for safely incorporating untrusted
            input into glob patterns to prevent pattern injection where malicious input could match
            unintended values.

            **Examples**:
            ```sapl
            policy "safe_user_input"
            permit
            where
              var safeUsername = patterns.escapeGlob(request.username);
              var pattern = string.concat("/users/", safeUsername, "/*");
              patterns.matchGlob(pattern, resource.path, ["/"]);
            ```

            ```sapl
            policy "literal_match"
            permit
            where
              var escaped = patterns.escapeGlob(resource.tag);
              escaped == "literal*text";
            ```
            """)
    public static Val escapeGlob(@Text Val text) {
        if (!text.isTextual()) {
            return Val.error(ERROR_ESCAPE_GLOB_TEXT);
        }

        return Val.of(escapeGlobCharacters(text.getText()));
    }

    @Function(docs = """
            ```isValidRegex(TEXT pattern)```: Checks if text is a valid regular expression.

            Returns true if the pattern is syntactically valid and within length limits, false otherwise.

            **Examples**:
            ```sapl
            policy "validate_pattern"
            permit
            where
              patterns.isValidRegex(resource.customPattern);
            ```

            ```sapl
            policy "check_before_use"
            permit
            where
              var pattern = request.filterPattern;
              patterns.isValidRegex(pattern) && patterns.findMatches(pattern, resource.text) != [];
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
            ```findMatches(TEXT pattern, TEXT value)```: Finds all matches of a regex pattern.

            Returns all non-overlapping matches. Maximum 10,000 matches returned. Patterns with dangerous
            constructs are rejected.

            **Examples**:
            ```sapl
            policy "extract_emails"
            permit
            where
              var emails = patterns.findMatches("[a-z0-9._%+-]+@[a-z0-9.-]+\\\\.[a-z]{2,}", resource.text);
              array.size(emails) > 0;
            ```

            ```sapl
            policy "find_tags"
            permit
            where
              var tags = patterns.findMatches("#[a-zA-Z0-9]+", resource.content);
              array.containsAll(tags, subject.allowedTags);
            ```

            ```sapl
            policy "extract_numbers"
            permit
            where
              var numbers = patterns.findMatches("\\\\d+", resource.input);
              array.size(numbers) <= 10;
            ```
            """)
    public static Val findMatches(@Text Val pattern, @Text Val value) {
        return findMatchesWithLimit(pattern, value, MAX_MATCHES);
    }

    @Function(docs = """
            ```findMatchesLimited(TEXT pattern, TEXT value, INT limit)```: Finds up to limit matches of a
            regex pattern.

            Stops searching after finding the specified number of matches. Limit is capped at 10,000.

            **Examples**:
            ```sapl
            policy "first_ten_matches"
            permit
            where
              var matches = patterns.findMatchesLimited("\\\\b\\\\w+\\\\b", resource.text, 10);
              array.size(matches) == 10;
            ```

            ```sapl
            policy "limited_extraction"
            permit
            where
              var urls = patterns.findMatchesLimited("https://[^\\\\s]+", resource.document, 5);
              array.size(urls) <= 5;
            ```
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
            ```findAllSubmatch(TEXT pattern, TEXT value)```: Finds all regex matches with their capturing groups.

            Each match returns an array where index 0 is the full match and subsequent indices are captured
            groups from parentheses in the pattern.

            **Examples**:
            ```sapl
            policy "parse_permissions"
            permit
            where
              var matches = patterns.findAllSubmatch("(\\\\w+):(\\\\w+):(\\\\w+)", subject.permissions);
              array.size(matches) > 0;
            ```

            ```sapl
            policy "extract_structured"
            permit
            where
              var data = patterns.findAllSubmatch("user=([^,]+),role=([^,]+)", resource.metadata);
              var firstMatch = data[0];
              firstMatch[2] == "admin";
            ```
            """)
    public static Val findAllSubmatch(@Text Val pattern, @Text Val value) {
        return findAllSubmatchWithLimit(pattern, value, MAX_MATCHES);
    }

    @Function(docs = """
            ```findAllSubmatchLimited(TEXT pattern, TEXT value, INT limit)```: Finds up to limit matches with
            capturing groups.

            Each match array contains the full match at index 0 and captured groups at subsequent indices.

            **Examples**:
            ```sapl
            policy "limited_parsing"
            permit
            where
              var matches = patterns.findAllSubmatchLimited("(\\\\d{4})-(\\\\d{2})-(\\\\d{2})", resource.dates, 3);
              array.size(matches) <= 3;
            ```
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
            ```replaceAll(TEXT value, TEXT pattern, TEXT replacement)```: Replaces all regex matches with
            replacement text.

            Replacement can include backreferences like `$1` to refer to captured groups from parentheses
            in the pattern.

            **Examples**:
            ```sapl
            policy "redact_emails"
            permit
            where
              var redacted = patterns.replaceAll(resource.text, "[a-z0-9._%+-]+@[a-z0-9.-]+\\\\.[a-z]{2,}", "[REDACTED]");
              resource.publicText == redacted;
            ```

            ```sapl
            policy "normalize_paths"
            permit
            where
              var normalized = patterns.replaceAll(resource.path, "/+", "/");
              string.startsWith(normalized, "/api");
            ```

            ```sapl
            policy "swap_format"
            permit
            where
              var swapped = patterns.replaceAll(resource.date, "(\\\\d{2})/(\\\\d{2})/(\\\\d{4})", "$3-$1-$2");
              swapped == resource.expectedFormat;
            ```
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
            ```split(TEXT pattern, TEXT value)```: Splits text by a regex pattern into array segments.

            Each match of the pattern becomes a boundary where the string is divided.

            **Examples**:
            ```sapl
            policy "parse_csv"
            permit
            where
              var fields = patterns.split(",", resource.csvLine);
              array.size(fields) == 5;
            ```

            ```sapl
            policy "split_whitespace"
            permit
            where
              var tokens = patterns.split("\\\\s+", resource.input);
              array.containsAll(tokens, subject.requiredTokens);
            ```

            ```sapl
            policy "split_delimiters"
            permit
            where
              var parts = patterns.split("[;,|]", resource.delimitedData);
              array.size(parts) > 0;
            ```
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
            ```matchTemplate(TEXT template, TEXT value, TEXT delimiterStart, TEXT delimiterEnd)```: Matches text
            against a template with embedded regex patterns.

            Combines literal text matching with embedded regular expressions. Text outside delimiters is
            matched literally with backslash escapes. Text inside delimiters is treated as regex patterns.

            **When to Use**:
            - Mix literal text with dynamic patterns
            - Build patterns from configuration without escaping entire strings
            - Separate static structure from variable matching logic

            **Template Syntax**:
            - Literal portions: Text outside delimiters, use `\\` to escape special characters
            - Pattern portions: Text between `delimiterStart` and `delimiterEnd`
            - Escape sequences: `\\*` becomes literal `*`, `\\\\` becomes literal `\\`

            **Examples**:
            ```sapl
            policy "api_version"
            permit
            where
              patterns.matchTemplate("/api/{{v[12]}}/users", resource.path, "{{", "}}");
            ```

            ```sapl
            policy "structured_id"
            permit
            where
              patterns.matchTemplate("tenant:{{\\\\d+}}:resource", resource.id, "{{", "}}");
            ```

            ```sapl
            policy "mixed_format"
            permit
            where
              var template = "user-{{[a-z]+}}-{{\\\\d{4}}}";
              patterns.matchTemplate(template, resource.userId, "{{", "}}");
            ```

            ```sapl
            policy "escaped_literals"
            permit
            where
              patterns.matchTemplate("file\\\\*{{\\\\d+}}\\\\.txt", resource.filename, "{{", "}}");
            ```
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

    /**
     * Escapes all glob metacharacters in the input string.
     */
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

    /**
     * Processes backslash escape sequences in template literal portions.
     */
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

    /**
     * Escapes all regex metacharacters in the input string.
     */
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

    /**
     * Validates that pattern and value are textual and within size limits.
     */
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

    /**
     * Validates that delimiters array contains only textual values.
     */
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

    /**
     * Extracts delimiter strings from Val array, returning default if undefined
     * or empty.
     */
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

    /**
     * Implements glob pattern matching by converting glob to regex and matching.
     */
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

    /**
     * Converts a glob pattern to equivalent regex pattern.
     */
    private static String convertGlobToRegex(String glob, List<String> delimiters, int recursionDepth) {
        if (recursionDepth > MAX_GLOB_RECURSION) {
            throw new IllegalStateException(ERROR_GLOB_TOO_NESTED.formatted(MAX_GLOB_RECURSION));
        }

        val alternativeGroupCount = countAlternativeGroups(glob);
        if (alternativeGroupCount > MAX_ALTERNATIVE_GROUPS) {
            throw new IllegalStateException(ERROR_TOO_MANY_ALT_GROUPS.formatted(MAX_ALTERNATIVE_GROUPS));
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

    /**
     * Counts the number of alternative groups in a glob pattern.
     */
    private static int countAlternativeGroups(String glob) {
        int count    = 0;
        int position = 0;
        int depth    = 0;

        while (position < glob.length()) {
            val character = glob.charAt(position);

            if (character == '\\' && position + 1 < glob.length()) {
                position += 2;
            } else if (character == '[') {
                int closingBracket = findClosingBracket(glob, position);
                position = closingBracket != -1 ? closingBracket + 1 : position + 1;
            } else if (character == '{') {
                if (depth == 0) {
                    count++;
                }
                depth++;
                position++;
            } else if (character == '}') {
                depth--;
                position++;
            } else {
                position++;
            }
        }

        return count;
    }

    /**
     * Processes backslash escape sequence in glob pattern.
     */
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

    /**
     * Processes wildcard character in glob pattern.
     */
    private static GlobConversionResult processWildcard(String glob, int position, Collection<String> delimiters) {
        if (position + 1 < glob.length() && glob.charAt(position + 1) == '*') {
            return new GlobConversionResult(REGEX_ANY_CHAR_MULTIPLE, position + 2);
        }
        return new GlobConversionResult(buildDelimiterAwarePattern(delimiters, true), position + 1);
    }

    /**
     * Processes single character wildcard in glob pattern.
     */
    private static GlobConversionResult processSingleCharacterWildcard(int position, Collection<String> delimiters) {
        return new GlobConversionResult(buildDelimiterAwarePattern(delimiters, false), position + 1);
    }

    /**
     * Processes character class in glob pattern.
     */
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

    /**
     * Determines if a character needs escaping within a character class.
     */
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

    /**
     * Processes alternative group in glob pattern.
     */
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

    /**
     * Processes literal character in glob pattern.
     */
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

    /**
     * Builds regex pattern that respects delimiter boundaries.
     */
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

    /**
     * Finds the closing brace matching an opening brace, handling nesting.
     */
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

    /**
     * Finds the closing bracket matching an opening bracket.
     */
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

    /**
     * Splits alternative group content by commas at nesting depth zero.
     */
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

    /**
     * Compiles regex pattern after validating it is not dangerous.
     */
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

    /**
     * Analyzes regex pattern for dangerous constructs that could cause ReDoS.
     * Uses pre-compiled patterns with find() to avoid ReDoS in detection itself.
     */
    private static boolean isDangerousPattern(String pattern) {
        if (pattern.split("\\|").length > MAX_ALTERNATIONS) {
            return true;
        }

        if (NESTED_QUANTIFIERS.matcher(pattern).find()) {
            return true;
        }

        if (ALTERNATION_WITH_QUANT.matcher(pattern).find()) {
            return true;
        }

        if (NESTED_WILDCARDS.matcher(pattern).find()) {
            return true;
        }

        if (NESTED_BOUNDED_QUANT.matcher(pattern).find()) {
            return true;
        }

        return pattern.contains(".*.*") || pattern.contains(".+.+");
    }

    /**
     * Finds all matches of regex pattern in value, up to specified limit.
     */
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

    /**
     * Finds all matches with capturing groups, up to specified limit.
     */
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

    /**
     * Builds regex pattern from template with embedded regex patterns.
     */
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

    /**
     * Logs security-related events at WARN level.
     */
    private static void logSecurityEvent(String event, String details) {
        if (log.isWarnEnabled()) {
            log.warn("SECURITY: {} - {}", event, details);
        }
    }

    /**
     * Holds the result of converting a glob pattern fragment to regex.
     */
    private record GlobConversionResult(String regexFragment, int nextPosition) {}
}
