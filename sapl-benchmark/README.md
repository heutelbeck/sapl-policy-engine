# SAPL Benchmark Suite

## Methodology

This benchmark suite produces reproducible, traceable performance data for SAPL. Every number traces to a stored CSV file. Every remote measurement is validated against Little's Law. Deviations from this methodology are documented per data point.

### Environment Control

Benchmarks run on a dedicated machine booted to console. No display server, compositor, or desktop environment. Background services compete for CPU time and cache, so we eliminate them.

Each benchmark run records the full environment: CPU model, core count and topology, OS version, kernel version, JVM version (exact build string), GC algorithm, power profile, and cooling setup. This metadata is stored alongside the measurement data.

The power profile is set to performance before any run:

```bash
powerprofilesctl set performance
cat /sys/devices/system/cpu/cpu0/cpufreq/energy_performance_preference
# should print: performance
```

### CPU Frequency Control

Modern CPUs dynamically scale frequency based on load, temperature, and power budgets. Intel's turbo boost runs cores at 5.6-6.0 GHz in short bursts, then decays to 4.0-4.5 GHz as the thermal/power budget is consumed. This creates a burst/decay pattern where the first measurement iteration runs 30-40% faster than subsequent ones, purely from frequency scaling, not from the software under test.

We eliminate this by disabling turbo boost and fixing all cores to a sustainable frequency:

```bash
# Disable turbo boost
echo 1 | sudo tee /sys/devices/system/cpu/intel_pstate/no_turbo

# Fix all cores to 4.0 GHz (adjust for your CPU's sustainable all-core frequency)
echo 4000000 | sudo tee /sys/devices/system/cpu/cpu*/cpufreq/scaling_max_freq
echo 4000000 | sudo tee /sys/devices/system/cpu/cpu*/cpufreq/scaling_min_freq

# Verify
cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq
# all cores should report 4000000
```

These settings reset on reboot. The fixed frequency produces lower absolute numbers than turbo-boosted peaks, but the numbers are stable and reproducible across runs.

### CPU Pinning

Server and client processes run on separate physical cores. Without pinning, the OS scheduler places both on the same cores, and the measurement tool steals CPU time from the system under test.

On the i9-13900KS, P-cores are CPUs 0-15 (8 physical cores with hyper-threading), E-cores are CPUs 16-31. We pin the server to a subset of P-cores and the client to the remaining cores:

```bash
# Server on first 4 P-cores (CPUs 0-7), client on E-cores only (CPUs 16-31)
taskset -c 0-7 java -Xmx32g -jar sapl-node.jar server ...
taskset -c 16-31 wrk -t2 -c64 ...

# For embedded benchmarks, pin the JVM to all P-cores with large heap
taskset -c 0-15 java -Xmx32g -jar sapl-node.jar benchmark --baseline -t 8
```

### Heap Size

JVM benchmarks allocate millions of short-lived objects per second. With default heap sizes, GC runs frequently during measurement and reduces throughput. We use `-Xmx32g` (or larger) to give the G1 collector enough headroom that young generation collections are infrequent during measurement windows. The benchmark runner triggers `System.gc()` between iterations to start each iteration with a clean heap.

Core sweeps (1, 2, 4, 6, 8 P-cores) show how each engine scales. The pinning formula for N P-cores:
- Server: CPUs 0 to (N*2-1) (P-cores with hyper-threading siblings)
- Client: CPUs 16-31 (E-cores only)

The client is always pinned to E-cores regardless of how many P-cores the server uses. This eliminates client CPU assignment as a variable. Validated by comparing wrk throughput on E-cores only vs remaining P-cores + E-cores: no meaningful difference. The 2-thread wrk client does not benefit from P-core speed. Comparison data is stored in `reference/client-pinning/`.

Unpinned runs are collected as a separate data point and labeled as such. They are not directly comparable to pinned runs.

### Steady-State Detection and Measurement

JIT compilation takes unpredictable time. C1 (fast compile) happens in seconds, C2 (optimized compile) takes 30-60 seconds, and speculative optimizations may deoptimize and recompile minutes into a run. Fixed-count warmup (e.g., "3 iterations of 5 seconds") provides no guarantee that the JIT has reached steady state.

JMH, the standard Java microbenchmarking harness, deliberately does not implement convergence detection. Its author Aleksey Shipilev has stated: "you cannot generally figure out whether you hit steady state or not. That 'stabilization' metric is very hard, if not impossible, to reliably define" [1]. Barrett et al. found that only 37% of VM-benchmark pairs demonstrate a clean warmup-then-steady-state pattern [2]. Traini et al. confirmed that 43.5% of (VM, benchmark) pairs are inconsistent across executions [3].

