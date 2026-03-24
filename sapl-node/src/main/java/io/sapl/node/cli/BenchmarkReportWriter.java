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
import java.util.Locale;

import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Writes benchmark results in Markdown and CSV formats. Produces
 * self-contained reports suitable for thesis appendices. Tables use
 * fixed-width columns for readability in both rendered Markdown and
 * plaintext.
 */
@UtilityClass
class BenchmarkReportWriter {

    private static final String HEADER_METHOD  = "Method";
    private static final String HEADER_THREADS = "Threads";

    static final String WARN_REPORT_WRITE_FAILED = "Warning: Failed to write report: %s";

    static void writeReports(List<BenchmarkResult> results, BenchmarkContext ctx, BenchmarkRunConfig cfg, String runner,
            Path outputDir, PrintWriter err) {
        val mode     = ctx.isRemote() ? "remote" : "embedded";
        val baseName = cfg.timestamp() + "_" + mode + "_report";
        try {
            Files.writeString(outputDir.resolve(baseName + ".md"), buildMarkdown(results, ctx, cfg, runner));
            Files.writeString(outputDir.resolve(baseName + ".csv"), buildCsv(results));
        } catch (IOException e) {
            err.println(WARN_REPORT_WRITE_FAILED.formatted(e.getMessage()));
        }
    }

    static String buildMarkdown(List<BenchmarkResult> results, BenchmarkContext ctx, BenchmarkRunConfig cfg,
            String runner) {
        val sb = new StringBuilder();
        val mw = maxMethodWidth(results);

        sb.append("# Benchmark Report\n\n");
        appendMethodology(sb, ctx, cfg, runner);
        appendResultsTable(sb, results, mw);
        appendLatencyTable(sb, results, mw);
        appendScalingTable(sb, results, mw);
        return sb.toString();
    }

    static String buildCsv(List<BenchmarkResult> results) {
        val sb = new StringBuilder();
        sb.append(
                "method,threads,mean_ops_s,median_ops_s,stddev,cv_pct,min_ops_s,max_ops_s,p5_ops_s,p95_ops_s,mean_ns_op\n");
        for (val r : results) {
            val meanNs = r.mean() > 0 ? 1_000_000_000.0 / r.mean() : 0;
            sb.append(String.format(Locale.US, "%s,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f", r.method(),
                    r.threads(), r.mean(), r.median(), r.stddev(), r.cv(), r.min(), r.max(), r.p5(), r.p95(), meanNs))
                    .append('\n');
        }
        return sb.toString();
    }

    private static void appendMethodology(StringBuilder sb, BenchmarkContext ctx, BenchmarkRunConfig cfg,
            String runner) {
        sb.append("## Methodology\n\n");
        sb.append("| Parameter   | Value                  |\n");
        sb.append("|-------------|------------------------|\n");
        sb.append("| Runner      | %-22s |".formatted(runner)).append('\n');
        sb.append("| Mode        | %-22s |".formatted(ctx.isRemote() ? "remote" : "embedded")).append('\n');
        if (ctx.isRemote()) {
            sb.append("| Remote URL  | %-22s |".formatted(ctx.remoteUrl())).append('\n');
        } else {
            sb.append("| Policies    | %-22s |".formatted(ctx.policiesPath())).append('\n');
            sb.append("| Config type | %-22s |".formatted(ctx.configType())).append('\n');
        }
        sb.append("| Warmup      | %-22s |"
                .formatted("%d iter x %d s".formatted(cfg.warmupIterations(), cfg.warmupTimeSeconds()))).append('\n');
        sb.append("| Measurement | %-22s |"
                .formatted("%d iter x %d s".formatted(cfg.measurementIterations(), cfg.measurementTimeSeconds())))
                .append('\n');
        sb.append("| Timestamp   | %-22s |".formatted(cfg.timestamp())).append('\n');
        sb.append('\n');
    }

    private static void appendResultsTable(StringBuilder sb, List<BenchmarkResult> results, int mw) {
        sb.append("## Results\n\n");
        val fmt = "| %-" + mw + "s | %7s | %14s | %14s | %10s | %5s | %14s | %14s | %14s | %14s |";
        val sep = "| " + "-".repeat(mw) + " | ------: | -------------: | -------------: | ---------: | ----: "
                + "| -------------: | -------------: | -------------: | -------------: |\n";
        sb.append(String.format(fmt, HEADER_METHOD, HEADER_THREADS, "Mean (ops/s)", "Median (ops/s)", "StdDev", "CV%",
                "Min", "Max", "p5", "p95")).append('\n');
        sb.append(sep);
        for (val r : results) {
            sb.append(String.format(Locale.US, "| %-" + mw
                    + "s | %7d | %,14.0f | %,14.0f | %,10.0f | %4.1f%% | %,14.0f | %,14.0f | %,14.0f | %,14.0f |",
                    r.method(), r.threads(), r.mean(), r.median(), r.stddev(), r.cv(), r.min(), r.max(), r.p5(),
                    r.p95())).append('\n');
        }
        sb.append('\n');
    }

