# SAPL Benchmarks

Three focused benchmarks measuring different aspects of SAPL performance.

## Quick Start

```shell
mvn package -pl sapl-node -DskipTests

# Run everything (~30 min with quick config)
cd sapl-node/benchmarks
nix develop ./opa --command bash -c 'export SAPL_JAR=/path/to/sapl-node.jar; ./run-all.sh /tmp/bench quick'
```

## Benchmarks

### 1. SAPL Performance (`run-sapl-performance.sh`)

Full SAPL 4.0 characterization. Measures embedded throughput, HTTP remote, RSocket remote, thread scaling, and JVM vs native across all policy complexity levels.

```shell
./run-sapl-performance.sh <output-dir> [quick|standard|scientific]
```

**What it measures:**
- No-op baseline (JMH overhead ceiling)
- Embedded throughput: empty through 500+ policies, simple and complex
- HTTP remote: blocking, concurrent, raw, wrk ceiling
- RSocket remote: blocking, concurrent
- All of the above for JVM and native binary
- Thread scaling per config level

**Scenarios:** empty, simple-1/100/500, complex-1/100, all-match-100, rbac-small, rbac-large, abac-equivalent

### 2. Version Comparison (`run-version-comparison.sh`)

Quick SAPL 3 vs SAPL 4 embedded comparison. Same scenarios, same combining algorithm (`DENY_OVERRIDES`), matching JMH parameters.

```shell
./run-version-comparison.sh <output-dir>
```

Always uses quick-equivalent parameters. Produces per-scenario throughput for direct improvement factor calculation.

**Prerequisites:** `~/git/sapl-benchmark3/target/sapl-benchmark3-1.0.0.jar`

### 3. Engine Comparison (`run-engine-comparison.sh`)

Cross-engine comparison: SAPL 4 vs OPA on the same RBAC deny case.

```shell
nix develop ./opa --command ./run-engine-comparison.sh <output-dir> [quick|standard|scientific]
```

**Phase 1 - Embedded:** OPA `opa bench` (with `--input` for fair comparison) vs SAPL embedded benchmark.

**Phase 2 - HTTP Remote:** wrk against both SAPL and OPA servers with identical parameters. Same tool, same transport, same warmup. This is the fair comparison.

**Phase 3 - RSocket:** SAPL-only capability demonstration. Shows what's possible beyond HTTP.

**Prerequisites:** `wrk` and `opa` on PATH (use `nix develop ./opa` for OPA)

## Configurations

| Config | Warmup | Measurement | Threads | Use case |
|--------|--------|-------------|---------|----------|
| `quick` | 1 x 1s | 2 x 2s | 1 | Smoke test, CI |
| `standard` | 3 x 5s | 5 x 5s | 1, 4, 8 | Development |
| `scientific` | 5 x 10s | 10 x 10s | 1, 4, 8, 16, 24 | Publication |

## Benchmark Methods

### Embedded

| Method | Description |
|--------|-------------|
| `noOp` | Returns PERMIT without PDP. JMH overhead ceiling. |
| `decideOnceBlocking` | Synchronous PDP evaluation. |
| `decideStreamFirst` | Reactive stream, first decision. |

### Remote (HTTP)

| Method | Description |
|--------|-------------|
| `decideOnceBlocking` | One HTTP round-trip, blocks thread. |
| `decideStreamFirst` | SSE stream, first event. |
| `decideOnceConcurrent` | 256 concurrent requests via WebClient. |
| `decideOnceRaw` | 256 concurrent via raw Netty (HTTP ceiling). |

### Remote (RSocket)

| Method | Description |
|--------|-------------|
| `decideOnceBlocking` | One RSocket request-response, protobuf. |
| `decideStreamFirst` | RSocket request-stream, first frame. |
| `decideOnceConcurrent` | 256 concurrent via protobuf client. |

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SAPL_JAR` | `../target/sapl-node-4.0.0-SNAPSHOT.jar` | Path to sapl-node JAR |
| `SAPL_NATIVE` | `sapl` on PATH | Path to native binary |
| `SAPL3_JAR` | `~/git/sapl-benchmark3/target/sapl-benchmark3-1.0.0.jar` | SAPL 3 benchmark JAR |

## Native Binary

Build with GraalVM:

```shell
nix develop ~/.dotfiles#graalvm --command mvn package -pl sapl-node -Pnative -DskipTests
```

Set `SAPL_NATIVE` to the binary path if not on PATH.

## Output

Each benchmark produces timestamped files per scenario:

```
results/scenario-name/
  YYYYMMDD-HHMMSS_embedded_report.md      Markdown with throughput, latency, scaling
  YYYYMMDD-HHMMSS_embedded_report.csv     CSV for chart generation
  YYYYMMDD-HHMMSS_embedded_all_1threads.json  JMH-compatible JSON
  wrk.txt                                  wrk output (remote only)
```
