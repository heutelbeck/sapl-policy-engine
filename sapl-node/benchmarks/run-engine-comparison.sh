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

set -euo pipefail

# run-engine-comparison.sh - Cross-engine comparison: SAPL 4 vs OPA.
#
# Phase 1: Embedded comparison using each engine's native benchmark tool
# Phase 2: HTTP remote comparison using wrk (same tool, same params, fair)
# Phase 3: RSocket demonstration (SAPL-only capability)
#
# Usage:
#   ./run-engine-comparison.sh <output-dir> [quick|standard|scientific]
#
# Prerequisites:
#   wrk on PATH, opa on PATH (or via: nix develop ./opa)
#
# Environment:
#   SAPL_JAR     Path to sapl-node JAR
#   SAPL_NATIVE  Path to native sapl binary

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OPA_DIR="$SCRIPT_DIR/opa"

if [ $# -lt 1 ] || [ $# -gt 2 ]; then
    echo "Usage: $0 <output-dir> [quick|standard|scientific]"
    exit 1
fi

OUTPUT_DIR=$1
CONFIG=${2:-standard}

export SAPL_JAR="${SAPL_JAR:-$SCRIPT_DIR/../target/sapl-node-4.0.0-SNAPSHOT.jar}"
export SAPL_NATIVE="${SAPL_NATIVE:-sapl}"
SAPL_JAR_CMD="java -jar $SAPL_JAR"

HAS_NATIVE=false
if command -v "$SAPL_NATIVE" &> /dev/null || [ -x "$SAPL_NATIVE" ]; then HAS_NATIVE=true; fi

HAS_OPA=false
if command -v opa &> /dev/null; then HAS_OPA=true; fi

HAS_WRK=false
if command -v wrk &> /dev/null; then HAS_WRK=true; fi

CONFIG_FILE="$SCRIPT_DIR/configs/$CONFIG.json"

# wrk parameters
case "$CONFIG" in
    quick)      WRK_THREADS=2;  WRK_CONNS=32;  WRK_DURATION=10s; WRK_WARMUP=5s;  OPA_COUNT=10 ;;
    standard)   WRK_THREADS=4;  WRK_CONNS=128; WRK_DURATION=30s; WRK_WARMUP=15s; OPA_COUNT=20 ;;
    scientific) WRK_THREADS=8;  WRK_CONNS=256; WRK_DURATION=60s; WRK_WARMUP=30s; OPA_COUNT=50 ;;
    *) echo "Error: Unknown config: $CONFIG"; exit 1 ;;
esac

POLICY_DIR="$OUTPUT_DIR/policies"
RBAC_SUB=(-s '{"username":"bob","role":"test"}' -a '"write"' -r '{"type":"foo123"}')

SERVER_PID=""

cleanup() {
    local exit_code=$?
    if [ -n "$SERVER_PID" ] && kill -0 "$SERVER_PID" 2>/dev/null; then
        kill "$SERVER_PID" 2>/dev/null || true
        wait "$SERVER_PID" 2>/dev/null || true
    fi
    if [ $exit_code -ne 0 ]; then
        echo "Error: script failed with exit code $exit_code" >&2
    fi
}
trap cleanup EXIT

wait_for_server() {
    local url=$1
    local max_wait=${2:-30}
    echo -n "  Waiting for server"
    for i in $(seq 1 "$max_wait"); do
        if curl -s --max-time 2 -o /dev/null "$url" 2>/dev/null; then
            echo " ready (${i}s)"
            return 0
        fi
        echo -n "."
        sleep 1
    done
    echo " TIMEOUT"
    return 1
}

stop_server() {
    if [ -n "$SERVER_PID" ] && kill -0 "$SERVER_PID" 2>/dev/null; then
        kill "$SERVER_PID" 2>/dev/null || true
        wait "$SERVER_PID" 2>/dev/null || true
        SERVER_PID=""
        sleep 1
    fi
}

echo "================================================================"
echo "  Engine Comparison: SAPL 4 vs OPA"
echo "================================================================"
echo "Config:  $CONFIG"
echo "Output:  $OUTPUT_DIR"
echo "OPA:     $( $HAS_OPA && echo "$(opa version 2>/dev/null | head -1)" || echo "not found (skipping)" )"
echo "wrk:     $( $HAS_WRK && echo "available" || echo "not found (skipping remote)" )"
echo "Native:  $( $HAS_NATIVE && echo "$SAPL_NATIVE" || echo "not found" )"
echo "================================================================"
echo

# Generate SAPL policies
bash "$SCRIPT_DIR/generate-policies.sh" "$SAPL_JAR_CMD" "$POLICY_DIR" 2>/dev/null

# ── Phase 1: Embedded Comparison ─────────────────────────────────────

echo ">>> Phase 1: Embedded Comparison (RBAC deny case)"
echo "================================================================"

EMBEDDED_DIR="$OUTPUT_DIR/embedded"
mkdir -p "$EMBEDDED_DIR"

echo "  --- SAPL 4 JVM ---"
mkdir -p "$EMBEDDED_DIR/sapl-jvm"
$SAPL_JAR_CMD benchmark --dir "$POLICY_DIR/rbac-small" "${RBAC_SUB[@]}" -c "$CONFIG_FILE" -o "$EMBEDDED_DIR/sapl-jvm"

if $HAS_NATIVE; then
    echo "  --- SAPL 4 native ---"
    mkdir -p "$EMBEDDED_DIR/sapl-native"
    $SAPL_NATIVE benchmark --dir "$POLICY_DIR/rbac-small" "${RBAC_SUB[@]}" -c "$CONFIG_FILE" -o "$EMBEDDED_DIR/sapl-native"
