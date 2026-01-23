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

import lombok.Getter;
import lombok.val;

import java.util.*;

/**
 * Collects coverage data for a single SAPL policy or policy set.
 * <p>
 * Tracks target expression hits and branch coverage for all condition
 * statements in the policy body. Supports incremental accumulation
 * from multiple test executions.
 */
@Getter
public class PolicyCoverageData {

    private static final int IDX_COVERED = 0;
    private static final int IDX_TOTAL   = 1;

    private final String               documentName;
    private String                     documentSource;
    private final String               documentType;
    private String                     filePath;
    private int                        sourceHash;
    private int                        targetTrueHits;
    private int                        targetFalseHits;
    private int                        targetStartLine;
    private int                        targetEndLine;
    private final Map<Long, BranchHit> branchHitsByPosition = new HashMap<>();

    /**
     * Creates coverage data for a policy document.
     *
     * @param documentName the policy or policy set name
     * @param documentSource the original SAPL metadata code (for HTML report
     * generation)
     * @param documentType "policy" or "set"
     */
    public PolicyCoverageData(String documentName, String documentSource, String documentType) {
        this.documentName   = documentName;
        this.documentSource = documentSource;
        this.documentType   = documentType;
        this.sourceHash     = documentSource != null ? documentSource.hashCode() : 0;
    }

    /**
     * Computes a unique position key for a condition based on its metadata
     * location.
     * The key combines line and character position to uniquely identify conditions
     * even when multiple appear on the same line.
     */
    private static long positionKey(int startLine, int startChar) {
        return startLine * 100_000L + startChar;
    }

    /**
     * Records a target expression evaluation result.
     *
     * @param matched true if the target matched (policy applies), false otherwise
     */
    public void recordTargetHit(boolean matched) {
        if (matched) {
            targetTrueHits++;
        } else {
            targetFalseHits++;
        }
    }

    /**
     * Records a target expression evaluation result with position data.
     * <p>
     * For policy sets with multiple nested policies, each target expression
     * is recorded as a branch hit keyed by its metadata position for unique
     * identification.
     *
     * @param matched true if the target matched (policy applies), false otherwise
     * @param startLine the 1-based start line of the target expression
     * @param endLine the 1-based end line of the target expression
     */
    public void recordTargetHit(boolean matched, int startLine, int endLine) {
        recordTargetHit(matched);
        // Store the primary target position for backwards compatibility
        if (this.targetStartLine == 0 && startLine > 0) {
            this.targetStartLine = startLine;
            this.targetEndLine   = endLine > 0 ? endLine : startLine;
        }
        // Record as branch hit keyed by position
        // Use negative statementId to distinguish targets from where-clause conditions
        if (startLine > 0) {
            val actualEndLine     = endLine > 0 ? endLine : startLine;
            val targetStatementId = -startLine;
            val key               = positionKey(startLine, 0);
            val newHit            = BranchHit.of(targetStatementId, startLine, actualEndLine, 0, 0, matched);
            branchHitsByPosition.merge(key, newHit, BranchHit::mergeHitCounts);
        }
    }

    /**
     * Records a condition evaluation from a ConditionHit (legacy, line-only
     * version).
     *
     * @param statementId the 0-based statement index
     * @param line the 1-based metadata line
     * @param result the evaluation result
     */
    public void recordConditionHit(int statementId, int line, boolean result) {
        val key    = positionKey(line, 0);
        val newHit = BranchHit.of(statementId, line, result);
        branchHitsByPosition.merge(key, newHit, BranchHit::mergeHitCounts);
    }

    /**
     * Records a condition evaluation with full position data.
     * <p>
     * This overload captures precise character positions for highlighting
     * multi-line expressions and distinguishing multiple conditions on the
     * same line.
     *
     * @param statementId the 0-based statement index
     * @param startLine the 1-based start line
     * @param endLine the 1-based end line
     * @param startChar character offset within start line (0-based)
     * @param endChar character offset within end line (0-based)
     * @param result the evaluation result
     */
    public void recordConditionHit(int statementId, int startLine, int endLine, int startChar, int endChar,
            boolean result) {
        val key    = positionKey(startLine, startChar);
        val newHit = BranchHit.of(statementId, startLine, endLine, startChar, endChar, result);
        branchHitsByPosition.merge(key, newHit, BranchHit::mergeHitCounts);
    }

