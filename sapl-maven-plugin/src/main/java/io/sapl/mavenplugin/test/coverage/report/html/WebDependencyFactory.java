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
package io.sapl.mavenplugin.test.coverage.report.html;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;

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

/**
 * Factory for web dependencies required by the HTML coverage report.
 * Discovers WebJar versions at runtime from the classpath, so version numbers
 * only need to be specified in pom.xml.
 */
@UtilityClass
public class WebDependencyFactory {

    // ==================== Resource Source Paths ====================
    private static final String SOURCE_DEPENDENCY_RESOURCES = "dependency-resources/";
    private static final String SOURCE_HTML_CSS             = "html/css/";
    private static final String SOURCE_IMAGES               = "images/";

    // ==================== Output Directory Paths ====================
    private static final String TARGET_BASE     = "html/assets/";
    private static final String TARGET_CSS      = TARGET_BASE + "lib/css/";
    private static final String TARGET_IMAGES   = TARGET_BASE + SOURCE_IMAGES;
    private static final String TARGET_JS       = TARGET_BASE + "lib/js";
    private static final String TARGET_JS_ADDON = TARGET_JS + "/addon/mode/";

    // ==================== WebJar Artifacts ====================
    private static final String WEBJAR_BASE          = "META-INF/resources/webjars/";
    private static final String WEBJAR_BOOTSTRAP     = "bootstrap";
    private static final String WEBJAR_CODEMIRROR    = "codemirror";
    private static final String WEBJAR_POPPERJS_CORE = "popperjs__core";
    private static final String WEBJAR_REQUIREJS     = "requirejs";

    // ==================== WebJar Subdirectory Paths ====================
    private static final String WEBJAR_SUBDIR_ADDON_MODE = "addon/mode/";
    private static final String WEBJAR_SUBDIR_CSS        = "css/";
    private static final String WEBJAR_SUBDIR_DIST_UMD   = "dist/umd/";
    private static final String WEBJAR_SUBDIR_JS         = "js/";
    private static final String WEBJAR_SUBDIR_LIB        = "lib/";

    // ==================== CSS File Names ====================
    private static final String FILE_BOOTSTRAP_MIN_CSS = "bootstrap.min.css";
    private static final String FILE_CODEMIRROR_CSS    = "codemirror.css";
    private static final String FILE_MAIN_CSS          = "main.css";

    // ==================== Image File Names ====================
    private static final String FILE_FAVICON     = "favicon.png";
    private static final String FILE_LOGO_HEADER = "logo-header.png";

    // ==================== JavaScript File Names ====================
    private static final String FILE_BOOTSTRAP_MIN_JS = "bootstrap.min.js";
    private static final String FILE_CODEMIRROR_JS    = "codemirror.js";
    private static final String FILE_POPPER_MIN_JS    = "popper.min.js";
    private static final String FILE_REQUIRE_JS       = "require.js";
    private static final String FILE_SAPL_MODE_JS     = "sapl-mode.js";
    private static final String FILE_SIMPLE_JS        = "simple.js";

    // ==================== Error Messages ====================
    private static final String ERROR_FAILED_DISCOVER_VERSION        = "Failed to discover version for WebJar: %s";
    private static final String ERROR_NO_VERSION_DIRECTORY_FOUND     = "No version directory found";
    private static final String ERROR_NO_VERSION_DIRECTORY_IN_WEBJAR = "No version directory found in WebJar";
    private static final String ERROR_WEBJAR_NOT_FOUND               = "WebJar not found on classpath: %s";

    // ==================== Version Cache ====================
    private static final Map<String, String> WEBJAR_VERSIONS = new HashMap<>();

    // ==================== Static Dependency List ====================
    private static final List<WebDependency> WEB_DEPENDENCIES;

    static {
        val codemirrorBase = webjarPath(WEBJAR_CODEMIRROR);
        val bootstrapBase  = webjarPath(WEBJAR_BOOTSTRAP);
        val popperjsBase   = webjarPath(WEBJAR_POPPERJS_CORE) + WEBJAR_SUBDIR_DIST_UMD;
        val requirejsBase  = webjarPath(WEBJAR_REQUIREJS);

        WEB_DEPENDENCIES = List.of(
                // JavaScript
                new WebDependency("@popperjs", FILE_POPPER_MIN_JS, popperjsBase, TARGET_JS),
                new WebDependency("bootstrap-js", FILE_BOOTSTRAP_MIN_JS, bootstrapBase + WEBJAR_SUBDIR_JS, TARGET_JS),
                new WebDependency("codemirror-js", FILE_CODEMIRROR_JS, codemirrorBase + WEBJAR_SUBDIR_LIB, TARGET_JS),
                new WebDependency(WEBJAR_REQUIREJS, FILE_REQUIRE_JS, requirejsBase, TARGET_JS),
                new WebDependency("sapl-mode", FILE_SAPL_MODE_JS, SOURCE_DEPENDENCY_RESOURCES, TARGET_JS),
                new WebDependency("simple-mode", FILE_SIMPLE_JS, codemirrorBase + WEBJAR_SUBDIR_ADDON_MODE,
                        TARGET_JS_ADDON),

                // CSS
                new WebDependency("bootstrap-css", FILE_BOOTSTRAP_MIN_CSS, bootstrapBase + WEBJAR_SUBDIR_CSS,
                        TARGET_CSS),
                new WebDependency("codemirror-css", FILE_CODEMIRROR_CSS, codemirrorBase + WEBJAR_SUBDIR_LIB,
                        TARGET_CSS),
                new WebDependency("main-css", FILE_MAIN_CSS, SOURCE_HTML_CSS, TARGET_CSS),

                // Images
                new WebDependency("favicon", FILE_FAVICON, SOURCE_IMAGES, TARGET_IMAGES),
                new WebDependency("logo-header", FILE_LOGO_HEADER, SOURCE_IMAGES, TARGET_IMAGES));
    }

    /**
     * Returns the list of web dependencies needed for the HTML coverage report.
     * WebJar versions are discovered at runtime from the classpath during class
     * initialization.
     *
     * @return immutable list of web dependencies with resolved source paths
     */
    public List<WebDependency> getWebDependencies() {
        return WEB_DEPENDENCIES;
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

    private String discoverVersionFromJar(String jarUri, String basePath) throws IOException {
        val parts   = jarUri.split("!");
        val jarPath = parts[0];
        try (FileSystem fileSystem = FileSystems.newFileSystem(URI.create(jarPath), Collections.emptyMap())) {
            val path = fileSystem.getPath(basePath);
            try (var entries = Files.list(path)) {
                return entries.filter(Files::isDirectory).map(p -> p.getFileName().toString()).findFirst()
                        .orElseThrow(() -> new IllegalStateException(ERROR_NO_VERSION_DIRECTORY_IN_WEBJAR));
            }
        }
    }

    private String discoverVersionFromFileSystem(Path basePath) throws IOException {
        try (var entries = Files.list(basePath)) {
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
    public record WebDependency(
            @NonNull String name,
            @NonNull String fileName,
            @NonNull String sourcePath,
            @NonNull String targetPath) {}

}
