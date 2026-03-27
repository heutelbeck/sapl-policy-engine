# Load Test Report

## Methodology

| Parameter      | Value                  |
|----------------|------------------------|
| Protocol       | RSocket                |
| Target         | 127.0.0.1:7000         |
| Concurrency    | 2048                   |
| Connections    | 8                      |
| VT/connection  | 256                    |
| Warmup         | convergence-based      |
| Measurement    | 30 s                   |
| Timestamp      | 20260326-033256        |
| JVM            | 21.0.9                 |
| OS             | Linux amd64            |
| CPUs           | 20                     |
| Label          | rsocket-jvm 6P server=0-11 8conn |

## Results

| Method           | Threads |   Mean (ops/s) |         95% CI | Median (ops/s) |     StdDev |   CV% |            Min |            Max |             p5 |            p95 |
| ---------------- | ------: | -------------: | -------------: | -------------: | ---------: | ----: | -------------: | -------------: | -------------: | -------------: |
| rsocket-8c-256vt |       1 |      1,006,044 |              0 |      1,006,044 |          0 |  0.0% |      1,006,044 |      1,006,044 |      1,006,044 |      1,006,044 |

## Latency (measured per-request)

| Method           | Threads |     p50 (ns) |     p90 (ns) |     p99 (ns) |   p99.9 (ns) |     max (ns) |
| ---------------- | ------: | -----------: | -----------: | -----------: | -----------: | -----------: |
| rsocket-8c-256vt |       1 |    1,450,000 |    3,195,670 |    6,061,233 |  182,301,207 |  222,581,760 |

## Latency (derived from throughput via Little's Law)

| Method           | Threads |   Mean (ns/op) |     p5 (ns/op) |    p95 (ns/op) |
| ---------------- | ------: | -------------: | -------------: | -------------: |
| rsocket-8c-256vt |       1 |            994 |            994 |            994 |

