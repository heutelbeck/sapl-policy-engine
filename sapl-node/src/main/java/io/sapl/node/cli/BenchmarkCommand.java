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
import java.nio.file.Files;
import java.util.concurrent.Callable;

import io.sapl.api.model.jackson.SaplJacksonModule;
import lombok.val;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;
import tools.jackson.databind.json.JsonMapper;

/**
 * Benchmarks PDP evaluation performance using JMH (JAR mode) or a built-in
 * timing harness (native image mode).
 */
// @formatter:off
@Command(
    name = "benchmark",
    mixinStandardHelpOptions = true,
    header = "Benchmark PDP evaluation performance.",
    description = { """
        Measures policy evaluation throughput and latency. Uses the Java
        Microbenchmark Harness (JMH) when running from a JAR, or a built-in
        timing harness when running as a native binary.

        By default, benchmarks the embedded PDP directly. Use --dir to
        specify a policy directory, --bundle for a bundle file.

        For reproducible runs, adjust warmup and measurement parameters
        via CLI flags or provide a JSON config file with --config.
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

          # Longer run with more iterations
          sapl benchmark --dir ./policies -s '"alice"' -a '"read"' -r '"doc"' --warmup-iterations 5 --warmup-time 5 --measurement-iterations 10 --measurement-time 10

          # Multi-threaded benchmark with JSON output
          sapl benchmark --dir ./policies -s '"alice"' -a '"read"' -r '"doc"' -t 4 -o ./results

          # Read subscription from file
          sapl benchmark --dir ./policies -f subscription.json

        See Also: sapl-check(1), sapl-decide-once(1)
        """ }
)
// @formatter:on
class BenchmarkCommand implements Callable<Integer> {

    static final String ERROR_OUTPUT_DIR_CREATION  = "Error: Could not create output directory: %s";
    static final String ERROR_SUBSCRIPTION_MISSING = "Error: Subscription is required. Use -s/-a/-r or -f.";

    @Spec
    CommandSpec spec;

    @Mixin
    PdpOptions pdpOptions;

    @Mixin
    BenchmarkOptions benchmarkOptions;

    @Override
    public Integer call() {
        val err = spec.commandLine().getErr();
        val out = spec.commandLine().getOut();
        try {
            if (pdpOptions.subscriptionInput == null) {
                err.println(ERROR_SUBSCRIPTION_MISSING);
                return 1;
            }
            val resolved = PolicySourceResolver.resolve(pdpOptions.policySource, pdpOptions.bundleVerification,
                    pdpOptions.saplHomeOverride, err);
            if (resolved == null) {
                return 1;
            }
            val mapper       = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
            val subscription = SubscriptionResolver.resolve(pdpOptions.subscriptionInput, mapper);
            val subJson      = mapper.writeValueAsString(subscription);
            val ctx          = new BenchmarkContext(subJson, resolved.path(), resolved.configType().name());

            if (benchmarkOptions.output != null) {
                Files.createDirectories(benchmarkOptions.output);
            }

            if (isNativeImage()) {
                return NativeBenchmarkRunner.run(ctx, benchmarkOptions, out, err);
            }
            return JmhBenchmarkRunner.run(ctx, benchmarkOptions, out, err);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return 1;
        } catch (IOException e) {
            err.println(ERROR_OUTPUT_DIR_CREATION.formatted(e.getMessage()));
            return 1;
        }
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
