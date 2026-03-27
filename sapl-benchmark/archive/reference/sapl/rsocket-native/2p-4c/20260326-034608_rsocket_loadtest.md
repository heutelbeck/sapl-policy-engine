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
| Timestamp      | 20260326-034608        |
| JVM            | 21.0.9                 |
| OS             | Linux amd64            |
| CPUs           | 28                     |
| Label          | rsocket-native 2P server=0-3 4conn |

## Results

| Method           | Threads |   Mean (ops/s) |         95% CI | Median (ops/s) |     StdDev |   CV% |            Min |            Max |             p5 |            p95 |
| ---------------- | ------: | -------------: | -------------: | -------------: | ---------: | ----: | -------------: | -------------: | -------------: | -------------: |
| rsocket-4c-256vt |       1 |        170,579 |              0 |        170,579 |          0 |  0.0% |        170,579 |        170,579 |        170,579 |        170,579 |

## Latency (measured per-request)

| Method           | Threads |     p50 (ns) |     p90 (ns) |     p99 (ns) |   p99.9 (ns) |     max (ns) |
| ---------------- | ------: | -----------: | -----------: | -----------: | -----------: | -----------: |
| rsocket-4c-256vt |       1 |    2,377,077 |    5,539,341 |   43,575,772 |  611,753,638 | 1,799,998,565 |

## Latency (derived from throughput via Little's Law)

| Method           | Threads |   Mean (ns/op) |     p5 (ns/op) |    p95 (ns/op) |
| ---------------- | ------: | -------------: | -------------: | -------------: |
| rsocket-4c-256vt |       1 |          5,862 |          5,862 |          5,862 |

