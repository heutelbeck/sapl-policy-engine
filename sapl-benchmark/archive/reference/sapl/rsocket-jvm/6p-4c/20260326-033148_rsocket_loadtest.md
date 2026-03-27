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
| Timestamp      | 20260326-033148        |
| JVM            | 21.0.9                 |
| OS             | Linux amd64            |
| CPUs           | 20                     |
| Label          | rsocket-jvm 6P server=0-11 4conn |

## Results

| Method           | Threads |   Mean (ops/s) |         95% CI | Median (ops/s) |     StdDev |   CV% |            Min |            Max |             p5 |            p95 |
| ---------------- | ------: | -------------: | -------------: | -------------: | ---------: | ----: | -------------: | -------------: | -------------: | -------------: |
| rsocket-4c-256vt |       1 |      1,110,443 |              0 |      1,110,443 |          0 |  0.0% |      1,110,443 |      1,110,443 |      1,110,443 |      1,110,443 |

## Latency (measured per-request)

| Method           | Threads |     p50 (ns) |     p90 (ns) |     p99 (ns) |   p99.9 (ns) |     max (ns) |
| ---------------- | ------: | -----------: | -----------: | -----------: | -----------: | -----------: |
| rsocket-4c-256vt |       1 |      808,680 |      996,814 |    1,344,878 |    3,448,339 |  198,967,629 |

## Latency (derived from throughput via Little's Law)

| Method           | Threads |   Mean (ns/op) |     p5 (ns/op) |    p95 (ns/op) |
| ---------------- | ------: | -------------: | -------------: | -------------: |
| rsocket-4c-256vt |       1 |            901 |            901 |            901 |

