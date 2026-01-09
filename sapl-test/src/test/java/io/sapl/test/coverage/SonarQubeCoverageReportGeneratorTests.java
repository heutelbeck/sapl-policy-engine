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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import lombok.val;

@DisplayName("SonarQube coverage report generator tests")
class SonarQubeCoverageReportGeneratorTests {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("generates empty coverage when no coverage file exists")
    void whenNoCoverageFile_thenGeneratesEmptyCoverage() throws IOException {
        val generator = new SonarQubeCoverageReportGenerator(tempDir);

        val xml = generator.generate();

        assertThat(xml).contains("<coverage version=\"1\"").doesNotContain("<file");
    }

    @Test
    @DisplayName("generates coverage XML from NDJSON data")
    void whenCoverageDataExists_thenGeneratesXml() throws IOException {
        writeCoverageNdjson(
                """
                        {"testIdentifier":"test-1","policies":[{"documentName":"elder-access","documentType":"policy","filePath":"policies/elder.sapl","targetTrueHits":1,"targetFalseHits":0,"branches":[{"statementId":0,"line":5,"trueHits":1,"falseHits":1}]}]}
                        """);

        val generator = new SonarQubeCoverageReportGenerator(tempDir);
        val xml       = generator.generate();

        assertThat(xml).contains("<coverage version=\"1\"").contains("<file path=\"policies/elder.sapl\"")
                .contains("lineNumber=\"1\"").contains("lineNumber=\"5\"").contains("branchesToCover=\"2\"")
                .contains("coveredBranches=\"2\"");
    }

    @Test
    @DisplayName("aggregates coverage from multiple test records")
    void whenMultipleTestRecords_thenAggregatesCoverage() throws IOException {
        writeCoverageNdjson(
                """
                        {"testIdentifier":"test-1","policies":[{"documentName":"ritual-policy","documentType":"policy","filePath":"policies/ritual.sapl","targetTrueHits":1,"targetFalseHits":0,"branches":[{"statementId":0,"line":3,"trueHits":1,"falseHits":0}]}]}
                        {"testIdentifier":"test-2","policies":[{"documentName":"ritual-policy","documentType":"policy","filePath":"policies/ritual.sapl","targetTrueHits":0,"targetFalseHits":1,"branches":[{"statementId":0,"line":3,"trueHits":0,"falseHits":1}]}]}
                        """);

        val generator = new SonarQubeCoverageReportGenerator(tempDir);
        val xml       = generator.generate();

        assertThat(xml).contains("coveredBranches=\"2\"");
    }

    @Test
    @DisplayName("skips policies without file path")
    void whenPolicyHasNoFilePath_thenSkipsIt() throws IOException {
        writeCoverageNdjson(
                """
                        {"testIdentifier":"test-1","policies":[{"documentName":"no-path-policy","documentType":"policy","targetTrueHits":1,"targetFalseHits":0}]}
                        """);

        val generator = new SonarQubeCoverageReportGenerator(tempDir);
        val xml       = generator.generate();

        assertThat(xml).doesNotContain("<file").doesNotContain("no-path-policy");
    }

    @Test
    @DisplayName("handles multiple policies in single record")
    void whenMultiplePoliciesInRecord_thenGeneratesAllFiles() throws IOException {
        writeCoverageNdjson(
                """
                        {"testIdentifier":"test-1","policies":[{"documentName":"cthulhu-access","documentType":"policy","filePath":"policies/cthulhu.sapl","targetTrueHits":1,"targetFalseHits":0},{"documentName":"dagon-access","documentType":"policy","filePath":"policies/dagon.sapl","targetTrueHits":1,"targetFalseHits":0}]}
                        """);

        val generator = new SonarQubeCoverageReportGenerator(tempDir);
        val xml       = generator.generate();

        assertThat(xml).contains("policies/cthulhu.sapl").contains("policies/dagon.sapl");
    }

