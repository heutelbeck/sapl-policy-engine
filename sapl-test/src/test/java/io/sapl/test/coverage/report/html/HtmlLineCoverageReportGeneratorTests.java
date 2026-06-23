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
package io.sapl.test.coverage.report.html;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.PATH;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.sapl.api.coverage.PolicyCoverageData;
import lombok.val;

/**
 * Integration tests for HtmlLineCoverageReportGenerator.
 * <p>
 * Tagged as "integration" because they require the CodeMirror WebJar on the
 * classpath, which is only available after Maven resolves dependencies.
 */
@Tag("integration")
@DisplayName("HTML coverage report generator")
class HtmlLineCoverageReportGeneratorTests {

    private static final float POLICY_SET_HIT_RATIO       = 100;
    private static final float POLICY_HIT_RATIO           = 66.6f;
    private static final float POLICY_CONDITION_HIT_RATIO = 43.9f;

    @Nested
    @DisplayName("report generation")
    class ReportGenerationTests {

        @TempDir
        Path tempDir;

        @BeforeEach
        void generateReport() throws IOException {
            val generator = new HtmlLineCoverageReportGenerator();
            generator.generateHtmlReport(SampleCoverageInformation.policies(), tempDir, POLICY_SET_HIT_RATIO,
                    POLICY_HIT_RATIO, POLICY_CONDITION_HIT_RATIO);
        }

