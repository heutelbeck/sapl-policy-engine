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

# SAPL 4 RSocket server benchmark using sapl loadtest.
# Sweeps: runtime (JVM/native) x scenarios x P-core counts x connection counts.
# Client always runs from JVM JAR (needs full Java RSocket stack).
# Usage: run-server-rsocket.sh [quick|full] [output-dir]

export LC_NUMERIC=C

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

QUALITY=${1:-quick}
OUTPUT_DIR=${2:-$SCRIPT_DIR/../results}

load_quality "$QUALITY"
load_experiment "server-rsocket"
log_env

if [ ! -f "$SAPL_NODE_JAR" ]; then
    echo "ERROR: sapl-node JAR not found at $SAPL_NODE_JAR"
    echo "The JVM JAR is required as the RSocket loadtest client."
    exit 1
fi

SCENARIO_DIR="/tmp/sapl-benchmark-scenarios"
rm -rf "$SCENARIO_DIR"
mkdir -p "$SCENARIO_DIR"

trap_cleanup

echo "Exporting scenarios..."
for scenario in "${SCENARIOS[@]}"; do
    java -jar "$SAPL4_BENCH_JAR" --scenario="$scenario" --export="$SCENARIO_DIR/$scenario" 2>/dev/null
    echo "  $scenario -> $SCENARIO_DIR/$scenario"
done
echo ""


run_converging_rsocket() {
    local scenario=$1
    local pcores=$2
    local connections=$3
    local vt=$4
    local outdir=$5
    local sub_file="$SCENARIO_DIR/$scenario/subscription.json"
    local prefix="${scenario}_${pcores}p_${connections}c${vt}vt"
    local client_cpu=$(client_cpus)

    local throughputs=()
    local last_latency=""

    for fork_index in $(seq 1 "$MAX_FORKS"); do
        wait_cool

        local output
        output=$(run_pinned "$client_cpu" java -jar "$SAPL_NODE_JAR" loadtest \
            --rsocket --host 127.0.0.1 --port 7000 \
            -f "$sub_file" \
            --connections "$connections" \
            --vt-per-connection "$vt" \
            --warmup-seconds 5 \
            --measurement-seconds "$WRK_MEASURE_TIME" \
            --machine-readable 2>/dev/null)

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
            last_latency="${latency_line#LATENCY:}"
        fi

        local fork_json="$outdir/${prefix}_fork${fork_index}.json"
        python3 "$BENCH_PY" write-fork-json \
            --output "$fork_json" --score "$throughput" --unit "req/s" \
            --benchmark rsocket-decide-once --mode thrpt --transport rsocket \
            --cores "$pcores" --connections "$connections" \
            --vt-per-connection "$vt" --warmup-seconds 5 \
            --measurement-time "${WRK_MEASURE_TIME} s" \
            --scenario "$scenario"

        local n=${#throughputs[@]}
        local cov="N/A"
        if [ "$n" -ge 2 ]; then
            cov=$(compute_cov "${throughputs[@]}")
        fi
        printf "    Fork %d: %'.0f req/s (CoV: %s%%)\n" "$fork_index" "${throughput%.*}" "$cov"

        local converged
        converged=$(check_convergence "$CONVERGENCE_WINDOW" "${throughputs[@]}")
        if [ "$converged" = "true" ]; then
            local window_cov=$(compute_cov "${throughputs[@]:$((${#throughputs[@]} - CONVERGENCE_WINDOW))}")
            printf "    Converged after %d forks (last %d: CoV %.2f%% < %s%%)\n" "$fork_index" "$CONVERGENCE_WINDOW" "$window_cov" "$CONVERGENCE_THRESHOLD"
            if [ -n "$last_latency" ]; then
                IFS=: read -ra lparts <<< "$last_latency"
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

    local csv="$outdir/${prefix}.csv"
    python3 "$BENCH_PY" write-csv \
        --output "$csv" \
        --throughputs "$(IFS=,; echo "${throughputs[*]}")" \
        --title "SAPL 4.0 Server RSocket Benchmark Results" \
        --unit throughput_req_s \
        --scenario "$scenario" --cores "$pcores" --connections "$connections" \
        --vt-per-connection "$vt" \
        --warmup "5s (built-in per fork)" \
        --measurement "${WRK_MEASURE_TIME}s" \
        --convergence-threshold "$CONVERGENCE_THRESHOLD" \
        --convergence-window "$CONVERGENCE_WINDOW" \
        ${last_latency:+--latency "$last_latency"}

    echo "    Result: $csv"
    return 0
}

# Detect available runtimes
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
    OUTDIR="$OUTPUT_DIR/server-rsocket-${runtime}-${PROFILE}-${RUN_TIMESTAMP}"
    mkdir -p "$OUTDIR"

    if [ "$runtime" = "jvm" ]; then
        SERVER_CMD="java -jar $SAPL_NODE_JAR"
    else
        SERVER_CMD="$SAPL_NATIVE"
    fi

    echo "================================================================"
    echo "  SAPL 4 RSocket Server Benchmark ($runtime)"
    echo "  Profile:     $QUALITY"
    echo "  Scenarios:   ${SCENARIOS[*]}"
    echo "  Cores:       ${CORE_SWEEP[*]}"
    echo "  Connections: ${CONN_SWEEP[*]}"
    echo "  VT/conn:     $RSOCKET_VT"
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

            echo "  Starting $runtime server (RSocket): $scenario on CPUs $cpu_range"
            taskset -c "$cpu_range" $SERVER_CMD server \
                --io.sapl.node.allow-no-auth=true \
                --io.sapl.pdp.embedded.policies-path="$SCENARIO_DIR/$scenario" \
                --io.sapl.pdp.embedded.config-path="$SCENARIO_DIR/$scenario" \
                --logging.level.root=WARN \
                --sapl.pdp.rsocket.enabled=true \
                --sapl.pdp.rsocket.port=7000 \
                >/dev/null 2>&1 &
            SERVER_PID=$!

            max_wait=30
            started=false
            for i in $(seq 1 $max_wait); do
                if curl -sf http://127.0.0.1:8443/actuator/health >/dev/null 2>&1; then
                    if ss -tln | grep -q ":7000 " 2>/dev/null; then
                        echo "  Server started (PID $SERVER_PID, HTTP + RSocket)"
                        started=true
                        break
                    fi
                fi
                sleep 1
            done

            if ! $started; then
                echo "  ERROR: Server did not start within ${max_wait}s, skipping"
                stop_server
                continue
            fi

            for connections in "${CONN_SWEEP[@]}"; do
                CURRENT_STEP=$((CURRENT_STEP + 1))
                pct=$((CURRENT_STEP * 100 / TOTAL_STEPS))

                echo "================================================================"
                echo "  Step $CURRENT_STEP of $TOTAL_STEPS ($pct%)"
                echo "  $runtime / $scenario / ${pcores}P / ${connections}c x ${RSOCKET_VT}vt"
                echo "================================================================"

                run_converging_rsocket "$scenario" "$pcores" "$connections" "$RSOCKET_VT" "$OUTDIR"
                echo ""
            done

            stop_server
        done
    done

    python3 "$BENCH_PY" summarize "$OUTDIR"
done

echo "================================================================"
echo "  SAPL 4 RSocket Server Benchmark Complete"
echo "  Results: $OUTPUT_DIR"
echo "================================================================"
