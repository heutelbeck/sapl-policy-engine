# Load Test Report

## Methodology

| Parameter      | Value                  |
|----------------|------------------------|
| Protocol       | RSocket                |
| Target         | 127.0.0.1:7000         |
| Concurrency    | 1024                   |
| Connections    | 4                      |
| VT/connection  | 256                    |
| Warmup         | convergence-based      |
| Measurement    | 30 s                   |
| Timestamp      | 20260326-035040        |
| JVM            | 21.0.9                 |
| OS             | Linux amd64            |
| CPUs           | 24                     |
| Label          | rsocket-native 4P server=0-7 4conn |

## Results

| Method           | Threads |   Mean (ops/s) |         95% CI | Median (ops/s) |     StdDev |   CV% |            Min |            Max |             p5 |            p95 |
| ---------------- | ------: | -------------: | -------------: | -------------: | ---------: | ----: | -------------: | -------------: | -------------: | -------------: |
| rsocket-4c-256vt |       1 |        103,283 |              0 |        103,283 |          0 |  0.0% |        103,283 |        103,283 |        103,283 |        103,283 |

## Latency (measured per-request)

| Method           | Threads |     p50 (ns) |     p90 (ns) |     p99 (ns) |   p99.9 (ns) |     max (ns) |
| ---------------- | ------: | -----------: | -----------: | -----------: | -----------: | -----------: |
| rsocket-4c-256vt |       1 |    5,271,286 |   21,079,265 |   46,622,103 |  496,066,213 | 1,180,062,648 |

## Latency (derived from throughput via Little's Law)

| Method           | Threads |   Mean (ns/op) |     p5 (ns/op) |    p95 (ns/op) |
| ---------------- | ------: | -------------: | -------------: | -------------: |
| rsocket-4c-256vt |       1 |          9,682 |          9,682 |          9,682 |

