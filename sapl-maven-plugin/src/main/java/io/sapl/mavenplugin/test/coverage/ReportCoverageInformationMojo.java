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
package io.sapl.mavenplugin.test.coverage;

import io.sapl.api.coverage.PolicyCoverageData;
import io.sapl.mavenplugin.test.coverage.report.html.HtmlLineCoverageReportGenerator;
import io.sapl.test.coverage.AggregatedCoverageData;
import io.sapl.test.coverage.CoverageReader;
import io.sapl.test.coverage.SonarQubeCoverageReportGenerator;
import lombok.Setter;
import lombok.val;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Maven mojo for generating SAPL policy coverage reports.
 * <p>
 * Reads coverage data from NDJSON files written by sapl-test and generates
 * coverage reports in HTML and SonarQube XML formats. Also validates coverage
 * against configured thresholds.
 */
@Mojo(name = "report-coverage-information", defaultPhase = LifecyclePhase.VERIFY)
public class ReportCoverageInformationMojo extends AbstractMojo {

    private static final String ERROR_CANNOT_REPORT_COVERAGE_DISABLED   = "Cannot report and validate SAPL code coverage requirements if coverage collection is disabled.";
    private static final String ERROR_COVERAGE_FILE_NOT_FOUND           = "Coverage file not found: %s";
    private static final String ERROR_COVERAGE_THRESHOLDS_NOT_MET       = "One or more SAPL coverage thresholds not met. See build log for details.";
    private static final String ERROR_FAILED_READING_COVERAGE_DATA      = "Failed reading coverage data from SAPL tests.";
    private static final String ERROR_GENERATING_SONARQUBE_REPORT       = "Error generating SonarQube coverage report.";
    private static final String ERROR_SAPL_TEST_REQUIREMENTS_NOT_PASSED = "SAPL test requirements not passed. Tests must be enabled.";

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "true")
    private boolean coverageEnabled;

    @Parameter
    private String outputDir;

    @Parameter(defaultValue = "0")
    private float policySetHitRatio;

    @Parameter(defaultValue = "0")
    private float policyHitRatio;

    @Parameter(defaultValue = "0")
    private float policyConditionHitRatio;

    @Parameter(defaultValue = "0")
    private float branchCoverageRatio;

    @Parameter(defaultValue = "false")
    private boolean enableSonarReport;

    @Parameter(defaultValue = "true")
    private boolean enableHtmlReport;

    @Setter
    @Parameter(defaultValue = "true")
    private boolean failOnDisabledTests;

    @Setter
    @Parameter(property = "maven.test.skip", defaultValue = "false")
    private boolean mavenTestSkip;

    @Setter
    @Parameter(property = "skipTests", defaultValue = "false")
    private boolean skipTests;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!coverageEnabled) {
            logCoverageDisabledError();
            throw new MojoFailureException(ERROR_CANNOT_REPORT_COVERAGE_DISABLED);
        }

        if (shouldSkipDueToDisabledTests()) {
            return;
        }

        var projectBuildDir = project != null ? project.getBuild().getDirectory() : null;
        val baseDir         = PathHelper.resolveBaseDir(outputDir, projectBuildDir, getLog());

        AggregatedCoverageData coverage;
        try {
            coverage = readCoverageData(baseDir);
        } catch (IOException e) {
            logCoverageReadError(e);
            throw new MojoFailureException(ERROR_FAILED_READING_COVERAGE_DATA, e);
        }

        logCoverageInfo(coverage);

        val policySetOk = checkPolicySetRatio(coverage);
        val policyOk    = checkPolicyRatio(coverage);
        val conditionOk = checkConditionRatio(coverage);
        val branchOk    = checkBranchCoverage(coverage);

        getLog().info("");
        getLog().info("");

        generateReports(coverage, baseDir);

        getLog().info("");
        getLog().info("");

        validateAllThresholds(policySetOk, policyOk, conditionOk, branchOk);
    }

    private void logCoverageDisabledError() {
        getLog().error("Policy coverage collection is disabled");
        getLog().error("Probable cause: sapl-maven-plugin executions block is misconfigured.");
        getLog().error("Please add the following to your plug-in configuration:");
        getLog().error("<executions>");
        getLog().error("    <execution>");
        getLog().error("        <id>coverage</id>");
        getLog().error("        <goals>");
        getLog().error("            <goal>enable-coverage-collection</goal>");
        getLog().error("            <goal>report-coverage-information</goal>");
        getLog().error("        </goals>");
        getLog().error("    </execution>");
        getLog().error("</executions>");
    }

    private boolean shouldSkipDueToDisabledTests() throws MojoFailureException {
        val testsSkipped = mavenTestSkip || skipTests;

        if (testsSkipped) {
            if (failOnDisabledTests) {
                getLog().error(
                        "Tests were skipped, but the sapl-maven-plugin is configured to enforce tests to be run.");
                getLog().error("Build used '-Dmaven.test.skip=true' or '-DskipTests'. "
                        + "Set 'failOnDisabledTests' to false to allow this.");
                throw new MojoFailureException(ERROR_SAPL_TEST_REQUIREMENTS_NOT_PASSED);
            } else {
                getLog().info("Tests disabled. Skipping coverage validation. "
                        + "Set 'failOnDisabledTests' to true to fail the build in this case.");
                return true;
            }
        }
        return false;
    }

    private AggregatedCoverageData readCoverageData(Path baseDir) throws IOException {
        val reader = new CoverageReader(baseDir);
        if (!reader.coverageFileExists()) {
            throw new IOException(ERROR_COVERAGE_FILE_NOT_FOUND.formatted(reader.getCoverageFilePath()));
        }
        return reader.readAggregated();
    }

    private void logCoverageReadError(IOException e) {
        getLog().error("Error reading coverage data: " + e.getMessage());
        getLog().error("Probable causes:");
        getLog().error(" - No tests of SAPL policies in this module.");
        getLog().error(" - Build skipped tests but sapl-maven-plugin is still executed.");
        getLog().error(" - The coverage.ndjson file was not created by sapl-test.");
    }

    private void logCoverageInfo(AggregatedCoverageData coverage) {
        getLog().info("");
        getLog().info("Measured SAPL coverage information:");
        getLog().info("");
        getLog().info("  Tests executed: " + coverage.getTestCount());
        getLog().info("  Total evaluations: " + coverage.getTotalEvaluations());
        getLog().info("  Policies covered: " + coverage.getPolicyCount());
        getLog().info("");
    }

    private boolean checkPolicySetRatio(AggregatedCoverageData coverage) {
        int setCount = coverage.getPolicySetCount();
        if (setCount == 0) {
            getLog().info("No policy sets to evaluate.");
            return true;
        }

        float ratio = coverage.getPolicySetHitRatio();
        getLog().info("Policy Set Hit Ratio: %.2f%% (%d of %d matched)".formatted(ratio,
                coverage.getMatchedPolicySetCount(), setCount));

        if (ratio < policySetHitRatio) {
            getLog().error("Policy Set Hit Ratio not fulfilled. Required: %.2f%%, Actual: %.2f%%"
                    .formatted(policySetHitRatio, ratio));
            return false;
        }
        return true;
    }

    private boolean checkPolicyRatio(AggregatedCoverageData coverage) {
        int policyCount = coverage.getStandalonePolicyCount();
        if (policyCount == 0) {
            getLog().info("No standalone policies to evaluate.");
            return true;
        }

        float ratio = coverage.getPolicyHitRatio();
        getLog().info("Policy Hit Ratio: %.2f%% (%d of %d matched)".formatted(ratio,
                coverage.getMatchedStandalonePolicyCount(), policyCount));

        if (ratio < policyHitRatio) {
            getLog().error("Policy Hit Ratio not fulfilled. Required: %.2f%%, Actual: %.2f%%".formatted(policyHitRatio,
                    ratio));
            return false;
        }
        return true;
    }

    private boolean checkConditionRatio(AggregatedCoverageData coverage) {
        val ratio = coverage.getConditionHitRatio();
        getLog().info("Condition Hit Ratio: %.2f%%".formatted(ratio));

        if (ratio < policyConditionHitRatio) {
            getLog().error("Condition Hit Ratio not fulfilled. Required: %.2f%%, Actual: %.2f%%"
                    .formatted(policyConditionHitRatio, ratio));
            return false;
        }
        return true;
    }

    private boolean checkBranchCoverage(AggregatedCoverageData coverage) {
        if (branchCoverageRatio <= 0) {
            return true;
        }

        val ratio = coverage.getOverallBranchCoverage();
        getLog().info("Branch Coverage: %.2f%%".formatted(ratio));

        if (ratio < branchCoverageRatio) {
            getLog().error("Branch Coverage not fulfilled. Required: %.2f%%, Actual: %.2f%%"
                    .formatted(branchCoverageRatio, ratio));
            return false;
        }
        return true;
    }

    private void generateReports(AggregatedCoverageData coverage, Path baseDir) throws MojoExecutionException {
        if (!enableSonarReport && !enableHtmlReport) {
            return;
        }

        if (enableSonarReport) {
            generateSonarReport(baseDir);
        }

        if (enableHtmlReport) {
            generateHtmlReport(coverage, baseDir);
        }
    }

    private void generateSonarReport(Path baseDir) throws MojoExecutionException {
        try {
            val sonarOutputPath = baseDir.resolve("sonar").resolve("sonar-generic-coverage.xml");
            val generator       = new SonarQubeCoverageReportGenerator(baseDir);
            generator.generateToFile(sonarOutputPath);
            getLog().info("SonarQube coverage report written to: " + sonarOutputPath);
        } catch (IOException e) {
            throw new MojoExecutionException(ERROR_GENERATING_SONARQUBE_REPORT, e);
        }
    }

    private void generateHtmlReport(AggregatedCoverageData coverage, Path baseDir) throws MojoExecutionException {
        val policies       = coverage.getPolicyCoverageList();
        val projectBaseDir = project != null ? project.getBasedir().toPath() : Path.of(".");
        populatePolicySources(policies, projectBaseDir);

        val generator = new HtmlLineCoverageReportGenerator();
        val indexHtml = generator.generateHtmlReport(policies, baseDir, coverage.getPolicySetHitRatio(),
                coverage.getPolicyHitRatio(), coverage.getConditionHitRatio());
        getLog().info("HTML coverage report: " + indexHtml.toUri());
    }

    /**
     * Reads policy source files to populate documentSource for HTML report
     * generation.
     * <p>
     * The NDJSON coverage format stores file paths relative to the classpath root.
     * For HTML reports with syntax highlighting, we need to read the actual source
     * from the src/main/resources or target/classes directories.
     */
    private void populatePolicySources(Collection<PolicyCoverageData> policies, Path projectBaseDir) {
        for (val policy : policies) {
            populatePolicySource(policy, projectBaseDir);
        }
    }

    private void populatePolicySource(PolicyCoverageData policy, Path projectBaseDir) {
        if (policy.getDocumentSource() != null && !policy.getDocumentSource().isEmpty()) {
            return;
        }

        val filePath = policy.getFilePath();
        if (filePath == null || filePath.isEmpty()) {
            getLog().debug("No file path for policy: " + policy.getDocumentName());
            return;
        }

        val source = tryReadPolicySource(projectBaseDir, filePath);
        if (source != null) {
            policy.setDocumentSource(source);
            getLog().debug("Loaded source for: " + policy.getDocumentName());
        } else {
            getLog().debug("Source file not found for: " + policy.getDocumentName());
        }
    }

    /**
     * Tries to read policy source from multiple locations.
     * <p>
     * File paths in coverage data are relative to the classpath root, so we need
     * to try src/main/resources first, then target/classes as a fallback.
     */
    private String tryReadPolicySource(Path projectBaseDir, String relativePath) {
        val candidatePaths = new Path[] { projectBaseDir.resolve("src/main/resources").resolve(relativePath),
                projectBaseDir.resolve("target/classes").resolve(relativePath), projectBaseDir.resolve(relativePath) };

        for (val path : candidatePaths) {
            if (Files.exists(path)) {
                try {
                    return Files.readString(path);
                } catch (IOException e) {
                    getLog().debug("Could not read: " + path + " - " + e.getMessage());
                }
            }
        }
        return null;
    }

    private void validateAllThresholds(boolean policySetOk, boolean policyOk, boolean conditionOk, boolean branchOk)
            throws MojoFailureException {
        if (policySetOk && policyOk && conditionOk && branchOk) {
            getLog().info("All coverage criteria passed.");
            getLog().info("");
        } else {
            throw new MojoFailureException(ERROR_COVERAGE_THRESHOLDS_NOT_MET);
        }
    }
}
