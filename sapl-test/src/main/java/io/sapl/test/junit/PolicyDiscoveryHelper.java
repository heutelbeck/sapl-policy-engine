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
package io.sapl.test.junit;

import io.sapl.test.plain.SaplDocument;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Helper class for discovering SAPL policy files from the classpath.
 */
@Slf4j
class PolicyDiscoveryHelper {

    private static final String   WARN_FAILED_TO_READ_POLICY = "Failed to read policy file {}: {}";
    private static final String[] SAPL_FILE_EXTENSIONS       = { "sapl" };
    public static final String    RESOURCES_ROOT             = "src/main/resources";

    private PolicyDiscoveryHelper() {
    }

    /**
     * Discovers all SAPL policy files from the resources root.
     *
     * @return list of SaplDocument instances
     */
    public static List<SaplDocument> discoverPolicies() {
        var directory = FileUtils.getFile(RESOURCES_ROOT);
        if (!directory.exists() || !directory.isDirectory()) {
            return new ArrayList<>();
        }
        var files = FileUtils.listFiles(directory, SAPL_FILE_EXTENSIONS, true);
        return processFiles(files, directory, PolicyDiscoveryHelper::extractPolicyName);
    }

    /**
     * Discovers all SAPL policy files from a specific subdirectory under resources.
     * <p>
     * Naming convention:
     * <ul>
     * <li>"policies" directory: just filename (e.g., "policySimple") for unit
     * tests</li>
     * <li>Other directories: full path (e.g., "policiesIT/policy_A") for
     * integration tests</li>
     * </ul>
     *
     * @param subdirectory the subdirectory to search (e.g., "policies")
     * @return list of SaplDocument instances
     */
    public static List<SaplDocument> discoverPolicies(String subdirectory) {
        var directory   = FileUtils.getFile(RESOURCES_ROOT, subdirectory);
        var useFilename = "policies".equals(subdirectory);
        if (!directory.exists() || !directory.isDirectory()) {
            return new ArrayList<>();
        }
        var files = FileUtils.listFiles(directory, SAPL_FILE_EXTENSIONS, true);
        return processFiles(files, directory,
                relativePath -> extractPolicyName(subdirectory, relativePath, useFilename));
    }

    private static List<SaplDocument> processFiles(Collection<File> files, File directory,
            UnaryOperator<String> nameExtractor) {
        var documents   = new ArrayList<SaplDocument>();
        var resourceDir = FileUtils.getFile(RESOURCES_ROOT);
        for (var file : files) {
            try {
                var relativePath = directory.toPath().relativize(file.toPath()).toString();
                var name         = nameExtractor.apply(relativePath);
                var sourceCode   = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                var filePath     = resourceDir.toPath().relativize(file.toPath()).toString().replace(File.separatorChar,
                        '/');
                documents.add(SaplDocument.of(name, sourceCode, filePath));
            } catch (IOException e) {
                log.warn(WARN_FAILED_TO_READ_POLICY, file.getPath(), e.getMessage());
            }
        }
        return documents;
    }

    /**
     * Extracts the policy name from a relative path (for root discovery).
     * Uses full relative path with normalized separators.
     *
     * @param relativePath the path relative to resources root
     * @return the policy name
     */
    private static String extractPolicyName(String relativePath) {
        // Remove .sapl extension
        var name = relativePath;
        if (name.endsWith(".sapl")) {
            name = name.substring(0, name.length() - 5);
        }
        // Normalize path separators to forward slashes
        return name.replace(File.separatorChar, '/');
    }

    /**
     * Extracts the policy name from a file path.
     * <p>
     * For "policies" directory, returns just the filename for unit test
     * compatibility.
     * For other directories, includes the subdirectory prefix for integration
     * tests.
     *
     * @param subdirectory the subdirectory prefix
     * @param relativePath the path relative to the subdirectory
     * @param useFilenameOnly if true, returns just the filename without prefix
     * @return the policy name
     */
    private static String extractPolicyName(String subdirectory, String relativePath, boolean useFilenameOnly) {
        // Remove .sapl extension
        var name = relativePath;
        if (name.endsWith(".sapl")) {
            name = name.substring(0, name.length() - 5);
        }
        // Normalize path separators to forward slashes
        name = name.replace(File.separatorChar, '/');

        if (useFilenameOnly) {
            // For "policies" directory, use just filename for unit tests
            var lastSlash = name.lastIndexOf('/');
            if (lastSlash >= 0) {
                name = name.substring(lastSlash + 1);
            }
            return name;
        }
        // For other directories, include subdirectory prefix for integration tests
        return subdirectory + "/" + name;
    }
}
