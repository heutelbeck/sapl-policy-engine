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
OPA_DIR="$SCRIPT_DIR/opa"

CORE_SWEEP=(1 2 4 6 8)
CONN_SWEEP=(32 64 128 256)
WRK_DURATION=30s
OPA_BENCH_COUNT=10

mkdir -p "$OUTPUT_DIR"

echo "================================================================"
echo "  OPA Reference Benchmark"
echo "================================================================"
log_env
echo "OPA:          $(opa version 2>/dev/null | head -1)"
echo "Core sweep:   ${CORE_SWEEP[*]} P-cores"
echo "Conn sweep:   ${CONN_SWEEP[*]}"
echo "wrk duration: $WRK_DURATION"
echo ""

# ── Embedded (opa bench) ─────────────────────────────────────────────

echo ">>> Embedded (opa bench, $OPA_BENCH_COUNT iterations)"
echo "================================================================"
mkdir -p "$OUTPUT_DIR/embedded"
echo '{"subject":"bob","resource":"foo123","action":"write"}' > "$OUTPUT_DIR/embedded/input.json"
opa bench --data "$OPA_DIR/rbac.rego" --input "$OUTPUT_DIR/embedded/input.json" \
    --count "$OPA_BENCH_COUNT" --benchmem 'data.rbac.allow' | tee "$OUTPUT_DIR/embedded/opa-bench.txt"
echo ""

# ── HTTP Core x Concurrency Matrix ──────────────────────────────────

echo ">>> HTTP Core x Concurrency Matrix (wrk -t2 -d$WRK_DURATION)"
echo "================================================================"

WRK_LUA="$OUTPUT_DIR/wrk-opa.lua"
cat > "$WRK_LUA" << 'LUA'
wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"
wrk.body = '{"input":{"subject":"bob","resource":"foo123","action":"write"}}'
LUA

echo "cores,conns,rps,p50,p99,temp" > "$OUTPUT_DIR/http-matrix.csv"

for PCORES in "${CORE_SWEEP[@]}"; do
    scpu=$(server_cpus "$PCORES")
    ccpu=$(client_cpus "$PCORES")

    echo ""
    echo "  === ${PCORES} P-core(s): server=$scpu client=$ccpu ==="

    pkill -f "opa run" 2>/dev/null; sleep 1
    run_pinned "$scpu" opa run --server --addr :8181 "$OPA_DIR/rbac.rego" >/dev/null 2>&1 &
    sleep 3

    if ! curl -sf http://127.0.0.1:8181/health >/dev/null 2>&1; then
        echo "    OPA failed to start"
        continue
    fi

    for C in "${CONN_SWEEP[@]}"; do
        wait_cool

        # Warmup (Go doesn't need JIT but connection pool needs priming)
        run_pinned "$ccpu" wrk -t2 -c"$C" -d5s -s "$WRK_LUA" http://127.0.0.1:8181/v1/data/rbac/allow >/dev/null 2>&1

        # Measure
        OUT=$(run_pinned "$ccpu" wrk -t2 -c"$C" -d"$WRK_DURATION" --latency -s "$WRK_LUA" http://127.0.0.1:8181/v1/data/rbac/allow 2>&1)
        RPS=$(echo "$OUT" | grep "Requests/sec" | awk '{print $2}')
        P50=$(echo "$OUT" | grep "50%" | awk '{print $2}')
        P99=$(echo "$OUT" | grep "99%" | head -1 | awk '{print $2}')
        TEMP=$(pkg_temp)

        echo "$PCORES,$C,$RPS,$P50,$P99,$TEMP" >> "$OUTPUT_DIR/http-matrix.csv"
        echo "    ${C}c: ${RPS}/s  p50=$P50  p99=$P99  ${TEMP}C"
    done

    pkill -f "opa run" 2>/dev/null
    sleep 2
done

# ── Unpinned max throughput ──────────────────────────────────────────

echo ""
echo "  === Unpinned (all cores) ==="
pkill -f "opa run" 2>/dev/null; sleep 1
opa run --server --addr :8181 "$OPA_DIR/rbac.rego" >/dev/null 2>&1 &
sleep 3

for C in "${CONN_SWEEP[@]}"; do
    wrk -t2 -c"$C" -d5s -s "$WRK_LUA" http://127.0.0.1:8181/v1/data/rbac/allow >/dev/null 2>&1
    OUT=$(wrk -t2 -c"$C" -d"$WRK_DURATION" --latency -s "$WRK_LUA" http://127.0.0.1:8181/v1/data/rbac/allow 2>&1)
    RPS=$(echo "$OUT" | grep "Requests/sec" | awk '{print $2}')
    P50=$(echo "$OUT" | grep "50%" | awk '{print $2}')
    P99=$(echo "$OUT" | grep "99%" | head -1 | awk '{print $2}')
    TEMP=$(pkg_temp)

    echo "unpinned,$C,$RPS,$P50,$P99,$TEMP" >> "$OUTPUT_DIR/http-matrix.csv"
    echo "    ${C}c: ${RPS}/s  p50=$P50  p99=$P99  ${TEMP}C"
done

pkill -f "opa run" 2>/dev/null

# ── Print matrix ─────────────────────────────────────────────────────

echo ""
echo "================================================================"
echo "  OPA HTTP Matrix"
echo "================================================================"
column -t -s, "$OUTPUT_DIR/http-matrix.csv"
echo ""
echo "Results saved to: $OUTPUT_DIR"
echo "================================================================"
