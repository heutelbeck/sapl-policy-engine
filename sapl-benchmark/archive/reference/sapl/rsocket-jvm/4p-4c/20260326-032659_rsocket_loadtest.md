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
| Timestamp      | 20260326-032659        |
| JVM            | 21.0.9                 |
| OS             | Linux amd64            |
| CPUs           | 24                     |
| Label          | rsocket-jvm 4P server=0-7 4conn |

## Results

| Method           | Threads |   Mean (ops/s) |         95% CI | Median (ops/s) |     StdDev |   CV% |            Min |            Max |             p5 |            p95 |
| ---------------- | ------: | -------------: | -------------: | -------------: | ---------: | ----: | -------------: | -------------: | -------------: | -------------: |
| rsocket-4c-256vt |       1 |      1,118,743 |              0 |      1,118,743 |          0 |  0.0% |      1,118,743 |      1,118,743 |      1,118,743 |      1,118,743 |

## Latency (measured per-request)

| Method           | Threads |     p50 (ns) |     p90 (ns) |     p99 (ns) |   p99.9 (ns) |     max (ns) |
| ---------------- | ------: | -----------: | -----------: | -----------: | -----------: | -----------: |
| rsocket-4c-256vt |       1 |      759,545 |    1,096,845 |    1,792,235 |    3,859,380 |  200,720,731 |

## Latency (derived from throughput via Little's Law)

| Method           | Threads |   Mean (ns/op) |     p5 (ns/op) |    p95 (ns/op) |
| ---------------- | ------: | -------------: | -------------: | -------------: |
| rsocket-4c-256vt |       1 |            894 |            894 |            894 |

