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

import static org.semver4j.Semver.parse;

import java.io.IOException;
import java.util.Properties;

import org.jspecify.annotations.Nullable;

import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Build-time information for the SAPL engine. The version is read once from the
 * Maven-filtered {@code saplversion.properties} resource so it always matches
 * {@code ${project.version}}.
 */
@UtilityClass
public class SaplBuildInfo {

    private static final String ERROR_ENGINE_VERSION_INVALID = "Cannot generate a minimum engine version requirement from sapl-pdp build version '%s'. Expected a valid semantic version.";

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

    /**
     * Generates a minimum version requirement accepted by the installed engine.
     * Releases use {@code >=major.minor.patch}. Prereleases use
     * {@code >=major.minor.patch-0} to include prereleases of that base version.
     * Build metadata is omitted. Later prerelease versions still require an
     * explicit range for their own base version.
     *
     * @return the minimum engine version requirement
     * @throws IllegalStateException if the engine build version is unavailable or invalid
     */
    public static String minimumVersionRequirement() {
        return minimumVersionRequirement(version());
    }

    static String minimumVersionRequirement(@Nullable String engineVersion) {
        val parsedVersion = parse(engineVersion);
        if (parsedVersion == null) {
            throw new IllegalStateException(ERROR_ENGINE_VERSION_INVALID.formatted(engineVersion));
        }
        val baseVersion = parsedVersion.withClearedPreReleaseAndBuild().getVersion();
        return ">=" + baseVersion + (parsedVersion.getPreRelease().isEmpty() ? "" : "-0");
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
