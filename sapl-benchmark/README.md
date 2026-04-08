# SAPL Benchmark Suite

## Methodology

This benchmark suite produces reproducible, traceable performance data for SAPL. Every number traces to a stored CSV file. Every remote measurement is validated against Little's Law.

### Environment Control

Benchmarks run on a dedicated machine. Background services compete for CPU time and cache, so we minimize them.

Each benchmark run records the full environment: CPU model, core count and topology, OS version, kernel version, JVM version (exact build string), GC algorithm, power profile, and cooling setup. This metadata is stored alongside the measurement data.

The power profile is set to performance before any run:

```bash
powerprofilesctl set performance
```

### CPU Frequency Control

Modern CPUs dynamically scale frequency based on load, temperature, and power budgets. Intel's turbo boost runs cores at 5.6-6.0 GHz in short bursts, then decays to 4.0-4.5 GHz as the thermal/power budget is consumed. This creates a burst/decay pattern where the first measurement iteration runs 30-40% faster than subsequent ones, purely from frequency scaling, not from the software under test.

For `full` quality runs, disable turbo boost and fix all cores to a sustainable frequency:

```bash
scripts/cpu-setup.sh    # disable turbo, fix 4GHz, performance profile
scripts/cpu-reset.sh    # restore defaults after benchmarking
```

The fixed frequency produces lower absolute numbers than turbo-boosted peaks, but the numbers are stable and reproducible across runs.

### CPU Pinning

Server and client processes run on separate physical cores. Without pinning, the OS scheduler can place both on the same cores, and the measurement tool steals CPU time from the system under test.

On hybrid architectures (e.g., i9-13900KS), P-cores are used for the server, E-cores for the client. Core sweeps (1, 2, 4, 6, 8 P-cores) show how each engine scales. The pinning formula for N P-cores:
- Server: CPUs 0 to (N*2-1) (P-cores with hyper-threading siblings)
- Client: E-cores only

The client is always pinned to E-cores regardless of how many P-cores the server uses. This eliminates client CPU assignment as a variable.

### Heap Size

JVM benchmarks allocate millions of short-lived objects per second. With default heap sizes, GC runs frequently during measurement and reduces throughput. We use `-Xmx32g` to give the G1 collector enough headroom that young generation collections are infrequent during measurement windows. The benchmark runner triggers `System.gc()` between forks.

### Warmup and Measurement

Each JMH fork is an independent JVM process with its own JIT compilation. Calibration runs (stored in `reference/`) showed that JIT compilation stabilizes within seconds for these workloads: 1 warmup iteration of 3 seconds produces the same throughput as 5 iterations of 45 seconds. This is consistent with the small code footprint of policy evaluation.

Each fork runs 1x3s warmup followed by a 30s measurement window (`full`) or 10s (`quick`). Calibration showed that 30s produces CoV under 1% for most scenarios, while 5s is measurably noisy. Calibration data is stored in `reference/`.

### Convergence

The benchmark runner launches forks sequentially and checks the coefficient of variation (CoV) across the last N fork results after each fork completes.

| Quality | Convergence | Window | Max Forks |
|---------|-------------|--------|-----------|
| quick   | CoV < 10%   | 2      | 2         |
| full    | CoV < 2%    | 3      | 5         |

If the maximum forks are reached without convergence, the result is reported with its CoV rather than discarded. Wide CoV indicates structural variance (hyper-threading scheduling, thermal effects) rather than methodology failure.

This follows the CoV heuristic from Georges et al. [4] applied at the fork level, where each sample is an independent VM invocation. Fork-level samples avoid the pseudoreplication problem of within-fork iteration samples.

**Remote benchmarks (HTTP/RSocket):** warmup uses convergence-based detection: 3 consecutive 3-second intervals within 5% of their mean.

### One Benchmark Method Per JVM

Each benchmark method (decideOnceBlocking, decideStreamFirst, noOp) runs in its own JVM invocation. Running multiple methods in the same JVM causes JIT cross-contamination.

### Thermal Management

Between each configuration change, the scripts wait for the CPU package temperature to drop below the quality profile's cool target (90C for `quick`, 60C for `full`).

### Little's Law Validation

Every remote benchmark data point is checked against Little's Law: `L = throughput x latency`. L should approximate the connection count. Values above 120% indicate server overload.

### Reported Metrics

| Metric | Source |
|--------|--------|
| Mean throughput (ops/s or req/s) | Direct measurement |
| 95% confidence interval | t-distribution from fork-level means |
| Coefficient of variation (CoV%) | stddev/mean across forks |
| Latency percentiles (p50, p90, p99, p99.9) | JMH SampleTime or wrk --latency |
| Latency confidence interval | JMH internal (from sample distribution) |

## Profiles

Profiles are split into **quality** (how rigorously to measure) and **experiments** (what to measure). Profile files live in `scripts/lib/profiles/`.

### Quality

| Parameter | quick | full |
|-----------|-------|------|
| Measurement time | 10s | 30s |
| Convergence | CoV < 10%, 2 forks, max 2 | CoV < 2%, 3 forks, max 5 |
| Cool target | 90C | 60C |

### Experiments

| Profile | What it measures |
|---------|-----------------|
| `embedded.sh` | Scenarios x threads x indexing (JVM and native) |
| `server-http.sh` | Scenarios x cores x connections (HTTP via wrk) |
| `server-rsocket.sh` | Scenarios x cores x connections x VT (RSocket) |
| `latency-cedar.sh` | Cedar OOPSLA scenarios, 200 seeds x 7 scaling factors |
| `latency-hospital-scaling.sh` | Hospital department count scaling curve |
| `latency-hospital-index.sh` | Hospital indexing strategy x unrolling matrix |

