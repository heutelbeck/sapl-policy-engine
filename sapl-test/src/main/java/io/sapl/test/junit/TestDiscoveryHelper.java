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

import org.apache.commons.io.FileUtils;

import java.util.List;

class TestDiscoveryHelper {

    private static final String[] SAPL_TEST_FILE_EXTENSIONS = { "sapltest" };
    public static final String    RESOURCES_ROOT            = "src/test/resources";

    private TestDiscoveryHelper() {
    }

    /**
     * Discovers all SAPL test files in the test resources directory.
     * <p>
     * Searches recursively for files with the {@code .sapltest} extension
     * under {@code src/test/resources}.
     *
     * @return list of relative paths to discovered test files
     */
    public static List<String> discoverTests() {
        var directory = FileUtils.getFile(RESOURCES_ROOT);
        return FileUtils.listFiles(directory, SAPL_TEST_FILE_EXTENSIONS, true).stream()
                .map(file -> directory.toPath().relativize(file.toPath()).toString()).toList();
    }
}
