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

import io.sapl.api.model.DocumentLines;
import io.sapl.api.model.SourceLocation;
import io.sapl.compiler.expressions.SaplCompilerException;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Utility for formatting compilation errors with source code context. Produces
 * human-readable error messages that include the relevant code snippet with the
 * error position highlighted. The lines and columns are those of
 * {@link DocumentLines}, so the snippet shows the lines of the document whatever
 * ends them, and a column counts code points.
 */
@UtilityClass
public class CompilationErrorFormatter {

    private static final int CONTEXT_LINES_AFTER  = 2;
    private static final int CONTEXT_LINES_BEFORE = 2;

    private static final String ERROR_UNKNOWN_HTML = "<div class=\"error\">Unknown compilation error (null exception)</div>";
    private static final String ERROR_UNKNOWN_TEXT = "Unknown compilation error (null exception)";

    private static final String LINE_MARKER = "> ";
    private static final String NO_MARKER   = "  ";

    /**
     * The lines of a document around the line of an error, and the width their
     * numbers take.
     */
    private record Snippet(
            DocumentLines lines,
            int errorLine,
            int errorColumn,
            int startLine,
            int endLine,
            int maxLineNumWidth) {

        static Snippet around(SourceLocation location) {
            val lines     = DocumentLines.of(location.documentSource());
            val errorLine = location.line();
            val endLine   = Math.min(lines.lineCount(), errorLine + CONTEXT_LINES_AFTER);
            return new Snippet(lines, errorLine, location.column(), Math.max(1, errorLine - CONTEXT_LINES_BEFORE),
                    endLine, String.valueOf(endLine).length());
        }

        String lineNumber(int line) {
            return String.format("%" + maxLineNumWidth + "d", line);
        }
    }

    /**
     * Formats a SaplCompilerException with source code context as plain text.
     *
     * @param exception the compilation exception, may be null
     * @return formatted error message with code snippet, never null
     */
    public static String format(SaplCompilerException exception) {
        if (exception == null) {
            return ERROR_UNKNOWN_TEXT;
        }

        val sb       = new StringBuilder();
        val location = exception.getLocation();

        sb.append("SAPL Compilation Error\n");

        if (location != null && location.documentName() != null) {
            sb.append("Document: ").append(location.documentName()).append('\n');
        }

        sb.append("Error: ").append(exception.getMessage()).append('\n');

        if (location != null) {
            sb.append("Location: line ").append(location.line());
            if (location.column() > 0) {
                sb.append(", column ").append(location.column());
            }
            sb.append('\n');

            val snippet = extractCodeSnippet(location);
            if (snippet != null && !snippet.isEmpty()) {
                sb.append('\n').append(snippet);
            }
        }

        return sb.toString();
    }

    /**
     * Formats a SaplCompilerException with source code context as HTML. Suitable
     * for display in web UIs.
     *
     * @param exception the compilation exception, may be null
     * @return formatted error message with code snippet in HTML, never null
     */
    public static String formatHtml(SaplCompilerException exception) {
        if (exception == null) {
            return ERROR_UNKNOWN_HTML;
        }

        val sb       = new StringBuilder();
        val location = exception.getLocation();

        sb.append("<div class=\"sapl-compilation-error\">\n");
        sb.append("  <div class=\"error-header\">SAPL Compilation Error</div>\n");

        if (location != null && location.documentName() != null) {
            sb.append("  <div class=\"error-document\">Document: <code>").append(escapeHtml(location.documentName()))
                    .append("</code></div>\n");
        }

        sb.append("  <div class=\"error-message\">").append(escapeHtml(exception.getMessage())).append("</div>\n");

        if (location != null) {
            sb.append("  <div class=\"error-location\">Line ").append(location.line());
            if (location.column() > 0) {
                sb.append(", column ").append(location.column());
            }
            sb.append("</div>\n");

            val snippet = extractCodeSnippetHtml(location);
            if (snippet != null && !snippet.isEmpty()) {
                sb.append("  <pre class=\"error-snippet\">").append(snippet).append("</pre>\n");
            }
        }

        sb.append("</div>");
        return sb.toString();
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String extractCodeSnippet(SourceLocation location) {
        if (location.documentSource() == null || location.documentSource().isEmpty()) {
            return null;
        }

        val snippet = Snippet.around(location);
        val sb      = new StringBuilder();

        for (int lineNum = snippet.startLine(); lineNum <= snippet.endLine(); lineNum++) {
            val lineContent = snippet.lines().textOfLine(lineNum);
            val isErrorLine = lineNum == snippet.errorLine();
            val marker      = isErrorLine ? LINE_MARKER : NO_MARKER;

            sb.append(marker).append(snippet.lineNumber(lineNum)).append(" | ").append(lineContent).append('\n');

            if (isErrorLine && snippet.errorColumn() > 0) {
                val caretPadding = marker.length() + snippet.maxLineNumWidth() + 3 + snippet.errorColumn() - 1;
                sb.append(" ".repeat(caretPadding)).append("^\n");
            }
        }

        return sb.toString();
    }

    private static String extractCodeSnippetHtml(SourceLocation location) {
        if (location.documentSource() == null || location.documentSource().isEmpty()) {
            return null;
        }

        val snippet = Snippet.around(location);
        val lines   = snippet.lines();
        val sb      = new StringBuilder();

        for (int lineNum = snippet.startLine(); lineNum <= snippet.endLine(); lineNum++) {
            val lineContent = lines.textOfLine(lineNum);
            val isErrorLine = lineNum == snippet.errorLine();

            if (isErrorLine) {
                sb.append("<span class=\"error-line\">");
            }

            sb.append("<span class=\"line-number\">").append(snippet.lineNumber(lineNum)).append("</span> ");

            // The mark covers the whole character at the column, also one outside the
            // basic plane that the string holds in two code units.
            val markStart = isErrorLine && snippet.errorColumn() > 0
                    ? lines.offsetOfColumn(lineNum, snippet.errorColumn()) - lines.startOfLine(lineNum)
                    : lineContent.length();
            if (markStart < lineContent.length()) {
                val markEnd = markStart + Character.charCount(lineContent.codePointAt(markStart));
                val before  = escapeHtml(lineContent.substring(0, markStart));
                val at      = escapeHtml(lineContent.substring(markStart, markEnd));
                val after   = escapeHtml(lineContent.substring(markEnd));
                sb.append(before).append("<span class=\"error-char\">").append(at).append("</span>").append(after);
            } else {
                sb.append(escapeHtml(lineContent));
            }

            if (isErrorLine) {
                sb.append("</span>");
            }

            sb.append('\n');
        }

        return sb.toString();
    }

}
