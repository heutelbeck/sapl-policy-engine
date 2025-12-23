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

import io.sapl.api.coverage.BranchHit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import lombok.val;

@DisplayName("BranchHit record tests")
class BranchHitTests {

    @Test
    @DisplayName("creates hit with true result")
    void whenOfWithTrueResult_thenTrueHitsIsOne() {
        val hit = BranchHit.of(0, 5, true);

        assertThat(hit).satisfies(h -> {
            assertThat(h.statementId()).isZero();
            assertThat(h.line()).isEqualTo(5);
            assertThat(h.trueHits()).isOne();
            assertThat(h.falseHits()).isZero();
        });
    }

    @Test
    @DisplayName("creates hit with false result")
    void whenOfWithFalseResult_thenFalseHitsIsOne() {
        val hit = BranchHit.of(3, 10, false);

        assertThat(hit).satisfies(h -> {
            assertThat(h.statementId()).isEqualTo(3);
            assertThat(h.line()).isEqualTo(10);
            assertThat(h.trueHits()).isZero();
            assertThat(h.falseHits()).isOne();
        });
    }

    @Test
    @DisplayName("merges compatible hits by summing counts")
    void whenMergeCompatibleHits_thenCountsSummed() {
        val hit1   = new BranchHit(0, 5, 2, 1);
        val hit2   = new BranchHit(0, 5, 1, 3);
        val merged = hit1.merge(hit2);

        assertThat(merged).satisfies(h -> {
            assertThat(h.statementId()).isZero();
            assertThat(h.line()).isEqualTo(5);
            assertThat(h.trueHits()).isEqualTo(3);
            assertThat(h.falseHits()).isEqualTo(4);
        });
    }

