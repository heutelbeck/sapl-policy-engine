# Benchmark Profiles and Run Plan

## Script Structure

```
scripts/
  lib/
    common.sh          # cooldown, pinning, profile defaults, temp reading
    sapl-wrk.lua       # wrk POST with subscription from file
    opa-wrk.lua        # wrk POST with subscription wrapped in {"input":...}
  setup-cpu.sh         # disable turbo, fix 4GHz, performance profile (run manually)
  reset-cpu.sh         # restore CPU defaults (run manually)
  run-embedded-sapl4.sh  # JMH forks(1) benchmark per scenario
  run-embedded-sapl3.sh  # SAPL 3 JMH benchmark (rbac only)
  run-embedded-native.sh # sapl-node native benchmark per scenario
  run-http-sapl.sh       # SAPL server + wrk core sweep per scenario
  run-http-opa.sh        # OPA server + wrk core sweep (rbac only)
  run-rsocket-sapl.sh    # SAPL RSocket server + loadtest core sweep per scenario
  run-all.sh             # orchestrator: run-all.sh [quick|base|rigorous]
```

## Binary Paths (environment variables)

```bash
SAPL_NODE_JAR   # sapl-node fat JAR (default: ../../sapl-node/target/sapl-node-4.0.0-SNAPSHOT.jar)
SAPL_NATIVE     # sapl-node native binary (default: ../../sapl-node/target/sapl)
SAPL4_BENCH_JAR # sapl-benchmark-sapl4 shaded JAR
SAPL3_BENCH_JAR # sapl-benchmark-sapl3 shaded JAR
OPA_BINARY      # opa binary (default: opa, must be in PATH)
```

## Run Sequence

### Per embedded benchmark
1. Cooldown to 40C
2. Run benchmark (pinned to N P-cores, -Xmx32g)

### Per server benchmark (HTTP/RSocket)
1. Clear config directory
2. Export scenario (policies + pdp.json + subscription.json)
3. Cooldown to 40C
4. Start server (pinned to N P-cores)
5. Wait for health
6. Warmup (convergence)
7. Measure (30s)
8. Record temperature
9. Stop server

### run-all.sh sequence
1. Validate all scenario sanity checks (fail fast)
2. Phase 1: OPA HTTP baseline (rbac, core sweep)
3. Phase 2: SAPL HTTP sweep (per scenario, JVM + Native, core sweep + unpinned)
4. Phase 3: SAPL RSocket sweep (per scenario, JVM + Native, core sweep + unpinned)
5. Phase 4: Embedded SAPL 4 JVM sweep (per scenario, JMH forks(1), core/thread sweep)
6. Phase 5: Embedded SAPL 4 Native sweep (per scenario, core/thread sweep)
7. Phase 6: Embedded SAPL 3 baseline (rbac only, core/thread sweep)

## Output Structure

```
results/<profile>-<timestamp>/
  opa-http/
  sapl-http-jvm/
  sapl-http-native/
  sapl-rsocket-jvm/
  sapl-rsocket-native/
  embedded-sapl4-jvm/
  embedded-sapl4-native/
  embedded-sapl3/
```

## Profiles

### Quick (default)

Purpose: validate that all scenarios, benchmarks, scripts, and infrastructure work. Not for data.

| Parameter | Embedded | HTTP | RSocket |
|-----------|----------|------|---------|
| Warmup | 1 x 3s/fork | 5s | 5s |
| Measurement | 5s/fork | 10s | 10s |
| Convergence | 2 forks, 5% | n/a | n/a |
| Max forks | 3 | n/a | n/a |
| Scenarios | rbac | rbac | rbac |
| Methods | decideOnceBlocking | n/a | n/a |
| Threads/cores | 1t/1P | 4P, 64c | 4P, 4x256VT |
| Latency | no | no | no |
| Engines | SAPL4 JVM only | SAPL JVM + OPA | SAPL JVM |

Estimated: ~2 minutes

### Base

Purpose: first meaningful data for development decisions.

| Parameter | Embedded | HTTP | RSocket |
|-----------|----------|------|---------|
| Warmup | 3 x 10s/fork | convergence 3x3s/5% | convergence 3x3s/5% |
| Measurement | 30s/fork | 30s | 30s |
| Convergence | 3 forks, 2% | n/a | n/a |
| Max forks | 5 | n/a | n/a |
| Scenarios | rbac, simple-1, simple-100, complex-1, complex-100 | rbac | rbac |
| Methods | decideOnceBlocking | n/a | n/a |
| Threads/cores | 1t/1P, 8t/8P | 1P, 4P, 8P; 64c, 128c | 1P, 4P, 8P; 4x256VT |
| Latency | yes | yes (wrk --latency) | no |
| Engines | SAPL4 JVM, SAPL4 Native, SAPL3 JVM (rbac only) | SAPL JVM, SAPL Native, OPA | SAPL JVM, SAPL Native |
| + unpinned | no | yes (per engine) | yes (per runtime) |

Estimated: ~2.5 hours

### Rigorous

Purpose: final reference data. Statistically sound.

| Parameter | Embedded | HTTP | RSocket |
|-----------|----------|------|---------|
| Warmup | 5 x 45s/fork | convergence 3x3s/5% | convergence 3x3s/5% |
| Measurement | 300s/fork | 30s | 30s |
| Convergence | 3 forks, 2% | n/a | n/a |
| Max forks | 10 | n/a | n/a |
| Scenarios | all 9 | all 9 | all 9 |
| Methods | decideOnceBlocking (all), decideStreamFirst (all), noOp (once) | n/a | n/a |
| Threads/cores | 1t/1P, 2t/2P, 4t/4P, 8t/8P | 1P, 2P, 4P, 6P, 8P; 32c, 64c, 128c, 256c | 1P, 2P, 4P, 6P, 8P; 4x256VT |
| Latency | yes (1-thread only) | yes (wrk --latency) | no |
| Cooldown | 40C between configs | 40C between configs | 40C between configs |
| Engines | SAPL4 JVM, SAPL4 Native, SAPL3 JVM (rbac only) | SAPL JVM, SAPL Native, OPA | SAPL JVM, SAPL Native |
| + unpinned | no | yes (per engine) | yes (per runtime) |
| + GC comparison | G1, Shenandoah, ZGC (rbac, 1t+8t) | no | no |
| + fork validation | forks(0) vs forks(1) (rbac, 1t+8t) | no | no |

Estimated: ~24 hours (run overnight, console boot, fixed frequency, no turbo)

## All 9 scenarios

rbac, rbac-large, simple-1, simple-100, simple-500, simple-1000, complex-1, complex-100, complex-1000

## Environment checklist

- [ ] Power profile: performance
- [ ] CPU frequency: fixed 4GHz, no turbo (rigorous). Turbo OK for quick/base.
- [ ] Console boot (rigorous only)
- [ ] No background services (rigorous only)
- [ ] Heap: -Xmx32g
- [ ] Correct JDK version
- [ ] All scenarios pass sanity check before measurement
