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

# run-sapl-performance.sh - Full SAPL 4.0 performance characterization.
#
# Measures embedded throughput, HTTP remote throughput/latency, RSocket remote
# throughput/latency, thread scaling, and JVM vs native across all policy
# complexity levels.
#
# Usage:
#   ./run-sapl-performance.sh <output-dir> [quick|standard|scientific]
#
# Environment:
#   SAPL_JAR     Path to sapl-node JAR (default: ../target/sapl-node-4.0.0-SNAPSHOT.jar)
#   SAPL_NATIVE  Path to native sapl binary (default: sapl on PATH)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

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

HAS_WRK=false
if command -v wrk &> /dev/null; then HAS_WRK=true; fi

CONFIG_FILE="$SCRIPT_DIR/configs/$CONFIG.json"
if [ ! -f "$CONFIG_FILE" ]; then
    echo "Error: Config not found: $CONFIG_FILE"
    exit 1
fi

POLICY_DIR="$OUTPUT_DIR/policies"

# wrk parameters per config level
case "$CONFIG" in
    quick)      WRK_THREADS=2;  WRK_CONNS=32;  WRK_DURATION=10s; WRK_WARMUP=5s ;;
    standard)   WRK_THREADS=4;  WRK_CONNS=128; WRK_DURATION=30s; WRK_WARMUP=15s ;;
    scientific) WRK_THREADS=8;  WRK_CONNS=256; WRK_DURATION=60s; WRK_WARMUP=30s ;;
    *) echo "Error: Unknown config: $CONFIG"; exit 1 ;;
esac

EMBEDDED_SCENARIOS="empty simple-1 simple-100 simple-500 complex-1 complex-100 all-match-100 rbac-small rbac-large abac-equivalent"
REMOTE_SCENARIOS="rbac-small simple-1 simple-100 complex-1 complex-100"

STANDARD_SUB=(-s '{"name":"alice","roles":["admin"],"department":"engineering","clearanceLevel":5}' -a '"read"' -r '"document"')
RBAC_SMALL_SUB=(-s '{"username":"bob","role":"test"}' -a '"write"' -r '{"type":"foo123"}')
RBAC_LARGE_SUB=(-s '{"username":"bob","role":"qa-berlin-junior"}' -a '"write"' -r '{"type":"engineering-london"}')
ABAC_SUB=(-s '{"username":"bob","department":"qa","location":"berlin","seniority":"junior"}' -a '"write"' -r '{"department":"engineering","location":"london"}')

run_benchmark_scenario() {
    local cmd=$1
    local scenario=$2
    local scenario_dir=$3
    local config=$4
    local output_dir=$5
    shift 5
    local extra_args=("$@")

    case "$scenario" in
        rbac-small)      $cmd benchmark --dir "$scenario_dir" "${RBAC_SMALL_SUB[@]}" -c "$config" -o "$output_dir" "${extra_args[@]}" ;;
        rbac-large)      $cmd benchmark --dir "$scenario_dir" "${RBAC_LARGE_SUB[@]}" -c "$config" -o "$output_dir" "${extra_args[@]}" ;;
        abac-equivalent) $cmd benchmark --dir "$scenario_dir" "${ABAC_SUB[@]}" -c "$config" -o "$output_dir" "${extra_args[@]}" ;;
        *)               $cmd benchmark --dir "$scenario_dir" "${STANDARD_SUB[@]}" -c "$config" -o "$output_dir" "${extra_args[@]}" ;;
    esac
}