Given this, we do not claim to detect steady state. Instead, we use a multi-level experiment design following Georges et al. [4] and Kalibera and Jones [5]:

**Level 2 (VM invocations):** each JMH fork is an independent JVM process with its own JIT compilation. The benchmark runner launches forks one at a time. Each fork runs generous internal warmup (20 iterations of 45 seconds = 15 minutes), then a single long measurement window (300 seconds). The internal warmup count is deliberately high because we cannot know when steady state is reached.

**Level 1 (measurement):** each fork produces one throughput number from its 300-second measurement window.

**Convergence as a quality gate:** after each fork completes, the runner checks the coefficient of variation (CV) across the last N fork results. If N consecutive forks produce results within the convergence threshold (default: 2%), the measurement is accepted. If the maximum number of forks is reached without convergence, the benchmark fails rather than producing unreliable data. This is the CoV heuristic from Georges et al. [4], applied at the fork level where each sample is an independent VM invocation. Applying it across forks rather than across iterations within a single fork avoids the pseudoreplication problem: fork-level samples are genuinely independent.

**Remote benchmarks (HTTP/RSocket):** each convergence check is a 3-second wrk or loadtest interval. Warmup is complete when 3 consecutive intervals produce throughput within 5% of their mean. The wider threshold accounts for network and OS scheduling variance. OPA (Go) converges immediately. SAPL (JVM) typically needs 15-45 seconds.

### One Benchmark Method Per JVM

Each benchmark method (decideOnceBlocking, decideStreamFirst, noOp) runs in its own JVM invocation. Running multiple methods in the same JVM causes JIT cross-contamination: the JIT makes optimization decisions during method A that change method B's performance. We observed this directly when noOp (1.26 billion ops/s) ran before decideOnceBlocking and skewed the convergence warmup.

The benchmark tool enforces this by accepting a single `--method` argument:

```bash
java -jar sapl-benchmark-sapl4.jar --scenario baseline --method decideOnceBlocking -t 8
java -jar sapl-benchmark-sapl4.jar --scenario baseline --method decideStreamFirst -t 8
```

### Rigorous benchmarks vs quick assessment

The `sapl-benchmark-sapl4` module is the rigorous benchmark tool. It uses `forks(1)` on a flat classpath, giving JMH full control over JVM isolation.

The `sapl-node benchmark` command is a quick assessment tool for users to evaluate their own policy sets. It runs in the Spring Boot fat JAR with `forks(0)` (required by the nested JAR classloader). The `forks(0)` vs `forks(1)` equivalence is validated and stored in `reference/fork-comparison/`.

### Measurement Duration

Each fork runs a single 300-second measurement window (default). Short iterations (1-5 seconds) are unsuitable for JVM benchmarks because JIT compilation, GC cycles, and OS scheduling noise dominate short windows. The 300-second window is long enough to average out GC pauses and provide a stable throughput number for the fork.

Remote benchmarks use 30 seconds sustained load per data point. The shorter window is acceptable because the server JVM warmup is handled separately during the convergence phase.

### Thermal Management

Between each configuration change (different core count, different concurrency level), the scripts wait for the CPU package temperature to drop below 50C. Every data point includes the CPU package temperature at the time of measurement. Data points collected during thermal throttling are discarded and re-measured after cooldown.

Temperature is read from the hardware monitor:

```bash
cat /sys/class/hwmon/hwmon*/temp1_input  # millidegrees C
```

### Little's Law Validation

Every remote benchmark data point is checked against Little's Law:

```
L = throughput x latency
```

L should approximate the connection count. A healthy measurement shows L/connections within 110%. Values above 120% indicate server overload and queueing; these data points are flagged.

For embedded benchmarks, the derived latency is cross-checked against the measured latency from JMH's SampleTime pass:

```
derived_latency_ns = threads x 1,000,000,000 / throughput
```

### Data Traceability

Every number traces to a specific CSV file in this repository. Embedded results are stored as dated CSV and Markdown reports. HTTP and RSocket results are stored as CSV matrices with columns for cores, connections, throughput, latency percentiles, and temperature. OPA results use the same CSV format.


### Visualization

Charts and tables are generated by scripts from the stored CSV data. No hand-authored visualizations. The pipeline:

```
CSV data (stored) -> script (in repo) -> chart/table (in document)
```

