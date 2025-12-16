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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.sapl.api.pdp.Decision;
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
public class CoverageWriter {

    private static final String       COVERAGE_FILENAME = "coverage.ndjson";
    private static final ObjectMapper MAPPER            = createObjectMapper();

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
     * @param record the coverage record to write
     * @throws IOException if writing fails
     */
    public void write(TestCoverageRecord record) throws IOException {
        Files.createDirectories(outputDirectory);

        val json = MAPPER.writeValueAsString(toSerializableMap(record));
        val file = getCoverageFilePath();

        Files.writeString(file, json + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * Writes a test coverage record, suppressing IOException.
     * <p>
     * Use this in contexts where IO errors should not fail the test.
     * Errors are logged to stderr.
     *
     * @param record the coverage record to write
     * @return true if write succeeded, false otherwise
     */
    public boolean writeSilently(TestCoverageRecord record) {
        try {
            write(record);
            return true;
        } catch (IOException e) {
            System.err.println("Failed to write coverage data: " + e.getMessage());
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
    private Map<String, Object> toSerializableMap(TestCoverageRecord record) {
        val map = new LinkedHashMap<String, Object>();
        map.put("testIdentifier", record.getTestIdentifier());
        map.put("timestamp", record.getTimestamp().toString());
        map.put("evaluationCount", record.getEvaluationCount());
        map.put("errorCount", record.getErrorCount());

        // Decision counts
        val decisions = new LinkedHashMap<String, Integer>();
        for (val decision : Decision.values()) {
            decisions.put(decision.name(), record.getDecisionCount(decision));
        }
        map.put("decisions", decisions);

        // Metrics
        val metrics = new LinkedHashMap<String, Object>();
        metrics.put("policyCount", record.getPolicyCount());
        metrics.put("matchedPolicyCount", record.getMatchedPolicyCount());
        metrics.put("branchCoveragePercent", round(record.getOverallBranchCoverage(), 2));
        map.put("metrics", metrics);

        // Policy coverage details
        val policies = new ArrayList<Map<String, Object>>();
        for (val coverage : record.getPolicyCoverageList()) {
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
        map.put("branchCoveragePercent", round(coverage.getBranchCoveragePercent(), 2));
        map.put("conditionCount", coverage.getConditionCount());
        map.put("fullyCoveredConditions", coverage.getFullyCoveredConditionCount());

        // Branch hits
        val branches = new ArrayList<Map<String, Object>>();
        for (val hit : coverage.getBranchHits()) {
            val branchMap = new LinkedHashMap<String, Object>();
            branchMap.put("statementId", hit.statementId());
            branchMap.put("line", hit.line());
            branchMap.put("trueHits", hit.trueHits());
            branchMap.put("falseHits", hit.falseHits());
            branchMap.put("fullyCovered", hit.isFullyCovered());
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

    private static double round(double value, int decimals) {
        val scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }

    private static ObjectMapper createObjectMapper() {
        val mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
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
