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
package io.sapl.benchmark.sapl3;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Rigorous JMH benchmark runner for SAPL 3.0, matching the sapl-benchmark-sapl4
 * methodology for version-to-version comparison. Uses fork-level convergence.
 */
@Command(name = "sapl-benchmark-sapl3", mixinStandardHelpOptions = true, description = "SAPL 3.0 embedded PDP benchmark.")
class Sapl3Benchmark implements Callable<Integer> {

    private static final Set<String> VALID_METHODS = Set.of("decideOnce", "decideFirst");

    private static final String ERROR_CONVERGENCE_FAILED = "FAILED: did not converge after %d forks (CoV %.2f%%, threshold %.1f%%).";
    private static final String ERROR_SANITY_CHECK       = "Sanity check failed: scenario '%s' produced %s but expected %s.";
    private static final String PROPERTY_UNKNOWN         = PROPERTY_UNKNOWN;
    private static final String ERROR_UNKNOWN_METHOD     = "Unknown method: %s. Valid methods: %s.";

    private static final double[] T_CRITICAL_95 = { 12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262,
            2.228, 2.201, 2.179, 2.160, 2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086, 2.080, 2.074, 2.069, 2.064,
            2.060, 2.056, 2.052, 2.048, 2.045, 2.042 };

    @Spec
    CommandSpec spec;

    @Option(names = "--scenario", defaultValue = "rbac", description = "Benchmark scenario.")
    private String scenario;

    @Option(names = "--method", defaultValue = "decideOnce", description = "Benchmark method: decideOnce, decideFirst.")
    private String method;

    @Option(names = { "-t", "--threads" }, defaultValue = "1", description = "Number of concurrent benchmark threads.")
    private int threads;

    @Option(names = "--warmup-time", defaultValue = "45", description = "Seconds per warmup iteration inside each fork.")
    private int warmupTimeSeconds;

    @Option(names = "--warmup-iterations", defaultValue = "5", description = "Number of warmup iterations inside each fork.")
    private int warmupIterations;

    @Option(names = "--measurement-time", defaultValue = "300", description = "Seconds for the single measurement window per fork.")
    private int measurementTimeSeconds;

    @Option(names = "--convergence-threshold", defaultValue = "2", description = "Maximum CoV (percent) across the convergence window.")
    private double convergenceThresholdPercent;

    @Option(names = "--convergence-window", defaultValue = "3", description = "Number of consecutive converged fork results.")
    private int convergenceWindow;

    @Option(names = "--max-forks", defaultValue = "10", description = "Maximum forks before failing.")
    private int maxForks;

    @Option(names = "--heap", defaultValue = "32g", description = "Heap size for the forked JVM.")
    private String heap;

    @Option(names = "--metadata", description = "Free-form metadata string.")
    private String metadata;

    @Option(names = { "-o", "--output" }, description = "Output directory for results.")
    private Path output;

    private String commandLine;

    @Override
    public Integer call() throws Exception {
        var out = spec.commandLine().getOut();
        var err = spec.commandLine().getErr();

        commandLine = String.join(" ", spec.commandLine().getParseResult().originalArgs());

        if (!validate(err)) {
            return 1;
        }

        if (output != null) {
            Files.createDirectories(output);
        }

        printHeader(out);

        var forkResults = runConvergenceForks(out, err);
        if (forkResults.isEmpty()) {
            return 1;
        }

        printResults(forkResults, out);

        if (output != null) {
            writeResults(forkResults, out);
        }

        return 0;
    }

    private boolean validate(PrintWriter err) {
        if (!VALID_METHODS.contains(method)) {
            err.println(ERROR_UNKNOWN_METHOD.formatted(method, VALID_METHODS));
            return false;
        }
        try {
            var resolvedScenario = Scenario.fromName(scenario);
            var pdp              = resolvedScenario.buildPdp();
            var decision         = pdp.decide(resolvedScenario.subscription())
                    .blockFirst(java.time.Duration.ofSeconds(10));
            if (pdp instanceof AutoCloseable closeable) {
                closeable.close();
            }
            if (decision == null || decision.getDecision() != resolvedScenario.expectedDecision().getDecision()) {
                err.println(ERROR_SANITY_CHECK.formatted(scenario, decision, resolvedScenario.expectedDecision()));
                return false;
            }
        } catch (Exception exception) {
            err.println(exception.getClass().getName() + ": " + exception.getMessage());
            return false;
        }
        return true;
    }

