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

# SAPL 4 HTTP server benchmark using wrk.
# Sweeps: runtime (JVM/native) x scenarios x P-core counts x connection counts.
# Usage: run-server-http.sh [quick|base|rigorous] [output-dir]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

PROFILE=${1:-quick}
OUTPUT_DIR=${2:-$SCRIPT_DIR/../results/$PROFILE-$(timestamp)}

profile_defaults "$PROFILE"
log_env

if ! $HAS_WRK; then
    echo "ERROR: wrk not found. Install wrk to run HTTP server benchmarks."
    exit 1
fi

SCENARIO_DIR="/tmp/sapl-benchmark-scenarios"
rm -rf "$SCENARIO_DIR"
mkdir -p "$SCENARIO_DIR"
LUA_SCRIPT="$SCRIPT_DIR/lib/sapl-wrk.lua"
HTTP_URL="http://127.0.0.1:8443/api/pdp/decide-once"

trap_cleanup

echo "Exporting scenarios..."
for scenario in "${SCENARIOS[@]}"; do
    java -jar "$SAPL4_BENCH_JAR" --scenario="$scenario" --export="$SCENARIO_DIR/$scenario" 2>/dev/null
    echo "  $scenario -> $SCENARIO_DIR/$scenario"
done
echo ""

# Convergence helpers (same as run-embedded-native.sh)
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
    python3 -c "print('true' if $cov <= $CONVERGENCE_THRESHOLD else 'false')"
}

