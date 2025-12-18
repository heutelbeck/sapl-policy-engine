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
package io.sapl.test.coverage;

import io.sapl.api.coverage.PolicyCoverageData;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.sapl.api.pdp.Decision;
import lombok.val;

@DisplayName("CoverageReader tests")
class CoverageReaderTests {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("roundtrip: write and read single record")
    void whenWriteAndRead_thenDataPreserved() throws IOException {
        val writer = new CoverageWriter(tempDir);
        val reader = new CoverageReader(tempDir);

        val original = createTestRecord("necronomicon-access-test");
        writer.write(original);

        val records = reader.readAllRecords();

        assertThat(records).hasSize(1);
        val read = records.getFirst();
        assertThat(read.getTestIdentifier()).isEqualTo("necronomicon-access-test");
        assertThat(read.getEvaluationCount()).isEqualTo(original.getEvaluationCount());
        assertThat(read.getDecisionCount(Decision.PERMIT)).isEqualTo(original.getDecisionCount(Decision.PERMIT));
        assertThat(read.getPolicyCount()).isEqualTo(original.getPolicyCount());
    }

    @Test
    @DisplayName("roundtrip: preserves policy coverage data")
    void whenWriteAndRead_thenPolicyCoveragePreserved() throws IOException {
        val writer = new CoverageWriter(tempDir);
        val reader = new CoverageReader(tempDir);

        val coverageRecord = new TestCoverageRecord("elder-ritual-test");
        val policy         = new PolicyCoverageData("elder-access-policy", null, "policy");
        policy.setFilePath("policies/elder-access.sapl");
        policy.recordTargetHit(true);
        policy.recordTargetHit(true);
        policy.recordConditionHit(0, 5, true);
        policy.recordConditionHit(0, 5, false);
        coverageRecord.addPolicyCoverage(policy);
        coverageRecord.recordDecision(Decision.PERMIT);

        writer.write(coverageRecord);
        val records = reader.readAllRecords();

        assertThat(records).hasSize(1);
        val readPolicy = records.getFirst().getPolicyCoverageList().getFirst();
        assertThat(readPolicy.getDocumentName()).isEqualTo("elder-access-policy");
        assertThat(readPolicy.getFilePath()).isEqualTo("policies/elder-access.sapl");
        assertThat(readPolicy.getTargetTrueHits()).isEqualTo(2);
        assertThat(readPolicy.getTargetFalseHits()).isZero();
        assertThat(readPolicy.getBranchHits()).hasSize(1);
        assertThat(readPolicy.getBranchHits().getFirst().isFullyCovered()).isTrue();
    }

