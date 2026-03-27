# Performance Comparison: SAPL 4 vs OPA

## Summary

This document presents a structured performance comparison between SAPL 4.0 and Open Policy Agent (OPA) 1.14 on identical hardware, using identical RBAC policies and the same measurement tools. All numbers are reproducible with the scripts provided in the SAPL repository.

**Key findings:**

- SAPL evaluates in **200ns**, OPA in **44us** per decision (isolated, no network).
  That is a **280x** engine speed difference.
- Over HTTP, SAPL requires **6 or more CPU cores** to outperform OPA. Below that, OPA's lightweight Go HTTP stack gives it an advantage.
- At 8 CPU cores, SAPL reaches **128K req/s** over HTTP, OPA reaches **83K req/s**.
  That is **1.5x** in SAPL's favor.
- Over RSocket/protobuf, SAPL reaches **1.35M req/s**. OPA does not offer a binary transport.
  That is **16x** OPA's peak HTTP throughput on the same hardware.
- OPA plateaus at ~83K req/s regardless of available CPU cores. SAPL scales linearly.

The performance gap over HTTP is smaller than the engine speed difference suggests. HTTP request handling overhead (framework, serialization, security filters) dominates the total request cost, masking the evaluation speed advantage. RSocket eliminates most of this overhead, revealing the engine's true performance.

## Test Environment

| Parameter | Value |
|-----------|-------|
| CPU | Intel Core i9-13900KS (8 P-cores at 5.6-6.0 GHz, 16 E-cores at 4.3 GHz) |
| RAM | 192 GB DDR5 |
| OS | NixOS Linux 6.18.18 |
| JVM | OpenJDK 21.0.9 (G1GC default) |
| OPA | 1.14.1 (Go binary, GOMAXPROCS auto-detected) |
| SAPL JVM | 4.0.0-SNAPSHOT (Spring Boot 4.0, Reactor Netty, Jackson 3) |
| SAPL Native | 4.0.0-SNAPSHOT (GraalVM native image) |
| HTTP load tool | wrk 4.2.0 (epoll, 2 threads) |
| RSocket load tool | Built-in JDK 21 virtual thread client |
| Cooling | Passive workstation heatsink with supplemental fan |

**Thermal note:** Individual CPU cores reach 100C under sustained load. This causes thermal throttling that reduces throughput during long measurement windows. Temperature is monitored per data point.

## Methodology

### Policy Under Test

Both engines evaluate an equivalent RBAC policy: user "bob" with role "test" requests "write" access to resource "foo123". The "test" role only permits "read", so the expected result is **deny**. This traverses role bindings, permission lookups, and a set membership test.

The policies are semantically identical. Both produce the same authorization decision for the same input, verified by automated tests against both engines with multiple input combinations.

### Measurement Protocol

- **CPU isolation:** Server and client pinned to separate physical cores via `taskset`. Server gets N P-cores (with hyper-threading siblings). Client gets remaining cores.
- **Warmup:** Convergence-based. Measurement starts after 3 consecutive 3-second intervals produce throughput within 5%. This ensures JVM JIT is fully optimized. OPA (Go) converges immediately.
- **Thermal cooldown:** Wait for package temperature below 38C between configurations.
- **Measurement duration:** 30 seconds sustained load per data point.
- **Little's Law validation:** Every data point is checked against L = throughput x latency. Ratios above 120% indicate server overload.

### About wrk

wrk maintains a fixed number of persistent TCP connections and sends requests sequentially on each. It records per-request latency and applies coordinated omission correction. Both engines are tested with identical wrk parameters.

## Embedded Evaluation Speed

Raw policy evaluation cost with no network or framework overhead.

| Engine | Runtime | Throughput (1 thread) | Throughput (8 threads) | Median Latency |
|--------|---------|----------------------|----------------------|----------------|
| **SAPL** | JVM (JIT) | 5,057,000 ops/s | 44,161,000 ops/s | ~200 ns |
| **SAPL** | Native (AOT) | 1,446,000 ops/s | 2,242,000 ops/s | ~690 ns |
| **OPA** | Go (AOT) | ~18,000 ops/s | N/A (single-threaded only) | 44,000 ns |

SAPL JVM is **280x faster** per evaluation. OPA's `opa bench` does not support multi-threaded benchmarking.

**Interpretation:** This difference only matters if evaluation is the bottleneck. Over HTTP, framework overhead dominates, as shown below.

<!-- Chart suggestion: Bar chart comparing single-threaded evaluation throughput (log scale) -->

## HTTP Server Throughput Matrix

