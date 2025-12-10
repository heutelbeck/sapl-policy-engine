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
package io.sapl.server.ce.ui.views.librariesdocumentation;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import io.sapl.api.documentation.LibraryDocumentation;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class MarkdownGenerator {
    private static final Parser       PARSER   = Parser.builder().build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().build();

    public String generateMarkdownForLibrary(LibraryDocumentation documentation) {
        final var sb = new StringBuilder();
        sb.append("# ").append(documentation.getName()).append("\n\n");
        sb.append(documentation.getDescription()).append("\n\n");
        sb.append(documentation.getLibraryDocumentation()).append("\n\n");
        sb.append("---\n\n");

        for (var entry : documentation.getDocumentation().entrySet()) {
            final var name = entry.getKey();
            final var docs = entry.getValue();
            sb.append("## ").append(name).append("\n\n");
            sb.append(docs).append("\n\n---\n\n");
        }
        return sb.toString();
    }

    public String generateMarkdownForPolicyInformationPoint(
            io.sapl.attributes.documentation.api.LibraryDocumentation pip) {
        final var sb = new StringBuilder();
        sb.append("# ").append(pip.namespace()).append("\n\n");
        sb.append(pip.descriptionMarkdown()).append("\n\n");
        sb.append(pip.documentationMarkdown()).append("\n\n");
        sb.append("---\n\n");

        for (var attribute : pip.attributes()) {
            final var name = attribute.fullyQualifiedName();
            final var docs = attribute.documentationMarkdown();
            sb.append("## ").append(name).append("\n\n");
            sb.append(docs).append("\n\n---\n\n");
        }
        return sb.toString();
    }

    public String markdownToHtml(@NonNull String markdown) {
        final var document = PARSER.parse(markdown);
        return RENDERER.render(document);
    }

    public String wrapInDiv(String innerHtml) {
        return String.format("<div>%n%s%n</div>", innerHtml);
    }
}
