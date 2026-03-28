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
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Callable;

import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.proto.SaplProtobufCodec;
import io.sapl.node.cli.benchmark.BenchmarkReportWriter;
import io.sapl.node.cli.benchmark.BenchmarkResult;
import io.sapl.node.cli.benchmark.HttpLoadGenerator;
import io.sapl.node.cli.benchmark.LoadtestContext;
import io.sapl.node.cli.benchmark.RSocketLoadGenerator;
import io.sapl.node.cli.options.SubscriptionInputOptions;
import io.sapl.node.cli.support.SubscriptionResolver;
import lombok.val;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import tools.jackson.databind.json.JsonMapper;

/**
 * Load tests a running SAPL PDP server via HTTP or RSocket. Measures server
 * throughput and per-request latency distribution under controlled
 * concurrency. For embedded PDP benchmarking, use {@code sapl benchmark}
 * instead.
 */
// @formatter:off
@Command(
    name = "loadtest",
    mixinStandardHelpOptions = true,
    header = "Load test a running SAPL PDP server.",
    description = { """
        Measures server throughput and per-request latency distribution
        under controlled concurrency.

        HTTP mode uses the JDK HttpClient with async request chaining.
        RSocket mode uses virtual threads with blocking request-response
        on multiplexed connections.

        Both modes pre-serialize the request payload to eliminate
        client-side overhead from the measurement.

        For embedded PDP benchmarking, use 'sapl benchmark' instead.
        """ },
    exitCodeListHeading = "%nExit Codes:%n",
    exitCodeList = {
        " 0:Load test completed successfully",
        " 1:Error during load test"
    },
    footerHeading = "%nExamples:%n",
    footer = { """
          # HTTP load test against a running server
          sapl loadtest --url http://localhost:8443 -s '{"role":"admin"}' -a '"read"' -r '"doc"'

          # RSocket load test
          sapl loadtest --rsocket --host localhost --port 7000 -s '{"role":"admin"}' -a '"read"' -r '"doc"'

          # With custom concurrency and output
          sapl loadtest --url http://localhost:8443 --concurrency 128 --measurement-seconds 30 -o ./results -s '"alice"' -a '"read"' -r '"doc"'

          # RSocket with connection tuning
          sapl loadtest --rsocket --connections 8 --vt-per-connection 512 -s '"alice"' -a '"read"' -r '"doc"'

        See Also: sapl-benchmark(1)
        """ }
)
// @formatter:on
public class LoadtestCommand implements Callable<Integer> {

    static final String ERROR_OUTPUT_DIR_CREATION  = "Error: Could not create output directory: %s.";
    static final String ERROR_SUBSCRIPTION_MISSING = "Error: Subscription is required. Use -s/-a/-r or -f.";

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Spec
    CommandSpec spec;

    @Option(names = "--url", defaultValue = "http://localhost:8443", description = "HTTP server URL (default: ${DEFAULT-VALUE})")
    String url;

    @Option(names = "--rsocket", description = "Use RSocket/protobuf transport instead of HTTP")
    boolean rsocket;

    @Option(names = "--host", defaultValue = "localhost", description = "RSocket server host (default: ${DEFAULT-VALUE})")
    String rsocketHost;

    @Option(names = "--port", defaultValue = "7000", description = "RSocket server port (default: ${DEFAULT-VALUE})")
    int rsocketPort;

    @Option(names = "--concurrency", defaultValue = "64", description = "Concurrent in-flight requests for HTTP (default: ${DEFAULT-VALUE})")
    int concurrency;

    @Option(names = "--connections", defaultValue = "8", description = "Number of TCP connections for RSocket (default: ${DEFAULT-VALUE})")
    int connections;

    @Option(names = "--vt-per-connection", defaultValue = "512", description = "Virtual threads per RSocket connection (default: ${DEFAULT-VALUE})")
    int vtPerConnection;

    @Option(names = "--warmup-seconds", defaultValue = "5", description = "Warmup duration in seconds (default: ${DEFAULT-VALUE})")
    int warmupSeconds;

