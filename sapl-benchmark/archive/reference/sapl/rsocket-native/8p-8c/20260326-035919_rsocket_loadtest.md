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
| Timestamp      | 20260326-035919        |
| JVM            | 21.0.9                 |
| OS             | Linux amd64            |
| CPUs           | 16                     |
| Label          | rsocket-native 8P server=0-15 8conn |

## Results

| Method           | Threads |   Mean (ops/s) |         95% CI | Median (ops/s) |     StdDev |   CV% |            Min |            Max |             p5 |            p95 |
| ---------------- | ------: | -------------: | -------------: | -------------: | ---------: | ----: | -------------: | -------------: | -------------: | -------------: |
| rsocket-8c-256vt |       1 |        250,138 |              0 |        250,138 |          0 |  0.0% |        250,138 |        250,138 |        250,138 |        250,138 |

## Latency (measured per-request)

| Method           | Threads |     p50 (ns) |     p90 (ns) |     p99 (ns) |   p99.9 (ns) |     max (ns) |
| ---------------- | ------: | -----------: | -----------: | -----------: | -----------: | -----------: |
| rsocket-8c-256vt |       1 |    1,507,465 |    1,918,227 |    3,841,225 | 2,338,986,723 | 4,043,875,850 |

## Latency (derived from throughput via Little's Law)

| Method           | Threads |   Mean (ns/op) |     p5 (ns/op) |    p95 (ns/op) |
| ---------------- | ------: | -------------: | -------------: | -------------: |
| rsocket-8c-256vt |       1 |          3,998 |          3,998 |          3,998 |

