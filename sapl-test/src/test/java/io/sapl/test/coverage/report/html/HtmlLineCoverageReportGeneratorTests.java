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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
        void whenGeneratedThenCreatesExpectedFiles() {
            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(tempDir.resolve("html/assets/images/favicon.png")).exists();
                softly.assertThat(tempDir.resolve("html/assets/images/logo-header.png")).exists();
                softly.assertThat(tempDir.resolve("html/assets/lib/css/main.css")).exists();
                softly.assertThat(tempDir.resolve("html/assets/lib/css/codemirror.css")).exists();
                softly.assertThat(tempDir.resolve("html/assets/lib/js/codemirror.js")).exists();
                softly.assertThat(tempDir.resolve("html/assets/lib/js/sapl-mode.js")).exists();
                softly.assertThat(tempDir.resolve("html/assets/lib/js/addon/mode/simple.js")).exists();
                softly.assertThat(tempDir.resolve("html/policies/policy_1.sapl.html")).exists();
                softly.assertThat(tempDir.resolve("html/report.html")).exists();
            });
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

}
