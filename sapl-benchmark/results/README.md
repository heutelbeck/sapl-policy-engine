# Benchmark Calibration Results

Hardware: i9-13900KS (8P+16E, 32 logical), CPU pinned to 4.0 GHz (turbo on, fixed frequency via `setup-cpu.sh`).

## 1. Reference Run

`embedded-sapl4-jvm/reference/rbac_decideOnceBlocking_8t_fork3.json`

A single rigorous fork with maximum parameters to establish a convergence ceiling:
- Warmup: 5 iterations x 45 seconds (225s total warmup)
- Measurement: 300 seconds (5 minutes)
- Scenario: rbac / decideOnceBlocking / 8 threads pinned to CPUs 0-15

Result: **26,797,842 ops/s**

This number serves as the reference target for the parameter sweeps below. The question: how much warmup and measurement time do we actually need to reach this number?

## 2. Warmup Parameter Sweep

`rigour-sweep-20260327-121700/`

Swept warmup iterations (1, 2, 3, 5) x warmup time (3, 5, 10, 15, 30, 45 seconds) with fixed 30s measurement, 2 forks each. Same scenario as reference.

Finding: **warmup parameters have no measurable impact.** All 24 configurations land within 1-6% of the reference, with no trend favoring longer warmup. The 1x3s configuration (26.36M, -1.6%) performs as well as 5x45s (26.17M, -2.4%). The JIT compiles this workload fully within the first few seconds.

## 3. Measurement Time Sweep

`measurement-sweep-20260327-134513/`

Swept measurement duration (5, 10, 15, 30, 60, 120 seconds) with fixed 1x3s warmup, 2 forks each. Same scenario as reference.

Results:

| Measurement | Mean (ops/s) | CoV | vs Reference |
|---|---|---|---|
| 5s | 25,232,502 | 2.48% | -5.8% |
| 10s | 28,252,760 | 11.80% | +5.4% (outlier) |
| 15s | 25,880,838 | 0.97% | -3.4% |
| 30s | 26,156,619 | 0.03% | -2.4% |
| 60s | 26,019,130 | 1.45% | -2.9% |
| 120s | 26,169,368 | 0.46% | -2.3% |

Finding: **30 seconds is the minimum measurement time for stable results.** At 5s, numbers are 5.8% low and noisy. At 10s, outlier forks appear. At 30s, fork-to-fork CoV drops to 0.03% and the result stabilizes at ~26.2M. Longer durations (60-120s) add no additional precision.

The residual -2.4% gap between 30s runs and the reference is within normal fork-to-fork variance. The reference itself was only 3 forks.

## 4. Chosen Profile Parameters

Based on these sweeps, the three benchmark profiles use identical warmup (1x3s) and differ only in measurement rigor:

| Profile | Measurement | Convergence | Max Forks | Latency | Purpose |
|---|---|---|---|---|---|
| quick | 10s | CoV < 50% / 2 forks | 2 | no | Validate sweep runs end-to-end |
| base | 30s | CoV < 2% / 3 forks | 5 | yes | Production-quality numbers |
| rigorous | 30s | CoV < 2% / 3 forks | 10 | yes | Noisy scenarios get more attempts |

All profiles sweep the full scenario and thread matrix (9 scenarios x 4 thread counts = 36 runs, plus rigorous extras for decideStreamFirst and noOp).
