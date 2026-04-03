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

# Native latency benchmark (HdrHistogram): measures p50/p90/p99/p99.9 across
# scenarios and scaling factors with multiple random entity graphs per data point.
#
# Usage: run-latency-native.sh [quick|full] [experiment] [output-dir]
# Experiments: latency-cedar, latency-hospital-scaling, latency-hospital-index

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

QUALITY=${1:-quick}
EXPERIMENT=${2:-latency-cedar}
OUTPUT_DIR=${3:-$SCRIPT_DIR/../results/latency-native-${EXPERIMENT}-$(timestamp)}

load_quality "$QUALITY"
load_experiment "$EXPERIMENT"

if [ ! -x "$SAPL_NATIVE" ]; then
    echo "ERROR: Native binary not found at $SAPL_NATIVE"
    echo "Build with: nix develop ~/.dotfiles#graalvm --command mvn package -pl sapl-node -Pnative -DskipTests"
    exit 1
fi

# Quick mode caps seeds and scaling factors for fast validation
if [ "$QUALITY" = "quick" ]; then
    SEEDS=$((SEEDS < 3 ? SEEDS : 3))
    SCALING_FACTORS=("${SCALING_FACTORS[@]:0:2}")
fi

log_env

RUN_TIMESTAMP=$(timestamp)
OUTDIR="$OUTPUT_DIR/latency-native-${EXPERIMENT}-${QUALITY}-${RUN_TIMESTAMP}"
SCENARIO_DIR="/tmp/sapl-latency-scenarios"
rm -rf "$SCENARIO_DIR"
mkdir -p "$OUTDIR" "$SCENARIO_DIR"

TOTAL_STEPS=$(( ${#APPS[@]} * ${#SCALING_FACTORS[@]} * ${#INDEXING_SWEEP[@]} * ${#UNROLL_SWEEP[@]} * SEEDS ))
CURRENT_STEP=0

echo "================================================================"
echo "  Native Latency Benchmark"
echo "  Quality:         $QUALITY"
echo "  Experiment:      $EXPERIMENT"
echo "  Binary:          $SAPL_NATIVE"
echo "  Applications:    ${APPS[*]}"
echo "  Scaling factors: ${SCALING_FACTORS[*]}"
echo "  Seeds:           $SEEDS"
echo "  Indexing:        ${INDEXING_SWEEP[*]}"
echo "  Unroll:          ${UNROLL_SWEEP[*]}"
echo "  Warmup:          ${WARMUP_ITERATIONS} x ${WARMUP_TIME}s"
echo "  Measurement:     ${MEASUREMENT_TIME}s"
echo "  Cool target:     ${COOL_TARGET}C"
echo "  Total runs:      $TOTAL_STEPS"
echo "  Output:          $OUTDIR"
echo "================================================================"
echo ""

SUMMARIZE="$SCRIPT_DIR/summarize-latency.sh"

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
                    echo "  $scenario / seed=$seed / $indexing / unroll=$unroll / CPUs $cpu_range"
                    echo "================================================================"
                    wait_cool

                    unroll_flag=""
                    if [ "$unroll" = "true" ]; then
                        unroll_flag="--unroll"
                    fi

                    # Export scenario for this seed
                    scenario_export="$SCENARIO_DIR/${scenario}-seed${seed}-${indexing}"
                    java -jar "$SAPL4_BENCH_JAR" \
                        --scenario="$scenario" \
                        --seed="$seed" \
                        --indexing="$indexing" \
                        $unroll_flag \
                        --export="$scenario_export" 2>/dev/null

                    # Construct output prefix matching JVM naming convention
                    unroll_label=""
                    if [ "$unroll" = "true" ]; then
                        unroll_label="_unroll"
                    fi
                    output_prefix="${scenario}_seed${seed}_${indexing}${unroll_label}"

                    # Run native benchmark
                    output=$(run_pinned "$cpu_range" "$SAPL_NATIVE" benchmark \
                        --dir "$scenario_export" \
                        -f "$scenario_export/subscription.json" \
                        --machine-readable \
                        -b decideOnceBlocking \
                        -t 1 \
                        --warmup-iterations="$WARMUP_ITERATIONS" \
                        --warmup-time="$WARMUP_TIME" \
                        --measurement-iterations=1 \
                        --measurement-time="$MEASUREMENT_TIME" \
                        --latency=true \
                        -o "$OUTDIR" \
                        --output-prefix="$output_prefix" 2>/dev/null)

                    throughput=$(echo "$output" | grep '^THROUGHPUT:' | head -1 | cut -d: -f2)
                    latency_line=$(echo "$output" | grep '^LATENCY:' | head -1)

                    if [ -z "$throughput" ]; then
                        echo "    FAILED"
                    else
                        latency_str="${latency_line#LATENCY:}"
                        IFS=: read -ra lparts <<< "$latency_str"
                        printf "    %'.0f ops/s  p50=%s ns  p99=%s ns\n" "${throughput%.*}" "${lparts[0]}" "${lparts[2]}"

                        # Write CSV matching JVM output format
                        python3 "$BENCH_PY" write-csv \
                            --output "$OUTDIR/${output_prefix}_decideOnceBlocking_1t.csv" \
                            --throughputs "$throughput" \
                            --title "SAPL 4.0 Native Latency Benchmark" \
                            --unit throughput_ops_s \
                            --scenario "$scenario" --method decideOnceBlocking --threads 1 \
                            --runtime native \
                            --warmup "${WARMUP_ITERATIONS} x ${WARMUP_TIME}s" \
                            --measurement "${MEASUREMENT_TIME}s" \
                            ${latency_str:+--latency "$latency_str"}
                    fi

                    echo ""
                done
            done

            # Update summary after each complete seed
            "$SUMMARIZE" "$OUTDIR" > /dev/null
            echo "  Completed seed $seed ($indexing / unroll=$unroll)"
            echo "  Summary updated: $OUTDIR/summary.md"
            echo ""
        done
    done
done

# Final summary
"$SUMMARIZE" "$OUTDIR"

echo "================================================================"
echo "  Native Latency Benchmark Complete"
echo "  Results: $OUTDIR"
echo "  Summary: $OUTDIR/summary.md"
echo "  CSV:     $OUTDIR/summary.csv"
echo "================================================================"