    @Option(names = "--measurement-seconds", defaultValue = "10", description = "Measurement duration in seconds (default: ${DEFAULT-VALUE})")
    int measureSeconds;

    @Option(names = { "-o", "--output" }, description = "Output directory for results (Markdown, CSV)")
    Path output;

    @Option(names = "--label", description = "Label for the report (e.g., 'Server pinned to CPUs 0-7')")
    String label;

    @Option(names = "--machine-readable", defaultValue = "false", description = "Output single-line parseable results for script integration")
    boolean machineReadable;

    @ArgGroup(exclusive = true, multiplicity = "0..1", heading = "%nSubscription Input:%n")
    SubscriptionInputOptions subscriptionInput;

    /**
     * Runs the load test and writes results.
     *
     * @return 0 on success, 1 on error
     */
    @Override
    public Integer call() {
        val out = spec.commandLine().getOut();
        val err = spec.commandLine().getErr();
        try {
            if (subscriptionInput == null) {
                err.println(ERROR_SUBSCRIPTION_MISSING);
                return 1;
            }

            val mapper       = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
            val subscription = SubscriptionResolver.resolve(subscriptionInput, mapper);
            val timestamp    = LocalDateTime.now().format(TIMESTAMP_FORMAT);

            if (output != null) {
                Files.createDirectories(output);
            }

            BenchmarkResult result;
            LoadtestContext ctx;

            val progressOut = machineReadable ? new PrintWriter(OutputStream.nullOutputStream()) : out;

            if (rsocket) {
                val protoPayload = SaplProtobufCodec.writeAuthorizationSubscription(subscription);
                ctx = LoadtestContext.rsocket(rsocketHost, rsocketPort, connections, vtPerConnection, warmupSeconds,
                        measureSeconds, timestamp, label);
                if (!machineReadable) {
                    out.println("RSocket load test: rsocket://%s:%d".formatted(rsocketHost, rsocketPort));
                }
                result = RSocketLoadGenerator.run(rsocketHost, rsocketPort, protoPayload, connections, vtPerConnection,
                        warmupSeconds, measureSeconds, progressOut);
            } else {
                val body = mapper.writeValueAsString(subscription).getBytes(StandardCharsets.UTF_8);
                ctx = LoadtestContext.http(url, concurrency, warmupSeconds, measureSeconds, timestamp, label);
                if (!machineReadable) {
                    out.println("HTTP load test: %s".formatted(url));
                }
                result = HttpLoadGenerator.run(url, body, concurrency, warmupSeconds, measureSeconds, progressOut);
            }

            val results = new ArrayList<BenchmarkResult>();
            results.add(result);

            if (machineReadable) {
                printMachineReadable(result, out);
            } else {
                printLatency(result, out);
            }

            if (output != null) {
                BenchmarkReportWriter.writeLoadtestReports(results, ctx, output, err);
                if (!machineReadable) {
                    out.println("Results written to: " + output);
                }
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

    private static void printMachineReadable(BenchmarkResult result, PrintWriter out) {
        out.println(String.format(Locale.US, "THROUGHPUT:%.2f", result.mean()));
        val latency = result.latency();
        if (latency != null) {
            out.println(String.format(Locale.US, "LATENCY:%.0f:%.0f:%.0f:%.0f:%.0f", latency.p50(), latency.p90(),
                    latency.p99(), latency.p999(), latency.max()));
        }
        out.flush();
    }

    private static void printLatency(BenchmarkResult result, PrintWriter out) {
        val latency = result.latency();
        if (latency == null) {
            return;
        }
        out.println("  Latency:");
        out.printf(Locale.US, "    p50:   %,.0f us%n", latency.p50() / 1000.0);
        out.printf(Locale.US, "    p90:   %,.0f us%n", latency.p90() / 1000.0);
        out.printf(Locale.US, "    p99:   %,.0f us%n", latency.p99() / 1000.0);
        out.printf(Locale.US, "    p99.9: %,.0f us%n", latency.p999() / 1000.0);
        out.printf(Locale.US, "    max:   %,.0f us%n", latency.max() / 1000.0);
        out.flush();
    }
}