        @Test
        @DisplayName("creates all expected files")
        void whenGeneratedThenCreatesExpectedFiles() throws IOException {
            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(tempDir.resolve("html/assets/images/favicon.png")).exists();
                softly.assertThat(tempDir.resolve("html/assets/images/logo-header.png")).exists();
                softly.assertThat(tempDir.resolve("html/assets/lib/css/main.css")).exists();
                softly.assertThat(tempDir.resolve("html/assets/lib/css/codemirror.css")).exists();
                softly.assertThat(tempDir.resolve("html/assets/lib/js/codemirror.js")).exists();
                softly.assertThat(tempDir.resolve("html/assets/lib/js/sapl-mode.js")).exists();
                softly.assertThat(tempDir.resolve("html/assets/lib/js/addon/mode/simple.js")).exists();
                softly.assertThat(tempDir.resolve("html/report.html")).exists();
            });
            try (val policyReports = Files.walk(tempDir.resolve("html/policies"))) {
                assertThat(policyReports.filter(Files::isRegularFile)).singleElement(as(PATH))
                        .extracting(Path::getFileName).asString().startsWith("policy_1.sapl-").endsWith(".html");
            }
        }

        @Test
        @DisplayName("report contains expected content")
        void whenGeneratedThenReportContainsExpectedContent() throws IOException {
            val reportContent = Files.readString(tempDir.resolve("html/report.html"));

            assertThat(reportContent).contains("policy_1.sapl", "Coverage Report");
        }

        @Test
        @DisplayName("does not include Bootstrap or Popper")
        void whenGeneratedThenNoBootstrapOrPopper() {
            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(tempDir.resolve("html/assets/lib/css/bootstrap.min.css")).doesNotExist();
                softly.assertThat(tempDir.resolve("html/assets/lib/js/bootstrap.min.js")).doesNotExist();
                softly.assertThat(tempDir.resolve("html/assets/lib/js/popper.min.js")).doesNotExist();
                softly.assertThat(tempDir.resolve("html/assets/lib/js/require.js")).doesNotExist();
            });
        }

    }

    @Nested
    @DisplayName("template placeholder injection")
    class TemplatePlaceholderInjectionTests {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("a document name that looks like a template placeholder is not substituted as one")
        void whenDocumentNameLooksLikePlaceholderThenItIsNotSubstituted() throws IOException {
            val policy    = new PolicyCoverageData("{{lineModelsJson}}", "policy \"x\"\npermit;", "policy");
            val generator = new HtmlLineCoverageReportGenerator();
            generator.generateHtmlReport(List.of(policy), tempDir, POLICY_SET_HIT_RATIO, POLICY_HIT_RATIO,
                    POLICY_CONDITION_HIT_RATIO);

            String content;
            val    policiesDir = tempDir.resolve("html").resolve("policies");
            try (val generated = Files.walk(policiesDir)) {
                val file = generated.filter(Files::isRegularFile).findFirst().orElseThrow();
                content = Files.readString(file);
            }

            assertThat(content).contains("{{lineModelsJson}}");
        }

    }

    @Nested
    @DisplayName("document name sanitization")
    class DocumentNameSanitizationTests {

        @TempDir
        Path tempDir;

        private void generate(String documentName) throws IOException {
            val policy    = new PolicyCoverageData(documentName, "policy \"x\"\npermit;", "policy");
            val generator = new HtmlLineCoverageReportGenerator();
            generator.generateHtmlReport(List.of(policy), tempDir, POLICY_SET_HIT_RATIO, POLICY_HIT_RATIO,
                    POLICY_CONDITION_HIT_RATIO);
        }

        @Test
        @DisplayName("a document name with path separators does not escape the policies directory")
        void whenDocumentNameContainsPathSeparatorThenFileStaysWithinPoliciesDirectory() throws IOException {
            generate("../../escaped");

            val policiesDir = tempDir.resolve("html").resolve("policies").toRealPath();
            try (val generated = Files.walk(policiesDir)) {
                assertThat(generated.filter(Files::isRegularFile))
                        .allSatisfy(file -> assertThat(file.toRealPath()).startsWith(policiesDir)).hasSize(1);
            }
        }

        @Test
        @DisplayName("the index link points to a file that actually exists on disk")
        void whenDocumentNameContainsSpecialCharactersThenIndexLinkResolvesToAnExistingFile() throws IOException {
            generate("a&b/c");

            val reportContent = Files.readString(tempDir.resolve("html").resolve("report.html"));
            val href          = reportContent.replaceAll("(?s).*<a href=\"policies/", "").replaceAll("(?s)\\.html\".*",
                    "");

            assertThat(tempDir.resolve("html").resolve("policies").resolve(href + ".html")).exists();
        }

        @Test
        @DisplayName("distinct documents that slug to the same name get distinct report files")
        void whenDistinctDocumentsSlugToSameNameThenEachGetsItsOwnReportFile() throws IOException {
            val first     = new PolicyCoverageData("a/b", "policy \"first\"\npermit;", "policy");
            val second    = new PolicyCoverageData("a_b", "policy \"second\"\npermit;", "policy");
            val generator = new HtmlLineCoverageReportGenerator();
            generator.generateHtmlReport(List.of(first, second), tempDir, POLICY_SET_HIT_RATIO, POLICY_HIT_RATIO,
                    POLICY_CONDITION_HIT_RATIO);

            val policiesDir = tempDir.resolve("html").resolve("policies");
            try (val generated = Files.walk(policiesDir)) {
                assertThat(generated.filter(Files::isRegularFile)).hasSize(2);
            }
        }

        @Test
        @DisplayName("every index link resolves to an existing file when document names collide on their slug")
        void whenDocumentNamesCollideOnSlugThenEveryIndexLinkResolvesToAnExistingFile() throws IOException {
            val first     = new PolicyCoverageData("a/b", "policy \"first\"\npermit;", "policy");
            val second    = new PolicyCoverageData("a_b", "policy \"second\"\npermit;", "policy");
            val generator = new HtmlLineCoverageReportGenerator();
            generator.generateHtmlReport(List.of(first, second), tempDir, POLICY_SET_HIT_RATIO, POLICY_HIT_RATIO,
                    POLICY_CONDITION_HIT_RATIO);

            val reportContent = Files.readString(tempDir.resolve("html").resolve("report.html"));
            val matcher       = Pattern.compile("<a href=\"policies/([^\"]+)\"").matcher(reportContent);
            val policiesDir   = tempDir.resolve("html").resolve("policies");
            SoftAssertions.assertSoftly(softly -> {
                var linkCount = 0;
                while (matcher.find()) {
                    linkCount++;
                    softly.assertThat(policiesDir.resolve(matcher.group(1))).exists();
                }
                softly.assertThat(linkCount).isEqualTo(2);
            });
        }

    }

}