run_remote_scenario() {
    local cmd=$1
    local scenario=$2
    local config=$3
    local output_dir=$4
    shift 4
    local extra_args=("$@")

    case "$scenario" in
        rbac-small)      $cmd benchmark "${RBAC_SMALL_SUB[@]}" -c "$config" -o "$output_dir" "${extra_args[@]}" ;;
        rbac-large)      $cmd benchmark "${RBAC_LARGE_SUB[@]}" -c "$config" -o "$output_dir" "${extra_args[@]}" ;;
        abac-equivalent) $cmd benchmark "${ABAC_SUB[@]}" -c "$config" -o "$output_dir" "${extra_args[@]}" ;;
        *)               $cmd benchmark "${STANDARD_SUB[@]}" -c "$config" -o "$output_dir" "${extra_args[@]}" ;;
    esac
}

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
    local url=${1:-http://localhost:8443/actuator/health}
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

start_server() {
    local cmd=$1
    local scenario_dir=$2
    local rsocket=${3:-false}
    stop_server
    local extra_args=""
    if [ "$rsocket" = "true" ]; then
        extra_args="--sapl.pdp.rsocket.enabled=true"
    fi
    cd "$scenario_dir"
    $cmd server --io.sapl.node.allow-no-auth=true $extra_args > "$OUTPUT_DIR/server-$$.log" 2>&1 &
    SERVER_PID=$!
    cd "$SCRIPT_DIR"
    wait_for_server
}

run_embedded() {
    local cmd=$1
    local label=$2
    local results_dir="$OUTPUT_DIR/$label"

    echo ">>> Embedded ($label)"
    echo "================================================================"
    bash "$SCRIPT_DIR/generate-policies.sh" "$cmd" "$POLICY_DIR" 2>/dev/null

    # No-op baseline (once, using empty policy dir)
    echo "  === noOp baseline ==="
    local noop_config=$(mktemp)
    python3 -c "
import json
with open('$CONFIG_FILE') as f: cfg = json.load(f)
cfg['benchmarks'] = ['noOp']
print(json.dumps(cfg))
" > "$noop_config"
    mkdir -p "$results_dir/noOp"
    $cmd benchmark --dir "$POLICY_DIR/empty" "${STANDARD_SUB[@]}" -c "$noop_config" -o "$results_dir/noOp"
    rm -f "$noop_config"

    for scenario in $EMBEDDED_SCENARIOS; do
        local scenario_dir="$POLICY_DIR/$scenario"
        if [ ! -d "$scenario_dir" ]; then
            echo "  Skipping $scenario (not generated)"
            continue
        fi
        echo "  === $scenario ==="
        mkdir -p "$results_dir/$scenario"
        run_benchmark_scenario "$cmd" "$scenario" "$scenario_dir" "$CONFIG_FILE" "$results_dir/$scenario"
    done
    echo
}

run_http_remote() {
    local server_cmd=$1
    local label=$2
    local results_dir="$OUTPUT_DIR/$label"

    echo ">>> HTTP Remote ($label)"
    echo "================================================================"

    # Create temporary remote config adding concurrent + raw methods
    local remote_config=$(mktemp)
    python3 -c "
import json
with open('$CONFIG_FILE') as f: cfg = json.load(f)
cfg['benchmarks'] = ['decideOnceBlocking', 'decideStreamFirst', 'decideOnceConcurrent', 'decideOnceRaw']
print(json.dumps(cfg))
" > "$remote_config"

    for scenario in $REMOTE_SCENARIOS; do
        local scenario_dir="$POLICY_DIR/$scenario"
        if [ ! -d "$scenario_dir" ]; then
            echo "  Skipping $scenario"
            continue
        fi
        echo "  === $scenario ==="
        start_server "$server_cmd" "$scenario_dir"
        mkdir -p "$results_dir/$scenario"
        run_remote_scenario "$SAPL_JAR_CMD" "$scenario" "$remote_config" "$results_dir/$scenario" --remote --url http://localhost:8443

        # wrk ceiling test (only for rbac-small - uses matching Lua script)
        if $HAS_WRK && [ "$scenario" = "rbac-small" ]; then
            echo "  --- wrk ceiling ($WRK_WARMUP warmup, $WRK_DURATION measurement) ---"
            wrk -t"$WRK_THREADS" -c"$WRK_CONNS" -d"$WRK_WARMUP" -s "$SCRIPT_DIR/wrk/sapl-rbac.lua" http://localhost:8443/api/pdp/decide-once > /dev/null 2>&1
            wrk -t"$WRK_THREADS" -c"$WRK_CONNS" -d"$WRK_DURATION" --latency -s "$SCRIPT_DIR/wrk/sapl-rbac.lua" http://localhost:8443/api/pdp/decide-once | tee "$results_dir/$scenario/wrk.txt"
        fi
        stop_server
    done
    rm -f "$remote_config"
    echo
}

run_rsocket_remote() {
    local server_cmd=$1
    local label=$2
    local results_dir="$OUTPUT_DIR/$label"

    echo ">>> RSocket Remote ($label)"
    echo "================================================================"

    # Create temporary rsocket config (concurrent but no raw)
    local rsocket_config=$(mktemp)
    python3 -c "
import json
with open('$CONFIG_FILE') as f: cfg = json.load(f)
cfg['benchmarks'] = ['decideOnceBlocking', 'decideStreamFirst', 'decideOnceConcurrent']
print(json.dumps(cfg))
" > "$rsocket_config"

    for scenario in $REMOTE_SCENARIOS; do
        local scenario_dir="$POLICY_DIR/$scenario"
        if [ ! -d "$scenario_dir" ]; then
            echo "  Skipping $scenario"
            continue
        fi
        echo "  === $scenario ==="
        start_server "$server_cmd" "$scenario_dir" true
        mkdir -p "$results_dir/$scenario"
        run_remote_scenario "$SAPL_JAR_CMD" "$scenario" "$rsocket_config" "$results_dir/$scenario" --remote --rsocket --host localhost --port 7000
        stop_server
    done
    rm -f "$rsocket_config"
    echo
}

echo "================================================================"
echo "  SAPL 4.0 Performance Benchmark"
echo "================================================================"
echo "Config:  $CONFIG"
echo "Output:  $OUTPUT_DIR"
echo "JAR:     $SAPL_JAR"
echo "Native:  $( $HAS_NATIVE && echo "$SAPL_NATIVE" || echo "not found" )"
echo "wrk:     $( $HAS_WRK && echo "available" || echo "not found" )"
echo "================================================================"
echo

# Phase 1+2: Embedded
run_embedded "$SAPL_JAR_CMD" "embedded-jvm"
if $HAS_NATIVE; then
    run_embedded "$SAPL_NATIVE" "embedded-native"
fi

# Phase 3+4: HTTP Remote
run_http_remote "$SAPL_JAR_CMD" "http-jvm-server"
if $HAS_NATIVE; then
    run_http_remote "$SAPL_NATIVE" "http-native-server"
fi

# Phase 5+6: RSocket Remote
run_rsocket_remote "$SAPL_JAR_CMD" "rsocket-jvm-server"
if $HAS_NATIVE; then
    run_rsocket_remote "$SAPL_NATIVE" "rsocket-native-server"
fi

echo "================================================================"
echo "  SAPL Performance Benchmark Complete"
echo "  Results: $OUTPUT_DIR"
echo "================================================================"
