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
package io.sapl.node.cli.commands;

import io.sapl.test.coverage.AggregatedCoverageData;
import io.sapl.test.coverage.CoverageWriter;
import io.sapl.test.coverage.SonarQubeCoverageReportGenerator;
import io.sapl.test.coverage.report.html.HtmlLineCoverageReportGenerator;
import io.sapl.test.coverage.report.html.PolicySourcePopulator;
import io.sapl.test.plain.*;
import lombok.val;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

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
public class TestCommand implements Callable<Integer> {

    private static final String POLICIES_DIRECTORY = "policies";
    private static final String SAPL_EXTENSION     = ".sapl";
    private static final String SAPLTEST_EXTENSION = ".sapltest";

    static final String ERROR_COVERAGE_REPORT     = "Error: Failed to generate coverage report: %s.";
    static final String ERROR_EXECUTION_FAILED    = "Error: Test execution failed: %s.";
    static final String ERROR_INVALID_THRESHOLD   = "Error: %s must be between 0 and 100, got: %.2f.";
    static final String ERROR_NO_POLICIES_FOUND   = "Error: No .sapl files found in: %s.";
    static final String ERROR_NO_TESTS_FOUND      = "Error: No .sapltest files found in: %s.";
    static final String ERROR_QUALITY_GATE_FAILED = "Error: Quality gate not met. See details above.";
    static final String ERROR_READING_FILES       = "Error: Failed to read files from: %s (%s).";

    static final String WARN_COVERAGE_WRITE_FAILED = "Warning: Failed to write coverage data: %s.";

    private static final String ANSI_BOLD       = "\u001B[1m";
    private static final String ANSI_BOLD_GREEN = "\u001B[1;32m";
    private static final String ANSI_BOLD_RED   = "\u001B[1;31m";
    private static final String ANSI_FAINT      = "\u001B[2m";
    private static final String ANSI_GREEN      = "\u001B[32m";
    private static final String ANSI_RED        = "\u001B[31m";
    private static final String ANSI_RESET      = "\u001B[0m";

    @Spec
    CommandSpec spec;

    @Option(names = "--dir", defaultValue = ".", description = "Directory containing .sapl policy files")
    Path dir;

    @Option(names = "--testdir", description = "Directory containing .sapltest test files (default: same as --dir)")
    Path testdir;

    @Option(names = "--output", defaultValue = "./sapl-coverage", description = "Output directory for coverage data and reports")
    Path output;

    @Option(names = "--html", negatable = true, defaultValue = "true", fallbackValue = "true", description = "Generate HTML coverage report")
    boolean html;

    @Option(names = "--sonar", negatable = true, defaultValue = "false", fallbackValue = "true", description = "Generate SonarQube coverage report")
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
            policies = discoverFiles(dir, SAPL_EXTENSION).stream().map(p -> toSaplDocument(p, dir)).toList();
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

        val config = TestConfiguration.builder().withSaplDocuments(policies).withSaplTestDocuments(tests)
                .withBasePath(dir).build();

        PlainTestResults results;
        try {
            val adapter = new PlainTestAdapter();
            results = adapter.execute(config);
        } catch (RuntimeException e) {
            err.println(ERROR_EXECUTION_FAILED.formatted(e.getMessage()));
            return 1;
        }

        cleanStaleCoverageData(err);
        writeCoverage(results, err);
        printSummary(results, out, err);

        val coverage = aggregateCoverage(results);
        generateReports(coverage, err);

        if (results.errors() > 0) {
            return 2;
        }
        if (!results.allPassed()) {
            return 2;
        }

