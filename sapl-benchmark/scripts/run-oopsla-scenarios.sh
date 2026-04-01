#!/usr/bin/env bash
#
# Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
#
# SPDX-License-Identifier: Apache-2.0
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# OOPSLA 2024 scenario benchmark: Cedar-equivalent applications (gdrive,
# github, TinyTodo) scaling entity count with multiple random entity graphs
# per data point, mirroring Cedar's benchmark methodology.
#
# Cedar benchmark parameters (cedar-examples/oopsla2024-benchmarks/README.md):
#   --app gdrive,github,tiny-todo
#   --num-hierarchies 200
#   --num-requests 500
#   --num-entities 5,10,15,20,30,40,50
#
# Measures LATENCY (median/p99) to match Cedar's reported metric.
# GC sweep (ZGC, Shenandoah) to find optimal latency GC.
#
# Profiles:
#   quick    - 5 seeds, 2 entity counts, validates sweep end-to-end
#   rigorous - 200 seeds, 7 entity counts (Cedar's exact parameters)
#
# Usage: run-oopsla-scenarios.sh [quick|rigorous] [output-dir]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

PROFILE=${1:-quick}
OUTPUT_DIR=${2:-$SCRIPT_DIR/../results/oopsla-${PROFILE}-$(timestamp)}

log_env

APPS=(tinytodo gdrive github)
# G1 (default) - best median/p99 for sub-microsecond evaluations.
# ZGC/Shenandoah only help at p99.9+ where GC pauses occur.
GC_SWEEP=(default)

case "$PROFILE" in
    quick)
        SEEDS=5
        ENTITY_COUNTS=(5 50)
        WARMUP_ITERATIONS=1
        WARMUP_TIME=10
        MEASUREMENT_TIME=10
        CONVERGENCE_THRESHOLD=50
        CONVERGENCE_WINDOW=2
        MAX_FORKS=2
        COOL_TARGET=90
        ;;
    rigorous)
        # Cedar: --num-hierarchies 200, --num-entities 5,10,15,20,30,40,50
        SEEDS=200
        ENTITY_COUNTS=(5 10 15 20 30 40 50)
        WARMUP_ITERATIONS=1
        WARMUP_TIME=10
        MEASUREMENT_TIME=10
        CONVERGENCE_THRESHOLD=50
        CONVERGENCE_WINDOW=2
        MAX_FORKS=2
        COOL_TARGET=80
        ;;
    *)
        echo "Unknown profile: $PROFILE. Use quick or rigorous."
        exit 1
        ;;
esac

RUN_TIMESTAMP=$(timestamp)
OUTDIR="$OUTPUT_DIR/oopsla-${PROFILE}-${RUN_TIMESTAMP}"
mkdir -p "$OUTDIR"

TOTAL_STEPS=$(( ${#APPS[@]} * ${#ENTITY_COUNTS[@]} * ${#GC_SWEEP[@]} * SEEDS ))
CURRENT_STEP=0

echo "================================================================"
echo "  OOPSLA 2024 Scenario Benchmark (Cedar-equivalent)"
echo "  Profile:       $PROFILE"
echo "  Applications:  ${APPS[*]}"
echo "  Entity counts: ${ENTITY_COUNTS[*]}"
echo "  Seeds:         $SEEDS"
echo "  GC sweep:      ${GC_SWEEP[*]}"
echo "  Warmup:        ${WARMUP_ITERATIONS} x ${WARMUP_TIME}s"
echo "  Measurement:   ${MEASUREMENT_TIME}s (throughput pass, serves as JIT warmup)"
echo "  Latency:       always (SampleTime pass after throughput)"
echo "  Convergence:   CoV < ${CONVERGENCE_THRESHOLD}% over ${CONVERGENCE_WINDOW} forks (max ${MAX_FORKS})"
echo "  Cool target:   ${COOL_TARGET}C"
echo "  Total runs:    $TOTAL_STEPS"
echo "  Output:        $OUTDIR"
echo "================================================================"
echo ""

for gc in "${GC_SWEEP[@]}"; do
    for app in "${APPS[@]}"; do
        for n in "${ENTITY_COUNTS[@]}"; do
            scenario="${app}-${n}"
            pcores=1
            cpu_range=$(server_cpus "$pcores")

            for seed in $(seq 0 $((SEEDS - 1))); do
                CURRENT_STEP=$((CURRENT_STEP + 1))
                pct=$((CURRENT_STEP * 100 / TOTAL_STEPS))

                echo "================================================================"
                echo "  [$pct%] Step $CURRENT_STEP / $TOTAL_STEPS"
                echo "  $scenario / seed=$seed / $gc / CPUs $cpu_range"
                echo "================================================================"
                wait_cool

                gc_flag=""
                if [ "$gc" != "default" ]; then
                    gc_flag="--gc=$gc"
                fi

                run_pinned "$cpu_range" java -jar "$SAPL4_BENCH_JAR" \
                    --scenario="$scenario" \
                    --seed="$seed" \
                    --method=decideOnceBlocking \
                    --indexing=AUTO \
                    $gc_flag \
                    -t 1 \
                    --warmup-iterations="$WARMUP_ITERATIONS" \
                    --warmup-time="$WARMUP_TIME" \
                    --measurement-time="$MEASUREMENT_TIME" \
                    --convergence-threshold="$CONVERGENCE_THRESHOLD" \
                    --convergence-window="$CONVERGENCE_WINDOW" \
                    --max-forks="$MAX_FORKS" \
                    --latency-only \
                    --heap=32g \
                    -o "$OUTDIR"

                echo ""
            done

            echo "  Completed $SEEDS seeds for $scenario ($gc)"
            echo ""
        done
    done
done

echo "================================================================"
echo "  OOPSLA 2024 Scenario Benchmark Complete"
echo "  Results: $OUTDIR"
echo "================================================================"
