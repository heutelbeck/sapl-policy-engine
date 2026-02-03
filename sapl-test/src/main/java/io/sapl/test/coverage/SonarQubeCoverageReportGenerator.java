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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import io.sapl.api.coverage.PolicyCoverageData;
import lombok.val;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates SonarQube generic test coverage XML from NDJSON coverage data.
 * <p>
 * Reads the coverage.ndjson file produced by test executions and transforms it
 * into SonarQube's generic test coverage format. Coverage data from multiple
 * test records is aggregated per policy file.
 * <p>
 * The generated XML follows the SonarQube generic test coverage format:
 *
 * <pre>{@code
 * <coverage version="1">
 *   <file path="policies/access-control.sapl">
 *     <lineToCover lineNumber="3" covered="true" branchesToCover=
"2" coveredBranches="2"/>
 *     <lineToCover lineNumber="5" covered="true" branchesToCover=
"2" coveredBranches="1"/>
 *   </file>
 * </coverage>
 * }</pre>
 *
 * @see <a href=
 * "https://docs.sonarqube.org/latest/analyzing-source-code/test-coverage/generic-test-data/">SonarQube
 * Generic Test Data</a>
 */
public class SonarQubeCoverageReportGenerator {

    private static final String ERROR_FAILED_TO_GENERATE_COVERAGE_XML       = "Failed to generate coverage XML";
    private static final String ERROR_FAILED_TO_GENERATE_EMPTY_COVERAGE_XML = "Failed to generate empty coverage XML";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final Path coverageDirectory;

    /**
     * Creates a generator for the specified coverage directory.
     *
     * @param coverageDirectory directory containing coverage.ndjson
     */
    public SonarQubeCoverageReportGenerator(Path coverageDirectory) {
        this.coverageDirectory = coverageDirectory;
    }

    /**
     * Generates SonarQube coverage XML from the NDJSON coverage data.
     *
     * @return the coverage XML as a string
     * @throws IOException if reading coverage data fails
     */
    public String generate() throws IOException {
        val coverageFile = coverageDirectory.resolve("coverage.ndjson");
        if (!Files.exists(coverageFile)) {
            return generateEmptyCoverage();
        }

        val aggregatedCoverage = aggregateCoverage(coverageFile);
        return generateXml(aggregatedCoverage);
    }

    /**
     * Generates SonarQube coverage XML and writes it to a file.
     *
     * @param outputPath the output file path
     * @throws IOException if reading or writing fails
     */
    public void generateToFile(Path outputPath) throws IOException {
        val xml    = generate();
        val parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(outputPath, xml);
    }

    /**
     * Creates a default generator using the standard coverage directory.
     *
     * @return a new generator for target/sapl-coverage
     */
    public static SonarQubeCoverageReportGenerator createDefault() {
        return new SonarQubeCoverageReportGenerator(Path.of("target", "sapl-coverage"));
    }

    private Map<String, PolicyCoverageData> aggregateCoverage(Path coverageFile) throws IOException {
        val aggregated = new HashMap<String, PolicyCoverageData>();

        for (val line : Files.readAllLines(coverageFile)) {
            if (line.isBlank()) {
                continue;
            }
            val coverageRecord = MAPPER.readTree(line);
            aggregatePoliciesFromRecord(coverageRecord, aggregated);
        }

        return aggregated;
    }

    private void aggregatePoliciesFromRecord(JsonNode coverageRecord, Map<String, PolicyCoverageData> aggregated) {
        val policies = coverageRecord.get("policies");
        if (policies == null || !policies.isArray()) {
            return;
        }

        for (val policy : policies) {
            aggregatePolicy(policy, aggregated);
        }
    }

    private void aggregatePolicy(JsonNode policy, Map<String, PolicyCoverageData> aggregated) {
        val documentName = getTextOrNull(policy, "documentName");
        if (documentName == null) {
            return;
        }

        val sourceHash = getIntOrZero(policy, "sourceHash");
        val uniqueKey  = documentName + "#" + sourceHash;
        val coverage   = aggregated.computeIfAbsent(uniqueKey,
                key -> createPolicyCoverageData(policy, documentName, sourceHash));

        aggregateTargetHits(policy, coverage);
        aggregateBranchHits(policy, coverage);
    }

    private PolicyCoverageData createPolicyCoverageData(JsonNode policy, String documentName, int sourceHash) {
        val documentType = getTextOrDefault(policy);
        val filePath     = getTextOrNull(policy, "filePath");
        val data         = new PolicyCoverageData(documentName, "", documentType);
        data.setSourceHash(sourceHash);
        if (filePath != null) {
            data.setFilePath(filePath);
        }
        return data;
    }

    private void aggregateTargetHits(JsonNode policy, PolicyCoverageData coverage) {
        val targetTrueHits  = getIntOrZero(policy, "targetTrueHits");
        val targetFalseHits = getIntOrZero(policy, "targetFalseHits");
        for (var i = 0; i < targetTrueHits; i++) {
            coverage.recordTargetHit(true);
        }
        for (var i = 0; i < targetFalseHits; i++) {
            coverage.recordTargetHit(false);
        }
    }

