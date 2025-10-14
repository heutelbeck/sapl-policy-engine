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
package io.sapl.test.coverage.api;

import io.sapl.test.coverage.api.model.PolicySetHit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

class CoverageCustomConfigTests {

    @Test
    void testSystemPropertyConfig_Reader(@TempDir Path tempDir) throws IOException {
        final var         coveragePath = tempDir.resolve("sapl-coverage");
        CoverageHitReader reader       = new CoverageHitAPIFile(coveragePath);
        Path              path         = coveragePath.resolve("hits").resolve("_policySetHits.txt");
        if (!Files.exists(path)) {
            final var parent = path.getParent();
            if (null != parent) {
                Files.createDirectories(parent);
            }
            Files.createFile(path);
        }
        Files.writeString(path, new PolicySetHit("set1") + System.lineSeparator(), StandardOpenOption.APPEND);

        // act
        List<PolicySetHit> resultPolicySetHits = reader.readPolicySetHits();

        // assert
        Assertions.assertThat(resultPolicySetHits).hasSize(1);
        Assertions.assertThat(resultPolicySetHits.get(0).getPolicySetId()).isEqualTo("set1");
    }

    @Test
    void testSystemPropertyConfig_Recorder(@TempDir Path tempDir) {
        final var           coveragePath = tempDir.resolve("sapl-coverage");
        CoverageHitRecorder recorder     = new CoverageHitAPIFile(coveragePath);
        // act
        recorder.createCoverageHitFiles();

        // assert
        Path path = coveragePath.resolve("hits").resolve("_policySetHits.txt");
        Assertions.assertThat(Files.exists(path)).isTrue();

    }

}
