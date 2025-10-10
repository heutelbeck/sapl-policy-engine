/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import io.sapl.attributes.documentation.api.LibraryDocumentation;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Utility class for generating and converting SAPL documentation.
 * Provides methods to generate markdown documentation from library
 * and policy information point documentation objects, and to convert
 * markdown content to HTML for display.
 * <p>
 * Uses Flexmark for markdown parsing and HTML rendering.
 * All methods are static as this is a utility class.
 */
@UtilityClass
public class MarkdownGenerator {

    /*
     * Markdown parser for converting markdown strings to document nodes.
     */
    private static final Parser PARSER = Parser.builder().build();

    /*
     * HTML renderer for converting markdown document nodes to HTML strings.
     */
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().build();

    private static final String MARKDOWN_HEADER_LEVEL_1  = "# ";
    private static final String MARKDOWN_HEADER_LEVEL_2  = "## ";
    private static final String MARKDOWN_HORIZONTAL_RULE = "---\n\n";
    private static final String MARKDOWN_LINE_BREAK      = "\n\n";

    /**
     * Generates markdown documentation for a function library.
     * Creates a structured markdown document with library name, description,
     * library-level documentation, and individual function documentation.
     * Each function is separated by horizontal rules.
     *
     * @param documentation the function library documentation object
     * @return markdown-formatted documentation string
     */
    public String generateMarkdownForLibrary(io.sapl.interpreter.functions.LibraryDocumentation documentation) {
        val stringBuilder = new StringBuilder();

        appendHeader(stringBuilder, documentation.getName());
        stringBuilder.append(documentation.getDescription()).append(MARKDOWN_LINE_BREAK);
        stringBuilder.append(documentation.getLibraryDocumentation()).append(MARKDOWN_LINE_BREAK);
        stringBuilder.append(MARKDOWN_HORIZONTAL_RULE);

        for (var entry : documentation.getDocumentation().entrySet()) {
            val functionName          = entry.getKey();
            val functionDocumentation = entry.getValue();
            appendSubHeader(stringBuilder, functionName);
            stringBuilder.append(functionDocumentation).append(MARKDOWN_LINE_BREAK);
            stringBuilder.append(MARKDOWN_HORIZONTAL_RULE);
        }

        return stringBuilder.toString();
    }

    /**
     * Generates markdown documentation for a policy information point.
     * Creates a structured markdown document with PIP namespace, description,
     * general documentation, and individual attribute documentation.
     * Each attribute is separated by horizontal rules.
     *
     * @param documentation the policy information point documentation object
     * @return markdown-formatted documentation string
     */
    public String generateMarkdownForPolicyInformationPoint(LibraryDocumentation documentation) {
        val stringBuilder = new StringBuilder();

        appendHeader(stringBuilder, documentation.namespace());
        stringBuilder.append(documentation.descriptionMarkdown()).append(MARKDOWN_LINE_BREAK);
        stringBuilder.append(documentation.documentationMarkdown()).append(MARKDOWN_LINE_BREAK);
        stringBuilder.append(MARKDOWN_HORIZONTAL_RULE);

        for (var entry : documentation.attributesMap().entrySet()) {
            val attributeName          = entry.getKey();
            val attributeDocumentation = entry.getValue();
            appendSubHeader(stringBuilder, attributeName);
            stringBuilder.append(attributeDocumentation).append(MARKDOWN_LINE_BREAK);
            stringBuilder.append(MARKDOWN_HORIZONTAL_RULE);
        }

        return stringBuilder.toString();
    }

    /**
     * Converts markdown content to HTML.
     * Parses the markdown string and renders it as HTML using Flexmark.
     *
     * @param markdown the markdown string to convert
     * @return HTML string representation of the markdown content
     */
    public String markdownToHtml(@NonNull String markdown) {
        val document = PARSER.parse(markdown);
        return RENDERER.render(document);
    }

    /**
     * Wraps HTML content in a div element.
     * Adds a div container around the provided HTML content with
     * proper line breaks for formatting.
     *
     * @param innerHtml the HTML content to wrap
     * @return the HTML content wrapped in a div element
     */
    public String wrapInDiv(String innerHtml) {
        return String.format("<div>%n%s%n</div>", innerHtml);
    }

    /*
     * Appends a level 1 markdown header with the specified title.
     * Adds two line breaks after the header.
     */
    private void appendHeader(StringBuilder stringBuilder, String title) {
        stringBuilder.append(MARKDOWN_HEADER_LEVEL_1).append(title).append(MARKDOWN_LINE_BREAK);
    }

    /*
     * Appends a level 2 markdown header with the specified title.
     * Adds two line breaks after the header.
     */
    private void appendSubHeader(StringBuilder stringBuilder, String title) {
        stringBuilder.append(MARKDOWN_HEADER_LEVEL_2).append(title).append(MARKDOWN_LINE_BREAK);
    }
}
