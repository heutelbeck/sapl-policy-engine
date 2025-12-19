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
import io.sapl.api.pdp.Decision;
import lombok.Getter;
import lombok.val;

import java.util.*;

/**
 * Aggregated coverage data from multiple test executions.
 * <p>
 * Combines coverage from multiple TestCoverageRecord instances into a unified
 * view suitable for generating reports and validating coverage thresholds.
 */
@Getter
public class AggregatedCoverageData {

    private final Map<String, PolicyCoverageData> policyCoverageByName = new HashMap<>();
    private final Map<Decision, Integer>          decisionCounts       = new EnumMap<>(Decision.class);
    private int                                   totalEvaluations;
    private int                                   totalErrors;
    private int                                   testCount;

    /**
     * Creates empty aggregated coverage data.
     */
    public AggregatedCoverageData() {
        for (val decision : Decision.values()) {
            decisionCounts.put(decision, 0);
        }
    }

    /**
     * Merges a test coverage record into this aggregated data.
     *
     * @param coverageRecord the test coverage record to merge
     */
    public void merge(TestCoverageRecord coverageRecord) {
        testCount++;
        totalEvaluations += coverageRecord.getEvaluationCount();
        totalErrors      += coverageRecord.getErrorCount();

        for (val decision : Decision.values()) {
            decisionCounts.merge(decision, coverageRecord.getDecisionCount(decision), Integer::sum);
        }

        for (val coverage : coverageRecord.getPolicyCoverageList()) {
            val key = uniqueKeyFor(coverage);
            policyCoverageByName.merge(key, clonePolicyCoverage(coverage), (existing, incoming) -> {
                existing.merge(incoming);
                return existing;
            });
        }
    }

    /**
     * Returns all policy coverage data as a list.
     *
     * @return list of aggregated policy coverage
     */
    public List<PolicyCoverageData> getPolicyCoverageList() {
        return new ArrayList<>(policyCoverageByName.values());
    }

    /**
     * Calculates overall branch coverage percentage.
     *
     * @return branch coverage (0.0 to 100.0)
     */
    public double getOverallBranchCoverage() {
        var coveredBranches = 0;
        var totalBranches   = 0;
        for (val coverage : policyCoverageByName.values()) {
            for (val hit : coverage.getBranchHits()) {
                coveredBranches += hit.coveredBranchCount();
                totalBranches   += hit.totalBranchCount();
            }
        }
        return totalBranches > 0 ? (coveredBranches * 100.0) / totalBranches : 0.0;
    }

    /**
     * Returns the count of policies that were hit (target matched).
     *
     * @return matched policy count
     */
    public int getMatchedPolicyCount() {
        return (int) policyCoverageByName.values().stream().filter(PolicyCoverageData::wasTargetMatched).count();
    }

    /**
     * Returns the total policy count.
     *
     * @return policy count
     */
    public int getPolicyCount() {
        return policyCoverageByName.size();
    }

    /**
     * Returns the count of policy sets in the coverage data.
     *
     * @return policy set count
     */
    public int getPolicySetCount() {
        return (int) policyCoverageByName.values().stream().filter(coverage -> "set".equals(coverage.getDocumentType()))
                .count();
    }

    /**
     * Returns the count of policy sets that were matched.
     *
     * @return matched policy set count
     */
    public int getMatchedPolicySetCount() {
        return (int) policyCoverageByName.values().stream().filter(coverage -> "set".equals(coverage.getDocumentType()))
                .filter(PolicyCoverageData::wasTargetMatched).count();
    }

    /**
     * Returns the count of standalone policies (not in a set).
     *
     * @return standalone policy count
     */
    public int getStandalonePolicyCount() {
        return (int) policyCoverageByName.values().stream()
                .filter(coverage -> "policy".equals(coverage.getDocumentType())).count();
    }

    /**
     * Returns the count of standalone policies that were matched.
     *
     * @return matched standalone policy count
     */
    public int getMatchedStandalonePolicyCount() {
        return (int) policyCoverageByName.values().stream()
                .filter(coverage -> "policy".equals(coverage.getDocumentType()))
                .filter(PolicyCoverageData::wasTargetMatched).count();
    }

    /**
     * Calculates policy set hit ratio.
     *
     * @return ratio (0.0 to 100.0), or 100.0 if no policy sets
     */
    public float getPolicySetHitRatio() {
        val total = getPolicySetCount();
        if (total == 0) {
            return 100.0f;
        }
        return (getMatchedPolicySetCount() * 100.0f) / total;
    }

    /**
     * Calculates standalone policy hit ratio.
     *
     * @return ratio (0.0 to 100.0), or 100.0 if no policies
     */
    public float getPolicyHitRatio() {
        val total = getStandalonePolicyCount();
        if (total == 0) {
            return 100.0f;
        }
        return (getMatchedStandalonePolicyCount() * 100.0f) / total;
    }

    /**
     * Calculates condition/branch hit ratio.
     * A condition is fully covered when both true and false branches are hit.
     *
     * @return ratio (0.0 to 100.0), or 100.0 if no conditions
     */
    public float getConditionHitRatio() {
        var totalConditions   = 0;
        var coveredConditions = 0;
        for (val coverage : policyCoverageByName.values()) {
            for (val hit : coverage.getBranchHits()) {
                totalConditions++;
                if (hit.isFullyCovered()) {
                    coveredConditions++;
                }
            }
        }
        if (totalConditions == 0) {
            return 100.0f;
        }
        return (coveredConditions * 100.0f) / totalConditions;
    }

    /**
     * Returns the count for a specific decision type.
     *
     * @param decision the decision type
     * @return count of that decision
     */
    public int getDecisionCount(Decision decision) {
        return decisionCounts.getOrDefault(decision, 0);
    }

    /**
     * Creates a unique key for a policy coverage entry.
     * <p>
     * Uses source hash to distinguish policies with the same name but different
     * content.
     * This handles cases where integration tests and unit tests use policies with
     * the same document name but from different source files.
     */
    private String uniqueKeyFor(PolicyCoverageData coverage) {
        return coverage.getDocumentName() + "#" + coverage.getSourceHash();
    }

    /**
     * Creates a deep copy of PolicyCoverageData for safe aggregation.
     */
    private PolicyCoverageData clonePolicyCoverage(PolicyCoverageData original) {
        val clone = new PolicyCoverageData(original.getDocumentName(), original.getDocumentSource(),
                original.getDocumentType());
        clone.setFilePath(original.getFilePath());

        for (int i = 0; i < original.getTargetTrueHits(); i++) {
            clone.recordTargetHit(true);
        }
        for (int i = 0; i < original.getTargetFalseHits(); i++) {
            clone.recordTargetHit(false);
        }

        for (val hit : original.getBranchHits()) {
            for (int i = 0; i < hit.trueHits(); i++) {
                clone.recordConditionHit(hit.statementId(), hit.startLine(), hit.endLine(), hit.startChar(),
                        hit.endChar(), true);
            }
            for (int i = 0; i < hit.falseHits(); i++) {
                clone.recordConditionHit(hit.statementId(), hit.startLine(), hit.endLine(), hit.startChar(),
                        hit.endChar(), false);
            }
        }

        return clone;
    }
}
