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

# SAPL 4 embedded benchmark: JVM (JMH forks(1)) and native (timing loops).
# Auto-detects native binary availability. Sweeps scenarios, threads, indexing.
#
# Usage: run-embedded.sh [quick|full] [output-dir] [experiment]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

QUALITY=${1:-quick}
OUTPUT_DIR=${2:-$SCRIPT_DIR/../results/${QUALITY}-$(timestamp)}
EXPERIMENT=${3:-embedded}

load_quality "$QUALITY"
load_experiment "$EXPERIMENT"
log_env

RUNTIMES=(jvm)
$HAS_NATIVE && RUNTIMES+=(native)

SCENARIO_DIR="/tmp/sapl-benchmark-scenarios"
rm -rf "$SCENARIO_DIR"
mkdir -p "$SCENARIO_DIR"

# Export scenarios for native (JVM reads them at runtime, but native needs files)
echo "Exporting scenarios..."
for indexing in "${INDEXING_SWEEP[@]}"; do
    for scenario in "${SCENARIOS[@]}"; do
        java -jar "$SAPL4_BENCH_JAR" --scenario="$scenario" --indexing="$indexing" --export="$SCENARIO_DIR/${scenario}-${indexing}" 2>/dev/null
        echo "  $scenario/$indexing -> $SCENARIO_DIR/${scenario}-${indexing}"
    done
done
echo ""

# ---- Native helper functions ----

run_native_fork() {
    local scenario=$1
    local method=$2
    local threads=$3
    local cpu_range=$4
    local indexing=$5
    local scenario_dir="$SCENARIO_DIR/${scenario}-${indexing}"
    local latency_flag="--latency=$LATENCY"

    run_pinned "$cpu_range" "$SAPL_NATIVE" benchmark \
        --dir "$scenario_dir" \
        -f "$scenario_dir/subscription.json" \
        --machine-readable \
        -b "$method" \
        -t "$threads" \
        --warmup-iterations="$WARMUP_ITERATIONS" \
        --warmup-time="$WARMUP_TIME" \
        --measurement-iterations=1 \
        --measurement-time="$MEASUREMENT_TIME" \
        -o "$OUTDIR" \
        --output-prefix="${scenario}_${indexing}" \
        $latency_flag 2>/dev/null
}

