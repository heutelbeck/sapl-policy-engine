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
package io.sapl.compiler;

import java.util.Map;

import lombok.experimental.UtilityClass;

/**
 * Utility class for SAPL string literal processing.
 */
@UtilityClass
public class StringsUtil {

    private static final Map<Character, Character> ESCAPE_CHARS = Map.of('n', '\n', 'r', '\r', 't', '\t', 'b', '\b',
            'f', '\f', '\\', '\\', '"', '"', '\'', '\'', '/', '/');

    /**
     * Removes surrounding quotes and processes escape sequences.
     *
     * @param quoted the raw string token from ANTLR (e.g., {@code "hello\nworld"})
     * @return the unquoted and unescaped string
     */
    public static String unquoteString(String quoted) {
        if (quoted == null || quoted.length() < 2) {
            return quoted;
        }
        var first = quoted.charAt(0);
        var last  = quoted.charAt(quoted.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return unescapeString(quoted.substring(1, quoted.length() - 1));
        }
        return quoted;
    }

    /**
     * Processes escape sequences (\n, \r, \t, \b, \f, \\, \", \', \/, \\uXXXX).
     */
    public static String unescapeString(String text) {
        if (text == null || !text.contains("\\")) {
            return text;
        }
        var result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\\' || i + 1 >= text.length()) {
                result.append(c);
                continue;
            }
            char next = text.charAt(++i);
            if (ESCAPE_CHARS.containsKey(next)) {
                result.append(ESCAPE_CHARS.get(next));
            } else if (next == 'u' && i + 4 < text.length()) {
                try {
                    result.append((char) Integer.parseInt(text.substring(i + 1, i + 5), 16));
                    i += 4;
                } catch (NumberFormatException e) {
                    result.append('\\');
                    i--;
                }
            } else {
                result.append('\\').append(next);
            }
        }
        return result.toString();
    }

}
