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
package io.sapl.benchmark.sapl4;

import io.sapl.api.model.Value;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.pdp.Decision;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Rigorous JMH benchmark runner for SAPL 4.0. Uses a multi-level
 * experiment design: each fork is an independent JVM process (Level 2), the
 * measurement within each fork is Level 1. Convergence is checked across
 * fork-level throughput values using the coefficient of variation (CoV)
 * heuristic from Georges et al. (OOPSLA 2007). Confidence intervals use the
 * t-distribution, not z, because the sample size (number of forks) is small.
 * <p>
 * Each invocation measures one method, one scenario, one thread count. Shell
 * scripts orchestrate multiple invocations for sweeps across scenarios,
 * methods, and thread counts.
 */
@Command(name = "sapl-benchmark-sapl4", mixinStandardHelpOptions = true, description = "SAPL 4.0 embedded PDP benchmark.")
class Sapl4Benchmark implements Callable<Integer> {

    private static final String ERROR_CONVERGENCE_FAILED = "FAILED: did not converge after %d forks (CoV %.2f%%, threshold %.1f%%).";
    private static final String ERROR_SANITY_CHECK       = "Sanity check failed: scenario '%s' produced %s but expected %s.";
    private static final String PROPERTY_UNKNOWN         = "unknown";
    private static final String ERROR_UNKNOWN_METHOD     = "Unknown method: %s. Valid methods: %s.";
    private static final String WARN_WRITE_FAILED        = "Warning: failed to write results: %s.";

    private static final Set<String> VALID_METHODS = Set.of("decideOnceBlocking", "decideStreamFirst", "noOp");

    // t-distribution critical values for 95% CI (two-tailed, alpha=0.05).
    // Index 0 = df=1, index 1 = df=2, etc. For df>30, z=1.96 is sufficient.
    private static final double[] T_CRITICAL_95 = { 12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262,
            2.228, 2.201, 2.179, 2.160, 2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086, 2.080, 2.074, 2.069, 2.064,
            2.060, 2.056, 2.052, 2.048, 2.045, 2.042 };

    @Spec
    CommandSpec spec;

    @Option(names = "--scenario", defaultValue = "baseline", description = "Benchmark scenario (use --help-scenarios to list).")
    private String scenario;

    @Option(names = "--seed", defaultValue = "42", description = "RNG seed for OOPSLA entity graph generation. Ignored for non-OOPSLA scenarios.")
    private long seed;

    @Option(names = "--indexing", defaultValue = "AUTO", description = "Indexing strategy: AUTO, NAIVE, CANONICAL.")
    private String indexing;

    @Option(names = "--unroll", defaultValue = "false", description = "Enable IN-operator unrolling for index matching.")
    private boolean unroll;

    @Option(names = "--method", defaultValue = "decideOnceBlocking", description = "Benchmark method: decideOnceBlocking, decideStreamFirst, noOp.")
    private String method;

    @Option(names = { "-t", "--threads" }, defaultValue = "1", description = "Number of concurrent benchmark threads.")
    private int threads;

    @Option(names = "--warmup-time", defaultValue = "45", description = "Seconds per warmup iteration inside each fork.")
    private int warmupTimeSeconds;

    @Option(names = "--warmup-iterations", defaultValue = "5", description = "Number of warmup iterations inside each fork.")
    private int warmupIterations;

    @Option(names = "--measurement-time", defaultValue = "300", description = "Seconds for the single measurement window per fork.")
    private int measurementTimeSeconds;

    @Option(names = "--convergence-threshold", defaultValue = "2", description = "Maximum CoV (percent) across the convergence window for acceptance.")
    private double convergenceThresholdPercent;

    @Option(names = "--convergence-window", defaultValue = "3", description = "Number of consecutive fork results that must be within the threshold.")
    private int convergenceWindow;

    @Option(names = "--max-forks", defaultValue = "10", description = "Maximum forks before failing if convergence is not reached.")
    private int maxForks;

    @Option(names = "--latency", defaultValue = "false", description = "Run a SampleTime latency pass after throughput measurement.")
    private boolean latency;

    @Option(names = "--latency-only", defaultValue = "false", description = "Run only the SampleTime latency pass, skip throughput convergence forks.")
    private boolean latencyOnly;

    @Option(names = "--gc", description = "GC algorithm (e.g., G1, Shenandoah, ZGC). Passed as -XX:+Use<name> to the forked JVM.")
    private String gc;

    @Option(names = "--virtual-threads", defaultValue = "false", description = "Enable virtual threads for Reactor's boundedElastic scheduler.")
    private boolean virtualThreads;

    @Option(names = "--parallel-pool-size", description = "Reactor parallel scheduler pool size (default: availableProcessors).")
    private Integer parallelPoolSize;

