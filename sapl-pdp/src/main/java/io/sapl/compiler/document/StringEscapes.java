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
package io.sapl.compiler.document;

import io.sapl.api.model.DocumentLines;
import io.sapl.compiler.diagnostics.Diagnostic;
import io.sapl.compiler.diagnostics.Problem;
import io.sapl.compiler.diagnostics.Region;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.antlr.v4.runtime.Token;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static io.sapl.compiler.util.TrojanSourceUtil.isTrojanSourceCharacter;

/**
 * The escape sequences of a string literal that must not be in a text.
 * <p>
 * A backslash followed by a character that forms no escape sequence, and a
 * unicode escape with fewer than four digits, are mistakes. An escape can also
 * denote a bidirectional control character without the character appearing in
 * the document, which would get it past the check on the text of the document.
 * And an escape can denote one half of a surrogate pair, which is no character
 * on its own.
 */
@UtilityClass
class StringEscapes {

    private static final String ESCAPED_CHARACTERS = "\"'\\/bfnrt";

    private static final int UNICODE_ESCAPE_DIGITS        = 4;
    private static final int UNICODE_ESCAPE_PREFIX_LENGTH = 2;
    private static final int HEXADECIMAL                  = 16;

    /**
     * The first code unit beyond ASCII. Only the hexadecimal digits of ASCII
     * form a unicode escape, not the digits of other scripts.
     */
    private static final int END_OF_ASCII = 0x80;

    /**
     * What stands at one place of the text of a string literal.
     */
    private enum Kind {
        /** A character that stands for itself. */
        CHARACTER,
        /** A backslash with the character after it, as in {@code \n}. */
        ESCAPE,
        /** A backslash, the letter u and four hexadecimal digits: one UTF-16 code unit. */
        CODE_UNIT,
        /** A backslash and the letter u with fewer than four hexadecimal digits. */
        INCOMPLETE_CODE_UNIT
    }

    /**
     * @param kind what stands there
     * @param start the offset in the literal where it starts
     * @param end the offset in the literal where it ends
     * @param value the escaped character or the code unit
     */
    private record Written(Kind kind, int start, int end, int value) {}

    /**
     * @param literal a string literal, closed by its quotation mark
     * @param lines the lines of the document
     * @return the problem of the first escape that must not be in a text, if
     * there is one
     */
    static Optional<Diagnostic> problemOf(Token literal, DocumentLines lines) {
        val text   = literal.getText();
        val offset = Tokens.startOf(literal, lines);
        // The escape of the first half of a surrogate pair, as long as the second
        // half has not followed it.
        Written firstHalf = null;
        var     position  = 0;
        while (position < text.length()) {
            val written = writtenAt(text, position);
            position = written.end();
            if (firstHalf != null && !isLowSurrogate(written)) {
                // Whatever else follows the first half leaves it alone.
                return Optional.of(loneSurrogate(firstHalf, text, offset, lines));
            }
            if (firstHalf != null) {
                // The second half completes the pair.
                firstHalf = null;
            } else if (isHighSurrogate(written)) {
                firstHalf = written;
            } else {
                val problem = problemOf(written);
                if (problem != null) {
                    return Optional.of(diagnostic(problem, written, text, offset, lines));
                }
            }
        }
        return Optional.ofNullable(firstHalf).map(half -> loneSurrogate(half, text, offset, lines));
    }

    private static @Nullable Problem problemOf(Written written) {
        return switch (written.kind()) {
        case CODE_UNIT            -> {
            if (isLowSurrogate(written)) {
                yield Problem.LONE_SURROGATE_ESCAPE;
            }

            yield isTrojanSourceCharacter(written.value()) ? Problem.ESCAPED_BIDIRECTIONAL_CONTROL : null;
        }
        case INCOMPLETE_CODE_UNIT -> Problem.INCOMPLETE_UNICODE_ESCAPE;
        case ESCAPE               -> ESCAPED_CHARACTERS.indexOf(written.value()) < 0 ? Problem.INVALID_ESCAPE : null;
        case CHARACTER            -> null;

        };
    }

    private static Diagnostic loneSurrogate(Written firstHalf, String text, int offset, DocumentLines lines) {
        return diagnostic(Problem.LONE_SURROGATE_ESCAPE, firstHalf, text, offset, lines);
    }

    private static Diagnostic diagnostic(Problem problem, Written written, String text, int offset,
            DocumentLines lines) {
        val escape = text.substring(written.start(), written.end());
        val values = problem.numberOfValues() == 0 ? List.<String>of() : List.of(escape);
        val region = Region.of(lines, offset + written.start(), offset + written.end());
        return new Diagnostic(problem, values, region, List.of(), null);
    }

    private static Written writtenAt(String text, int start) {
        val first = text.codePointAt(start);
        val next  = start + Character.charCount(first);
        if (first != '\\' || next >= text.length()) {
            // A backslash at the end of the text escapes nothing.
            return new Written(Kind.CHARACTER, start, next, first);
        }
        val escaped = text.codePointAt(next);
        if (escaped != 'u') {
            return new Written(Kind.ESCAPE, start, next + Character.charCount(escaped), escaped);
        }
        val digitsStart = start + UNICODE_ESCAPE_PREFIX_LENGTH;
        var digitsEnd   = digitsStart;
        while (digitsEnd < text.length() && digitsEnd - digitsStart < UNICODE_ESCAPE_DIGITS
                && Character.digit(text.charAt(digitsEnd), HEXADECIMAL) >= 0 && text.charAt(digitsEnd) < END_OF_ASCII) {
            digitsEnd++;
        }
        if (digitsEnd - digitsStart < UNICODE_ESCAPE_DIGITS) {
            return new Written(Kind.INCOMPLETE_CODE_UNIT, start, digitsEnd, 0);
        }
        val codeUnit = Integer.parseInt(text, digitsStart, digitsEnd, HEXADECIMAL);
        return new Written(Kind.CODE_UNIT, start, digitsEnd, codeUnit);
    }

    private static boolean isHighSurrogate(Written written) {
        return written.kind() == Kind.CODE_UNIT && Character.isHighSurrogate((char) written.value());
    }

    private static boolean isLowSurrogate(Written written) {
        return written.kind() == Kind.CODE_UNIT && Character.isLowSurrogate((char) written.value());
    }

}
