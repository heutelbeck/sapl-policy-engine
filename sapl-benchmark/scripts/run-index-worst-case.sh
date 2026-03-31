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

# Worst-case A/B: NAIVE vs CANONICAL with zero predicate sharing.
# Each filler policy uses unique predicates -- canonical index overhead only.
# Usage: run-index-worst-case.sh [output-dir]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

OUTPUT_DIR=${1:-$SCRIPT_DIR/../results/index-worst-case-$(timestamp)}

# Override profile defaults for focused comparison
WARMUP_ITERATIONS=1
WARMUP_TIME=3
MEASUREMENT_TIME=10
CONVERGENCE_THRESHOLD=5
CONVERGENCE_WINDOW=2
MAX_FORKS=3
LATENCY=true
COOL_TARGET=60

SCENARIOS=(simple-1 simple-100 simple-500 simple-1000 complex-1 complex-100 complex-1000)
INDEXING_SWEEP=(NAIVE CANONICAL)
THREAD_SWEEP=(1)

RUN_TIMESTAMP=$(timestamp)
OUTDIR="$OUTPUT_DIR/index-worst-case-${RUN_TIMESTAMP}"
mkdir -p "$OUTDIR"

TOTAL_STEPS=$(( ${#INDEXING_SWEEP[@]} * ${#SCENARIOS[@]} * ${#THREAD_SWEEP[@]} ))
CURRENT_STEP=0

echo "================================================================"
echo "  Index Worst-Case Benchmark (NAIVE vs CANONICAL, no sharing)"
echo "  Scenarios: ${SCENARIOS[*]}"
echo "  Indexing:  ${INDEXING_SWEEP[*]}"
echo "  Threads:   ${THREAD_SWEEP[*]}"
echo "  Warmup:    ${WARMUP_ITERATIONS} x ${WARMUP_TIME}s"
echo "  Measure:   ${MEASUREMENT_TIME}s"
echo "  Total:     $TOTAL_STEPS benchmark runs"
echo "  Output:    $OUTDIR"
echo "================================================================"
echo ""

for indexing in "${INDEXING_SWEEP[@]}"; do
    for scenario in "${SCENARIOS[@]}"; do
        for threads in "${THREAD_SWEEP[@]}"; do
            CURRENT_STEP=$((CURRENT_STEP + 1))
            pcores=$threads
            cpu_range=$(server_cpus "$pcores")
            pct=$((CURRENT_STEP * 100 / TOTAL_STEPS))

            echo "================================================================"
            echo "  Step $CURRENT_STEP of $TOTAL_STEPS ($pct%)"
            echo "  $scenario / decideOnceBlocking / ${threads}t / $indexing"
            echo "================================================================"
            wait_cool

            run_pinned "$cpu_range" java -jar "$SAPL4_BENCH_JAR" \
                --scenario="$scenario" \
                --method=decideOnceBlocking \
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

echo "================================================================"
echo "  Index Worst-Case Complete"
echo "  Results: $OUTDIR"
echo "================================================================"
