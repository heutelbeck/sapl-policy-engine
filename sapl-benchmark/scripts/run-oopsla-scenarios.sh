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
# github, TinyTodo) scaling entity count, with NAIVE vs CANONICAL comparison.
# Based on: Cedar OOPSLA 2024, Section 5.2 (arxiv.org/pdf/2403.04651)
# Usage: run-oopsla-scenarios.sh [quick|base] [output-dir]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

PROFILE=${1:-quick}
OUTPUT_DIR=${2:-$SCRIPT_DIR/../results/oopsla-${PROFILE}-$(timestamp)}

SCENARIOS=(gdrive-5 gdrive-10 gdrive-25 gdrive-50 github-5 github-10 github-25 github-50 tinytodo-5 tinytodo-10 tinytodo-25 tinytodo-50)
INDEXING_SWEEP=(NAIVE CANONICAL)
THREAD_SWEEP=(1)

case "$PROFILE" in
    quick)
        WARMUP_ITERATIONS=1
        WARMUP_TIME=3
        MEASUREMENT_TIME=10
        CONVERGENCE_THRESHOLD=10
        CONVERGENCE_WINDOW=2
        MAX_FORKS=2
        LATENCY=true
        COOL_TARGET=90
        SCENARIOS=(gdrive-5 gdrive-25 gdrive-50 github-5 github-25 github-50 tinytodo-5 tinytodo-25 tinytodo-50)
        ;;
    base)
        WARMUP_ITERATIONS=1
        WARMUP_TIME=3
        MEASUREMENT_TIME=30
        CONVERGENCE_THRESHOLD=2
        CONVERGENCE_WINDOW=3
        MAX_FORKS=5
        LATENCY=true
        COOL_TARGET=60
        ;;
    *)
        echo "Unknown profile: $PROFILE. Use quick or base."
        exit 1
        ;;
esac

RUN_TIMESTAMP=$(timestamp)
OUTDIR="$OUTPUT_DIR/oopsla-${RUN_TIMESTAMP}"
mkdir -p "$OUTDIR"

TOTAL_STEPS=$(( ${#INDEXING_SWEEP[@]} * ${#SCENARIOS[@]} * ${#THREAD_SWEEP[@]} ))
CURRENT_STEP=0

echo "================================================================"
echo "  OOPSLA 2024 Scenario Benchmark (Cedar-equivalent)"
echo "  Profile:   $PROFILE"
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
echo "  OOPSLA 2024 Scenario Benchmark Complete"
echo "  Results: $OUTDIR"
echo "================================================================"
