package io.sapl.benchmark;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import io.sapl.generator.DomainData;
import lombok.Getter;

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