run_native_converging() {
    local scenario=$1
    local method=$2
    local threads=$3
    local cpu_range=$4
    local indexing=$5
    local prefix="${scenario}_${indexing}_${method}_${threads}t"

    local throughputs=()
    local last_latency=""

    for fork_index in $(seq 1 "$MAX_FORKS"); do
        wait_cool

        local output
        output=$(run_native_fork "$scenario" "$method" "$threads" "$cpu_range" "$indexing")

        local throughput
        throughput=$(echo "$output" | grep '^THROUGHPUT:' | head -1 | cut -d: -f2)
        local latency_line
        latency_line=$(echo "$output" | grep '^LATENCY:' | head -1)

        if [ -z "$throughput" ]; then
            echo "    Fork $fork_index: FAILED"
            continue
        fi

        throughputs+=("$throughput")
        if [ -n "$latency_line" ]; then
            last_latency="$latency_line"
        fi

        local fork_json="$OUTDIR/${prefix}_fork${fork_index}.json"
        python3 "$BENCH_PY" write-fork-json \
            --output "$fork_json" --score "$throughput" --unit "ops/s" \
            --benchmark "$method" --mode thrpt --threads "$threads" \
            --runtime native --warmup-iterations "$WARMUP_ITERATIONS" \
            --warmup-time "${WARMUP_TIME} s" \
            --measurement-time "${MEASUREMENT_TIME} s" \
            --scenario "$scenario"

        local n=${#throughputs[@]}
        local cov="N/A"
        if [ "$n" -ge 2 ]; then
            cov=$(compute_cov "${throughputs[@]}")
        fi
        printf "    Fork %d: %.0f ops/s (CoV: %s%%)\n" "$fork_index" "${throughput%.*}" "$cov"

        local converged
        converged=$(check_convergence "$CONVERGENCE_WINDOW" "${throughputs[@]}")
        if [ "$converged" = "true" ]; then
            echo "    Converged after $fork_index forks (CoV ${cov}% < ${CONVERGENCE_THRESHOLD}%)"
            if [ -n "$last_latency" ]; then
                local lparts
                IFS=: read -ra lparts <<< "${last_latency#LATENCY:}"
                printf "    Latency: p50=%s ns  p90=%s ns  p99=%s ns  p99.9=%s ns  max=%s ns\n" \
                    "${lparts[0]}" "${lparts[1]}" "${lparts[2]}" "${lparts[3]}" "${lparts[4]}"
            fi
            break
        fi
    done

    local n=${#throughputs[@]}
    if [ "$n" -eq 0 ]; then
        echo "    FAILED: no successful forks"
        return 1
    fi

    local csv="$OUTDIR/${prefix}.csv"
    local latency_arg="${last_latency#LATENCY:}"
    python3 "$BENCH_PY" write-csv \
        --output "$csv" \
        --throughputs "$(IFS=,; echo "${throughputs[*]}")" \
        --title "SAPL 4.0 Native Benchmark Results" \
        --unit throughput_ops_s \
        --scenario "$scenario" --method "$method" --threads "$threads" \
        --runtime native \
        --warmup "${WARMUP_ITERATIONS} x ${WARMUP_TIME}s" \
        --measurement "${MEASUREMENT_TIME}s" \
        --convergence-threshold "$CONVERGENCE_THRESHOLD" \
        --convergence-window "$CONVERGENCE_WINDOW" \
        ${latency_arg:+--latency "$latency_arg"}

    echo "    Result: $csv"
    return 0
}

# ---- Main benchmark function (dispatches to JVM or native) ----

run_benchmark() {
    local runtime=$1
    local scenario=$2
    local method=$3
    local threads=$4
    local indexing=$5

    local pcores=$threads
    local cpu_range
    cpu_range=$(server_cpus "$pcores")

    CURRENT_STEP=$((CURRENT_STEP + 1))
    local pct=$((CURRENT_STEP * 100 / TOTAL_STEPS))

    echo "================================================================"
    echo "  Step $CURRENT_STEP of $TOTAL_STEPS ($pct%)"
    echo "  $runtime / $scenario / $method / ${threads}t / $indexing pinned to CPUs $cpu_range"
    echo "================================================================"

    if [ "$runtime" = "jvm" ]; then
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
    else
        run_native_converging "$scenario" "$method" "$threads" "$cpu_range" "$indexing"
    fi

    echo ""
}

# ---- Run loop ----

for runtime in "${RUNTIMES[@]}"; do
    RUN_TIMESTAMP=$(timestamp)
    OUTDIR="$OUTPUT_DIR/${EXPERIMENT}-${runtime}-${QUALITY}-${RUN_TIMESTAMP}"
    mkdir -p "$OUTDIR"

    MAIN_STEPS=$(( ${#INDEXING_SWEEP[@]} * ${#SCENARIOS[@]} * ${#METHODS[@]} * ${#THREAD_SWEEP[@]} ))
    EXTRA_STEPS=0
    if [ "$QUALITY" = "full" ]; then
        EXTRA_STEPS=$(( ${#SCENARIOS[@]} * ${#THREAD_SWEEP[@]} + ${#THREAD_SWEEP[@]} ))
    fi
    TOTAL_STEPS=$(( MAIN_STEPS + EXTRA_STEPS ))
    CURRENT_STEP=0

    echo "================================================================"
    echo "  SAPL 4 Embedded Benchmark ($runtime)"
    echo "  Profile:   $QUALITY"
    echo "  Experiment:$EXPERIMENT"
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
                    run_benchmark "$runtime" "$scenario" "$method" "$threads" "$indexing"
                done
            done
        done
    done

    if [ "$QUALITY" = "full" ]; then
        for indexing in "${INDEXING_SWEEP[@]}"; do
            for scenario in "${SCENARIOS[@]}"; do
                for threads in "${THREAD_SWEEP[@]}"; do
                    run_benchmark "$runtime" "$scenario" "decideStreamFirst" "$threads" "$indexing"
                done
            done
        done

        for threads in "${THREAD_SWEEP[@]}"; do
            run_benchmark "$runtime" "baseline" "noOp" "$threads" "AUTO"
        done
    fi

    python3 "$BENCH_PY" summarize "$OUTDIR"

    echo "================================================================"
    echo "  SAPL 4 Embedded Complete ($runtime)"
    echo "  Results: $OUTDIR"
    echo "================================================================"
    echo ""
done
