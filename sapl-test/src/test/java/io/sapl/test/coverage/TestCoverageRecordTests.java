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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.api.pdp.Decision;
import lombok.val;

@DisplayName("TestCoverageRecord tests")
class TestCoverageRecordTests {

    @Test
    @DisplayName("initializes with provided test identifier")
    void whenCreatedWithIdentifier_thenHasIdentifier() {
        val coverageRecord = new TestCoverageRecord("ritual-summoning-test");

        assertThat(coverageRecord.getTestIdentifier()).isEqualTo("ritual-summoning-test");
        assertThat(coverageRecord.getTimestamp()).isNotNull();
        assertThat(coverageRecord.getEvaluationCount()).isZero();
        assertThat(coverageRecord.getErrorCount()).isZero();
        assertThat(coverageRecord.getPolicyCount()).isZero();
    }

    @Test
    @DisplayName("uses default identifier when null provided")
    void whenCreatedWithNull_thenUsesDefault() {
        val coverageRecord = new TestCoverageRecord(null);

        assertThat(coverageRecord.getTestIdentifier()).isEqualTo("unnamed-test");
    }

    @Test
    @DisplayName("records decision and increments evaluation count")
    void whenRecordDecision_thenCountsIncremented() {
        val coverageRecord = new TestCoverageRecord("test");

        coverageRecord.recordDecision(Decision.PERMIT);
        coverageRecord.recordDecision(Decision.PERMIT);
        coverageRecord.recordDecision(Decision.DENY);

        assertThat(coverageRecord.getEvaluationCount()).isEqualTo(3);
        assertThat(coverageRecord.getDecisionCount(Decision.PERMIT)).isEqualTo(2);
        assertThat(coverageRecord.getDecisionCount(Decision.DENY)).isOne();
        assertThat(coverageRecord.getDecisionCount(Decision.INDETERMINATE)).isZero();
        assertThat(coverageRecord.getDecisionCount(Decision.NOT_APPLICABLE)).isZero();
    }

    @Test
    @DisplayName("records errors separately from evaluations")
    void whenRecordError_thenErrorCountIncremented() {
        val coverageRecord = new TestCoverageRecord("test");

        coverageRecord.recordError();
        coverageRecord.recordError();

        assertThat(coverageRecord.getErrorCount()).isEqualTo(2);
        assertThat(coverageRecord.getEvaluationCount()).isZero();
    }