    private static void appendLatencyTable(StringBuilder sb, List<BenchmarkResult> results, int mw) {
        val measured = results.stream().anyMatch(r -> r.latency() != null);
        if (measured) {
            appendMeasuredLatencyTable(sb, results, mw);
        }
        appendDerivedLatencyTable(sb, results, mw);
    }

    private static void appendMeasuredLatencyTable(StringBuilder sb, List<BenchmarkResult> results, int mw) {
        val withLatency = results.stream().filter(r -> r.latency() != null).toList();
        if (withLatency.isEmpty()) {
            return;
        }
        sb.append("## Latency (measured per-request)\n\n");
        val fmt = "| %-" + mw + "s | %7s | %12s | %12s | %12s | %12s | %12s |";
        val sep = "| " + "-".repeat(mw)
                + " | ------: | -----------: | -----------: | -----------: | -----------: | -----------: |\n";
        sb.append(String.format(fmt, HEADER_METHOD, HEADER_THREADS, "p50 (ns)", "p90 (ns)", "p99 (ns)", "p99.9 (ns)",
                "max (ns)")).append('\n');
        sb.append(sep);
        for (val r : withLatency) {
            val l = r.latency();
            sb.append(String.format(Locale.US,
                    "| %-" + mw + "s | %7d | %,12.0f | %,12.0f | %,12.0f | %,12.0f | %,12.0f |", r.method(),
                    r.threads(), l.p50(), l.p90(), l.p99(), l.p999(), l.max())).append('\n');
        }
        sb.append('\n');
    }

    private static void appendDerivedLatencyTable(StringBuilder sb, List<BenchmarkResult> results, int mw) {
        sb.append("## Latency (derived from throughput)\n\n");
        val fmt = "| %-" + mw + "s | %7s | %14s | %14s | %14s |";
        val sep = "| " + "-".repeat(mw) + " | ------: | -------------: | -------------: | -------------: |\n";
        sb.append(String.format(fmt, HEADER_METHOD, HEADER_THREADS, "Mean (ns/op)", "p5 (ns/op)", "p95 (ns/op)"))
                .append('\n');
        sb.append(sep);
        for (val r : results) {
            val meanNs = r.mean() > 0 ? 1_000_000_000.0 / r.mean() : 0;
            val p5Ns   = r.p95() > 0 ? 1_000_000_000.0 / r.p95() : 0;
            val p95Ns  = r.p5() > 0 ? 1_000_000_000.0 / r.p5() : 0;
            sb.append(String.format(Locale.US, "| %-" + mw + "s | %7d | %,14.0f | %,14.0f | %,14.0f |", r.method(),
                    r.threads(), meanNs, p5Ns, p95Ns)).append('\n');
        }
        sb.append('\n');
    }

    private static void appendScalingTable(StringBuilder sb, List<BenchmarkResult> results, int mw) {
        val methods = results.stream().map(BenchmarkResult::method).distinct().toList();
        if (methods.isEmpty()) {
            return;
        }
        sb.append("## Scaling Efficiency\n\n");
        val fmt = "| %-" + mw + "s | %7s | %18s | %13s | %5s |";
        val sep = "| " + "-".repeat(mw) + " | ------: | -----------------: | ------------: | ----: |\n";
        sb.append(String.format(fmt, HEADER_METHOD, HEADER_THREADS, "Throughput (ops/s)", "Scaling vs 1T", "Ideal"))
                .append('\n');
        sb.append(sep);
        for (val method : methods) {
            val methodResults = results.stream().filter(r -> r.method().equals(method)).toList();
            val baseline      = methodResults.stream().filter(r -> r.threads() == 1).findFirst()
                    .map(BenchmarkResult::mean).orElse(0.0);
            for (val r : methodResults) {
                val scaling = baseline > 0 ? r.mean() / baseline : 0.0;
                sb.append(String.format(Locale.US, "| %-" + mw + "s | %7d | %,18.0f | %12.1fx | %4.1fx |", r.method(),
                        r.threads(), r.mean(), scaling, (double) r.threads())).append('\n');
            }
        }
        sb.append('\n');
    }

    private static int maxMethodWidth(List<BenchmarkResult> results) {
        return Math.max(6, results.stream().mapToInt(r -> r.method().length()).max().orElse(20));
    }

}
