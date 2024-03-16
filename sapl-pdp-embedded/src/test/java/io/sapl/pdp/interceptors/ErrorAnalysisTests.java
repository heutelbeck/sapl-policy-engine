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

import java.io.IOException;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Traced;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.SAPLInterpreter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class ErrorAnalysisTests {

    private SAPLInterpreter parser = new DefaultSAPLInterpreter();

    private static final String ANSI_ERROR_ON  = "\u001B[31m\u001B[7m";
    private static final String ANSI_ERROR_OFF = "\u001B[0m";
    private static final String HTML_ERROR_ON  = "<span style=\"color: red\">";
    private static final String HTML_ERROR_OFF = "</span>";

    @Test
    void runIt() throws IOException {
        var doc    = parser.parseDocument("""
                policy "some policy"
                permit true == false
                    || "X" != 1 / 0
                    || 123 != "123" where true;
                """);
        var result = doc.sapl().matches().block();
        log.info("Result: {}", result);
        dumpErrors(result, true, OutputFormat.ANSI_TEXT);
        dumpErrors(result, false, OutputFormat.ANSI_TEXT);
        dumpErrors(result, true, OutputFormat.PLAIN_TEXT);
        dumpErrors(result, false, OutputFormat.PLAIN_TEXT);
        dumpErrors(result, true, OutputFormat.HTML);
        dumpErrors(result, false, OutputFormat.HTML);
    }

    private void dumpErrors(Traced traced, boolean enumerateLines, OutputFormat format) {
        for (var error : traced.getErrorsFromTrace()) {
            multiLog(errorReport(error, enumerateLines, format));
        }
    }

    enum OutputFormat {
        PLAIN_TEXT, ANSI_TEXT, HTML
    }

    private String errorReport(Val error, boolean enumerateLines, OutputFormat format) {
        if (!error.isError()) {
            return "No error";
        }
        var report = "";
        if (error.getErrorSourceReference() instanceof EObject errorSource) {
            var markedSource = markErrorSourcePlainText(errorSource, enumerateLines, format);
            report += String.format("Error in document '%s' at (%d,%d): %s\n", markedSource.documentName(),
                    markedSource.row(), markedSource.column(), error.getMessage());
            report += markedSource.source();
        } else {
            report += "Error: " + error.getMessage();
        }
        if (format == OutputFormat.HTML) {
            report = wrapHtmlReport(report);
        }
        return report;
    }

    private String wrapHtmlReport(String report) {
        var wrapped = "<div style=\"display: block; font-family: monospace; white-space: pre; padding: 0 1em; background-color: #eee;\">\n";
        wrapped += report;
        wrapped += "</div>\n";
        return wrapped;
    }

    private record MarkedSource(String source, int row, int column, String documentName) {
    }

    private MarkedSource markErrorSourcePlainText(EObject errorSource, boolean enumerateLines, OutputFormat format) {
        var nodeSet = NodeModelUtils.getNode(errorSource);
        var start   = nodeSet.getOffset();
        var end     = nodeSet.getEndOffset();

        String documentName = null;
        var    root         = nodeSet.getRootNode();

        if (root.getSemanticElement() instanceof SAPL sapl) {
            documentName = sapl.getPolicyElement().getSaplName();
        }

        var documentSource          = root.getText();
        var lines                   = documentSource.split("\n");
        var currentLineStartOffset  = 0;
        var markedSource            = "";
        var lineNumber              = 1;
        var column                  = 0;
        var row                     = nodeSet.getStartLine();
        var maxEnumerationWidth     = maxEnumerationWidth(lines.length + 1);
        var enumerationFormatString = enumerationFormatString(maxEnumerationWidth);
        var asciiMarkingLinePrefix  = " ".repeat(maxEnumerationWidth) + "|";
        for (var line : lines) {
            var currentLineEndOffset = currentLineStartOffset + line.length();
            if (lineNumber == row) {
                column = start - currentLineStartOffset + 1;
            }
            var formattedLine = formatLine(line, format, start, end, currentLineStartOffset, currentLineEndOffset);
            if (enumerateLines) {
                markedSource += String.format(enumerationFormatString, lineNumber, formattedLine);
            } else {
                markedSource += formattedLine + "\n";
            }
            if (format == OutputFormat.PLAIN_TEXT) {
                markedSource += createCodeMarkingLine(line, enumerateLines, start, end, currentLineStartOffset,
                        currentLineEndOffset, asciiMarkingLinePrefix);
            }
            lineNumber++;
            currentLineStartOffset = currentLineEndOffset + 1;
        }
        return new MarkedSource(markedSource, row, column, documentName);
    }

    private String createCodeMarkingLine(String line, boolean enumerateLines, int start, int end,
            int currentLineStartOffset, int currentLineEndOffset, String asciiMarkingLinePrefix) {
        var codeMarkingLine = "";
        if ((currentLineStartOffset <= start && currentLineEndOffset > start)
                || (currentLineStartOffset < end && currentLineEndOffset >= end)) {
            if (enumerateLines) {
                codeMarkingLine += asciiMarkingLinePrefix;
            }
            for (var i = 0; i < line.length(); i++) {
                var currentOffset = currentLineStartOffset + i;
                if (currentOffset == start || currentOffset == end - 1) {
                    codeMarkingLine += "^";
                } else if (currentOffset >= start && currentOffset < end) {
                    codeMarkingLine += "-";
                } else {
                    codeMarkingLine += " ";
                }
            }
            codeMarkingLine += "\n";
        }
        return codeMarkingLine;
    }

    private int maxEnumerationWidth(int i) {
        return (int) Math.floor(Math.log10(i)) + 1;
    }

    private String enumerationFormatString(int width) {
        return "%" + width + "d|%s\n";
    }

    private String formatLine(String line, OutputFormat format, int start, int end, int currentLineStartOffset,
            int currentLineEndOffset) {
        if (format == OutputFormat.PLAIN_TEXT) {
            return line;
        }
        return styleLine(format, line, start, end, currentLineStartOffset, currentLineEndOffset);
    }

    private String styleLine(OutputFormat format, String line, int start, int end, int currentLineStartOffset,
            int currentLineEndOffset) {
        var newLine              = "";
        var currentOffset        = currentLineStartOffset;
        var ansi                 = format == OutputFormat.ANSI_TEXT;
        var on                   = ansi ? ANSI_ERROR_ON : HTML_ERROR_ON;
        var off                  = ansi ? ANSI_ERROR_OFF : HTML_ERROR_OFF;
        var highlightingTurnedOn = false;
        if (start < currentLineStartOffset && end >= currentLineStartOffset) {
            newLine              += on;
            highlightingTurnedOn  = true;
        }
        for (var character : line.split("(?!^)")) {
            if (currentOffset == start) {
                newLine              += on;
                highlightingTurnedOn  = true;
            }
            newLine += character;
            if (currentOffset == end - 1) {
                newLine              += off;
                highlightingTurnedOn  = false;
            }
            currentOffset++;
        }
        if (highlightingTurnedOn) {
            newLine += off;
        }
        return newLine;
    }

    void multiLog(String s) {
        for (var line : s.split("\n")) {
            log.info(line);
        }
    }
}