    @Option(names = "--heap", defaultValue = "32g", description = "Heap size for the forked JVM (e.g., 32g, 16g, 4g).")
    private String heap;

    @Option(names = "--jvm-args", description = "Additional JVM arguments passed to the forked JVM (comma-separated).")
    private String jvmArgs;

    @Option(names = "--metadata", description = "Free-form metadata string recorded in the output (e.g., pinning, frequency, cooling).")
    private String metadata;

    @Option(names = { "-o", "--output" }, description = "Output directory for results.")
    private Path output;

    @Option(names = "--export", description = "Export scenario policies, pdp.json, and subscription.json to a directory and exit.")
    private Path export;

    private String commandLine;

    /**
     * Validates inputs, runs fork-level convergence benchmarks, and reports
     * results.
     *
     * @return 0 on success, 1 on validation or convergence failure
     * @throws Exception if JMH runner fails
     */
    @Override
    public Integer call() throws Exception {
        var out = spec.commandLine().getOut();
        var err = spec.commandLine().getErr();

        commandLine = String.join(" ", spec.commandLine().getParseResult().originalArgs());

        if (export != null) {
            var resolvedScenario = ScenarioFactory.create(scenario, seed);
            ScenarioFactory.exportScenario(resolvedScenario, export, indexing);
            out.println("Exported scenario '" + scenario + "' to " + export);
            out.flush();
            return 0;
        }

        if (!validate(err)) {
            return 1;
        }

        if (output != null) {
            Files.createDirectories(output);
        }

        printHeader(out);

        List<Double>  forkResults   = List.of();
        LatencyResult latencyResult = null;

        if (latencyOnly) {
            latencyResult = runLatencyPass(out);
        } else {
            forkResults = runConvergenceForks(out, err);
            if (forkResults.isEmpty()) {
                return 1;
            }
            if (latency) {
                latencyResult = runLatencyPass(out);
            }
        }

        printResults(forkResults, latencyResult, out);

        if (output != null) {
            writeResults(forkResults, latencyResult, out);
        }

        return 0;
    }

    record LatencyResult(double p50, double p90, double p99, double p999, double max) {}

    private boolean validate(PrintWriter err) {
        if (!VALID_METHODS.contains(method)) {
            err.println(ERROR_UNKNOWN_METHOD.formatted(method, VALID_METHODS));
            return false;
        }
        try {
            var resolvedScenario = ScenarioFactory.create(scenario, seed);
            var flags            = ObjectValue.builder().put("indexing", Value.of(indexing.toUpperCase()))
                    .put("unrollInOperator", Value.of(unroll)).build();
            var components       = resolvedScenario.buildPdp(flags);
            var pdp              = components.pdp();
            var decision         = pdp.decideOnceBlocking(resolvedScenario.subscription());
            components.dispose();
            if (resolvedScenario.expectedDecision() != null
                    && decision.decision() != resolvedScenario.expectedDecision().decision()) {
                err.println(ERROR_SANITY_CHECK.formatted(scenario, decision, resolvedScenario.expectedDecision()));
                return false;
            }
        } catch (IllegalArgumentException exception) {
            err.println(exception.getMessage());
            return false;
        }
        return true;
    }