Neither engine's raw evaluation speed is the bottleneck over HTTP. SAPL evaluates in 200ns, OPA in 44us, but an HTTP round-trip through any production framework costs 7-40us of overhead per request regardless of evaluation time. The HTTP comparison is effectively a comparison of server framework ecosystems: SAPL runs on Spring Boot 4.0 (Reactor Netty, Spring Security, Jackson 3, Micrometer) while OPA uses Go's `net/http` standard library with no middleware, no metrics, and no security filters. Go's `net/http` is a lightweight goroutine-per-connection server built into the Go runtime. It has no equivalent to Spring Security's filter chain, observation hooks, or Jackson's per-response serializer construction. This structural difference explains why OPA leads at low core counts despite its 220x slower evaluation engine.

Complete core x connection count matrix. All numbers in requests per second.

### SAPL JVM HTTP

| P-cores | 32 conns | 64 conns | 128 conns | 256 conns |
|---------|---------|---------|----------|----------|
| 1 | 15,070 | 16,188 | 16,416 | 17,095 |
| 2 | 28,440 | 31,351 | 32,721 | 33,895 |
| 4 | 42,670 | 60,063 | 62,426 | 64,666 |
| 6 | 42,484 | 86,107 | 94,457 | 96,522 |
| 8 | 42,366 | 103,791 | 123,326 | 127,756 |
| unpinned | 85,990 | 120,538 | 149,800 | 163,634 |

### SAPL Native HTTP

| P-cores | 32 conns | 64 conns | 128 conns | 256 conns |
|---------|---------|---------|----------|----------|
| 1 | 7,847 | 8,137 | 8,638 | 8,618 |
| 2 | 15,921 | 16,688 | 17,406 | 17,744 |
| 4 | 31,315 | 32,835 | 33,971 | 32,372 |
| 6 | 46,050 | 48,494 | 50,046 | 50,288 |
| 8 | 57,837 | 62,913 | 65,214 | 63,539 |
| unpinned | 80,648 | 83,395 | 62,595 | 13,367 |

### OPA HTTP

| P-cores | 32 conns | 64 conns | 128 conns | 256 conns |
|---------|---------|---------|----------|----------|
| 1 | 27,372 | 25,370 | 23,476 | 21,904 |
| 2 | 48,164 | 47,640 | 47,131 | 47,856 |
| 4 | 68,235 | 68,571 | 68,521 | 70,760 |
| 6 | 69,627 | 72,833 | 75,224 | 78,374 |
| 8 | 23,846 | 53,906 | 73,972 | 82,852 |
| unpinned | 12,993 | 22,677 | 14,371 | 15,065 |

**Note on OPA 8P/32c anomaly (23,846):** This data point was collected immediately after a 100C thermal event from the 6P/256c run. The CPU was still throttling. The 8P/128c and 8P/256c numbers (74K-83K) are more representative.

**Note on OPA unpinned:** OPA unpinned numbers are low because the wrk client and OPA server compete for the same CPU cores. This is not representative of production deployment.

<!-- Chart suggestion: Line chart with P-cores on X-axis, throughput on Y-axis, one line per engine. Shows crossover point. -->
<!-- Chart suggestion: Heatmap of the full matrix with color coding: green (>80K), yellow (40-80K), red (<40K) -->

### Head-to-Head: Best Throughput per Core Count

| P-cores | SAPL JVM (best) | SAPL Native (best) | OPA (best) | JVM/OPA | Native/OPA |
|---------|----------------|-------------------|------------|---------|------------|
| 1 | 17,095 (256c) | 8,638 (128c) | 27,372 (32c) | 0.62x | 0.32x |
| 2 | 33,895 (256c) | 17,744 (256c) | 48,164 (32c) | 0.70x | 0.37x |
| 4 | 64,666 (256c) | 33,971 (128c) | 70,760 (256c) | 0.91x | 0.48x |
| 6 | 96,522 (256c) | 50,288 (256c) | 78,374 (256c) | **1.23x** | 0.64x |
| 8 | 127,756 (256c) | 65,214 (128c) | 82,852 (256c) | **1.54x** | 0.79x |

The JVM crossover occurs between 4 and 6 P-cores. The native image does not reach OPA's HTTP throughput in this test, confirming that GraalVM native images trade peak throughput for startup time and memory footprint.

<!-- Chart suggestion: Bar chart, grouped by core count, SAPL vs OPA, with crossover line annotated -->

### Why OPA Wins at Low Core Counts

OPA's Go HTTP server (`net/http`) has lower per-request overhead than SAPL's Spring WebFlux stack. A JFR profiling session during SAPL HTTP load revealed that policy evaluation does not appear in the top 25 CPU consumers. The dominant costs are:

