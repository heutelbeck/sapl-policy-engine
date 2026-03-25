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

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.node.cli.benchmark.BenchmarkConfig;
import io.sapl.node.cli.benchmark.BenchmarkContext;
import io.sapl.node.cli.benchmark.BenchmarkReportWriter;
import io.sapl.node.cli.benchmark.BenchmarkResult;
import io.sapl.node.cli.benchmark.BenchmarkRunConfig;
import io.sapl.node.cli.benchmark.JmhBenchmarkRunner;
import io.sapl.node.cli.benchmark.NativeBenchmarkRunner;
import io.sapl.node.cli.options.BenchmarkOptions;
import io.sapl.node.cli.options.BundleVerificationOptions;
import io.sapl.node.cli.options.PolicySourceOptions;
import io.sapl.node.cli.options.SubscriptionInputOptions;
import io.sapl.node.cli.support.PolicySourceResolver;
import io.sapl.node.cli.support.SubscriptionResolver;
import lombok.val;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;
import tools.jackson.databind.json.JsonMapper;

/**
 * Benchmarks embedded PDP evaluation performance using JMH (JAR mode) or a
 * built-in timing harness (native image mode). For remote server load
 * testing, use {@code sapl loadtest} instead.
 */
// @formatter:off
@Command(
    name = "benchmark",
    mixinStandardHelpOptions = true,
    header = "Benchmark embedded PDP evaluation performance.",
    description = { """
        Measures policy evaluation throughput and per-request latency
        distribution for an embedded PDP. Uses JMH when running from a JAR,
        or a built-in timing harness when running as a native binary.

        Requires a policy directory (--dir) or bundle (--bundle) containing
        the policies to evaluate.

        Latency percentiles (p50, p90, p99, p99.9) are measured per-request
        for blocking methods via a separate JMH SampleTime pass.

        When --output is specified, produces JSON (JMH-compatible), Markdown
        (with methodology, throughput, latency, and scaling tables), and CSV
        files with timestamped filenames.

        For reproducible runs with multiple thread counts, provide a JSON
        config file with --config.

        For remote server load testing (HTTP or RSocket), use
        'sapl loadtest' instead.
        """ },
    exitCodeListHeading = "%nExit Codes:%n",
    exitCodeList = {
        " 0:Benchmark completed successfully",
        " 1:Error during benchmark"
    },
    footerHeading = "%nExamples:%n",
    footer = { """
          # Quick benchmark with local policies
          sapl benchmark --dir ./policies -s '"alice"' -a '"read"' -r '"doc"'

          # Longer run with more iterations and output
          sapl benchmark --dir ./policies -s '"alice"' -a '"read"' -r '"doc"' --warmup-iterations 5 --warmup-time 5 --measurement-iterations 10 --measurement-time 10 -o ./results

          # Multi-threaded benchmark
          sapl benchmark --dir ./policies -s '"alice"' -a '"read"' -r '"doc"' -t 4

        See Also: sapl-loadtest(1), sapl-check(1), sapl-decide-once(1)
        """ }
)
// @formatter:on
public class BenchmarkCommand implements Callable<Integer> {

    static final String ERROR_OUTPUT_DIR_CREATION  = "Error: Could not create output directory: %s";
    static final String ERROR_SUBSCRIPTION_MISSING = "Error: Subscription is required. Use -s/-a/-r or -f.";

    @Spec
    CommandSpec spec;

    @ArgGroup(exclusive = true, heading = "%nPolicy Source:%n")
    PolicySourceOptions policySource;

    @ArgGroup(exclusive = true, heading = "%nBundle Verification:%n")
    BundleVerificationOptions bundleVerification;

    @ArgGroup(exclusive = true, multiplicity = "0..1", heading = "%nSubscription Input:%n")
    SubscriptionInputOptions subscriptionInput;

    @Mixin
    public BenchmarkOptions benchmarkOptions;

    /**
     * Runs the embedded PDP benchmark and writes results.
     *
     * @return 0 on success, 1 on error
     */
    @Override
    public Integer call() {
        val err = spec.commandLine().getErr();
        val out = spec.commandLine().getOut();
        try {
            if (subscriptionInput == null) {
                err.println(ERROR_SUBSCRIPTION_MISSING);
                return 1;
            }

            val mapper       = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
            val subscription = SubscriptionResolver.resolve(subscriptionInput, mapper);
            val subJson      = mapper.writeValueAsString(subscription);
            val ctx          = buildContext(subJson, err);

            if (ctx == null) {
                return 1;
            }

            val config = benchmarkOptions.configFile != null ? BenchmarkConfig.load(benchmarkOptions.configFile) : null;
            val runCfg = BenchmarkRunConfig.resolve(benchmarkOptions, config);

            if (runCfg.output() != null) {
                Files.createDirectories(runCfg.output());
            }

            val estimatedSeconds = runCfg.estimatedDurationSeconds();
            err.println("Estimated duration: %d:%02d".formatted(estimatedSeconds / 60, estimatedSeconds % 60));

            val allResults = runAllBenchmarks(ctx, runCfg, out, err);
            if (allResults.isEmpty()) {
                return 1;
            }

            if (runCfg.output() != null) {
                val runner = isNativeImage() ? "native (AOT)" : "JVM (JMH)";
                BenchmarkReportWriter.writeReports(allResults, ctx, runCfg, runner, runCfg.output(), err);
            }
            return 0;
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return 1;
        } catch (IOException e) {
            err.println(ERROR_OUTPUT_DIR_CREATION.formatted(e.getMessage()));
            return 1;
        }
    }

    private BenchmarkContext buildContext(String subJson, PrintWriter err) {
        val resolved = PolicySourceResolver.resolve(policySource, bundleVerification, null, err);
        if (resolved == null) {
            return null;
        }
        return new BenchmarkContext(subJson, resolved.path(), resolved.configType().name());
    }

    private List<BenchmarkResult> runAllBenchmarks(BenchmarkContext ctx, BenchmarkRunConfig runCfg, PrintWriter out,
            PrintWriter err) {
        val allResults = new ArrayList<BenchmarkResult>();
        for (int threads : runCfg.threads()) {
            err.println("Running with %d thread(s)...".formatted(threads));
            val threadResults = isNativeImage() ? NativeBenchmarkRunner.run(ctx, runCfg, threads, out, err)
                    : JmhBenchmarkRunner.run(ctx, runCfg, threads, out, err);
            if (threadResults.isEmpty()) {
                return List.of();
            }
            allResults.addAll(threadResults);
        }
        return allResults;
    }

    private static boolean isNativeImage() {
        try {
            val clazz = Class.forName("org.graalvm.nativeimage.ImageInfo");
            return (boolean) clazz.getMethod("inImageCode").invoke(null);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
