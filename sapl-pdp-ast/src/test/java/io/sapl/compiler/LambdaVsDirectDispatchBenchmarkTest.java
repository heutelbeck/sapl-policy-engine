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
package io.sapl.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;

import javax.imageio.ImageIO;

import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.inference.TTest;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.renderer.category.StatisticalBarRenderer;
import org.jfree.chart.renderer.xy.DeviationRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.statistics.DefaultStatisticalCategoryDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.data.xy.YIntervalSeries;
import org.jfree.data.xy.YIntervalSeriesCollection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Peer-review quality benchmark comparing expression evaluation dispatch
 * mechanisms.
 *
 * <h2>Research Question</h2>
 * Does direct virtual method dispatch provide measurable performance
 * improvement
 * over lambda indirection for expression tree evaluation?
 *
 * <h2>Hypothesis</h2>
 * H₀: There is no significant difference between dispatch mechanisms
 * H₁: Direct dispatch is faster than lambda indirection
 *
 * <h2>Methodology</h2>
 * <ul>
 * <li>Independent variables: Dispatch mechanism, operation complexity</li>
 * <li>Dependent variable: Execution time (nanoseconds)</li>
 * <li>Statistical test: Welch's t-test (unequal variances)</li>
 * <li>Effect size: Cohen's d</li>
 * <li>Confidence intervals: 95% CI using t-distribution</li>
 * <li>Significance level: α = 0.05</li>
 * </ul>
 *
 * <h2>Operation Complexity Levels</h2>
 * <ul>
 * <li>LIGHT: Simple arithmetic (representative of basic expressions)</li>
 * <li>MEDIUM: Map lookup + string operations (representative of attribute
 * access)</li>
 * <li>HEAVY: Object creation + field manipulation (representative of JSON
 * operations)</li>
 * </ul>
 */
