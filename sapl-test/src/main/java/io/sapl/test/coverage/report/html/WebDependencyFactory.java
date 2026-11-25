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
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Properties;

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

    private static final String TARGET_BASE = "html/assets/";
    private static final String IMAGES_DIR  = "images/";

    private static final String TARGET_CSS      = TARGET_BASE + "lib/css/";
    private static final String TARGET_IMAGES   = TARGET_BASE + IMAGES_DIR;
    private static final String TARGET_JS       = TARGET_BASE + "lib/js";
    private static final String TARGET_JS_ADDON = TARGET_JS + "/addon/mode/";

    private static final String CODEMIRROR_METADATA = "/META-INF/maven/org.webjars.npm/codemirror/pom.properties";
    private static final String CODEMIRROR_BASE     = "META-INF/resources/webjars/codemirror/%s/";
    private static final String ERROR_VERSION       = "Cannot read CodeMirror WebJar version";

    private static final List<WebDependency> WEB_DEPENDENCIES;

    static {
        val codemirrorBase = codeMirrorPath();

        WEB_DEPENDENCIES = List.of(
                // JavaScript
                new WebDependency("codemirror-js", "codemirror.js", codemirrorBase + "lib/", TARGET_JS),
                new WebDependency("simple-mode", "simple.js", codemirrorBase + "addon/mode/", TARGET_JS_ADDON),
                new WebDependency("sapl-mode", "sapl-mode.js", RESOURCE_PREFIX + "js/", TARGET_JS),

                // CSS
                new WebDependency("codemirror-css", "codemirror.css", codemirrorBase + "lib/", TARGET_CSS),
                new WebDependency("main-css", "main.css", RESOURCE_PREFIX + "css/", TARGET_CSS),

                // Images
                new WebDependency("favicon", "favicon.png", RESOURCE_PREFIX + IMAGES_DIR, TARGET_IMAGES),
                new WebDependency("logo-header", "logo-header.png", RESOURCE_PREFIX + IMAGES_DIR, TARGET_IMAGES));
    }

    /**
     * Returns the list of web dependencies needed for the HTML coverage report.
     *
     * @return immutable list of web dependencies with resolved source paths
     */
    List<WebDependency> getWebDependencies() {
        return WEB_DEPENDENCIES;
    }

    private static String codeMirrorPath() {
        try (val metadata = WebDependencyFactory.class.getResourceAsStream(CODEMIRROR_METADATA)) {
            if (metadata == null) {
                throw new IllegalStateException(ERROR_VERSION);
            }
            val properties = new Properties();
            properties.load(metadata);
            val version = properties.getProperty("version");
            if (version == null) {
                throw new IllegalStateException(ERROR_VERSION);
            }
            return CODEMIRROR_BASE.formatted(version);
        } catch (IOException e) {
            throw new UncheckedIOException(ERROR_VERSION, e);
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