    private void printHeader(PrintWriter out) {
        var activeGc   = ManagementFactory.getGarbageCollectorMXBeans().stream().map(GarbageCollectorMXBean::getName)
                .collect(Collectors.joining(", "));
        var jvmVersion = System.getProperty("java.version", PROPERTY_UNKNOWN) + " ("
                + System.getProperty("java.vm.name", PROPERTY_UNKNOWN) + ")";

        out.println("SAPL 4.0 Benchmark");
        out.println("  Command:     " + commandLine);
        out.println("  JVM:         " + jvmVersion);
        out.println("  Heap:        " + heap);
        out.println("  GC:          " + (gc != null ? gc + " (override)" : activeGc));
        out.println("  Virtual thr: " + virtualThreads);
        out.println("  Pool size:   " + (parallelPoolSize != null ? parallelPoolSize
                : "default (" + Runtime.getRuntime().availableProcessors() + ")"));
        out.println("  Scenario:    " + scenario);
        out.println("  Seed:        " + seed);
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

    private List<Double> runConvergenceForks(PrintWriter out, PrintWriter err) throws RunnerException {
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
        var builder = baseOptions(includePattern).mode(Mode.Throughput).timeUnit(TimeUnit.SECONDS);

        buildJvmArgs(builder);

        if (output != null) {
            var resultPath = output
                    .resolve(baseFileName() + "_" + method + "_" + threads + "t_fork" + forkIndex + ".json").toString();
            builder.resultFormat(ResultFormatType.JSON).result(resultPath);
        }

        var runResults = new Runner(builder.build()).run();
        if (runResults.isEmpty()) {
            throw new RunnerException("Fork " + forkIndex + " produced no results (forked VM may have failed).");
        }
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

    private void printResults(List<Double> forkThroughputs, LatencyResult latencyResult, PrintWriter out) {
        out.println();
        out.println("Result:");
        if (!forkThroughputs.isEmpty()) {
            var mean                   = forkThroughputs.stream().mapToDouble(Double::doubleValue).average()
                    .orElse(0.0);
            var count                  = forkThroughputs.size();
            var sumSquaredDeviation    = forkThroughputs.stream().mapToDouble(value -> (value - mean) * (value - mean))
                    .sum();
            var standardDeviation      = count > 1 ? Math.sqrt(sumSquaredDeviation / (count - 1)) : 0.0;
            var coefficientOfVariation = mean > 0 ? (standardDeviation / mean) * 100.0 : 0.0;
            var confidenceInterval95   = count > 1 ? tCritical95(count - 1) * standardDeviation / Math.sqrt(count)
                    : 0.0;
            out.println(String.format(Locale.US, "  Mean:    %,.0f ops/s", mean));
            out.println(String.format(Locale.US, "  StdDev:  %,.0f ops/s", standardDeviation));
            out.println(String.format(Locale.US, "  CoV:     %.2f%%", coefficientOfVariation));
            out.println(String.format(Locale.US, "  95%% CI:  +/- %,.0f ops/s (t-distribution, df=%d)",
                    confidenceInterval95, count - 1));
            out.println(String.format(Locale.US, "  Forks:   %d", count));
            out.println(String.format(Locale.US, "  Per fork: %s", forkThroughputs.stream()
                    .map(value -> String.format(Locale.US, "%,.0f", value)).collect(Collectors.joining(", "))));
        }
        if (latencyResult != null) {
            out.println(String.format(Locale.US,
                    "  Latency: p50=%.0f ns  p90=%.0f ns  p99=%.0f ns  p99.9=%.0f ns  max=%.0f ns", latencyResult.p50(),
                    latencyResult.p90(), latencyResult.p99(), latencyResult.p999(), latencyResult.max()));
        }
        out.flush();
    }

    private void writeResults(List<Double> forkThroughputs, LatencyResult latencyResult, PrintWriter out) {
        try {
            Files.createDirectories(output);
            var csvPath = output.resolve(baseFileName() + "_" + method + "_" + threads + "t.csv");
            var csv     = new StringBuilder();
            csv.append("# SAPL 4.0 Benchmark Results\n");
            csv.append("# Command: ").append(commandLine).append('\n');
            csv.append("# JVM: ").append(System.getProperty("java.version", PROPERTY_UNKNOWN)).append(" (")
                    .append(System.getProperty("java.vm.name", PROPERTY_UNKNOWN)).append(")\n");
            csv.append("# GC: ").append(gc != null ? gc : "default").append('\n');
            csv.append("# Scenario: ").append(scenario).append('\n');
            csv.append("# Seed: ").append(seed).append('\n');
            csv.append("# Method: ").append(method).append('\n');
            csv.append("# Threads: ").append(threads).append('\n');
            csv.append("# Warmup: ").append(warmupIterations).append(" x ").append(warmupTimeSeconds).append("s\n");
            csv.append("# Measurement: ").append(measurementTimeSeconds).append("s\n");
            csv.append("# Convergence: CoV < ").append(convergenceThresholdPercent).append("% over ")
                    .append(convergenceWindow).append(" forks\n");
            csv.append("# Virtual threads: ").append(virtualThreads).append('\n');
            if (parallelPoolSize != null) {
                csv.append("# Parallel pool size: ").append(parallelPoolSize).append('\n');
            }
            if (metadata != null) {
                csv.append("# Metadata: ").append(metadata).append('\n');
            }
            if (latencyResult != null) {
                csv.append("# Latency p50 (ns): ").append(String.format(Locale.US, "%.0f", latencyResult.p50()))
                        .append('\n');
                csv.append("# Latency p90 (ns): ").append(String.format(Locale.US, "%.0f", latencyResult.p90()))
                        .append('\n');
                csv.append("# Latency p99 (ns): ").append(String.format(Locale.US, "%.0f", latencyResult.p99()))
                        .append('\n');
                csv.append("# Latency p99.9 (ns): ").append(String.format(Locale.US, "%.0f", latencyResult.p999()))
                        .append('\n');
                csv.append("# Latency max (ns): ").append(String.format(Locale.US, "%.0f", latencyResult.max()))
                        .append('\n');
            }
            var decisions = countDecisions();
            csv.append("# Decisions PERMIT: ").append(decisions.get(Decision.PERMIT)).append('\n');
            csv.append("# Decisions DENY: ").append(decisions.get(Decision.DENY)).append('\n');
            csv.append("# Decisions INDETERMINATE: ").append(decisions.get(Decision.INDETERMINATE)).append('\n');
            csv.append("# Decisions NOT_APPLICABLE: ").append(decisions.get(Decision.NOT_APPLICABLE)).append('\n');
            csv.append("fork,throughput_ops_s\n");
            for (int i = 0; i < forkThroughputs.size(); i++) {
                csv.append(String.format(Locale.US, "%d,%.2f%n", i + 1, forkThroughputs.get(i)));
            }
            Files.writeString(csvPath, csv.toString());
            out.println("  Output:  " + csvPath);
        } catch (IOException exception) {
            out.println(WARN_WRITE_FAILED.formatted(exception.getClass().getName() + ": " + exception.getMessage()));
        }
    }

    private LatencyResult runLatencyPass(PrintWriter out) throws RunnerException {
        out.println();
        out.println("Running latency pass (SampleTime)...");
        out.flush();

        var includePattern = EmbeddedPdpBenchmark.class.getName() + "\\." + method;

        var builder = baseOptions(includePattern).mode(Mode.SampleTime).timeUnit(TimeUnit.NANOSECONDS);

        buildJvmArgs(builder);

        if (output != null) {
            var resultPath = output.resolve(baseFileName() + "_" + method + "_" + threads + "t_latency.json")
                    .toString();
            builder.resultFormat(ResultFormatType.JSON).result(resultPath);
        }

        var runResults = new Runner(builder.build()).run();
        var result     = runResults.iterator().next().getPrimaryResult();
        var statistics = result.getStatistics();
        return new LatencyResult(statistics.getPercentile(50.0), statistics.getPercentile(90.0),
                statistics.getPercentile(99.0), statistics.getPercentile(99.9), statistics.getMax());
    }

    private ChainedOptionsBuilder baseOptions(String includePattern) {
        return new OptionsBuilder().include(includePattern).forks(1).warmupIterations(warmupIterations)
                .warmupTime(TimeValue.seconds(warmupTimeSeconds)).measurementIterations(1)
                .measurementTime(TimeValue.seconds(measurementTimeSeconds)).threads(threads)
                .param("scenarioName", scenario).param("indexingStrategy", indexing)
                .param("unrollInOperator", String.valueOf(unroll)).param("seed", String.valueOf(seed)).shouldDoGC(true)
                .syncIterations(true);
    }

    private void buildJvmArgs(ChainedOptionsBuilder builder) {
        var args = new ArrayList<String>();
        args.add("-Xmx" + heap);
        args.add("-Dorg.slf4j.simpleLogger.defaultLogLevel=WARN");
        if (gc != null) {
            args.add("-XX:+Use" + gc);
        }
        if (virtualThreads) {
            args.add("-Dreactor.schedulers.defaultBoundedElasticOnVirtualThreads=true");
        }
        if (parallelPoolSize != null) {
            args.add("-Dreactor.schedulers.defaultPoolSize=" + parallelPoolSize);
        }
        appendExtraJvmArgs(args);
        if (!args.isEmpty()) {
            builder.jvmArgsAppend(args.toArray(String[]::new));
        }
    }

    private void appendExtraJvmArgs(List<String> args) {
        if (jvmArgs != null) {
            for (var arg : jvmArgs.split(",")) {
                args.add(arg.trim());
            }
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

    private String baseFileName() {
        var gcLabel     = gc != null ? "_" + gc : "";
        var unrollLabel = unroll ? "_unroll" : "";
        return scenario + "_seed" + seed + gcLabel + "_" + indexing + unrollLabel;
    }

    private Map<Decision, Integer> countDecisions() {
        var resolvedScenario = ScenarioFactory.create(scenario, seed);
        var flags            = ObjectValue.builder().put("indexing", Value.of(indexing.toUpperCase()))
                .put("unrollInOperator", Value.of(unroll)).build();
        var components       = resolvedScenario.buildPdp(flags);
        var pdp              = components.pdp();
        var counts           = new EnumMap<Decision, Integer>(Decision.class);
        for (var d : Decision.values()) {
            counts.put(d, 0);
        }
        for (var sub : resolvedScenario.subscriptions()) {
            var decision = pdp.decideOnceBlocking(sub).decision();
            counts.merge(decision, 1, Integer::sum);
        }
        components.dispose();
        return counts;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new Sapl4Benchmark()).execute(args));
    }

}