    @Test
    @DisplayName("merge throws on different statementId")
    void whenMergeDifferentStatementId_thenThrows() {
        val hit1 = new BranchHit(0, 5, 1, 0);
        val hit2 = new BranchHit(1, 5, 0, 1);

        assertThatThrownBy(() -> hit1.merge(hit2)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot merge BranchHits");
    }

    @Test
    @DisplayName("mergeByLine throws on different line")
    void whenMergeByLineDifferentLine_thenThrows() {
        val hit1 = new BranchHit(0, 5, 1, 0);
        val hit2 = new BranchHit(0, 6, 0, 1);

        assertThatThrownBy(() -> hit1.mergeByLine(hit2)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot merge BranchHits");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("coverageStatusCases")
    @DisplayName("coverage status detection")
    void whenCheckingCoverageStatus_thenCorrectResult(String description, int trueHits, int falseHits,
            boolean expectedFully, boolean expectedPartially, int expectedCoveredCount) {
        val hit = new BranchHit(0, 1, trueHits, falseHits);

        assertThat(hit.isFullyCovered()).as("isFullyCovered").isEqualTo(expectedFully);
        assertThat(hit.isPartiallyCovered()).as("isPartiallyCovered").isEqualTo(expectedPartially);
        assertThat(hit.coveredBranchCount()).as("coveredBranchCount").isEqualTo(expectedCoveredCount);
    }

    static Stream<Arguments> coverageStatusCases() {
        return Stream.of(arguments("no coverage", 0, 0, false, false, 0),
                arguments("only true branch", 1, 0, false, true, 1),
                arguments("only false branch", 0, 1, false, true, 1), arguments("full coverage", 1, 1, true, true, 2),
                arguments("multiple true hits only", 5, 0, false, true, 1),
                arguments("multiple hits both branches", 3, 2, true, true, 2));
    }

    @Test
    @DisplayName("total branch count is 2 for conditions")
    void whenGetTotalBranchCountForCondition_thenTwo() {
        assertThat(new BranchHit(0, 1, 0, 0).totalBranchCount()).isEqualTo(2);
        assertThat(new BranchHit(0, 1, 5, 3).totalBranchCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("total branch count is 1 for target expressions")
    void whenGetTotalBranchCountForTarget_thenOne() {
        // Negative statementId (except special constants) = target expression
        assertThat(new BranchHit(-1, 1, 0, 0).totalBranchCount()).isEqualTo(1);
        assertThat(new BranchHit(-5, 1, 1, 0).totalBranchCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("total branch count is 1 for single-branch policy outcome")
    void whenGetTotalBranchCountForSingleBranchPolicy_thenOne() {
        assertThat(new BranchHit(BranchHit.POLICY_SINGLE_BRANCH_ID, 1, 0, 0).totalBranchCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("total branch count is 2 for two-branch policy outcome")
    void whenGetTotalBranchCountForTwoBranchPolicy_thenTwo() {
        assertThat(new BranchHit(BranchHit.POLICY_TWO_BRANCH_ID, 1, 0, 0).totalBranchCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("record equality works correctly")
    void whenComparingEqualHits_thenEqual() {
        val hit1 = new BranchHit(2, 7, 3, 4);
        val hit2 = new BranchHit(2, 7, 3, 4);

        assertThat(hit1).isEqualTo(hit2).hasSameHashCodeAs(hit2);
    }

    @Test
    @DisplayName("record inequality works correctly")
    void whenComparingDifferentHits_thenNotEqual() {
        val base = new BranchHit(2, 7, 3, 4);

        assertThat(base).isNotEqualTo(new BranchHit(1, 7, 3, 4)).isNotEqualTo(new BranchHit(2, 8, 3, 4))
                .isNotEqualTo(new BranchHit(2, 7, 2, 4)).isNotEqualTo(new BranchHit(2, 7, 3, 5));
    }

    // ========== Policy Outcome Tests ==========

    @Test
    @DisplayName("forPolicyOutcome creates single-branch hit for policy without conditions")
    void whenForPolicyOutcomeWithoutConditions_thenSingleBranch() {
        val hit = BranchHit.forPolicyOutcome(1, 3, 0, 10, true, false);

        assertThat(hit.statementId()).isEqualTo(BranchHit.POLICY_SINGLE_BRANCH_ID);
        assertThat(hit.startLine()).isEqualTo(1);
        assertThat(hit.endLine()).isEqualTo(3);
        assertThat(hit.startChar()).isZero();
        assertThat(hit.endChar()).isEqualTo(10);
        assertThat(hit.trueHits()).isOne();
        assertThat(hit.falseHits()).isZero();
        assertThat(hit.isPolicyOutcome()).isTrue();
        assertThat(hit.isSingleBranch()).isTrue();
        assertThat(hit.totalBranchCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("forPolicyOutcome creates two-branch hit for policy with conditions")
    void whenForPolicyOutcomeWithConditions_thenTwoBranch() {
        val hit = BranchHit.forPolicyOutcome(1, 5, 0, 20, false, true);

        assertThat(hit.statementId()).isEqualTo(BranchHit.POLICY_TWO_BRANCH_ID);
        assertThat(hit.trueHits()).isZero();
        assertThat(hit.falseHits()).isOne();
        assertThat(hit.isPolicyOutcome()).isTrue();
        assertThat(hit.isSingleBranch()).isFalse();
        assertThat(hit.totalBranchCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("isPolicyOutcome returns true only for policy outcome IDs")
    void whenCheckingIsPolicyOutcome_thenCorrectResult() {
        assertThat(new BranchHit(BranchHit.POLICY_SINGLE_BRANCH_ID, 1, 0, 0).isPolicyOutcome()).isTrue();
        assertThat(new BranchHit(BranchHit.POLICY_TWO_BRANCH_ID, 1, 0, 0).isPolicyOutcome()).isTrue();
        assertThat(new BranchHit(0, 1, 0, 0).isPolicyOutcome()).isFalse();
        assertThat(new BranchHit(-1, 1, 0, 0).isPolicyOutcome()).isFalse();
    }

    @Test
    @DisplayName("isTargetExpression returns true for negative IDs except policy constants")
    void whenCheckingIsTargetExpression_thenCorrectResult() {
        assertThat(new BranchHit(-1, 1, 0, 0).isTargetExpression()).isTrue();
        assertThat(new BranchHit(-100, 1, 0, 0).isTargetExpression()).isTrue();
        assertThat(new BranchHit(0, 1, 0, 0).isTargetExpression()).isFalse();
        assertThat(new BranchHit(5, 1, 0, 0).isTargetExpression()).isFalse();
        // Policy outcome constants are NOT target expressions
        assertThat(new BranchHit(BranchHit.POLICY_SINGLE_BRANCH_ID, 1, 0, 0).isTargetExpression()).isFalse();
        assertThat(new BranchHit(BranchHit.POLICY_TWO_BRANCH_ID, 1, 0, 0).isTargetExpression()).isFalse();
    }

    @Test
    @DisplayName("isSingleBranch returns true for targets and single-branch policies")
    void whenCheckingIsSingleBranch_thenCorrectResult() {
        // Target expressions are single-branch
        assertThat(new BranchHit(-1, 1, 0, 0).isSingleBranch()).isTrue();
        assertThat(new BranchHit(-50, 1, 0, 0).isSingleBranch()).isTrue();
        // Single-branch policy outcome is single-branch
        assertThat(new BranchHit(BranchHit.POLICY_SINGLE_BRANCH_ID, 1, 0, 0).isSingleBranch()).isTrue();
        // Conditions and two-branch policies are NOT single-branch
        assertThat(new BranchHit(0, 1, 0, 0).isSingleBranch()).isFalse();
        assertThat(new BranchHit(5, 1, 0, 0).isSingleBranch()).isFalse();
        assertThat(new BranchHit(BranchHit.POLICY_TWO_BRANCH_ID, 1, 0, 0).isSingleBranch()).isFalse();
    }

    @Test
    @DisplayName("coveredBranchCount respects single-branch semantics")
    void whenGettingCoveredBranchCountForSingleBranch_thenMaxOne() {
        // Single-branch: only trueHits matters, returns 0 or 1
        assertThat(new BranchHit(-1, 1, 0, 0).coveredBranchCount()).isZero();
        assertThat(new BranchHit(-1, 1, 1, 0).coveredBranchCount()).isEqualTo(1);
        assertThat(new BranchHit(-1, 1, 5, 0).coveredBranchCount()).isEqualTo(1);
        // Even with falseHits, single-branch only counts trueHits
        assertThat(new BranchHit(-1, 1, 0, 5).coveredBranchCount()).isZero();
        assertThat(new BranchHit(-1, 1, 1, 5).coveredBranchCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("isFullyCoveredSemantic respects branch type")
    void whenCheckingIsFullyCoveredSemantic_thenUsesCorrectSemantics() {
        // Two-branch condition: needs both true and false
        assertThat(new BranchHit(0, 1, 1, 0).isFullyCoveredSemantic()).isFalse();
        assertThat(new BranchHit(0, 1, 0, 1).isFullyCoveredSemantic()).isFalse();
        assertThat(new BranchHit(0, 1, 1, 1).isFullyCoveredSemantic()).isTrue();

        // Single-branch target: only needs trueHits
        assertThat(new BranchHit(-1, 1, 0, 0).isFullyCoveredSemantic()).isFalse();
        assertThat(new BranchHit(-1, 1, 1, 0).isFullyCoveredSemantic()).isTrue();

        // Single-branch policy: only needs trueHits
        assertThat(new BranchHit(BranchHit.POLICY_SINGLE_BRANCH_ID, 1, 1, 0).isFullyCoveredSemantic()).isTrue();

        // Two-branch policy: needs both
        assertThat(new BranchHit(BranchHit.POLICY_TWO_BRANCH_ID, 1, 1, 0).isFullyCoveredSemantic()).isFalse();
        assertThat(new BranchHit(BranchHit.POLICY_TWO_BRANCH_ID, 1, 1, 1).isFullyCoveredSemantic()).isTrue();
    }
}
