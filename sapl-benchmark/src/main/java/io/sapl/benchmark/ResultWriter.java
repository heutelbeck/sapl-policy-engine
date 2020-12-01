/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.spring.pdp.embedded.EmbeddedPDPProperties.IndexType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jxls.template.SimpleExporter;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.BitmapEncoder.BitmapFormat;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.XYChart;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static io.sapl.benchmark.BenchmarkConstants.DEFAULT_HEIGHT;
import static io.sapl.benchmark.BenchmarkConstants.DEFAULT_WIDTH;
import static io.sapl.benchmark.BenchmarkConstants.ERROR_WRITING_BITMAP;
import static io.sapl.benchmark.BenchmarkConstants.EXPORT_PROPERTIES;
import static io.sapl.benchmark.BenchmarkConstants.EXPORT_PROPERTIES_AGGREGATES;

@Slf4j
@RequiredArgsConstructor
public class ResultWriter {


    //    private static final String SUMMARY_CSV_PATH = Benchmark.DEFAULT_PATH + "benchmark_summary.csv";

    private final String resultPath;
    private final IndexType indexType;

    public void writeFinalResults(BenchmarkDataContainer benchmarkDataContainer, XYChart overviewChart) {
        log.info("writing charts and results to {}", resultPath);

        writeOverviewChart(overviewChart);
        writeOverviewExcel(benchmarkDataContainer.getData());


        buildAggregateData(benchmarkDataContainer);
        writeHistogramChart(benchmarkDataContainer);
        writeHistogramExcel(benchmarkDataContainer.getAggregateData());
        appendHistogramToCSVFile(benchmarkDataContainer.getAggregateData(), resultPath);
    }

    public void writeDetailsChart(List<XlsRecord> results, double[] times, String configName) {
        int i = 0;
        for (XlsRecord item : results) {
            times[i] = item.getTimeDuration();
            i++;
        }

        XYChart details = new XYChart(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        details.setTitle("Evaluation Time");
        details.setXAxisTitle("Run");
        details.setYAxisTitle("ms");
        details.addSeries(configName, times);

        try {
            BitmapEncoder.saveBitmap(details, resultPath + configName
                    .replaceAll("[^a-zA-Z0-9]", ""), BitmapFormat.PNG);
        } catch (IOException e) {
            log.error(ERROR_WRITING_BITMAP, e);
            System.exit(1);
        }
    }


    // HOSPITAL(15932) p20115 v821735	25,62	63,89	28,86	27,17
    private void appendHistogramToCSVFile(List<AggregateRecord> aggregateRecords, String path) {
        try (FileWriter fw = new FileWriter(path + "benchmark_summary.csv", Charset.defaultCharset(), true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            for (AggregateRecord dat : aggregateRecords) {
                // indextype, name, seed, policies, variables, min, max, avg, mdn
                out.printf("%s,\t %s;\t %s;\t %d;\t %d;\t %.2f;\t %.2f;\t %.2f;\t %.2f;\t %s",
                        indexType, dat.getName(),
                        dat.getSeed(), dat.getPolicyCount(), dat.getVariableCount(), dat.getMin(), dat.getMax(),
                        dat.getAvg(), dat.getMdn(), System.lineSeparator());
            }
        } catch (IOException e) {
            log.error("Error appending to  CSV", e);
            System.exit(1);
        }
    }

    private void writeOverviewChart(XYChart chart) {
        chart.setTitle("Evaluation Time");
        chart.setXAxisTitle("Run");
        chart.setYAxisTitle("ms");
        try {
            BitmapEncoder.saveBitmap(chart, resultPath + "overview-" + indexType, BitmapFormat.PNG);
        } catch (IOException e) {
            log.error(ERROR_WRITING_BITMAP, e);
            System.exit(1);
        }
    }

    private void writeHistogramChart(BenchmarkDataContainer benchmarkDataContainer) {

        CategoryChart histogram = new CategoryChart(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        histogram.setTitle("Aggregates");
        histogram.setXAxisTitle("Run");
        histogram.setYAxisTitle("ms");
        histogram.addSeries("min", benchmarkDataContainer.getIdentifier(), benchmarkDataContainer.getMinValues());
        histogram.addSeries("max", benchmarkDataContainer.getIdentifier(), benchmarkDataContainer.getMaxValues());
        histogram.addSeries("avg", benchmarkDataContainer.getIdentifier(), benchmarkDataContainer.getAvgValues());
        histogram.addSeries("mdn", benchmarkDataContainer.getIdentifier(), benchmarkDataContainer.getMdnValues());

        try {
            BitmapEncoder.saveBitmap(histogram, resultPath + "histogram-" + indexType, BitmapFormat.PNG);
        } catch (IOException e) {
            log.error(ERROR_WRITING_BITMAP, e);
            System.exit(1);
        }
    }

    private void writeOverviewExcel(List<XlsRecord> data) {
        try (OutputStream os = Files.newOutputStream(Paths.get(resultPath, "overview-" + indexType + ".xls"))) {
            SimpleExporter exp = new SimpleExporter();
            exp.gridExport(getExportHeader(), data, EXPORT_PROPERTIES, os);
        } catch (IOException e) {
            log.error("Error writing XLS", e);
            System.exit(1);
        }
    }

    private void writeHistogramExcel(List<AggregateRecord> data) {
        try (OutputStream os = Files.newOutputStream(Paths.get(resultPath, "histogram-" + indexType + ".xls"))) {
            SimpleExporter exp = new SimpleExporter();
            exp.gridExport(getExportHeaderAggregates(), data, EXPORT_PROPERTIES_AGGREGATES, os);
        } catch (IOException e) {
            log.error("Error writing XLS", e);
            System.exit(1);
        }
    }

    private void buildAggregateData(BenchmarkDataContainer benchmarkDataContainer) {
        for (int i = 0; i < benchmarkDataContainer.getIdentifier().size(); i++) {
            PolicyGeneratorConfiguration config = benchmarkDataContainer.getConfigs().get(i);
            benchmarkDataContainer.getAggregateData()
                    .add(new AggregateRecord(
                            benchmarkDataContainer.getIdentifier().get(i), //name
                            benchmarkDataContainer.getMinValues().get(i),  //min
                            benchmarkDataContainer.getMaxValues().get(i),  //max
                            benchmarkDataContainer.getAvgValues().get(i),  //avg
                            benchmarkDataContainer.getMdnValues().get(i),  //mdn
                            config.getSeed(),
                            config.getPolicyCount(),
                            config.getVariablePoolCount(),
                            benchmarkDataContainer.getRuns(),
                            benchmarkDataContainer.getIterations()
                    ));
        }
    }

    private List<String> getExportHeader() {
        return Arrays.asList("Iteration", "Test Case", "Preparation Time (ms)", "Execution Time (ms)", "Request String",
                "Response String (ms)");
    }

    private List<String> getExportHeaderAggregates() {
        return Arrays.asList("Test Case", "Minimum Time (ms)", "Maximum Time (ms)", "Average Time (ms)",
                "Median Time (ms)", "Seed", "Policy Count", "Variable Count", "Runs", "Iterations");
    }


}
