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
        return java.util.Map.of("benchmark", getBenchmarkName(), "pdpName", getPdpName(), "threads", getThreads(),
                "thrpt", getThroughputAvg(), "thrpt_stddev", getThroughputStdDev(), "rspt", getResponseTimeAvg(),
                "rspt_stddev", getResponseTimeStdDev(), "rspt_min", benchmarkResult.getResponseTimeMin(), "rspt_max",
                benchmarkResult.getResponseTimeMax(), "chart", chartFilePath);
    }
}