    @Test
    @DisplayName("adds and merges policy coverage data")
    void whenAddPolicyCoverage_thenMergedByName() {
        val coverageRecord = new TestCoverageRecord("test");

        val coverage1 = new PolicyCoverageData("necronomicon-policy", "", "policy");
        coverage1.recordTargetHit(true);

        val coverage2 = new PolicyCoverageData("necronomicon-policy", "", "policy");
        coverage2.recordTargetHit(false);

        val coverage3 = new PolicyCoverageData("dagon-policy", "", "policy");
        coverage3.recordTargetHit(true);

        coverageRecord.addPolicyCoverage(coverage1);
        coverageRecord.addPolicyCoverage(coverage2);
        coverageRecord.addPolicyCoverage(coverage3);

        assertThat(coverageRecord.getPolicyCount()).isEqualTo(2);
        assertThat(coverageRecord.getMatchedPolicyCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("returns policy coverage as immutable list")
    void whenGetPolicyCoverageList_thenReturnsImmutableCopy() {
        val coverageRecord = new TestCoverageRecord("test");

        val coverage = new PolicyCoverageData("cthulhu-policy", "", "policy");
        coverageRecord.addPolicyCoverage(coverage);

        val list = coverageRecord.getPolicyCoverageList();

        assertThat(list).hasSize(1);
        assertThat(list.getFirst().getDocumentName()).isEqualTo("cthulhu-policy");
    }

    @Test
    @DisplayName("counts matched policies correctly")
    void whenPoliciesHaveMixedTargetResults_thenCountsMatchedCorrectly() {
        val coverageRecord = new TestCoverageRecord("test");

        val matched = new PolicyCoverageData("matched-policy", "", "policy");
        matched.recordTargetHit(true);

        val unmatched = new PolicyCoverageData("unmatched-policy", "", "policy");
        unmatched.recordTargetHit(false);

        coverageRecord.addPolicyCoverage(matched);
        coverageRecord.addPolicyCoverage(unmatched);

        assertThat(coverageRecord.getPolicyCount()).isEqualTo(2);
        assertThat(coverageRecord.getMatchedPolicyCount()).isOne();
    }

    @Test
    @DisplayName("calculates overall branch coverage across all policies")
    void whenMultiplePoliciesWithBranches_thenCalculatesOverallCoverage() {
        val coverageRecord = new TestCoverageRecord("test");

        val coverage1 = new PolicyCoverageData("policy1", "", "policy");
        coverage1.recordConditionHit(0, 3, true);
        coverage1.recordConditionHit(0, 3, false);

        val coverage2 = new PolicyCoverageData("policy2", "", "policy");
        coverage2.recordConditionHit(0, 5, true);

        coverageRecord.addPolicyCoverage(coverage1);
        coverageRecord.addPolicyCoverage(coverage2);

        assertThat(coverageRecord.getOverallBranchCoverage()).isEqualTo(75.0);
    }

    @Test
    @DisplayName("returns zero overall branch coverage when no conditions")
    void whenNoPoliciesWithConditions_thenZeroCoverage() {
        val coverageRecord = new TestCoverageRecord("test");

        val coverage = new PolicyCoverageData("empty-policy", "", "policy");
        coverageRecord.addPolicyCoverage(coverage);

        assertThat(coverageRecord.getOverallBranchCoverage()).isZero();
    }

    @Test
    @DisplayName("merges two records combining all data")
    void whenMergeTwoRecords_thenCombinesAllData() {
        val record1 = new TestCoverageRecord("test1");
        record1.recordDecision(Decision.PERMIT);
        record1.recordDecision(Decision.DENY);
        record1.recordError();

        val coverage1 = new PolicyCoverageData("shared-policy", "", "policy");
        coverage1.recordTargetHit(true);
        coverage1.recordConditionHit(0, 3, true);
        record1.addPolicyCoverage(coverage1);

        val record2 = new TestCoverageRecord("test2");
        record2.recordDecision(Decision.PERMIT);
        record2.recordDecision(Decision.INDETERMINATE);
        record2.recordError();
        record2.recordError();

        val coverage2 = new PolicyCoverageData("shared-policy", "", "policy");
        coverage2.recordTargetHit(false);
        coverage2.recordConditionHit(0, 3, false);
        record2.addPolicyCoverage(coverage2);

        val unique = new PolicyCoverageData("unique-policy", "", "policy");
        unique.recordTargetHit(true);
        record2.addPolicyCoverage(unique);

        record1.merge(record2);

        assertThat(record1.getEvaluationCount()).isEqualTo(4);
        assertThat(record1.getErrorCount()).isEqualTo(3);
        assertThat(record1.getDecisionCount(Decision.PERMIT)).isEqualTo(2);
        assertThat(record1.getDecisionCount(Decision.DENY)).isOne();
        assertThat(record1.getDecisionCount(Decision.INDETERMINATE)).isOne();
        assertThat(record1.getPolicyCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("merged records combine branch coverage correctly")
    void whenMergeRecords_thenBranchCoveragesCombined() {
        val record1   = new TestCoverageRecord("test1");
        val coverage1 = new PolicyCoverageData("policy", "", "policy");
        coverage1.recordConditionHit(0, 3, true);
        record1.addPolicyCoverage(coverage1);

        val record2   = new TestCoverageRecord("test2");
        val coverage2 = new PolicyCoverageData("policy", "", "policy");
        coverage2.recordConditionHit(0, 3, false);
        record2.addPolicyCoverage(coverage2);

        record1.merge(record2);

        assertThat(record1.getOverallBranchCoverage()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("preserves original test identifier after merge")
    void whenMerge_thenOriginalIdentifierPreserved() {
        val record1 = new TestCoverageRecord("arkham-test-suite");
        val record2 = new TestCoverageRecord("miskatonic-tests");

        record1.merge(record2);

        assertThat(record1.getTestIdentifier()).isEqualTo("arkham-test-suite");
    }
}
