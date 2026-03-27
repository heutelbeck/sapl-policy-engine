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
| Timestamp      | 20260326-031938        |
| JVM            | 21.0.9                 |
| OS             | Linux amd64            |
| CPUs           | 30                     |
| Label          | rsocket-jvm 1P server=0-1 4conn |

## Results

| Method           | Threads |   Mean (ops/s) |         95% CI | Median (ops/s) |     StdDev |   CV% |            Min |            Max |             p5 |            p95 |
| ---------------- | ------: | -------------: | -------------: | -------------: | ---------: | ----: | -------------: | -------------: | -------------: | -------------: |
| rsocket-4c-256vt |       1 |        308,008 |              0 |        308,008 |          0 |  0.0% |        308,008 |        308,008 |        308,008 |        308,008 |

## Latency (measured per-request)

| Method           | Threads |     p50 (ns) |     p90 (ns) |     p99 (ns) |   p99.9 (ns) |     max (ns) |
| ---------------- | ------: | -----------: | -----------: | -----------: | -----------: | -----------: |
| rsocket-4c-256vt |       1 |    2,228,373 |    5,181,739 |    9,547,326 |  192,687,144 |  231,982,534 |

## Latency (derived from throughput via Little's Law)

| Method           | Threads |   Mean (ns/op) |     p5 (ns/op) |    p95 (ns/op) |
| ---------------- | ------: | -------------: | -------------: | -------------: |
| rsocket-4c-256vt |       1 |          3,247 |          3,247 |          3,247 |

