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

# Core x Concurrency matrix sweep to find optimal operating point.
# Uses wrk for HTTP and sapl loadtest for RSocket.
#
# Usage: ./run-sweep.sh <output-dir> [http|rsocket|both]
#
# Environment:
#   SAPL_JAR     Path to sapl-node JAR (default: ../target/sapl-node-4.0.0-SNAPSHOT.jar)
#   SAPL_NATIVE  Path to native binary (default: sapl on PATH)
#   POLICY_DIR   Policy directory (default: generates rbac-small)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

if [ $# -lt 1 ]; then
    echo "Usage: $0 <output-dir> [http|rsocket|both]"
    exit 1
fi

OUTPUT_DIR=$1
MODE=${2:-both}
SAPL_JAR="${SAPL_JAR:-$SCRIPT_DIR/../target/sapl-node-4.0.0-SNAPSHOT.jar}"
SAPL_JAR_CMD="java -jar $SAPL_JAR"
POLICY_DIR="${POLICY_DIR:-$OUTPUT_DIR/policies}"

CORES=(1 2 4 6 8)
CONNS=(4 16 64 128 256)

mkdir -p "$OUTPUT_DIR"
trap_cleanup

# Generate policies if needed
if [ ! -d "$POLICY_DIR/rbac-small" ]; then
    echo "Generating policies..."
    $SAPL_JAR_CMD generate-benchmark-policies "$POLICY_DIR"
    echo ""
fi

WRK_LUA="$OUTPUT_DIR/wrk-rbac.lua"
cat > "$WRK_LUA" << 'LUA'
wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"
wrk.body = '{"subject":{"username":"bob","role":"test"},"action":"write","resource":{"type":"foo123"}}'
LUA

echo "================================================================"
echo "  Core x Concurrency Sweep"
echo "================================================================"
log_env
echo "Mode: $MODE"
echo "Cores: ${CORES[*]}"
echo "Concurrency: ${CONNS[*]}"
echo ""

# Start server on ALL cores for JIT warmup
echo "Starting server for JIT warmup..."
local_rsocket=false
if [ "$MODE" = "rsocket" ] || [ "$MODE" = "both" ]; then local_rsocket=true; fi
start_server "$SAPL_JAR_CMD" "$POLICY_DIR/rbac-small" "$local_rsocket"

# JIT warmup with convergence (wrk on all cores)
if [ "$MODE" = "http" ] || [ "$MODE" = "both" ]; then
    echo -n "JIT warmup (HTTP): "
    local samples=()
    for i in $(seq 1 $MAX_WARMUP_ITERS); do
        rps=$(wrk -t2 -c128 -d${WARMUP_INTERVAL}s -s "$WRK_LUA" http://127.0.0.1:8443/api/pdp/decide-once 2>&1 | grep "Requests/sec" | awk '{printf "%.0f", $2}')
        echo -n "${rps}/s "
        samples+=("$rps")
        n=${#samples[@]}
        if [ "$n" -ge "$CONVERGE_WINDOW" ]; then
            converged=true
            for j in $(seq 1 $((CONVERGE_WINDOW - 1))); do
                prev=${samples[$((n - 1 - j))]}
                if [ "$prev" -gt 0 ] && [ "$(( (rps - prev) * 100 / prev ))" -gt "$CONVERGE_THRESHOLD" ] 2>/dev/null; then
                    converged=false; break
                fi
                if [ "$prev" -gt 0 ] && [ "$(( (prev - rps) * 100 / prev ))" -gt "$CONVERGE_THRESHOLD" ] 2>/dev/null; then
                    converged=false; break
                fi
            done
            if $converged; then echo "(converged)"; break; fi
        fi
    done
    [ "${converged:-false}" != "true" ] && echo "(max iterations)"
    echo ""
fi

# HTTP sweep
if [ "$MODE" = "http" ] || [ "$MODE" = "both" ]; then
    echo "cores,conns,rps,lat_ms,computed_L,ratio,temp" > "$OUTPUT_DIR/sweep_http.csv"

    for PCORES in "${CORES[@]}"; do
        scpu=$(server_cpus "$PCORES")
        ccpu=$(client_cpus "$PCORES")

        wait_cool
        pin_server "$scpu"
        echo "=== $PCORES P-core(s): server=$scpu client=$ccpu ==="

        # Brief re-warmup
        run_pinned "$ccpu" wrk -t2 -c128 -d3s -s "$WRK_LUA" http://127.0.0.1:8443/api/pdp/decide-once >/dev/null 2>&1

        for C in "${CONNS[@]}"; do
            run_pinned "$ccpu" wrk -t2 -c"$C" -d2s -s "$WRK_LUA" http://127.0.0.1:8443/api/pdp/decide-once >/dev/null 2>&1
            OUT=$(run_pinned "$ccpu" wrk -t2 -c"$C" -d${DEFAULT_MEASURE_SECONDS}s --latency -s "$WRK_LUA" http://127.0.0.1:8443/api/pdp/decide-once 2>&1)

            RPS=$(echo "$OUT" | grep "Requests/sec" | awk '{print $2}')
            LAT_RAW=$(echo "$OUT" | grep "Latency" | head -1 | awk '{print $2}')
            TEMP=$(pkg_temp)

            if echo "$LAT_RAW" | grep -q "us"; then
                LAT_MS=$(echo "$LAT_RAW" | sed 's/us//' | awk '{printf "%.3f", $1/1000}')
            elif echo "$LAT_RAW" | grep -q "ms"; then
                LAT_MS=$(echo "$LAT_RAW" | sed 's/ms//')
            else
                LAT_MS="0"
            fi

            COMPUTED=$(echo "$RPS $LAT_MS" | awk '{printf "%.1f", $1 * $2 / 1000}')
            RATIO=$(echo "$COMPUTED $C" | awk '{printf "%.0f", $1/$2*100}')

            echo "$PCORES,$C,$RPS,$LAT_MS,$COMPUTED,$RATIO,$TEMP" >> "$OUTPUT_DIR/sweep_http.csv"
            echo "  ${C}conn: ${RPS}/s  lat=${LAT_MS}ms  L=${COMPUTED} (${RATIO}%)  ${TEMP}C"
        done
    done
    echo ""
fi

# RSocket sweep
if [ "$MODE" = "rsocket" ] || [ "$MODE" = "both" ]; then
    echo "cores,conns,rps,lat_us,temp" > "$OUTPUT_DIR/sweep_rsocket.csv"

    for PCORES in "${CORES[@]}"; do
        scpu=$(server_cpus "$PCORES")
        ccpu=$(client_cpus "$PCORES")

        wait_cool
        pin_server "$scpu"
        echo "=== RSocket $PCORES P-core(s): server=$scpu client=$ccpu ==="

        for C in 4 8; do  # Connection count sweep (VT fixed at 256)
            run_pinned "$ccpu" $SAPL_JAR_CMD loadtest --rsocket --host 127.0.0.1 --port 7000 \
                --connections "$C" --vt-per-connection 256 \
                --warmup-seconds "$DEFAULT_WARMUP_SECONDS" --measurement-seconds "$DEFAULT_MEASURE_SECONDS" \
                --label "${PCORES}P server=$scpu client=$ccpu ${C}conn" \
                "${RBAC_SMALL_SUB[@]}" \
                -o "$OUTPUT_DIR/rsocket-${PCORES}p-${C}c" 2>&1 | grep -E "req/s|Latency|Connected"
            TEMP=$(pkg_temp)
            echo "  (${TEMP}C)"
        done
    done
    echo ""
fi

stop_server
echo "================================================================"
echo "  Sweep complete. Results: $OUTPUT_DIR"
echo "================================================================"
