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

import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.node.cli.benchmark.*;
import io.sapl.node.cli.options.BenchmarkOptions;
import io.sapl.node.cli.options.BundleVerificationOptions;
import io.sapl.node.cli.options.PolicySourceOptions;
import io.sapl.node.cli.options.SubscriptionInputOptions;
import io.sapl.node.cli.support.PolicySourceResolver;
import io.sapl.node.cli.support.SubscriptionResolver;
import lombok.val;
import picocli.CommandLine.*;
import picocli.CommandLine.Model.CommandSpec;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Quick assessment tool for embedded PDP evaluation performance. Uses a
 * built-in timing harness. For rigorous benchmarks, use the
 * sapl-benchmark-sapl4 module. For remote server load testing, use
 * {@code sapl loadtest}.
 */
// @formatter:off
@Command(
    name = "benchmark",
    mixinStandardHelpOptions = true,
    header = "Benchmark embedded PDP evaluation performance.",
    description = { """
        Quick assessment of policy evaluation throughput and latency for
        an embedded PDP using a built-in timing harness.

        Use --rbac for a self-contained benchmark without policy files,
        or provide a policy directory (--dir) or bundle (--bundle).

        When --output is specified, produces Markdown and CSV reports
        with timestamped filenames.

        For rigorous benchmarks with proper JIT isolation,
        use the sapl-benchmark-sapl4 module instead.

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
          # Built-in RBAC benchmark (no files needed)
          sapl benchmark --rbac -o ./results

          # Quick benchmark with local policies
          sapl benchmark --dir ./policies -s '"alice"' -a '"read"' -r '"doc"'

          # Multi-threaded benchmark with config file
          sapl benchmark --rbac -c configs/standard.json -o ./results

        See Also: sapl-loadtest(1), sapl-check(1), sapl-decide-once(1)
        """ }
)
// @formatter:on
public class BenchmarkCommand implements Callable<Integer> {

    static final String ERROR_OUTPUT_DIR_CREATION  = "Error: Could not create output directory: %s.";
    static final String ERROR_RBAC_CONFLICT        = "Error: --rbac cannot be combined with --dir, --bundle, or subscription options.";
    static final String ERROR_SUBSCRIPTION_MISSING = "Error: Subscription is required. Use -s/-a/-r, -f, or --rbac.";

    @Spec
    CommandSpec spec;

    @Option(names = "--rbac", description = "Use built-in RBAC benchmark (no policy files or subscription needed).")
    boolean rbac;

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
            val ctx = resolveContext(err);
            if (ctx == null) {
                return 1;
            }

            val runCfg = BenchmarkRunConfig.resolve(benchmarkOptions);

            if (runCfg.output() != null) {
                Files.createDirectories(runCfg.output());
            }

            val allResults = runAllBenchmarks(ctx, runCfg, out, err);
            if (allResults.isEmpty()) {
                return 1;
            }

            if (runCfg.output() != null && !runCfg.machineReadable()) {
                BenchmarkReportWriter.writeReports(allResults, ctx, runCfg, "timing loop", runCfg.output(), err);
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

    private BenchmarkContext resolveContext(PrintWriter err) {
        if (rbac) {
            if (policySource != null || subscriptionInput != null) {
                err.println(ERROR_RBAC_CONFLICT);
                return null;
            }
            return BenchmarkContext.rbacDefault();
        }
        if (subscriptionInput == null) {
            err.println(ERROR_SUBSCRIPTION_MISSING);
            return null;
        }
        val mapper       = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
        val subscription = SubscriptionResolver.resolve(subscriptionInput, mapper);
        val subJson      = mapper.writeValueAsString(subscription);
        val resolved     = PolicySourceResolver.resolve(policySource, bundleVerification, null, err);
        if (resolved == null) {
            return null;
        }
        String subsJson = loadSubscriptionsJson(resolved.path());
        return new BenchmarkContext(subJson, subsJson, resolved.path(), resolved.configType().name());
    }

    private List<BenchmarkResult> runAllBenchmarks(BenchmarkContext ctx, BenchmarkRunConfig runCfg, PrintWriter out,
            PrintWriter err) {
        val allResults = new ArrayList<BenchmarkResult>();
        for (int threads : runCfg.threads()) {
            err.println("Running with %d thread(s)...".formatted(threads));
            val threadResults = EmbeddedBenchmarkRunner.run(ctx, runCfg, threads, out, err);
            if (threadResults.isEmpty()) {
                return List.of();
            }
            allResults.addAll(threadResults);
        }
        return allResults;
    }

    private static String loadSubscriptionsJson(String policyPath) {
        if (policyPath == null) {
            return null;
        }
        val subscriptionsFile = java.nio.file.Path.of(policyPath).resolve("subscriptions.json");
        try {
            if (Files.exists(subscriptionsFile)) {
                return Files.readString(subscriptionsFile);
            }
        } catch (IOException e) {
            // fall through to single subscription
        }
        return null;
    }
}
