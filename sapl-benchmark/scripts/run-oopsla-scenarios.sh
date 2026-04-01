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
# Each seed builds a unique entity graph. JMH measures throughput for that
# graph, cycling through 500 subscriptions. Results across seeds are
# aggregated for median/p99.
#
# Profiles:
#   quick    - 5 seeds, 3 entity counts, validates sweep end-to-end
#   rigorous - 200 seeds, 7 entity counts (Cedar's exact parameters)
#
# Usage: run-oopsla-scenarios.sh [quick|rigorous] [output-dir]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

PROFILE=${1:-quick}
OUTPUT_DIR=${2:-$SCRIPT_DIR/../results/oopsla-${PROFILE}-$(timestamp)}

# Load JMH convergence parameters from common profile
profile_defaults "$PROFILE"
log_env

# Override OOPSLA-specific sweep dimensions
APPS=(tinytodo gdrive github)
METHODS=(decideOnceBlocking)
INDEXING_SWEEP=(AUTO)
THREAD_SWEEP=(1)

case "$PROFILE" in
    quick)
        SEEDS=5
        ENTITY_COUNTS=(5 50)
        COOL_TARGET=90
        ;;
    rigorous)
        # Cedar: --num-hierarchies 200, --num-entities 5,10,15,20,30,40,50
        SEEDS=200
        ENTITY_COUNTS=(5 10 15 20 30 40 50)
        ;;
    *)
        echo "Unknown profile: $PROFILE. Use quick or rigorous."
        exit 1
        ;;
esac

RUN_TIMESTAMP=$(timestamp)
OUTDIR="$OUTPUT_DIR"
mkdir -p "$OUTDIR"

# Sweep: apps x entity_counts x methods x indexing x threads x seeds
TOTAL_STEPS=$(( ${#APPS[@]} * ${#ENTITY_COUNTS[@]} * ${#METHODS[@]} * ${#INDEXING_SWEEP[@]} * ${#THREAD_SWEEP[@]} * SEEDS ))
CURRENT_STEP=0

echo "================================================================"
echo "  OOPSLA 2024 Scenario Benchmark (Cedar-equivalent)"
echo "  Profile:       $PROFILE"
echo "  Applications:  ${APPS[*]}"
echo "  Entity counts: ${ENTITY_COUNTS[*]}"
echo "  Seeds:         $SEEDS"
echo "  Methods:       ${METHODS[*]}"
echo "  Indexing:      ${INDEXING_SWEEP[*]}"
echo "  Threads:       ${THREAD_SWEEP[*]}"
echo "  Warmup:        ${WARMUP_ITERATIONS} x ${WARMUP_TIME}s"
echo "  Measurement:   ${MEASUREMENT_TIME}s per seed"
echo "  Convergence:   CoV < ${CONVERGENCE_THRESHOLD}% over ${CONVERGENCE_WINDOW} forks (max ${MAX_FORKS})"
echo "  Cool target:   ${COOL_TARGET}C"
echo "  Total runs:    $TOTAL_STEPS"
echo "  Output:        $OUTDIR"
echo "================================================================"
echo ""

for indexing in "${INDEXING_SWEEP[@]}"; do
    for app in "${APPS[@]}"; do
        for n in "${ENTITY_COUNTS[@]}"; do
            scenario="${app}-${n}"
            for method in "${METHODS[@]}"; do
                for threads in "${THREAD_SWEEP[@]}"; do
                    pcores=$threads
                    cpu_range=$(server_cpus "$pcores")

                    for seed in $(seq 0 $((SEEDS - 1))); do
                        CURRENT_STEP=$((CURRENT_STEP + 1))
                        pct=$((CURRENT_STEP * 100 / TOTAL_STEPS))

                        echo "================================================================"
                        echo "  Step $CURRENT_STEP of $TOTAL_STEPS ($pct%)"
                        echo "  $scenario / $method / ${threads}t / $indexing / seed=$seed"
                        echo "  Pinned to CPUs $cpu_range"
                        echo "================================================================"
                        wait_cool

                        run_pinned "$cpu_range" java -jar "$SAPL4_BENCH_JAR" \
                            --scenario="$scenario" \
                            --seed="$seed" \
                            --method="$method" \
                            --indexing="$indexing" \
                            -t "$threads" \
                            --warmup-iterations="$WARMUP_ITERATIONS" \
                            --warmup-time="$WARMUP_TIME" \
                            --measurement-time="$MEASUREMENT_TIME" \
                            --convergence-threshold="$CONVERGENCE_THRESHOLD" \
                            --convergence-window="$CONVERGENCE_WINDOW" \
                            --max-forks="$MAX_FORKS" \
                            --latency="$LATENCY" \
                            --heap=32g \
                            -o "$OUTDIR"

                        echo ""
                    done
                done
            done
        done
    done
done

echo "================================================================"
echo "  OOPSLA 2024 Scenario Benchmark Complete"
echo "  Results: $OUTDIR"
echo "================================================================"
