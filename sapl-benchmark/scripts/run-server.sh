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

# SAPL 4 server benchmark: HTTP (wrk2) and/or RSocket (sapl loadtest).
# Auto-detects native binary. Sweeps runtime x protocol x scenarios x cores x connections.
#
# Usage: run-server.sh [quick|full] [output-dir] [experiment]
# Experiments: server-http, server-rsocket, server-all

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

QUALITY=${1:-quick}
OUTPUT_DIR=${2:-$SCRIPT_DIR/../results}
EXPERIMENT=${3:-server-http}

load_quality "$QUALITY"
load_experiment "$EXPERIMENT"
log_env

# Detect available runtimes
RUNTIMES=()
[ -f "$SAPL_NODE_JAR" ] && RUNTIMES+=(jvm)
$HAS_NATIVE && RUNTIMES+=(native)

if [ ${#RUNTIMES[@]} -eq 0 ]; then
    echo "ERROR: No server binary found."
    echo "  JVM JAR: $SAPL_NODE_JAR"
    echo "  Native:  $SAPL_NATIVE"
    exit 1
fi

# Protocol detection from experiment profile variables
PROTOCOLS=()
[ -n "${CONN_SWEEP+x}" ] && $HAS_WRK && PROTOCOLS+=(http)
[ -n "${RSOCKET_VT+x}" ] && PROTOCOLS+=(rsocket-tcp)
if [ -n "${TRANSPORT_SWEEP+x}" ]; then
    PROTOCOLS=()
    for t in "${TRANSPORT_SWEEP[@]}"; do
        if [ "$t" = "tcp" ]; then
            PROTOCOLS+=(rsocket-tcp)
        elif [ "$t" = "uds" ]; then
            PROTOCOLS+=(rsocket-uds)
        fi
    done
    $HAS_WRK && [ -n "${CONN_SWEEP+x}" ] && PROTOCOLS+=(http)
fi

# Defaults for missing profile variables
RSOCKET_VT=${RSOCKET_VT:-256}
RSOCKET_CONN_SWEEP=${RSOCKET_CONN_SWEEP:-${CONN_SWEEP[@]}}

SCENARIO_DIR="/tmp/sapl-benchmark-scenarios"
rm -rf "$SCENARIO_DIR"
mkdir -p "$SCENARIO_DIR"

LUA_SCRIPT="$SCRIPT_DIR/lib/sapl-wrk.lua"
HTTP_URL="http://127.0.0.1:8443/api/pdp/decide-once"
UDS_SOCKET="/tmp/sapl-bench.sock"

trap_cleanup

echo "Exporting scenarios..."
for scenario in "${SCENARIOS[@]}"; do
    java -jar "$SAPL4_BENCH_JAR" --scenario="$scenario" --export="$SCENARIO_DIR/$scenario" 2>/dev/null
    echo "  $scenario -> $SCENARIO_DIR/$scenario"
done
echo ""

# ---- HTTP benchmark (wrk2) ----

run_http_converging() {
    local scenario=$1
    local pcores=$2
    local connections=$3
    local outdir=$4
    local sub_file="$SCENARIO_DIR/$scenario/subscription.json"
    local prefix="${scenario}_http_${pcores}p_${connections}c"
    local client_cpu=$(client_cpus)
    local throughputs=()
    local last_latency=""

    for fork_index in $(seq 1 "$MAX_FORKS"); do
        wait_cool
        local wrk_output
        wrk_output=$(SUBSCRIPTION_FILE="$sub_file" run_pinned "$client_cpu" wrk2 -t2 -c"$connections" -d${WRK_MEASURE_TIME}s --latency -s "$LUA_SCRIPT" "$HTTP_URL" 2>&1)
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
        python3 "$BENCH_PY" write-fork-json \
            --output "$fork_json" --score "$throughput" --unit "req/s" \
            --benchmark http-decide-once --mode thrpt --transport http \
            --cores "$pcores" --connections "$connections" \
            --measurement-time "${WRK_MEASURE_TIME} s" --scenario "$scenario"
        local n=${#throughputs[@]}
        local cov="N/A"
        if [ "$n" -ge 2 ]; then cov=$(compute_cov "${throughputs[@]}"); fi
        printf "    Fork %d: %.0f req/s (CoV: %s%%)\n" "$fork_index" "${throughput%.*}" "$cov"
        local converged
        converged=$(check_convergence "$CONVERGENCE_WINDOW" "${throughputs[@]}")
        if [ "$converged" = "true" ]; then
            printf "    Converged after %d forks\n" "$fork_index"
            break
        fi
    done
    if [ ${#throughputs[@]} -eq 0 ]; then echo "    FAILED"; return 1; fi
    python3 "$BENCH_PY" write-csv --output "$outdir/${prefix}.csv" \
        --throughputs "$(IFS=,; echo "${throughputs[*]}")" \
        --title "SAPL 4.0 Server HTTP Benchmark" --unit throughput_req_s \
        --scenario "$scenario" --cores "$pcores" --connections "$connections" \
        --measurement "${WRK_MEASURE_TIME}s" \
        --convergence-threshold "$CONVERGENCE_THRESHOLD" --convergence-window "$CONVERGENCE_WINDOW" \
        ${last_latency:+--latency "$last_latency"}
    echo "    Result: $outdir/${prefix}.csv"
}

# ---- RSocket benchmark (sapl loadtest) ----

run_rsocket_converging() {
    local scenario=$1
    local pcores=$2
    local connections=$3
    local vt=$4
    local outdir=$5
    local transport=$6
    local sub_file="$SCENARIO_DIR/$scenario/subscription.json"
    local prefix="${scenario}_rsocket-${transport}_${pcores}p_${connections}c${vt}vt"
    local client_cpu=$(client_cpus)
    local throughputs=()
    local last_latency=""

    for fork_index in $(seq 1 "$MAX_FORKS"); do
        wait_cool
        local loadtest_args="--rsocket -f $sub_file --connections $connections --vt-per-connection $vt --warmup-seconds 5 --measurement-seconds $WRK_MEASURE_TIME --machine-readable"
        if [ "$transport" = "uds" ]; then
            loadtest_args="$loadtest_args --socket-path $UDS_SOCKET"
        else
            loadtest_args="$loadtest_args --host 127.0.0.1 --port 7000"
        fi
        local output
        output=$(run_pinned "$client_cpu" java -jar "$SAPL_NODE_JAR" loadtest $loadtest_args 2>/dev/null)
        local throughput
        throughput=$(echo "$output" | grep '^THROUGHPUT:' | head -1 | cut -d: -f2)
        if [ -z "$throughput" ]; then echo "    Fork $fork_index: FAILED"; continue; fi
        throughputs+=("$throughput")
        local latency_line
        latency_line=$(echo "$output" | grep '^LATENCY:' | head -1)
        if [ -n "$latency_line" ]; then last_latency="${latency_line#LATENCY:}"; fi
        local fork_json="$outdir/${prefix}_fork${fork_index}.json"
        python3 "$BENCH_PY" write-fork-json \
            --output "$fork_json" --score "$throughput" --unit "req/s" \
            --benchmark rsocket-decide-once --mode thrpt --transport "rsocket-$transport" \
            --cores "$pcores" --connections "$connections" --vt-per-connection "$vt" \
            --measurement-time "${WRK_MEASURE_TIME} s" --scenario "$scenario"
        local n=${#throughputs[@]}
        local cov="N/A"
        if [ "$n" -ge 2 ]; then cov=$(compute_cov "${throughputs[@]}"); fi
        printf "    Fork %d: %.0f req/s (CoV: %s%%)\n" "$fork_index" "${throughput%.*}" "$cov"
        local converged
        converged=$(check_convergence "$CONVERGENCE_WINDOW" "${throughputs[@]}")
        if [ "$converged" = "true" ]; then
            printf "    Converged after %d forks\n" "$fork_index"
            break
        fi
    done
    if [ ${#throughputs[@]} -eq 0 ]; then echo "    FAILED"; return 1; fi
    python3 "$BENCH_PY" write-csv --output "$outdir/${prefix}.csv" \
        --throughputs "$(IFS=,; echo "${throughputs[*]}")" \
        --title "SAPL 4.0 Server RSocket Benchmark" --unit throughput_req_s \
        --scenario "$scenario" --cores "$pcores" --connections "$connections" \
        --vt-per-connection "$vt" \
        --measurement "${WRK_MEASURE_TIME}s" \
        --convergence-threshold "$CONVERGENCE_THRESHOLD" --convergence-window "$CONVERGENCE_WINDOW" \
        ${last_latency:+--latency "$last_latency"}
    echo "    Result: $outdir/${prefix}.csv"
}

# ---- Main sweep ----

for runtime in "${RUNTIMES[@]}"; do
    for protocol in "${PROTOCOLS[@]}"; do
        RUN_TIMESTAMP=$(timestamp)
        OUTDIR="$OUTPUT_DIR/server-${protocol}-${runtime}-${QUALITY}-${RUN_TIMESTAMP}"
        mkdir -p "$OUTDIR"

        if [ "$runtime" = "jvm" ]; then
            SERVER_BASE="java -jar $SAPL_NODE_JAR"
        else
            SERVER_BASE="$SAPL_NATIVE"
        fi

        local_conn_sweep=("${CONN_SWEEP[@]}")

        echo "================================================================"
        echo "  Server Benchmark ($runtime / $protocol)"
        echo "  Profile:     $QUALITY"
        echo "  Scenarios:   ${SCENARIOS[*]}"
        echo "  Cores:       ${CORE_SWEEP[*]}"
        echo "  Connections: ${local_conn_sweep[*]}"
        echo "  Measure:     ${WRK_MEASURE_TIME}s"
        echo "  Output:      $OUTDIR"
        echo "================================================================"
        echo ""

        for scenario in "${SCENARIOS[@]}"; do
            for pcores in "${CORE_SWEEP[@]}"; do
                cpu_range=$(server_cpus "$pcores")
                stop_server
                wait_cool

                echo "  Starting $runtime server: $scenario on CPUs $cpu_range (${pcores} P-cores)"
                server_cmd="$SERVER_BASE"
                if [ "$runtime" = "jvm" ]; then
                    server_cmd="java -XX:ActiveProcessorCount=$((pcores * 2)) -jar $SAPL_NODE_JAR"
                fi

                taskset -c "$cpu_range" $server_cmd server \
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
                    if curl -sf http://127.0.0.1:8443/actuator/health >/dev/null 2>&1 \
                       && ss -tln | grep -q ":7000 " 2>/dev/null; then
                        started=true
                        break
                    fi
                    sleep 1
                done

                if ! $started; then
                    echo "  ERROR: Server did not start within ${max_wait}s, skipping"
                    stop_server
                    continue
                fi
                echo "  Server started (PID $SERVER_PID)"

                # Warmup
                if [ "$protocol" = "http" ] && $WRK_CONVERGE; then
                    converge_wrk "${local_conn_sweep[0]}" "$HTTP_URL" "$LUA_SCRIPT" "$SCENARIO_DIR/$scenario/subscription.json"
                fi

                for connections in "${local_conn_sweep[@]}"; do
                    echo "  --- $scenario / $runtime / $protocol / ${pcores}P / ${connections}c ---"

                    if [ "$protocol" = "http" ]; then
                        run_http_converging "$scenario" "$pcores" "$connections" "$OUTDIR"
                    else
                        local transport="${protocol#rsocket-}"
                        run_rsocket_converging "$scenario" "$pcores" "$connections" "$RSOCKET_VT" "$OUTDIR" "$transport"
                    fi
                    echo ""
                done

                stop_server
            done
        done

        python3 "$BENCH_PY" summarize "$OUTDIR"
        echo ""
    done
done

echo "================================================================"
echo "  Server Benchmark Complete"
echo "  Results: $OUTPUT_DIR"
echo "================================================================"
