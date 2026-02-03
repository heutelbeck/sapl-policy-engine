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

import io.sapl.compiler.document.VoteWithCoverage;
import lombok.Getter;
import lombok.val;

import java.util.HashMap;
import java.util.Map;

/**
 * Accumulates coverage data during test execution.
 * <p>
 * Collects coverage from each authorization decision and maintains
 * a map of policy sources for correlation. Thread-safe for use in
 * reactive streams.
 */
public class CoverageAccumulator {

    @Getter
    private final String testIdentifier;

    @Getter
    private final Map<String, String> policySources   = new HashMap<>();
    private final Map<String, String> policyFilePaths = new HashMap<>();

    private final TestCoverageRecord coverageRecord;
    private final Object             lock = new Object();

    /**
     * Creates a new accumulator for a test.
     *
     * @param testIdentifier identifier for the test (e.g., class#method or DSL
     * scenario name)
     */
    public CoverageAccumulator(String testIdentifier) {
        this.testIdentifier = testIdentifier;
        this.coverageRecord = new TestCoverageRecord(testIdentifier);
    }

    /**
     * Registers policy source code for later correlation in HTML reports.
     *
     * @param policyName the policy or policy set name
     * @param source the SAPL source code
     */
    public void registerPolicySource(String policyName, String source) {
        synchronized (lock) {
            policySources.put(policyName, source);
        }
    }

    /**
     * Registers multiple policy sources.
     *
     * @param sources map of policy names to source code
     */
    public void registerPolicySources(Map<String, String> sources) {
        synchronized (lock) {
            policySources.putAll(sources);
        }
    }

    /**
     * Registers a file path for a policy.
     * <p>
     * File paths should be relative to the project root for SonarQube
     * compatibility.
     *
     * @param policyName the policy or policy set name
     * @param filePath the relative file path (e.g.,
     * "src/main/resources/policies/access.sapl")
     */
    public void registerPolicyFilePath(String policyName, String filePath) {
        synchronized (lock) {
            policyFilePaths.put(policyName, filePath);
        }
    }

    /**
     * Registers multiple policy file paths.
     *
     * @param filePaths map of policy names to file paths
     */
    public void registerPolicyFilePaths(Map<String, String> filePaths) {
        synchronized (lock) {
            policyFilePaths.putAll(filePaths);
        }
    }

    /**
     * Records coverage from a vote with coverage data.
     *
     * @param voteWithCoverage the vote containing coverage information
     */
    public void recordCoverage(VoteWithCoverage voteWithCoverage) {
        synchronized (lock) {
            // Extract and accumulate policy coverage
            val coverages = CoverageExtractor.extractCoverage(voteWithCoverage, policySources);
            for (val coverage : coverages) {
                // Set file path if registered
                val filePath = policyFilePaths.get(coverage.getDocumentName());
                if (filePath != null) {
                    coverage.setFilePath(filePath);
                }
                coverageRecord.addPolicyCoverage(coverage);
            }

            // Record decision outcome
            coverageRecord.recordDecision(voteWithCoverage.vote().authorizationDecision().decision());
        }
    }

    /**
     * Records that an error occurred during evaluation.
     */
    public void recordError() {
        synchronized (lock) {
            coverageRecord.recordError();
        }
    }

    /**
     * Returns the accumulated coverage record.
     * <p>
     * Should be called after all evaluations are complete.
     *
     * @return the accumulated test coverage record
     */
    public TestCoverageRecord getRecord() {
        synchronized (lock) {
            return coverageRecord;
        }
    }

    /**
     * Checks if any coverage data has been collected.
     *
     * @return true if at least one evaluation was recorded
     */
    public boolean hasCoverage() {
        synchronized (lock) {
            return coverageRecord.getEvaluationCount() > 0;
        }
    }

    /**
     * Returns summary statistics for logging.
     *
     * @return human-readable summary string
     */
    public String getSummary() {
        synchronized (lock) {
            return "Coverage[test=%s, policies=%d, evaluations=%d, branchCoverage=%.1f%%]".formatted(testIdentifier,
                    coverageRecord.getPolicyCount(), coverageRecord.getEvaluationCount(),
                    coverageRecord.getOverallBranchCoverage());
        }
    }
}
