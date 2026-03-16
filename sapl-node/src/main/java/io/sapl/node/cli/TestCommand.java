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
package io.sapl.node.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import io.sapl.test.coverage.AggregatedCoverageData;
import io.sapl.test.coverage.CoverageWriter;
import io.sapl.test.coverage.SonarQubeCoverageReportGenerator;
import io.sapl.test.coverage.report.html.HtmlLineCoverageReportGenerator;
import io.sapl.test.coverage.report.html.PolicySourcePopulator;
import io.sapl.test.plain.PlainTestAdapter;
import io.sapl.test.plain.PlainTestResults;
import io.sapl.test.plain.SaplDocument;
import io.sapl.test.plain.SaplTestDocument;
import io.sapl.test.plain.TestConfiguration;
import io.sapl.test.plain.TestStatus;
import lombok.val;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Discovers .sapl and .sapltest files, runs all test scenarios, and generates
 * coverage reports.
 */
// @formatter:off
@Command(
    name = "test",
    mixinStandardHelpOptions = true,
    header = "Run SAPL tests and generate coverage reports.",
    description = { """
        Discovers .sapl policy files and .sapltest test files from a directory,
        executes all test scenarios, and generates coverage reports. Policies
        and tests are matched by the document names referenced in the test
        files.

        Policies are discovered from --dir. Tests are discovered from --testdir
        if specified, otherwise from --dir.

        Coverage data is written to the output directory as coverage.ndjson.
        HTML and SonarQube reports can be generated from this data.

        Quality gate thresholds can be configured to fail the command when
        coverage ratios are below the required percentages.
        """ },
    exitCodeListHeading = "%nExit Codes:%n",
    exitCodeList = {
        " 0:All tests passed (and quality gate met, if configured)",
        " 1:Error during test execution (I/O, parse errors)",
        " 2:One or more tests failed",
        " 3:Quality gate not met (tests passed but coverage below threshold)"
    },
    footerHeading = "%nExamples:%n",
    footer = { """
          # Run tests from current directory
          sapl test

          # Run tests from a specific directory
          sapl test --dir ./my-policies

          # Policies in one directory, tests in another
          sapl test --dir ./policies --testdir ./tests

          # Generate only SonarQube report (no HTML)
          sapl test --no-html --sonar

          # Custom output directory
          sapl test --output ./reports/sapl-coverage

          # Enforce a coverage threshold
          sapl test --policy-hit-ratio 80

        See Also: sapl-check(1), sapl-decide(1)
        """ }
)
// @formatter:on
class TestCommand implements Callable<Integer> {

    private static final String SAPL_EXTENSION     = ".sapl";
    private static final String SAPLTEST_EXTENSION = ".sapltest";

    static final String ERROR_COVERAGE_REPORT     = "Error: Failed to generate coverage report: %s";
    static final String ERROR_EXECUTION_FAILED    = "Error: Test execution failed: %s";
    static final String ERROR_INVALID_THRESHOLD   = "Error: %s must be between 0 and 100, got: %.2f";
    static final String ERROR_NO_POLICIES_FOUND   = "Error: No .sapl files found in: %s";
    static final String ERROR_NO_TESTS_FOUND      = "Error: No .sapltest files found in: %s";
    static final String ERROR_QUALITY_GATE_FAILED = "Error: Quality gate not met. See details above.";
    static final String ERROR_READING_FILES       = "Error: Failed to read files from: %s (%s)";

    static final String WARN_COVERAGE_WRITE_FAILED = "Warning: Failed to write coverage data: %s";

    @Spec
    CommandSpec spec;

    @Option(names = "--dir", defaultValue = ".", description = "Directory containing .sapl policy files")
    Path dir;

    @Option(names = "--testdir", description = "Directory containing .sapltest test files (default: same as --dir)")
    Path testdir;

    @Option(names = "--output", defaultValue = "./sapl-coverage", description = "Output directory for coverage data and reports")
    Path output;

    @Option(names = "--html", negatable = true, defaultValue = "true", description = "Generate HTML coverage report")
    boolean html;

    @Option(names = "--sonar", negatable = true, defaultValue = "false", description = "Generate SonarQube coverage report")
    boolean sonar;

    @Option(names = "--policy-set-hit-ratio", defaultValue = "0", description = "Required policy set hit ratio, 0-100 (0 = disabled)")
    float policySetHitRatio;

