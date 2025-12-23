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
package io.sapl.mavenplugin.test.coverage.report.html;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.sapl.mavenplugin.test.coverage.report.SampleCoverageInformation;

/**
 * Integration tests for HtmlLineCoverageReportGenerator.
 * <p>
 * These tests are tagged as "integration" because they require assets (like
 * sapl-mode.js)
 * that are only available after the maven build process copies them from
 * node_modules.
 * They should be run during the verify phase, not the test phase.
 */
@Tag("integration")
class HtmlLineCoverageReportGeneratorTests {

    private static final float POLICY_SET_HIT_RATIO       = 100;
    private static final float POLICY_HIT_RATIO           = 66.6f;
    private static final float POLICY_CONDITION_HIT_RATIO = 43.9f;

    private final HtmlLineCoverageReportGenerator generator = new HtmlLineCoverageReportGenerator();

    @Test
    void whenGenerateHtmlReport_thenCreatesExpectedFiles(@TempDir Path tempDir) throws MojoExecutionException {
        generator.generateHtmlReport(SampleCoverageInformation.policies(), tempDir, POLICY_SET_HIT_RATIO,
                POLICY_HIT_RATIO, POLICY_CONDITION_HIT_RATIO);

        assertAll("All expected files should exist",
                () -> assertTrue(Files.exists(tempDir.resolve("html/assets/images/favicon.png"))),
                () -> assertTrue(Files.exists(tempDir.resolve("html/assets/images/logo-header.png"))),
                () -> assertTrue(Files.exists(tempDir.resolve("html/assets/lib/css/main.css"))),
                () -> assertTrue(Files.exists(tempDir.resolve("html/policies/policy_1.sapl.html"))),
                () -> assertTrue(Files.exists(tempDir.resolve("html/report.html"))));
    }

    @Test
    void whenGenerateHtmlReport_thenReportContainsExpectedContent(@TempDir Path tempDir)
            throws MojoExecutionException, IOException {
        generator.generateHtmlReport(SampleCoverageInformation.policies(), tempDir, POLICY_SET_HIT_RATIO,
                POLICY_HIT_RATIO, POLICY_CONDITION_HIT_RATIO);

        var reportContent = Files.readString(tempDir.resolve("html/report.html"));

        assertAll("Report should contain expected content",
                () -> assertTrue(reportContent.contains("policy_1.sapl"), "Should contain policy filename"),
                () -> assertTrue(reportContent.contains("Coverage Report"), "Should contain report title"));
    }
}
