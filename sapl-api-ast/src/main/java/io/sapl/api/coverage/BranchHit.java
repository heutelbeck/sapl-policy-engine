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
package io.sapl.api.coverage;

/**
 * Records branch coverage for a single condition statement in a policy.
 * <p>
 * Each condition can evaluate to true or false, creating two branches.
 * Full branch coverage requires both branches to be executed at least once.
 * <p>
 * Position data (startLine, endLine, startChar, endChar) enables precise
 * highlighting of multi-line expressions and multiple conditions on the same
 * line.
 * <p>
 * Special statementId conventions:
 * <ul>
 * <li>Non-negative: where-clause condition (0-based index)</li>
 * <li>Negative (except special constants): target expression</li>
 * <li>{@link #POLICY_SINGLE_BRANCH_ID}: policy outcome, single branch (no
 * conditions)</li>
 * <li>{@link #POLICY_TWO_BRANCH_ID}: policy outcome, two branches (has
 * conditions)</li>
 * </ul>
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
     * StatementId for policy outcome tracking when policy has no conditions.
     * Single-branch: policy either returns entitlement or is not applicable
     * (target didn't match).
     */
    public static final int POLICY_SINGLE_BRANCH_ID = Integer.MIN_VALUE;

    /**
     * StatementId for policy outcome tracking when policy has conditions.
     * Two-branch: policy can return entitlement (conditions pass) or
     * NOT_APPLICABLE (conditions fail).
     */
    public static final int POLICY_TWO_BRANCH_ID = Integer.MIN_VALUE + 1;

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
     * Creates a BranchHit for policy outcome tracking.
     * <p>
     * For policies without conditions (single-branch), only
     * {@code entitlementReturned=true} is meaningful since NOT_APPLICABLE only
     * happens when the target doesn't match.
     * <p>
     * For policies with conditions (two-branch), both outcomes are possible:
     * entitlement returned (conditions pass) or NOT_APPLICABLE (conditions fail).
     *
     * @param startLine the 1-based start line of the policy declaration
     * @param endLine the 1-based end line of the policy declaration
     * @param startChar character offset within start line (0-based)
     * @param endChar character offset within end line (0-based)
     * @param entitlementReturned true if policy returned its entitlement, false if
     * NOT_APPLICABLE
     * @param hasConditions true if policy has where-clause conditions (two-branch)
     * @return a new BranchHit for policy outcome tracking
     */
    public static BranchHit forPolicyOutcome(int startLine, int endLine, int startChar, int endChar,
            boolean entitlementReturned, boolean hasConditions) {
        int id = hasConditions ? POLICY_TWO_BRANCH_ID : POLICY_SINGLE_BRANCH_ID;
        return entitlementReturned ? new BranchHit(id, startLine, endLine, startChar, endChar, 1, 0)
                : new BranchHit(id, startLine, endLine, startChar, endChar, 0, 1);
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
     * Returns the number of branches covered.
     * <p>
     * For single-branch items, returns 0 or 1.
     * For two-branch items, returns 0, 1, or 2.
     *
     * @return count of covered branches
     */
    public int coveredBranchCount() {
        if (isSingleBranch()) {
            return trueHits > 0 ? 1 : 0;
        }
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
     * Total branches for this item.
     * <p>
     * Returns 1 for single-branch items (targets, policies without conditions).
     * Returns 2 for two-branch items (where-clause conditions, policies with
     * conditions).
     *
     * @return 1 for single-branch, 2 for two-branch
     */
    public int totalBranchCount() {
        return isSingleBranch() ? 1 : 2;
    }

    /**
     * Checks if this is a policy outcome tracking entry.
     *
     * @return true if this tracks policy-level outcomes
     */
    public boolean isPolicyOutcome() {
        return statementId == POLICY_SINGLE_BRANCH_ID || statementId == POLICY_TWO_BRANCH_ID;
    }

    /**
     * Checks if this is a target expression entry.
     * <p>
     * Target expressions use negative statementIds (except the special policy
     * outcome constants).
     *
     * @return true if this tracks a target expression
     */
    public boolean isTargetExpression() {
        return statementId < 0 && !isPolicyOutcome();
    }

    /**
     * Checks if this is a single-branch item.
     * <p>
     * Single-branch items only need one hit (trueHits &gt; 0) to be fully covered.
     * This includes target expressions and policies without conditions.
     *
     * @return true if this is single-branch
     */
    public boolean isSingleBranch() {
        return statementId == POLICY_SINGLE_BRANCH_ID || isTargetExpression();
    }

    /**
     * Checks if this item is fully covered, accounting for single vs two-branch
     * semantics.
     * <p>
     * Single-branch items are fully covered when trueHits &gt; 0.
     * Two-branch items require both trueHits &gt; 0 and falseHits &gt; 0.
     *
     * @return true if fully covered according to branch semantics
     */
    public boolean isFullyCoveredSemantic() {
        return isSingleBranch() ? trueHits > 0 : (trueHits > 0 && falseHits > 0);
    }
}
