/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.ArrayList;
import java.util.List;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class WebDependencyFactory {
    public List<WebDependency> getWebDependencies() {
        final List<WebDependency> dependencies = new ArrayList<>();

        final String SOURCE_BASE = "dependency-resources/";
        final String TARGET_BASE = "html/assets/";
        final String JS_BASE     = TARGET_BASE + "lib/js";
        final String CSS_BASE    = TARGET_BASE + "lib/css/";
        final String IMAGES      = "images/";
        final String IMAGE_BASE  = TARGET_BASE + IMAGES;

        // JS
        dependencies.add(new WebDependency("sapl-mode", "sapl-mode.js", SOURCE_BASE, JS_BASE));
        dependencies.add(new WebDependency("codemirror", "codemirror.js", SOURCE_BASE + "codemirror/lib/", JS_BASE));
        dependencies.add(new WebDependency("simple_mode", "simple.js", SOURCE_BASE + "codemirror/addon/mode/",
                JS_BASE + "/addon/mode/"));
        dependencies
                .add(new WebDependency("bootstrap", "bootstrap.min.js", SOURCE_BASE + "bootstrap/dist/js/", JS_BASE));
        dependencies.add(
                new WebDependency("@popperjs", "popper.min.js", SOURCE_BASE + "@popperjs/core/dist/umd/", JS_BASE));
        dependencies.add(new WebDependency("requirejs", "require.js", SOURCE_BASE + "requirejs/", JS_BASE));

        // CSS
        dependencies.add(new WebDependency("main.css", "main.css", "html/css/", CSS_BASE));
        dependencies.add(
                new WebDependency("bootstrap", "bootstrap.min.css", SOURCE_BASE + "bootstrap/dist/css/", CSS_BASE));
        dependencies.add(new WebDependency("codemirror", "codemirror.css", SOURCE_BASE + "codemirror/lib/", CSS_BASE));

        // images
        dependencies.add(new WebDependency("logo-header", "logo-header.png", IMAGES, IMAGE_BASE));
        dependencies.add(new WebDependency("favicon", "favicon.png", IMAGES, IMAGE_BASE));

        return dependencies;
    }

    public record WebDependency(@NonNull String name, @NonNull String fileName, @NonNull String sourcePath,
            @NonNull String targetPath) {
    }

}
