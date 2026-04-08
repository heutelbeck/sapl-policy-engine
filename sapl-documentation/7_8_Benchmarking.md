---
layout: default
title: Benchmarking
parent: SAPL Node
nav_order: 708
---

## Benchmarking

SAPL Node includes two commands for measuring PDP performance: `sapl benchmark` for embedded evaluation throughput, and `sapl loadtest` for remote server load testing.

### Embedded Benchmark

The `benchmark` command measures policy evaluation throughput and latency of an embedded PDP using a built-in timing harness. It runs entirely in-process without a server.

```bash
sapl benchmark --rbac -o ./results
```

The `--rbac` flag uses a built-in RBAC scenario that requires no policy files. Alternatively, point at your own policies:

```bash
sapl benchmark --dir ./policies -s '"alice"' -a '"read"' -r '"doc"' -o ./results
```

#### Options

| Option                     | Default              | Description                                               |
|----------------------------|----------------------|-----------------------------------------------------------|
| `--rbac`                   |                      | Use built-in RBAC benchmark (no files needed)             |
| `--dir`, `--bundle`        | `~/.sapl/`           | Policy source                                             |
| `-s`, `-a`, `-r`           |                      | Subscription components (JSON values)                     |
| `-b`, `--benchmark`        | `decideOnceBlocking` | Method: `decideOnceBlocking`, `decideStreamFirst`, `noOp` |
| `-t`, `--threads`          | `1`                  | Concurrent benchmark threads                              |
| `--warmup-iterations`      | `3`                  | Number of warmup iterations                               |
| `--warmup-time`            | `45`                 | Seconds per warmup iteration                              |
| `--measurement-iterations` | `5`                  | Number of measurement iterations                          |
| `--measurement-time`       | `45`                 | Seconds per measurement iteration                         |
| `--latency`                | `true`               | Run a latency measurement pass after throughput           |
| `-o`, `--output`           |                      | Output directory for Markdown, CSV, and JSON reports      |
| `--machine-readable`       | `false`              | Output single-line parseable results for scripts          |

{: .note }
> For rigorous benchmarks with JIT isolation across forked JVMs, convergence checking, and advanced JVM tuning, use the `sapl-benchmark-sapl4` module instead. The embedded `sapl benchmark` command is designed for quick assessments.

### Remote Load Test

The `loadtest` command measures throughput and per-request latency of a running SAPL Node server. It supports both HTTP/JSON and RSocket/protobuf transports.

#### HTTP

```bash
sapl loadtest --url http://localhost:8443 -s '"alice"' -a '"read"' -r '"doc"'
```

#### RSocket

```bash
sapl loadtest --rsocket --host localhost --port 7000 -s '"alice"' -a '"read"' -r '"doc"'
```

#### Options

| Option                  | Default                 | Description                                        |
|-------------------------|-------------------------|----------------------------------------------------|
| `--url`                 | `http://localhost:8443` | HTTP server URL                                    |
| `--rsocket`             |                         | Use RSocket/protobuf transport instead of HTTP     |
| `--host`                | `localhost`             | RSocket server host                                |
| `--port`                | `7000`                  | RSocket server port                                |
| `--socket-path`         |                         | Unix domain socket path (alternative to host/port) |
| `--concurrency`         | `64`                    | Concurrent in-flight requests (HTTP)               |
| `--connections`         | `8`                     | Number of TCP connections (RSocket)                |
| `--vt-per-connection`   | `512`                   | Virtual threads per RSocket connection             |
| `--rate`                | `0`                     | Target req/s (0 = saturation mode)                 |
| `--warmup-seconds`      | `5`                     | Warmup duration                                    |
| `--measurement-seconds` | `10`                    | Measurement duration                               |
| `-o`, `--output`        |                         | Output directory for reports                       |
| `--label`               |                         | Label for the report                               |
| `--machine-readable`    | `false`                 | Output single-line parseable results for scripts   |

#### Saturation vs Paced Mode

By default (`--rate 0`), the load generator sends requests as fast as possible (saturation mode) to find the server's throughput ceiling. When `--rate` is set, it sends requests at a fixed rate with coordinated omission correction for accurate latency measurement under controlled load.

#### RSocket Connection Tuning

RSocket mode distributes load across multiple TCP connections. Each connection runs a configurable number of virtual threads. The total concurrency is `--connections` multiplied by `--vt-per-connection`. For example, 8 connections with 512 virtual threads each gives 4096 concurrent in-flight requests.

```bash
sapl loadtest --rsocket --connections 8 --vt-per-connection 512 -s '"alice"' -a '"read"' -r '"doc"'
```

For the full option reference, see the [CLI Reference](../7_9_CommandLine/).
