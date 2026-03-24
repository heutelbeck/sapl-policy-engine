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

# run-remote-wrk.sh - Benchmarks policy engines via HTTP using wrk.
#
# Starts a server, benchmarks it with wrk, and stops it. Supports SAPL
# (JVM and native) and OPA servers for fair cross-engine comparison using
# the same benchmark tool and RBAC policy.
#
# Requires: wrk on PATH.
#
# Usage:
#   ./run-remote-wrk.sh <engine> <output-dir> [config]
#
# Arguments:
#   engine       sapl-jvm | sapl-native | opa
#   output-dir   Directory for results
#   config       quick | standard | scientific (default: standard)
#
# Environment:
#   SAPL_JAR     Path to sapl-node JAR (default: ../target/sapl-node-4.0.0-SNAPSHOT.jar)
#   SAPL_NATIVE  Path to native sapl binary (default: sapl on PATH)
#   OPA_BIN      Path to opa binary (default: opa on PATH)
#
# Examples:
#   ./run-remote-wrk.sh sapl-jvm /tmp/bench-remote
#   ./run-remote-wrk.sh sapl-native /tmp/bench-remote scientific
#   ./run-remote-wrk.sh opa /tmp/bench-remote quick

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ $# -lt 2 ] || [ $# -gt 3 ]; then
    echo "Usage: $0 <sapl-jvm|sapl-native|opa> <output-dir> [quick|standard|scientific]"
    exit 1
fi

if ! command -v wrk &> /dev/null; then
    echo "Error: wrk not found on PATH"
    exit 1
fi

ENGINE=$1
OUTPUT_DIR=$2
CONFIG=${3:-standard}

SAPL_JAR="${SAPL_JAR:-$SCRIPT_DIR/../target/sapl-node-4.0.0-SNAPSHOT.jar}"
SAPL_NATIVE="${SAPL_NATIVE:-sapl}"
OPA_BIN="${OPA_BIN:-opa}"

# wrk parameters per config level
case "$CONFIG" in
    quick)      WRK_THREADS=2;  WRK_CONNS=32;  WRK_DURATION=10s ;;
    standard)   WRK_THREADS=4;  WRK_CONNS=128; WRK_DURATION=30s ;;
    scientific) WRK_THREADS=8;  WRK_CONNS=256; WRK_DURATION=60s ;;
    *) echo "Error: Unknown config: $CONFIG (use quick, standard, or scientific)"; exit 1 ;;
esac

POLICY_DIR="$OUTPUT_DIR/policies"
RESULTS_DIR="$OUTPUT_DIR/results/$ENGINE"
mkdir -p "$RESULTS_DIR"

SERVER_PID=""

cleanup() {
    if [ -n "$SERVER_PID" ] && kill -0 "$SERVER_PID" 2>/dev/null; then
        echo "Stopping server (PID $SERVER_PID)..."
        kill "$SERVER_PID" 2>/dev/null || true
        wait "$SERVER_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT

wait_for_server() {
    local url=$1
    local max_wait=${2:-30}
    echo -n "Waiting for server at $url"
    for i in $(seq 1 "$max_wait"); do
        if curl -s --max-time 2 -o /dev/null "$url" 2>/dev/null; then
            echo " ready (${i}s)"
            return 0
        fi
        echo -n "."
        sleep 1
    done
    echo " TIMEOUT after ${max_wait}s"
    return 1
}

start_sapl_server() {
    local cmd=$1
    bash "$SCRIPT_DIR/generate-policies.sh" "$POLICY_DIR" 2>/dev/null
    cd "$POLICY_DIR/rbac-small"
    $cmd server > "$RESULTS_DIR/server.log" 2>&1 &
    SERVER_PID=$!
    cd "$SCRIPT_DIR"
    wait_for_server "http://localhost:8443/actuator/health"
}

start_opa_server() {
    $OPA_BIN run --server --addr :8181 "$SCRIPT_DIR/opa/rbac.rego" > "$RESULTS_DIR/server.log" 2>&1 &
    SERVER_PID=$!
    wait_for_server "http://localhost:8181/health"
}

run_wrk() {
    local label=$1
    local url=$2
    local lua_script=$3
    echo "--- $label: wrk -t$WRK_THREADS -c$WRK_CONNS -d$WRK_DURATION ---"
    wrk -t"$WRK_THREADS" -c"$WRK_CONNS" -d"$WRK_DURATION" --latency -s "$lua_script" "$url" | tee "$RESULTS_DIR/$label.txt"
    echo
}

echo "================================================================"
echo "  Remote Benchmark (wrk): $ENGINE"
echo "  Config: $CONFIG (wrk -t$WRK_THREADS -c$WRK_CONNS -d$WRK_DURATION)"
echo "  Output: $RESULTS_DIR"
echo "================================================================"
echo

case "$ENGINE" in
    sapl-jvm)
        start_sapl_server "java -jar $SAPL_JAR"
        run_wrk "sapl-jvm-rbac" "http://localhost:8443/api/pdp/decide-once" "$SCRIPT_DIR/wrk/sapl-rbac.lua"
        ;;
    sapl-native)
        start_sapl_server "$SAPL_NATIVE"
        run_wrk "sapl-native-rbac" "http://localhost:8443/api/pdp/decide-once" "$SCRIPT_DIR/wrk/sapl-rbac.lua"
        ;;
    opa)
        start_opa_server
        run_wrk "opa-rbac" "http://localhost:8181/v1/data/rbac/allow" "$SCRIPT_DIR/wrk/opa-rbac.lua"
        ;;
    *)
        echo "Error: Unknown engine: $ENGINE (use sapl-jvm, sapl-native, or opa)"
        exit 1
        ;;
esac

echo "================================================================"
echo "  Benchmark complete: $ENGINE"
echo "  Results: $RESULTS_DIR"
echo "================================================================"
