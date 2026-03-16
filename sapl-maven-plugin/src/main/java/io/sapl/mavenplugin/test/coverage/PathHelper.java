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
package io.sapl.mavenplugin.test.coverage;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.plugin.logging.Log;

import lombok.experimental.UtilityClass;

/**
 * Utility for resolving the coverage output base directory.
 */
@UtilityClass
public class PathHelper {

    /**
     * Resolves the base directory for coverage output.
     *
     * @param configBaseDir user-configured base directory, or null/empty for
     * default
     * @param projectBuildDir Maven project build output directory
     * @param log Maven logger
     * @return resolved coverage base directory
     */
    public static Path resolveBaseDir(String configBaseDir, String projectBuildDir, Log log) {
        if (configBaseDir != null && !configBaseDir.isEmpty()) {
            log.debug(String.format("Using \"%s\" as base dir for sapl coverage", configBaseDir));
            return Paths.get(configBaseDir).resolve("sapl-coverage");
        } else {
            log.debug(String.format("Using \"%s\" as base dir for sapl coverage", projectBuildDir));
            return Paths.get(projectBuildDir).resolve("sapl-coverage");
        }
    }

}
