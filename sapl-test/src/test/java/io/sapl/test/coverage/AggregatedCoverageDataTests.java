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
        val aggregated     = new AggregatedCoverageData();
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

    @Test
    @DisplayName("policy set count and hit ratio")
    void whenPolicySets_thenCountAndRatioCalculated() {
        val aggregated = new AggregatedCoverageData();

        val coverageRecord = new TestCoverageRecord("set-test");
        val policySet      = new PolicyCoverageData("my-set", null, "set");
        policySet.recordTargetHit(true);
        val policy = new PolicyCoverageData("my-policy", null, "policy");
        policy.recordTargetHit(true);
        coverageRecord.addPolicyCoverage(policySet);
        coverageRecord.addPolicyCoverage(policy);
        coverageRecord.recordDecision(Decision.PERMIT);

        aggregated.merge(coverageRecord);

        assertThat(aggregated.getPolicySetCount()).isOne();
        assertThat(aggregated.getMatchedPolicySetCount()).isOne();
        assertThat(aggregated.getPolicySetHitRatio()).isEqualTo(100.0f);
    }

    @Test
    @DisplayName("standalone policy count and hit ratio")
    void whenStandalonePolicies_thenCountAndRatioCalculated() {
        val aggregated = new AggregatedCoverageData();

        val coverageRecord = new TestCoverageRecord("standalone-test");
        val policy1        = new PolicyCoverageData("policy1", null, "policy");
        policy1.recordTargetHit(true);
        policy1.recordPolicyOutcome(1, 1, 0, 0, true, false);
        val policy2 = new PolicyCoverageData("policy2", null, "policy");
        policy2.recordTargetHit(false);
        coverageRecord.addPolicyCoverage(policy1);
        coverageRecord.addPolicyCoverage(policy2);
        coverageRecord.recordDecision(Decision.PERMIT);

        aggregated.merge(coverageRecord);

        assertThat(aggregated.getStandalonePolicyCount()).isEqualTo(2);
        assertThat(aggregated.getMatchedStandalonePolicyCount()).isOne();
        assertThat(aggregated.getPolicyHitRatio()).isEqualTo(50.0f);
    }

    @Test
    @DisplayName("condition hit ratio with fully covered conditions")
    void whenConditionsFullyCovered_thenRatioIsHundred() {
        val aggregated = new AggregatedCoverageData();

        val coverageRecord = new TestCoverageRecord("condition-test");
        val policy         = new PolicyCoverageData("cond-policy", null, "policy");
        policy.recordTargetHit(true);
        policy.recordConditionHit(0, 5, true);
        policy.recordConditionHit(0, 5, false);
        coverageRecord.addPolicyCoverage(policy);
        coverageRecord.recordDecision(Decision.PERMIT);

        aggregated.merge(coverageRecord);

        assertThat(aggregated.getConditionHitRatio()).isEqualTo(100.0f);
    }

    @Test
    @DisplayName("condition hit ratio with partially covered conditions")
    void whenConditionsPartiallyCovered_thenRatioIsPartial() {
        val aggregated = new AggregatedCoverageData();

        val coverageRecord = new TestCoverageRecord("partial-test");
        val policy         = new PolicyCoverageData("partial-policy", null, "policy");
        policy.recordTargetHit(true);
        policy.recordConditionHit(0, 5, true);
        policy.recordConditionHit(1, 7, true);
        coverageRecord.addPolicyCoverage(policy);
        coverageRecord.recordDecision(Decision.PERMIT);

        aggregated.merge(coverageRecord);

        assertThat(aggregated.getConditionHitRatio()).isEqualTo(0.0f);
    }

    @Test
    @DisplayName("hit ratios return 100% when no items")
    void whenNoItems_thenRatioIsHundred() {
        val aggregated = new AggregatedCoverageData();

        assertThat(aggregated.getPolicySetHitRatio()).isEqualTo(100.0f);
        assertThat(aggregated.getPolicyHitRatio()).isEqualTo(100.0f);
        assertThat(aggregated.getConditionHitRatio()).isEqualTo(100.0f);
        assertThat(aggregated.getOverallBranchCoverage()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("decision count for all decision types")
    void whenMultipleDecisions_thenAllCounted() {
        val aggregated = new AggregatedCoverageData();

        val coverageRecord = new TestCoverageRecord("decision-test");
        coverageRecord.recordDecision(Decision.PERMIT);
        coverageRecord.recordDecision(Decision.DENY);
        coverageRecord.recordDecision(Decision.NOT_APPLICABLE);
        coverageRecord.recordDecision(Decision.INDETERMINATE);

        aggregated.merge(coverageRecord);

        assertThat(aggregated.getDecisionCount(Decision.PERMIT)).isOne();
        assertThat(aggregated.getDecisionCount(Decision.DENY)).isOne();
        assertThat(aggregated.getDecisionCount(Decision.NOT_APPLICABLE)).isOne();
        assertThat(aggregated.getDecisionCount(Decision.INDETERMINATE)).isOne();
    }
}