    private void printHeader(PrintWriter out) {
        var jvmVersion = System.getProperty("java.version", PROPERTY_UNKNOWN) + " ("
                + System.getProperty("java.vm.name", PROPERTY_UNKNOWN) + ")";

        out.println("SAPL 3.0 Benchmark");
        out.println("  Command:     " + commandLine);
        out.println("  JVM:         " + jvmVersion);
        out.println("  Heap:        " + heap);
        out.println("  Scenario:    " + scenario);
        out.println("  Method:      " + method);
        out.println("  Threads:     " + threads);
        out.println("  Warmup:      " + warmupIterations + " x " + warmupTimeSeconds + "s per fork");
        out.println("  Measurement: " + measurementTimeSeconds + "s per fork");
        out.println("  Convergence: CoV < " + convergenceThresholdPercent + "% over " + convergenceWindow
                + " forks (max " + maxForks + ")");
        if (metadata != null) {
            out.println("  Metadata:    " + metadata);
        }
        out.println();
        out.flush();
    }

    private ArrayList<Double> runConvergenceForks(PrintWriter out, PrintWriter err) throws RunnerException {
        var forkThroughputs = new ArrayList<Double>();
        var includePattern  = EmbeddedPdpBenchmark.class.getName() + "\\." + method;

        for (int forkIndex = 1; forkIndex <= maxForks; forkIndex++) {
            var throughput = runSingleFork(includePattern, forkIndex);
            forkThroughputs.add(throughput);

            var currentCoV = computeCoV(forkThroughputs);
            var covDisplay = forkThroughputs.size() >= 2 ? String.format(Locale.US, " (CoV: %.2f%%)", currentCoV) : "";
            out.println(String.format(Locale.US, "Fork %d: %,.0f ops/s%s", forkIndex, throughput, covDisplay));
            out.flush();

            if (isConverged(forkThroughputs)) {
                out.println(
                        String.format(Locale.US, "Converged after %d forks (CoV %.2f%% < %.1f%% over last %d forks)",
                                forkIndex, currentCoV, convergenceThresholdPercent, convergenceWindow));
                out.flush();
                return forkThroughputs;
            }
        }

        err.println(String.format(Locale.US, ERROR_CONVERGENCE_FAILED, maxForks, computeCoV(forkThroughputs),
                convergenceThresholdPercent));
        return List.of();
    }

    private double runSingleFork(String includePattern, int forkIndex) throws RunnerException {
        var builder = new OptionsBuilder().include(includePattern).forks(1).warmupIterations(warmupIterations)
                .warmupTime(TimeValue.seconds(warmupTimeSeconds)).measurementIterations(1)
                .measurementTime(TimeValue.seconds(measurementTimeSeconds)).threads(threads)
                .param("scenarioName", scenario).mode(Mode.Throughput).timeUnit(TimeUnit.SECONDS).shouldDoGC(true)
                .syncIterations(true).jvmArgsAppend("-Xmx" + heap);

        if (output != null) {
            var resultPath = output.resolve(scenario + "_" + method + "_" + threads + "t_fork" + forkIndex + ".json")
                    .toString();
            builder.resultFormat(ResultFormatType.JSON).result(resultPath);
        }

        var runResults = new Runner(builder.build()).run();
        return runResults.iterator().next().getPrimaryResult().getScore();
    }

    private boolean isConverged(List<Double> throughputs) {
        return throughputs.size() >= convergenceWindow && computeCoV(throughputs) <= convergenceThresholdPercent;
    }

