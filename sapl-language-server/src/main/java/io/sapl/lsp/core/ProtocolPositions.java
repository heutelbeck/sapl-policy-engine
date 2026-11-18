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
package io.sapl.lsp.core;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import io.sapl.api.model.DocumentLines;
import io.sapl.compiler.util.SourceLocationUtil;
import lombok.NonNull;
import lombok.val;

/**
 * Converts the places of one document into the positions of the language
 * server protocol and back. A position counts its line from 0 and its character
 * in UTF-16 code units from the start of the line, and its lines end as
 * {@link DocumentLines} ends them: at a line feed, a carriage return, or both
 * together. A character after the end of a line stands for the end of the line,
 * and a line after the last for the end of the document, as the protocol says.
 * <p>
 * A token is placed by its index in the code points of the document. A token
 * the parser conjured while it recovered from an error, such as
 * {@code <missing ']'>}, stands in no text. It is placed where the parser
 * reports it and reaches as far as its text, but not beyond the end of that
 * line. So the place after an unfinished construct, where the author types on,
 * still belongs to that construct.
 */
public final class ProtocolPositions {

    private final DocumentLines lines;

    private ProtocolPositions(DocumentLines lines) {
        this.lines = lines;
    }

    /**
     * @param content the text of a document
     * @return the positions of the document
     * @throws NullPointerException if the content is null
     */
    public static ProtocolPositions of(@NonNull String content) {
        return new ProtocolPositions(DocumentLines.of(content));
    }

    /**
     * @param token a token
     * @return the positions of the document the token was read from, whose lines
     * are counted once per parse. A token that was not read from a document
     * belongs to an empty document.
     * @throws NullPointerException if the token is null
     */
    public static ProtocolPositions of(@NonNull Token token) {
        val lines = SourceLocationUtil.documentLinesOf(token);
        return lines == null ? of("") : new ProtocolPositions(lines);
    }

    /**
     * @param context a node of a parse tree, or null
     * @return the range of the node in the document it was read from. A node
     * without a start and a stop token, as error recovery can leave one, has an
     * empty range at the start of the document.
     */
    public static Range rangeOfNode(ParserRuleContext context) {
        if (context == null || context.getStart() == null || context.getStop() == null) {
            return new Range(new Position(0, 0), new Position(0, 0));
        }
        return of(context.getStart()).rangeOf(context);
    }

    /**
     * @param offset a place in the document
     * @return the position of the place
     */
    public Position positionOf(int offset) {
        val place = Math.clamp(offset, 0, lines.text().length());
        val line  = lines.lineOf(place);
        return new Position(line - 1, place - lines.startOfLine(line));
    }

    /**
     * @param offset a place in the document
     * @return the line of the place, counted from 0
     */
    public int lineOf(int offset) {
        return lines.lineOf(offset) - 1;
    }

    /**
     * @param token a token of the document
     * @return the line of the first character of the token, counted from 0
     */
    public int lineOf(Token token) {
        return lineOf(startOf(token));
    }

    /**
     * @return the range from the start to the end of the document
     */
    public Range rangeOfDocument() {
        return new Range(new Position(0, 0), positionOf(lines.text().length()));
    }

    /**
     * @param position a position of the protocol
     * @return the place of the position in the document
     */
    public int offsetOf(Position position) {
        val line = position.getLine() + 1L;
        if (line > lines.lineCount()) {
            return lines.text().length();
        }
        if (line < 1) {
            return 0;
        }
        val start = lines.startOfLine((int) line);
        return Math.clamp(start + (long) position.getCharacter(), start, lines.endOfLine((int) line));
    }

    /**
     * @param position a position of the protocol
     * @return true if the document has the line of the position
     */
    public boolean holds(Position position) {
        return position.getLine() >= 0 && position.getLine() < lines.lineCount();
    }

    /**
     * @param line a line as the parser counts it: from 1, ended only by a line
     * feed
     * @param charPositionInLine the code points from the start of that line
     * @return the position of the place the parser reports
     */
    public Position positionOfParserPosition(int line, int charPositionInLine) {
        return positionOf(lines.offsetOfParserPosition(line, charPositionInLine));
    }

    /**
     * @param token a token of the document
     * @return the place of the first character of the token
     */
    public int startOf(Token token) {
        if (token.getStartIndex() < 0) {
            return lines.offsetOfParserPosition(token.getLine(), token.getCharPositionInLine());
        }
        return lines.offsetOfCodePoint(token.getStartIndex());
    }

    /**
     * @param token a token of the document
     * @return the place after the last character of the token
     */
    public int endOf(Token token) {
        if (token.getStartIndex() < 0) {
            val start = startOf(token);
            return Math.min(start + token.getText().length(), lines.endOfLine(lines.lineOf(start)));
        }
        // The end of file has no characters: ANTLR stops it before it starts.
        return lines.offsetOfCodePoint(Math.max(token.getStartIndex(), token.getStopIndex() + 1));
    }

    /**
     * @param token a token of the document
     * @return the range of the token
     */
    public Range rangeOf(Token token) {
        return new Range(positionOf(startOf(token)), positionOf(endOf(token)));
    }

    /**
     * @param context a node of the parse tree of the document, with a start and a
     * stop token
     * @return the range from the start of its first token to the end of its last
     * token
     */
    public Range rangeOf(ParserRuleContext context) {
        return new Range(positionOf(startOf(context.getStart())), positionOf(endOf(context.getStop())));
    }

    /**
     * @param context a node of the parse tree of the document
     * @param offset a place in the document
     * @return true if the place lies between the start of the first token and the
     * end of the last token of the node, both included
     */
    public boolean encloses(ParserRuleContext context, int offset) {
        if (context.getStart() == null || context.getStop() == null) {
            return false;
        }
        return startOf(context.getStart()) <= offset && offset <= endOf(context.getStop());
    }

    /**
     * Splits the range of a token at the ends of its lines. The protocol forbids
     * a semantic token to cross a line, and a line break belongs to no line.
     *
     * @param token a token of the document
     * @return one range for each line the token covers with at least one
     * character, in document order
     */
    public List<Range> lineRangesOf(Token token) {
        val start     = startOf(token);
        val end       = endOf(token);
        val firstLine = lines.lineOf(start);
        // Most tokens end on the line they start on; that needs no second search.
        val lastLine = end <= lines.endOfLine(firstLine) ? firstLine : lines.lineOf(end);
        val ranges   = new ArrayList<Range>(lastLine - firstLine + 1);
        for (var line = firstLine; line <= lastLine; line++) {
            val lineStart = lines.startOfLine(line);
            val from      = Math.max(start, lineStart);
            val to        = Math.min(end, lines.endOfLine(line));
            if (to > from) {
                ranges.add(new Range(new Position(line - 1, from - lineStart), new Position(line - 1, to - lineStart)));
            }
        }
        return ranges;
    }

}