        return checkQualityGate(coverage, out, err);
    }

    private boolean validateThresholds(PrintWriter err) {
        val policySetOk = validateThreshold("--policy-set-hit-ratio", policySetHitRatio, err);
        val policyOk    = validateThreshold("--policy-hit-ratio", policyHitRatio, err);
        val conditionOk = validateThreshold("--condition-hit-ratio", conditionHitRatio, err);
        val branchOk    = validateThreshold("--branch-coverage-ratio", branchCoverageRatio, err);
        return policySetOk && policyOk && conditionOk && branchOk;
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

    private static SaplDocument toSaplDocument(Path path, Path baseDir) {
        try {
            val source       = Files.readString(path);
            val relativePath = baseDir.relativize(path).toString().replace('\\', '/');
            val stripped     = relativePath.endsWith(SAPL_EXTENSION)
                    ? relativePath.substring(0, relativePath.length() - SAPL_EXTENSION.length())
                    : relativePath;
            val name         = extractDocumentName(stripped);
            return SaplDocument.of(name, source, path.toString());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read: " + path + ".", e);
        }
    }

    /**
     * Extracts the document name from a relative path, applying the SAPL naming
     * convention: files in the {@code policies/} directory are named by filename
     * only, files in other directories use path-qualified names.
     *
     * @param relativePath the path relative to the base directory, without
     * extension
     * @return the document name
     */
    private static String extractDocumentName(String relativePath) {
        val firstSlash = relativePath.indexOf('/');
        if (firstSlash < 0) {
            return relativePath;
        }
        val firstComponent = relativePath.substring(0, firstSlash);
        if (POLICIES_DIRECTORY.equals(firstComponent)) {
            return relativePath.substring(relativePath.lastIndexOf('/') + 1);
        }
        return relativePath;
    }

    private static SaplTestDocument toSaplTestDocument(Path path) {
        try {
            val source = Files.readString(path);
            val name   = path.getFileName().toString();
            return SaplTestDocument.of(name, source);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read: " + path + ".", e);
        }
    }

    private void cleanStaleCoverageData(PrintWriter err) {
        try {
            Files.deleteIfExists(output.resolve("coverage.ndjson"));
        } catch (IOException e) {
            err.println(WARN_COVERAGE_WRITE_FAILED.formatted(e.getMessage()));
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
        val color = Ansi.AUTO.enabled();

        val grouped = new LinkedHashMap<String, LinkedHashMap<String, List<ScenarioResult>>>();
        for (val scenario : results.scenarioResults()) {
            grouped.computeIfAbsent(scenario.saplTestDocumentId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(scenario.requirementName(), k -> new ArrayList<>()).add(scenario);
        }

        out.println();
        var firstFile = true;
        for (val fileEntry : grouped.entrySet()) {
            if (!firstFile) {
                out.println();
            }
            firstFile = false;
            out.println(styled(color, ANSI_BOLD, "  " + fileEntry.getKey()));

            for (val reqEntry : fileEntry.getValue().entrySet()) {
                out.println(styled(color, ANSI_BOLD, "    " + reqEntry.getKey()));

                for (val scenario : reqEntry.getValue()) {
                    val duration  = formatDuration(scenario.duration().toMillis());
                    val statusTag = switch (scenario.status()) {
                                  case PASSED -> styled(color, ANSI_GREEN, "PASS");
                                  case FAILED -> styled(color, ANSI_BOLD_RED, "FAIL");
                                  case ERROR  -> styled(color, ANSI_BOLD_RED, "ERR ");
                                  };
                    out.println("      " + statusTag + "  " + scenario.scenarioName() + "  "
                            + styled(color, ANSI_FAINT, duration));

                    if (scenario.status() != TestStatus.PASSED && scenario.failureMessage() != null) {
                        err.println(styled(color, ANSI_RED, "            " + scenario.failureMessage()));
                    }
                }
            }
        }

        out.println();
        val totalDuration = results.scenarioResults().stream().map(ScenarioResult::duration).reduce(Duration.ZERO,
                Duration::plus);

        out.println(buildSummaryLine(results, color));
        out.println("Time:   " + styled(color, ANSI_FAINT, formatDuration(totalDuration.toMillis())));
    }

    private static String buildSummaryLine(PlainTestResults results, boolean color) {
        val summary = new StringBuilder("Tests:  ");
        if (results.passed() > 0) {
            summary.append(styled(color, ANSI_BOLD_GREEN, results.passed() + " passed"));
        }
        if (results.failed() > 0) {
            summary.append(", ").append(styled(color, ANSI_BOLD_RED, results.failed() + " failed"));
        }
        if (results.errors() > 0) {
            summary.append(", ").append(styled(color, ANSI_BOLD_RED, results.errors() + " errors"));
        }
        summary.append(", ").append(results.total()).append(" total");
        return summary.toString();
    }

    private static String styled(boolean color, String code, String text) {
        return color ? code + text + ANSI_RESET : text;
    }

    private static String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + "ms";
        }
        return "%.2fs".formatted(millis / 1000.0);
    }

    private void generateReports(AggregatedCoverageData coverage, PrintWriter err) {
        if (html) {
            generateHtmlReport(coverage, err);
        }
        if (sonar) {
            generateSonarReport(err);
        }
    }

    private AggregatedCoverageData aggregateCoverage(PlainTestResults results) {
        val aggregated = new AggregatedCoverageData();
        for (val coverageRecord : results.coverageByDocumentId().values()) {
            aggregated.merge(coverageRecord);
        }
        return aggregated;
    }

    private void generateHtmlReport(AggregatedCoverageData coverage, PrintWriter err) {
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
        val color   = Ansi.AUTO.enabled();
        val hasGate = policySetHitRatio > 0 || policyHitRatio > 0 || conditionHitRatio > 0 || branchCoverageRatio > 0;

        if (hasGate) {
            out.println(styled(color, ANSI_BOLD, "Coverage:"));
        }

        val policySetOk = checkThreshold("Policy Set Hit Ratio", coverage.getPolicySetHitRatio(), policySetHitRatio,
                color, out);
        val policyOk    = checkThreshold("Policy Hit Ratio", coverage.getPolicyHitRatio(), policyHitRatio, color, out);
        val conditionOk = checkThreshold("Condition Hit Ratio", coverage.getConditionHitRatio(), conditionHitRatio,
                color, out);
        val branchOk    = checkThreshold("Branch Coverage", coverage.getOverallBranchCoverage(), branchCoverageRatio,
                color, out);

        if (policySetOk && policyOk && conditionOk && branchOk) {
            return 0;
        }
        err.println(ERROR_QUALITY_GATE_FAILED);
        return 3;
    }

    private static boolean checkThreshold(String label, double actual, double required, boolean color,
            PrintWriter out) {
        if (required <= 0) {
            return true;
        }
        val passed    = actual >= required;
        val indicator = passed ? styled(color, ANSI_GREEN, "PASS") : styled(color, ANSI_BOLD_RED, "FAIL");
        out.println("  %-25s %6.2f%% >= %6.2f%%  %s".formatted(label, actual, required, indicator));
        return passed;
    }

}
