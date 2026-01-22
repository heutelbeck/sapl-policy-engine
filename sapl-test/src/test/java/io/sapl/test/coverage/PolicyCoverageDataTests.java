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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.api.coverage.BranchHit;
import io.sapl.api.coverage.LineCoverageStatus;
import io.sapl.api.coverage.PolicyCoverageData;
import lombok.val;

@DisplayName("PolicyCoverageData tests")
class PolicyCoverageDataTests {

    private static final String CULTIST_POLICY = """
            policy "cultist-access"
            permit
                subject.role == "cultist";
                resource.artifact == "necronomicon";
            """;

    @Test
    @DisplayName("initializes with document metadata")
    void whenCreated_thenHasCorrectMetadata() {
        val coverage = new PolicyCoverageData("elder-policy", CULTIST_POLICY, "policy");

        assertThat(coverage.getDocumentName()).isEqualTo("elder-policy");
        assertThat(coverage.getDocumentSource()).isEqualTo(CULTIST_POLICY);
        assertThat(coverage.getDocumentType()).isEqualTo("policy");
        assertThat(coverage.getTargetTrueHits()).isZero();
        assertThat(coverage.getTargetFalseHits()).isZero();
        assertThat(coverage.getConditionCount()).isZero();
    }

    @Test
    @DisplayName("records target hit as true")
    void whenRecordTargetHitTrue_thenTrueHitsIncremented() {
        val coverage = new PolicyCoverageData("test", "", "policy");

        coverage.recordTargetHit(true);
        coverage.recordTargetHit(true);

        assertThat(coverage.getTargetTrueHits()).isEqualTo(2);
        assertThat(coverage.getTargetFalseHits()).isZero();
        assertThat(coverage.wasTargetMatched()).isTrue();
        assertThat(coverage.wasTargetEvaluated()).isTrue();
    }

    @Test
    @DisplayName("records target hit as false")
    void whenRecordTargetHitFalse_thenFalseHitsIncremented() {
        val coverage = new PolicyCoverageData("test", "", "policy");

        coverage.recordTargetHit(false);

        assertThat(coverage.getTargetTrueHits()).isZero();
        assertThat(coverage.getTargetFalseHits()).isOne();
        assertThat(coverage.wasTargetMatched()).isFalse();
        assertThat(coverage.wasTargetEvaluated()).isTrue();
    }

