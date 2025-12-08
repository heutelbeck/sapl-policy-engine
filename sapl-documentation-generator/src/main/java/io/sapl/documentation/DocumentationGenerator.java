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
package io.sapl.documentation;

import io.sapl.api.documentation.LibraryDocumentation;
import io.sapl.api.documentation.LibraryType;
import io.sapl.attributes.libraries.HttpPolicyInformationPoint;
import io.sapl.attributes.libraries.JWTPolicyInformationPoint;
import io.sapl.attributes.libraries.TimePolicyInformationPoint;
import io.sapl.extensions.mqtt.MqttFunctionLibrary;
import io.sapl.extensions.mqtt.MqttPolicyInformationPoint;
import io.sapl.functions.DefaultLibraries;
import io.sapl.functions.geo.GeographicFunctionLibrary;
import io.sapl.pip.geo.traccar.TraccarPolicyInformationPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Component responsible for generating markdown documentation for SAPL
 * libraries and policy information points.
 * Executes automatically after application startup, generates documentation
 * files, and shuts down the application.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentationGenerator {
    private static final String MARKDOWN_HEADER_LEVEL_1  = "# ";
    private static final String MARKDOWN_HEADER_LEVEL_2  = "## ";
    private static final String MARKDOWN_HORIZONTAL_RULE = "---\n\n";
    private static final String MARKDOWN_LINE_BREAK      = "\n\n";

    private final ConfigurableApplicationContext applicationContext;

    @Value("${application.version}")
    private String version;

    @Value("${sapl.documentation.target:target/doc}")
    private String targetPath;

    /**
     * Executes documentation generation after the application context has fully
     * started.
     * Generates markdown files for all function libraries and policy information
     * points,
     * then shuts down the application.
     *
     * @param event the application ready event
     * @throws IOException if file operations fail
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) throws IOException {
        log.info("Starting DocumentationGenerator\n");
        log.info("SAPL Version: {}", version);

        createTargetDirectoryIfNotExisting();
        // LibraryDocumentationExtractor
        val libraries = new ArrayList<Class<?>>(DefaultLibraries.STATIC_LIBRARIES);
        libraries.add(GeographicFunctionLibrary.class);
        libraries.add(MqttFunctionLibrary.class);
        val libDocs = sortDocs(libraries.stream().map(LibraryDocumentationExtractor::extractFunctionLibrary).toList());

        val pips = new ArrayList<Class<?>>();
        pips.add(MqttPolicyInformationPoint.class);
        pips.add(TraccarPolicyInformationPoint.class);
        pips.add(HttpPolicyInformationPoint.class);
        pips.add(JWTPolicyInformationPoint.class);
        pips.add(TimePolicyInformationPoint.class);
        val pipDocs = sortDocs(
                pips.stream().map(LibraryDocumentationExtractor::extractPolicyInformationPoint).toList());

        var navOrder = 101;
        for (val lib : libDocs) {
            log.info("Library: {}", lib.name());
            val documentationMD = generateMarkdownForLibrary(lib, navOrder++);
            writeToFile("lib_" + lib.name() + ".md", documentationMD);
        }
        navOrder = 201;
        for (val pip : pipDocs) {
            log.info("Policy Information Point: {}", pip.name());
            val documentationMD = generateMarkdownForLibrary(pip, navOrder++);
            writeToFile("pip_" + pip.name() + ".md", documentationMD);
        }

        SpringApplication.exit(applicationContext, () -> 0);
    }

    /**
     * Creates the target directory if it does not already exist.
     * Creates all necessary parent directories as well.
     *
     * @throws IOException if directory creation fails
     */
    private void createTargetDirectoryIfNotExisting() throws IOException {
        val targetDirectory = Path.of(targetPath);
        if (!Files.exists(targetDirectory)) {
            Files.createDirectories(targetDirectory);
            log.info("Created target directory: {}", targetPath);
        }
    }

    /**
     * Writes the given content to a file in the target directory.
     * If the file already exists, it will be overwritten.
     *
     * @param filename the name of the file to write
     * @param content the content to write to the file
     * @throws IOException if writing to the file fails
     */
    private void writeToFile(String filename, String content) throws IOException {
        val filePath = Path.of(targetPath, filename);
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
        log.debug("Written documentation to: {}", filePath);
    }

    List<LibraryDocumentation> sortDocs(Collection<LibraryDocumentation> pips) {
        val sortedPips = new ArrayList<LibraryDocumentation>(pips.size());
        sortedPips.addAll(pips);
        sortedPips.sort(Comparator.comparing(LibraryDocumentation::name));
        return sortedPips;
    }

    public String generateMarkdownForLibrary(LibraryDocumentation documentation, int navOrder) {
        val stringBuilder = new StringBuilder();
        appendFrontmatter(stringBuilder, navOrder, documentation.name(),
                documentation.type() == LibraryType.FUNCTION_LIBRARY ? "Functions" : "Attribute Finders");
        appendHeader(stringBuilder, documentation.name());
        stringBuilder.append(documentation.description()).append(MARKDOWN_LINE_BREAK);
        stringBuilder.append(documentation.documentation()).append(MARKDOWN_LINE_BREAK);
        stringBuilder.append(MARKDOWN_HORIZONTAL_RULE);

        for (var entry : documentation.entries()) {
            val functionName          = entry.name();
            val functionDocumentation = entry.documentation();
            appendSubHeader(stringBuilder, functionName);
            stringBuilder.append(functionDocumentation).append(MARKDOWN_LINE_BREAK);
            stringBuilder.append(MARKDOWN_HORIZONTAL_RULE);
        }

        return stringBuilder.toString();
    }

    /**
     * Appends Jekyll front matter to the Markdown document.
     * Includes title, parent page, and navigation order.
     *
     * @param stringBuilder the string builder to append to
     * @param navOrder the navigation order for the page
     * @param title the page title
     * @param parent the parent page name
     */
    private void appendFrontmatter(StringBuilder stringBuilder, int navOrder, String title, String parent) {
        stringBuilder.append(String.format("""
                ---
                layout: default
                title: %s
                parent: %s
                grand_parent: SAPL Reference
                nav_order: %d
                ---
                """, title, parent, navOrder));
    }

    /**
     * Appends a level 1 markdown header with the specified title.
     * Adds two line breaks after the header.
     *
     * @param stringBuilder the string builder to append to
     * @param title the header title
     */
    private void appendHeader(StringBuilder stringBuilder, String title) {
        stringBuilder.append(MARKDOWN_HEADER_LEVEL_1).append(title).append(MARKDOWN_LINE_BREAK);
    }

    /**
     * Appends a level 2 markdown header with the specified title.
     * Adds two line breaks after the header.
     *
     * @param stringBuilder the string builder to append to
     * @param title the header title
     */
    private void appendSubHeader(StringBuilder stringBuilder, String title) {
        stringBuilder.append(MARKDOWN_HEADER_LEVEL_2).append(title).append(MARKDOWN_LINE_BREAK);
    }
}