    @Option(names = "--policy-hit-ratio", defaultValue = "0", description = "Required policy hit ratio, 0-100 (0 = disabled)")
    float policyHitRatio;

    @Option(names = "--condition-hit-ratio", defaultValue = "0", description = "Required condition hit ratio, 0-100 (0 = disabled)")
    float conditionHitRatio;

    @Option(names = "--branch-coverage-ratio", defaultValue = "0", description = "Required branch coverage ratio, 0-100 (0 = disabled)")
    float branchCoverageRatio;

    @Override
    public Integer call() {
        val out = spec.commandLine().getOut();
        val err = spec.commandLine().getErr();

        if (!validateThresholds(err)) {
            return 1;
        }

        val effectiveTestdir = testdir != null ? testdir : dir;

        List<SaplDocument>     policies;
        List<SaplTestDocument> tests;
        try {
            policies = discoverFiles(dir, SAPL_EXTENSION).stream().map(TestCommand::toSaplDocument).toList();
            tests    = discoverFiles(effectiveTestdir, SAPLTEST_EXTENSION).stream().map(TestCommand::toSaplTestDocument)
                    .toList();
        } catch (IOException e) {
            err.println(ERROR_READING_FILES.formatted(dir, e.getMessage()));
            return 1;
        }

        if (policies.isEmpty()) {
            err.println(ERROR_NO_POLICIES_FOUND.formatted(dir));
            return 1;
        }
        if (tests.isEmpty()) {
            err.println(ERROR_NO_TESTS_FOUND.formatted(effectiveTestdir));
            return 1;
        }

        val config = TestConfiguration.builder().withSaplDocuments(policies).withSaplTestDocuments(tests).build();

        PlainTestResults results;
        try {
            val adapter = new PlainTestAdapter();
            results = adapter.execute(config);
        } catch (RuntimeException e) {
            err.println(ERROR_EXECUTION_FAILED.formatted(e.getMessage()));
            return 1;
        }

        writeCoverage(results, err);
        printSummary(results, out, err);

        val coverage = aggregateCoverage(results);
        generateReports(coverage, policies, err);

        if (results.errors() > 0) {
            return 2;
        }
        if (!results.allPassed()) {
            return 2;
        }

        return checkQualityGate(coverage, out, err);
    }

    private boolean validateThresholds(PrintWriter err) {
        val valid = validateThreshold("--policy-set-hit-ratio", policySetHitRatio, err)
                & validateThreshold("--policy-hit-ratio", policyHitRatio, err)
                & validateThreshold("--condition-hit-ratio", conditionHitRatio, err)
                & validateThreshold("--branch-coverage-ratio", branchCoverageRatio, err);
        return valid;
    }

    private static boolean validateThreshold(String name, float value, PrintWriter err) {
        if (value < 0 || value > 100) {
            err.println(ERROR_INVALID_THRESHOLD.formatted(name, value));
            return false;
        }
        return true;
    }

