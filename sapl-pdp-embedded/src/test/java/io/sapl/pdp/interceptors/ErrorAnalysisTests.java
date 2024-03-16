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
import org.eclipse.xtext.nodemodel.INode;
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
    }

    private void dumpErrors(Traced traced, boolean enumerateLines, OutputFormat format) {
        for (var error : traced.getErrorsFromTrace()) {
            multiLog(errorReport(error, enumerateLines, format));
        }
    }

    void dumpNodeInfo(EObject o) {
        INode nodeSet = NodeModelUtils.getNode(o);
        log.error("text                : '{}'", nodeSet.getText());
        log.error("toString            : {}", nodeSet.toString());
        log.error("startLine           : {}", nodeSet.getStartLine());
        log.error("endLine             : {}", nodeSet.getEndLine());
        log.error("endOffset           : {}", nodeSet.getEndOffset());
        log.error("grammarElement      : {}", nodeSet.getGrammarElement());
        log.error("length              : {}", nodeSet.getLength());
        log.error("offset              : {}", nodeSet.getOffset());
        log.error("root node           : {}", nodeSet.getRootNode());
        log.error("syntax error message: {}", nodeSet.getSyntaxErrorMessage());
        log.error("totalendline        : {}", nodeSet.getTotalEndLine());
        log.error("TotalEndOffset      : {}", nodeSet.getTotalEndOffset());
        log.error("TotalLength         : {}", nodeSet.getTotalLength());
        log.error("TotalOffset         : {}", nodeSet.getTotalOffset());
        log.error("TotalStartLine      : {}", nodeSet.getTotalStartLine());
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
        return report;
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
                markedSource += codeMarkingLine;
            }
            lineNumber++;
            currentLineStartOffset = currentLineEndOffset + 1;
        }
        return new MarkedSource(markedSource, row, column, documentName);
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
        if (format == OutputFormat.ANSI_TEXT) {
            return formatAnsiLine(line, start, end, currentLineStartOffset, currentLineEndOffset);
        }
        return line;
    }

    private String formatAnsiLine(String line, int start, int end, int currentLineStartOffset,
            int currentLineEndOffset) {
        var newLine       = "";
        var currentOffset = currentLineStartOffset;
        if (start < currentLineStartOffset && end >= currentLineStartOffset) {
            newLine += ANSI_ERROR_ON;
        }
        for (var character : line.split("(?!^)")) {
            if (currentOffset == start) {
                newLine += ANSI_ERROR_ON;
            }
            newLine += character;
            if (currentOffset == end - 1) {
                newLine += ANSI_ERROR_OFF;
            }
            currentOffset++;
        }
        return newLine + ANSI_ERROR_OFF;
    }

    void multiLog(String s) {
        for (var line : s.split("\n")) {
            log.info(line);
        }
    }
}