    @Test
    @DisplayName("records condition hits and accumulates branches")
    void whenRecordConditionHits_thenBranchesAccumulated() {
        val coverage = new PolicyCoverageData("test", "", "policy");

        coverage.recordConditionHit(0, 3, true);
        coverage.recordConditionHit(0, 3, false);
        coverage.recordConditionHit(1, 5, true);

        assertThat(coverage.getConditionCount()).isEqualTo(2);
        assertThat(coverage.getFullyCoveredConditionCount()).isOne();
        assertThat(coverage.getPartiallyCoveredConditionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("calculates branch coverage percentage")
    void whenCalculateBranchCoverage_thenCorrectPercentage() {
        val coverage = new PolicyCoverageData("test", "", "policy");

        coverage.recordConditionHit(0, 3, true);
        coverage.recordConditionHit(0, 3, false);
        coverage.recordConditionHit(1, 5, true);

        assertThat(coverage.getBranchCoveragePercent()).isEqualTo(75.0);
    }

    @Test
    @DisplayName("returns zero branch coverage when no conditions")
    void whenNoConditions_thenZeroBranchCoverage() {
        val coverage = new PolicyCoverageData("test", "", "policy");

        assertThat(coverage.getBranchCoveragePercent()).isZero();
    }

    @Test
    @DisplayName("merges coverage from same document")
    void whenMergeSameDocument_thenCombinesCoverage() {
        val coverage1 = new PolicyCoverageData("ritual-policy", "", "policy");
        coverage1.recordTargetHit(true);
        coverage1.recordConditionHit(0, 3, true);

        val coverage2 = new PolicyCoverageData("ritual-policy", "", "policy");
        coverage2.recordTargetHit(false);
        coverage2.recordConditionHit(0, 3, false);
        coverage2.recordConditionHit(1, 5, true);

        coverage1.merge(coverage2);

        assertThat(coverage1.getTargetTrueHits()).isOne();
        assertThat(coverage1.getTargetFalseHits()).isOne();
        assertThat(coverage1.getConditionCount()).isEqualTo(2);
        assertThat(coverage1.getFullyCoveredConditionCount()).isOne();
    }

    @Test
    @DisplayName("merge throws on different document names")
    void whenMergeDifferentDocuments_thenThrows() {
        val coverage1 = new PolicyCoverageData("cthulhu-policy", "", "policy");
        val coverage2 = new PolicyCoverageData("dagon-policy", "", "policy");

        assertThatThrownBy(() -> coverage1.merge(coverage2)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot merge coverage for different documents");
    }

    @Test
    @DisplayName("returns branch hits as immutable list")
    void whenGetBranchHits_thenReturnsImmutableList() {
        val coverage = new PolicyCoverageData("test", "", "policy");
        coverage.recordConditionHit(0, 3, true);
        coverage.recordConditionHit(1, 5, false);

        val hits = coverage.getBranchHits();

        assertThat(hits).hasSize(2);
        assertThatThrownBy(hits::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("calculates line count from source")
    void whenGetLineCount_thenCountsSourceLines() {
        val coverage = new PolicyCoverageData("test", CULTIST_POLICY, "policy");

        assertThat(coverage.getLineCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("returns zero line count for empty source")
    void whenEmptySource_thenZeroLineCount() {
        val coverage = new PolicyCoverageData("test", "", "policy");

        assertThat(coverage.getLineCount()).isZero();
    }

    @Test
    @DisplayName("returns zero line count for null source")
    void whenNullSource_thenZeroLineCount() {
        val coverage = new PolicyCoverageData("test", null, "policy");

        assertThat(coverage.getLineCount()).isZero();
    }

    @Test
    @DisplayName("returns covered lines set")
    void whenGetCoveredLines_thenReturnsCorrectLines() {
        val coverage = new PolicyCoverageData("test", "", "policy");
        coverage.recordTargetHit(true);
        coverage.recordConditionHit(0, 3, true);
        coverage.recordConditionHit(1, 5, false);

        val coveredLines = coverage.getCoveredLines();

        assertThat(coveredLines).containsExactlyInAnyOrder(1, 3, 5);
    }

    @Test
    @DisplayName("covered lines excludes target when not matched")
    void whenTargetNotMatched_thenLine1NotIncluded() {
        val coverage = new PolicyCoverageData("test", "", "policy");
        coverage.recordTargetHit(false);
        coverage.recordConditionHit(0, 3, true);

        val coveredLines = coverage.getCoveredLines();

        assertThat(coveredLines).containsExactly(3);
    }

    @Test
    @DisplayName("file path is null by default")
    void whenCreated_thenFilePathIsNull() {
        val coverage = new PolicyCoverageData("test", "", "policy");

        assertThat(coverage.getFilePath()).isNull();
    }

    @Test
    @DisplayName("file path can be set")
    void whenSetFilePath_thenFilePathIsStored() {
        val coverage = new PolicyCoverageData("arkham-access", "", "policy");

        coverage.setFilePath("policies/arkham/access-control.sapl");

        assertThat(coverage.getFilePath()).isEqualTo("policies/arkham/access-control.sapl");
    }

    @Test
    @DisplayName("conditions on same line with different character positions are tracked separately")
    void whenTwoConditionsOnSameLine_thenTrackedSeparately() {
        val coverage = new PolicyCoverageData("test", "", "policy");

        // Two conditions on line 5, at different character positions
        coverage.recordConditionHit(0, 5, 5, 0, 20, true);   // condition at chars 0-20
        coverage.recordConditionHit(1, 5, 5, 25, 50, false); // condition at chars 25-50

        // Should be 2 separate conditions, not merged
        assertThat(coverage.getConditionCount()).isEqualTo(2);
        assertThat(coverage.getBranchCoveragePercent()).isEqualTo(50.0); // 2 of 4 branches
    }

    @Test
    @DisplayName("coverage ratio is independent of code layout - same line vs separate lines")
    void whenSameConditionsDifferentLayout_thenSameCoverageRatio() {
        // Layout 1: conditions on separate lines
        val coverageSeparateLines = new PolicyCoverageData("test", "", "policy");
        coverageSeparateLines.recordConditionHit(0, 5, 5, 0, 20, true);   // line 5
        coverageSeparateLines.recordConditionHit(1, 6, 6, 0, 30, false);  // line 6

        // Layout 2: same conditions on same line
        val coverageSameLine = new PolicyCoverageData("test", "", "policy");
        coverageSameLine.recordConditionHit(0, 5, 5, 0, 20, true);   // line 5, chars 0-20
        coverageSameLine.recordConditionHit(1, 5, 5, 25, 50, false); // line 5, chars 25-50

        // Both should have same coverage ratio
        assertThat(coverageSeparateLines.getConditionCount()).isEqualTo(coverageSameLine.getConditionCount());
        assertThat(coverageSeparateLines.getBranchCoveragePercent())
                .isEqualTo(coverageSameLine.getBranchCoveragePercent());
    }

    @Test
    @DisplayName("line coverage shows aggregated branches for same-line conditions")
    void whenTwoConditionsOnSameLine_thenLineCoverageShowsAggregatedBranches() {
        val source   = "line1\nline2\nline3\nline4\nline5";
        val coverage = new PolicyCoverageData("test", source, "policy");

        // Condition 0: true hit only (1/2 branches)
        coverage.recordConditionHit(0, 5, 5, 0, 10, true);
        // Condition 1: both branches hit (2/2 branches)
        coverage.recordConditionHit(1, 5, 5, 15, 25, true);
        coverage.recordConditionHit(1, 5, 5, 15, 25, false);

        val lineCoverage = coverage.getLineCoverage();
        val line5Info    = lineCoverage.get(4); // 0-indexed

        // Line 5 should show 3 of 4 branches covered (1+2 covered, 2+2 total)
        assertThat(line5Info.coveredBranches()).isEqualTo(3);
        assertThat(line5Info.totalBranches()).isEqualTo(4);
        assertThat(line5Info.status()).isEqualTo(LineCoverageStatus.PARTIALLY_COVERED);
    }

    @Test
    @DisplayName("records single-branch policy outcome for policy without conditions")
    void whenRecordPolicyOutcomeWithoutConditions_thenSingleBranch() {
        val coverage = new PolicyCoverageData("simple-permit", "policy \"test\" permit", "policy");

        coverage.recordPolicyOutcome(1, 1, 0, 20, true, false);

        val hits = coverage.getBranchHits();
        assertThat(hits).hasSize(1);

        val hit = hits.getFirst();
        assertThat(hit.statementId()).isEqualTo(BranchHit.POLICY_SINGLE_BRANCH_ID);
        assertThat(hit.isSingleBranch()).isTrue();
        assertThat(hit.trueHits()).isOne();
        assertThat(hit.falseHits()).isZero();
        assertThat(hit.totalBranchCount()).isEqualTo(1);
        assertThat(hit.coveredBranchCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("records two-branch policy outcome for policy with conditions")
    void whenRecordPolicyOutcomeWithConditions_thenTwoBranch() {
        val coverage = new PolicyCoverageData("conditional-permit", "policy \"test\" permit x > 0;", "policy");

        coverage.recordPolicyOutcome(1, 1, 0, 30, true, true);

        val hits = coverage.getBranchHits();
        assertThat(hits).hasSize(1);

        val hit = hits.getFirst();
        assertThat(hit.statementId()).isEqualTo(BranchHit.POLICY_TWO_BRANCH_ID);
        assertThat(hit.isSingleBranch()).isFalse();
        assertThat(hit.trueHits()).isOne();
        assertThat(hit.falseHits()).isZero();
        assertThat(hit.totalBranchCount()).isEqualTo(2);
        assertThat(hit.coveredBranchCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("merges policy outcome hits for same policy")
    void whenRecordMultiplePolicyOutcomes_thenMerged() {
        val coverage = new PolicyCoverageData("test-policy", "policy \"test\" permit x > 0;", "policy");

        // First evaluation: entitlement returned
        coverage.recordPolicyOutcome(1, 1, 0, 30, true, true);
        // Second evaluation: NOT_APPLICABLE
        coverage.recordPolicyOutcome(1, 1, 0, 30, false, true);

        val hits = coverage.getBranchHits();
        assertThat(hits).hasSize(1);

        val hit = hits.getFirst();
        assertThat(hit.trueHits()).isOne();
        assertThat(hit.falseHits()).isOne();
        assertThat(hit.isFullyCovered()).isTrue();
        assertThat(hit.coveredBranchCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("single-branch policy is fully covered with one true hit")
    void whenSingleBranchPolicyHitOnce_thenFullyCovered() {
        val source   = "policy \"permit-all\" permit";
        val coverage = new PolicyCoverageData("permit-all", source, "policy");

        coverage.recordPolicyOutcome(1, 1, 0, 25, true, false);

        // 100% coverage: 1 branch covered of 1 total
        assertThat(coverage.getBranchCoveragePercent()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("two-branch policy needs both outcomes for full coverage")
    void whenTwoBranchPolicyOnlyOneOutcome_thenPartiallyCovered() {
        val source   = "policy \"conditional\" permit x;";
        val coverage = new PolicyCoverageData("conditional", source, "policy");

        coverage.recordPolicyOutcome(1, 1, 0, 35, true, true);

        // 50% coverage: 1 branch covered of 2 total
        assertThat(coverage.getBranchCoveragePercent()).isEqualTo(50.0);

        // Add NOT_APPLICABLE outcome
        coverage.recordPolicyOutcome(1, 1, 0, 35, false, true);

        // 100% coverage: 2 branches covered of 2 total
        assertThat(coverage.getBranchCoveragePercent()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("line coverage includes policy outcome with correct branch semantics")
    void whenLineCoverageWithPolicyOutcome_thenCorrectBranchCounts() {
        val source   = "line1\nline2";
        val coverage = new PolicyCoverageData("test", source, "policy");

        // Single-branch policy on line 1
        coverage.recordPolicyOutcome(1, 1, 0, 10, true, false);

        val lineCoverage = coverage.getLineCoverage();
        val line1Info    = lineCoverage.getFirst();

        // Single-branch: 1/1 = fully covered
        assertThat(line1Info.coveredBranches()).isEqualTo(1);
        assertThat(line1Info.totalBranches()).isEqualTo(1);
        assertThat(line1Info.status()).isEqualTo(LineCoverageStatus.FULLY_COVERED);
    }

    @Test
    @DisplayName("target expression uses single-branch semantics")
    void whenTargetExpressionHit_thenSingleBranchSemantics() {
        val source   = "line1\nline2";
        val coverage = new PolicyCoverageData("test", source, "policy");

        // Target on line 1 (uses negative statementId)
        coverage.recordTargetHit(true, 1, 1);

        val lineCoverage = coverage.getLineCoverage();
        val line1Info    = lineCoverage.getFirst();

        // Target is single-branch: hit = 1/1 covered
        assertThat(line1Info.coveredBranches()).isEqualTo(1);
        assertThat(line1Info.totalBranches()).isEqualTo(1);
    }
}
