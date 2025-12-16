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

import lombok.Getter;
import lombok.val;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects coverage data for a single SAPL policy or policy set.
 * <p>
 * Tracks target expression hits and branch coverage for all condition
 * statements in the policy body. Supports incremental accumulation
 * from multiple test executions.
 */
@Getter
public class PolicyCoverageData {

    private final String                  documentName;
    private String                        documentSource;
    private final String                  documentType;
    private String                        filePath;
    private int                           sourceHash;
    private int                           targetTrueHits;
    private int                           targetFalseHits;
    private final Map<Integer, BranchHit> branchHitsByLine = new HashMap<>();

    /**
     * Creates coverage data for a policy document.
     *
     * @param documentName the policy or policy set name
     * @param documentSource the original SAPL source code (for HTML report
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
     * Records a condition evaluation from a ConditionHit.
     *
     * @param statementId the 0-based statement index
     * @param line the 1-based source line
     * @param result the evaluation result
     */
    public void recordConditionHit(int statementId, int line, boolean result) {
        val newHit = BranchHit.of(statementId, line, result);
        branchHitsByLine.merge(line, newHit, BranchHit::mergeByLine);
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
        for (val entry : other.branchHitsByLine.entrySet()) {
            branchHitsByLine.merge(entry.getKey(), entry.getValue(), BranchHit::mergeByLine);
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
     * Sets the document source for this policy.
     * <p>
     * Used when the source is loaded from a file at report generation time,
     * rather than being captured during test execution.
     *
     * @param documentSource the SAPL policy source code
     */
    public void setDocumentSource(String documentSource) {
        this.documentSource = documentSource;
        if (this.sourceHash == 0 && documentSource != null) {
            this.sourceHash = documentSource.hashCode();
        }
    }

    /**
     * Sets the source hash for this policy document.
     * <p>
     * Used when deserializing from JSON where the full source is not available.
     *
     * @param sourceHash the hash code of the original document source
     */
    public void setSourceHash(int sourceHash) {
        this.sourceHash = sourceHash;
    }

    /**
     * Checks if the target expression was ever matched.
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
     * Returns the total number of condition statements tracked.
     *
     * @return count of distinct statements
     */
    public int getConditionCount() {
        return branchHitsByLine.size();
    }

    /**
     * Returns the number of fully covered conditions (both branches hit).
     *
     * @return count of fully covered conditions
     */
    public int getFullyCoveredConditionCount() {
        return (int) branchHitsByLine.values().stream().filter(BranchHit::isFullyCovered).count();
    }

    /**
     * Returns the number of partially covered conditions (at least one branch hit).
     *
     * @return count of partially covered conditions
     */
    public int getPartiallyCoveredConditionCount() {
        return (int) branchHitsByLine.values().stream().filter(BranchHit::isPartiallyCovered).count();
    }

    /**
     * Calculates the branch coverage percentage.
     * <p>
     * Branch coverage = (covered branches / total branches) × 100
     * where each condition has 2 branches (true and false).
     *
     * @return coverage percentage (0.0 to 100.0), or 0.0 if no conditions
     */
    public double getBranchCoveragePercent() {
        if (branchHitsByLine.isEmpty()) {
            return 0.0;
        }
        var coveredBranches = 0;
        var totalBranches   = 0;
        for (val hit : branchHitsByLine.values()) {
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
        return List.copyOf(branchHitsByLine.values());
    }

    /**
     * Returns the number of source lines in the policy.
     *
     * @return line count, or 0 if source not available
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
    public java.util.Set<Integer> getCoveredLines() {
        val lines = new java.util.HashSet<Integer>();
        for (val hit : branchHitsByLine.values()) {
            if (hit.isPartiallyCovered()) {
                lines.add(hit.line());
            }
        }
        if (wasTargetMatched()) {
            lines.add(1);
        }
        return lines;
    }

    /**
     * Computes line coverage information for all lines in this document.
     * <p>
     * For each line, determines coverage status based on branch hits recorded
     * for conditions on that line.
     *
     * @return list of LineCoverageInfo for each line (1-indexed, list index 0 =
     * line 1)
     */
    public List<LineCoverageInfo> getLineCoverage() {
        val lineCount = getLineCount();
        if (lineCount == 0) {
            return List.of();
        }

        val branchesByLine = new HashMap<Integer, int[]>();
        for (val hit : branchHitsByLine.values()) {
            branchesByLine.compute(hit.line(), (line, counts) -> {
                if (counts == null) {
                    counts = new int[2];
                }
                counts[0] += hit.coveredBranchCount();
                counts[1] += hit.totalBranchCount();
                return counts;
            });
        }

        val result = new java.util.ArrayList<LineCoverageInfo>(lineCount);
        for (int i = 1; i <= lineCount; i++) {
            val counts = branchesByLine.get(i);
            if (counts != null) {
                result.add(LineCoverageInfo.withBranches(i, counts[0], counts[1]));
            } else if (i == 1 && wasTargetMatched()) {
                result.add(LineCoverageInfo.withBranches(i, 1, 1));
            } else if (i == 1 && wasTargetEvaluated()) {
                result.add(LineCoverageInfo.withBranches(i, 0, 1));
            } else {
                result.add(LineCoverageInfo.irrelevant(i));
            }
        }
        return result;
    }
}
