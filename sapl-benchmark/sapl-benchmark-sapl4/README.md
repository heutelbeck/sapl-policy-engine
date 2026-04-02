# SAPL 4.0 Benchmark Suite

JMH-based benchmark for the embedded SAPL 4.0 Policy Decision Point. Measures latency and throughput across configurable scenarios, indexing strategies, and compiler flags.

## Quick Start

```bash
# Build the benchmark JAR
mvn package -pl sapl-benchmark/sapl-benchmark-sapl4 -DskipTests

# Run a quick latency measurement
java -jar target/sapl-benchmark-sapl4-4.0.0-SNAPSHOT.jar \
    --scenario=hospital-10 --latency-only \
    --warmup-iterations=1 --warmup-time=10 --measurement-time=10 \
    --max-forks=2 -t 1

# Export a scenario for inspection
java -jar target/sapl-benchmark-sapl4-4.0.0-SNAPSHOT.jar \
    --scenario=hospital-5 --export=/tmp/hospital-5
```

## CLI Options

| Option | Default | Description |
|--------|---------|-------------|
| `--scenario` | `rbac` | Scenario name (see Scenarios below) |
| `--seed` | `42` | RNG seed for entity graph and subscription generation |
| `--indexing` | `AUTO` | Indexing strategy: `AUTO`, `NAIVE`, `CANONICAL` |
| `--unroll` | `false` | Enable IN-operator unrolling for index matching |
| `--method` | `decideOnceBlocking` | Benchmark method: `decideOnceBlocking`, `decideStreamFirst`, `noOp` |
| `-t, --threads` | `1` | Number of concurrent benchmark threads |
| `--warmup-iterations` | `5` | Warmup iterations per fork |
| `--warmup-time` | `45` | Seconds per warmup iteration |
| `--measurement-time` | `300` | Seconds for the measurement window per fork |
| `--convergence-threshold` | `2` | Maximum CoV (percent) for convergence |
| `--convergence-window` | `3` | Consecutive forks within threshold for acceptance |
| `--max-forks` | `10` | Maximum forks before failing |
| `--latency` | `false` | Run a SampleTime latency pass after throughput |
| `--latency-only` | `false` | Run only the SampleTime latency pass |
| `--gc` | (default G1) | GC algorithm: `ZGC`, `ShenandoahGC`, etc. |
| `--heap` | `32g` | Heap size for the forked JVM |
| `--export` | | Export scenario to directory and exit |
| `-o, --output` | | Output directory for results |

## Scenarios

### Static Scenarios

| Name | Policies | Description |
|------|----------|-------------|
| `rbac` | 1 | Single RBAC policy with permission map lookup. Expected: DENY. |
| `rbac-large` | 1 | Same RBAC policy, 200 roles (10 depts x 5 locations x 4 seniority). Expected: PERMIT. |
| `simple-1/100/500/1000` | N | Baseline scaling. One matching policy, N-1 fillers with unique predicates. |
| `complex-1/100/1000` | N | Attribute-heavy scaling. Regex, array membership, nested access. |
| `shared-100/500/1000` | N | Shared action predicates across fillers, cycling through 4 actions. |

### Cedar OOPSLA Scenarios (seeded)

Translations of Cedar's OOPSLA 2024 benchmark scenarios. Entity graphs are generated deterministically from the seed. Each produces 500 subscriptions per seed.

| Name | Policies | Domain | Scaling factor = |
|------|----------|--------|------------------|
| `tinytodo-N` | 4 | Task lists with team sharing (ReBAC + ABAC) | Entity count |
| `gdrive-N` | 5 | File sharing with view hierarchy and ownership | Entity count |
| `github-N` | 8 | Repo access with multi-level org/team hierarchy | Entity count |

### Hospital Scenario (seeded)

Realistic hospital authorization domain designed for canonical index benchmarking at scale. Combines ReBAC, ABAC, and IN-list policies with massive predicate overlap.

| Name | Policies | Scaling factor = |
|------|----------|------------------|
| `hospital-N` | 33N + 5 | Number of departments |

**Scaling examples:** hospital-5 = 170 policies, hospital-100 = 3,305 policies, hospital-300 = 9,905 policies.

#### Domain Model

**Entity graph (ReBAC):** Staff -> Team -> Department. Transitive closure via `graph.transitiveClosureSet(staffGraph)`. Each department-scoped policy verifies subject membership.

**Attributes (ABAC):**
- Subject: `role` (9 roles), `clearance` (1-4)
- Resource: `type` (10 types), `department`, `sensitivity` (1-4)
- Action: 12 distinct actions

**Policy types:**
- 32 department-scoped permit policies per department (role x resourceType, mix of `action == "X"` and `action in [...]`)
- 1 sensitivity deny policy per department (`resource.sensitivity > subject.clearance`)
- 5 global emergency override policies

**Predicate overlap:** Fixed vocabulary of 9 roles, 12 actions, 10 resource types shared across all departments. At n=100: `action == "read"` appears in 1,000+ policies, `resource.type == "PatientRecord"` in 700+. The canonical index exploits this to narrow 3,305 policies to ~33 candidates.

