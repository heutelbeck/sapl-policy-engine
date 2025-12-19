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

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregate coverage record for a single test execution.
 * <p>
 * Collects coverage data from all policy evaluations during a test,
 * along with metadata for reporting and correlation.
 */
@Getter
public class TestCoverageRecord {

    private final String                          testIdentifier;
    private final Instant                         timestamp;
    private final Map<String, PolicyCoverageData> policyCoverageByName = new HashMap<>();
    private final Map<Decision, Integer>          decisionCounts       = new EnumMap<>(Decision.class);
    private int                                   evaluationCount;
    private int                                   errorCount;

    /**
     * Creates a new test coverage record.
     *
     * @param testIdentifier optional identifier for the test (e.g., "RequirementX >
     * ScenarioY")
     */
    public TestCoverageRecord(String testIdentifier) {
        this.testIdentifier = testIdentifier != null ? testIdentifier : "unnamed-test";
        this.timestamp      = Instant.now();
        for (val decision : Decision.values()) {
            decisionCounts.put(decision, 0);
        }
    }

    /**
     * Records coverage data for a policy evaluation.
     *
     * @param policyCoverage the coverage data from the evaluation
     */
    public void addPolicyCoverage(PolicyCoverageData policyCoverage) {
        policyCoverageByName.merge(policyCoverage.getDocumentName(), policyCoverage, (existing, incoming) -> {
            existing.merge(incoming);
            return existing;
        });
    }

    /**
     * Records the decision outcome from an evaluation.
     *
     * @param decision the authorization decision
     */
    public void recordDecision(Decision decision) {
        evaluationCount++;
        decisionCounts.merge(decision, 1, Integer::sum);
    }

    /**
     * Records that an error occurred during evaluation.
     */
    public void recordError() {
        errorCount++;
    }

    /**
     * Returns all policy coverage data as an immutable list.
     *
     * @return list of policy coverage records
     */
    public List<PolicyCoverageData> getPolicyCoverageList() {
        return List.copyOf(policyCoverageByName.values());
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
     * Calculates the total number of policies tracked.
     *
     * @return policy count
     */
    public int getPolicyCount() {
        return policyCoverageByName.size();
    }

    /**
     * Calculates the number of policies that were matched (target evaluated true).
     *
     * @return matched policy count
     */
    public int getMatchedPolicyCount() {
        return (int) policyCoverageByName.values().stream().filter(PolicyCoverageData::wasTargetMatched).count();
    }

    /**
     * Calculates overall branch coverage across all policies.
     *
     * @return branch coverage percentage (0.0 to 100.0)
     */
    public double getOverallBranchCoverage() {
        var totalCoveredBranches = 0;
        var totalBranches        = 0;
        for (val coverage : policyCoverageByName.values()) {
            for (val hit : coverage.getBranchHits()) {
                totalCoveredBranches += hit.coveredBranchCount();
                totalBranches        += hit.totalBranchCount();
            }
        }
        return totalBranches > 0 ? (totalCoveredBranches * 100.0) / totalBranches : 0.0;
    }

    /**
     * Merges another TestCoverageRecord into this one.
     *
     * @param other the record to merge
     */
    public void merge(TestCoverageRecord other) {
        for (val coverage : other.policyCoverageByName.values()) {
            addPolicyCoverage(coverage);
        }
        for (val entry : other.decisionCounts.entrySet()) {
            decisionCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        this.evaluationCount += other.evaluationCount;
        this.errorCount      += other.errorCount;
    }
}