A chart is reproducible by running the script against the stored data.

### Fairness

When comparing engines (SAPL vs OPA, SAPL 4 vs SAPL 3), both run on the same hardware, same power profile, same thermal conditions, with the same measurement tool and duration. The policy under test is semantically identical across engines, verified by testing multiple input combinations. Results report where each engine wins and where it loses.

### Reported Metrics

For each benchmark configuration:

| Metric | Source |
|--------|--------|
| Mean throughput (ops/s or req/s) | Direct measurement |
| 95% confidence interval | t-distribution (not z) from fork-level means |
| Coefficient of variation (CV%) | stddev/mean across forks, flags instability |
| Latency percentiles (p50, p90, p99, p99.9) | JMH SampleTime or wrk --latency |
| CPU temperature at measurement time | hwmon sensor reading |
| Little's Law ratio (remote only) | Derived, validates measurement health |
| Scaling efficiency (multi-thread) | throughput_N / (N x throughput_1) |

## Directory Structure

```
sapl-benchmark/
  README.md                       # This file
  sapl-benchmark-sapl4/         # Maven module: forks(1) JMH runner for validation
  sapl-benchmark-sapl3/           # Maven module: SAPL 3.0 version comparison
  scripts/                        # Benchmark orchestration scripts
  reference/                      # Stored measurement data (CSV, reports)
  archive/                        # Previous scripts, preserved for reference
```

## Running Benchmarks

### Embedded (SAPL 4, fat JAR, forks(0))

```bash
taskset -c 0-15 java -Xmx32g -jar sapl-node/target/sapl-node-4.0.0-SNAPSHOT.jar benchmark --baseline
taskset -c 0-15 java -Xmx32g -jar sapl-node/target/sapl-node-4.0.0-SNAPSHOT.jar benchmark --baseline -b decideStreamFirst -t 8
```

### Rigorous (SAPL 4, flat classpath, forks(1))

```bash
mvn package -pl :sapl-benchmark-sapl4 -am -DskipTests -q
taskset -c 0-15 java -Xmx32g -jar sapl-benchmark/sapl-benchmark-sapl4/target/sapl-benchmark-sapl4-4.0.0-SNAPSHOT.jar --scenario baseline -t 8
```

### Version comparison (SAPL 3)

```bash
mvn package -pl :sapl-benchmark-sapl3 -am -DskipTests -q
taskset -c 0-15 java -Xmx32g -jar sapl-benchmark/sapl-benchmark-sapl3/target/sapl-benchmark-sapl3-4.0.0-SNAPSHOT.jar /tmp/sapl3-results scientific
```

## References

[1] A. Shipilev. "Re: Warmup Iterations." jmh-dev mailing list, January 2014. https://mail.openjdk.org/pipermail/jmh-dev/2014-January/000350.html

[2] E. Barrett, C. F. Bolz-Tereick, R. Killick, S. Mount, and L. Tratt. 2017. Virtual Machine Warmup Blows Hot and Cold. In Proceedings of the ACM on Programming Languages 1, OOPSLA, Article 52. https://doi.org/10.1145/3133876

[3] L. Traini, V. Cortellessa, D. Di Pompeo, and M. Tucci. 2023. Towards Effective Assessment of Steady State Performance in Java Software: Are We There Yet? Empirical Software Engineering 28, 1, Article 13. https://doi.org/10.1007/s10664-022-10247-x

[4] A. Georges, D. Buytaert, and L. Eeckhout. 2007. Statistically Rigorous Java Performance Evaluation. In Proceedings of the 22nd ACM SIGPLAN Conference on Object-Oriented Programming, Systems, Languages, and Applications (OOPSLA '07). ACM, 57-76. https://doi.org/10.1145/1297027.1297033

[5] T. Kalibera and R. Jones. 2013. Rigorous Benchmarking in Reasonable Time. In Proceedings of the 2013 ACM SIGPLAN International Symposium on Memory Management (ISMM '13). ACM, 63-74. https://doi.org/10.1145/2464157.2464160

[6] C. Laaber, S. Wursten, H. C. Gall, and P. Leitner. 2020. Dynamically Reconfiguring Software Microbenchmarks: Reducing Execution Time Without Sacrificing Result Quality. In Proceedings of the 28th ACM Joint European Software Engineering Conference and Symposium on the Foundations of Software Engineering (ESEC/FSE 2020). ACM, 989-1001. https://doi.org/10.1145/3368089.3409683
