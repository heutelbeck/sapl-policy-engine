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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.sapl.api.coverage.PolicyCoverageData;
import lombok.val;

@DisplayName("PolicySourcePopulator")
class PolicySourcePopulatorTests {

    @Nested
    @DisplayName("populateSources")
    class PopulateSourcesTests {

        @Test
        @DisplayName("populates source from candidate directory when source is missing")
        void whenSourceMissingThenPopulatesFromCandidateDir(@TempDir Path tempDir) throws Exception {
            val policySource = "policy \"p\" permit";
            Files.writeString(tempDir.resolve("test.sapl"), policySource);

            val policy = new PolicyCoverageData("test.sapl", null, "policy");
            policy.setFilePath("test.sapl");

            PolicySourcePopulator.populateSources(List.of(policy), List.of(tempDir));

            assertThat(policy.getDocumentSource()).isEqualTo(policySource);
        }

        @Test
        @DisplayName("skips population when source is already set")
        void whenSourceAlreadySetThenSkipsPopulation(@TempDir Path tempDir) throws Exception {
            val existingSource = "policy \"original\" permit";
            Files.writeString(tempDir.resolve("test.sapl"), "policy \"different\" permit");

            val policy = new PolicyCoverageData("test.sapl", existingSource, "policy");
            policy.setFilePath("test.sapl");

            PolicySourcePopulator.populateSources(List.of(policy), List.of(tempDir));

            assertThat(policy.getDocumentSource()).isEqualTo(existingSource);
        }

        @Test
        @DisplayName("leaves source null when file not found in any candidate directory")
        void whenFileNotFoundThenSourceRemainsNull(@TempDir Path tempDir) {
            val policy = new PolicyCoverageData("missing.sapl", null, "policy");
            policy.setFilePath("missing.sapl");

            PolicySourcePopulator.populateSources(List.of(policy), List.of(tempDir));

            assertThat(policy.getDocumentSource()).isNull();
        }

        @Test
        @DisplayName("skips population when file path is null")
        void whenFilePathNullThenSkipsPopulation() {
            val policy = new PolicyCoverageData("test.sapl", null, "policy");

            PolicySourcePopulator.populateSources(List.of(policy), List.of());

            assertThat(policy.getDocumentSource()).isNull();
        }

    }

}