fi

if $HAS_OPA; then
    echo "  --- OPA ---"
    mkdir -p "$EMBEDDED_DIR/opa"
    # Create input file for fair comparison (OPA must parse input, not use inline data)
    echo '{"subject":"bob","resource":"foo123","action":"write"}' > "$EMBEDDED_DIR/opa/input.json"
    opa bench --data "$OPA_DIR/rbac.rego" --input "$EMBEDDED_DIR/opa/input.json" --count "$OPA_COUNT" --benchmem --format json 'data.rbac.allow' > "$EMBEDDED_DIR/opa/opa-bench.json"
    opa bench --data "$OPA_DIR/rbac.rego" --input "$EMBEDDED_DIR/opa/input.json" --count "$OPA_COUNT" --benchmem 'data.rbac.allow' | tee "$EMBEDDED_DIR/opa/opa-bench.txt"
fi
echo

# ── Phase 2: HTTP Remote Comparison (wrk) ────────────────────────────

if $HAS_WRK; then
    echo ">>> Phase 2: HTTP Remote Comparison (wrk -t$WRK_THREADS -c$WRK_CONNS -d$WRK_DURATION)"
    echo "================================================================"

    WRK_DIR="$OUTPUT_DIR/http-wrk"
    mkdir -p "$WRK_DIR"

    run_wrk_test() {
        local label=$1
        local server_cmd=$2
        local url=$3
        local lua=$4
        local health_url=$5

        echo "  --- $label ---"
        cd "$POLICY_DIR/rbac-small"
        $server_cmd server --io.sapl.node.allow-no-auth=true > "$WRK_DIR/$label-server.log" 2>&1 &
        SERVER_PID=$!
        cd "$SCRIPT_DIR"
        wait_for_server "$health_url"

        echo "  warmup ($WRK_WARMUP)..."
        wrk -t"$WRK_THREADS" -c"$WRK_CONNS" -d"$WRK_WARMUP" -s "$lua" "$url" > /dev/null 2>&1
        echo "  measurement ($WRK_DURATION)..."
        wrk -t"$WRK_THREADS" -c"$WRK_CONNS" -d"$WRK_DURATION" --latency -s "$lua" "$url" | tee "$WRK_DIR/$label.txt"
        stop_server
        echo
    }

    run_wrk_test "sapl-jvm" "$SAPL_JAR_CMD" "http://localhost:8443/api/pdp/decide-once" "$SCRIPT_DIR/wrk/sapl-rbac.lua" "http://localhost:8443/actuator/health"

    if $HAS_NATIVE; then
        run_wrk_test "sapl-native" "$SAPL_NATIVE" "http://localhost:8443/api/pdp/decide-once" "$SCRIPT_DIR/wrk/sapl-rbac.lua" "http://localhost:8443/actuator/health"
    fi

    if $HAS_OPA; then
        echo "  --- OPA server ---"
        opa run --server --addr :8181 "$OPA_DIR/rbac.rego" > "$WRK_DIR/opa-server.log" 2>&1 &
        SERVER_PID=$!
        wait_for_server "http://localhost:8181/health"

        echo "  warmup ($WRK_WARMUP)..."
        wrk -t"$WRK_THREADS" -c"$WRK_CONNS" -d"$WRK_WARMUP" -s "$SCRIPT_DIR/wrk/opa-rbac.lua" http://localhost:8181/v1/data/rbac/allow > /dev/null 2>&1
        echo "  measurement ($WRK_DURATION)..."
        wrk -t"$WRK_THREADS" -c"$WRK_CONNS" -d"$WRK_DURATION" --latency -s "$SCRIPT_DIR/wrk/opa-rbac.lua" http://localhost:8181/v1/data/rbac/allow | tee "$WRK_DIR/opa.txt"
        stop_server
        echo
    fi
else
    echo ">>> Phase 2: HTTP Remote - SKIPPED (wrk not on PATH)"
    echo
fi

# ── Phase 3: RSocket Demonstration (SAPL-only) ──────────────────────

echo ">>> Phase 3: RSocket (SAPL-only capability)"
echo "================================================================"

RSOCKET_DIR="$OUTPUT_DIR/rsocket"
mkdir -p "$RSOCKET_DIR"

# Create rsocket config
RSOCKET_CONFIG=$(mktemp)
python3 -c "
import json
with open('$CONFIG_FILE') as f: cfg = json.load(f)
cfg['benchmarks'] = ['decideOnceBlocking', 'decideOnceConcurrent']
print(json.dumps(cfg))
" > "$RSOCKET_CONFIG"

echo "  --- SAPL JVM server + RSocket ---"
cd "$POLICY_DIR/rbac-small"
$SAPL_JAR_CMD server --io.sapl.node.allow-no-auth=true --sapl.pdp.rsocket.enabled=true > "$RSOCKET_DIR/server.log" 2>&1 &
SERVER_PID=$!
cd "$SCRIPT_DIR"
wait_for_server "http://localhost:8443/actuator/health"

mkdir -p "$RSOCKET_DIR/sapl-jvm"
$SAPL_JAR_CMD benchmark --remote --rsocket --host localhost --port 7000 "${RBAC_SUB[@]}" -c "$RSOCKET_CONFIG" -o "$RSOCKET_DIR/sapl-jvm"
stop_server

rm -f "$RSOCKET_CONFIG"
echo

echo "================================================================"
echo "  Engine Comparison Complete"
echo "  Embedded:  $EMBEDDED_DIR"
echo "  HTTP wrk:  $OUTPUT_DIR/http-wrk"
echo "  RSocket:   $RSOCKET_DIR"
echo "================================================================"
