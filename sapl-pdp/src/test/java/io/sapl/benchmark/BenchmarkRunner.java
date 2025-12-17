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
package io.sapl.benchmark;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.runner.options.VerboseMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Programmatic runner for SAPL PDP benchmarks using JMH. Executes benchmarks
 * and generates comprehensive reports.
 *
 * <pre>
 *   mvn test-compile exec:java -Dexec.mainClass="io.sapl.benchmark.BenchmarkRunner" \
 *       -Dexec.classpathScope=test -Dexec.args="[QUICK|STANDARD|FULL]"
 * </pre>
 */
public final class BenchmarkRunner {

    /**
     * Benchmark profiles with different levels of rigor. Note: forks=0 required
     * when running via exec:java since forked
     * JVMs cannot access Maven's test classpath. For forked runs, use the JMH Maven
     * Plugin.
     */
    public enum Profile {
        /** Fast validation - minimal runs for development validation */
        FAST(0, 1, 1, 1, 1),
        /** Quick validation - minimal runs for fast iteration */
        QUICK(0, 2, 1, 2, 1),
        /** Standard benchmark - good balance of speed and accuracy */
        STANDARD(0, 3, 2, 3, 3),
        /** Full scientific rigor - for publication */
        FULL(0, 5, 3, 5, 5);

        final int forks;
        final int warmupIterations;
        final int warmupSeconds;
        final int measurementIterations;
        final int measurementSeconds;

        Profile(int forks, int warmupIterations, int warmupSeconds, int measurementIterations, int measurementSeconds) {
            this.forks                 = forks;
            this.warmupIterations      = warmupIterations;
            this.warmupSeconds         = warmupSeconds;
            this.measurementIterations = measurementIterations;
            this.measurementSeconds    = measurementSeconds;
        }
    }

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final Profile profile;
    private final Path    outputDirectory;
    private final String  timestamp;

    public BenchmarkRunner(Profile profile, Path outputDirectory) {
        this.profile         = profile;
        this.outputDirectory = outputDirectory;
        this.timestamp       = LocalDateTime.now().format(TIMESTAMP_FORMAT);
    }

    public static void main(String[] args) throws Exception {
        var profile   = args.length > 0 ? Profile.valueOf(args[0].toUpperCase()) : Profile.QUICK;
        var outputDir = Path.of("target", "benchmark-results");

        printHeader();
        System.out.println("Profile: " + profile);
        System.out.println("Output: " + outputDir.toAbsolutePath());
        System.out.println();

        var runner  = new BenchmarkRunner(profile, outputDir);
        var results = runner.runBenchmarks();
        runner.generateReport(results);

        System.out.println("\nBenchmark complete. Results in: " + outputDir.toAbsolutePath());
    }

    private static void printHeader() {
        System.out.println("=".repeat(80));
        System.out.println("SAPL PDP BENCHMARK SUITE (JMH-based)");
        System.out.println("=".repeat(80));
    }

    /**
     * Runs all benchmarks and returns results.
     */
    public Collection<RunResult> runBenchmarks() throws RunnerException, IOException {
        Files.createDirectories(outputDirectory);

        var jsonResultPath = outputDirectory.resolve("jmh-results-" + timestamp + ".json");

        Options options = new OptionsBuilder().include(PdpBenchmark.class.getSimpleName()).forks(profile.forks)
                .warmupIterations(profile.warmupIterations).warmupTime(TimeValue.seconds(profile.warmupSeconds))
                .measurementIterations(profile.measurementIterations)
                .measurementTime(TimeValue.seconds(profile.measurementSeconds)).resultFormat(ResultFormatType.JSON)
                .result(jsonResultPath.toString()).verbosity(VerboseMode.NORMAL).build();

        System.out.println("\nStarting JMH benchmarks...\n");
        var runner = new Runner(options);
        return runner.run();
    }

    /**
     * Generates a comprehensive Markdown report from benchmark results.
     */
    public void generateReport(Collection<RunResult> results) throws IOException {
        var reportPath = outputDirectory.resolve("benchmark-report-" + timestamp + ".md");
        var report     = new StringBuilder();

        // Header
        report.append("# SAPL PDP Benchmark Report\n\n");
        report.append("**Generated:** ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");

        // System info
        appendSystemInfo(report);

        // Benchmark configuration
        appendBenchmarkConfig(report);

        // Results summary
        appendResultsSummary(report, results);

        // Detailed results by factor
        appendDetailedResults(report, results);

        // Scaling analysis
        appendScalingAnalysis(report, results);

        // Latency analysis
        appendLatencyAnalysis(report, results);

        Files.writeString(reportPath, report.toString());
        System.out.println("\nReport generated: " + reportPath);
    }

