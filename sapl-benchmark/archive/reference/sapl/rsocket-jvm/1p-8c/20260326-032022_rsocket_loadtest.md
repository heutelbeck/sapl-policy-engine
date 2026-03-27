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
| Timestamp      | 20260326-032022        |
| JVM            | 21.0.9                 |
| OS             | Linux amd64            |
| CPUs           | 30                     |
| Label          | rsocket-jvm 1P server=0-1 8conn |

## Results

| Method           | Threads |   Mean (ops/s) |         95% CI | Median (ops/s) |     StdDev |   CV% |            Min |            Max |             p5 |            p95 |
| ---------------- | ------: | -------------: | -------------: | -------------: | ---------: | ----: | -------------: | -------------: | -------------: | -------------: |
| rsocket-8c-256vt |       1 |        243,424 |              0 |        243,424 |          0 |  0.0% |        243,424 |        243,424 |        243,424 |        243,424 |

## Latency (measured per-request)

| Method           | Threads |     p50 (ns) |     p90 (ns) |     p99 (ns) |   p99.9 (ns) |     max (ns) |
| ---------------- | ------: | -----------: | -----------: | -----------: | -----------: | -----------: |
| rsocket-8c-256vt |       1 |    5,078,581 |   12,698,027 |   52,093,218 |  257,630,852 |  346,602,529 |

## Latency (derived from throughput via Little's Law)

| Method           | Threads |   Mean (ns/op) |     p5 (ns/op) |    p95 (ns/op) |
| ---------------- | ------: | -------------: | -------------: | -------------: |
| rsocket-8c-256vt |       1 |          4,108 |          4,108 |          4,108 |

