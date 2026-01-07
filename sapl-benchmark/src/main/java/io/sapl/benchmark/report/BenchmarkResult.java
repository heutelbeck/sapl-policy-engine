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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;

import com.nimbusds.jose.shaded.gson.JsonElement;
import com.nimbusds.jose.shaded.gson.JsonObject;

import io.sapl.benchmark.util.BenchmarkException;
import lombok.Getter;

public final class BenchmarkResult {
    private final JsonObject benchmarkResultJson;

    @Getter
    private final String  benchmarkFullName;
    @Getter
    private final String  pdp;
    @Getter
    private final String  decisionMethod;
    @Getter
    private final String  authMethod;
    @Getter
    private final int     threads;
    @Getter
    private final Integer measureTimeInSeconds;

    private static final String PRIMARY_METRIC_FIELD = "primaryMetric";
    private final String[]      benchmarkNameElements;

    BenchmarkResult(JsonElement resultJsonElement) {
        this.benchmarkResultJson = resultJsonElement.getAsJsonObject();
        final var benchmarkName = benchmarkResultJson.get("benchmark").getAsString();
        this.benchmarkFullName     = benchmarkName;
        this.benchmarkNameElements = benchmarkName.split("\\.");
        this.threads               = benchmarkResultJson.get("threads").getAsInt();
        this.pdp                   = getPdpFromBenchmarkName(this.benchmarkFullName);
        this.decisionMethod        = getDecisionMethodFromBenchmarkName(this.benchmarkFullName);
        this.authMethod            = getAuthMethodFromBenchmarkName(this.benchmarkFullName);
        this.measureTimeInSeconds  = getThroughputInSeconds();
    }

    private static String getPdpFromBenchmarkName(String benchmarkName) {
        final var benchmarkNames = benchmarkName.split("\\.");
        return benchmarkNames[benchmarkNames.length - 2].replaceAll("Benchmark$", "").toLowerCase();
    }

    private static String getDecisionMethodFromBenchmarkName(String benchmarkName) {
        final var benchmarkNames = benchmarkName.split("\\.");
        final var methodName     = benchmarkNames[benchmarkNames.length - 1];
        if (methodName.endsWith("DecideOnce")) {
            return "Decide Once";
        } else if (methodName.endsWith("DecideSubscribe") || methodName.endsWith("Decide")) {
            return "Decide Subscribe";
        } else {
            throw new BenchmarkException("Unable to determine DecisionMethod in " + methodName);
        }
    }

    private static String getAuthMethodFromBenchmarkName(String benchmarkName) {
        final var benchmarkNames = benchmarkName.split("\\.");
        final var methodName     = benchmarkNames[benchmarkNames.length - 1];
        return methodName.replaceAll("Decide(Once|Subscribe)?$", "");
    }

    private Integer getThroughputInSeconds() {
        final var measureTimeStr = benchmarkResultJson.get("measurementTime").getAsString();
        return Integer.valueOf(measureTimeStr.replaceAll(" s$", ""));
    }

    private Double throughputToResponseTimeInMs(Double throughput) {
        return 1 / throughput / threads * 1000;
    }

    public Double getResponseTimeAvg() {
        return throughputToResponseTimeInMs(getThoughputAvg());
    }

    public Double getThoughputAvg() {
        return benchmarkResultJson.get(PRIMARY_METRIC_FIELD).getAsJsonObject().get("score").getAsDouble();
    }

    public List<List<Double>> getThroughputRawResults() {
        List<List<Double>> resultArray = new ArrayList<>();
        final var          jsonRawData = benchmarkResultJson.get(PRIMARY_METRIC_FIELD).getAsJsonObject().get("rawData")
                .getAsJsonArray();
        for (JsonElement forkData : jsonRawData) {
            List<Double> forkResultArray = new ArrayList<>();
            for (JsonElement entry : forkData.getAsJsonArray()) {
                forkResultArray.add(entry.getAsDouble());
            }
            resultArray.add(forkResultArray);
        }
        return resultArray;
    }

    public List<List<Double>> getResponseTimeRawResults() {
        List<List<Double>> resultArray = new ArrayList<>();
        final var          jsonRawData = benchmarkResultJson.get(PRIMARY_METRIC_FIELD).getAsJsonObject().get("rawData")
                .getAsJsonArray();
        for (JsonElement forkData : jsonRawData) {
            List<Double> forkResultArray = new ArrayList<>();
            for (JsonElement entry : forkData.getAsJsonArray()) {
                forkResultArray.add(throughputToResponseTimeInMs(entry.getAsDouble()));
            }
            resultArray.add(forkResultArray);
        }
        return resultArray;
    }

    public List<Double> getThroughputAllRawResults() {
        List<Double> resultArray = new ArrayList<>();
        for (var forkData : getThroughputRawResults()) {
            resultArray.addAll(forkData);
        }
        return resultArray;
    }

    public List<Double> getResponseTimeAllRawResults() {
        List<Double> resultArray = new ArrayList<>();
        for (var forkData : getResponseTimeRawResults()) {
            resultArray.addAll(forkData);
        }
        return resultArray;
    }

    public Double getThoughputStdDev() {
        final var rawResults = getThroughputAllRawResults();
        return new StandardDeviation().evaluate(rawResults.stream().mapToDouble(d -> d).toArray());
    }

    public Double getResponseTimeStdDev() {
        final var rawResults = getResponseTimeAllRawResults();
        return new StandardDeviation().evaluate(rawResults.stream().mapToDouble(d -> d).toArray());
    }

    public Double getResponseTimeMin() {
        return getResponseTimeAllRawResults().stream().mapToDouble(d -> d).min().getAsDouble();
    }

    public Double getResponseTimeMax() {
        return getResponseTimeAllRawResults().stream().mapToDouble(d -> d).max().getAsDouble();
    }

    public String getBenchmarkShortName() {
        return Arrays.stream(benchmarkNameElements).skip(benchmarkNameElements.length - (long) 2).map(String::valueOf)
                .collect(Collectors.joining("."));
    }

}
