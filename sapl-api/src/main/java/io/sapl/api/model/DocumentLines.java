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
package io.sapl.api.model;

import lombok.NonNull;
import lombok.val;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * The lines of the text of a document and the places in it. Every tool that
 * names a place in a document, from error reports to the language server,
 * counts lines and columns here, so all of them agree.
 * <p>
 * A place is an offset into the text: the number of UTF-16 code units before
 * it, as a Java string indexes it. A range of the text ends exclusively.
 * <p>
 * A line ends at a line feed, at a carriage return, or at a carriage return
 * followed by a line feed, which together are one line break. This is the rule
 * of the language server protocol. The text after the last line break is the
 * last line, so a text that ends with a line break has an empty last line, and
 * an empty text has one empty line. The line break belongs to no line.
 * <p>
 * For people, a place has a line and a column, both counted from 1. The column
 * counts code points: a character outside the basic multilingual plane is one
 * column, although the string holds it in two code units. The language server
 * protocol counts lines from 0 and the character of a position in UTF-16 code
 * units from the start of its line; both follow from {@link #lineOf(int)} and
 * {@link #startOfLine(int)}.
 * <p>
 * The parser of SAPL, built with ANTLR, reports places in its own numbers: the
 * index of a code point in the text, and a line that ends only at a line feed,
 * counted from 1, with the position in that line counted in code points from 0.
 * {@link #offsetOfCodePoint(int)} and
 * {@link #offsetOfParserPosition(int, int)} translate these numbers into
 * offsets, {@link #parserLineOf(int)} and {@link #charPositionInLineOf(int)}
 * translate back.
 * <p>
 * An instance is immutable. It is built in one pass over the text, once per
 * document, and every lookup afterwards is a binary search that does not walk
 * the text. A text without characters outside the basic multilingual plane
 * needs no conversion between code points and offsets. A line or an offset
 * outside the document is taken as the nearest place in the document.
 */
public final class DocumentLines {

    private final String text;
    private final int[]  lineStarts;
    private final int[]  lineFeedLineStarts;
    private final int[]  surrogatePairOffsets;

    private DocumentLines(String text, int[] lineStarts, int[] lineFeedLineStarts, int[] surrogatePairOffsets) {
        this.text                 = text;
        this.lineStarts           = lineStarts;
        this.lineFeedLineStarts   = lineFeedLineStarts;
        this.surrogatePairOffsets = surrogatePairOffsets;
    }

    /**
     * Counts the lines of a text.
     *
     * @param text the text of a document
     * @return the lines of the text
     * @throws NullPointerException if the text is null
     */
    public static DocumentLines of(@NonNull String text) {
        val lineStarts           = IntStream.builder().add(0);
        val surrogatePairOffsets = IntStream.builder();
        var loneCarriageReturn   = false;
        var offset               = 0;
        while (offset < text.length()) {
            val character = text.charAt(offset);
            val next      = offset + 1 < text.length() ? text.charAt(offset + 1) : 0;
            if (character == '\n') {
                lineStarts.add(offset + 1);
            } else if (character == '\r') {
                // A carriage return before a line feed is one line break with it, which
                // the line feed ends.
                if (next != '\n') {
                    lineStarts.add(offset + 1);
                    loneCarriageReturn = true;
                }
            } else if (Character.isHighSurrogate(character) && Character.isLowSurrogate(next)) {
                surrogatePairOffsets.add(offset);
                offset++;
            }
            offset++;
        }
        val starts = lineStarts.build().toArray();
        // The lines of the parser differ only where a carriage return ends a line
        // alone.
        val lineFeedStarts = loneCarriageReturn ? lineFeedLineStartsOf(text) : starts;
        return new DocumentLines(text, starts, lineFeedStarts, surrogatePairOffsets.build().toArray());
    }

    /**
     * @return the text of the document
     */
    public String text() {
        return text;
    }

    /**
     * @return the number of lines, at least 1
     */
    public int lineCount() {
        return lineStarts.length;
    }

    /**
     * @param offset a place in the document
     * @return the line of the place, counted from 1
     */
    public int lineOf(int offset) {
        return lineIndexOf(lineStarts, clampOffset(offset)) + 1;
    }

    /**
     * @param offset a place in the document
     * @return the column of the place, counted from 1 in code points
     */
    public int columnOf(int offset) {
        val place = clampOffset(offset);
        return codePointIndexOf(place) - codePointIndexOf(startOfLine(lineOf(place))) + 1;
    }

    /**
     * @param line a line, counted from 1
     * @return the offset of the first character of the line
     */
    public int startOfLine(int line) {
        return lineStarts[clampLine(line) - 1];
    }

    /**
     * @param line a line, counted from 1
     * @return the offset after the last character of the line, before its line
     * break
     */
    public int endOfLine(int line) {
        val index = clampLine(line) - 1;
        if (index == lineStarts.length - 1) {
            return text.length();
        }
        val nextStart = lineStarts[index + 1];
        if (text.charAt(nextStart - 1) == '\n' && nextStart - 2 >= lineStarts[index]
                && text.charAt(nextStart - 2) == '\r') {
            return nextStart - 2;
        }
        return nextStart - 1;
    }

    /**
     * @param line a line, counted from 1
     * @return the text of the line, without its line break
     */
    public String textOfLine(int line) {
        return text.substring(startOfLine(line), endOfLine(line));
    }

    /**
     * The place of a column in a line. A column after the end of the line is the
     * end of the line.
     *
     * @param line a line, counted from 1
     * @param column a column of the line, counted from 1 in code points
     * @return the offset of the place
     */
    public int offsetOfColumn(int line, int column) {
        val start = startOfLine(line);
        val place = offsetOfCodePoint(codePointIndexOf(start) + Math.max(column, 1) - 1);
        return Math.min(place, endOfLine(line));
    }

    /**
     * Translates the index of a code point, as the parser counts it, into an
     * offset.
     *
     * @param codePointIndex the number of code points before a place
     * @return the offset of the place
     */
    public int offsetOfCodePoint(int codePointIndex) {
        // The k-th character outside the basic plane is the code point with the index
        // of its offset minus k. These indices grow with k, so a binary search finds
        // how many of these characters stand before the code point.
        var low  = 0;
        var high = surrogatePairOffsets.length;
        while (low < high) {
            val middle = (low + high) >>> 1;
            if (surrogatePairOffsets[middle] - middle < codePointIndex) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return codePointIndex + low;
    }

    /**
     * Translates a position, as the parser reports it, into an offset. A position
     * after the end of its line is the end of the line.
     *
     * @param line a line that ends only at a line feed, counted from 1
     * @param charPositionInLine the code points from the start of that line to
     * the place
     * @return the offset of the place
     */
    public int offsetOfParserPosition(int line, int charPositionInLine) {
        val index = Math.clamp(line - 1L, 0, lineFeedLineStarts.length - 1);
        val start = lineFeedLineStarts[index];
        val end   = index + 1 < lineFeedLineStarts.length ? lineFeedLineStarts[index + 1] - 1 : text.length();
        val place = offsetOfCodePoint(codePointIndexOf(start) + Math.max(charPositionInLine, 0));
        return Math.min(place, end);
    }

    /**
     * @param offset a place in the document
     * @return the line of the place as the parser counts it: a line ends only at
     * a line feed, and lines count from 1
     */
    public int parserLineOf(int offset) {
        return lineIndexOf(lineFeedLineStarts, clampOffset(offset)) + 1;
    }

    /**
     * @param offset a place in the document
     * @return the position of the place in its line as the parser counts it: the
     * code points from the start of the line, which ends only at a line feed
     */
    public int charPositionInLineOf(int offset) {
        val place = clampOffset(offset);
        return codePointIndexOf(place) - codePointIndexOf(lineFeedLineStarts[lineIndexOf(lineFeedLineStarts, place)]);
    }

    private static int[] lineFeedLineStartsOf(String text) {
        val starts = IntStream.builder().add(0);
        for (var offset = 0; offset < text.length(); offset++) {
            if (text.charAt(offset) == '\n') {
                starts.add(offset + 1);
            }
        }
        return starts.build().toArray();
    }

    /**
     * The index of the code point at an offset. An offset between the two code
     * units of a character outside the basic plane belongs to that character.
     */
    private int codePointIndexOf(int offset) {
        if (surrogatePairOffsets.length == 0) {
            return offset;
        }
        val found       = Arrays.binarySearch(surrogatePairOffsets, offset);
        val pairsBefore = found >= 0 ? found : -found - 1;
        return offset - pairsBefore;
    }

    private static int lineIndexOf(int[] starts, int offset) {
        val found = Arrays.binarySearch(starts, offset);
        return found >= 0 ? found : -found - 2;
    }

    private int clampOffset(int offset) {
        return Math.clamp(offset, 0, text.length());
    }

    private int clampLine(int line) {
        return Math.clamp(line, 1, lineStarts.length);
    }

}