    /**
     * Records a policy outcome for coverage tracking.
     * <p>
     * This tracks whether a policy returned its declared entitlement (PERMIT/DENY)
     * or returned NOT_APPLICABLE due to condition failures.
     * <p>
     * For policies without conditions (single-branch), only
     * {@code entitlementReturned=true} is meaningful.
     * For policies with conditions (two-branch), both outcomes should be tested.
     *
     * @param startLine the 1-based start line of the policy declaration
     * @param endLine the 1-based end line of the policy declaration
     * @param startChar character offset within start line (0-based)
     * @param endChar character offset within end line (0-based)
     * @param entitlementReturned true if policy returned its entitlement, false if
     * NOT_APPLICABLE
     * @param hasConditions true if policy has where-clause conditions (two-branch)
     */
    public void recordPolicyOutcome(int startLine, int endLine, int startChar, int endChar, boolean entitlementReturned,
            boolean hasConditions) {
        val key    = positionKey(startLine, startChar);
        val newHit = BranchHit.forPolicyOutcome(startLine, endLine, startChar, endChar, entitlementReturned,
                hasConditions);
        branchHitsByPosition.merge(key, newHit, BranchHit::mergeHitCounts);
    }

    /**
     * Merges another PolicyCoverageData into this one.
     * <p>
     * Combines target hits and branch hits. Both must represent the same document.
     *
     * @param other the coverage data to merge
     * @throws IllegalArgumentException if document names differ
     */
    public void merge(PolicyCoverageData other) {
        if (!this.documentName.equals(other.documentName)) {
            throw new IllegalArgumentException("Cannot merge coverage for different documents: '%s' vs '%s'"
                    .formatted(documentName, other.documentName));
        }
        this.targetTrueHits  += other.targetTrueHits;
        this.targetFalseHits += other.targetFalseHits;
        if (this.filePath == null && other.filePath != null) {
            this.filePath = other.filePath;
        }
        for (val entry : other.branchHitsByPosition.entrySet()) {
            branchHitsByPosition.merge(entry.getKey(), entry.getValue(), BranchHit::mergeHitCounts);
        }
    }

    /**
     * Sets the file path for this policy document.
     * <p>
     * The path should be relative to the project root for SonarQube compatibility.
     *
     * @param filePath the relative file path (e.g., "policies/access-control.sapl")
     */
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    /**
     * Sets the document metadata for this policy.
     * <p>
     * Used when the metadata is loaded from a file at report generation time,
     * rather than being captured during test execution.
     *
     * @param documentSource the SAPL policy metadata code
     */
    public void setDocumentSource(String documentSource) {
        this.documentSource = documentSource;
        if (this.sourceHash == 0 && documentSource != null) {
            this.sourceHash = documentSource.hashCode();
        }
    }

    /**
     * Sets the metadata hash for this policy document.
     * <p>
     * Used when deserializing from JSON where the full metadata is not available.
     *
     * @param sourceHash the hash code of the original document metadata
     */
    public void setSourceHash(int sourceHash) {
        this.sourceHash = sourceHash;
    }

    /**
     * Checks if the target expression was ever matched.
     * <p>
     * For policy sets, this tracks the "for" clause match.
     * For standalone policies, use {@link #wasActivated()} instead.
     *
     * @return true if targetTrueHits is greater than zero
     */
    public boolean wasTargetMatched() {
        return targetTrueHits > 0;
    }

    /**
     * Checks if the target expression was ever evaluated (matched or not).
     *
     * @return true if any target hits recorded
     */
    public boolean wasTargetEvaluated() {
        return targetTrueHits > 0 || targetFalseHits > 0;
    }

    /**
     * Checks if this policy was activated (returned its declared entitlement).
     * <p>
     * A policy is "activated" when its conditions pass and it returns PERMIT or
     * DENY (not NOT_APPLICABLE). This is the appropriate "hit" metric for
     * standalone policies that don't have explicit target expressions.
     * <p>
     * For policy sets, use {@link #wasTargetMatched()} to check the "for" clause.
     *
     * @return true if policy returned its entitlement at least once
     */
    public boolean wasActivated() {
        return branchHitsByPosition.values().stream().filter(BranchHit::isPolicyOutcome)
                .anyMatch(hit -> hit.trueHits() > 0);
    }

    /**
     * Returns the total number of condition statements tracked.
     *
     * @return count of distinct statements
     */
    public int getConditionCount() {
        return branchHitsByPosition.size();
    }

    /**
     * Returns the number of fully covered conditions (both branches hit).
     *
     * @return count of fully covered conditions
     */
    public int getFullyCoveredConditionCount() {
        return (int) branchHitsByPosition.values().stream().filter(BranchHit::isFullyCovered).count();
    }

    /**
     * Returns the number of partially covered conditions (at least one branch hit).
     *
     * @return count of partially covered conditions
     */
    public int getPartiallyCoveredConditionCount() {
        return (int) branchHitsByPosition.values().stream().filter(BranchHit::isPartiallyCovered).count();
    }

