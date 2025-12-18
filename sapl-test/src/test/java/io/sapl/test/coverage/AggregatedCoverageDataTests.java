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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.api.pdp.Decision;
import lombok.val;

@DisplayName("AggregatedCoverageData tests")
class AggregatedCoverageDataTests {

    @Test
    @DisplayName("empty aggregation has zero counts")
    void whenEmpty_thenZeroCounts() {
        val aggregated = new AggregatedCoverageData();

        assertThat(aggregated.getTestCount()).isZero();
        assertThat(aggregated.getTotalEvaluations()).isZero();
        assertThat(aggregated.getTotalErrors()).isZero();
        assertThat(aggregated.getPolicyCount()).isZero();
    }

    @Test
    @DisplayName("merging single record")
    void whenMergeSingle_thenCountsMatch() {
        val aggregated = new AggregatedCoverageData();
        val coverageRecord = new TestCoverageRecord("innsmouth-test");
        coverageRecord.recordDecision(Decision.PERMIT);
        coverageRecord.recordError();

        aggregated.merge(coverageRecord);

        assertThat(aggregated.getTestCount()).isOne();
        assertThat(aggregated.getTotalEvaluations()).isOne();
        assertThat(aggregated.getTotalErrors()).isOne();
        assertThat(aggregated.getDecisionCount(Decision.PERMIT)).isOne();
    }

    @Test
    @DisplayName("merging multiple records sums counts")
    void whenMergeMultiple_thenCountsSummed() {
        val aggregated = new AggregatedCoverageData();

        val record1 = new TestCoverageRecord("dunwich-test-1");
        record1.recordDecision(Decision.PERMIT);
        record1.recordDecision(Decision.PERMIT);

        val record2 = new TestCoverageRecord("dunwich-test-2");
        record2.recordDecision(Decision.DENY);
        record2.recordError();

        aggregated.merge(record1);
        aggregated.merge(record2);

        assertThat(aggregated.getTestCount()).isEqualTo(2);
        assertThat(aggregated.getTotalEvaluations()).isEqualTo(3);
        assertThat(aggregated.getTotalErrors()).isOne();
        assertThat(aggregated.getDecisionCount(Decision.PERMIT)).isEqualTo(2);
        assertThat(aggregated.getDecisionCount(Decision.DENY)).isOne();
    }

    @Test
    @DisplayName("merging same policy combines coverage")
    void whenMergeSamePolicy_thenCoverageCombined() {
        val aggregated = new AggregatedCoverageData();

        val record1 = new TestCoverageRecord("ritual-test-1");
        val policy1 = new PolicyCoverageData("summoning-policy", null, "policy");
        policy1.recordTargetHit(true);
        policy1.recordConditionHit(0, 5, true);
        record1.addPolicyCoverage(policy1);
        record1.recordDecision(Decision.PERMIT);

        val record2 = new TestCoverageRecord("ritual-test-2");
        val policy2 = new PolicyCoverageData("summoning-policy", null, "policy");
        policy2.recordTargetHit(true);
        policy2.recordConditionHit(0, 5, false);
        record2.addPolicyCoverage(policy2);
        record2.recordDecision(Decision.DENY);

        aggregated.merge(record1);
        aggregated.merge(record2);

        assertThat(aggregated.getPolicyCount()).isOne();
        val policy = aggregated.getPolicyCoverageList().getFirst();
        assertThat(policy.getTargetTrueHits()).isEqualTo(2);
        assertThat(policy.getBranchHits().getFirst().isFullyCovered()).isTrue();
        assertThat(policy.getBranchCoveragePercent()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("merging different policies keeps separate")
    void whenMergeDifferentPolicies_thenKeptSeparate() {
        val aggregated = new AggregatedCoverageData();

        val coverageRecord = new TestCoverageRecord("multi-test");
        val policy1        = new PolicyCoverageData("deep-ones-access", null, "policy");
        policy1.recordTargetHit(true);
        val policy2 = new PolicyCoverageData("elder-things-access", null, "policy");
        policy2.recordTargetHit(false);
        coverageRecord.addPolicyCoverage(policy1);
        coverageRecord.addPolicyCoverage(policy2);
        coverageRecord.recordDecision(Decision.PERMIT);

        aggregated.merge(coverageRecord);

        assertThat(aggregated.getPolicyCount()).isEqualTo(2);
        assertThat(aggregated.getMatchedPolicyCount()).isOne();
    }

    @Test
    @DisplayName("overall branch coverage calculated correctly")
    void whenMultipleBranches_thenCorrectCoverage() {
        val aggregated = new AggregatedCoverageData();

        val coverageRecord = new TestCoverageRecord("coverage-test");
        val policy         = new PolicyCoverageData("arcane-policy", null, "policy");
        policy.recordTargetHit(true);
        policy.recordConditionHit(0, 3, true);
        policy.recordConditionHit(0, 3, false);
        policy.recordConditionHit(1, 5, true);
        coverageRecord.addPolicyCoverage(policy);
        coverageRecord.recordDecision(Decision.PERMIT);

        aggregated.merge(coverageRecord);

        assertThat(aggregated.getOverallBranchCoverage()).isEqualTo(75.0);
    }

    @Test
    @DisplayName("preserves file path through aggregation")
    void whenFilePathSet_thenPreservedInAggregation() {
        val aggregated = new AggregatedCoverageData();

        val coverageRecord = new TestCoverageRecord("path-test");
        val policy         = new PolicyCoverageData("shoggoth-policy", null, "policy");
        policy.setFilePath("policies/shoggoth-access.sapl");
        policy.recordTargetHit(true);
        coverageRecord.addPolicyCoverage(policy);
        coverageRecord.recordDecision(Decision.PERMIT);

        aggregated.merge(coverageRecord);

        val aggregatedPolicy = aggregated.getPolicyCoverageList().getFirst();
        assertThat(aggregatedPolicy.getFilePath()).isEqualTo("policies/shoggoth-access.sapl");
    }

    @Test
    @DisplayName("file path from later record fills in missing path")
    void whenFilePathAddedLater_thenFillsIn() {
        val aggregated = new AggregatedCoverageData();

        val record1 = new TestCoverageRecord("no-path-test");
        val policy1 = new PolicyCoverageData("nyarlathotep-policy", null, "policy");
        policy1.recordTargetHit(true);
        record1.addPolicyCoverage(policy1);
        record1.recordDecision(Decision.PERMIT);

        val record2 = new TestCoverageRecord("with-path-test");
        val policy2 = new PolicyCoverageData("nyarlathotep-policy", null, "policy");
        policy2.setFilePath("policies/nyarlathotep.sapl");
        policy2.recordTargetHit(false);
        record2.addPolicyCoverage(policy2);
        record2.recordDecision(Decision.DENY);

        aggregated.merge(record1);
        aggregated.merge(record2);

        val aggregatedPolicy = aggregated.getPolicyCoverageList().getFirst();
        assertThat(aggregatedPolicy.getFilePath()).isEqualTo("policies/nyarlathotep.sapl");
    }
}