@Disabled("Only for benchmarking")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LambdaVsDirectDispatchBenchmarkTest {

    // ==================== BENCHMARK CONFIGURATION ====================

    // Full rigorous benchmark configuration
    private static final int    WARMUP_ITERATIONS_STANDARD = 100_000;
    private static final int    WARMUP_ITERATIONS_DEPTH2   = 500_000;
    private static final int    MEASUREMENT_RUNS_STANDARD  = 100;
    private static final int    MEASUREMENT_RUNS_DEPTH2    = 200;
    private static final int    ITERATIONS_PER_RUN_STD     = 50_000;
    private static final int    ITERATIONS_PER_RUN_DEPTH2  = 100_000;
    private static final double SIGNIFICANCE_LEVEL         = 0.05;
    private static final double CONFIDENCE_LEVEL           = 0.95;
    private static final int[]  TREE_DEPTHS                = { 2, 3, 4, 5, 6, 7, 8, 9, 10 };

    private static final Path OUTPUT_DIR = Path.of("target/benchmarks/lambda-vs-directdispatch-benchmark");

    // ==================== OPERATION COMPLEXITY LEVELS ====================

    enum OperationComplexity {
        LIGHT("Light (arithmetic)"),
        MEDIUM("Medium (lookup)"),
        HEAVY("Heavy (object creation)");

        final String description;

        OperationComplexity(String description) {
            this.description = description;
        }
    }

    // ==================== SELF-CONTAINED TYPE SYSTEM ====================

    sealed interface Val permits NumVal, StrVal, ObjVal, UndefinedVal {
        long asLong();
    }

    record NumVal(long value) implements Val {
        @Override
        public long asLong() {
            return value;
        }
    }

    record StrVal(String value) implements Val {
        @Override
        public long asLong() {
            return value.length();
        }
    }

    record ObjVal(Map<String, Val> fields) implements Val {
        @Override
        public long asLong() {
            return fields.size();
        }
    }

    enum UndefinedVal implements Val {
        INSTANCE;

        @Override
        public long asLong() {
            return 0;
        }
    }

    record Ctx(long seed, Map<String, Val> attributes) {
        Ctx(long seed) {
            this(seed, Map.of("attr1", new NumVal(seed), "attr2", new StrVal("value" + seed)));
        }
    }

    // ==================== OPERATORS BY COMPLEXITY ====================

    // LIGHT: Simple arithmetic - ~5-10 CPU cycles
    static final BinaryOperator<Val> ADD_LIGHT = (left, right) -> {
        if (left instanceof NumVal l && right instanceof NumVal r) {
            return new NumVal(l.value() + r.value());
        }
        return UndefinedVal.INSTANCE;
    };

    // MEDIUM: Map lookup + string comparison - ~50-100 CPU cycles
    static final BinaryOperator<Val> ADD_MEDIUM = (left, right) -> {
        if (left instanceof NumVal l && right instanceof NumVal r) {
            // Simulate attribute lookup overhead
            String key      = "attr" + (l.value() % 2 + 1);
            int    hashCode = key.hashCode();
            return new NumVal(l.value() + r.value() + (hashCode % 10));
        }
        return UndefinedVal.INSTANCE;
    };

    // HEAVY: Object creation + field manipulation - ~200-500 CPU cycles
    static final BinaryOperator<Val> ADD_HEAVY = (left, right) -> {
        if (left instanceof NumVal l && right instanceof NumVal r) {
            // Simulate JSON object manipulation
            Map<String, Val> fields = new HashMap<>();
            fields.put("left", l);
            fields.put("right", r);
            fields.put("sum", new NumVal(l.value() + r.value()));
            ObjVal obj = new ObjVal(fields);
            return new NumVal(obj.asLong() + l.value() + r.value());
        }
        return UndefinedVal.INSTANCE;
    };

    // ==================== APPROACH A: LAMBDA INDIRECTION ====================

    sealed interface LambdaExpr permits LambdaLeaf, LambdaBinary {
        Val eval(Ctx ctx);
    }

    record LambdaLeaf(Function<Ctx, Val> function) implements LambdaExpr {
        @Override
        public Val eval(Ctx ctx) {
            return function.apply(ctx);
        }
    }

    record LambdaBinary(Function<Ctx, Val> function) implements LambdaExpr {
        @Override
        public Val eval(Ctx ctx) {
            return function.apply(ctx);
        }
    }

    // ==================== APPROACH B: DIRECT VIRTUAL DISPATCH ====================

    sealed interface DirectExpr permits DirectLeaf, DirectBinary {
        Val eval(Ctx ctx);
    }

    record DirectLeaf(long value) implements DirectExpr {
        @Override
        public Val eval(Ctx ctx) {
            return new NumVal(value + ctx.seed());
        }
    }

    record DirectBinary(DirectExpr left, DirectExpr right, BinaryOperator<Val> operator) implements DirectExpr {
        @Override
        public Val eval(Ctx ctx) {
            return operator.apply(left.eval(ctx), right.eval(ctx));
        }
    }

    // ==================== TREE BUILDERS ====================

    private LambdaExpr buildLambdaTree(int depth, int[] counter, BinaryOperator<Val> operator) {
        if (depth <= 1) {
            long val = ++counter[0];
            return new LambdaLeaf(ctx -> new NumVal(val + ctx.seed()));
        }
        LambdaExpr left  = buildLambdaTree(depth - 1, counter, operator);
        LambdaExpr right = buildLambdaTree(depth - 1, counter, operator);
        return new LambdaBinary(ctx -> operator.apply(left.eval(ctx), right.eval(ctx)));
    }

    private DirectExpr buildDirectTree(int depth, int[] counter, BinaryOperator<Val> operator) {
        if (depth <= 1) {
            return new DirectLeaf(++counter[0]);
        }
        DirectExpr left  = buildDirectTree(depth - 1, counter, operator);
        DirectExpr right = buildDirectTree(depth - 1, counter, operator);
        return new DirectBinary(left, right, operator);
    }

    // ==================== STATISTICAL INFRASTRUCTURE ====================

    record RawMeasurement(
            int depth,
            int leafCount,
            OperationComplexity complexity,
            double[] lambdaTimes,
            double[] directTimes) {}

    record StatisticalResult(
            int depth,
            int leafCount,
            OperationComplexity complexity,
            double lambdaMean,
            double lambdaStdDev,
            double lambdaCi95Lower,
            double lambdaCi95Upper,
            double directMean,
            double directStdDev,
            double directCi95Lower,
            double directCi95Upper,
            double absoluteDiff,
            double percentDiff,
            double diffCi95Lower,
            double diffCi95Upper,
            double pValue,
            double cohensD,
            boolean significant,
            String effectSizeInterpretation) {}

    private int getWarmupIterations(int depth) {
        return depth == 2 ? WARMUP_ITERATIONS_DEPTH2 : WARMUP_ITERATIONS_STANDARD;
    }

    private int getMeasurementRuns(int depth) {
        return depth == 2 ? MEASUREMENT_RUNS_DEPTH2 : MEASUREMENT_RUNS_STANDARD;
    }

    private int getIterationsPerRun(int depth) {
        return depth == 2 ? ITERATIONS_PER_RUN_DEPTH2 : ITERATIONS_PER_RUN_STD;
    }

    private double[] runMeasurements(Function<Ctx, Long> task, int runs, int iterationsPerRun) {
        double[] timings = new double[runs];
        long     sink    = 0;

        for (int run = 0; run < runs; run++) {
            Ctx  ctx   = new Ctx(run % 100);
            long start = System.nanoTime();
            for (int i = 0; i < iterationsPerRun; i++) {
                sink += task.apply(ctx);
            }
            long end = System.nanoTime();
            timings[run] = (end - start) / (double) iterationsPerRun;
        }

        if (sink == Long.MIN_VALUE) {
            System.out.println("Sink: " + sink);
        }

        return removeOutliers(timings);
    }

    private double[] removeOutliers(double[] data) {
        DescriptiveStatistics stats = new DescriptiveStatistics(data);
        double                q1    = stats.getPercentile(25);
        double                q3    = stats.getPercentile(75);
        double                iqr   = q3 - q1;
        double                lower = q1 - 1.5 * iqr;
        double                upper = q3 + 1.5 * iqr;

        return Arrays.stream(data).filter(v -> v >= lower && v <= upper).toArray();
    }

    private double[] computeConfidenceInterval(double[] data, double confidenceLevel) {
        DescriptiveStatistics stats = new DescriptiveStatistics(data);
        double                mean  = stats.getMean();
        double                sem   = stats.getStandardDeviation() / Math.sqrt(data.length);

        TDistribution tDist  = new TDistribution(data.length - 1);
        double        tValue = tDist.inverseCumulativeProbability(1 - (1 - confidenceLevel) / 2);

        return new double[] { mean - tValue * sem, mean + tValue * sem };
    }

    private StatisticalResult analyze(RawMeasurement raw) {
        DescriptiveStatistics lambdaStats = new DescriptiveStatistics(raw.lambdaTimes);
        DescriptiveStatistics directStats = new DescriptiveStatistics(raw.directTimes);

        double   lambdaMean   = lambdaStats.getMean();
        double   lambdaStdDev = lambdaStats.getStandardDeviation();
        double[] lambdaCi     = computeConfidenceInterval(raw.lambdaTimes, CONFIDENCE_LEVEL);

        double   directMean   = directStats.getMean();
        double   directStdDev = directStats.getStandardDeviation();
        double[] directCi     = computeConfidenceInterval(raw.directTimes, CONFIDENCE_LEVEL);

        double absoluteDiff = lambdaMean - directMean;
        double percentDiff  = (absoluteDiff / lambdaMean) * 100;

        // CI for difference (using Welch-Satterthwaite approximation)
        double        seLambda    = lambdaStdDev / Math.sqrt(raw.lambdaTimes.length);
        double        seDirect    = directStdDev / Math.sqrt(raw.directTimes.length);
        double        seDiff      = Math.sqrt(seLambda * seLambda + seDirect * seDirect);
        double        dfWelch     = Math.pow(seLambda * seLambda + seDirect * seDirect, 2)
                / (Math.pow(seLambda, 4) / (raw.lambdaTimes.length - 1)
                        + Math.pow(seDirect, 4) / (raw.directTimes.length - 1));
        TDistribution tDistDiff   = new TDistribution(dfWelch);
        double        tValueDiff  = tDistDiff.inverseCumulativeProbability(1 - (1 - CONFIDENCE_LEVEL) / 2);
        double        diffCiLower = absoluteDiff - tValueDiff * seDiff;
        double        diffCiUpper = absoluteDiff + tValueDiff * seDiff;

        TTest  tTest  = new TTest();
        double pValue = tTest.tTest(raw.lambdaTimes, raw.directTimes);

        double pooledStdDev = Math.sqrt((lambdaStdDev * lambdaStdDev + directStdDev * directStdDev) / 2);
        double cohensD      = pooledStdDev > 0 ? absoluteDiff / pooledStdDev : 0;

        String effectSize;
        double absD = Math.abs(cohensD);
        if (absD < 0.2)
            effectSize = "negligible";
        else if (absD < 0.5)
            effectSize = "small";
        else if (absD < 0.8)
            effectSize = "medium";
        else
            effectSize = "large";

        return new StatisticalResult(raw.depth, raw.leafCount, raw.complexity, lambdaMean, lambdaStdDev, lambdaCi[0],
                lambdaCi[1], directMean, directStdDev, directCi[0], directCi[1], absoluteDiff, percentDiff, diffCiLower,
                diffCiUpper, pValue, cohensD, pValue < SIGNIFICANCE_LEVEL, effectSize);
    }

    // ==================== OUTPUT GENERATION ====================

    @BeforeAll
    void setupOutputDirectory() throws IOException {
        Files.createDirectories(OUTPUT_DIR);
    }

    private void writeSystemInfo(PrintWriter out) {
        out.println("% Benchmark System Information");
        out.println("% Generated: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        out.println("% Java Version: " + System.getProperty("java.version"));
        out.println("% Java Vendor: " + System.getProperty("java.vendor"));
        out.println("% JVM: " + System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version"));
        out.println("% OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        out.println("% Architecture: " + System.getProperty("os.arch"));
        out.println("% Available Processors: " + Runtime.getRuntime().availableProcessors());
        out.println("% Max Memory: " + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + " MB");
        out.println("%");
        out.println("% Benchmark Configuration:");
        out.printf(Locale.US, "%% Warmup Iterations (standard): %,d%n", WARMUP_ITERATIONS_STANDARD);
        out.printf(Locale.US, "%% Warmup Iterations (depth 2): %,d%n", WARMUP_ITERATIONS_DEPTH2);
        out.printf(Locale.US, "%% Measurement Runs (standard): %d%n", MEASUREMENT_RUNS_STANDARD);
        out.printf(Locale.US, "%% Measurement Runs (depth 2): %d%n", MEASUREMENT_RUNS_DEPTH2);
        out.printf(Locale.US, "%% Significance Level: %.2f%n", SIGNIFICANCE_LEVEL);
        out.printf(Locale.US, "%% Confidence Level: %.0f%%%n", CONFIDENCE_LEVEL * 100);
        out.println();
    }

    private void writeCsv(List<StatisticalResult> results, Path path) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(path.toFile()))) {
            out.println(
                    "depth,leaves,complexity," + "lambda_mean_ns,lambda_stddev_ns,lambda_ci95_lower,lambda_ci95_upper,"
                            + "direct_mean_ns,direct_stddev_ns,direct_ci95_lower,direct_ci95_upper,"
                            + "absolute_diff_ns,percent_diff,diff_ci95_lower,diff_ci95_upper,"
                            + "p_value,cohens_d,significant,effect_size");

            for (StatisticalResult r : results) {
                out.printf(Locale.US,
                        "%d,%d,%s,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.6f,%.4f,%s,%s%n",
                        r.depth, r.leafCount, r.complexity.name(), r.lambdaMean, r.lambdaStdDev, r.lambdaCi95Lower,
                        r.lambdaCi95Upper, r.directMean, r.directStdDev, r.directCi95Lower, r.directCi95Upper,
                        r.absoluteDiff, r.percentDiff, r.diffCi95Lower, r.diffCi95Upper, r.pValue, r.cohensD,
                        r.significant, r.effectSizeInterpretation);
            }
        }
    }

    private void writeLatexTable(List<StatisticalResult> results, Path path) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(path.toFile()))) {
            writeSystemInfo(out);

            out.println("\\begin{table}[htbp]");
            out.println("\\centering");
            out.println("\\caption{Lambda Indirection vs Direct Virtual Dispatch Performance Comparison}");
            out.println("\\label{tab:dispatch-benchmark}");
            out.println("\\footnotesize");
            out.println("\\begin{tabular}{rrl|cc|cc|ccc}");
            out.println("\\toprule");
            out.println("& & & \\multicolumn{2}{c|}{\\textbf{Lambda (ns)}} & "
                    + "\\multicolumn{2}{c|}{\\textbf{Direct (ns)}} & "
                    + "\\multicolumn{3}{c}{\\textbf{Analysis}} \\\\");
            out.println("\\textbf{D} & \\textbf{N} & \\textbf{Op} & " + "\\textbf{Mean} & \\textbf{95\\% CI} & "
                    + "\\textbf{Mean} & \\textbf{95\\% CI} & "
                    + "\\textbf{$\\Delta$\\%} & \\textbf{p} & \\textbf{d} \\\\");
            out.println("\\midrule");

            for (StatisticalResult r : results) {
                String sig     = r.significant ? "\\textbf{" : "";
                String end     = r.significant ? "}" : "";
                String opShort = switch (r.complexity) {
                               case LIGHT  -> "L";
                               case MEDIUM -> "M";
                               case HEAVY  -> "H";
                               };
                out.printf(Locale.US,
                        "%d & %d & %s & %.1f & [%.1f,%.1f] & %.1f & [%.1f,%.1f] & %s%.1f%s & %.4f & %.2f \\\\%n",
                        r.depth, r.leafCount, opShort, r.lambdaMean, r.lambdaCi95Lower, r.lambdaCi95Upper, r.directMean,
                        r.directCi95Lower, r.directCi95Upper, sig, r.percentDiff, end, r.pValue, r.cohensD);
            }

            out.println("\\bottomrule");
            out.println("\\end{tabular}");
            out.println("\\par\\smallskip");
            out.println("\\footnotesize{D=Depth, N=Leaves, Op=Operation (L=Light, M=Medium, H=Heavy). "
                    + "Bold $\\Delta$\\% indicates p < 0.05. "
                    + "Cohen's d: $|d| < 0.2$ negligible, $0.2 \\leq |d| < 0.5$ small, "
                    + "$0.5 \\leq |d| < 0.8$ medium, $|d| \\geq 0.8$ large.}");
            out.println("\\end{table}");
        }
    }

    private void writeLatexSummaryTable(List<StatisticalResult> results, Path path) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(path.toFile()))) {
            writeSystemInfo(out);

            long   significantCount = results.stream().filter(r -> r.significant && r.percentDiff > 0).count();
            double avgSpeedup       = results.stream().filter(r -> r.significant && r.percentDiff > 0)
                    .mapToDouble(r -> r.percentDiff).average().orElse(0);
            double minSpeedup       = results.stream().filter(r -> r.significant && r.percentDiff > 0)
                    .mapToDouble(r -> r.percentDiff).min().orElse(0);
            double maxSpeedup       = results.stream().filter(r -> r.significant && r.percentDiff > 0)
                    .mapToDouble(r -> r.percentDiff).max().orElse(0);

            out.println("\\begin{table}[htbp]");
            out.println("\\centering");
            out.println("\\caption{Summary Statistics for Dispatch Mechanism Comparison}");
            out.println("\\label{tab:dispatch-summary}");
            out.println("\\begin{tabular}{lr}");
            out.println("\\toprule");
            out.println("\\textbf{Metric} & \\textbf{Value} \\\\");
            out.println("\\midrule");
            out.printf(Locale.US, "Total test configurations & %d \\\\%n", results.size());
            out.printf(Locale.US, "Significant improvements (direct faster) & %d (%.0f\\%%) \\\\%n", significantCount,
                    100.0 * significantCount / results.size());
            out.printf(Locale.US, "Average speedup (significant cases) & %.1f\\%% \\\\%n", avgSpeedup);
            out.printf(Locale.US, "Speedup range (significant cases) & [%.1f\\%%, %.1f\\%%] \\\\%n", minSpeedup,
                    maxSpeedup);
            out.printf(Locale.US, "Confidence level & %.0f\\%% \\\\%n", CONFIDENCE_LEVEL * 100);
            out.printf(Locale.US, "Significance level ($\\alpha$) & %.2f \\\\%n", SIGNIFICANCE_LEVEL);
            out.println("\\bottomrule");
            out.println("\\end{tabular}");
            out.println("\\end{table}");
        }
    }

    private void generateBarChartWithCI(List<StatisticalResult> results, OperationComplexity complexity, Path pngPath)
            throws IOException {
        List<StatisticalResult> filtered = results.stream().filter(r -> r.complexity == complexity).toList();

        DefaultStatisticalCategoryDataset dataset = new DefaultStatisticalCategoryDataset();

        for (StatisticalResult r : filtered) {
            double lambdaError = (r.lambdaCi95Upper - r.lambdaCi95Lower) / 2;
            double directError = (r.directCi95Upper - r.directCi95Lower) / 2;
            dataset.add(r.lambdaMean, lambdaError, "Lambda", "D" + r.depth);
            dataset.add(r.directMean, directError, "Direct", "D" + r.depth);
        }

        JFreeChart chart = ChartFactory.createLineChart("Dispatch Comparison: " + complexity.description, "Tree Depth",
                "Execution Time (ns)", dataset, PlotOrientation.VERTICAL, true, true, false);

        chart.addSubtitle(
                new TextTitle("Error bars show 95% confidence intervals", new Font("SansSerif", Font.PLAIN, 10)));

        CategoryPlot           plot     = chart.getCategoryPlot();
        StatisticalBarRenderer renderer = new StatisticalBarRenderer();
        renderer.setSeriesPaint(0, new Color(66, 133, 244));
        renderer.setSeriesPaint(1, new Color(52, 168, 83));
        renderer.setErrorIndicatorPaint(Color.BLACK);
        renderer.setDrawBarOutline(true);
        plot.setRenderer(renderer);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        ImageIO.write(chart.createBufferedImage(800, 500), "PNG", pngPath.toFile());
    }

    private void generateSpeedupChart(List<StatisticalResult> results, OperationComplexity complexity, Path pngPath)
            throws IOException {
        List<StatisticalResult> filtered = results.stream().filter(r -> r.complexity == complexity).toList();

        DefaultStatisticalCategoryDataset dataset = new DefaultStatisticalCategoryDataset();

        for (StatisticalResult r : filtered) {
            // Convert diff CI to percentage
            double diffCiError = ((r.diffCi95Upper - r.diffCi95Lower) / 2 / r.lambdaMean) * 100;
            dataset.add(r.percentDiff, diffCiError, "Speedup", "D" + r.depth);
        }

        JFreeChart chart = ChartFactory.createLineChart("Speedup: " + complexity.description, "Tree Depth",
                "Speedup (%)", dataset, PlotOrientation.VERTICAL, false, true, false);

        chart.addSubtitle(new TextTitle("Error bars show 95% CI. Positive = Direct faster.",
                new Font("SansSerif", Font.PLAIN, 10)));

        CategoryPlot           plot     = chart.getCategoryPlot();
        StatisticalBarRenderer renderer = new StatisticalBarRenderer();
        renderer.setSeriesPaint(0, new Color(52, 168, 83));
        renderer.setErrorIndicatorPaint(Color.BLACK);
        plot.setRenderer(renderer);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        ImageIO.write(chart.createBufferedImage(800, 400), "PNG", pngPath.toFile());
    }

    private void generatePieChart(List<StatisticalResult> results, Path pngPath) throws IOException {
        long significantFaster = results.stream().filter(r -> r.significant && r.percentDiff > 0).count();
        long significantSlower = results.stream().filter(r -> r.significant && r.percentDiff < 0).count();
        long notSignificant    = results.stream().filter(r -> !r.significant).count();

        DefaultPieDataset<String> dataset = new DefaultPieDataset<>();
        if (significantFaster > 0)
            dataset.setValue("Direct Faster (p<0.05)", significantFaster);
        if (significantSlower > 0)
            dataset.setValue("Lambda Faster (p<0.05)", significantSlower);
        if (notSignificant > 0)
            dataset.setValue("No Significant Difference", notSignificant);

        JFreeChart chart = ChartFactory.createPieChart("Statistical Significance Distribution", dataset, true, true,
                false);

        PiePlot<?> plot = (PiePlot<?>) chart.getPlot();
        plot.setSectionPaint("Direct Faster (p<0.05)", new Color(52, 168, 83));
        plot.setSectionPaint("Lambda Faster (p<0.05)", new Color(234, 67, 53));
        plot.setSectionPaint("No Significant Difference", Color.GRAY);
        plot.setBackgroundPaint(Color.WHITE);

        ImageIO.write(chart.createBufferedImage(600, 400), "PNG", pngPath.toFile());
    }

    private void generateEffectSizePieChart(List<StatisticalResult> results, Path pngPath) throws IOException {
        long negligible = results.stream().filter(r -> r.effectSizeInterpretation.equals("negligible")).count();
        long small      = results.stream().filter(r -> r.effectSizeInterpretation.equals("small")).count();
        long medium     = results.stream().filter(r -> r.effectSizeInterpretation.equals("medium")).count();
        long large      = results.stream().filter(r -> r.effectSizeInterpretation.equals("large")).count();

        DefaultPieDataset<String> dataset = new DefaultPieDataset<>();
        if (negligible > 0)
            dataset.setValue("Negligible (|d|<0.2)", negligible);
        if (small > 0)
            dataset.setValue("Small (0.2≤|d|<0.5)", small);
        if (medium > 0)
            dataset.setValue("Medium (0.5≤|d|<0.8)", medium);
        if (large > 0)
            dataset.setValue("Large (|d|≥0.8)", large);

        JFreeChart chart = ChartFactory.createPieChart("Effect Size Distribution (Cohen's d)", dataset, true, true,
                false);

        PiePlot<?> plot = (PiePlot<?>) chart.getPlot();
        plot.setSectionPaint("Negligible (|d|<0.2)", new Color(200, 200, 200));
        plot.setSectionPaint("Small (0.2≤|d|<0.5)", new Color(144, 194, 231));
        plot.setSectionPaint("Medium (0.5≤|d|<0.8)", new Color(66, 133, 244));
        plot.setSectionPaint("Large (|d|≥0.8)", new Color(25, 80, 150));
        plot.setBackgroundPaint(Color.WHITE);

        ImageIO.write(chart.createBufferedImage(600, 400), "PNG", pngPath.toFile());
    }

    // ==================== LINE CHART OPTIONS ====================

    /**
     * Option A: One line chart per complexity level with Lambda and Direct lines.
     * Shows execution time progression across tree depths with 95% CI bands.
     */
    private void generateLineChartOptionA(List<StatisticalResult> results, OperationComplexity complexity, Path pngPath)
            throws IOException {
        List<StatisticalResult> filtered = results.stream().filter(r -> r.complexity == complexity).toList();

        YIntervalSeriesCollection dataset = new YIntervalSeriesCollection();

        YIntervalSeries lambdaSeries = new YIntervalSeries("Lambda");
        YIntervalSeries directSeries = new YIntervalSeries("Direct");

        for (StatisticalResult r : filtered) {
            lambdaSeries.add(r.depth, r.lambdaMean, r.lambdaCi95Lower, r.lambdaCi95Upper);
            directSeries.add(r.depth, r.directMean, r.directCi95Lower, r.directCi95Upper);
        }

        dataset.addSeries(lambdaSeries);
        dataset.addSeries(directSeries);

        NumberAxis xAxis = new NumberAxis("Tree Depth");
        xAxis.setRange(2, 10);
        xAxis.setTickUnit(new org.jfree.chart.axis.NumberTickUnit(1));
        LogarithmicAxis yAxis = new LogarithmicAxis("Execution Time (ns)");
        yAxis.setAllowNegativesFlag(false);
        yAxis.setStrictValuesFlag(false);

        DeviationRenderer renderer = new DeviationRenderer(true, true);
        renderer.setSeriesPaint(0, new Color(66, 133, 244));
        renderer.setSeriesFillPaint(0, new Color(66, 133, 244, 100));
        renderer.setSeriesPaint(1, new Color(52, 168, 83));
        renderer.setSeriesFillPaint(1, new Color(52, 168, 83, 100));
        renderer.setSeriesStroke(0, new BasicStroke(3.0f));
        renderer.setSeriesStroke(1, new BasicStroke(3.0f));

        XYPlot plot = new XYPlot(dataset, xAxis, yAxis, renderer);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);

        JFreeChart chart = new JFreeChart("Option A: " + complexity.description, JFreeChart.DEFAULT_TITLE_FONT, plot,
                true);
        chart.addSubtitle(
                new TextTitle("Shaded areas show 95% confidence intervals", new Font("SansSerif", Font.PLAIN, 10)));
        chart.setBackgroundPaint(Color.WHITE);

        ImageIO.write(chart.createBufferedImage(1400, 800), "PNG", pngPath.toFile());
    }

    /**
     * Option B: Single merged chart with all 6 lines (Lambda/Direct x 3
     * complexities).
     * Uses logarithmic Y-axis to accommodate the wide range of values.
     */
    private void generateLineChartOptionB(List<StatisticalResult> results, Path pngPath) throws IOException {
        YIntervalSeriesCollection dataset = new YIntervalSeriesCollection();

        for (OperationComplexity complexity : OperationComplexity.values()) {
            List<StatisticalResult> filtered = results.stream().filter(r -> r.complexity == complexity).toList();

            String suffix = switch (complexity) {
            case LIGHT  -> "L";
            case MEDIUM -> "M";
            case HEAVY  -> "H";
            };

            YIntervalSeries lambdaSeries = new YIntervalSeries("Lambda-" + suffix);
            YIntervalSeries directSeries = new YIntervalSeries("Direct-" + suffix);

            for (StatisticalResult r : filtered) {
                lambdaSeries.add(r.depth, r.lambdaMean, r.lambdaCi95Lower, r.lambdaCi95Upper);
                directSeries.add(r.depth, r.directMean, r.directCi95Lower, r.directCi95Upper);
            }

            dataset.addSeries(lambdaSeries);
            dataset.addSeries(directSeries);
        }

        NumberAxis xAxis = new NumberAxis("Tree Depth");
        xAxis.setRange(2, 10);
        xAxis.setTickUnit(new org.jfree.chart.axis.NumberTickUnit(1));
        LogarithmicAxis yAxis = new LogarithmicAxis("Execution Time (ns) - Log Scale");
        yAxis.setAllowNegativesFlag(false);
        yAxis.setStrictValuesFlag(false);

        DeviationRenderer renderer     = new DeviationRenderer(true, true);
        Color[]           lambdaColors = { new Color(66, 133, 244), new Color(100, 149, 237),
                new Color(135, 206, 250) };
        Color[]           directColors = { new Color(52, 168, 83), new Color(60, 179, 113), new Color(144, 238, 144) };

        for (int i = 0; i < 3; i++) {
            int lambdaIdx = i * 2;
            int directIdx = i * 2 + 1;
            renderer.setSeriesPaint(lambdaIdx, lambdaColors[i]);
            renderer.setSeriesFillPaint(lambdaIdx,
                    new Color(lambdaColors[i].getRed(), lambdaColors[i].getGreen(), lambdaColors[i].getBlue(), 30));
            renderer.setSeriesStroke(lambdaIdx, new BasicStroke(2.0f));

            renderer.setSeriesPaint(directIdx, directColors[i]);
            renderer.setSeriesFillPaint(directIdx,
                    new Color(directColors[i].getRed(), directColors[i].getGreen(), directColors[i].getBlue(), 30));
            renderer.setSeriesStroke(directIdx, new BasicStroke(2.0f));
        }

        XYPlot plot = new XYPlot(dataset, xAxis, yAxis, renderer);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);

        JFreeChart chart = new JFreeChart("Option B: All Complexities (Log Scale)", JFreeChart.DEFAULT_TITLE_FONT, plot,
                true);
        chart.addSubtitle(new TextTitle("L=Light, M=Medium, H=Heavy. Blue=Lambda, Green=Direct",
                new Font("SansSerif", Font.PLAIN, 10)));
        chart.setBackgroundPaint(Color.WHITE);

        ImageIO.write(chart.createBufferedImage(1600, 900), "PNG", pngPath.toFile());
    }

    /**
     * Option C: Speedup line chart - one line per complexity showing percentage
     * improvement.
     */
    private void generateLineChartOptionC(List<StatisticalResult> results, Path pngPath) throws IOException {
        YIntervalSeriesCollection dataset = new YIntervalSeriesCollection();

        Color[] colors = { new Color(144, 194, 231), new Color(66, 133, 244), new Color(25, 80, 150) };

        int colorIdx = 0;
        for (OperationComplexity complexity : OperationComplexity.values()) {
            List<StatisticalResult> filtered = results.stream().filter(r -> r.complexity == complexity).toList();

            YIntervalSeries series = new YIntervalSeries(complexity.description);

            for (StatisticalResult r : filtered) {
                double lowerPct = (r.diffCi95Lower / r.lambdaMean) * 100;
                double upperPct = (r.diffCi95Upper / r.lambdaMean) * 100;
                series.add(r.depth, r.percentDiff, lowerPct, upperPct);
            }

            dataset.addSeries(series);
            colorIdx++;
        }

        NumberAxis xAxis = new NumberAxis("Tree Depth");
        xAxis.setRange(2, 10);
        xAxis.setTickUnit(new org.jfree.chart.axis.NumberTickUnit(1));
        NumberAxis yAxis = new NumberAxis("Speedup (%)");

        DeviationRenderer renderer = new DeviationRenderer(true, true);
        for (int i = 0; i < 3; i++) {
            renderer.setSeriesPaint(i, colors[i]);
            renderer.setSeriesFillPaint(i,
                    new Color(colors[i].getRed(), colors[i].getGreen(), colors[i].getBlue(), 50));
            renderer.setSeriesStroke(i, new BasicStroke(2.5f));
        }

        XYPlot plot = new XYPlot(dataset, xAxis, yAxis, renderer);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);

        JFreeChart chart = new JFreeChart("Option C: Speedup by Complexity", JFreeChart.DEFAULT_TITLE_FONT, plot, true);
        chart.addSubtitle(new TextTitle("Positive = Direct faster. Shaded areas show 95% CI.",
                new Font("SansSerif", Font.PLAIN, 10)));
        chart.setBackgroundPaint(Color.WHITE);

        ImageIO.write(chart.createBufferedImage(1400, 800), "PNG", pngPath.toFile());
    }

    /**
     * Option D: Dual panel - absolute times on top, speedup on bottom, shared X
     * axis.
     */
    private void generateLineChartOptionD(List<StatisticalResult> results, Path pngPath) throws IOException {
        // Top panel: Absolute times (Lambda and Direct)
        YIntervalSeriesCollection absDataset = new YIntervalSeriesCollection();
        for (OperationComplexity complexity : OperationComplexity.values()) {
            List<StatisticalResult> filtered = results.stream().filter(r -> r.complexity == complexity).toList();
            String                  suffix   = switch (complexity) {
                                             case LIGHT  -> "L";
                                             case MEDIUM -> "M";
                                             case HEAVY  -> "H";
                                             };

            YIntervalSeries lambdaSeries = new YIntervalSeries("Lambda-" + suffix);
            YIntervalSeries directSeries = new YIntervalSeries("Direct-" + suffix);
            for (StatisticalResult r : filtered) {
                lambdaSeries.add(r.depth, r.lambdaMean, r.lambdaCi95Lower, r.lambdaCi95Upper);
                directSeries.add(r.depth, r.directMean, r.directCi95Lower, r.directCi95Upper);
            }
            absDataset.addSeries(lambdaSeries);
            absDataset.addSeries(directSeries);
        }

        LogarithmicAxis absYAxis = new LogarithmicAxis("Time (ns)");
        absYAxis.setAllowNegativesFlag(false);
        absYAxis.setStrictValuesFlag(false);
        DeviationRenderer absRenderer  = new DeviationRenderer(true, true);
        Color[]           lambdaColors = { new Color(255, 120, 120), new Color(220, 80, 80), new Color(180, 40, 40) };
        Color[]           directColors = { new Color(120, 200, 120), new Color(80, 160, 80), new Color(40, 120, 40) };
        for (int i = 0; i < 3; i++) {
            int lambdaIdx = i * 2;
            int directIdx = i * 2 + 1;
            absRenderer.setSeriesPaint(lambdaIdx, lambdaColors[i]);
            absRenderer.setSeriesFillPaint(lambdaIdx,
                    new Color(lambdaColors[i].getRed(), lambdaColors[i].getGreen(), lambdaColors[i].getBlue(), 60));
            absRenderer.setSeriesStroke(lambdaIdx, new BasicStroke(2.5f));
            absRenderer.setSeriesPaint(directIdx, directColors[i]);
            absRenderer.setSeriesFillPaint(directIdx,
                    new Color(directColors[i].getRed(), directColors[i].getGreen(), directColors[i].getBlue(), 60));
            absRenderer.setSeriesStroke(directIdx, new BasicStroke(2.5f));
        }

        XYPlot absPlot = new XYPlot(absDataset, null, absYAxis, absRenderer);
        absPlot.setBackgroundPaint(Color.WHITE);
        absPlot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        // Bottom panel: Speedup
        YIntervalSeriesCollection speedupDataset = new YIntervalSeriesCollection();
        for (OperationComplexity complexity : OperationComplexity.values()) {
            List<StatisticalResult> filtered = results.stream().filter(r -> r.complexity == complexity).toList();

            YIntervalSeries series = new YIntervalSeries(complexity.description);
            for (StatisticalResult r : filtered) {
                double lowerPct = (r.diffCi95Lower / r.lambdaMean) * 100;
                double upperPct = (r.diffCi95Upper / r.lambdaMean) * 100;
                series.add(r.depth, r.percentDiff, lowerPct, upperPct);
            }
            speedupDataset.addSeries(series);
        }

        NumberAxis        speedupYAxis    = new NumberAxis("Speedup (%)");
        DeviationRenderer speedupRenderer = new DeviationRenderer(true, true);
        Color[]           speedupColors   = { new Color(52, 168, 83), new Color(34, 139, 34), new Color(0, 100, 0) };
        for (int i = 0; i < 3; i++) {
            speedupRenderer.setSeriesPaint(i, speedupColors[i]);
            speedupRenderer.setSeriesFillPaint(i,
                    new Color(speedupColors[i].getRed(), speedupColors[i].getGreen(), speedupColors[i].getBlue(), 50));
            speedupRenderer.setSeriesStroke(i, new BasicStroke(2.0f));
        }

        XYPlot speedupPlot = new XYPlot(speedupDataset, null, speedupYAxis, speedupRenderer);
        speedupPlot.setBackgroundPaint(Color.WHITE);
        speedupPlot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        // Combined plot
        NumberAxis sharedXAxis = new NumberAxis("Tree Depth");
        sharedXAxis.setRange(2, 10);
        sharedXAxis.setTickUnit(new org.jfree.chart.axis.NumberTickUnit(1));

        CombinedDomainXYPlot combinedPlot = new CombinedDomainXYPlot(sharedXAxis);
        combinedPlot.add(absPlot, 2);
        combinedPlot.add(speedupPlot, 1);
        combinedPlot.setGap(10);

        JFreeChart chart = new JFreeChart("Option D: Dual Panel (Time + Speedup)", JFreeChart.DEFAULT_TITLE_FONT,
                combinedPlot, true);
        chart.addSubtitle(new TextTitle("Top: Direct dispatch times. Bottom: Speedup vs Lambda.",
                new Font("SansSerif", Font.PLAIN, 10)));
        chart.setBackgroundPaint(Color.WHITE);

        ImageIO.write(chart.createBufferedImage(1600, 1000), "PNG", pngPath.toFile());
    }

    private void writePgfplotsChart(List<StatisticalResult> results, OperationComplexity complexity, Path path)
            throws IOException {
        List<StatisticalResult> filtered = results.stream().filter(r -> r.complexity == complexity).toList();

        try (PrintWriter out = new PrintWriter(new FileWriter(path.toFile()))) {
            writeSystemInfo(out);
            out.println("% Requires: \\usepackage{pgfplots}");
            out.println("% \\pgfplotsset{compat=1.18}");
            out.println();
            out.println("\\begin{tikzpicture}");
            out.println("\\begin{axis}[");
            out.println("    ybar,");
            out.println("    width=12cm,");
            out.println("    height=8cm,");
            out.println("    xlabel={Tree Depth},");
            out.println("    ylabel={Execution Time (ns)},");
            out.println("    symbolic x coords={"
                    + filtered.stream().map(r -> "D" + r.depth).reduce((a, b) -> a + "," + b).orElse("") + "},");
            out.println("    xtick=data,");
            out.println("    legend style={at={(0.5,-0.15)},anchor=north,legend columns=2},");
            out.println("    ymin=0,");
            out.println("    bar width=8pt,");
            out.println("    error bars/.cd,");
            out.println("    y dir=both,");
            out.println("    y explicit,");
            out.println("]");
            out.println();

            out.print("\\addplot[fill=blue!60,error bars/.cd,y dir=both,y explicit] coordinates {");
            for (StatisticalResult r : filtered) {
                double error = (r.lambdaCi95Upper - r.lambdaCi95Lower) / 2;
                out.printf(Locale.US, "(D%d,%.1f) +- (0,%.1f) ", r.depth, r.lambdaMean, error);
            }
            out.println("};");

            out.print("\\addplot[fill=green!60,error bars/.cd,y dir=both,y explicit] coordinates {");
            for (StatisticalResult r : filtered) {
                double error = (r.directCi95Upper - r.directCi95Lower) / 2;
                out.printf(Locale.US, "(D%d,%.1f) +- (0,%.1f) ", r.depth, r.directMean, error);
            }
            out.println("};");

            out.println("\\legend{Lambda Indirection, Direct Dispatch}");
            out.println("\\end{axis}");
            out.println("\\end{tikzpicture}");
        }
    }

    // ==================== MAIN BENCHMARK TEST ====================

    @Test
    void benchmarkLambdaVsDirectDispatch() throws IOException {
        System.out.println("=".repeat(80));
        System.out.println("LAMBDA INDIRECTION vs DIRECT VIRTUAL DISPATCH BENCHMARK");
        System.out.println("Statistical Analysis with Confidence Intervals");
        System.out.println("=".repeat(80));
        System.out.println();
        System.out.println("System Information:");
        System.out.println(
                "  Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        System.out.println("  JVM: " + System.getProperty("java.vm.name"));
        System.out.println("  OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        System.out.println("  CPUs: " + Runtime.getRuntime().availableProcessors());
        System.out.println();
        System.out.println("Benchmark Configuration:");
        System.out.printf("  Standard: %,d warmup, %d runs × %,d iterations%n", WARMUP_ITERATIONS_STANDARD,
                MEASUREMENT_RUNS_STANDARD, ITERATIONS_PER_RUN_STD);
        System.out.printf("  Depth 2:  %,d warmup, %d runs × %,d iterations (extended)%n", WARMUP_ITERATIONS_DEPTH2,
                MEASUREMENT_RUNS_DEPTH2, ITERATIONS_PER_RUN_DEPTH2);
        System.out.printf("  Significance: α = %.2f, Confidence: %.0f%%%n", SIGNIFICANCE_LEVEL, CONFIDENCE_LEVEL * 100);
        System.out.println();
        System.out.println("Operation Complexity Levels:");
        System.out.println("  LIGHT:  Simple arithmetic (~5-10 CPU cycles)");
        System.out.println("  MEDIUM: Map lookup + string ops (~50-100 CPU cycles)");
        System.out.println("  HEAVY:  Object creation + fields (~200-500 CPU cycles)");
        System.out.println();
        System.out.println("=".repeat(80));

        List<StatisticalResult> allResults = new ArrayList<>();

        for (OperationComplexity complexity : OperationComplexity.values()) {
            System.out.printf("%n>>> Testing %s operations%n", complexity.description);

            BinaryOperator<Val> operator = switch (complexity) {
            case LIGHT  -> ADD_LIGHT;
            case MEDIUM -> ADD_MEDIUM;
            case HEAVY  -> ADD_HEAVY;
            };

            for (int depth : TREE_DEPTHS) {
                int leafCount  = 1 << (depth - 1);
                int warmup     = getWarmupIterations(depth);
                int runs       = getMeasurementRuns(depth);
                int iterations = getIterationsPerRun(depth);

                System.out.printf("%nDepth %d (%d leaves, %s warmup=%,d, runs=%d, iter=%,d)...%n", depth, leafCount,
                        depth == 2 ? "EXTENDED" : "standard", warmup, runs, iterations);

                LambdaExpr lambdaTree = buildLambdaTree(depth, new int[] { 0 }, operator);
                DirectExpr directTree = buildDirectTree(depth, new int[] { 0 }, operator);

                // Verify correctness
                Ctx  verifyCtx    = new Ctx(0);
                long lambdaResult = lambdaTree.eval(verifyCtx).asLong();
                long directResult = directTree.eval(verifyCtx).asLong();
                assertEquals(lambdaResult, directResult, "Results must match for depth " + depth);

                // Warmup
                System.out.print("  Warming up... ");
                for (int i = 0; i < warmup; i++) {
                    lambdaTree.eval(new Ctx(i % 100));
                    directTree.eval(new Ctx(i % 100));
                }
                System.out.println("done");

                // Measure
                System.out.print("  Measuring lambda... ");
                double[] lambdaTimes = runMeasurements(ctx -> lambdaTree.eval(ctx).asLong(), runs, iterations);
                System.out.printf("done (%d samples)%n", lambdaTimes.length);

                System.out.print("  Measuring direct... ");
                double[] directTimes = runMeasurements(ctx -> directTree.eval(ctx).asLong(), runs, iterations);
                System.out.printf("done (%d samples)%n", directTimes.length);

                RawMeasurement    raw    = new RawMeasurement(depth, leafCount, complexity, lambdaTimes, directTimes);
                StatisticalResult result = analyze(raw);
                allResults.add(result);

                System.out.printf("  Lambda: %.2f ns [%.2f, %.2f] 95%% CI%n", result.lambdaMean, result.lambdaCi95Lower,
                        result.lambdaCi95Upper);
                System.out.printf("  Direct: %.2f ns [%.2f, %.2f] 95%% CI%n", result.directMean, result.directCi95Lower,
                        result.directCi95Upper);
                System.out.printf("  Diff:   %.2f%% [%.2f, %.2f] 95%% CI (p=%.4f, d=%.2f, %s)%n", result.percentDiff,
                        (result.diffCi95Lower / result.lambdaMean) * 100,
                        (result.diffCi95Upper / result.lambdaMean) * 100, result.pValue, result.cohensD,
                        result.significant ? "SIGNIFICANT" : "not significant");
            }
        }

        // Print summary table
        System.out.println();
        System.out.println("=".repeat(100));
        System.out.println("RESULTS SUMMARY");
        System.out.println("=".repeat(100));
        System.out.println();
        System.out.printf("%-3s %-6s %-6s %15s %15s %10s %8s %8s %6s%n", "Op", "Depth", "Leaves", "Lambda [95%CI]",
                "Direct [95%CI]", "Diff%", "p-value", "Cohen d", "Sig.");
        System.out.println("-".repeat(100));

        for (StatisticalResult r : allResults) {
            String opShort = switch (r.complexity) {
            case LIGHT  -> "L";
            case MEDIUM -> "M";
            case HEAVY  -> "H";
            };
            System.out.printf("%-3s %-6d %-6d %6.1f [%5.1f,%5.1f] %6.1f [%5.1f,%5.1f] %+9.1f%% %8.4f %+7.2f %6s%n",
                    opShort, r.depth, r.leafCount, r.lambdaMean, r.lambdaCi95Lower, r.lambdaCi95Upper, r.directMean,
                    r.directCi95Lower, r.directCi95Upper, r.percentDiff, r.pValue, r.cohensD,
                    r.significant ? "YES" : "no");
        }

        System.out.println("-".repeat(100));
        System.out.println();

        // Write outputs
        System.out.println("Writing output files to: " + OUTPUT_DIR.toAbsolutePath());

        writeCsv(allResults, OUTPUT_DIR.resolve("benchmark_results.csv"));
        System.out.println("  ✓ benchmark_results.csv");

        writeLatexTable(allResults, OUTPUT_DIR.resolve("table_results.tex"));
        System.out.println("  ✓ table_results.tex");

        writeLatexSummaryTable(allResults, OUTPUT_DIR.resolve("table_summary.tex"));
        System.out.println("  ✓ table_summary.tex");

        for (OperationComplexity complexity : OperationComplexity.values()) {
            String suffix = complexity.name().toLowerCase();
            generateBarChartWithCI(allResults, complexity, OUTPUT_DIR.resolve("chart_comparison_" + suffix + ".png"));
            System.out.println("  ✓ chart_comparison_" + suffix + ".png");

            generateSpeedupChart(allResults, complexity, OUTPUT_DIR.resolve("chart_speedup_" + suffix + ".png"));
            System.out.println("  ✓ chart_speedup_" + suffix + ".png");

            writePgfplotsChart(allResults, complexity, OUTPUT_DIR.resolve("chart_" + suffix + ".tex"));
            System.out.println("  ✓ chart_" + suffix + ".tex (PGFPlots)");
        }

        generatePieChart(allResults, OUTPUT_DIR.resolve("chart_significance.png"));
        System.out.println("  ✓ chart_significance.png");

        generateEffectSizePieChart(allResults, OUTPUT_DIR.resolve("chart_effect_size.png"));
        System.out.println("  ✓ chart_effect_size.png");

        // Line charts (Options A, B, C, D)
        for (OperationComplexity complexity : OperationComplexity.values()) {
            String suffix = complexity.name().toLowerCase();
            generateLineChartOptionA(allResults, complexity,
                    OUTPUT_DIR.resolve("chart_line_optionA_" + suffix + ".png"));
            System.out.println("  ✓ chart_line_optionA_" + suffix + ".png");
        }
        generateLineChartOptionB(allResults, OUTPUT_DIR.resolve("chart_line_optionB_all.png"));
        System.out.println("  ✓ chart_line_optionB_all.png");

        generateLineChartOptionC(allResults, OUTPUT_DIR.resolve("chart_line_optionC_speedup.png"));
        System.out.println("  ✓ chart_line_optionC_speedup.png");

        generateLineChartOptionD(allResults, OUTPUT_DIR.resolve("chart_line_optionD_dual.png"));
        System.out.println("  ✓ chart_line_optionD_dual.png");

        // Statistical conclusion
        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("STATISTICAL CONCLUSION");
        System.out.println("=".repeat(80));

        for (OperationComplexity complexity : OperationComplexity.values()) {
            List<StatisticalResult> filtered = allResults.stream().filter(r -> r.complexity == complexity).toList();

            long   sigCount   = filtered.stream().filter(r -> r.significant && r.percentDiff > 0).count();
            double avgSpeedup = filtered.stream().filter(r -> r.significant && r.percentDiff > 0)
                    .mapToDouble(r -> r.percentDiff).average().orElse(0);

            System.out.printf("%n%s:%n", complexity.description);
            System.out.printf("  Significant improvements: %d of %d (%.0f%%)%n", sigCount, filtered.size(),
                    100.0 * sigCount / filtered.size());
            if (sigCount > 0) {
                System.out.printf("  Average speedup: %.1f%%%n", avgSpeedup);
            }
        }

        long totalSig = allResults.stream().filter(r -> r.significant && r.percentDiff > 0).count();
        System.out.println();
        if (totalSig > allResults.size() / 2) {
            System.out.println("OVERALL CONCLUSION: REJECT H₀");
            System.out.printf("Direct dispatch is significantly faster in %d of %d configurations (%.0f%%)%n", totalSig,
                    allResults.size(), 100.0 * totalSig / allResults.size());
        } else {
            System.out.println("OVERALL CONCLUSION: FAIL TO REJECT H₀");
        }

        System.out.println();
        System.out.println("=".repeat(80));
    }
}
