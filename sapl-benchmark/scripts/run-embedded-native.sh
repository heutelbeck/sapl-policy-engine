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

# SAPL 4 embedded native benchmark (timing loops, convergence via re-invocation).
# Uses sapl-benchmark-sapl4 JAR to export scenarios, then runs the native binary.
# Usage: run-embedded-native.sh [quick|base|rigorous] [output-dir]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

PROFILE=${1:-quick}
OUTPUT_DIR=${2:-$SCRIPT_DIR/../results/$PROFILE-$(timestamp)}

profile_defaults "$PROFILE"
log_env

if [ ! -x "$SAPL_NATIVE" ]; then
    echo "ERROR: Native binary not found at $SAPL_NATIVE"
    echo "Build with: nix develop ~/.dotfiles#graalvm --command mvn package -pl sapl-node -Pnative -DskipTests"
    exit 1
fi

RUN_TIMESTAMP=$(timestamp)
OUTDIR="$OUTPUT_DIR/embedded-sapl4-native-${PROFILE}-${RUN_TIMESTAMP}"
SCENARIO_DIR="/tmp/sapl-benchmark-scenarios"
rm -rf "$SCENARIO_DIR"
mkdir -p "$OUTDIR" "$SCENARIO_DIR"

echo "================================================================"
echo "  SAPL 4 Embedded Native Benchmark"
echo "  Profile:   $PROFILE"
echo "  Binary:    $SAPL_NATIVE"
echo "  Scenarios: ${SCENARIOS[*]}"
echo "  Methods:   ${METHODS[*]}"
echo "  Threads:   ${THREAD_SWEEP[*]}"
echo "  Warmup:    ${WARMUP_ITERATIONS} x ${WARMUP_TIME}s"
echo "  Measure:   ${MEASUREMENT_TIME}s"
echo "  Converg:   CoV < ${CONVERGENCE_THRESHOLD}% over ${CONVERGENCE_WINDOW} forks (max ${MAX_FORKS})"
echo "  Latency:   ${LATENCY}"
echo "================================================================"
echo ""

echo "Exporting scenarios..."
for scenario in "${SCENARIOS[@]}"; do
    java -jar "$SAPL4_BENCH_JAR" --scenario="$scenario" --export="$SCENARIO_DIR/$scenario" 2>/dev/null
    echo "  $scenario -> $SCENARIO_DIR/$scenario"
done
echo ""

# Convergence helpers
compute_cov() {
    python3 -c "
import math, sys
vals = [float(x) for x in sys.argv[1:]]
n = len(vals)
if n < 2:
    print('999.99')
    sys.exit()
mean = sum(vals) / n
std = math.sqrt(sum((v - mean)**2 for v in vals) / (n - 1))
print(f'{std / mean * 100:.2f}')
" "$@"
}