    @Test
    @DisplayName("writes coverage XML to file")
    void whenGenerateToFile_thenWritesXmlFile() throws IOException {
        writeCoverageNdjson(
                """
                        {"testIdentifier":"test-1","policies":[{"documentName":"test","documentType":"policy","filePath":"test.sapl","targetTrueHits":1,"targetFalseHits":0}]}
                        """);

        val outputPath = tempDir.resolve("output").resolve("coverage.xml");
        val generator  = new SonarQubeCoverageReportGenerator(tempDir);
        generator.generateToFile(outputPath);

        assertThat(outputPath).exists();
        assertThat(Files.readString(outputPath)).contains("<coverage version=\"1\"");
    }

    @ParameterizedTest(name = "target line coverage: trueHits={0}, falseHits={1} -> covered={2}")
    @CsvSource({ "1, 0, true", "0, 1, false" })
    void whenTargetHits_thenLine1CoverageReflectsHits(int trueHits, int falseHits, boolean covered) throws IOException {
        writeCoverageNdjson(String.format(
                """
                {"testIdentifier":"test-1","policies":[{"documentName":"test","documentType":"policy","filePath":"test.sapl","targetTrueHits":%d,"targetFalseHits":%d}]}
                """,
                trueHits, falseHits));

        val generator = new SonarQubeCoverageReportGenerator(tempDir);
        val xml       = generator.generate();

        assertThat(xml).contains("lineNumber=\"1\" covered=\"" + covered + "\"");
    }

    @Test
    @DisplayName("includes branch coverage data for conditions")
    void whenConditionsHaveBranches_thenIncludesBranchData() throws IOException {
        writeCoverageNdjson(
                """
                        {"testIdentifier":"test-1","policies":[{"documentName":"test","documentType":"policy","filePath":"test.sapl","targetTrueHits":1,"targetFalseHits":0,"branches":[{"statementId":0,"line":7,"trueHits":3,"falseHits":0}]}]}
                        """);

        val generator = new SonarQubeCoverageReportGenerator(tempDir);
        val xml       = generator.generate();

        assertThat(xml).contains("lineNumber=\"7\"").contains("branchesToCover=\"2\"")
                .contains("coveredBranches=\"1\"");
    }

    @Test
    @DisplayName("createDefault uses target/sapl-coverage directory")
    void whenCreateDefault_thenUsesTargetDirectory() {
        val generator = SonarQubeCoverageReportGenerator.createDefault();

        assertThat(generator).isNotNull();
    }

    @Test
    @DisplayName("skips blank lines in NDJSON file")
    void whenBlankLinesInNdjson_thenSkipsThem() throws IOException {
        writeCoverageNdjson(
                """
                        {"testIdentifier":"test-1","policies":[{"documentName":"test","documentType":"policy","filePath":"test.sapl","targetTrueHits":1,"targetFalseHits":0}]}

                        {"testIdentifier":"test-2","policies":[{"documentName":"test","documentType":"policy","filePath":"test.sapl","targetTrueHits":1,"targetFalseHits":0}]}
                        """);

        val generator = new SonarQubeCoverageReportGenerator(tempDir);
        val xml       = generator.generate();

        assertThat(xml).contains("<coverage version=\"1\"").contains("test.sapl");
    }

    @Test
    @DisplayName("handles missing policies array gracefully")
    void whenNoPoliciesArray_thenGeneratesEmptyFile() throws IOException {
        writeCoverageNdjson("""
                {"testIdentifier":"test-1"}
                """);

        val generator = new SonarQubeCoverageReportGenerator(tempDir);
        val xml       = generator.generate();

        assertThat(xml).contains("<coverage version=\"1\"").doesNotContain("<file");
    }

    @Test
    @DisplayName("handles missing documentName gracefully")
    void whenMissingDocumentName_thenSkipsPolicy() throws IOException {
        writeCoverageNdjson(
                """
                        {"testIdentifier":"test-1","policies":[{"documentType":"policy","filePath":"test.sapl","targetTrueHits":1}]}
                        """);

        val generator = new SonarQubeCoverageReportGenerator(tempDir);
        val xml       = generator.generate();

        assertThat(xml).doesNotContain("<file");
    }

    private void writeCoverageNdjson(String content) throws IOException {
        Files.writeString(tempDir.resolve("coverage.ndjson"), content);
    }
}
