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
package io.sapl.pdp.configuration.source;

import io.sapl.pdp.configuration.PDPConfigurationException;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Utility class for PDP identifier validation and path resolution.
 */
@UtilityClass
public class PdpIdValidator {

    public static final String DEFAULT_PDP_ID = "default";

    static final String ERROR_PDP_ID_EXCEEDS_MAX_LENGTH = "PDP identifier exceeds maximum length of %d characters.";
    static final String ERROR_PDP_ID_INVALID_CHARACTERS = "PDP identifier contains invalid characters. Only alphanumeric characters, hyphens, underscores, and dots are allowed.";
    static final String ERROR_PDP_ID_NULL_OR_EMPTY      = "PDP identifier must not be null or empty.";

    /**
     * Maximum allowed length for PDP identifiers.
     */
    public static final int MAX_PDP_ID_LENGTH = 255;

    /**
     * Pattern for valid PDP identifiers: alphanumeric, hyphens, underscores, and
     * dots.
     */
    public static final Pattern VALID_PDP_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");

    /**
     * Validates a PDP identifier.
     *
     * @param pdpId
     * the PDP identifier to validate
     *
     * @throws PDPConfigurationException
     * if the pdpId is invalid
     */
    public static void validatePdpId(String pdpId) {
        if (pdpId == null || pdpId.isEmpty()) {
            throw new PDPConfigurationException(ERROR_PDP_ID_NULL_OR_EMPTY);
        }
        if (pdpId.length() > MAX_PDP_ID_LENGTH) {
            throw new PDPConfigurationException(ERROR_PDP_ID_EXCEEDS_MAX_LENGTH.formatted(MAX_PDP_ID_LENGTH));
        }
        if (!VALID_PDP_ID_PATTERN.matcher(pdpId).matches()) {
            throw new PDPConfigurationException(ERROR_PDP_ID_INVALID_CHARACTERS);
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
    public static boolean isValidPdpId(String pdpId) {
        return pdpId != null && !pdpId.isEmpty() && pdpId.length() <= MAX_PDP_ID_LENGTH
                && VALID_PDP_ID_PATTERN.matcher(pdpId).matches();
    }

    /**
     * Resolves the home folder marker (~) in a path string.
     * <p>
     * If the path starts with ~ followed by the system file separator, the ~ is
     * replaced with the user's home directory (from the "user.home" system
     * property). Otherwise, the path is returned as-is.
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
    public static Path resolveHomeFolderIfPresent(String path) {
        val normalizedPath = path.replace("/", File.separator);

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
    public static Path resolveHomeFolderIfPresent(Path path) {
        return resolveHomeFolderIfPresent(path.toString());
    }

}
