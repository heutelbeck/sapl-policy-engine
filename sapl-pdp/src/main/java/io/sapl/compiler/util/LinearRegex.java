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

import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayDeque;

/**
 * Compiles the regular expressions of policies with RE2/J, which matches in
 * time linear in the length of the input and never backtracks. Patterns may
 * come from policies, from authorization subscriptions or from policy
 * information points, so a pattern is hostile input.
 * <p>
 * Linear matching makes a time budget for matching unnecessary. What remains
 * is the size of the compiled program: RE2/J expands counted repetitions, so
 * {@code (?:a{1000}){1000}} compiles to a million instructions, and a third
 * level of nesting exhausts the heap. Matching costs time proportional to the
 * length of the input times the size of the program. Before compiling, the
 * size of the program is therefore estimated from the pattern, and a pattern
 * whose program would exceed {@value #MAX_PROGRAM_SIZE} instructions, or whose
 * groups nest deeper than {@value #MAX_NESTING_DEPTH} levels, is refused like a
 * pattern with a syntax error.
 * <p>
 * The syntax is that of RE2. Look-around, backreferences, atomic groups and
 * possessive quantifiers do not exist in it and are refused. The classes
 * {@code \d}, {@code \w}, {@code \s} and {@code \b} hold ASCII characters
 * only, matching without regard to case follows the simple case folding of
 * Unicode, and {@code .} matches every character except the line feed.
 */
@UtilityClass
public class LinearRegex {

    /**
     * The largest number of instructions a compiled pattern may have.
     */
    public static final int MAX_PROGRAM_SIZE = 10_000;

    /**
     * The deepest nesting of groups a pattern may have.
     */
    public static final int MAX_NESTING_DEPTH = 250;

    static final String ERROR_EXPRESSION_NESTS_TOO_DEEPLY    = "expression nests too deeply";
    static final String ERROR_EXPRESSION_TOO_LARGE           = "expression too large";
    static final String ERROR_UNEXPECTED_CLOSING_PARENTHESIS = "unexpected )";

    private static final int UNBOUNDED         = -1;
    private static final int NO_COUNT          = -2;
    private static final int MAX_REPEAT_COUNT  = 1_000;
    private static final int CAPTURE_SIZE      = 2;
    private static final int MAX_OCTAL_DIGITS  = 3;
    private static final int MAX_REPEAT_DIGITS = 4;

    /**
     * Compiles a pattern for matching in linear time.
     *
     * @param regex the pattern in RE2 syntax
     * @return the compiled pattern
     * @throws PatternSyntaxException if the pattern is invalid, too large, or
     * nests its groups too deeply. {@link PatternSyntaxException#getDescription()}
     * states the reason.
     */
    public static Pattern compile(String regex) {
        new SizeEstimate(regex).check();
        return Pattern.compile(regex);
    }

    /**
     * Walks the pattern once and sums up the instructions RE2/J compiles it to.
     * The walk is iterative, so a deeply nested pattern cannot exhaust the stack
     * here. A pattern RE2/J refuses for its syntax may be estimated in any way,
     * as long as the walk ends. RE2/J reports the syntax error afterwards, except
     * for a closing parenthesis without an open group, which RE2/J reports as an
     * internal error only.
     */
    private static final class SizeEstimate {

        private final String            regex;
        private final ArrayDeque<Group> enclosing = new ArrayDeque<>();
        private Group                   current   = new Group(false);
        private int                     position;

        SizeEstimate(String regex) {
            this.regex = regex;
        }

        void check() {
            while (position < regex.length()) {
                step();
                failIfTooLarge(current.size());
            }
            while (!enclosing.isEmpty()) {
                closeGroup();
                failIfTooLarge(current.size());
            }
        }

        private void step() {
            val character = regex.charAt(position);
            switch (character) {
            case '\\'          -> escape();
            case '['           -> characterClass();
            case '('           -> openGroup();
            case ')'           -> {
                position++;
                if (enclosing.isEmpty()) {
                    throw new PatternSyntaxException(ERROR_UNEXPECTED_CLOSING_PARENTHESIS, regex);
                }
                closeGroup();
            }
            case '|'           -> {
                position++;
                current.alternative();
            }
            case '*', '+', '?' -> {
                position++;
                skipNonGreedyMark();
                current.repeat(character == '+' ? 1 : 0, character == '?' ? 1 : UNBOUNDED);
            }
            case '{'           -> countedRepetition();
            default            -> {
                position += Character.charCount(regex.codePointAt(position));
                current.atom(1);
            }
            }
        }

        private void escape() {
            position++;
            if (position >= regex.length()) {
                current.atom(1);
                return;
            }
            val escaped = regex.charAt(position++);
            switch (escaped) {
            case 'Q'      -> quotedSection();
            case 'p', 'P' -> skipBracedOrSingle();
            case 'x'      -> skipBracedOrHexadecimal();
            default       -> {
                if (escaped >= '0' && escaped <= '7') {
                    skipOctalDigits();
                }
                current.atom(1);
            }
            }
        }

        private void quotedSection() {
            val end        = regex.indexOf("\\E", position);
            val sectionEnd = end < 0 ? regex.length() : end;
            while (position < sectionEnd) {
                position += Character.charCount(regex.codePointAt(position));
                current.atom(1);
                failIfTooLarge(current.size());
            }
            position = end < 0 ? regex.length() : end + 2;
        }

        private void skipBracedOrSingle() {
            if (position < regex.length() && regex.charAt(position) == '{') {
                skipPast('}');
            } else if (position < regex.length()) {
                position++;
            }
            current.atom(1);
        }

