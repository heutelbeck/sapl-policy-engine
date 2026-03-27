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
| Timestamp      | 20260326-032744        |
| JVM            | 21.0.9                 |
| OS             | Linux amd64            |
| CPUs           | 24                     |
| Label          | rsocket-jvm 4P server=0-7 8conn |

## Results

| Method           | Threads |   Mean (ops/s) |         95% CI | Median (ops/s) |     StdDev |   CV% |            Min |            Max |             p5 |            p95 |
| ---------------- | ------: | -------------: | -------------: | -------------: | ---------: | ----: | -------------: | -------------: | -------------: | -------------: |
| rsocket-8c-256vt |       1 |        943,524 |              0 |        943,524 |          0 |  0.0% |        943,524 |        943,524 |        943,524 |        943,524 |

## Latency (measured per-request)

| Method           | Threads |     p50 (ns) |     p90 (ns) |     p99 (ns) |   p99.9 (ns) |     max (ns) |
| ---------------- | ------: | -----------: | -----------: | -----------: | -----------: | -----------: |
| rsocket-8c-256vt |       1 |    1,457,368 |    3,041,578 |    5,546,102 |  192,234,183 | 1,723,106,224 |

## Latency (derived from throughput via Little's Law)

| Method           | Threads |   Mean (ns/op) |     p5 (ns/op) |    p95 (ns/op) |
| ---------------- | ------: | -------------: | -------------: | -------------: |
| rsocket-8c-256vt |       1 |          1,060 |          1,060 |          1,060 |

