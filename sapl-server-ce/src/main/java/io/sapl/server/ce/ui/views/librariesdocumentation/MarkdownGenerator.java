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
package io.sapl.server.ce.ui.views.librariesdocumentation;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import io.sapl.api.documentation.LibraryDocumentation;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

/**
 * Utilities for generating markdown documentation from library documentation.
 */
@UtilityClass
public class MarkdownGenerator {
    private static final Parser       PARSER   = Parser.builder().build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().build();

    /**
     * Generates markdown documentation for a library.
     *
     * @param library the library documentation
     * @return the markdown string
     */
    public String generateMarkdownForLibrary(LibraryDocumentation library) {
        var builder = new StringBuilder();
        builder.append("# ").append(library.name()).append("\n\n");
        builder.append(library.description()).append("\n\n");
        if (library.documentation() != null && !library.documentation().isEmpty()) {
            builder.append(library.documentation()).append("\n\n");
        }
        builder.append("---\n\n");

        for (var entry : library.entries()) {
            builder.append("## ").append(entry.name()).append("\n\n");
            if (entry.documentation() != null && !entry.documentation().isEmpty()) {
                builder.append(entry.documentation()).append("\n\n");
            }
            builder.append("---\n\n");
        }
        return builder.toString();
    }

    /**
     * Converts markdown to HTML.
     *
     * @param markdown the markdown string
     * @return the HTML string
     */
    public String markdownToHtml(@NonNull String markdown) {
        var document = PARSER.parse(markdown);
        return RENDERER.render(document);
    }

    /**
     * Wraps content in a div element.
     *
     * @param innerHtml the inner HTML content
     * @return the wrapped HTML
     */
    public String wrapInDiv(String innerHtml) {
        return "<div>%n%s%n</div>".formatted(innerHtml);
    }
}
