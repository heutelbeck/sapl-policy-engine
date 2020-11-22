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

import io.sapl.generator.DomainData;
import io.sapl.spring.pdp.embedded.EmbeddedPDPProperties.IndexType;
import lombok.Getter;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

@Getter
public class BenchmarkDataContainer {

    final String benchmarkId;
    final long benchmarkTimestamp;
    final String runtimeInfo;

    final IndexType indexType;
    final int runs;
    final int iterations;

    List<XlsRecord> data = new LinkedList<>();
    List<Double> minValues = new LinkedList<>();
    List<Double> maxValues = new LinkedList<>();
    List<Double> avgValues = new LinkedList<>();
    List<Double> mdnValues = new LinkedList<>();
    List<String> identifier = new LinkedList<>();
    List<PolicyGeneratorConfiguration> configs = new LinkedList<>();

    List<AggregateRecord> aggregateData = new LinkedList<>();

    public BenchmarkDataContainer(IndexType indexType, DomainData domainData, int runs, int iterations) {
        this.benchmarkId = UUID.randomUUID().toString();
        this.benchmarkTimestamp = System.currentTimeMillis();
        this.runtimeInfo = String.format("%s_%s", System.getProperty("java.vendor"),
                System.getProperty("java.version"));
        this.indexType = indexType;
        this.runs = runs;
        this.iterations = iterations;

    }
}
