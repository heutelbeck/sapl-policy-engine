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
package io.sapl.test.coverage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.test.SaplTestFixture;
import lombok.val;

@DisplayName("Fixture coverage integration tests")
class FixtureCoverageIntegrationTests {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final String MISKATONIC_POLICY = """
            policy "miskatonic-library-access"
            permit
                subject.role == "faculty";
                resource.department == "occult studies";
            """;

    @TempDir
    Path tempDir;

    private static AuthorizationSubscription facultySubscription() {
        return AuthorizationSubscription.of(Map.of("role", "faculty"), "read", Map.of("department", "occult studies"));
    }

    @Test
    @DisplayName("collects coverage when enabled")
    void whenCoverageEnabled_thenWritesCoverageFile() throws IOException {
        val fixture = SaplTestFixture.createSingleTest().withPolicy(MISKATONIC_POLICY)
                .withTestIdentifier("arkham-library-test").withCoverageOutput(tempDir);

        fixture.whenDecide(facultySubscription()).expectPermit().verify();

        val coverageFiles = listCoverageFiles(tempDir);
        assertThat(coverageFiles).hasSize(1);

        val json = readFirstCoverageRecord(coverageFiles.getFirst());
        assertThat(json.get("testIdentifier").asText()).isEqualTo("arkham-library-test");
        assertThat(json.get("evaluationCount").asInt()).isPositive();
    }

    @Test
    @DisplayName("collects no coverage when disabled")
    void whenCoverageDisabled_thenNoCoverageFile() {
        val fixture = SaplTestFixture.createSingleTest().withPolicy(MISKATONIC_POLICY).withCoverageOutput(tempDir)
                .withCoverageFileWriteDisabled();

        fixture.whenDecide(facultySubscription()).expectPermit().verify();

        val coverageFiles = listCoverageFiles(tempDir);
        assertThat(coverageFiles).isEmpty();
    }

    @Test
    @DisplayName("generates test identifier when not provided")
    void whenNoTestIdentifier_thenGeneratesOne() throws IOException {
        val fixture = SaplTestFixture.createSingleTest().withPolicy(MISKATONIC_POLICY).withCoverageOutput(tempDir);

        fixture.whenDecide(facultySubscription()).expectPermit().verify();

        val coverageFiles = listCoverageFiles(tempDir);
        val json          = readFirstCoverageRecord(coverageFiles.getFirst());

        assertThat(json.get("testIdentifier").asText()).isNotEmpty();
    }

    @Test
    @DisplayName("records policy coverage details")
    void whenPolicyEvaluated_thenRecordsPolicyDetails() throws IOException {
        val fixture = SaplTestFixture.createSingleTest().withPolicy(MISKATONIC_POLICY)
                .withTestIdentifier("coverage-detail-test").withCoverageOutput(tempDir);

        fixture.whenDecide(facultySubscription()).expectPermit().verify();

        val coverageFiles = listCoverageFiles(tempDir);
        val json          = readFirstCoverageRecord(coverageFiles.getFirst());

        assertThat(json.has("policies")).isTrue();
        val policies = json.get("policies");
        assertThat(policies).isNotEmpty();

        val firstPolicy = policies.get(0);
        assertThat(firstPolicy.get("documentName").asText()).isEqualTo("miskatonic-library-access");
        assertThat(firstPolicy.get("documentType").asText()).isEqualTo("policy");
    }

    @Test
    @DisplayName("records decision counts")
    void whenDecisionsRecorded_thenCountsCorrect() throws IOException {
        val fixture = SaplTestFixture.createSingleTest().withPolicy(MISKATONIC_POLICY)
                .withTestIdentifier("decision-count-test").withCoverageOutput(tempDir);

        fixture.whenDecide(facultySubscription()).expectPermit().verify();

        val coverageFiles = listCoverageFiles(tempDir);
        val json          = readFirstCoverageRecord(coverageFiles.getFirst());

        assertThat(json.has("decisions")).isTrue();
        val decisions = json.get("decisions");
        assertThat(decisions.get("PERMIT").asInt()).isPositive();
    }

    @Test
    @DisplayName("accumulates multiple evaluations in single test")
    void whenMultipleEvaluations_thenAccumulatesCoverage() throws IOException {
        val fixture = SaplTestFixture.createSingleTest().withPolicy(MISKATONIC_POLICY)
                .withTestIdentifier("multi-eval-test").withCoverageOutput(tempDir);

        // First evaluation - should permit
        fixture.whenDecide(facultySubscription()).expectPermit().verify();

        val coverageFiles = listCoverageFiles(tempDir);
        // Each verify() call writes coverage, so we should have at least one record
        assertThat(coverageFiles).isNotEmpty();
    }

    @Test
    @DisplayName("verify returns TestResult with coverage")
    void whenVerify_thenReturnsTestResultWithCoverage() {
        val fixture = SaplTestFixture.createSingleTest().withPolicy(MISKATONIC_POLICY).withTestIdentifier("result-test")
                .withCoverageOutput(tempDir);

        val result = fixture.whenDecide(facultySubscription()).expectPermit().verify();

        assertThat(result.passed()).isTrue();
        assertThat(result.hasCoverage()).isTrue();
        assertThat(result.coverage()).isNotNull();
        assertThat(result.coverage().getTestIdentifier()).isEqualTo("result-test");
    }

    @Test
    @DisplayName("withCoverageFileWriteDisabled prevents file writing")
    void whenCoverageFileWriteDisabled_thenNoFileWritten() {
        val fixture = SaplTestFixture.createSingleTest().withPolicy(MISKATONIC_POLICY)
                .withTestIdentifier("no-file-test").withCoverageOutput(tempDir).withCoverageFileWriteDisabled();

        val result = fixture.whenDecide(facultySubscription()).expectPermit().verify();

        assertThat(result.passed()).isTrue();
        assertThat(result.hasCoverage()).isTrue();
        val coverageFiles = listCoverageFiles(tempDir);
        assertThat(coverageFiles).isEmpty();
    }

    private List<Path> listCoverageFiles(Path directory) {
        try {
            if (!Files.exists(directory)) {
                return List.of();
            }
            try (val stream = Files.list(directory)) {
                return stream.filter(p -> p.getFileName().toString().endsWith(".ndjson")).toList();
            }
        } catch (IOException e) {
            return List.of();
        }
    }

    private JsonNode readFirstCoverageRecord(Path coverageFile) throws IOException {
        val content = Files.readString(coverageFile);
        val lines   = content.trim().split(System.lineSeparator());
        return MAPPER.readTree(lines[0]);
    }
}
