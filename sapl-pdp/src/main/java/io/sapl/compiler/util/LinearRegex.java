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
package io.sapl.compiler.util;

import com.heutelbeck.rustregex4j.IsMatch;
import com.heutelbeck.rustregex4j.Regex;
import com.heutelbeck.rustregex4j.RegexCompilation;
import com.heutelbeck.rustregex4j.RegexError;
import com.heutelbeck.rustregex4j.RegexLimits;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Compiles the regular expressions of SAPL with the {@code regex} crate of
 * Rust through rustregex4j, which matches in time linear in the length of the
 * text and never backtracks. Patterns may come from policies, from
 * authorization subscriptions or from policy information points, so a pattern
 * is hostile input.
 * <p>
 * The syntax is that of the {@code regex} crate. Look-around, backreferences,
 * atomic groups and possessive quantifiers do not exist in it and are refused.
 * The classes {@code \d}, {@code \w}, {@code \s} and the word boundary
 * {@code \b} are Unicode-aware; {@code (?-u)} or explicit classes such as
 * {@code [0-9]} restrict them to ASCII. Matching without regard to case follows
 * the simple case folding of Unicode, and {@code .} matches every character
 * except the line feed.
 * <p>
 * The limits of {@link #LIMITS} bound the cost of every pattern: its length,
 * the depth of its nesting, the size of its compiled program and of the lazy
 * DFA, and the length of the texts it is matched against. A pattern beyond a
 * limit is refused like a pattern with a syntax error, a text beyond the limit
 * fails the match.
 */
@UtilityClass
public class LinearRegex {

    /** The default length limit of a pattern in UTF-8 bytes. */
    public static final int DEFAULT_MAX_PATTERN_BYTES = 1024;

    /** The default size limit of a compiled pattern in KiB. */
    public static final int DEFAULT_SIZE_LIMIT_KIBIBYTES = 256;

    /** The default length limit of a searched text in UTF-8 bytes. */
    public static final int DEFAULT_MAX_TEXT_BYTES = 64 * 1024;

    /**
     * The default limits for the regular expressions of SAPL: patterns up to 1024
     * UTF-8 bytes, nested up to 64 levels, compiled to at most 256 KiB with a
     * lazy DFA of at most 2 MiB, matched against texts up to 64 KiB of UTF-8.
     * The operator {@code =~} and the patterns of enforced schemas follow the
     * limits of the PDP configuration instead, which default to these.
     */
    public static final RegexLimits LIMITS = limits(DEFAULT_MAX_PATTERN_BYTES, DEFAULT_SIZE_LIMIT_KIBIBYTES,
            DEFAULT_MAX_TEXT_BYTES);

    private static final int  NEST_LIMIT     = 64;
    private static final long DFA_SIZE_LIMIT = 2L * 1024 * 1024;

    static final String ERROR_EXPRESSION_NESTS_TOO_DEEPLY = "expression nests too deeply";
    static final String ERROR_EXPRESSION_TOO_LARGE        = "expression too large";
    static final String ERROR_EXPRESSION_TOO_LONG         = "expression too long";
    static final String ERROR_INTERNAL                    = "internal error of the regular expression engine";
    static final String ERROR_INVALID_ARGUMENT            = "invalid argument";
    static final String ERROR_MALFORMED_UNICODE           = "malformed Unicode";
    static final String ERROR_RESULT_TOO_LONG             = "result too long";
    static final String ERROR_TEXT_TOO_LONG               = "text too long";

    private static final String SYNTAX_ERROR_LINE_PREFIX = "error: ";

    /**
     * Creates limits with the nesting depth and the size of the lazy DFA that
     * SAPL fixes for all regular expressions.
     *
     * @param maxPatternBytes the length limit of a pattern in UTF-8 bytes
     * @param sizeLimitKibibytes the size limit of a compiled pattern in KiB
     * @param maxTextBytes the length limit of a searched text in UTF-8 bytes
     * @return the limits
     * @throws IllegalArgumentException if a limit is not positive
     */
    public static RegexLimits limits(int maxPatternBytes, int sizeLimitKibibytes, int maxTextBytes) {
        return new RegexLimits(maxPatternBytes, NEST_LIMIT, 1024L * sizeLimitKibibytes, DFA_SIZE_LIMIT, maxTextBytes);
    }

    /**
     * Compiles a pattern whose matches may lie anywhere in a text, with the
     * default limits.
     *
     * @param pattern the pattern
     * @return the compiled pattern, or the error that refuses it
     */
    public static RegexCompilation compile(String pattern) {
        return Regex.compile(pattern, LIMITS);
    }

    /**
     * Compiles a pattern that matches a text only if it matches the whole text,
     * with the default limits.
     *
     * @param pattern the pattern
     * @return the compiled pattern, or the error that refuses it
     */
    public static RegexCompilation compileWholeText(String pattern) {
        return compileWholeText(pattern, LIMITS);
    }

    /**
     * Compiles a pattern that matches a text only if it matches the whole text, as
     * the operator {@code =~} demands.
     *
     * @param pattern the pattern
     * @param limits the limits of the PDP configuration
     * @return the compiled pattern, or the error that refuses it
     */
    public static RegexCompilation compileWholeText(String pattern, RegexLimits limits) {
        return Regex.compileWholeText(pattern, limits);
    }

    /**
     * Compiles a pattern whose matches may lie anywhere in a text, where an
     * interface demands an exception for a refused pattern, as for a constant of
     * the code.
     *
     * @param pattern the pattern
     * @return the compiled pattern
     * @throws RegexRefusedException if the pattern is refused
     */
    public static Regex compileOrRefuse(String pattern) {
        return compileOrRefuse(pattern, LIMITS);
    }

    /**
     * Compiles a pattern whose matches may lie anywhere in a text, where an
     * interface demands an exception for a refused pattern.
     *
     * @param pattern the pattern
     * @param limits the limits of the PDP configuration
     * @return the compiled pattern
     * @throws RegexRefusedException if the pattern is refused
     */
    public static Regex compileOrRefuse(String pattern, RegexLimits limits) {
        return orRefuse(pattern, Regex.compile(pattern, limits));
    }

    /**
     * Compiles a pattern that must match the whole text, where an interface
     * demands an exception for a refused pattern, as for a constant of the code.
     *
     * @param pattern the pattern
     * @return the compiled pattern
     * @throws RegexRefusedException if the pattern is refused
     */
    public static Regex compileWholeTextOrRefuse(String pattern) {
        return orRefuse(pattern, compileWholeText(pattern));
    }

    /**
     * Reports whether a compiled pattern matches a text, where an interface
     * demands a boolean. A text the engine cannot search, because it exceeds the
     * text limit or holds an unpaired surrogate, is neither a match nor a
     * non-match, so it fails with an exception instead.
     *
     * @param regex the compiled pattern
     * @param text the text
     * @return whether the pattern matches the text
     * @throws RegexSearchFailedException if the engine cannot search the text
     */
    public static boolean matches(Regex regex, String text) {
        return switch (regex.isMatch(text)) {
        case IsMatch(var matches) -> matches;
        case RegexError error     -> throw new RegexSearchFailedException(regex.pattern(), describe(error));
        };
    }

    private static Regex orRefuse(String pattern, RegexCompilation compilation) {
        return switch (compilation) {
        case Regex regex      -> regex;
        case RegexError error -> throw new RegexRefusedException(pattern, describe(error));
        };
    }

    /**
     * Describes why a pattern was refused or a search failed, in the words SAPL
     * reports. A syntax error is described by the
     * last line of the message of the parser, which names the error without the
     * pattern.
     *
     * @param error the error
     * @return the description, a lower case phrase without a final period
     */
    public static String describe(RegexError error) {
        return switch (error.kind()) {
        case SYNTAX            -> syntaxErrorDescription(error.message());
        case NESTING_TOO_DEEP  -> ERROR_EXPRESSION_NESTS_TOO_DEEPLY;
        case PATTERN_TOO_LONG  -> ERROR_EXPRESSION_TOO_LONG;
        case PATTERN_TOO_LARGE -> ERROR_EXPRESSION_TOO_LARGE;
        case MALFORMED_UNICODE -> ERROR_MALFORMED_UNICODE;
        case TEXT_TOO_LONG     -> ERROR_TEXT_TOO_LONG;
        case INVALID_ARGUMENT  -> ERROR_INVALID_ARGUMENT;
        case RESULT_TOO_LONG   -> ERROR_RESULT_TOO_LONG;
        case INTERNAL          -> ERROR_INTERNAL;
        };
    }

    private static String syntaxErrorDescription(String message) {
        val lines = message.strip().lines().toList();
        val last  = lines.isEmpty() ? "" : lines.getLast().strip();
        return last.startsWith(SYNTAX_ERROR_LINE_PREFIX) ? last.substring(SYNTAX_ERROR_LINE_PREFIX.length()) : last;
    }
}