    private static List<Path> discoverFiles(Path directory, String extension) throws IOException {
        try (Stream<Path> stream = Files.walk(directory)) {
            return stream.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(extension)).sorted().toList();
        }
    }

    private static SaplDocument toSaplDocument(Path path) {
        try {
            val source = Files.readString(path);
            val name   = path.getFileName().toString();
            return SaplDocument.of(name, source, path.toString());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read: " + path, e);
        }
    }

    private static SaplTestDocument toSaplTestDocument(Path path) {
        try {
            val source = Files.readString(path);
            val name   = path.getFileName().toString();
            return SaplTestDocument.of(name, source);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read: " + path, e);
        }
    }

    private void writeCoverage(PlainTestResults results, PrintWriter err) {
        val writer = new CoverageWriter(output);
        for (val entry : results.coverageByDocumentId().entrySet()) {
            try {
                writer.write(entry.getValue());
            } catch (IOException e) {
                err.println(WARN_COVERAGE_WRITE_FAILED.formatted(e.getMessage()));
            }
        }
    }

    private void printSummary(PlainTestResults results, PrintWriter out, PrintWriter err) {
        out.println();
        for (val scenario : results.scenarioResults()) {
            val icon = switch (scenario.status()) {
            case PASSED -> "PASS";
            case FAILED -> "FAIL";
            case ERROR  -> "ERR ";
            };
            out.println("  " + icon + "  " + scenario.fullName());
            if (scenario.status() != TestStatus.PASSED && scenario.failureMessage() != null) {
                err.println("         " + scenario.failureMessage());
            }
        }
        out.println();
        out.println("Tests: %d total, %d passed, %d failed, %d errors".formatted(results.total(), results.passed(),
                results.failed(), results.errors()));
    }

    private void generateReports(AggregatedCoverageData coverage, List<SaplDocument> policies, PrintWriter err) {
        if (html) {
            generateHtmlReport(coverage, policies, err);
        }
        if (sonar) {
            generateSonarReport(err);
        }
    }

    private AggregatedCoverageData aggregateCoverage(PlainTestResults results) {
        val aggregated = new AggregatedCoverageData();
        for (val record : results.coverageByDocumentId().values()) {
            aggregated.merge(record);
        }
        return aggregated;
    }

    private void generateHtmlReport(AggregatedCoverageData coverage, List<SaplDocument> policies, PrintWriter err) {
        val policyCoverageList = coverage.getPolicyCoverageList();
        PolicySourcePopulator.populateSources(policyCoverageList, List.of(dir.toAbsolutePath()));

        try {
            val generator  = new HtmlLineCoverageReportGenerator();
            val reportPath = generator.generateHtmlReport(policyCoverageList, output, coverage.getPolicySetHitRatio(),
                    coverage.getPolicyHitRatio(), coverage.getConditionHitRatio());
            val out        = spec.commandLine().getOut();
            out.println("HTML coverage report: " + reportPath.toUri());
        } catch (IOException e) {
            err.println(ERROR_COVERAGE_REPORT.formatted(e.getMessage()));
        }
    }

    private void generateSonarReport(PrintWriter err) {
        try {
            val generator  = new SonarQubeCoverageReportGenerator(output);
            val outputPath = output.resolve("sonar").resolve("sonar-generic-coverage.xml");
            generator.generateToFile(outputPath);
            val out = spec.commandLine().getOut();
            out.println("SonarQube coverage report: " + outputPath);
        } catch (IOException e) {
            err.println(ERROR_COVERAGE_REPORT.formatted(e.getMessage()));
        }
    }

    private int checkQualityGate(AggregatedCoverageData coverage, PrintWriter out, PrintWriter err) {
        val policySetOk = checkPolicySetRatio(coverage, out);
        val policyOk    = checkPolicyRatio(coverage, out);
        val conditionOk = checkConditionRatio(coverage, out);
        val branchOk    = checkBranchCoverage(coverage, out);

        if (policySetOk && policyOk && conditionOk && branchOk) {
            return 0;
        }
        err.println(ERROR_QUALITY_GATE_FAILED);
        return 3;
    }

    private boolean checkPolicySetRatio(AggregatedCoverageData coverage, PrintWriter out) {
        if (policySetHitRatio <= 0) {
            return true;
        }
        val actual = coverage.getPolicySetHitRatio();
        out.println("Policy Set Hit Ratio: %.2f%% (required: %.2f%%)".formatted(actual, policySetHitRatio));
        return actual >= policySetHitRatio;
    }

    private boolean checkPolicyRatio(AggregatedCoverageData coverage, PrintWriter out) {
        if (policyHitRatio <= 0) {
            return true;
        }
        val actual = coverage.getPolicyHitRatio();
        out.println("Policy Hit Ratio: %.2f%% (required: %.2f%%)".formatted(actual, policyHitRatio));
        return actual >= policyHitRatio;
    }

    private boolean checkConditionRatio(AggregatedCoverageData coverage, PrintWriter out) {
        if (conditionHitRatio <= 0) {
            return true;
        }
        val actual = coverage.getConditionHitRatio();
        out.println("Condition Hit Ratio: %.2f%% (required: %.2f%%)".formatted(actual, conditionHitRatio));
        return actual >= conditionHitRatio;
    }

    private boolean checkBranchCoverage(AggregatedCoverageData coverage, PrintWriter out) {
        if (branchCoverageRatio <= 0) {
            return true;
        }
        val actual = coverage.getOverallBranchCoverage();
        out.println("Branch Coverage: %.2f%% (required: %.2f%%)".formatted(actual, branchCoverageRatio));
        return actual >= branchCoverageRatio;
    }

}