    private double computeCoV(List<Double> throughputs) {
        var windowStart = Math.max(0, throughputs.size() - convergenceWindow);
        var recent      = throughputs.subList(windowStart, throughputs.size());
        var mean        = recent.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        if (mean <= 0 || recent.size() < 2) {
            return Double.MAX_VALUE;
        }
        var sumSquaredDeviation = recent.stream().mapToDouble(value -> (value - mean) * (value - mean)).sum();
        var standardDeviation   = Math.sqrt(sumSquaredDeviation / (recent.size() - 1));
        return (standardDeviation / mean) * 100.0;
    }

    private void printResults(List<Double> forkThroughputs, PrintWriter out) {
        var mean                   = forkThroughputs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        var count                  = forkThroughputs.size();
        var sumSquaredDeviation    = forkThroughputs.stream().mapToDouble(value -> (value - mean) * (value - mean))
                .sum();
        var standardDeviation      = count > 1 ? Math.sqrt(sumSquaredDeviation / (count - 1)) : 0.0;
        var coefficientOfVariation = mean > 0 ? (standardDeviation / mean) * 100.0 : 0.0;
        var confidenceInterval95   = count > 1 ? tCritical95(count - 1) * standardDeviation / Math.sqrt(count) : 0.0;

        out.println();
        out.println("Result:");
        out.println(String.format(Locale.US, "  Mean:    %,.0f ops/s", mean));
        out.println(String.format(Locale.US, "  StdDev:  %,.0f ops/s", standardDeviation));
        out.println(String.format(Locale.US, "  CoV:     %.2f%%", coefficientOfVariation));
        out.println(String.format(Locale.US, "  95%% CI:  +/- %,.0f ops/s (t-distribution, df=%d)",
                confidenceInterval95, count - 1));
        out.println(String.format(Locale.US, "  Forks:   %d", count));
        out.println(String.format(Locale.US, "  Per fork: %s", forkThroughputs.stream()
                .map(value -> String.format(Locale.US, "%,.0f", value)).collect(Collectors.joining(", "))));
        out.flush();
    }

    private void writeResults(List<Double> forkThroughputs, PrintWriter out) {
        try {
            Files.createDirectories(output);
            var csvPath = output.resolve(scenario + "_" + method + "_" + threads + "t.csv");
            var csv     = new StringBuilder();
            csv.append("# SAPL 3.0 Benchmark Results\n");
            csv.append("# Command: ").append(commandLine).append('\n');
            csv.append("# JVM: ").append(System.getProperty("java.version", PROPERTY_UNKNOWN)).append('\n');
            csv.append("# Scenario: ").append(scenario).append('\n');
            csv.append("# Method: ").append(method).append('\n');
            csv.append("# Threads: ").append(threads).append('\n');
            csv.append("# Warmup: ").append(warmupIterations).append(" x ").append(warmupTimeSeconds).append("s\n");
            csv.append("# Measurement: ").append(measurementTimeSeconds).append("s\n");
            csv.append("# Convergence: CoV < ").append(convergenceThresholdPercent).append("% over ")
                    .append(convergenceWindow).append(" forks\n");
            if (metadata != null) {
                csv.append("# Metadata: ").append(metadata).append('\n');
            }
            csv.append("fork,throughput_ops_s\n");
            for (int i = 0; i < forkThroughputs.size(); i++) {
                csv.append(String.format(Locale.US, "%d,%.2f%n", i + 1, forkThroughputs.get(i)));
            }
            Files.writeString(csvPath, csv.toString());
            out.println("  Output:  " + csvPath);
        } catch (Exception exception) {
            out.println("Warning: failed to write results: " + exception.getClass().getName() + ": "
                    + exception.getMessage());
        }
    }

    private static double tCritical95(int degreesOfFreedom) {
        if (degreesOfFreedom <= 0) {
            return 0.0;
        }
        if (degreesOfFreedom <= T_CRITICAL_95.length) {
            return T_CRITICAL_95[degreesOfFreedom - 1];
        }
        return 1.96;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new Sapl3Benchmark()).execute(args));
    }

}
