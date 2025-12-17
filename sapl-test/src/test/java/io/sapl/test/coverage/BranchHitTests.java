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
    @DisplayName("total branch count is always 2")
    void whenGetTotalBranchCount_thenAlwaysTwo() {
        assertThat(new BranchHit(0, 1, 0, 0).totalBranchCount()).isEqualTo(2);
        assertThat(new BranchHit(0, 1, 5, 3).totalBranchCount()).isEqualTo(2);
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
}