1. Micrometer metrics instrumentation
2. Reactor operator chain assembly (Spring Security observation hooks)
3. Jackson JSON serializer construction (per response)
4. Netty HTTP header encoding
5. Spring Security firewall checks

These are framework costs, not engine costs. OPA's Go server does not have equivalent middleware layers when running with default settings.

### Why SAPL Wins at High Core Counts

With 6+ cores, the JVM has enough headroom for background threads (JIT compiler, garbage collector, event loop workers) alongside request handling. SAPL's 200ns evaluation then dominates: more cores means proportionally more concurrent evaluations completing per second. OPA's 44us evaluation is 220x slower per request, so adding cores yields diminishing returns once the Go HTTP stack is saturated.

### Scaling

OPA plateaus at approximately 83K req/s. SAPL scales linearly from 17K (1 core) to 128K (8 cores) and beyond (164K unpinned). This linear scaling is consistent with Little's Law: at 200ns evaluation cost, the server needs very few in-flight requests to keep each core busy, leaving spare capacity for additional cores to contribute.

<!-- Chart suggestion: Scaling efficiency chart (throughput / cores) showing SAPL linear vs OPA plateau -->

## RSocket / Protobuf Transport

SAPL offers an alternative binary transport using RSocket with Protocol Buffer serialization. This bypasses the HTTP framework stack entirely. OPA does not offer a comparable transport.

### SAPL JVM RSocket (4 connections x 256 virtual threads)

| P-cores | Throughput (req/s) | vs OPA HTTP peak (83K) |
|---------|-------------------|----------------------|
| 1 | 308,008 | 3.7x |
| 2 | 579,925 | 7.0x |
| 4 | 1,118,743 | 13.5x |
| 6 | 1,110,443 | 13.4x |
| 8 | 1,353,627 | 16.3x |

### SAPL Native RSocket (8 connections x 256 virtual threads)

| P-cores | Throughput (req/s) | vs OPA HTTP peak (83K) |
|---------|-------------------|----------------------|
| 1 | 115,796 | 1.4x |
| 2 | 170,579 | 2.1x |
| 4 | 205,833 | 2.5x |
| 6 | 228,810 | 2.8x |
| 8 | 250,138 | 3.0x |

At 4 P-cores, SAPL JVM over RSocket exceeds **1 million authorization decisions per second**.

**This is not a protocol-vs-protocol comparison.** HTTP is universally supported. RSocket requires a SAPL client library. The comparison illustrates the throughput available when framework overhead is eliminated, revealing the engine's actual performance.

### Would a slower engine bottleneck RSocket?

Over HTTP, both engines are fast enough that evaluation cost disappears into framework overhead. Over RSocket, that changes. At 1.35M req/s on 8 P-cores, SAPL spends 1,350,000 x 200ns = 0.27 CPU-seconds per wall-clock second on evaluation, roughly 3% of the available 8 core-seconds. The engine is nowhere near the bottleneck.

