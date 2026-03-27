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

# Cross-engine comparison: SAPL 4 vs OPA.
#
# Phase 1: Embedded - each engine's native benchmark tool
# Phase 2: HTTP core sweep - wrk against both servers at 1P, 4P, 8P
# Phase 3: RSocket core sweep - SAPL-only at 1P, 4P, 8P
#
# Usage: ./run-engine-comparison.sh <output-dir> [quick|standard]
#
# Prerequisites:
#   opa on PATH (or via: nix develop ./opa)
#   wrk on PATH
#
# Environment:
#   SAPL_JAR, SAPL_NATIVE

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

if [ $# -lt 1 ]; then
    echo "Usage: $0 <output-dir> [quick|standard]"
    exit 1
fi

OUTPUT_DIR=$1
CONFIG=${2:-quick}
SAPL_JAR="${SAPL_JAR:-$SCRIPT_DIR/../target/sapl-node-4.0.0-SNAPSHOT.jar}"
SAPL_JAR_CMD="java -jar $SAPL_JAR"
SAPL_NATIVE_CMD="${SAPL_NATIVE:-sapl}"
OPA_DIR="$SCRIPT_DIR/opa"
POLICY_DIR="$OUTPUT_DIR/policies"

HAS_OPA=false
command -v opa &>/dev/null && HAS_OPA=true

CORE_SWEEP=(1 4 8)

case "$CONFIG" in
    quick)    WRK_CONNS=64;  WRK_DURATION=30s; OPA_COUNT=10 ;;
    standard) WRK_CONNS=128; WRK_DURATION=60s; OPA_COUNT=20 ;;
    *) echo "Error: Unknown config: $CONFIG"; exit 1 ;;
esac

mkdir -p "$OUTPUT_DIR"
trap_cleanup

echo "================================================================"
echo "  Engine Comparison: SAPL 4 vs OPA ($CONFIG)"
echo "================================================================"
log_env
echo "OPA:        $( $HAS_OPA && echo "$(opa version 2>/dev/null | head -1)" || echo "not found (skipping)" )"
echo "Core sweep: ${CORE_SWEEP[*]} P-cores"
echo ""

# Generate SAPL policies
if [ ! -d "$POLICY_DIR/rbac-small" ]; then
    $SAPL_JAR_CMD generate-benchmark-policies "$POLICY_DIR" 2>/dev/null
fi