## Running Benchmarks

### Build

```bash
./scripts/build-nix.sh       # JVM JARs + native image (recommended)
./scripts/build.sh           # JVM JARs only (native skipped if no GraalVM)
```

All binaries are copied to `sapl-benchmark/bin/` where the run scripts expect them. This directory survives `mvn clean`.

### Embedded (JMH forks(1), flat classpath)

```bash
./scripts/run-embedded-jvm.sh quick /path/to/results
./scripts/run-embedded-jvm.sh full /path/to/results
```

### Embedded native (GraalVM)

```bash
./scripts/run-embedded-native.sh quick /path/to/results
```

### HTTP server (wrk)

```bash
./scripts/run-server-http.sh full /path/to/results
```

### RSocket server

```bash
./scripts/run-server-rsocket.sh full /path/to/results
```

### Latency JVM (seeds x scaling factors, JMH SampleTime)

```bash
./scripts/run-latency-jvm.sh quick latency-cedar /path/to/results
./scripts/run-latency-jvm.sh full latency-hospital-scaling /path/to/results
```

### Latency native (seeds x scaling factors, HdrHistogram)

```bash
./scripts/run-latency-native.sh quick latency-cedar /path/to/results
./scripts/run-latency-native.sh full latency-hospital-index /path/to/results
```

Quick mode caps seeds to 3 and scaling factors to the first 2 values.

### Quick assessment (Spring Boot fat JAR)

```bash
taskset -c 0-15 java -Xmx32g -jar sapl-node.jar benchmark -t 8
```

The `sapl-node benchmark` command uses `forks(0)` (required by nested JAR classloader). The `forks(0)` vs `forks(1)` equivalence is validated in `reference/fork-comparison/`.

### Summarize results

```bash
python3 scripts/lib/bench.py summarize /path/to/results/embedded-jvm-quick-20260403-150718
```

Produces `summary.csv` (machine-readable, includes 95% CI bounds) and `summary.md` (human-readable table). Auto-runs at the end of each benchmark script.

## Scripts

```
bin/                               # stable build output (created by build.sh)
  sapl-node.jar                    # Spring Boot fat JAR
  sapl-benchmark-sapl4.jar         # JMH benchmark runner
  sapl                             # native binary (optional)
scripts/
  build.sh                         # build all binaries into bin/
  build-nix.sh                     # convenience: enters GraalVM Nix shell + build.sh
  lib/
    bench.py                     # statistics, convergence, CSV/JSON I/O, aggregation
    common.sh                    # env detection, pinning, thermal, server lifecycle
    profiles/quality/            # quick.sh, full.sh
    profiles/experiments/        # embedded.sh, server-http.sh, latency-cedar.sh, ...
    sapl-wrk.lua                 # wrk POST with subscription from file
    opa-wrk.lua                  # wrk POST for OPA
  cpu-setup.sh                   # disable turbo, fix frequency (run manually)
  cpu-reset.sh                   # restore CPU defaults (run manually)
  run-embedded-jvm.sh            # JMH forks(1) benchmark
  run-embedded-native.sh         # native binary benchmark
  run-server-http.sh             # HTTP server benchmark via wrk
  run-server-rsocket.sh          # RSocket server benchmark
  run-latency-jvm.sh             # JVM latency across seeds and scaling factors
  run-latency-native.sh          # native latency across seeds and scaling factors
  calibrate-warmup-time.sh       # find minimum warmup (run once)
  calibrate-measurement-time.sh  # find minimum measurement duration (run once)
  summarize-latency.sh           # aggregate latency results
```

## References

[1] A. Shipilev. "Re: Warmup Iterations." jmh-dev mailing list, January 2014. https://mail.openjdk.org/pipermail/jmh-dev/2014-January/000350.html

[2] E. Barrett, C. F. Bolz-Tereick, R. Killick, S. Mount, and L. Tratt. 2017. Virtual Machine Warmup Blows Hot and Cold. In Proceedings of the ACM on Programming Languages 1, OOPSLA, Article 52. https://doi.org/10.1145/3133876

[3] L. Traini, V. Cortellessa, D. Di Pompeo, and M. Tucci. 2023. Towards Effective Assessment of Steady State Performance in Java Software: Are We There Yet? Empirical Software Engineering 28, 1, Article 13. https://doi.org/10.1007/s10664-022-10247-x

[4] A. Georges, D. Buytaert, and L. Eeckhout. 2007. Statistically Rigorous Java Performance Evaluation. In Proceedings of the 22nd ACM SIGPLAN Conference on Object-Oriented Programming, Systems, Languages, and Applications (OOPSLA '07). ACM, 57-76. https://doi.org/10.1145/1297027.1297033

[5] T. Kalibera and R. Jones. 2013. Rigorous Benchmarking in Reasonable Time. In Proceedings of the 2013 ACM SIGPLAN International Symposium on Memory Management (ISMM '13). ACM, 63-74. https://doi.org/10.1145/2464157.2464160

[6] C. Laaber, S. Wursten, H. C. Gall, and P. Leitner. 2020. Dynamically Reconfiguring Software Microbenchmarks: Reducing Execution Time Without Sacrificing Result Quality. In Proceedings of the 28th ACM Joint European Software Engineering Conference and Symposium on the Foundations of Software Engineering (ESEC/FSE 2020). ACM, 989-1001. https://doi.org/10.1145/3368089.3409683