    /**
     * Calculates the branch coverage percentage.
     * <p>
     * Branch coverage = (covered branches / total branches) x 100
     * where each condition has 2 branches (true and false).
     * This calculation is independent of metadata code layout.
     *
     * @return coverage percentage (0.0 to 100.0), or 0.0 if no conditions
     */
    public double getBranchCoveragePercent() {
        if (branchHitsByPosition.isEmpty()) {
            return 0.0;
        }
        var coveredBranches = 0;
        var totalBranches   = 0;
        for (val hit : branchHitsByPosition.values()) {
            coveredBranches += hit.coveredBranchCount();
            totalBranches   += hit.totalBranchCount();
        }
        return totalBranches > 0 ? (coveredBranches * 100.0) / totalBranches : 0.0;
    }

    /**
     * Returns all branch hits as an immutable list.
     *
     * @return list of branch hits
     */
    public List<BranchHit> getBranchHits() {
        return List.copyOf(branchHitsByPosition.values());
    }

    /**
     * Returns the number of metadata lines in the policy.
     *
     * @return line count, or 0 if metadata not available
     */
    public int getLineCount() {
        if (documentSource == null || documentSource.isEmpty()) {
            return 0;
        }
        return (int) documentSource.lines().count();
    }

    /**
     * Returns lines covered (having at least one branch hit).
     *
     * @return set of 1-based line numbers
     */
    public Set<Integer> getCoveredLines() {
        val lines = new HashSet<Integer>();
        for (val hit : branchHitsByPosition.values()) {
            if (hit.isPartiallyCovered()) {
                // BinaryOperationCompiler all lines spanned by this condition
                for (int line = hit.startLine(); line <= hit.endLine(); line++) {
                    lines.add(line);
                }
            }
        }
        if (wasTargetMatched()) {
            // BinaryOperationCompiler all lines covered by the target expression
            val startLine = targetStartLine > 0 ? targetStartLine : 1;
            val endLine   = targetEndLine > 0 ? targetEndLine : startLine;
            for (int line = startLine; line <= endLine; line++) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * Computes line coverage information for all lines in this document.
     * <p>
     * Aggregates coverage from all statements that touch each line. When multiple
     * conditions appear on the same line, their branch counts are summed to show
     * the total coverage for that line.
     * <p>
     * Target expressions (negative statementId) use single-branch semantics: they
     * are either "hit" (fully covered) or "not hit" (not covered). This reflects
     * the architectural reality that we can only observe target matches, not
     * mismatches, since non-matching policies are filtered by the PRP before
     * reaching the PDP trace.
     * <p>
     * Where-clause conditions (non-negative statementId) use two-branch semantics:
     * each condition has true and false branches that can be independently covered.
     *
     * @return list of LineCoverageInfo for each line (1-indexed, list index 0 =
     * line 1)
     */
    public List<LineCoverageInfo> getLineCoverage() {
        val lineCount = getLineCount();
        if (lineCount == 0) {
            return List.of();
        }

        val branchesByLine = aggregateBranchCountsByLine();
        val targetStart    = targetStartLine > 0 ? targetStartLine : 1;
        val targetEnd      = targetEndLine > 0 ? targetEndLine : targetStart;

        val result = new ArrayList<LineCoverageInfo>(lineCount);
        for (int i = 1; i <= lineCount; i++) {
            result.add(createLineCoverageInfo(i, branchesByLine.get(i), targetStart, targetEnd));
        }
        return result;
    }

    private Map<Integer, int[]> aggregateBranchCountsByLine() {
        val branchesByLine = new HashMap<Integer, int[]>();
        for (val hit : branchHitsByPosition.values()) {
            for (int line = hit.startLine(); line <= hit.endLine(); line++) {
                branchesByLine.compute(line, (l, counts) -> {
                    val result = counts != null ? counts : new int[2];
                    result[IDX_COVERED] += hit.coveredBranchCount();
                    result[IDX_TOTAL]   += hit.totalBranchCount();
                    return result;
                });
            }
        }
        return branchesByLine;
    }

    private LineCoverageInfo createLineCoverageInfo(int line, int[] counts, int targetStart, int targetEnd) {
        if (counts != null) {
            return LineCoverageInfo.withBranches(line, counts[IDX_COVERED], counts[IDX_TOTAL]);
        }
        if (line >= targetStart && line <= targetEnd) {
            if (wasTargetMatched()) {
                return LineCoverageInfo.withBranches(line, 1, 1);
            }
            if (wasTargetEvaluated()) {
                return LineCoverageInfo.withBranches(line, 0, 1);
            }
        }
        return LineCoverageInfo.irrelevant(line);
    }
}
