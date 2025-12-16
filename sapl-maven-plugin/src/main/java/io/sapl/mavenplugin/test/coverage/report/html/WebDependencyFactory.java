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
package io.sapl.mavenplugin.test.coverage.report.html;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory for web dependencies required by the HTML coverage report.
 * Discovers WebJar versions at runtime from the classpath, so version numbers
 * only need to be specified in pom.xml.
 */
@UtilityClass
public class WebDependencyFactory {

    private static final String WEBJAR_BASE = "META-INF/resources/webjars/";

    private static final Map<String, String> WEBJAR_VERSIONS = new HashMap<>();

    /**
     * Returns the list of web dependencies needed for the HTML coverage report.
     * WebJar versions are discovered at runtime from the classpath.
     *
     * @return list of web dependencies with resolved source paths
     */
    public List<WebDependency> getWebDependencies() {
        val dependencies = new ArrayList<WebDependency>();

        val targetBase = "html/assets/";
        val jsBase     = targetBase + "lib/js";
        val cssBase    = targetBase + "lib/css/";
        val images     = "images/";
        val imageBase  = targetBase + images;

        val codemirrorBase = webjarPath("codemirror");
        val bootstrapBase  = webjarPath("bootstrap");
        val popperjsBase   = webjarPath("popperjs__core") + "dist/umd/";
        val requirejsBase  = webjarPath("requirejs");

        // JS
        dependencies.add(new WebDependency("sapl-mode", "sapl-mode.js", "dependency-resources/", jsBase));
        dependencies.add(new WebDependency("codemirror", "codemirror.js", codemirrorBase + "lib/", jsBase));
        dependencies.add(
                new WebDependency("simple_mode", "simple.js", codemirrorBase + "addon/mode/", jsBase + "/addon/mode/"));
        dependencies.add(new WebDependency("bootstrap", "bootstrap.min.js", bootstrapBase + "js/", jsBase));
        dependencies.add(new WebDependency("@popperjs", "popper.min.js", popperjsBase, jsBase));
        dependencies.add(new WebDependency("requirejs", "require.js", requirejsBase, jsBase));

        // CSS
        dependencies.add(new WebDependency("main.css", "main.css", "html/css/", cssBase));
        dependencies.add(new WebDependency("bootstrap", "bootstrap.min.css", bootstrapBase + "css/", cssBase));
        dependencies.add(new WebDependency("codemirror", "codemirror.css", codemirrorBase + "lib/", cssBase));

        // images
        dependencies.add(new WebDependency("logo-header", "logo-header.png", images, imageBase));
        dependencies.add(new WebDependency("favicon", "favicon.png", images, imageBase));

        return dependencies;
    }

    /**
     * Constructs the WebJar classpath prefix for an artifact, discovering the
     * version at runtime.
     *
     * @param artifactName the WebJar artifact name (e.g., "codemirror",
     * "bootstrap")
     * @return the classpath prefix including version (e.g.,
     * "META-INF/resources/webjars/codemirror/6.65.7/")
     */
    private String webjarPath(String artifactName) {
        val version = WEBJAR_VERSIONS.computeIfAbsent(artifactName, WebDependencyFactory::discoverWebjarVersion);
        return WEBJAR_BASE + artifactName + "/" + version + "/";
    }

    /**
     * Discovers the version of a WebJar by scanning the classpath.
     * WebJars store resources at META-INF/resources/webjars/{artifact}/{version}/
     */
    private String discoverWebjarVersion(String artifactName) {
        val basePath = WEBJAR_BASE + artifactName + "/";
        val resource = WebDependencyFactory.class.getClassLoader().getResource(basePath);

        if (resource == null) {
            throw new IllegalStateException("WebJar not found on classpath: " + artifactName);
        }

        try {
            if ("jar".equals(resource.getProtocol())) {
                return discoverVersionFromJar(resource.toURI().toString(), basePath);
            } else {
                return discoverVersionFromFileSystem(Path.of(resource.toURI()));
            }
        } catch (URISyntaxException | IOException e) {
            throw new IllegalStateException("Failed to discover version for WebJar: " + artifactName, e);
        }
    }

    private String discoverVersionFromJar(String jarUri, String basePath) throws IOException {
        val parts   = jarUri.split("!");
        val jarPath = parts[0];
        try (FileSystem fileSystem = FileSystems.newFileSystem(java.net.URI.create(jarPath), Collections.emptyMap())) {
            val path = fileSystem.getPath(basePath);
            try (var entries = Files.list(path)) {
                return entries.filter(Files::isDirectory).map(p -> p.getFileName().toString()).findFirst()
                        .orElseThrow(() -> new IllegalStateException("No version directory found in WebJar"));
            }
        }
    }

    private String discoverVersionFromFileSystem(Path basePath) throws IOException {
        try (var entries = Files.list(basePath)) {
            return entries.filter(Files::isDirectory).map(p -> p.getFileName().toString()).findFirst()
                    .orElseThrow(() -> new IllegalStateException("No version directory found"));
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
    public record WebDependency(
            @NonNull String name,
            @NonNull String fileName,
            @NonNull String sourcePath,
            @NonNull String targetPath) {}

}