# Convergence warmup for wrk
wrk_warmup_converge() {
    local ccpus=$1 lua=$2 url=$3
    echo -n "    warmup: "
    local samples=()
    for i in $(seq 1 $MAX_WARMUP_ITERS); do
        local rps=$(run_pinned "$ccpus" wrk -t2 -c"$WRK_CONNS" -d${WARMUP_INTERVAL}s -s "$lua" "$url" 2>&1 | grep "Requests/sec" | awk '{printf "%.0f", $2}')
        echo -n "${rps}/s "
        samples+=("$rps")
        local n=${#samples[@]}
        if [ "$n" -ge "$CONVERGE_WINDOW" ]; then
            local converged=true
            for j in $(seq 1 $((CONVERGE_WINDOW - 1))); do
                local p=${samples[$((n - 1 - j))]}
                if [ "$p" -gt 0 ] 2>/dev/null; then
                    local d=$(( (rps > p ? rps - p : p - rps) * 100 / p ))
                    if [ "$d" -gt "$CONVERGE_THRESHOLD" ]; then converged=false; break; fi
                fi
            done
            if $converged; then echo "(converged)"; return; fi
        fi
    done
    echo "(max iterations)"
}

# ── Phase 1: Embedded Comparison ─────────────────────────────────────

echo ">>> Phase 1: Embedded Comparison (RBAC deny case)"
echo "================================================================"
EMBEDDED_DIR="$OUTPUT_DIR/embedded"

echo "  --- SAPL 4 JVM ---"
mkdir -p "$EMBEDDED_DIR/sapl-jvm"
$SAPL_JAR_CMD benchmark --dir "$POLICY_DIR/rbac-small" "${RBAC_SMALL_SUB[@]}" \
    -c "$SCRIPT_DIR/configs/$CONFIG.json" -o "$EMBEDDED_DIR/sapl-jvm" 2>&1 | tail -5

if $HAS_NATIVE; then
    echo "  --- SAPL 4 native ---"
    mkdir -p "$EMBEDDED_DIR/sapl-native"
    $SAPL_NATIVE_CMD benchmark --dir "$POLICY_DIR/rbac-small" "${RBAC_SMALL_SUB[@]}" \
        -c "$SCRIPT_DIR/configs/$CONFIG.json" -o "$EMBEDDED_DIR/sapl-native" 2>&1 | tail -5
fi

if $HAS_OPA; then
    echo "  --- OPA (opa bench) ---"
    mkdir -p "$EMBEDDED_DIR/opa"
    echo '{"subject":"bob","resource":"foo123","action":"write"}' > "$EMBEDDED_DIR/opa/input.json"
    opa bench --data "$OPA_DIR/rbac.rego" --input "$EMBEDDED_DIR/opa/input.json" \
        --count "$OPA_COUNT" --benchmem 'data.rbac.allow' | tee "$EMBEDDED_DIR/opa/opa-bench.txt"
fi
echo ""

# ── Phase 2: HTTP Core Sweep (wrk) ──────────────────────────────────

if $HAS_WRK; then
    echo ">>> Phase 2: HTTP Core Sweep (wrk -t2 -c$WRK_CONNS -d$WRK_DURATION)"
    echo "================================================================"
    WRK_DIR="$OUTPUT_DIR/http-wrk"
    mkdir -p "$WRK_DIR"

    echo "P-cores,Engine,Throughput,p50,p99" > "$WRK_DIR/sweep.csv"

    for PCORES in "${CORE_SWEEP[@]}"; do
        scpu=$(server_cpus "$PCORES")
        ccpu=$(client_cpus "$PCORES")
        echo ""
        echo "  === ${PCORES} P-core(s): server=$scpu client=$ccpu ==="

        # SAPL JVM
        echo "  --- SAPL JVM ---"
        start_server "$SAPL_JAR_CMD" "$POLICY_DIR/rbac-small"
        pin_server "$scpu"
        wait_cool
        wrk_warmup_converge "$ccpu" "$SCRIPT_DIR/opa/sapl-rbac.lua" "http://127.0.0.1:8443/api/pdp/decide-once"
        echo "    measure ($WRK_DURATION)..."
        OUT=$(run_pinned "$ccpu" wrk -t2 -c"$WRK_CONNS" -d"$WRK_DURATION" --latency -s "$SCRIPT_DIR/opa/sapl-rbac.lua" http://127.0.0.1:8443/api/pdp/decide-once 2>&1)
        echo "$OUT" | tee "$WRK_DIR/sapl-jvm-${PCORES}p.txt"
        RPS=$(echo "$OUT" | grep "Requests/sec" | awk '{print $2}')
        P50=$(echo "$OUT" | grep "50%" | awk '{print $2}')
        P99=$(echo "$OUT" | grep "99%" | head -1 | awk '{print $2}')
        echo "$PCORES,SAPL-JVM,$RPS,$P50,$P99" >> "$WRK_DIR/sweep.csv"
        stop_server
        sleep 2

        # OPA
        if $HAS_OPA; then
            echo "  --- OPA ---"
            pkill -f "opa run" 2>/dev/null; sleep 1
            run_pinned "$scpu" opa run --server --addr :8181 "$OPA_DIR/rbac.rego" >"$WRK_DIR/opa-${PCORES}p-server.log" 2>&1 &
            sleep 3
            if curl -sf http://127.0.0.1:8181/health >/dev/null 2>&1; then
                wait_cool
                wrk_warmup_converge "$ccpu" "$SCRIPT_DIR/opa/opa-rbac.lua" "http://127.0.0.1:8181/v1/data/rbac/allow"
                echo "    measure ($WRK_DURATION)..."
                OUT=$(run_pinned "$ccpu" wrk -t2 -c"$WRK_CONNS" -d"$WRK_DURATION" --latency -s "$SCRIPT_DIR/opa/opa-rbac.lua" http://127.0.0.1:8181/v1/data/rbac/allow 2>&1)
                echo "$OUT" | tee "$WRK_DIR/opa-${PCORES}p.txt"
                RPS=$(echo "$OUT" | grep "Requests/sec" | awk '{print $2}')
                P50=$(echo "$OUT" | grep "50%" | awk '{print $2}')
                P99=$(echo "$OUT" | grep "99%" | head -1 | awk '{print $2}')
                echo "$PCORES,OPA,$RPS,$P50,$P99" >> "$WRK_DIR/sweep.csv"
            else
                echo "    OPA server failed to start"
            fi
            pkill -f "opa run" 2>/dev/null
            sleep 2
        fi
    done

    echo ""
    echo "  HTTP Sweep Summary:"
    echo "  -------------------"
    cat "$WRK_DIR/sweep.csv" | column -t -s,
    echo ""
else
    echo ">>> Phase 2: HTTP Remote - SKIPPED (wrk not on PATH)"
    echo ""
fi

# ── Phase 3: RSocket Core Sweep (SAPL-only) ─────────────────────────

echo ">>> Phase 3: RSocket Core Sweep (SAPL-only capability)"
echo "================================================================"
RSOCKET_DIR="$OUTPUT_DIR/rsocket"
mkdir -p "$RSOCKET_DIR"

for PCORES in "${CORE_SWEEP[@]}"; do
    scpu=$(server_cpus "$PCORES")
    ccpu=$(client_cpus "$PCORES")
    echo ""
    echo "  === ${PCORES} P-core(s): server=$scpu client=$ccpu ==="

    start_server "$SAPL_JAR_CMD" "$POLICY_DIR/rbac-small" true
    pin_server "$scpu"
    wait_cool

    run_pinned "$ccpu" $SAPL_JAR_CMD loadtest --rsocket --host 127.0.0.1 --port 7000 \
        --connections 4 --vt-per-connection 256 \
        --warmup-seconds "$DEFAULT_WARMUP_SECONDS" --measurement-seconds 30 \
        --label "SAPL RSocket ${PCORES}P server=$scpu client=$ccpu" \
        "${RBAC_SMALL_SUB[@]}" \
        -o "$RSOCKET_DIR/${PCORES}p" 2>&1

    stop_server
    sleep 3
done
echo ""

echo "================================================================"
echo "  Engine Comparison Complete"
echo "  Embedded:  $EMBEDDED_DIR"
echo "  HTTP wrk:  $OUTPUT_DIR/http-wrk"
echo "  RSocket:   $RSOCKET_DIR"
echo "================================================================"