    private void appendSystemInfo(StringBuilder report) {
        report.append("## System Information\n\n");
        report.append("| Property | Value |\n");
        report.append("|----------|-------|\n");
        report.append("| Java Version | ").append(System.getProperty("java.version")).append(" |\n");
        report.append("| Java Vendor | ").append(System.getProperty("java.vendor")).append(" |\n");
        report.append("| OS | ").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.version")).append(" |\n");
        report.append("| Architecture | ").append(System.getProperty("os.arch")).append(" |\n");
        report.append("| CPU Cores | ").append(Runtime.getRuntime().availableProcessors()).append(" |\n");
        report.append("| Max Heap | ").append(formatBytes(Runtime.getRuntime().maxMemory())).append(" |\n");
        report.append('\n');
    }

    private void appendBenchmarkConfig(StringBuilder report) {
        report.append("## Benchmark Configuration\n\n");
        report.append("| Parameter | Value |\n");
        report.append("|-----------|-------|\n");
        report.append("| Profile | ").append(profile.name()).append(" |\n");
        report.append("| Forks | ").append(profile.forks).append(" |\n");
        report.append("| Warmup | ").append(profile.warmupIterations).append(" x ").append(profile.warmupSeconds)
                .append("s |\n");
        report.append("| Measurement | ").append(profile.measurementIterations).append(" x ")
                .append(profile.measurementSeconds).append("s |\n");
        report.append('\n');
    }

    private void appendResultsSummary(StringBuilder report, Collection<RunResult> results) {
        report.append("## Results Summary\n\n");

        // Group by benchmark method (throughput tests)
        var throughputResults = results.stream().filter(r -> r.getParams().getBenchmark().contains("throughput"))
                .toList();

        if (throughputResults.isEmpty()) {
            report.append("No throughput results available.\n\n");
            return;
        }

        // Find best configuration
        var best = throughputResults.stream().max(Comparator.comparingDouble(r -> r.getPrimaryResult().getScore()))
                .orElse(null);

        if (best != null) {
            report.append("**Best Configuration:**\n");
            report.append("- Benchmark: ").append(extractMethodName(best)).append('\n');
            report.append("- Path: ").append(best.getParams().getParam("evaluationPath")).append('\n');
            report.append("- Policies: ").append(best.getParams().getParam("policyCount")).append('\n');
            report.append("- Algorithm: ").append(best.getParams().getParam("combiningAlgorithm")).append('\n');
            report.append("- Throughput: ").append(formatNumber(best.getPrimaryResult().getScore()))
                    .append(" ops/sec\n\n");
        }

        // Summary table for REACTIVE vs PURE at max threads
        report.append("### Evaluation Path Comparison (48 threads, 9 policies, DENY_OVERRIDES)\n\n");
        report.append("| Path | Throughput (ops/sec) | Error |\n");
        report.append("|------|---------------------|-------|\n");

        // Order: REACTIVE, PURE for logical comparison
        var pathOrder = List.of("REACTIVE", "PURE");
        for (var targetPath : pathOrder) {
            for (var result : throughputResults) {
                var benchmark = result.getParams().getBenchmark();
                if (benchmark.contains("48threads") && "9".equals(result.getParams().getParam("policyCount"))
                        && "DENY_OVERRIDES".equals(result.getParams().getParam("combiningAlgorithm"))) {
                    var path = result.getParams().getParam("evaluationPath");
                    if (targetPath.equals(path)) {
                        var score = result.getPrimaryResult().getScore();
                        var error = result.getPrimaryResult().getScoreError();
                        report.append("| ").append(path).append(" | ").append(formatNumber(score)).append(" | +/- ")
                                .append(formatNumber(error)).append(" |\n");
                    }
                }
            }
        }
        report.append('\n');
    }

    private void appendDetailedResults(StringBuilder report, Collection<RunResult> results) {
        report.append("## Detailed Results\n\n");

        // Group results by evaluation path
        Map<String, List<RunResult>> byPath = results.stream()
                .filter(r -> r.getParams().getBenchmark().contains("throughput")).collect(Collectors.groupingBy(
                        r -> r.getParams().getParam("evaluationPath"), LinkedHashMap::new, Collectors.toList()));

        for (var entry : byPath.entrySet()) {
            report.append("### ").append(entry.getKey()).append(" Path\n\n");
            report.append("| Threads | Policies | Algorithm | Throughput | Error |\n");
            report.append("|---------|----------|-----------|------------|-------|\n");

            var sorted = entry.getValue().stream().sorted(Comparator.comparingInt(this::extractThreadCount)
                    .thenComparingInt(r -> Integer.parseInt(r.getParams().getParam("policyCount")))).toList();

            for (var result : sorted) {
                var threads   = extractThreadCount(result);
                var policies  = result.getParams().getParam("policyCount");
                var algorithm = result.getParams().getParam("combiningAlgorithm");
                var score     = result.getPrimaryResult().getScore();
                var error     = result.getPrimaryResult().getScoreError();

                report.append("| ").append(threads).append(" | ").append(policies).append(" | ").append(algorithm)
                        .append(" | ").append(formatNumber(score)).append(" | +/- ").append(formatNumber(error))
                        .append(" |\n");
            }
            report.append('\n');
        }
    }