**IN-list overlap with == predicates:** Multi-action policies like `action in ["read", "write", "create"]` contain elements that appear as explicit `action == "read"` in other roles. When the compiler unrolls IN-lists, both produce identical indexable predicates.

**Subscription generation:** 500 requests per seed with weighted distributions: actions biased toward "read" (~39%), resource types toward PatientRecord/LabResult/CareNote, departments 80% home department. Produces ~15-17% PERMIT, ~83-85% DENY, 0% INDETERMINATE.

**Entity graph generation:** Per department: 2 teams, 5 staff with random role assignments. Cross-department team membership with p=0.05 per other department.

## Benchmark Scripts

Located in `sapl-benchmark/scripts/`. All scripts use `lib/common.sh` for CPU pinning, thermal throttling detection, and profile defaults.

### Latency Benchmark

```bash
scripts/run-latency-bench.sh [profile] [output-dir]
```

| Profile | Apps | Scaling | Seeds | Indexing | Unroll | Purpose |
|---------|------|---------|-------|----------|--------|---------|
| `quick` | github | 5, 50 | 3 | AUTO | false | End-to-end validation |
| `rigorous` | tinytodo, gdrive, github | 5-50 (7 values) | 200 | AUTO | false | Cedar-comparable data |
| `hospital-scaling` | hospital | 5-300 (9 values) | 1 | AUTO | false | Scaling curve |
| `hospital-index` | hospital | 5, 50, 100, 300 | 1 | NAIVE, CANONICAL, AUTO | false | Index comparison |
| `hospital-unroll` | hospital | 5, 50, 100, 300 | 1 | NAIVE, CANONICAL, AUTO | false | Index + unroll comparison |

### Other Scripts

| Script | Purpose |
|--------|---------|
| `run-embedded-sapl4.sh` | Full embedded benchmark (scenarios x threads) |
| `run-embedded-native.sh` | GraalVM native image benchmark |
| `run-server-http.sh` | HTTP server throughput via wrk |
| `run-server-rsocket.sh` | RSocket server throughput |
| `run-rigour-sweep.sh` | Warmup parameter calibration |
| `run-measurement-sweep.sh` | Measurement time calibration |
| `run-index-shared.sh` | NAIVE vs CANONICAL with shared predicates |
| `run-index-worst-case.sh` | NAIVE vs CANONICAL worst case |
| `setup-cpu.sh` / `reset-cpu.sh` | CPU frequency pinning for reproducible benchmarks |
| `summarize-latency.sh` | Aggregates latency results into summary.csv and summary.md |

## Output Format

Each benchmark run produces:
- **CSV** (`scenario_seedN_INDEXING_method_Nt.csv`): Throughput per fork, latency percentiles, decision counts (PERMIT/DENY/INDETERMINATE/NOT_APPLICABLE) as comment headers
- **JSON** (`..._latency.json`): JMH SampleTime results with full histogram

The CSV includes metadata:
```
# Command: --scenario=hospital-100 --indexing=AUTO ...
# Latency p50 (ns): 8944
# Latency p99 (ns): 28576
# Decisions PERMIT: 83
# Decisions DENY: 417
# Decisions INDETERMINATE: 0
# Decisions NOT_APPLICABLE: 0
```

## Compiler Flags

The benchmark exercises the compiler flags from `pdp.json`:

```json
{
  "compilerFlags": {
    "indexing": "AUTO",
    "unrollInOperator": false,
    "minPoliciesForCanonical": 10,
    "minSharingForCanonical": 1.5
  }
}
```

- **indexing**: `NAIVE` (linear scan), `CANONICAL` (SACMAT '21 count-and-eliminate), `AUTO` (heuristic selection based on policy count and predicate sharing)
- **unrollInOperator**: Transforms `EXPR in [a, b, c]` into `EXPR == a || EXPR == b || EXPR == c` for improved index matching
- **minPoliciesForCanonical**: AUTO mode threshold - use NAIVE below this count
- **minSharingForCanonical**: AUTO mode threshold - use NAIVE if predicate sharing ratio is below this

## Smoke Tests

`ScenarioSmokeTests` validates all scenarios across multiple seeds:

```bash
mvn test -pl sapl-benchmark/sapl-benchmark-sapl4 -Dtest="ScenarioSmokeTests"
```

Two test groups:
1. **CompilationAndEvaluation**: All scenarios compile without errors, zero INDETERMINATE across all 500 subscriptions
2. **IndexConsistency**: NAIVE and CANONICAL produce identical decisions for every subscription

Prints compile times per scenario for performance regression detection.

## Convergence Methodology

Based on Georges et al. (OOPSLA 2007). Each fork is an independent JVM process. Throughput convergence is checked across fork-level results using the coefficient of variation (CoV). Confidence intervals use the t-distribution for small sample sizes.

Calibration sweeps confirmed:
- Warmup parameters have no measurable impact (JIT compiles within seconds)
- 30s measurement time is sufficient for stable results (CoV < 0.03%)
- See `raw-data/results/` in `sapl-papers` repo for calibration data