    private void aggregateBranchHits(JsonNode policy, PolicyCoverageData coverage) {
        val branches = policy.get("branches");
        if (branches == null || !branches.isArray()) {
            return;
        }

        for (val branch : branches) {
            aggregateBranch(branch, coverage);
        }
    }

    private void aggregateBranch(JsonNode branch, PolicyCoverageData coverage) {
        val statementId = getIntOrZero(branch, "statementId");
        val trueHits    = getIntOrZero(branch, "trueHits");
        val falseHits   = getIntOrZero(branch, "falseHits");

        // Support both new format (startLine/endLine) and legacy (line)
        val startLine = branch.has("startLine") ? getIntOrZero(branch, "startLine") : getIntOrZero(branch, "line");
        val endLine   = branch.has("endLine") ? getIntOrZero(branch, "endLine") : startLine;
        val startChar = getIntOrZero(branch, "startChar");
        val endChar   = getIntOrZero(branch, "endChar");

        for (var i = 0; i < trueHits; i++) {
            coverage.recordConditionHit(statementId, startLine, endLine, startChar, endChar, true);
        }
        for (var i = 0; i < falseHits; i++) {
            coverage.recordConditionHit(statementId, startLine, endLine, startChar, endChar, false);
        }
    }

    private String generateXml(Map<String, PolicyCoverageData> coverageByPolicy) throws IOException {
        val writer        = new StringWriter();
        val outputFactory = XMLOutputFactory.newInstance();

        try {
            val xml = outputFactory.createXMLStreamWriter(writer);
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeCharacters("\n");
            xml.writeStartElement("coverage");
            xml.writeAttribute("version", "1");
            xml.writeCharacters("\n");

            for (val coverage : coverageByPolicy.values()) {
                writeFileCoverage(xml, coverage);
            }

            xml.writeEndElement();
            xml.writeCharacters("\n");
            xml.writeEndDocument();
            xml.close();
        } catch (XMLStreamException e) {
            throw new IOException(ERROR_FAILED_TO_GENERATE_COVERAGE_XML, e);
        }

        return writer.toString();
    }

    private void writeFileCoverage(XMLStreamWriter xml, PolicyCoverageData coverage) throws XMLStreamException {
        val filePath = coverage.getFilePath();
        if (filePath == null || filePath.isBlank()) {
            // Skip policies without file path - SonarQube requires file paths
            return;
        }

        xml.writeCharacters("  ");
        xml.writeStartElement("file");
        xml.writeAttribute("path", filePath);
        xml.writeCharacters("\n");

        // Write target line coverage (line 1 for policy declaration)
        if (coverage.wasTargetEvaluated()) {
            writeLineCoverage(xml, 1, coverage.wasTargetMatched(), 0, 0);
        }

        // Write branch coverage for each condition
        for (val branch : coverage.getBranchHits()) {
            val line            = branch.line();
            val covered         = branch.isPartiallyCovered();
            val branchesToCover = branch.totalBranchCount();
            val coveredBranches = branch.coveredBranchCount();
            writeLineCoverage(xml, line, covered, branchesToCover, coveredBranches);
        }

        xml.writeCharacters("  ");
        xml.writeEndElement();
        xml.writeCharacters("\n");
    }

    private void writeLineCoverage(XMLStreamWriter xml, int lineNumber, boolean covered, int branchesToCover,
            int coveredBranches) throws XMLStreamException {
        xml.writeCharacters("    ");
        xml.writeEmptyElement("lineToCover");
        xml.writeAttribute("lineNumber", String.valueOf(lineNumber));
        xml.writeAttribute("covered", String.valueOf(covered));
        if (branchesToCover > 0) {
            xml.writeAttribute("branchesToCover", String.valueOf(branchesToCover));
            xml.writeAttribute("coveredBranches", String.valueOf(coveredBranches));
        }
        xml.writeCharacters("\n");
    }

    private String generateEmptyCoverage() throws IOException {
        val writer        = new StringWriter();
        val outputFactory = XMLOutputFactory.newInstance();

        try {
            val xml = outputFactory.createXMLStreamWriter(writer);
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeCharacters("\n");
            xml.writeStartElement("coverage");
            xml.writeAttribute("version", "1");
            xml.writeEndElement();
            xml.writeCharacters("\n");
            xml.writeEndDocument();
            xml.close();
        } catch (XMLStreamException e) {
            throw new IOException(ERROR_FAILED_TO_GENERATE_EMPTY_COVERAGE_XML, e);
        }

        return writer.toString();
    }

    private static String getTextOrNull(JsonNode node, String field) {
        val value = node.get(field);
        return value != null && value.isString() ? value.asString() : null;
    }

    private static String getTextOrDefault(JsonNode node) {
        val value = getTextOrNull(node, "documentType");
        return value != null ? value : "policy";
    }

    private static int getIntOrZero(JsonNode node, String field) {
        val value = node.get(field);
        return value != null && value.isNumber() ? value.asInt() : 0;
    }
}
