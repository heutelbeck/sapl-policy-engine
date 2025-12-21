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
package io.sapl.pdp.configuration;

import reactor.core.Disposable;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Source of PDP configurations that notifies a callback when configurations
 * change.
 * <p>
 * Implementations handle the specifics of where configurations come from
 * (filesystem directories, ZIP bundles,
 * classpath resources) and monitor for changes. When a configuration is loaded
 * or updated, the implementation invokes
 * the callback provided at construction time.
 * <p>
 * The callback-based design provides several benefits:
 * <ul>
 * <li><b>Simple lifecycle:</b> No subscription management - dispose stops
 * watching</li>
 * <li><b>Spring-friendly:</b> Sources are beans with standard destroy
 * methods</li>
 * <li><b>Testable:</b> Callbacks are easy to mock and verify</li>
 * <li><b>Clear dependencies:</b> Source depends on consumer (via callback), not
 * vice versa</li>
 * </ul>
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * // Create source with callback to configuration register
 * var source = new DirectoryPDPConfigurationSource(Path.of("/policies"), "default",
 *         security -> register.loadConfiguration(security, true));
 *
 * // Source automatically loads initial configuration and watches for changes
 * // When done, dispose to stop file watching
 * source.dispose();
 * }</pre>
 * <p>
 * Thread Safety: Implementations must be thread-safe. The callback may be
 * invoked from background threads (e.g., file
 * watcher threads).
 */
public interface PDPConfigurationSource extends Disposable {

    String DEFAULT_PDP_ID = "default";

    /**
     * Maximum allowed length for PDP identifiers.
     */
    int MAX_PDP_ID_LENGTH = 255;

    /**
     * Pattern for valid PDP identifiers: alphanumeric, hyphens, underscores, and
     * dots.
     */
    Pattern VALID_PDP_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");

    /**
     * Validates a PDP identifier.
     *
     * @param pdpId
     * the PDP identifier to validate
     *
     * @throws PDPConfigurationException
     * if the pdpId is invalid
     */
    static void validatePdpId(String pdpId) {
        if (pdpId == null || pdpId.isEmpty()) {
            throw new PDPConfigurationException("PDP identifier must not be null or empty.");
        }
        if (pdpId.length() > MAX_PDP_ID_LENGTH) {
            throw new PDPConfigurationException(
                    "PDP identifier exceeds maximum length of %d characters.".formatted(MAX_PDP_ID_LENGTH));
        }
        if (!VALID_PDP_ID_PATTERN.matcher(pdpId).matches()) {
            throw new PDPConfigurationException(
                    "PDP identifier contains invalid characters. Only alphanumeric characters, hyphens, underscores, and dots are allowed.");
        }
    }

    /**
     * Checks if a PDP identifier is valid without throwing an exception.
     *
     * @param pdpId
     * the PDP identifier to check
     *
     * @return true if valid, false otherwise
     */
    static boolean isValidPdpId(String pdpId) {
        return pdpId != null && !pdpId.isEmpty() && pdpId.length() <= MAX_PDP_ID_LENGTH
                && VALID_PDP_ID_PATTERN.matcher(pdpId).matches();
    }

    /**
     * Resolves the home folder marker (~) in a path string.
     * <p>
     * If the path starts with ~ followed by the system file separator, the ~ is
     * replaced with the user's home directory
     * (from the "user.home" system property). Otherwise, the path is returned
     * as-is.
     * </p>
     * <p>
     * This method normalizes forward slashes to the system file separator for
     * cross-platform compatibility.
     * </p>
     *
     * @param path
     * the path string potentially containing ~ at the start
     *
     * @return the resolved path
     */
    static Path resolveHomeFolderIfPresent(String path) {
        var normalizedPath = path.replace("/", File.separator);

        if (normalizedPath.startsWith("~" + File.separator)) {
            return Paths.get(System.getProperty("user.home") + normalizedPath.substring(1));
        }

        return Paths.get(normalizedPath);
    }

    /**
     * Resolves the home folder marker (~) in a Path.
     * <p>
     * This overload converts the path to a string and delegates to
     * {@link #resolveHomeFolderIfPresent(String)}.
     * </p>
     *
     * @param path
     * the path potentially containing ~ at the start
     *
     * @return the resolved path
     */
    static Path resolveHomeFolderIfPresent(Path path) {
        return resolveHomeFolderIfPresent(path.toString());
    }

}
