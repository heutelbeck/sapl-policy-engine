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

import java.util.IdentityHashMap;
import java.util.Map;

import static java.util.stream.Collectors.joining;

/**
 * Utility for formatting compilation errors with source code context. Produces
 * human-readable error messages that include the relevant code snippet with the
 * error position highlighted. The lines and columns are those of
 * {@link DocumentLines}, so the snippet shows the lines of the document whatever
 * ends them, and a column counts code points. For multiple errors, each
 * displayed source line includes up to 200 code points. Three dots mark a
 * clipped end. The reported location always uses the original column.
 */
@UtilityClass
public class CompilationErrorFormatter {

    private static final int    MULTIPLE_ERROR_LINE_WIDTH = 200;
    private static final String ELLIPSIS                  = "...";

    private static final int CONTEXT_LINES_AFTER  = 2;
    private static final int CONTEXT_LINES_BEFORE = 2;

    private static final String ERROR_UNKNOWN_HTML = "<div class=\"error\">Unknown compilation error (null exception)</div>";
    private static final String ERROR_UNKNOWN_TEXT = "Unknown compilation error (null exception)";

    private static final String LINE_MARKER = "> ";
    private static final String NO_MARKER   = "  ";

    private record ReportSources(boolean clipped, Map<String, DocumentLines> documents) {

        ReportSources(boolean clipped) {
            this(clipped, new IdentityHashMap<>());
        }

        String documentName(String name) {
            if (!clipped || name.length() <= MULTIPLE_ERROR_LINE_WIDTH) {
                return name;
            }
            int end = MULTIPLE_ERROR_LINE_WIDTH;
            if (Character.isSurrogatePair(name.charAt(end - 1), name.charAt(end))) {
                end--;
            }
            return name.substring(0, end) + ELLIPSIS;
        }
    }

