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
package io.sapl.util;

import io.sapl.compiler.diagnostics.Region;
import lombok.experimental.UtilityClass;
import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * The documents and places that the tests of places in documents share: lines
 * that end in each of the three ways, and a character outside the basic plane.
 */
@UtilityClass
public class PlacedDocuments {

    /**
     * U+1F600 GRINNING FACE, a character outside the basic plane: one code point,
     * two UTF-16 code units.
     */
    public static final String GRINNING_FACE = "\uD83D\uDE00";

    /**
     * A region of a document for a problem whose place does not matter.
     */
    public static final Region SOMEWHERE = new Region(0, 1, 1, 1, 1, 2);

    /**
     * A document and where a construct stands in it.
     *
     * @param description what is special about the document
     * @param document the SAPL document
     * @param text the text of the construct
     * @param line the line of its first character, from 1
     * @param column the column of its first character, from 1, in characters
     * @param endLine the line of the position after its last character
     * @param endColumn the column of the position after its last character
     */
    public record Placed(
            String description,
            String document,
            String text,
            int line,
            int column,
            int endLine,
            int endColumn) {

        @Override
        public String toString() {
            return description;
        }
    }

    /**
     * @return the three line ends, each with its description
     */
    public static Stream<Arguments> lineEnds() {
        return Stream.of(arguments("a line feed", "\n"), arguments("a carriage return", "\r"),
                arguments("a carriage return and a line feed", "\r\n"));
    }

    /**
     * @param lineEnd what ends the lines
     * @param thirdLine the line after the effect of the policy
     * @return a policy of three lines
     */
    public static String documentOf(String lineEnd, String thirdLine) {
        return String.join(lineEnd, "policy \"p\"", "permit", thirdLine);
    }

    /**
     * @param thirdLine the third line of a policy, in ASCII, that holds the
     * construct
     * @param text the text of the construct
     * @param line the line of its first character, from 1
     * @param column the column of its first character, from 1
     * @param endLine the line of the position after its last character
     * @param endColumn the column of the position after its last character
     * @return the construct in the policy, once for each of the three line ends
     */
    public static Stream<Placed> placedWithEveryLineEnd(String thirdLine, String text, int line, int column,
            int endLine, int endColumn) {
        return Stream.of(
                new Placed("lines that end with a line feed", documentOf("\n", thirdLine), text, line, column, endLine,
                        endColumn),
                new Placed("lines that end with a carriage return", documentOf("\r", thirdLine), text, line, column,
                        endLine, endColumn),
                new Placed("lines that end with a carriage return and a line feed", documentOf("\r\n", thirdLine), text,
                        line, column, endLine, endColumn));
    }

}
