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

# SAPL 4 embedded benchmark (JMH forks(1), per scenario, core/thread sweep).
# Usage: run-embedded-jvm.sh [quick|base|rigorous] [output-dir]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

PROFILE=${1:-quick}
OUTPUT_DIR=${2:-$SCRIPT_DIR/../results/$PROFILE-$(timestamp)}

profile_defaults "$PROFILE"
log_env

RUN_TIMESTAMP=$(timestamp)
OUTDIR="$OUTPUT_DIR/embedded-sapl4-jvm-${PROFILE}-${RUN_TIMESTAMP}"
mkdir -p "$OUTDIR"

# Count total steps
MAIN_STEPS=$(( ${#INDEXING_SWEEP[@]} * ${#SCENARIOS[@]} * ${#METHODS[@]} * ${#THREAD_SWEEP[@]} ))
EXTRA_STEPS=0
if [ "$PROFILE" = "rigorous" ]; then
    EXTRA_STEPS=$(( ${#SCENARIOS[@]} * ${#THREAD_SWEEP[@]} + ${#THREAD_SWEEP[@]} ))
fi
TOTAL_STEPS=$(( MAIN_STEPS + EXTRA_STEPS ))
CURRENT_STEP=0

echo "================================================================"
echo "  SAPL 4 Embedded Benchmark (JMH forks(1))"
echo "  Profile:   $PROFILE"
echo "  Scenarios: ${SCENARIOS[*]}"
echo "  Methods:   ${METHODS[*]}"
echo "  Indexing:  ${INDEXING_SWEEP[*]}"
echo "  Threads:   ${THREAD_SWEEP[*]}"
echo "  Warmup:    ${WARMUP_ITERATIONS} x ${WARMUP_TIME}s"
echo "  Measure:   ${MEASUREMENT_TIME}s"
echo "  Latency:   ${LATENCY}"
echo "  Total:     $TOTAL_STEPS benchmark runs"
echo "  Output:    $OUTDIR"
echo "================================================================"
echo ""

for indexing in "${INDEXING_SWEEP[@]}"; do
    for scenario in "${SCENARIOS[@]}"; do
        for method in "${METHODS[@]}"; do
            for threads in "${THREAD_SWEEP[@]}"; do
                CURRENT_STEP=$((CURRENT_STEP + 1))
                pcores=$threads
                cpu_range=$(server_cpus "$pcores")
                pct=$((CURRENT_STEP * 100 / TOTAL_STEPS))

                echo "================================================================"
                echo "  Step $CURRENT_STEP of $TOTAL_STEPS ($pct%)"
                echo "  $scenario / $method / ${threads}t / $indexing pinned to CPUs $cpu_range"
                echo "================================================================"
                wait_cool

                run_pinned "$cpu_range" java -jar "$SAPL4_BENCH_JAR" \
                    --scenario="$scenario" \
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

if [ "$PROFILE" = "rigorous" ]; then
    for indexing in "${INDEXING_SWEEP[@]}"; do
        for scenario in "${SCENARIOS[@]}"; do
            for threads in "${THREAD_SWEEP[@]}"; do
                CURRENT_STEP=$((CURRENT_STEP + 1))
                pcores=$threads
                cpu_range=$(server_cpus "$pcores")
                pct=$((CURRENT_STEP * 100 / TOTAL_STEPS))

                echo "================================================================"
                echo "  Step $CURRENT_STEP of $TOTAL_STEPS ($pct%)"
                echo "  $scenario / decideStreamFirst / ${threads}t / $indexing pinned to CPUs $cpu_range"
                echo "================================================================"
                wait_cool

                run_pinned "$cpu_range" java -jar "$SAPL4_BENCH_JAR" \
                    --scenario="$scenario" \
                    --method=decideStreamFirst \
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

    for threads in "${THREAD_SWEEP[@]}"; do
        CURRENT_STEP=$((CURRENT_STEP + 1))
        pcores=$threads
        cpu_range=$(server_cpus "$pcores")
        pct=$((CURRENT_STEP * 100 / TOTAL_STEPS))

        echo "================================================================"
        echo "  Step $CURRENT_STEP of $TOTAL_STEPS ($pct%)"
        echo "  noOp / ${threads}t pinned to CPUs $cpu_range"
        echo "================================================================"
        wait_cool

        run_pinned "$cpu_range" java -jar "$SAPL4_BENCH_JAR" \
            --scenario=baseline \
            --method=noOp \
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
fi

echo "================================================================"
echo "  SAPL 4 Embedded Complete"
echo "  Results: $OUTDIR"
echo "================================================================"
