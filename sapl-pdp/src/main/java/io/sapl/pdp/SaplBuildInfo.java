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
package io.sapl.pdp;

import java.io.IOException;
import java.util.Properties;

import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Build-time information for the SAPL engine. The version is read once from the
 * Maven-filtered {@code saplversion.properties} resource so it always matches
 * {@code ${project.version}}.
 */
@UtilityClass
public class SaplBuildInfo {

    private static final String RESOURCE    = "/saplversion.properties";
    private static final String VERSION_KEY = "saplVersion";
    private static final String UNKNOWN     = "unknown";

    private static final String VERSION = load();

    /**
     * The SAPL engine version, for example {@code 4.1.0} or
     * {@code 4.1.0-SNAPSHOT}, or {@code unknown} if the resource is unavailable.
     *
     * @return the engine version string
     */
    public static String version() {
        return VERSION;
    }

    private static String load() {
        try (val in = SaplBuildInfo.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return UNKNOWN;
            }
            val properties = new Properties();
            properties.load(in);
            return properties.getProperty(VERSION_KEY, UNKNOWN);
        } catch (IOException e) {
            // Build info is best-effort. An unreadable resource degrades to unknown
            // rather than failing engine startup.
            return UNKNOWN;
        }
    }
}