If the engine instead took 44us per evaluation (OPA's measured speed), the math shifts: 1,350,000 x 44us = 59.4 CPU-seconds per wall-clock second. That exceeds the 8 available core-seconds by 7.4x. The theoretical ceiling drops to 8 cores / 44us = 182,000 req/s, a 7.4x reduction from observed throughput and only 2.2x OPA's HTTP peak.

In other words, the 220x engine speed difference is invisible over HTTP because the framework costs 7-40us per request anyway. But RSocket's sub-microsecond transport overhead exposes the engine directly. A 44us engine would turn RSocket into a CPU-bound evaluation bottleneck, eliminating the transport's advantage entirely. This is why SAPL's engine speed matters: not for HTTP, but for high-throughput binary transports where the framework is no longer the limiting factor.

<!-- Chart suggestion: Stacked or grouped bar chart showing HTTP JVM / HTTP Native / RSocket JVM / RSocket Native at each core count -->
<!-- Chart suggestion: Single chart with all 5 lines (SAPL HTTP JVM, SAPL HTTP Native, SAPL RSocket JVM, SAPL RSocket Native, OPA HTTP) on log-scale Y-axis -->

## Latency

### HTTP p50 Latency at 64 Connections (milliseconds)

| P-cores | SAPL JVM | SAPL Native | OPA |
|---------|---------|-------------|-----|
| 1 | 3.74 | 6.81 | 2.40 |
| 2 | 1.92 | 3.35 | 0.92 |
| 4 | 1.01 | 1.73 | 1.05 |
| 6 | 0.71 | 1.19 | 0.93 |
| 8 | 0.59 | 0.92 | 1.17 |

At 8 P-cores, SAPL JVM has the best median latency (590us) while OPA's latency increases (1.17ms) as it approaches its throughput ceiling.

<!-- Chart suggestion: Latency distribution chart (p50/p90/p99/p99.9) comparing SAPL HTTP vs OPA HTTP -->

## Little's Law Validation

Every measurement can be validated using Little's Law: L = throughput x latency, where L should approximate the connection count.

For example, SAPL JVM at 8P/64c: 103,791 req/s x 0.589ms = 61.1 (expected: 64). Ratio: 95%. This indicates a healthy measurement with no significant queueing.

Measurements where L/connections exceeds 120% indicate server overload. In the matrices above, OPA at 1P/256c shows: 21,904 x 10.99ms = 240.7 (expected: 256). Ratio: 94%. Even under heavy load, both engines validate against Little's Law, confirming measurement integrity.

## Bandwidth Considerations

At SAPL's RSocket peak (1.35M req/s), the estimated network bandwidth is approximately 3 Gbit/s (78 bytes request + 32 bytes response + TCP/IP overhead per round-trip). This exceeds 1 Gigabit Ethernet capacity. Deployments targeting maximum RSocket throughput require 10 Gigabit networking or localhost deployment.

HTTP bandwidth at 128K req/s is approximately 850 Mbit/s, within 1 Gigabit Ethernet capacity.

## Limitations and Caveats

1. **Single policy tested.** These results reflect one RBAC deny-case policy. Complex policies with external data lookups will show different characteristics.
2. **Thermally constrained hardware.** Server-grade hardware with proper cooling would produce higher absolute numbers for both engines.
3. **Localhost only.** Real network latency (100-500us per hop) would reduce the relative advantage of lower per-request overhead.
4. **SAPL uses Spring Framework.** The HTTP overhead is not inherent to SAPL but to the Spring WebFlux ecosystem.
5. **OPA has no server-side tuning.** `opa run --server` provides no threading, caching, or pool configuration. These numbers represent OPA's default and only behavior.
6. **GraalVM native image tradeoffs.** Native images trade peak throughput for instant startup and lower memory footprint. For throughput-sensitive deployments, JVM is recommended.

## Reproducing These Results

All benchmark scripts are in `sapl-node/benchmarks/`:

```bash
# OPA reference (run once, results stored)
nix develop ./opa --command bash run-opa-reference.sh ./reference/opa

# SAPL reference
SAPL_JAR=path/to/jar SAPL_NATIVE=path/to/binary bash run-sapl-reference.sh ./reference/sapl
```

## Raw Data and Benchmark Scripts

All benchmark scripts and raw measurement data are available on GitHub for independent verification:

- **Benchmark scripts:** [`sapl-node/benchmarks/`](https://github.com/heutelbeck/sapl-policy-engine/tree/master/sapl-node/benchmarks)
  - `run-opa-reference.sh` - OPA core x concurrency matrix
  - `run-sapl-reference.sh` - SAPL 6-way matrix (embedded/HTTP/RSocket x JVM/native)
  - `run-engine-comparison.sh` - Side-by-side comparison
  - `run-sweep.sh` - Core x concurrency sweep with Little's Law validation
  - `lib/common.sh` - Shared functions (CPU pinning, thermal cooldown, convergence warmup)

- **Raw CSV data:** [`sapl-node/benchmarks/opa/reference/`](https://github.com/heutelbeck/sapl-policy-engine/tree/master/sapl-node/benchmarks/opa/reference)
  - `opa-reference-matrix-2026-03-26.csv` - OPA HTTP matrix (5 core levels x 4 connection levels + unpinned)
  - `sapl/http-jvm/matrix.csv` - SAPL JVM HTTP matrix
  - `sapl/http-native/matrix.csv` - SAPL Native HTTP matrix
  - `sapl/rsocket-jvm/matrix.csv` - SAPL JVM RSocket matrix
  - `sapl/rsocket-native/matrix.csv` - SAPL Native RSocket matrix

- **OPA policy and tooling:** [`sapl-node/benchmarks/opa/`](https://github.com/heutelbeck/sapl-policy-engine/tree/master/sapl-node/benchmarks/opa)
  - `rbac.rego` - OPA RBAC policy (uses `input` for both `opa bench` and HTTP server)
  - `sapl-rbac.lua` / `opa-rbac.lua` - wrk load scripts for both engines
  - `flake.nix` - Nix devshell providing the OPA binary

All charts on this page render from JSON data embedded in the page source. Right-click and view source to inspect the raw numbers.
