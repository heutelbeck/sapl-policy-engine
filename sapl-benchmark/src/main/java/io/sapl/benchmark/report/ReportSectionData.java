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
package io.sapl.benchmark.report;

import java.util.Map;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReportSectionData {
    private String          chartFilePath;
    private BenchmarkResult benchmarkResult;

    public String getBenchmarkName() {
        return benchmarkResult.getBenchmarkShortName();
    }

    public String getPdpName() {
        return benchmarkResult.getPdp();
    }

    public String getAuthMethod() {
        return benchmarkResult.getAuthMethod();
    }

    public Integer getThreads() {
        return benchmarkResult.getThreads();
    }

    public Double getThroughputAvg() {
        return benchmarkResult.getThoughputAvg();
    }

    public Double getThroughputStdDev() {
        return benchmarkResult.getThoughputStdDev();
    }

    public Double getResponseTimeAvg() {
        return benchmarkResult.getResponseTimeAvg();
    }

    public Double getResponseTimeStdDev() {
        return benchmarkResult.getResponseTimeStdDev();
    }

    public Map<String, Object> getMap() {
        return Map.of("benchmark", getBenchmarkName(), "pdpName", getPdpName(), "threads", getThreads(),
                "thrpt", getThroughputAvg(), "thrpt_stddev", getThroughputStdDev(), "rspt", getResponseTimeAvg(),
                "rspt_stddev", getResponseTimeStdDev(), "rspt_min", benchmarkResult.getResponseTimeMin(), "rspt_max",
                benchmarkResult.getResponseTimeMax(), "chart", chartFilePath);
    }
}
