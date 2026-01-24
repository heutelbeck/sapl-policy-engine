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

import io.sapl.api.model.SourceLocation;
import io.sapl.compiler.expressions.SaplCompilerException;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Utility for formatting compilation errors with source code context. Produces
 * human-readable error messages that include the relevant code snippet with the
 * error position highlighted.
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

        val source = location.documentSource();
        val lines  = source.split("\n", -1);

        val errorLine       = location.line();
        val errorColumn     = location.column();
        val startLine       = Math.max(1, errorLine - CONTEXT_LINES_BEFORE);
        val endLine         = Math.min(lines.length, errorLine + CONTEXT_LINES_AFTER);
        val maxLineNumWidth = String.valueOf(endLine).length();
        val lineNumFormat   = "%" + maxLineNumWidth + "d";
        val sb              = new StringBuilder();

        for (int lineNum = startLine; lineNum <= endLine; lineNum++) {
            val lineIndex   = lineNum - 1;
            val lineContent = lineIndex < lines.length ? lines[lineIndex] : "";
            val isErrorLine = lineNum == errorLine;
            val marker      = isErrorLine ? LINE_MARKER : NO_MARKER;

            sb.append(marker).append(String.format(lineNumFormat, lineNum)).append(" | ").append(lineContent)
                    .append('\n');

            if (isErrorLine && errorColumn > 0) {
                val caretPadding = marker.length() + maxLineNumWidth + 3 + errorColumn - 1;
                sb.append(" ".repeat(caretPadding)).append("^\n");
            }
        }

        return sb.toString();
    }

    private static String extractCodeSnippetHtml(SourceLocation location) {
        if (location.documentSource() == null || location.documentSource().isEmpty()) {
            return null;
        }

        val source = location.documentSource();
        val lines  = source.split("\n", -1);

        val errorLine       = location.line();
        val errorColumn     = location.column();
        val startLine       = Math.max(1, errorLine - CONTEXT_LINES_BEFORE);
        val endLine         = Math.min(lines.length, errorLine + CONTEXT_LINES_AFTER);
        val maxLineNumWidth = String.valueOf(endLine).length();
        val lineNumFormat   = "%" + maxLineNumWidth + "d";
        val sb              = new StringBuilder();

        for (int lineNum = startLine; lineNum <= endLine; lineNum++) {
            val lineIndex   = lineNum - 1;
            val lineContent = lineIndex < lines.length ? lines[lineIndex] : "";
            val isErrorLine = lineNum == errorLine;

            if (isErrorLine) {
                sb.append("<span class=\"error-line\">");
            }

            sb.append("<span class=\"line-number\">").append(String.format(lineNumFormat, lineNum)).append("</span> ");

            if (isErrorLine && errorColumn > 0 && errorColumn <= lineContent.length()) {
                val before = escapeHtml(lineContent.substring(0, errorColumn - 1));
                val at     = escapeHtml(String.valueOf(lineContent.charAt(errorColumn - 1)));
                val after  = escapeHtml(lineContent.substring(errorColumn));
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
