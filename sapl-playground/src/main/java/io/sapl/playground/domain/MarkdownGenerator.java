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
package io.sapl.playground.domain;

import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import io.sapl.api.documentation.LibraryDocumentation;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.List;

/**
 * Utility class for generating and converting SAPL documentation. Provides
 * methods to generate markdown documentation
 * from library documentation objects, and to convert markdown content to HTML
 * for display.
 * <p>
 * Uses Flexmark for markdown parsing and HTML rendering. All methods are static
 * as this is a utility class.
 */
@UtilityClass
public class MarkdownGenerator {

    private static final MutableDataSet OPTIONS  = new MutableDataSet().set(Parser.EXTENSIONS,
            List.of(TablesExtension.create()));
    private static final Parser         PARSER   = Parser.builder(OPTIONS).build();
    private static final HtmlRenderer   RENDERER = HtmlRenderer.builder(OPTIONS).build();

    private static final String MARKDOWN_HEADER_LEVEL_1  = "# ";
    private static final String MARKDOWN_HEADER_LEVEL_2  = "## ";
    private static final String MARKDOWN_HORIZONTAL_RULE = "---\n\n";
    private static final String MARKDOWN_LINE_BREAK      = "\n\n";

    private static final String DOCS_STYLE = """
            <style>
            .sapl-docs { padding: 1rem 1.5rem; line-height: 1.6; }
            .sapl-docs table { border-collapse: collapse; width: 100%; margin: 1rem 0; }
            .sapl-docs th, .sapl-docs td {
                border: 1px solid var(--lumo-contrast-20pct, rgba(255, 255, 255, 0.2));
                padding: 0.5rem 0.75rem; text-align: left;
            }
            .sapl-docs th {
                background-color: var(--lumo-contrast-5pct, rgba(255, 255, 255, 0.05));
                font-weight: 600;
            }
            .sapl-docs pre {
                background-color: var(--lumo-contrast-5pct, rgba(255, 255, 255, 0.05));
                padding: 0.75rem 1rem; border-radius: 4px; overflow-x: auto;
            }
            .sapl-docs code { font-size: 0.875em; }
            .sapl-docs h1, .sapl-docs h2, .sapl-docs h3 { margin-top: 1.5rem; margin-bottom: 0.5rem; }
            .sapl-docs hr {
                border: none;
                border-top: 1px solid var(--lumo-contrast-10pct, rgba(255, 255, 255, 0.1));
                margin: 1.5rem 0;
            }
            </style>""";

    /**
     * Generates markdown documentation for a library (function library or PIP).
     * Creates a structured Markdown document
     * with library name, description, library-level documentation, and individual
     * entry documentation. Each entry is
     * separated by horizontal rules.
     *
     * @param documentation
     * the library documentation object
     *
     * @return markdown-formatted documentation string
     */
    public String generateMarkdownForLibrary(LibraryDocumentation documentation) {
        val stringBuilder = new StringBuilder();

        appendHeader(stringBuilder, documentation.name());
        if (documentation.description() != null && !documentation.description().isBlank()) {
            stringBuilder.append(documentation.description()).append(MARKDOWN_LINE_BREAK);
        }
        if (documentation.documentation() != null && !documentation.documentation().isBlank()) {
            stringBuilder.append(documentation.documentation()).append(MARKDOWN_LINE_BREAK);
        }
        stringBuilder.append(MARKDOWN_HORIZONTAL_RULE);

        for (var entry : documentation.entries()) {
            appendSubHeader(stringBuilder, entry.name());
            if (entry.documentation() != null && !entry.documentation().isBlank()) {
                stringBuilder.append(entry.documentation()).append(MARKDOWN_LINE_BREAK);
            }
            stringBuilder.append(MARKDOWN_HORIZONTAL_RULE);
        }

        return stringBuilder.toString();
    }

    /**
     * Converts markdown content to HTML. Parses the markdown string and renders it
     * as HTML using Flexmark.
     *
     * @param markdown
     * the markdown string to convert
     *
     * @return HTML string representation of the markdown content
     */
    public String markdownToHtml(@NonNull String markdown) {
        val document = PARSER.parse(markdown);
        return RENDERER.render(document);
    }

    /**
     * Wraps HTML content in a div element. Adds a div container around the provided
     * HTML content with proper line
     * breaks for formatting.
     *
     * @param innerHtml
     * the HTML content to wrap
     *
     * @return the HTML content wrapped in a div element
     */
    public String wrapInDiv(String innerHtml) {
        return "<div class=\"sapl-docs\">%s%n%s%n</div>".formatted(DOCS_STYLE, innerHtml);
    }

    private void appendHeader(StringBuilder stringBuilder, String title) {
        stringBuilder.append(MARKDOWN_HEADER_LEVEL_1).append(title).append(MARKDOWN_LINE_BREAK);
    }

    private void appendSubHeader(StringBuilder stringBuilder, String title) {
        stringBuilder.append(MARKDOWN_HEADER_LEVEL_2).append(title).append(MARKDOWN_LINE_BREAK);
    }
}
