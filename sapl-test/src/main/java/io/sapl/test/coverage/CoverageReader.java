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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.coverage.PolicyCoverageData;
import io.sapl.api.pdp.Decision;
import lombok.val;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads coverage data from NDJSON (Newline Delimited JSON) files.
 * <p>
 * Parses coverage data written by CoverageWriter and reconstructs
 * TestCoverageRecord objects or aggregates them into AggregatedCoverageData.
 */
public class CoverageReader {

    private static final String       COVERAGE_FILENAME = "coverage.ndjson";
    private static final ObjectMapper MAPPER            = new ObjectMapper();

    private final Path outputDirectory;

    /**
     * Creates a reader for the specified output directory.
     *
     * @param outputDirectory the directory containing coverage files
     */
    public CoverageReader(Path outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    /**
     * Reads all test coverage records from the NDJSON file.
     *
     * @return list of test coverage records
     * @throws IOException if reading or parsing fails
     */
    public List<TestCoverageRecord> readAllRecords() throws IOException {
        val file    = getCoverageFilePath();
        val records = new ArrayList<TestCoverageRecord>();

        if (!Files.exists(file)) {
            return records;
        }

        val lines = Files.readAllLines(file);
        for (val line : lines) {
            if (line.isBlank()) {
                continue;
            }
            records.add(parseRecord(line));
        }

        return records;
    }

    /**
     * Reads and aggregates all coverage data from the NDJSON file.
     *
     * @return aggregated coverage data
     * @throws IOException if reading or parsing fails
     */
    public AggregatedCoverageData readAggregated() throws IOException {
        val aggregated = new AggregatedCoverageData();
        for (val coverageRecord : readAllRecords()) {
            aggregated.merge(coverageRecord);
        }
        return aggregated;
    }

    /**
     * Returns the path to the coverage file.
     *
     * @return the coverage file path
     */
    public Path getCoverageFilePath() {
        return outputDirectory.resolve(COVERAGE_FILENAME);
    }

    /**
     * Checks if the coverage file exists.
     *
     * @return true if coverage data exists
     */
    public boolean coverageFileExists() {
        return Files.exists(getCoverageFilePath());
    }

    /**
     * Parses a single JSON line into a TestCoverageRecord.
     */
    private TestCoverageRecord parseRecord(String json) throws IOException {
        val node           = MAPPER.readTree(json);
        val testId         = node.path("testIdentifier").asText("unnamed-test");
        val coverageRecord = new TestCoverageRecord(testId);

        parseDecisions(node, coverageRecord);
        parsePolicies(node, coverageRecord);

        return coverageRecord;
    }

    /**
     * Parses decision counts and records them.
     */
    private void parseDecisions(JsonNode node, TestCoverageRecord coverageRecord) {
        val decisions   = node.path("decisions");
        val evaluations = node.path("evaluationCount").asInt(0);

        for (val decision : Decision.values()) {
            val count = decisions.path(decision.name()).asInt(0);
            for (int i = 0; i < count; i++) {
                coverageRecord.recordDecision(decision);
            }
        }

        // Adjust if evaluationCount differs from sum of decisions
        val recordedEvaluations = coverageRecord.getEvaluationCount();
        if (evaluations > recordedEvaluations) {
            val diff = evaluations - recordedEvaluations;
            for (int i = 0; i < diff; i++) {
                coverageRecord.recordDecision(Decision.INDETERMINATE);
            }
        }

        val errors = node.path("errorCount").asInt(0);
        for (int i = 0; i < errors; i++) {
            coverageRecord.recordError();
        }
    }

    /**
     * Parses policy coverage data.
     */
    private void parsePolicies(JsonNode node, TestCoverageRecord coverageRecord) {
        val policies = node.path("policies");
        if (!policies.isArray()) {
            return;
        }

        for (val policy : policies) {
            val coverage = parsePolicyCoverage(policy);
            coverageRecord.addPolicyCoverage(coverage);
        }
    }

    /**
     * Parses a single policy coverage entry.
     */
    private PolicyCoverageData parsePolicyCoverage(JsonNode node) {
        val documentName = node.path("documentName").asText("unknown");
        val documentType = node.path("documentType").asText("policy");
        val filePath     = node.has("filePath") ? node.path("filePath").asText() : null;

        val coverage = new PolicyCoverageData(documentName, null, documentType);
        if (filePath != null) {
            coverage.setFilePath(filePath);
        }
        if (node.has("sourceHash")) {
            coverage.setSourceHash(node.path("sourceHash").asInt(0));
        }

        val targetTrueHits  = node.path("targetTrueHits").asInt(0);
        val targetFalseHits = node.path("targetFalseHits").asInt(0);

        for (int i = 0; i < targetTrueHits; i++) {
            coverage.recordTargetHit(true);
        }
        for (int i = 0; i < targetFalseHits; i++) {
            coverage.recordTargetHit(false);
        }

        parseBranches(node, coverage);

        return coverage;
    }

    /**
     * Parses branch hit data with full position information.
     */
    private void parseBranches(JsonNode policyNode, PolicyCoverageData coverage) {
        val branches = policyNode.path("branches");
        if (!branches.isArray()) {
            return;
        }

        for (val branch : branches) {
            val statementId = branch.path("statementId").asInt(0);
            val trueHits    = branch.path("trueHits").asInt(0);
            val falseHits   = branch.path("falseHits").asInt(0);

            // Read full position data (new format) or fall back to line-only (legacy)
            val startLine = branch.has("startLine") ? branch.path("startLine").asInt(1) : branch.path("line").asInt(1);
            val endLine   = branch.has("endLine") ? branch.path("endLine").asInt(startLine) : startLine;
            val startChar = branch.path("startChar").asInt(0);
            val endChar   = branch.path("endChar").asInt(0);

            for (int i = 0; i < trueHits; i++) {
                coverage.recordConditionHit(statementId, startLine, endLine, startChar, endChar, true);
            }
            for (int i = 0; i < falseHits; i++) {
                coverage.recordConditionHit(statementId, startLine, endLine, startChar, endChar, false);
            }
        }
    }

    /**
     * Creates a default coverage reader using target/sapl-coverage directory.
     *
     * @return a new CoverageReader
     */
    public static CoverageReader createDefault() {
        return new CoverageReader(Path.of("target", "sapl-coverage"));
    }
}
