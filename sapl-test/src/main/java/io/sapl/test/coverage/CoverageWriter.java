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

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.json.JsonMapper;
import io.sapl.api.coverage.PolicyCoverageData;
import io.sapl.api.pdp.Decision;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes coverage data to disk in NDJSON (Newline Delimited JSON) format.
 * <p>
 * All test executions append to a single shared coverage file. Each record is
 * written as a single line of JSON to support concurrent appends from parallel
 * test execution.
 */
@Slf4j
public class CoverageWriter {

    private static final String     COVERAGE_FILENAME             = "coverage.ndjson";
    private static final JsonMapper MAPPER                        = createJsonMapper();
    private static final String     WARN_FAILED_TO_WRITE_COVERAGE = "Failed to write coverage data: {}";

    private final Path outputDirectory;

    /**
     * Creates a writer for the specified output directory.
     *
     * @param outputDirectory the directory for coverage files (typically
     * target/sapl-coverage)
     */
    public CoverageWriter(Path outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    /**
     * Writes a test coverage record to disk.
     * <p>
     * Creates the output directory if it doesn't exist. Each record is written
     * as a single line of JSON to support concurrent appends.
     *
     * @param coverageRecord the coverage record to write
     * @throws IOException if writing fails
     */
    public void write(TestCoverageRecord coverageRecord) throws IOException {
        Files.createDirectories(outputDirectory);

        val json = MAPPER.writeValueAsString(toSerializableMap(coverageRecord));
        val file = getCoverageFilePath();

        Files.writeString(file, json + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * Writes a test coverage record, suppressing IOException.
     * <p>
     * Use this in contexts where IO errors should not fail the test.
     * Errors are logged to stderr.
     *
     * @param coverageRecord the coverage record to write
     * @return true if write succeeded, false otherwise
     */
    public boolean writeSilently(TestCoverageRecord coverageRecord) {
        try {
            write(coverageRecord);
            return true;
        } catch (IOException e) {
            log.warn(WARN_FAILED_TO_WRITE_COVERAGE, e.getMessage());
            return false;
        }
    }

    /**
     * Returns the path to the shared coverage file.
     *
     * @return the coverage file path
     */
    public Path getCoverageFilePath() {
        return outputDirectory.resolve(COVERAGE_FILENAME);
    }

    /**
     * Checks if the coverage file exists.
     *
     * @return true if coverage data has been written
     */
    public boolean coverageFileExists() {
        return Files.exists(getCoverageFilePath());
    }

    /**
     * Converts a TestCoverageRecord to a serializable map structure.
     */
    private Map<String, Object> toSerializableMap(TestCoverageRecord coverageRecord) {
        val map = new LinkedHashMap<String, Object>();
        map.put("testIdentifier", coverageRecord.getTestIdentifier());
        map.put("timestamp", coverageRecord.getTimestamp().toString());
        map.put("evaluationCount", coverageRecord.getEvaluationCount());
        map.put("errorCount", coverageRecord.getErrorCount());

        // Decision counts
        val decisions = new LinkedHashMap<String, Integer>();
        for (val decision : Decision.values()) {
            decisions.put(decision.name(), coverageRecord.getDecisionCount(decision));
        }
        map.put("decisions", decisions);

        // Metrics
        val metrics = new LinkedHashMap<String, Object>();
        metrics.put("policyCount", coverageRecord.getPolicyCount());
        metrics.put("matchedPolicyCount", coverageRecord.getMatchedPolicyCount());
        metrics.put("branchCoveragePercent", round(coverageRecord.getOverallBranchCoverage()));
        map.put("metrics", metrics);

        // Policy coverage details
        val policies = new ArrayList<Map<String, Object>>();
        for (val coverage : coverageRecord.getPolicyCoverageList()) {
            policies.add(toSerializableMap(coverage));
        }
        map.put("policies", policies);

        return map;
    }

    /**
     * Converts a PolicyCoverageData to a serializable map structure.
     */
    private Map<String, Object> toSerializableMap(PolicyCoverageData coverage) {
        val map = new LinkedHashMap<String, Object>();
        map.put("documentName", coverage.getDocumentName());
        map.put("documentType", coverage.getDocumentType());
        if (coverage.getFilePath() != null) {
            map.put("filePath", coverage.getFilePath());
        }
        map.put("targetTrueHits", coverage.getTargetTrueHits());
        map.put("targetFalseHits", coverage.getTargetFalseHits());
        map.put("branchCoveragePercent", round(coverage.getBranchCoveragePercent()));
        map.put("conditionCount", coverage.getConditionCount());
        map.put("fullyCoveredConditions", coverage.getFullyCoveredConditionCount());

        // Branch hits with full position data for distinguishing same-line conditions
        val branches = new ArrayList<Map<String, Object>>();
        for (val hit : coverage.getBranchHits()) {
            val branchMap = new LinkedHashMap<String, Object>();
            branchMap.put("statementId", hit.statementId());
            branchMap.put("startLine", hit.startLine());
            branchMap.put("endLine", hit.endLine());
            branchMap.put("startChar", hit.startChar());
            branchMap.put("endChar", hit.endChar());
            branchMap.put("trueHits", hit.trueHits());
            branchMap.put("falseHits", hit.falseHits());
            branchMap.put("fullyCovered", hit.isFullyCoveredSemantic());
            branchMap.put("isSingleBranch", hit.isSingleBranch());
            branchMap.put("isPolicyOutcome", hit.isPolicyOutcome());
            branchMap.put("totalBranchCount", hit.totalBranchCount());
            branchMap.put("coveredBranchCount", hit.coveredBranchCount());
            branches.add(branchMap);
        }
        map.put("branches", branches);

        // Include source if available (compressed for storage)
        if (coverage.getDocumentSource() != null && !coverage.getDocumentSource().isEmpty()) {
            map.put("lineCount", coverage.getLineCount());
            // Only include source hash for deduplication, not full source
            map.put("sourceHash", coverage.getDocumentSource().hashCode());
        }

        return map;
    }

    private static double round(double value) {
        val scale = Math.pow(10, 2);
        return Math.round(value * scale) / scale;
    }

    private static JsonMapper createJsonMapper() {
        return JsonMapper.builder()
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL)).build();
    }

    /**
     * Creates a default coverage writer using target/sapl-coverage directory.
     *
     * @return a new CoverageWriter
     */
    public static CoverageWriter createDefault() {
        return new CoverageWriter(Path.of("target", "sapl-coverage"));
    }
}
