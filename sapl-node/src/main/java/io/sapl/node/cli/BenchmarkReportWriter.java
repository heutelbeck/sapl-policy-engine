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
 * self-contained reports suitable for thesis appendices.
 */
@UtilityClass
class BenchmarkReportWriter {

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

        sb.append("# Benchmark Report\n\n");

        sb.append("## Methodology\n\n");
        sb.append("| Parameter | Value |\n");
        sb.append("|-----------|-------|\n");
        sb.append("| Runner | %s |\n".formatted(runner));
        sb.append("| Mode | %s |\n".formatted(ctx.isRemote() ? "remote" : "embedded"));
        if (ctx.isRemote()) {
            sb.append("| Remote URL | %s |\n".formatted(ctx.remoteUrl()));
        } else {
            sb.append("| Policy source | %s |\n".formatted(ctx.policiesPath()));
            sb.append("| Config type | %s |\n".formatted(ctx.configType()));
        }
        sb.append("| Warmup | %d iterations x %d s |\n".formatted(cfg.warmupIterations(), cfg.warmupTimeSeconds()));
        sb.append("| Measurement | %d iterations x %d s |\n".formatted(cfg.measurementIterations(),
                cfg.measurementTimeSeconds()));
        sb.append("| Timestamp | %s |\n".formatted(cfg.timestamp()));
        sb.append('\n');

        sb.append("## Results\n\n");
        sb.append("| Method | Threads | Mean (ops/s) | Median (ops/s) | StdDev | CV% | Min | Max | p5 | p95 |\n");
        sb.append("|--------|---------|-------------|---------------|--------|-----|-----|-----|-----|-----|\n");
        for (val r : results) {
            sb.append(String.format(Locale.US,
                    "| %s | %d | %,.0f | %,.0f | %,.0f | %.1f%% | %,.0f | %,.0f | %,.0f | %,.0f |\n", r.method(),
                    r.threads(), r.mean(), r.median(), r.stddev(), r.cv(), r.min(), r.max(), r.p5(), r.p95()));
        }
        sb.append('\n');

        sb.append("## Latency (derived from throughput)\n\n");
        sb.append("| Method | Threads | Mean (ns/op) | p5 (ns/op) | p95 (ns/op) |\n");
        sb.append("|--------|---------|-------------|-----------|------------|\n");
        for (val r : results) {
            val meanNs = r.mean() > 0 ? 1_000_000_000.0 / r.mean() : 0;
            val p5Ns   = r.p95() > 0 ? 1_000_000_000.0 / r.p95() : 0;
            val p95Ns  = r.p5() > 0 ? 1_000_000_000.0 / r.p5() : 0;
            sb.append(String.format(Locale.US, "| %s | %d | %,.0f | %,.0f | %,.0f |\n", r.method(), r.threads(), meanNs,
                    p5Ns, p95Ns));
        }
        sb.append('\n');

        appendScalingTable(sb, results);

        return sb.toString();
    }

    private static void appendScalingTable(StringBuilder sb, List<BenchmarkResult> results) {
        val methods = results.stream().map(BenchmarkResult::method).distinct().toList();
        if (methods.isEmpty()) {
            return;
        }
        sb.append("## Scaling Efficiency\n\n");
        sb.append("| Method | Threads | Throughput (ops/s) | Scaling vs 1T | Ideal |\n");
        sb.append("|--------|---------|--------------------|---------------|-------|\n");
        for (val method : methods) {
            val methodResults = results.stream().filter(r -> r.method().equals(method)).toList();
            val baseline      = methodResults.stream().filter(r -> r.threads() == 1).findFirst()
                    .map(BenchmarkResult::mean).orElse(0.0);
            for (val r : methodResults) {
                val scaling = baseline > 0 ? r.mean() / baseline : 0.0;
                sb.append(String.format(Locale.US, "| %s | %d | %,.0f | %.1fx | %.1fx |\n", r.method(), r.threads(),
                        r.mean(), scaling, (double) r.threads()));
            }
        }
        sb.append('\n');
    }

    static String buildCsv(List<BenchmarkResult> results) {
        val sb = new StringBuilder();
        sb.append(
                "method,threads,mean_ops_s,median_ops_s,stddev,cv_pct,min_ops_s,max_ops_s,p5_ops_s,p95_ops_s,mean_ns_op\n");
        for (val r : results) {
            val meanNs = r.mean() > 0 ? 1_000_000_000.0 / r.mean() : 0;
            sb.append(String.format(Locale.US, "%s,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f\n", r.method(),
                    r.threads(), r.mean(), r.median(), r.stddev(), r.cv(), r.min(), r.max(), r.p5(), r.p95(), meanNs));
        }
        return sb.toString();
    }

}