    @Test
    @DisplayName("roundtrip: write multiple records and aggregate")
    void whenWriteMultipleAndAggregate_thenCombined() throws IOException {
        val writer = new CoverageWriter(tempDir);
        val reader = new CoverageReader(tempDir);

        val record1 = new TestCoverageRecord("cthulhu-test-1");
        val policy1 = new PolicyCoverageData("deep-ones-policy", null, "policy");
        policy1.recordTargetHit(true);
        policy1.recordConditionHit(0, 3, true);
        record1.addPolicyCoverage(policy1);
        record1.recordDecision(Decision.PERMIT);

        val record2 = new TestCoverageRecord("cthulhu-test-2");
        val policy2 = new PolicyCoverageData("deep-ones-policy", null, "policy");
        policy2.recordTargetHit(true);
        policy2.recordConditionHit(0, 3, false);
        record2.addPolicyCoverage(policy2);
        record2.recordDecision(Decision.DENY);

        writer.write(record1);
        writer.write(record2);

        val aggregated = reader.readAggregated();

        assertThat(aggregated.getTestCount()).isEqualTo(2);
        assertThat(aggregated.getTotalEvaluations()).isEqualTo(2);
        assertThat(aggregated.getDecisionCount(Decision.PERMIT)).isOne();
        assertThat(aggregated.getDecisionCount(Decision.DENY)).isOne();
        assertThat(aggregated.getPolicyCount()).isOne();

        val policy = aggregated.getPolicyCoverageList().getFirst();
        assertThat(policy.getTargetTrueHits()).isEqualTo(2);
        assertThat(policy.getBranchHits().getFirst().isFullyCovered()).isTrue();
        assertThat(policy.getBranchCoveragePercent()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("reads empty file returns empty list")
    void whenFileEmpty_thenEmptyList() throws IOException {
        val reader = new CoverageReader(tempDir);

        val records = reader.readAllRecords();

        assertThat(records).isEmpty();
    }

    @Test
    @DisplayName("roundtrip: preserves error count")
    void whenWriteWithErrors_thenErrorCountPreserved() throws IOException {
        val writer = new CoverageWriter(tempDir);
        val reader = new CoverageReader(tempDir);

        val coverageRecord = new TestCoverageRecord("error-test");
        coverageRecord.recordError();
        coverageRecord.recordError();
        writer.write(coverageRecord);

        val read = reader.readAllRecords().getFirst();

        assertThat(read.getErrorCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("roundtrip: preserves all decision types")
    void whenWriteAllDecisions_thenAllPreserved() throws IOException {
        val writer = new CoverageWriter(tempDir);
        val reader = new CoverageReader(tempDir);

        val coverageRecord = new TestCoverageRecord("decision-test");
        coverageRecord.recordDecision(Decision.PERMIT);
        coverageRecord.recordDecision(Decision.PERMIT);
        coverageRecord.recordDecision(Decision.DENY);
        coverageRecord.recordDecision(Decision.INDETERMINATE);
        coverageRecord.recordDecision(Decision.NOT_APPLICABLE);
        writer.write(coverageRecord);

        val read = reader.readAllRecords().getFirst();

        assertThat(read.getDecisionCount(Decision.PERMIT)).isEqualTo(2);
        assertThat(read.getDecisionCount(Decision.DENY)).isOne();
        assertThat(read.getDecisionCount(Decision.INDETERMINATE)).isOne();
        assertThat(read.getDecisionCount(Decision.NOT_APPLICABLE)).isOne();
    }

    @Test
    @DisplayName("coverageFileExists returns correct status")
    void whenFileWritten_thenExistsReturnsTrue() throws IOException {
        val writer = new CoverageWriter(tempDir);
        val reader = new CoverageReader(tempDir);

        assertThat(reader.coverageFileExists()).isFalse();

        writer.write(new TestCoverageRecord("exists-test"));

        assertThat(reader.coverageFileExists()).isTrue();
    }

    @Test
    @DisplayName("roundtrip: multiple policies with different branch coverage")
    void whenMultiplePolicies_thenAllPreserved() throws IOException {
        val writer = new CoverageWriter(tempDir);
        val reader = new CoverageReader(tempDir);

        val coverageRecord = new TestCoverageRecord("multi-policy-test");
        val policy1        = new PolicyCoverageData("miskatonic-access", null, "policy");
        policy1.setFilePath("policies/miskatonic.sapl");
        policy1.recordTargetHit(true);
        policy1.recordConditionHit(0, 3, true);
        policy1.recordConditionHit(0, 3, false);

        val policy2 = new PolicyCoverageData("arkham-access", null, "policy");
        policy2.setFilePath("policies/arkham.sapl");
        policy2.recordTargetHit(false);
        policy2.recordConditionHit(0, 5, true);

        coverageRecord.addPolicyCoverage(policy1);
        coverageRecord.addPolicyCoverage(policy2);
        coverageRecord.recordDecision(Decision.PERMIT);

        writer.write(coverageRecord);
        val read = reader.readAllRecords().getFirst();

        assertThat(read.getPolicyCoverageList()).hasSize(2);
        assertThat(read.getMatchedPolicyCount()).isOne();
    }

    private TestCoverageRecord createTestRecord(String identifier) {
        val coverageRecord = new TestCoverageRecord(identifier);
        val policy         = new PolicyCoverageData("test-policy", null, "policy");
        policy.recordTargetHit(true);
        policy.recordConditionHit(0, 3, true);
        coverageRecord.addPolicyCoverage(policy);
        coverageRecord.recordDecision(Decision.PERMIT);
        coverageRecord.recordDecision(Decision.DENY);
        return coverageRecord;
    }
}