    private record DisplayLine(String text, int caretColumn, int markOffset) {}

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
            int maxLineNumWidth,
            boolean clipped) {

        static Snippet around(SourceLocation location, ReportSources sources) {
            val lines     = sources.documents().computeIfAbsent(location.documentSource(), DocumentLines::of);
            val errorLine = location.line();
            val endLine   = Math.min(lines.lineCount(), errorLine + CONTEXT_LINES_AFTER);
            return new Snippet(lines, errorLine, location.column(), Math.max(1, errorLine - CONTEXT_LINES_BEFORE),
                    endLine, String.valueOf(endLine).length(), sources.clipped());
        }

        DisplayLine displayLine(int line) {
            int start       = lines.startOfLine(line);
            int end         = lines.endOfLine(line);
            int firstColumn = 1;
            int caret       = 0;
            int mark        = end;
            if (line == errorLine && errorColumn > 0) {
                caret = errorColumn;
                mark  = lines.offsetOfColumn(line, errorColumn);
            }
            String prefix = "";
            String suffix = "";
            if (clipped) {
                val endColumn = lines.columnOf(end);
                caret = Math.min(caret, endColumn);
                val lastStartColumn = Math.max(1, endColumn - MULTIPLE_ERROR_LINE_WIDTH);
                firstColumn = Math.clamp((long) caret - MULTIPLE_ERROR_LINE_WIDTH / 2, 1, lastStartColumn);
                if (firstColumn > 1) {
                    start  = lines.offsetOfColumn(line, firstColumn);
                    prefix = ELLIPSIS;
                }
                if (endColumn - firstColumn > MULTIPLE_ERROR_LINE_WIDTH) {
                    end    = lines.offsetOfColumn(line, firstColumn + MULTIPLE_ERROR_LINE_WIDTH);
                    suffix = ELLIPSIS;
                }
            }
            val text = prefix + lines.text().substring(start, end) + suffix;
            if (caret == 0) {
                return new DisplayLine(text, 0, text.length());
            }
            return new DisplayLine(text, prefix.length() + caret - firstColumn + 1, prefix.length() + mark - start);
        }

        String lineNumber(int line) {
            val number = String.valueOf(line);
            return " ".repeat(Math.max(0, maxLineNumWidth - number.length())) + number;
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
        val diagnostics = exception.diagnostics();
        val sources     = new ReportSources(diagnostics.size() > 1);
        return diagnostics.stream().map(failure -> formatOne(failure, sources)).collect(joining("\n"));
    }

    private static String formatOne(SaplCompilerException exception, ReportSources sources) {
        val sb       = new StringBuilder();
        val location = exception.getLocation();

        sb.append("SAPL Compilation Error\n");

        if (location != null && location.documentName() != null) {
            sb.append("Document: ").append(sources.documentName(location.documentName())).append('\n');
        }

        sb.append("Error: ").append(exception.getMessage()).append('\n');

        if (location != null) {
            sb.append("Location: line ").append(location.line());
            if (location.column() > 0) {
                sb.append(", column ").append(location.column());
            }
            sb.append('\n');

            val snippet = extractCodeSnippet(location, sources);
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
        val diagnostics = exception.diagnostics();
        val sources     = new ReportSources(diagnostics.size() > 1);
        return diagnostics.stream().map(failure -> formatOneHtml(failure, sources)).collect(joining("\n"));
    }

    private static String formatOneHtml(SaplCompilerException exception, ReportSources sources) {
        val sb       = new StringBuilder();
        val location = exception.getLocation();

        sb.append("<div class=\"sapl-compilation-error\">\n");
        sb.append("  <div class=\"error-header\">SAPL Compilation Error</div>\n");

        if (location != null && location.documentName() != null) {
            sb.append("  <div class=\"error-document\">Document: <code>")
                    .append(escapeHtml(sources.documentName(location.documentName()))).append("</code></div>\n");
        }

        sb.append("  <div class=\"error-message\">").append(escapeHtml(exception.getMessage())).append("</div>\n");

        if (location != null) {
            sb.append("  <div class=\"error-location\">Line ").append(location.line());
            if (location.column() > 0) {
                sb.append(", column ").append(location.column());
            }
            sb.append("</div>\n");

            val snippet = extractCodeSnippetHtml(location, sources);
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

    private static String extractCodeSnippet(SourceLocation location, ReportSources sources) {
        if (location.documentSource() == null || location.documentSource().isEmpty()) {
            return null;
        }

        val snippet = Snippet.around(location, sources);
        val sb      = new StringBuilder();

        for (int lineNum = snippet.startLine(); lineNum <= snippet.endLine(); lineNum++) {
            val displayed   = snippet.displayLine(lineNum);
            val isErrorLine = lineNum == snippet.errorLine();
            val marker      = isErrorLine ? LINE_MARKER : NO_MARKER;

            sb.append(marker).append(snippet.lineNumber(lineNum)).append(" | ").append(displayed.text()).append('\n');

            if (displayed.caretColumn() > 0) {
                val caretPadding = marker.length() + snippet.maxLineNumWidth() + 3 + displayed.caretColumn() - 1;
                sb.append(" ".repeat(caretPadding)).append("^\n");
            }
        }

        return sb.toString();
    }

    private static String extractCodeSnippetHtml(SourceLocation location, ReportSources sources) {
        if (location.documentSource() == null || location.documentSource().isEmpty()) {
            return null;
        }

        val snippet = Snippet.around(location, sources);
        val sb      = new StringBuilder();

        for (int lineNum = snippet.startLine(); lineNum <= snippet.endLine(); lineNum++) {
            val displayed   = snippet.displayLine(lineNum);
            val lineContent = displayed.text();
            val isErrorLine = lineNum == snippet.errorLine();

            if (isErrorLine) {
                sb.append("<span class=\"error-line\">");
            }

            sb.append("<span class=\"line-number\">").append(snippet.lineNumber(lineNum)).append("</span> ");

            val markStart = displayed.markOffset();
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