check_convergence() {
    local window=$1
    shift
    local vals=("$@")
    local n=${#vals[@]}
    if [ "$n" -lt "$window" ]; then
        echo "false"
        return
    fi
    local recent=("${vals[@]:$((n - window))}")
    local cov=$(compute_cov "${recent[@]}")
    local converged=$(python3 -c "print('true' if $cov <= $CONVERGENCE_THRESHOLD else 'false')")
    echo "$converged"
}

run_single_fork() {
    local scenario=$1
    local method=$2
    local threads=$3
    local cpu_range=$4
    local scenario_dir="$SCENARIO_DIR/$scenario"
    local latency_flag=""
    if [ "$LATENCY" = "true" ]; then
        latency_flag="--latency=true"
    else
        latency_flag="--latency=false"
    fi

    local output
    output=$(run_pinned "$cpu_range" "$SAPL_NATIVE" benchmark \
        --dir "$scenario_dir" \
        -f "$scenario_dir/subscription.json" \
        --machine-readable \
        -b "$method" \
        -t "$threads" \
        --warmup-iterations="$WARMUP_ITERATIONS" \
        --warmup-time="$WARMUP_TIME" \
        --measurement-iterations=1 \
        --measurement-time="$MEASUREMENT_TIME" \
        $latency_flag 2>/dev/null)

    echo "$output"
}

run_converging() {
    local scenario=$1
    local method=$2
    local threads=$3
    local cpu_range=$4
    local prefix="${scenario}_${method}_${threads}t"

    local throughputs=()
    local last_latency=""

    for fork_index in $(seq 1 "$MAX_FORKS"); do
        wait_cool

        local output
        output=$(run_single_fork "$scenario" "$method" "$threads" "$cpu_range")

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
        python3 -c "
import json
data = [{
    'benchmark': '$method',
    'mode': 'thrpt',
    'threads': $threads,
    'runtime': 'native',
    'warmupIterations': $WARMUP_ITERATIONS,
    'warmupTime': '$WARMUP_TIME s',
    'measurementTime': '$MEASUREMENT_TIME s',
    'params': {'scenarioName': '$scenario'},
    'primaryMetric': {
        'score': $throughput,
        'scoreUnit': 'ops/s'
    }
}]
with open('$fork_json', 'w') as f:
    json.dump(data, f, indent=4)
"

        local n=${#throughputs[@]}
        local cov="N/A"
        if [ "$n" -ge 2 ]; then
            cov=$(compute_cov "${throughputs[@]}")
        fi
        printf "    Fork %d: %'.0f ops/s (CoV: %s%%)\n" "$fork_index" "${throughput%.*}" "$cov"

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
    python3 -c "
import math
vals = [float(x) for x in '${throughputs[*]}'.split()]
n = len(vals)
mean = sum(vals) / n
std = math.sqrt(sum((v - mean)**2 for v in vals) / (n - 1)) if n > 1 else 0
cov = std / mean * 100 if mean > 0 else 0

latency_line = '${last_latency}'
latency_comments = ''
if latency_line.startswith('LATENCY:'):
    parts = latency_line.split(':')
    if len(parts) == 6:
        latency_comments = f'''# Latency p50 (ns): {parts[1]}
# Latency p90 (ns): {parts[2]}
# Latency p99 (ns): {parts[3]}
# Latency p99.9 (ns): {parts[4]}
# Latency max (ns): {parts[5]}
'''

print(f'''# SAPL 4.0 Native Benchmark Results
# Scenario: $scenario
# Method: $method
# Threads: $threads
# Runtime: native
# Warmup: $WARMUP_ITERATIONS x ${WARMUP_TIME}s
# Measurement: ${MEASUREMENT_TIME}s
# Convergence: CoV < ${CONVERGENCE_THRESHOLD}% over $CONVERGENCE_WINDOW forks
# Mean: {mean:.2f} ops/s
# StdDev: {std:.2f} ops/s
# CoV: {cov:.2f}%
# Forks: {n}
{latency_comments}fork,throughput_ops_s''')
for i, v in enumerate(vals, 1):
    print(f'{i},{v:.2f}')
" > "$csv"

    echo "    Result: $csv"
    return 0
}

# Count total steps
MAIN_STEPS=$(( ${#SCENARIOS[@]} * ${#METHODS[@]} * ${#THREAD_SWEEP[@]} ))
EXTRA_STEPS=0
if [ "$PROFILE" = "rigorous" ]; then
    EXTRA_STEPS=$(( ${#SCENARIOS[@]} * ${#THREAD_SWEEP[@]} + ${#THREAD_SWEEP[@]} ))
fi
TOTAL_STEPS=$(( MAIN_STEPS + EXTRA_STEPS ))
CURRENT_STEP=0

echo "  Total:     $TOTAL_STEPS benchmark runs"
echo "  Output:    $OUTDIR"
echo "================================================================"
echo ""

for scenario in "${SCENARIOS[@]}"; do
    for method in "${METHODS[@]}"; do
        for threads in "${THREAD_SWEEP[@]}"; do
            CURRENT_STEP=$((CURRENT_STEP + 1))
            pcores=$threads
            cpu_range=$(server_cpus "$pcores")
            pct=$((CURRENT_STEP * 100 / TOTAL_STEPS))

            echo "================================================================"
            echo "  Step $CURRENT_STEP of $TOTAL_STEPS ($pct%)"
            echo "  $scenario / $method / ${threads}t pinned to CPUs $cpu_range"
            echo "================================================================"

            run_converging "$scenario" "$method" "$threads" "$cpu_range"
            echo ""
        done
    done
done

if [ "$PROFILE" = "rigorous" ]; then
    for scenario in "${SCENARIOS[@]}"; do
        for threads in "${THREAD_SWEEP[@]}"; do
            CURRENT_STEP=$((CURRENT_STEP + 1))
            pcores=$threads
            cpu_range=$(server_cpus "$pcores")
            pct=$((CURRENT_STEP * 100 / TOTAL_STEPS))

            echo "================================================================"
            echo "  Step $CURRENT_STEP of $TOTAL_STEPS ($pct%)"
            echo "  $scenario / decideStreamFirst / ${threads}t pinned to CPUs $cpu_range"
            echo "================================================================"

            run_converging "$scenario" "decideStreamFirst" "$threads" "$cpu_range"
            echo ""
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

        run_converging "rbac" "noOp" "$threads" "$cpu_range"
        echo ""
    done
fi

echo "================================================================"
echo "  SAPL 4 Embedded Native Complete"
echo "  Results: $OUTDIR"
echo "================================================================"
