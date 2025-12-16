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
 * <p>
 * Position data (startLine, endLine, startChar, endChar) enables precise
 * highlighting of multi-line expressions and multiple conditions on the same
 * line.
 *
 * @param statementId the 0-based index of the statement in the policy body
 * @param startLine the 1-based source line where this condition starts
 * @param endLine the 1-based source line where this condition ends
 * @param startChar character offset within the start line (0-based)
 * @param endChar character offset within the end line (0-based)
 * @param trueHits count of evaluations resulting in true
 * @param falseHits count of evaluations resulting in false
 */
public record BranchHit(
        int statementId,
        int startLine,
        int endLine,
        int startChar,
        int endChar,
        int trueHits,
        int falseHits) {

    /**
     * Backwards-compatible constructor for legacy code that only has line number.
     *
     * @param statementId the statement index
     * @param line the source line
     * @param trueHits count of true evaluations
     * @param falseHits count of false evaluations
     */
    public BranchHit(int statementId, int line, int trueHits, int falseHits) {
        this(statementId, line, line, 0, 0, trueHits, falseHits);
    }

    /**
     * Returns the start line (alias for backwards compatibility).
     *
     * @return the start line number
     */
    public int line() {
        return startLine;
    }

    /**
     * Creates a new BranchHit with a single hit recorded (legacy, line-only
     * version).
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
     * Creates a new BranchHit with full position data and a single hit recorded.
     *
     * @param statementId the statement index
     * @param startLine the 1-based start line
     * @param endLine the 1-based end line
     * @param startChar character offset within start line (0-based)
     * @param endChar character offset within end line (0-based)
     * @param result the evaluation result
     * @return a new BranchHit with full position data
     */
    public static BranchHit of(int statementId, int startLine, int endLine, int startChar, int endChar,
            boolean result) {
        return result ? new BranchHit(statementId, startLine, endLine, startChar, endChar, 1, 0)
                : new BranchHit(statementId, startLine, endLine, startChar, endChar, 0, 1);
    }

    /**
     * Merges another BranchHit into this one by summing hit counts.
     * <p>
     * Both BranchHits must have the same statementId. Position data is preserved
     * from this BranchHit (the first one in the merge chain).
     *
     * @param other the BranchHit to merge
     * @return a new BranchHit with combined hit counts
     * @throws IllegalArgumentException if statementIds differ
     */
    public BranchHit merge(BranchHit other) {
        if (this.statementId != other.statementId) {
            throw new IllegalArgumentException("Cannot merge BranchHits with different statementId: %d vs %d"
                    .formatted(statementId, other.statementId));
        }
        return new BranchHit(statementId, startLine, endLine, startChar, endChar, trueHits + other.trueHits,
                falseHits + other.falseHits);
    }

    /**
     * Merges another BranchHit into this one by summing hit counts only.
     * <p>
     * This method does not require matching statementIds or positions, making it
     * suitable for merging hits that are keyed externally by position. Position
     * data is preserved from this BranchHit.
     *
     * @param other the BranchHit to merge
     * @return a new BranchHit with combined hit counts
     */
    public BranchHit mergeHitCounts(BranchHit other) {
        return new BranchHit(statementId, startLine, endLine, startChar, endChar, trueHits + other.trueHits,
                falseHits + other.falseHits);
    }

    /**
     * Merges another BranchHit into this one by start line only.
     * <p>
     * This method is used when merging coverage from different policies that
     * share the same source file (e.g., inner policies within a policy set).
     * The statementId is ignored since it's only unique within each policy.
     * Position data is preserved from the first hit.
     *
     * @param other the BranchHit to merge
     * @return a new BranchHit with combined hit counts
     * @throws IllegalArgumentException if lines differ
     */
    public BranchHit mergeByLine(BranchHit other) {
        if (this.startLine != other.startLine) {
            throw new IllegalArgumentException(
                    "Cannot merge BranchHits with different lines: %d vs %d".formatted(startLine, other.startLine));
        }
        return new BranchHit(statementId, startLine, endLine, startChar, endChar, trueHits + other.trueHits,
                falseHits + other.falseHits);
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
