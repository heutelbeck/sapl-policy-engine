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
# Usage: run-latency-bench.sh [quick|full] [experiment] [output-dir]
# Experiments: latency-cedar, latency-hospital-scaling, latency-hospital-index

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

QUALITY=${1:-quick}
EXPERIMENT=${2:-latency-cedar}
OUTPUT_DIR=${3:-$SCRIPT_DIR/../results/latency-${EXPERIMENT}-$(timestamp)}

load_quality "$QUALITY"
load_experiment "$EXPERIMENT"

# Quick mode caps seeds and scaling factors for fast validation
if [ "$QUALITY" = "quick" ]; then
    SEEDS=$((SEEDS < 3 ? SEEDS : 3))
    SCALING_FACTORS=("${SCALING_FACTORS[@]:0:2}")
fi

log_env

RUN_TIMESTAMP=$(timestamp)
OUTDIR="$OUTPUT_DIR/latency-${EXPERIMENT}-${QUALITY}-${RUN_TIMESTAMP}"
mkdir -p "$OUTDIR"

TOTAL_STEPS=$(( ${#APPS[@]} * ${#SCALING_FACTORS[@]} * ${#GC_SWEEP[@]} * ${#INDEXING_SWEEP[@]} * ${#UNROLL_SWEEP[@]} * SEEDS ))
CURRENT_STEP=0

echo "================================================================"
echo "  Latency Benchmark"
echo "  Quality:         $QUALITY"
echo "  Experiment:      $EXPERIMENT"
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
