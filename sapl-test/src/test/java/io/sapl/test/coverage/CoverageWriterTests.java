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

import io.sapl.api.coverage.PolicyCoverageData;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.json.JsonMapper;

import io.sapl.api.pdp.Decision;
import lombok.val;

@DisplayName("CoverageWriter tests")
class CoverageWriterTests {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("creates output directory if not exists")
    void whenDirectoryNotExists_thenCreatesIt() throws IOException {
        val outputDir = tempDir.resolve("nested").resolve("coverage-dir");
        val writer    = new CoverageWriter(outputDir);

        writer.write(createSimpleRecord("test"));

        assertThat(outputDir).exists().isDirectory();
    }

    @Test
    @DisplayName("writes record as NDJSON format")
    void whenWriteRecord_thenNdjsonFormat() throws IOException {
        val writer         = new CoverageWriter(tempDir);
        val coverageRecord = createSimpleRecord("arkham-ritual-test");

        writer.write(coverageRecord);

        val content = Files.readString(writer.getCoverageFilePath());
        assertThat(content).endsWith(System.lineSeparator());
        assertThat(content.trim().split(System.lineSeparator())).hasSize(1);

        val json = MAPPER.readTree(content.trim());
        assertThat(json.get("testIdentifier").asText()).isEqualTo("arkham-ritual-test");
    }

    @Test
    @DisplayName("appends multiple records to same file")
    void whenWriteMultipleRecords_thenAppendsToFile() throws IOException {
        val writer = new CoverageWriter(tempDir);

        writer.write(createSimpleRecord("test-1"));
        writer.write(createSimpleRecord("test-2"));
        writer.write(createSimpleRecord("test-3"));

        val content = Files.readString(writer.getCoverageFilePath());
        val lines   = content.trim().split(System.lineSeparator());

        assertThat(lines).hasSize(3);
        assertThat(MAPPER.readTree(lines[0]).get("testIdentifier").asText()).isEqualTo("test-1");
        assertThat(MAPPER.readTree(lines[1]).get("testIdentifier").asText()).isEqualTo("test-2");
        assertThat(MAPPER.readTree(lines[2]).get("testIdentifier").asText()).isEqualTo("test-3");
    }

    @Test
    @DisplayName("multiple writers append to same shared file")
    void whenMultipleWriters_thenSameFile() throws IOException {
        val writer1 = new CoverageWriter(tempDir);
        val writer2 = new CoverageWriter(tempDir);

        writer1.write(createSimpleRecord("test-from-writer1"));
        writer2.write(createSimpleRecord("test-from-writer2"));

        assertThat(writer1.getCoverageFilePath()).isEqualTo(writer2.getCoverageFilePath());

        val content = Files.readString(writer1.getCoverageFilePath());
        val lines   = content.trim().split(System.lineSeparator());

        assertThat(lines).hasSize(2);
    }

    @Test
    @DisplayName("coverage file has correct name")
    void whenGetCoverageFilePath_thenCorrectName() {
        val writer = new CoverageWriter(tempDir);

        val path = writer.getCoverageFilePath();

        assertThat(path.getFileName().toString()).isEqualTo("coverage.ndjson");
    }

    @Test
    @DisplayName("serializes decision counts")
    void whenRecordHasDecisions_thenSerializesAll() throws IOException {
        val writer         = new CoverageWriter(tempDir);
        val coverageRecord = new TestCoverageRecord("test");
        coverageRecord.recordDecision(Decision.PERMIT);
        coverageRecord.recordDecision(Decision.PERMIT);
        coverageRecord.recordDecision(Decision.DENY);

        writer.write(coverageRecord);

        val json      = MAPPER.readTree(Files.readString(writer.getCoverageFilePath()).trim());
        val decisions = json.get("decisions");

        assertThat(decisions.get("PERMIT").asInt()).isEqualTo(2);
        assertThat(decisions.get("DENY").asInt()).isOne();
        assertThat(decisions.get("INDETERMINATE").asInt()).isZero();
        assertThat(decisions.get("NOT_APPLICABLE").asInt()).isZero();
    }

