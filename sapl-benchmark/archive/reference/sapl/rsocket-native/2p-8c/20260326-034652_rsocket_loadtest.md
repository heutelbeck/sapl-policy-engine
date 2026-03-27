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
| Timestamp      | 20260326-034652        |
| JVM            | 21.0.9                 |
| OS             | Linux amd64            |
| CPUs           | 28                     |
| Label          | rsocket-native 2P server=0-3 8conn |

## Results

| Method           | Threads |   Mean (ops/s) |         95% CI | Median (ops/s) |     StdDev |   CV% |            Min |            Max |             p5 |            p95 |
| ---------------- | ------: | -------------: | -------------: | -------------: | ---------: | ----: | -------------: | -------------: | -------------: | -------------: |
| rsocket-8c-256vt |       1 |        149,877 |              0 |        149,877 |          0 |  0.0% |        149,877 |        149,877 |        149,877 |        149,877 |

## Latency (measured per-request)

| Method           | Threads |     p50 (ns) |     p90 (ns) |     p99 (ns) |   p99.9 (ns) |     max (ns) |
| ---------------- | ------: | -----------: | -----------: | -----------: | -----------: | -----------: |
| rsocket-8c-256vt |       1 |    4,932,357 |   10,199,671 |  156,178,790 | 1,679,480,161 | 1,798,835,624 |

## Latency (derived from throughput via Little's Law)

| Method           | Threads |   Mean (ns/op) |     p5 (ns/op) |    p95 (ns/op) |
| ---------------- | ------: | -------------: | -------------: | -------------: |
| rsocket-8c-256vt |       1 |          6,672 |          6,672 |          6,672 |

