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
package io.sapl.api.pdp.plugins;

import io.sapl.api.SaplVersion;
import lombok.NonNull;
import lombok.val;
import org.semver4j.Semver;
import org.semver4j.range.RangeListFactory;

import java.io.Serial;
import java.io.Serializable;

/**
 * A plugin a PDP requires, together with the versions of that plugin the PDP
 * accepts. In {@code pdp.json} a requirement is written as
 * {@code pluginId:versionRange}, e.g. {@code sapl-plugins-example:1.4.+}.
 * <p>
 * The version range follows the NPM range syntax of semver4j, the same syntax
 * {@code semver.satisfies} uses in policies: exact versions ({@code 1.4.0}),
 * X-ranges ({@code 1.4.x}, {@code 1.4.+}), tilde ({@code ~1.4.0}), caret
 * ({@code ^1.4.0}), comparators ({@code >=1.4.0 <2.0.0}) and
 * {@link #ANY_VERSION}. A version range is mandatory; use {@link #ANY_VERSION}
 * to accept any version explicitly.
 * <p>
 * Pre-release versions such as {@code 1.4.0-SNAPSHOT} only satisfy a range
 * that itself names a pre-release of the same {@code major.minor.patch}, e.g.
 * {@code 1.4.0-SNAPSHOT} or {@code >=1.4.0-0}. This also applies to
 * {@link #ANY_VERSION}, which accepts every release but no pre-release.
 *
 * @param pluginId the {@code Plugin-Id} from the plugin manifest
 * @param versionRange the range of plugin versions the PDP accepts
 *
 * @since 4.2.0
 */
public record PluginRequirement(@NonNull String pluginId, @NonNull String versionRange) implements Serializable {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;
    /**
     * The version range accepting every release of a plugin, excluding
     * pre-releases.
     */
    public static final String ANY_VERSION = "*";
    private static final String ERROR_BLANK_PLUGIN_ID = "Plugin ID must not be blank in plugin entry: %s";
    private static final String ERROR_BLANK_VERSION_RANGE = "Version range must not be blank in plugin entry: %s";
    private static final String ERROR_INVALID_VERSION_RANGE = "Version range must be a valid version range in plugin entry: %s";
    private static final char SEPARATOR = ':';

    /**
     * Creates a requirement from its parts.
     *
     * @param pluginId the {@code Plugin-Id} from the plugin manifest
     * @param versionRange the range of plugin versions the PDP accepts
     *
     * @throws IllegalArgumentException if the plugin id or the version range is
     * blank, or the version range is not a valid range
     */
    public PluginRequirement {
        val pluginEntry = pluginId + SEPARATOR + versionRange;
        if (pluginId.isBlank()) {
            throw new IllegalArgumentException(ERROR_BLANK_PLUGIN_ID.formatted(pluginEntry));
        }
        if (versionRange.isBlank()) {
            throw new IllegalArgumentException(ERROR_BLANK_VERSION_RANGE.formatted(pluginEntry));
        }
        if (!ANY_VERSION.equals(versionRange) && RangeListFactory.create(versionRange).get().isEmpty()) {
            throw new IllegalArgumentException(ERROR_INVALID_VERSION_RANGE.formatted(pluginEntry));
        }
    }

    /**
     * Creates a requirement from a configuration entry of the form
     * {@code pluginId:versionRange}. The entry is split at the first
     * {@code ':'}.
     *
     * @param pluginEntry the entry as written in {@code pdp.json}
     *
     * @throws IllegalArgumentException if the entry has no version range, or
     * the plugin id or the version range is invalid
     */
    public PluginRequirement(@NonNull String pluginEntry) {
        this(parsePluginId(pluginEntry), parseVersionRange(pluginEntry));
    }

    private static String parsePluginId(String pluginEntry) {
        val separatorIndex = pluginEntry.indexOf(SEPARATOR);
        return separatorIndex == -1 ? pluginEntry : pluginEntry.substring(0, separatorIndex);
    }

    private static String parseVersionRange(String pluginEntry) {
        val separatorIndex = pluginEntry.indexOf(SEPARATOR);
        return separatorIndex == -1 ? "" : pluginEntry.substring(separatorIndex + 1);
    }

    /**
     * Checks whether a plugin version lies within the accepted version range.
     *
     * @param version the version of the loaded plugin
     *
     * @return {@code true} if the version satisfies the version range
     */
    public boolean isSatisfiedBy(@NonNull Semver version) {
        return version.satisfies(versionRange);
    }

    /**
     * Returns the requirement in its configuration form
     * {@code pluginId:versionRange}, as accepted by
     * {@link #PluginRequirement(String)}.
     *
     * @return the configuration entry of this requirement
     */
    @Override
    public String toString() {
        return pluginId + SEPARATOR + versionRange;
    }
}
