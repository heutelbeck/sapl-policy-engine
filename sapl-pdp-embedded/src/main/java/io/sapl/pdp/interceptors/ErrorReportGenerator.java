/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.pdp.interceptors;

import org.apache.commons.text.StringEscapeUtils;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.SaplError;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ErrorReportGenerator {

    private static final String ANSI_ERROR_ON  = "\u001B[31m\u001B[7m";
    private static final String ANSI_ERROR_OFF = "\u001B[0m";
    private static final String HTML_ERROR_ON  = "<span style=\"color: red\">";
    private static final String HTML_ERROR_OFF = "</span>";

    public enum OutputFormat {
        PLAIN_TEXT, ANSI_TEXT, HTML
    }

    public String errorReport(Val value, boolean enumerateLines, OutputFormat format) {
        if (!value.isError()) {
            return "No error";
        }
        final var report = new StringBuilder();
        if (format == OutputFormat.HTML) {
            report.append(
                    "<div style=\"display: block; font-family: monospace; white-space: pre; padding: 0 1em; background-color: #282a36;\">\n");
        }

        final var    error        = value.getError();
        String       errorMessage;
        MarkedSource markedSource = null;
        if (error.source() == null || error.source().isEmpty()) {
            errorMessage = String.format("Error: %s", error.message());
        } else {
            markedSource = markErrorSourcePlainText(error, enumerateLines, format);
            // %n introduces unnecessary newlines in logs. Stick with \n.
            final var name = markedSource.documentName() != null ? "'" + markedSource.documentName() + "' " : "";
            errorMessage = String.format("Error in document %sat (%d,%d): %s\n", name, markedSource.row(),
                    markedSource.column(), value.getMessage());
        }
        if (format == OutputFormat.HTML) {
            report.append(StringEscapeUtils.ESCAPE_HTML4.translate(errorMessage));
        } else {
            report.append(errorMessage);
        }
        if (markedSource != null) {
            report.append(markedSource.source());
        }
        if (format == OutputFormat.HTML) {
            report.append("</div>\n");
        }
        return report.toString();
    }

    private MarkedSource markErrorSourcePlainText(SaplError saplError, boolean enumerateLines, OutputFormat format) {
        final var documentSource          = saplError.source();
        final var start                   = saplError.offset();
        final var end                     = saplError.endOffset();
        final var documentName            = saplError.documentName();
        final var markedSource            = new StringBuilder();
        final var row                     = saplError.startLine();
        final var lines                   = documentSource.split("\n");
        final var maxEnumerationWidth     = maxEnumerationWidth(lines.length + 1);
        final var enumerationFormatString = enumerationFormatString(maxEnumerationWidth);
        final var asciiMarkingLinePrefix  = " ".repeat(maxEnumerationWidth) + "|";

        var currentLineStartOffset = 0;
        var lineNumber             = 1;
        var column                 = 0;

        for (var line : lines) {
            final var currentLineEndOffset = currentLineStartOffset + line.length();
            if (lineNumber == row) {
                column = start - currentLineStartOffset + 1;
            }
            final var formattedLine = formatLine(line, format, start, end, currentLineStartOffset);
            if (enumerateLines) {
                markedSource.append(String.format(enumerationFormatString, lineNumber, formattedLine));
            } else {
                markedSource.append(formattedLine).append('\n');
            }
            if (format == OutputFormat.PLAIN_TEXT) {
                markedSource.append(createCodeMarkingLine(line, enumerateLines, start, end, currentLineStartOffset,
                        currentLineEndOffset, asciiMarkingLinePrefix));
            }
            lineNumber++;
            currentLineStartOffset = currentLineEndOffset + 1;
        }
        return new MarkedSource(markedSource.toString(), row, column, documentName);
    }

    private String createCodeMarkingLine(String line, boolean enumerateLines, int start, int end,
            int currentLineStartOffset, int currentLineEndOffset, String asciiMarkingLinePrefix) {
        final var codeMarkingLine = new StringBuilder();
        if (isBetween(currentLineStartOffset, start, end) || isBetween(currentLineEndOffset, start, end)) {
            if (enumerateLines) {
                codeMarkingLine.append(asciiMarkingLinePrefix);
            }
            for (var i = 0; i < line.length(); i++) {
                final var currentOffset = currentLineStartOffset + i;
                if (currentOffset == start || currentOffset == end - 1) {
                    codeMarkingLine.append('^');
                } else if (isBetween(currentOffset, start, end - 1)) {
                    codeMarkingLine.append('-');
                } else {
                    codeMarkingLine.append(' ');
                }
            }
            codeMarkingLine.append('\n');
        }
        return codeMarkingLine.toString();
    }

    private boolean isBetween(int x, int lowerBound, int upperBound) {
        return (x >= lowerBound) && (x <= upperBound);
    }

    private int maxEnumerationWidth(int i) {
        return (int) Math.floor(Math.log10(i)) + 1;
    }

    private String enumerationFormatString(int width) {
        return "%" + width + "d|%s\n";
    }

    private String formatLine(String line, OutputFormat format, int start, int end, int currentLineStartOffset) {
        if (format == OutputFormat.PLAIN_TEXT) {
            return line;
        }
        return styleLine(format, line, start, end, currentLineStartOffset);
    }

    private String styleLine(OutputFormat format, String line, int start, int end, int currentLineStartOffset) {
        final var newLine = new StringBuilder();
        final var ansi    = format == OutputFormat.ANSI_TEXT;
        final var on      = ansi ? ANSI_ERROR_ON : HTML_ERROR_ON;
        final var off     = ansi ? ANSI_ERROR_OFF : HTML_ERROR_OFF;

        var currentOffset        = currentLineStartOffset;
        var highlightingTurnedOn = false;

        if (start < currentLineStartOffset && end >= currentLineStartOffset) {
            newLine.append(on);
            highlightingTurnedOn = true;
        }
        for (var character : line.split("(?!^)")) {
            if (currentOffset == start) {
                newLine.append(on);
                highlightingTurnedOn = true;
            }
            if (ansi) {
                newLine.append(character);
            } else {
                newLine.append(StringEscapeUtils.ESCAPE_HTML4.translate(character));
            }
            if (currentOffset == end - 1) {
                newLine.append(off);
                highlightingTurnedOn = false;
            }
            currentOffset++;
        }
        if (highlightingTurnedOn) {
            newLine.append(off);
        }
        return newLine.toString();
    }

    private record MarkedSource(String source, int row, int column, String documentName) {}

}
