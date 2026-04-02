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

# Latency benchmark: measures p50/p90/p99/p99.9 across scenarios and scaling
# factors with multiple random entity graphs per data point.
#
# Profiles:
#   quick              - github, 3 seeds, 2 scaling factors, validates end-to-end
#   rigorous           - cedar scenarios, 200 seeds, 7 scaling factors
#   hospital-scaling   - hospital, 1 seed, sweeps 5 to 300 departments (170-9905 policies)
#   hospital-index     - hospital, 1 seed, NAIVE vs CANONICAL vs AUTO comparison
#   hospital-unroll    - hospital, 1 seed, NAIVE/CANONICAL/AUTO x unroll/no-unroll
#
# Usage: run-latency-bench.sh [profile] [output-dir]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

PROFILE=${1:-quick}
OUTPUT_DIR=${2:-$SCRIPT_DIR/../results/latency-${PROFILE}-$(timestamp)}

log_env

# Defaults (profiles override these)
GC_SWEEP=(default)
INDEXING_SWEEP=(AUTO)
UNROLL_SWEEP=(false)

case "$PROFILE" in
    quick)
        SEEDS=3
        SCALING_FACTORS=(5 50)
        APPS=(github)
        INDEXING_SWEEP=(AUTO)
        WARMUP_ITERATIONS=1
        WARMUP_TIME=10
        MEASUREMENT_TIME=10
        CONVERGENCE_THRESHOLD=50
        CONVERGENCE_WINDOW=2
        MAX_FORKS=2
        COOL_TARGET=90
        ;;
    rigorous)
        SEEDS=200
        SCALING_FACTORS=(5 10 15 20 30 40 50)
        APPS=(tinytodo gdrive github)
        INDEXING_SWEEP=(AUTO)
        WARMUP_ITERATIONS=1
        WARMUP_TIME=10
        MEASUREMENT_TIME=10
        CONVERGENCE_THRESHOLD=50
        CONVERGENCE_WINDOW=2
        MAX_FORKS=2
        COOL_TARGET=80
        ;;
    hospital-scaling)
        SEEDS=1
        SCALING_FACTORS=(5 10 25 50 100 150 200 250 300)
        APPS=(hospital)
        INDEXING_SWEEP=(AUTO)
        WARMUP_ITERATIONS=1
        WARMUP_TIME=10
        MEASUREMENT_TIME=10
        CONVERGENCE_THRESHOLD=50
        CONVERGENCE_WINDOW=2
        MAX_FORKS=2
        COOL_TARGET=90
        ;;
    hospital-index)
        SEEDS=1
        SCALING_FACTORS=(5 50 100 300)
        APPS=(hospital)
        INDEXING_SWEEP=(NAIVE CANONICAL AUTO)
        WARMUP_ITERATIONS=1
        WARMUP_TIME=10
        MEASUREMENT_TIME=10
        CONVERGENCE_THRESHOLD=50
        CONVERGENCE_WINDOW=2
        MAX_FORKS=2
        COOL_TARGET=90
        ;;
    hospital-unroll)
        SEEDS=1
        SCALING_FACTORS=(5 50 100 300)
        APPS=(hospital)
        INDEXING_SWEEP=(NAIVE CANONICAL AUTO)
        UNROLL_SWEEP=(false)
        WARMUP_ITERATIONS=1
        WARMUP_TIME=10
        MEASUREMENT_TIME=10
        CONVERGENCE_THRESHOLD=50
        CONVERGENCE_WINDOW=2
        MAX_FORKS=2
        COOL_TARGET=90
        ;;
    *)
        echo "Unknown profile: $PROFILE"
        echo "Available: quick, rigorous, hospital-scaling, hospital-index, hospital-unroll"
        exit 1
        ;;
esac

RUN_TIMESTAMP=$(timestamp)
OUTDIR="$OUTPUT_DIR/latency-${PROFILE}-${RUN_TIMESTAMP}"
mkdir -p "$OUTDIR"

TOTAL_STEPS=$(( ${#APPS[@]} * ${#SCALING_FACTORS[@]} * ${#GC_SWEEP[@]} * ${#INDEXING_SWEEP[@]} * ${#UNROLL_SWEEP[@]} * SEEDS ))
CURRENT_STEP=0

echo "================================================================"
echo "  Latency Benchmark"
echo "  Profile:         $PROFILE"
echo "  Applications:    ${APPS[*]}"
echo "  Scaling factors: ${SCALING_FACTORS[*]}"
echo "  Seeds:           $SEEDS"
echo "  Indexing:        ${INDEXING_SWEEP[*]}"
echo "  Unroll:          ${UNROLL_SWEEP[*]}"
echo "  GC:              ${GC_SWEEP[*]}"
echo "  Warmup:          ${WARMUP_ITERATIONS} x ${WARMUP_TIME}s"
echo "  Measurement:     ${MEASUREMENT_TIME}s"
echo "  Convergence:     CoV < ${CONVERGENCE_THRESHOLD}% over ${CONVERGENCE_WINDOW} forks (max ${MAX_FORKS})"
echo "  Cool target:     ${COOL_TARGET}C"
echo "  Total runs:      $TOTAL_STEPS"
echo "  Output:          $OUTDIR"
echo "================================================================"
echo ""

SUMMARIZE="$SCRIPT_DIR/summarize-latency.sh"

for gc in "${GC_SWEEP[@]}"; do
    for indexing in "${INDEXING_SWEEP[@]}"; do
        for unroll in "${UNROLL_SWEEP[@]}"; do
            for seed in $(seq 0 $((SEEDS - 1))); do
                for app in "${APPS[@]}"; do
                    for n in "${SCALING_FACTORS[@]}"; do
                        scenario="${app}-${n}"
                        pcores=1
                        cpu_range=$(server_cpus "$pcores")
                        CURRENT_STEP=$((CURRENT_STEP + 1))
                        pct=$((CURRENT_STEP * 100 / TOTAL_STEPS))

                        echo "================================================================"
                        echo "  [$pct%] Step $CURRENT_STEP / $TOTAL_STEPS"
                        echo "  $scenario / seed=$seed / $indexing / unroll=$unroll / $gc / CPUs $cpu_range"
                        echo "================================================================"
                        wait_cool

                        gc_flag=""
                        if [ "$gc" != "default" ]; then
                            gc_flag="--gc=$gc"
                        fi

                        unroll_flag=""
                        if [ "$unroll" = "true" ]; then
                            unroll_flag="--unroll"
                        fi

                        run_pinned "$cpu_range" java -jar "$SAPL4_BENCH_JAR" \
                            --scenario="$scenario" \
                            --seed="$seed" \
                            --method=decideOnceBlocking \
                            --indexing="$indexing" \
                            $unroll_flag \
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
                done

                # Update summary after each complete seed
                "$SUMMARIZE" "$OUTDIR" > /dev/null
                echo "  Completed seed $seed ($indexing / unroll=$unroll / $gc)"
                echo "  Summary updated: $OUTDIR/summary.md"
                echo ""
            done
        done
    done
done

# Final summary
"$SUMMARIZE" "$OUTDIR"

echo "================================================================"
echo "  Latency Benchmark Complete"
echo "  Results: $OUTDIR"
echo "  Summary: $OUTDIR/summary.md"
echo "  CSV:     $OUTDIR/summary.csv"
echo "================================================================"
