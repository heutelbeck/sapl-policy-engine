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
package io.sapl.pdp.benchmark;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.sapl.benchmark.BenchmarkCommand;
import picocli.CommandLine;

class SaplPdpBenchmarkIT {
    private static final String TMP_REPORT_PATH = "target/results/tmp_benchmark_test";

    @BeforeAll
    static void createEmptyBenchmarkResultFolder() throws IOException {
        final var tmpReportPathFile = new File(TMP_REPORT_PATH);
        FileUtils.deleteDirectory(tmpReportPathFile);
        assertTrue(tmpReportPathFile.mkdirs());
    }

    @Test
    void whenExecutingEmbeddedBenchmark_withAllSettings_thenReportsAreCreated() {
        // start benchmark
        final var returnCode = new CommandLine(new BenchmarkCommand()).execute("--cfg",
                "src/test/resources/integrationtest_benchmark_config.yaml", "--output", TMP_REPORT_PATH);
        Assertions.assertEquals(0, returnCode);

        // build a list of expected report files
        List<String> reportFiles = new ArrayList<>(List.of("Report.html", "custom.css", "favicon.png",
                "integrationtest_benchmark_config.yaml", "results_1threads.json", "results_1threads.log"));
        for (var decisionMethod : List.of("Decide Subscribe", "Decide Once")) {
            reportFiles.add("img/Average Response Time - " + decisionMethod + " - 1 thread(s).png");
            for (var benchmarkType : List.of("EmbeddedBenchmark", "HttpBenchmark", "RsocketBenchmark")) {
                reportFiles.add("img/Throughput - " + decisionMethod + " - "
                        + benchmarkType.replace("Benchmark", "").toLowerCase() + ".png");
                for (var authMethod : List.of("noAuth", "basicAuth", "apiKey", "oAuth2")) {
                    // embedded supports only noAuth
                    if (!"EmbeddedBenchmark".equals(benchmarkType) || "noAuth".equals(authMethod)) {
                        reportFiles.add("img/" + benchmarkType + "." + authMethod + decisionMethod.replace(" ", "")
                                + "_1_threads_rspt.png");
                        reportFiles.add("img/" + benchmarkType + "." + authMethod + decisionMethod.replace(" ", "")
                                + "_1_threads_thrpt.png");
                    }
                }
            }
        }

        // ensure that all expected report files are present and not empty
        for (String fileName : reportFiles) {
            File reportFile = new File(TMP_REPORT_PATH + "/" + fileName);
            assertTrue(reportFile.exists(), reportFile + " does not exist");
            assertTrue(reportFile.length() >= 0, reportFile + " is empty");

        }
    }
}