    private void appendScalingAnalysis(StringBuilder report, Collection<RunResult> results) {
        report.append("## Scaling Analysis\n\n");
        report.append("Throughput scaling with thread count (9 policies, DENY_OVERRIDES):\n\n");
        report.append("| Threads | REACTIVE | PURE | PURE Speedup |\n");
        report.append("|---------|----------|------|-------------|\n");

        var pureResults     = new LinkedHashMap<Integer, Double>();
        var reactiveResults = new LinkedHashMap<Integer, Double>();

        for (var result : results) {
            var benchmark = result.getParams().getBenchmark();
            if (!benchmark.contains("throughput"))
                continue;
            if (!"9".equals(result.getParams().getParam("policyCount")))
                continue;
            if (!"DENY_OVERRIDES".equals(result.getParams().getParam("combiningAlgorithm")))
                continue;

            var threads = extractThreadCount(result);
            var path    = result.getParams().getParam("evaluationPath");
            var score   = result.getPrimaryResult().getScore();

            if ("PURE".equals(path)) {
                pureResults.put(threads, score);
            } else if ("REACTIVE".equals(path)) {
                reactiveResults.put(threads, score);
            }
        }

        for (var threads : new int[] { 1, 4, 8, 16, 24, 32, 48 }) {
            var pure     = pureResults.getOrDefault(threads, 0.0);
            var reactive = reactiveResults.getOrDefault(threads, 0.0);
            var speedup  = reactive > 0 ? pure / reactive : 0;

            report.append("| ").append(threads).append(" | ").append(formatNumber(reactive)).append(" | ")
                    .append(formatNumber(pure)).append(" | ").append(String.format("%.2fx", speedup)).append(" |\n");
        }
        report.append('\n');
    }

    private void appendLatencyAnalysis(StringBuilder report, Collection<RunResult> results) {
        report.append("## Latency Analysis\n\n");

        var latencyResults = results.stream().filter(r -> r.getParams().getBenchmark().contains("latency")).toList();

        if (latencyResults.isEmpty()) {
            report.append("No latency results available.\n\n");
            return;
        }

        report.append("Single-threaded latency (microseconds):\n\n");
        report.append("| Path | Policies | Algorithm | Mean | p50 | p99 | p99.9 |\n");
        report.append("|------|----------|-----------|------|-----|-----|-------|\n");

        for (var result : latencyResults) {
            var path      = result.getParams().getParam("evaluationPath");
            var policies  = result.getParams().getParam("policyCount");
            var algorithm = result.getParams().getParam("combiningAlgorithm");
            var primary   = result.getPrimaryResult();

            // JMH SampleTime mode provides percentiles
            var stats = primary.getStatistics();
            var mean  = stats.getMean();
            var p50   = stats.getPercentile(50);
            var p99   = stats.getPercentile(99);
            var p999  = stats.getPercentile(99.9);

            report.append("| ").append(path).append(" | ").append(policies).append(" | ").append(algorithm)
                    .append(" | ").append(String.format("%.1f", mean)).append(" | ").append(String.format("%.1f", p50))
                    .append(" | ").append(String.format("%.1f", p99)).append(" | ").append(String.format("%.1f", p999))
                    .append(" |\n");
        }
        report.append('\n');
    }

    private String extractMethodName(RunResult result) {
        var fullName = result.getParams().getBenchmark();
        var lastDot  = fullName.lastIndexOf('.');
        return lastDot >= 0 ? fullName.substring(lastDot + 1) : fullName;
    }

    private int extractThreadCount(RunResult result) {
        var method = extractMethodName(result);
        if (method.contains("1thread"))
            return 1;
        if (method.contains("4thread"))
            return 4;
        if (method.contains("8thread"))
            return 8;
        if (method.contains("16thread"))
            return 16;
        if (method.contains("24thread"))
            return 24;
        if (method.contains("32thread"))
            return 32;
        if (method.contains("48thread"))
            return 48;
        return 1;
    }

    private static String formatNumber(double value) {
        if (value >= 1_000_000) {
            return String.format("%.2fM", value / 1_000_000);
        } else if (value >= 1_000) {
            return String.format("%.1fK", value / 1_000);
        }
        return String.format("%.0f", value);
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        } else if (bytes >= 1024L * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
        return bytes + " bytes";
    }
}
