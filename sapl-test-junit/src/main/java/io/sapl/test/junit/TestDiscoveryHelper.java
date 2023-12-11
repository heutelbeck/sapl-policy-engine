/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.test.utils.ClasspathHelper;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.assertj.core.util.Arrays;

class TestDiscoveryHelper {
    TestDiscoveryHelper() {
    }

    private static final String[] SAPLTEST_FILE_EXTENSIONS = Arrays.array("sapltest");

    public static List<String> discoverTests() {
        var dir = ClasspathHelper.findPathOnClasspath(TestDiscoveryHelper.class.getClassLoader(), "").toFile();

        return FileUtils.listFiles(dir, SAPLTEST_FILE_EXTENSIONS, true).stream()
                .map(file -> dir.toPath().relativize(file.toPath()).toString()).toList();
    }
}
