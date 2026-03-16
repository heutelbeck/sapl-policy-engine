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
package io.sapl.test.coverage.report.html;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Factory for web dependencies required by the HTML coverage report.
 * Discovers CodeMirror WebJar version at runtime from the classpath, so the
 * version number only needs to be specified in pom.xml.
 */
@UtilityClass
class WebDependencyFactory {

    private static final String RESOURCE_PREFIX = "io/sapl/test/coverage/report/html/";

    private static final String TARGET_BASE     = "html/assets/";
    private static final String TARGET_CSS      = TARGET_BASE + "lib/css/";
    private static final String TARGET_IMAGES   = TARGET_BASE + "images/";
    private static final String TARGET_JS       = TARGET_BASE + "lib/js";
    private static final String TARGET_JS_ADDON = TARGET_JS + "/addon/mode/";

    private static final String WEBJAR_BASE       = "META-INF/resources/webjars/";
    private static final String WEBJAR_CODEMIRROR = "codemirror";

    private static final String ERROR_FAILED_DISCOVER_VERSION        = "Failed to discover version for WebJar: %s";
    private static final String ERROR_NO_VERSION_DIRECTORY_FOUND     = "No version directory found";
    private static final String ERROR_NO_VERSION_DIRECTORY_IN_WEBJAR = "No version directory found in WebJar";
    private static final String ERROR_WEBJAR_NOT_FOUND               = "WebJar not found on classpath: %s";

    private static final Map<String, String> WEBJAR_VERSIONS = new HashMap<>();

    private static final List<WebDependency> WEB_DEPENDENCIES;

    static {
        val codemirrorBase = webjarPath(WEBJAR_CODEMIRROR);

        WEB_DEPENDENCIES = List.of(
                // JavaScript
                new WebDependency("codemirror-js", "codemirror.js", codemirrorBase + "lib/", TARGET_JS),
                new WebDependency("simple-mode", "simple.js", codemirrorBase + "addon/mode/", TARGET_JS_ADDON),
                new WebDependency("sapl-mode", "sapl-mode.js", RESOURCE_PREFIX + "js/", TARGET_JS),

                // CSS
                new WebDependency("codemirror-css", "codemirror.css", codemirrorBase + "lib/", TARGET_CSS),
                new WebDependency("main-css", "main.css", RESOURCE_PREFIX + "css/", TARGET_CSS),

                // Images
                new WebDependency("favicon", "favicon.png", RESOURCE_PREFIX + "images/", TARGET_IMAGES),
                new WebDependency("logo-header", "logo-header.png", RESOURCE_PREFIX + "images/", TARGET_IMAGES));
    }

    /**
     * Returns the list of web dependencies needed for the HTML coverage report.
     *
     * @return immutable list of web dependencies with resolved source paths
     */
    List<WebDependency> getWebDependencies() {
        return WEB_DEPENDENCIES;
    }

    private static String webjarPath(String artifactName) {
        val version = WEBJAR_VERSIONS.computeIfAbsent(artifactName, WebDependencyFactory::discoverWebjarVersion);
        return WEBJAR_BASE + artifactName + "/" + version + "/";
    }

    private static String discoverWebjarVersion(String artifactName) {
        val basePath = WEBJAR_BASE + artifactName + "/";
        val resource = WebDependencyFactory.class.getClassLoader().getResource(basePath);

        if (resource == null) {
            throw new IllegalStateException(ERROR_WEBJAR_NOT_FOUND.formatted(artifactName));
        }

        try {
            if ("jar".equals(resource.getProtocol())) {
                return discoverVersionFromJar(resource.toURI().toString(), basePath);
            } else {
                return discoverVersionFromFileSystem(Path.of(resource.toURI()));
            }
        } catch (URISyntaxException | IOException e) {
            throw new IllegalStateException(ERROR_FAILED_DISCOVER_VERSION.formatted(artifactName), e);
        }
    }

    private static String discoverVersionFromJar(String jarUri, String basePath) throws IOException {
        val parts   = jarUri.split("!");
        val jarPath = parts[0];
        try (FileSystem fileSystem = FileSystems.newFileSystem(URI.create(jarPath), Collections.emptyMap())) {
            val path = fileSystem.getPath(basePath);
            try (val entries = Files.list(path)) {
                return entries.filter(Files::isDirectory).map(p -> p.getFileName().toString()).findFirst()
                        .orElseThrow(() -> new IllegalStateException(ERROR_NO_VERSION_DIRECTORY_IN_WEBJAR));
            }
        }
    }

    private static String discoverVersionFromFileSystem(Path basePath) throws IOException {
        try (val entries = Files.list(basePath)) {
            return entries.filter(Files::isDirectory).map(p -> p.getFileName().toString()).findFirst()
                    .orElseThrow(() -> new IllegalStateException(ERROR_NO_VERSION_DIRECTORY_FOUND));
        }
    }

    /**
     * Represents a web dependency to be copied to the output directory.
     *
     * @param name the logical name of the dependency
     * @param fileName the file name
     * @param sourcePath the classpath source path
     * @param targetPath the output target path
     */
    record WebDependency(
            @NonNull String name,
            @NonNull String fileName,
            @NonNull String sourcePath,
            @NonNull String targetPath) {}

}
