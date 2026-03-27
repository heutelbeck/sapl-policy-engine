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

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

if [ $# -lt 1 ]; then
    echo "Usage: $0 <output-dir>"
    exit 1
fi

OUTPUT_DIR=$1
SAPL_JAR="${SAPL_JAR:-$SCRIPT_DIR/../target/sapl-node-4.0.0-SNAPSHOT.jar}"
SAPL_JAR_CMD="java -jar $SAPL_JAR"
SAPL_NATIVE_CMD="${SAPL_NATIVE:-sapl}"
POLICY_DIR="$OUTPUT_DIR/policies"

CORE_SWEEP=(1 2 4 6 8)
CONN_SWEEP=(32 64 128 256)
WRK_DURATION=30s

WRK_LUA="$OUTPUT_DIR/wrk-sapl.lua"

mkdir -p "$OUTPUT_DIR"
trap_cleanup

echo "================================================================"
echo "  SAPL Reference Benchmark"
echo "================================================================"
log_env
echo "Core sweep:   ${CORE_SWEEP[*]} P-cores"
echo "Conn sweep:   ${CONN_SWEEP[*]}"
echo "wrk duration: $WRK_DURATION"
echo ""

# Generate policies
if [ ! -d "$POLICY_DIR/rbac-small" ]; then
    $SAPL_JAR_CMD generate-benchmark-policies "$POLICY_DIR" 2>/dev/null
fi

cat > "$WRK_LUA" << 'LUA'
wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"
wrk.body = '{"subject":{"username":"bob","role":"test"},"action":"write","resource":{"type":"foo123"}}'
LUA

# ── 1. Embedded JVM ──────────────────────────────────────────────────

echo ">>> 1/6: Embedded JVM"
echo "================================================================"
mkdir -p "$OUTPUT_DIR/embedded-jvm"
$SAPL_JAR_CMD benchmark --dir "$POLICY_DIR/rbac-small" "${RBAC_SMALL_SUB[@]}" \
    -c "$SCRIPT_DIR/configs/standard.json" -o "$OUTPUT_DIR/embedded-jvm" 2>&1 | tail -8
echo ""

# ── 2. Embedded Native ───────────────────────────────────────────────

if $HAS_NATIVE; then
    echo ">>> 2/6: Embedded Native"
    echo "================================================================"
    mkdir -p "$OUTPUT_DIR/embedded-native"
    $SAPL_NATIVE_CMD benchmark --dir "$POLICY_DIR/rbac-small" "${RBAC_SMALL_SUB[@]}" \
        -c "$SCRIPT_DIR/configs/standard.json" -o "$OUTPUT_DIR/embedded-native" 2>&1 | tail -8
    echo ""
else
    echo ">>> 2/6: Embedded Native - SKIPPED (no native binary)"
    echo ""
fi

# ── Helper: HTTP matrix for a given server command ───────────────────

run_http_matrix() {
    local label=$1 server_cmd=$2 csv_file=$3

    echo "cores,conns,rps,p50,p99,temp" > "$csv_file"

    for PCORES in "${CORE_SWEEP[@]}"; do
        scpu=$(server_cpus "$PCORES")
        ccpu=$(client_cpus "$PCORES")

        echo "  === ${PCORES} P-core(s): server=$scpu client=$ccpu ==="
        start_server "$server_cmd" "$POLICY_DIR/rbac-small"
        pin_server "$scpu"

        for C in "${CONN_SWEEP[@]}"; do
            wait_cool

            # Convergence warmup
            local samples=() converged=false
            for wi in $(seq 1 $MAX_WARMUP_ITERS); do
                local rps=$(run_pinned "$ccpu" wrk -t2 -c"$C" -d${WARMUP_INTERVAL}s -s "$WRK_LUA" http://127.0.0.1:8443/api/pdp/decide-once 2>&1 | grep "Requests/sec" | awk '{printf "%.0f", $2}')
                samples+=("$rps")
                local n=${#samples[@]}
                if [ "$n" -ge "$CONVERGE_WINDOW" ]; then
                    converged=true
                    for j in $(seq 1 $((CONVERGE_WINDOW - 1))); do
                        local p=${samples[$((n - 1 - j))]}
                        if [ "$p" -gt 0 ] 2>/dev/null; then
                            local d=$(( (rps > p ? rps - p : p - rps) * 100 / p ))
                            if [ "$d" -gt "$CONVERGE_THRESHOLD" ]; then converged=false; break; fi
                        fi
                    done
                    if $converged; then break; fi
                fi
            done

            # Measure
            OUT=$(run_pinned "$ccpu" wrk -t2 -c"$C" -d"$WRK_DURATION" --latency -s "$WRK_LUA" http://127.0.0.1:8443/api/pdp/decide-once 2>&1)
            RPS=$(echo "$OUT" | grep "Requests/sec" | awk '{print $2}')
            P50=$(echo "$OUT" | grep "50%" | awk '{print $2}')
            P99=$(echo "$OUT" | grep "99%" | head -1 | awk '{print $2}')
            TEMP=$(pkg_temp)

            echo "$PCORES,$C,$RPS,$P50,$P99,$TEMP" >> "$csv_file"
            echo "    ${C}c: ${RPS}/s  p50=$P50  p99=$P99  ${TEMP}C"
        done

        stop_server
        sleep 2
    done

    # Unpinned
    echo "  === Unpinned (all cores) ==="
    start_server "$server_cmd" "$POLICY_DIR/rbac-small"

    for C in "${CONN_SWEEP[@]}"; do
        wait_cool
        # Warmup
        wrk -t2 -c"$C" -d5s -s "$WRK_LUA" http://127.0.0.1:8443/api/pdp/decide-once >/dev/null 2>&1
        wrk -t2 -c"$C" -d5s -s "$WRK_LUA" http://127.0.0.1:8443/api/pdp/decide-once >/dev/null 2>&1
        wrk -t2 -c"$C" -d5s -s "$WRK_LUA" http://127.0.0.1:8443/api/pdp/decide-once >/dev/null 2>&1
        OUT=$(wrk -t2 -c"$C" -d"$WRK_DURATION" --latency -s "$WRK_LUA" http://127.0.0.1:8443/api/pdp/decide-once 2>&1)
        RPS=$(echo "$OUT" | grep "Requests/sec" | awk '{print $2}')
        P50=$(echo "$OUT" | grep "50%" | awk '{print $2}')
        P99=$(echo "$OUT" | grep "99%" | head -1 | awk '{print $2}')
        TEMP=$(pkg_temp)
        echo "unpinned,$C,$RPS,$P50,$P99,$TEMP" >> "$csv_file"
        echo "    ${C}c: ${RPS}/s  p50=$P50  p99=$P99  ${TEMP}C"
    done

    stop_server
    sleep 2
}

# ── Helper: RSocket matrix for a given server command ────────────────

run_rsocket_matrix() {
    local label=$1 server_cmd=$2 csv_file=$3

    echo "cores,conns,vt,rps,p50,p99,p999,temp" > "$csv_file"

    for PCORES in "${CORE_SWEEP[@]}"; do
        scpu=$(server_cpus "$PCORES")
        ccpu=$(client_cpus "$PCORES")

        echo "  === ${PCORES} P-core(s): server=$scpu client=$ccpu ==="
        start_server "$server_cmd" "$POLICY_DIR/rbac-small" true
        pin_server "$scpu"
        wait_cool

        for CONNS in 4 8; do
            run_pinned "$ccpu" $SAPL_JAR_CMD loadtest --rsocket --host 127.0.0.1 --port 7000 \
                --connections "$CONNS" --vt-per-connection 256 \
                --warmup-seconds 5 --measurement-seconds 30 \
                --label "$label ${PCORES}P server=$scpu ${CONNS}conn" \
                "${RBAC_SMALL_SUB[@]}" \
                -o "$OUTPUT_DIR/$label/${PCORES}p-${CONNS}c" 2>&1 | grep -E "req/s|p50|p99"

            # Extract from report CSV
            local csv=$(ls "$OUTPUT_DIR/$label/${PCORES}p-${CONNS}c"/*loadtest.csv 2>/dev/null | head -1)
            if [ -n "$csv" ]; then
                local rps=$(tail -1 "$csv" | cut -d, -f3)
                local TEMP=$(pkg_temp)
                echo "$PCORES,$CONNS,256,$rps,,,,$TEMP" >> "$csv_file"
                echo "    ${CONNS}c x 256vt: ${rps}/s  ${TEMP}C"
            fi
        done

        stop_server
        sleep 2
    done
}

# ── 3. HTTP JVM ──────────────────────────────────────────────────────

echo ">>> 3/6: HTTP JVM"
echo "================================================================"
mkdir -p "$OUTPUT_DIR/http-jvm"
run_http_matrix "http-jvm" "$SAPL_JAR_CMD" "$OUTPUT_DIR/http-jvm/matrix.csv"
echo ""

# ── 4. HTTP Native ───────────────────────────────────────────────────

if $HAS_NATIVE; then
    echo ">>> 4/6: HTTP Native"
    echo "================================================================"
    mkdir -p "$OUTPUT_DIR/http-native"
    run_http_matrix "http-native" "$SAPL_NATIVE_CMD" "$OUTPUT_DIR/http-native/matrix.csv"
    echo ""
else
    echo ">>> 4/6: HTTP Native - SKIPPED"
    echo ""
fi

# ── 5. RSocket JVM ───────────────────────────────────────────────────

echo ">>> 5/6: RSocket JVM"
echo "================================================================"
mkdir -p "$OUTPUT_DIR/rsocket-jvm"
run_rsocket_matrix "rsocket-jvm" "$SAPL_JAR_CMD" "$OUTPUT_DIR/rsocket-jvm/matrix.csv"
echo ""

# ── 6. RSocket Native ────────────────────────────────────────────────

if $HAS_NATIVE; then
    echo ">>> 6/6: RSocket Native"
    echo "================================================================"
    mkdir -p "$OUTPUT_DIR/rsocket-native"
    run_rsocket_matrix "rsocket-native" "$SAPL_NATIVE_CMD" "$OUTPUT_DIR/rsocket-native/matrix.csv"
    echo ""
else
    echo ">>> 6/6: RSocket Native - SKIPPED"
    echo ""
fi

# ── Summary ──────────────────────────────────────────────────────────

echo "================================================================"
echo "  SAPL Reference Benchmark Complete"
echo "================================================================"
echo ""
echo "HTTP JVM:"
column -t -s, "$OUTPUT_DIR/http-jvm/matrix.csv" 2>/dev/null
echo ""
echo "HTTP Native:"
column -t -s, "$OUTPUT_DIR/http-native/matrix.csv" 2>/dev/null
echo ""
echo "RSocket JVM:"
column -t -s, "$OUTPUT_DIR/rsocket-jvm/matrix.csv" 2>/dev/null
echo ""
echo "RSocket Native:"
column -t -s, "$OUTPUT_DIR/rsocket-native/matrix.csv" 2>/dev/null
echo ""
echo "Results: $OUTPUT_DIR"
echo "================================================================"