run_converging_wrk() {
    local scenario=$1
    local pcores=$2
    local connections=$3
    local outdir=$4
    local sub_file="$SCENARIO_DIR/$scenario/subscription.json"
    local prefix="${scenario}_${pcores}p_${connections}c"
    local client_cpu=$(client_cpus)

    local throughputs=()
    local last_latency=""

    for fork_index in $(seq 1 "$MAX_FORKS"); do
        wait_cool

        local wrk_output
        wrk_output=$(SUBSCRIPTION_FILE="$sub_file" run_pinned "$client_cpu" wrk -t2 -c"$connections" -d${WRK_MEASURE_TIME}s --latency -s "$LUA_SCRIPT" "$HTTP_URL" 2>&1)

        local throughput
        throughput=$(parse_wrk_rps "$wrk_output")

        if [ -z "$throughput" ] || [ "$throughput" = "0.00" ]; then
            echo "    Fork $fork_index: FAILED"
            continue
        fi

        throughputs+=("$throughput")

        local latency_str
        latency_str=$(parse_wrk_latency "$wrk_output")
        if [ -n "$latency_str" ] && [ "$latency_str" != "::" ]; then
            last_latency="$latency_str"
        fi

        local fork_json="$outdir/${prefix}_fork${fork_index}.json"
        python3 -c "
import json
data = [{
    'benchmark': 'http-decide-once',
    'mode': 'thrpt',
    'transport': 'http',
    'cores': $pcores,
    'connections': $connections,
    'measurementTime': '$WRK_MEASURE_TIME s',
    'params': {'scenarioName': '$scenario'},
    'primaryMetric': {
        'score': $throughput,
        'scoreUnit': 'req/s'
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
        printf "    Fork %d: %'.0f req/s (CoV: %s%%)\n" "$fork_index" "${throughput%.*}" "$cov"

        local converged
        converged=$(check_convergence "$CONVERGENCE_WINDOW" "${throughputs[@]}")
        if [ "$converged" = "true" ]; then
            echo "    Converged after $fork_index forks (CoV ${cov}% < ${CONVERGENCE_THRESHOLD}%)"
            if [ -n "$last_latency" ]; then
                IFS=: read -ra lparts <<< "$last_latency"
                printf "    Latency: p50=%s ns  p90=%s ns  p99=%s ns\n" "${lparts[0]}" "${lparts[1]}" "${lparts[2]}"
            fi
            break
        fi
    done

    local n=${#throughputs[@]}
    if [ "$n" -eq 0 ]; then
        echo "    FAILED: no successful forks"
        return 1
    fi

    local csv="$outdir/${prefix}.csv"
    python3 -c "
import math
vals = [float(x) for x in '${throughputs[*]}'.split()]
n = len(vals)
mean = sum(vals) / n
std = math.sqrt(sum((v - mean)**2 for v in vals) / (n - 1)) if n > 1 else 0
cov = std / mean * 100 if mean > 0 else 0

latency_str = '${last_latency}'
latency_comments = ''
if latency_str and ':' in latency_str:
    parts = latency_str.split(':')
    if len(parts) == 3 and all(p for p in parts):
        latency_comments = f'''# Latency p50 (ns): {parts[0]}
# Latency p90 (ns): {parts[1]}
# Latency p99 (ns): {parts[2]}
'''

print(f'''# SAPL 4.0 Server HTTP Benchmark Results
# Scenario: $scenario
# Cores: $pcores P-cores
# Connections: $connections
# Measurement: ${WRK_MEASURE_TIME}s
# Convergence: CoV < ${CONVERGENCE_THRESHOLD}% over $CONVERGENCE_WINDOW forks
# Mean: {mean:.2f} req/s
# StdDev: {std:.2f} req/s
# CoV: {cov:.2f}%
# Forks: {n}
{latency_comments}fork,throughput_req_s''')
for i, v in enumerate(vals, 1):
    print(f'{i},{v:.2f}')
" > "$csv"

    echo "    Result: $csv"
    return 0
}

# Count total steps
RUNTIMES=()
[ -f "$SAPL_NODE_JAR" ] && RUNTIMES+=(jvm)
[ -x "$SAPL_NATIVE" ] && RUNTIMES+=(native)

if [ ${#RUNTIMES[@]} -eq 0 ]; then
    echo "ERROR: No server binary found."
    echo "  JVM JAR: $SAPL_NODE_JAR"
    echo "  Native:  $SAPL_NATIVE"
    exit 1
fi

TOTAL_STEPS=$(( ${#RUNTIMES[@]} * ${#SCENARIOS[@]} * ${#CORE_SWEEP[@]} * ${#CONN_SWEEP[@]} ))
CURRENT_STEP=0

for runtime in "${RUNTIMES[@]}"; do
    RUN_TIMESTAMP=$(timestamp)
    OUTDIR="$OUTPUT_DIR/server-http-${runtime}-${PROFILE}-${RUN_TIMESTAMP}"
    mkdir -p "$OUTDIR"

    if [ "$runtime" = "jvm" ]; then
        SERVER_CMD="java -jar $SAPL_NODE_JAR"
    else
        SERVER_CMD="$SAPL_NATIVE"
    fi

    echo "================================================================"
    echo "  SAPL 4 HTTP Server Benchmark ($runtime)"
    echo "  Profile:     $PROFILE"
    echo "  Scenarios:   ${SCENARIOS[*]}"
    echo "  Cores:       ${CORE_SWEEP[*]}"
    echo "  Connections: ${CONN_SWEEP[*]}"
    echo "  Measure:     ${WRK_MEASURE_TIME}s"
    echo "  Converg:     CoV < ${CONVERGENCE_THRESHOLD}% over ${CONVERGENCE_WINDOW} forks (max ${MAX_FORKS})"
    echo "  Total:       $TOTAL_STEPS runs across all runtimes"
    echo "  Output:      $OUTDIR"
    echo "================================================================"
    echo ""

    for scenario in "${SCENARIOS[@]}"; do
        for pcores in "${CORE_SWEEP[@]}"; do
            cpu_range=$(server_cpus "$pcores")

            stop_server
            wait_cool

            echo "  Starting $runtime server: $scenario on CPUs $cpu_range"
            run_pinned "$cpu_range" $SERVER_CMD server \
                --io.sapl.node.allow-no-auth=true \
                --io.sapl.pdp.embedded.policies-path="$SCENARIO_DIR/$scenario" \
                --io.sapl.pdp.embedded.config-path="$SCENARIO_DIR/$scenario" \
                --logging.level.root=WARN \
                >/dev/null 2>&1 &
            SERVER_PID=$!

            local max_wait=30
            for i in $(seq 1 $max_wait); do
                if curl -sf http://127.0.0.1:8443/actuator/health >/dev/null 2>&1; then
                    echo "  Server started (PID $SERVER_PID)"
                    break
                fi
                sleep 1
            done

            if ! curl -sf http://127.0.0.1:8443/actuator/health >/dev/null 2>&1; then
                echo "  ERROR: Server did not start within ${max_wait}s, skipping"
                stop_server
                continue
            fi

            if $WRK_CONVERGE; then
                converge_wrk "${CONN_SWEEP[0]}" "$HTTP_URL" "$LUA_SCRIPT" "$SCENARIO_DIR/$scenario/subscription.json"
            fi

            for connections in "${CONN_SWEEP[@]}"; do
                CURRENT_STEP=$((CURRENT_STEP + 1))
                pct=$((CURRENT_STEP * 100 / TOTAL_STEPS))

                echo "================================================================"
                echo "  Step $CURRENT_STEP of $TOTAL_STEPS ($pct%)"
                echo "  $runtime / $scenario / ${pcores}P / ${connections}c"
                echo "================================================================"

                run_converging_wrk "$scenario" "$pcores" "$connections" "$OUTDIR"
                echo ""
            done

            stop_server
        done
    done
done

echo "================================================================"
echo "  SAPL 4 HTTP Server Benchmark Complete"
echo "  Results: $OUTPUT_DIR"
echo "================================================================"