        private void skipBracedOrHexadecimal() {
            if (position < regex.length() && regex.charAt(position) == '{') {
                skipPast('}');
            } else {
                position = Math.min(regex.length(), position + 2);
            }
            current.atom(1);
        }

        private void skipOctalDigits() {
            var digits = 1;
            while (digits < MAX_OCTAL_DIGITS && position < regex.length() && regex.charAt(position) >= '0'
                    && regex.charAt(position) <= '7') {
                position++;
                digits++;
            }
        }

        private void skipPast(char terminator) {
            val end = regex.indexOf(terminator, position);
            position = end < 0 ? regex.length() : end + 1;
        }

        private void characterClass() {
            position++;
            if (position < regex.length() && regex.charAt(position) == '^') {
                position++;
            }
            if (position < regex.length() && regex.charAt(position) == ']') {
                position++;
            }
            while (position < regex.length()) {
                val character = regex.charAt(position);
                if (character == ']') {
                    position++;
                    break;
                }
                if (character == '\\') {
                    position++;
                    skipClassEscape();
                } else if (regex.startsWith("[:", position)) {
                    val end = regex.indexOf(":]", position + 2);
                    position = end < 0 ? position + 1 : end + 2;
                } else {
                    position++;
                }
            }
            current.atom(1);
        }

        private void skipClassEscape() {
            if (position >= regex.length()) {
                return;
            }
            val escaped = regex.charAt(position++);
            if ((escaped == 'p' || escaped == 'P' || escaped == 'x') && position < regex.length()
                    && regex.charAt(position) == '{') {
                skipPast('}');
            }
        }

        private void openGroup() {
            position++;
            var capturing = true;
            if (regex.startsWith("?", position)) {
                position++;
                if (regex.startsWith("P<", position) || regex.startsWith("<", position)) {
                    skipPast('>');
                } else {
                    while (position < regex.length() && isFlagCharacter(regex.charAt(position))) {
                        position++;
                    }
                    if (regex.startsWith(")", position)) {
                        position++;
                        return;
                    }
                    if (regex.startsWith(":", position)) {
                        position++;
                    }
                    capturing = false;
                }
            }
            enclosing.push(current);
            if (enclosing.size() > MAX_NESTING_DEPTH) {
                throw new PatternSyntaxException(ERROR_EXPRESSION_NESTS_TOO_DEEPLY, regex);
            }
            current = new Group(capturing);
        }

        private static boolean isFlagCharacter(char character) {
            return Character.isLetter(character) || character == '-';
        }

        private void closeGroup() {
            val group = current;
            current = enclosing.pop();
            current.atom(group.size());
        }

        private void skipNonGreedyMark() {
            if (position < regex.length() && regex.charAt(position) == '?') {
                position++;
            }
        }

        private void countedRepetition() {
            val start = position;
            position++;
            val minimum = readCount();
            if (minimum == NO_COUNT) {
                position = start + 1;
                current.atom(1);
                return;
            }
            var maximum = minimum;
            if (regex.startsWith(",", position)) {
                position++;
                maximum = regex.startsWith("}", position) ? UNBOUNDED : readCount();
            }
            if (maximum == NO_COUNT || !regex.startsWith("}", position)) {
                position = start + 1;
                current.atom(1);
                return;
            }
            position++;
            skipNonGreedyMark();
            current.repeat(minimum, maximum);
        }

        /**
         * Reads a repetition count. Counts above the largest RE2 accepts are read as
         * one more than that, which RE2/J refuses later.
         *
         * @return the count, or {@code NO_COUNT} if no digit stands at the position
         */
        private int readCount() {
            val start = position;
            while (position < regex.length() && regex.charAt(position) >= '0' && regex.charAt(position) <= '9') {
                position++;
            }
            if (position == start) {
                return NO_COUNT;
            }
            if (position - start > MAX_REPEAT_DIGITS) {
                return MAX_REPEAT_COUNT + 1;
            }
            return Math.min(Integer.parseInt(regex, start, position, 10), MAX_REPEAT_COUNT + 1);
        }

        private void failIfTooLarge(long size) {
            if (size > MAX_PROGRAM_SIZE) {
                throw new PatternSyntaxException(ERROR_EXPRESSION_TOO_LARGE, regex);
            }
        }
    }

    /**
     * The estimated size of an open group: the alternatives read so far and the
     * concatenation being read.
     */
    private static final class Group {

        private final boolean capturing;
        private long          alternatives;
        private long          separators;
        private long          concatenation;
        private long          last;

        Group(boolean capturing) {
            this.capturing = capturing;
        }

        void atom(long size) {
            concatenation += size;
            last           = size;
        }

        /**
         * Applies a repetition to the last atom the way RE2 expands it: the minimum
         * as copies, each optional repetition as a copy and a split, and an unbounded
         * maximum as a loop.
         *
         * @param minimum the least number of repetitions
         * @param maximum the largest number of repetitions, or {@code UNBOUNDED} for no
         * bound
         */
        void repeat(int minimum, int maximum) {
            final long repeated;
            if (maximum == UNBOUNDED) {
                repeated = minimum == 0 ? last + 1 : minimum * last + 1;
            } else {
                repeated = minimum * last + Math.max(0, maximum - minimum) * (last + 1);
            }
            concatenation += repeated - last;
            last           = repeated;
        }

        void alternative() {
            alternatives += concatenation;
            separators++;
            concatenation = 0;
            last          = 0;
        }

        long size() {
            return alternatives + concatenation + separators + (capturing ? CAPTURE_SIZE : 0);
        }
    }
}