    @Test
    @DisplayName("serializes metrics")
    void whenRecordHasCoverage_thenSerializesMetrics() throws IOException {
        val writer         = new CoverageWriter(tempDir);
        val coverageRecord = new TestCoverageRecord("test");
        val coverage       = new PolicyCoverageData("elder-policy", "", "policy");
        coverage.recordTargetHit(true);
        coverage.recordConditionHit(0, 3, true);
        coverage.recordConditionHit(0, 3, false);
        coverageRecord.addPolicyCoverage(coverage);

        writer.write(coverageRecord);

        val json    = MAPPER.readTree(Files.readString(writer.getCoverageFilePath()).trim());
        val metrics = json.get("metrics");

        assertThat(metrics.get("policyCount").asInt()).isOne();
        assertThat(metrics.get("matchedPolicyCount").asInt()).isOne();
        assertThat(metrics.get("branchCoveragePercent").asDouble()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("serializes policy coverage details")
    void whenRecordHasPolicyCoverage_thenSerializesDetails() throws IOException {
        val writer         = new CoverageWriter(tempDir);
        val coverageRecord = new TestCoverageRecord("test");
        val coverage       = new PolicyCoverageData("necronomicon-policy", "policy \"necronomicon-policy\" permit",
                "policy");
        coverage.recordTargetHit(true);
        coverage.recordTargetHit(false);
        coverage.recordConditionHit(0, 5, true);
        coverageRecord.addPolicyCoverage(coverage);

        writer.write(coverageRecord);

        val json     = MAPPER.readTree(Files.readString(writer.getCoverageFilePath()).trim());
        val policies = json.get("policies");

        assertThat(policies).hasSize(1);
        val policy = policies.get(0);
        assertThat(policy.get("documentName").asText()).isEqualTo("necronomicon-policy");
        assertThat(policy.get("documentType").asText()).isEqualTo("policy");
        assertThat(policy.get("targetTrueHits").asInt()).isOne();
        assertThat(policy.get("targetFalseHits").asInt()).isOne();
        assertThat(policy.get("conditionCount").asInt()).isOne();
    }

    @Test
    @DisplayName("serializes branch hits")
    void whenPolicyHasBranchHits_thenSerializesBranches() throws IOException {
        val writer         = new CoverageWriter(tempDir);
        val coverageRecord = new TestCoverageRecord("test");
        val coverage       = new PolicyCoverageData("policy", "", "policy");
        coverage.recordConditionHit(0, 3, true);
        coverage.recordConditionHit(0, 3, false);
        coverage.recordConditionHit(1, 7, true);
        coverageRecord.addPolicyCoverage(coverage);

        writer.write(coverageRecord);

        val json     = MAPPER.readTree(Files.readString(writer.getCoverageFilePath()).trim());
        val branches = json.get("policies").get(0).get("branches");

        assertThat(branches).hasSize(2);
    }

    @Test
    @DisplayName("includes line count when source available")
    void whenSourceAvailable_thenIncludesLineCount() throws IOException {
        val writer         = new CoverageWriter(tempDir);
        val coverageRecord = new TestCoverageRecord("test");
        val source         = "policy \"test\" permit\nwhere\n    true;";
        val coverage       = new PolicyCoverageData("test", source, "policy");
        coverageRecord.addPolicyCoverage(coverage);

        writer.write(coverageRecord);

        val json   = MAPPER.readTree(Files.readString(writer.getCoverageFilePath()).trim());
        val policy = json.get("policies").get(0);

        assertThat(policy.get("lineCount").asInt()).isEqualTo(3);
        assertThat(policy.has("sourceHash")).isTrue();
    }

    @Test
    @DisplayName("writeSilently returns true on success")
    void whenWriteSilentlySucceeds_thenReturnsTrue() {
        val writer = new CoverageWriter(tempDir);

        val result = writer.writeSilently(createSimpleRecord("test"));

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("writeSilently returns false on failure")
    void whenWriteSilentlyFails_thenReturnsFalse() throws IOException {
        // Create a file where directory creation should fail
        val blockingFile = tempDir.resolve("blocking-file");
        Files.writeString(blockingFile, "blocking");
        val invalidPath = blockingFile.resolve("cannot-create-under-file");
        val writer      = new CoverageWriter(invalidPath);

        val result = writer.writeSilently(createSimpleRecord("test"));

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("coverageFileExists returns false before write")
    void whenNoWriteYet_thenCoverageFileDoesNotExist() {
        val writer = new CoverageWriter(tempDir);

        assertThat(writer.coverageFileExists()).isFalse();
    }

    @Test
    @DisplayName("coverageFileExists returns true after write")
    void whenWritten_thenCoverageFileExists() throws IOException {
        val writer = new CoverageWriter(tempDir);

        writer.write(createSimpleRecord("test"));

        assertThat(writer.coverageFileExists()).isTrue();
    }

    @Test
    @DisplayName("createDefault uses target/sapl-coverage path")
    void whenCreateDefault_thenUsesTargetSaplCoverage() {
        val writer = CoverageWriter.createDefault();

        val path = writer.getCoverageFilePath();

        assertThat(path).isEqualTo(Path.of("target", "sapl-coverage", "coverage.ndjson"));
    }

    @Test
    @DisplayName("rounds branch coverage to two decimals")
    void whenBranchCoverageHasManyDecimals_thenRoundsToTwo() throws IOException {
        val writer         = new CoverageWriter(tempDir);
        val coverageRecord = new TestCoverageRecord("test");
        val coverage       = new PolicyCoverageData("policy", "", "policy");
        // 3 conditions: 2 fully covered, 1 partially = 5/6 branches = 83.333...%
        coverage.recordConditionHit(0, 3, true);
        coverage.recordConditionHit(0, 3, false);
        coverage.recordConditionHit(1, 5, true);
        coverage.recordConditionHit(1, 5, false);
        coverage.recordConditionHit(2, 7, true);
        coverageRecord.addPolicyCoverage(coverage);

        writer.write(coverageRecord);

        val json           = MAPPER.readTree(Files.readString(writer.getCoverageFilePath()).trim());
        val coverageResult = json.get("policies").get(0).get("branchCoveragePercent").asDouble();

        assertThat(coverageResult).isEqualTo(83.33);
    }

    @Test
    @DisplayName("serializes timestamp as ISO string")
    void whenRecordHasTimestamp_thenSerializesAsIso() throws IOException {
        val writer         = new CoverageWriter(tempDir);
        val coverageRecord = createSimpleRecord("test");

        writer.write(coverageRecord);

        val json      = MAPPER.readTree(Files.readString(writer.getCoverageFilePath()).trim());
        val timestamp = json.get("timestamp").asText();

        assertThat(timestamp).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");
    }

    @Test
    @DisplayName("serializes file path when present")
    void whenPolicyHasFilePath_thenSerializesIt() throws IOException {
        val writer         = new CoverageWriter(tempDir);
        val coverageRecord = new TestCoverageRecord("test");
        val coverage       = new PolicyCoverageData("elder-policy", "", "policy");
        coverage.setFilePath("policies/elder/access.sapl");
        coverageRecord.addPolicyCoverage(coverage);

        writer.write(coverageRecord);

        val json   = MAPPER.readTree(Files.readString(writer.getCoverageFilePath()).trim());
        val policy = json.get("policies").get(0);

        assertThat(policy.get("filePath").asText()).isEqualTo("policies/elder/access.sapl");
    }

    @Test
    @DisplayName("omits file path when null")
    void whenPolicyHasNoFilePath_thenOmitsIt() throws IOException {
        val writer         = new CoverageWriter(tempDir);
        val coverageRecord = new TestCoverageRecord("test");
        val coverage       = new PolicyCoverageData("elder-policy", "", "policy");
        coverageRecord.addPolicyCoverage(coverage);

        writer.write(coverageRecord);

        val json   = MAPPER.readTree(Files.readString(writer.getCoverageFilePath()).trim());
        val policy = json.get("policies").get(0);

        assertThat(policy.has("filePath")).isFalse();
    }

    private TestCoverageRecord createSimpleRecord(String testIdentifier) {
        val coverageRecord = new TestCoverageRecord(testIdentifier);
        coverageRecord.recordDecision(Decision.PERMIT);
        return coverageRecord;
    }
}
