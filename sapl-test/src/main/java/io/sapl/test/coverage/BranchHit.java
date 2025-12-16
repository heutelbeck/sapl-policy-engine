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

/**
 * Records branch coverage for a single condition statement in a policy.
 * <p>
 * Each condition can evaluate to true or false, creating two branches.
 * Full branch coverage requires both branches to be executed at least once.
 *
 * @param statementId the 0-based index of the statement in the policy body
 * @param line the 1-based source line number where this condition appears
 * @param trueHits count of evaluations resulting in true
 * @param falseHits count of evaluations resulting in false
 */
public record BranchHit(int statementId, int line, int trueHits, int falseHits) {

    /**
     * Creates a new BranchHit with a single hit recorded.
     *
     * @param statementId the statement index
     * @param line the source line
     * @param result the evaluation result (true increments trueHits, false
     * increments falseHits)
     * @return a new BranchHit with one hit recorded
     */
    public static BranchHit of(int statementId, int line, boolean result) {
        return result ? new BranchHit(statementId, line, 1, 0) : new BranchHit(statementId, line, 0, 1);
    }

    /**
     * Merges another BranchHit into this one by summing hit counts.
     * <p>
     * Both BranchHits must have the same statementId and line.
     *
     * @param other the BranchHit to merge
     * @return a new BranchHit with combined hit counts
     * @throws IllegalArgumentException if statementId or line differ
     */
    public BranchHit merge(BranchHit other) {
        if (this.statementId != other.statementId || this.line != other.line) {
            throw new IllegalArgumentException(
                    "Cannot merge BranchHits with different statementId or line: (%d, %d) vs (%d, %d)"
                            .formatted(statementId, line, other.statementId, other.line));
        }
        return new BranchHit(statementId, line, trueHits + other.trueHits, falseHits + other.falseHits);

    }

    /**
     * Merges another BranchHit into this one by line number only.
     * <p>
     * This method is used when merging coverage from different policies that
     * share the same source file (e.g., inner policies within a policy set).
     * The statementId is ignored since it's only unique within each policy.
     *
     * @param other the BranchHit to merge
     * @return a new BranchHit with combined hit counts
     * @throws IllegalArgumentException if lines differ
     */
    public BranchHit mergeByLine(BranchHit other) {
        if (this.line != other.line) {
            throw new IllegalArgumentException(
                    "Cannot merge BranchHits with different lines: %d vs %d".formatted(line, other.line));
        }
        return new BranchHit(statementId, line, trueHits + other.trueHits, falseHits + other.falseHits);
    }

    /**
     * Checks if this branch is fully covered (both true and false branches
     * executed).
     *
     * @return true if both trueHits and falseHits are greater than zero
     */
    public boolean isFullyCovered() {
        return trueHits > 0 && falseHits > 0;
    }

    /**
     * Checks if this branch is partially covered (at least one branch executed).
     *
     * @return true if either trueHits or falseHits is greater than zero
     */
    public boolean isPartiallyCovered() {
        return trueHits > 0 || falseHits > 0;
    }

    /**
     * Returns the number of branches covered (0, 1, or 2).
     *
     * @return count of covered branches
     */
    public int coveredBranchCount() {
        var count = 0;
        if (trueHits > 0) {
            count++;
        }
        if (falseHits > 0) {
            count++;
        }
        return count;
    }

    /**
     * Total branches for this condition (always 2: true and false).
     *
     * @return 2
     */
    public int totalBranchCount() {
        return 2;
    }
}
