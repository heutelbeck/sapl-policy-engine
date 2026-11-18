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
package io.sapl.lsp.sapl;

import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.params.provider.Arguments;

import lombok.val;
import lombok.experimental.UtilityClass;

/**
 * Reads a document the way an editor does that speaks the language server
 * protocol. A line ends with a line feed, with a carriage return, or with both
 * together as one line break. Lines count from 0, and the character of a
 * position counts the UTF-16 code units from the start of its line.
 * <p>
 * It also holds the documents that the tests of places share: lines that end
 * in each of the three ways, and characters outside the basic plane.
 */
@UtilityClass
public class ProtocolText {

    /**
     * U+1F600 GRINNING FACE, a character outside the basic plane: one code point,
     * two UTF-16 code units.
     */
    public static final String GRINNING_FACE = "\uD83D\uDE00";

    private static final String ERROR_TEXT_NOT_IN_DOCUMENT = "The document does not hold the text '%s'.";

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
     * @param plainLine the third line of a policy, in ASCII
     * @param lineWithFaces the same construct on the third line, after characters
     * outside the basic plane
     * @return the policy with each of the three line ends, and the policy with the
     * faces, each with its description
     */
    public static Stream<Arguments> documentsWithEveryLineEndAndFaces(String plainLine, String lineWithFaces) {
        return Stream.of(arguments("lines that end with a line feed", documentOf("\n", plainLine)),
                arguments("lines that end with a carriage return", documentOf("\r", plainLine)),
                arguments("lines that end with a carriage return and a line feed", documentOf("\r\n", plainLine)),
                arguments("characters outside the basic plane before the place, on its line",
                        documentOf("\n", lineWithFaces)));
    }

    /**
     * @param document the text of the document
     * @param range a range of the protocol
     * @return the text of the document that an editor marks for the range
     */
    public static String textOf(String document, Range range) {
        return document.substring(offsetOf(document, range.getStart()), offsetOf(document, range.getEnd()));
    }

    /**
     * @param document the text of the document
     * @param text a text that the document holds
     * @return the position at which an editor shows the first character of the
     * first occurrence of the text
     */
    public static Position positionOf(String document, String text) {
        val offset = document.indexOf(text);
        if (offset < 0) {
            throw new IllegalArgumentException(ERROR_TEXT_NOT_IN_DOCUMENT.formatted(text));
        }
        val lines = linesOf(document);
        var line  = lines.size() - 1;
        while (lines.get(line).start() > offset) {
            line--;
        }
        return new Position(line, offset - lines.get(line).start());
    }

    /**
     * A character beyond the end of its line stands for the end of that line, as
     * the protocol says. So does a line beyond the last one for the end of the
     * document.
     */
    private static int offsetOf(String document, Position position) {
        val lines = linesOf(document);
        if (position.getLine() >= lines.size()) {
            return document.length();
        }
        val line = lines.get(position.getLine());
        return Math.min(line.start() + position.getCharacter(), line.end());
    }

    /**
     * A line of a document.
     *
     * @param start the offset of its first character
     * @param end the offset after its last character, before its line break
     */
    private record Line(int start, int end) {}

    private static List<Line> linesOf(String document) {
        val lines = new ArrayList<Line>();
        var start = 0;
        var index = 0;
        while (index < document.length()) {
            val character = document.charAt(index);
            val end       = index;
            index++;
            if (character == '\r' && index < document.length() && document.charAt(index) == '\n') {
                index++;
            }
            if (character == '\r' || character == '\n') {
                lines.add(new Line(start, end));
                start = index;
            }
        }
        lines.add(new Line(start, document.length()));
        return lines;
    }

}
