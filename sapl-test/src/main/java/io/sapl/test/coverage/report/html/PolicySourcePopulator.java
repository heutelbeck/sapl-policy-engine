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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import io.sapl.api.coverage.PolicyCoverageData;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Reads policy source files to populate
 * {@link PolicyCoverageData#getDocumentSource()} for HTML report generation.
 * <p>
 * Coverage data stores file paths relative to the classpath root. For HTML
 * reports with syntax highlighting, the actual source text must be read from
 * candidate directories (e.g. {@code src/main/resources},
 * {@code target/classes},
 * or a CLI {@code --dir} path).
 */
@Slf4j
@UtilityClass
public class PolicySourcePopulator {

    /**
     * Populates the document source for each policy by searching candidate
     * directories.
     *
     * @param policies the coverage data whose sources may be missing
     * @param candidateDirs ordered list of directories to search for source files
     */
    public static void populateSources(Collection<PolicyCoverageData> policies, List<Path> candidateDirs) {
        for (val policy : policies) {
            populateSource(policy, candidateDirs);
        }
    }

    private static void populateSource(PolicyCoverageData policy, List<Path> candidateDirs) {
        if (policy.getDocumentSource() != null && !policy.getDocumentSource().isEmpty()) {
            return;
        }

        val filePath = policy.getFilePath();
        if (filePath == null || filePath.isEmpty()) {
            log.debug("No file path for policy: {}", policy.getDocumentName());
            return;
        }

        val source = tryReadSource(candidateDirs, filePath);
        if (source != null) {
            policy.setDocumentSource(source);
            log.debug("Loaded source for: {}", policy.getDocumentName());
        } else {
            log.debug("Source file not found for: {}", policy.getDocumentName());
        }
    }

    private static String tryReadSource(List<Path> candidateDirs, String relativePath) {
        for (val dir : candidateDirs) {
            val path = dir.resolve(relativePath);
            if (Files.exists(path)) {
                try {
                    return Files.readString(path);
                } catch (IOException e) {
                    log.debug("Could not read: {} - {}", path, e.getMessage());
                }
            }
        }
        return null;
    }

}
