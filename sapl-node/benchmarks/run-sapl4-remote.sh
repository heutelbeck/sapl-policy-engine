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

# run-sapl4-remote.sh - Benchmarks SAPL 4.0 remote PDP with built-in client.
#
# Starts a SAPL server, benchmarks it with the sapl benchmark command,
# and stops the server. Supports all server/client permutations (JVM/native).
#
# Usage:
#   ./run-sapl4-remote.sh <server-cmd> <client-cmd> <output-dir> [config]
#
# Arguments:
#   server-cmd   Command to start the server ("java -jar path.jar" or native binary path)
#   client-cmd   Command for the benchmark client ("java -jar path.jar" or native binary path)
#   output-dir   Directory for results
#   config       quick | standard | scientific (default: standard)
#                Resolves to remote-quick.json, remote-standard.json, etc.
#
# Examples:
#   ./run-sapl4-remote.sh "java -jar ../target/sapl-node-4.0.0-SNAPSHOT.jar" "java -jar ../target/sapl-node-4.0.0-SNAPSHOT.jar" /tmp/bench jvm-jvm
#   ./run-sapl4-remote.sh ../target/sapl ../target/sapl /tmp/bench native-native

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ $# -lt 3 ] || [ $# -gt 4 ]; then
    echo "Usage: $0 <server-command> <client-command> <output-dir> [quick|standard|scientific]"
    exit 1
fi

SERVER_CMD=$1
CLIENT_CMD=$2
OUTPUT_DIR=$3
CONFIG_NAME=${4:-standard}

# Resolve config file (prefer remote-* variant)
if [ -f "$CONFIG_NAME" ]; then
    CONFIG_FILE="$CONFIG_NAME"
elif [ -f "$SCRIPT_DIR/configs/remote-$CONFIG_NAME.json" ]; then
    CONFIG_FILE="$SCRIPT_DIR/configs/remote-$CONFIG_NAME.json"
elif [ -f "$SCRIPT_DIR/configs/$CONFIG_NAME.json" ]; then
    CONFIG_FILE="$SCRIPT_DIR/configs/$CONFIG_NAME.json"
else
    echo "Error: Config not found: $CONFIG_NAME"
    exit 1
fi

POLICY_DIR="$OUTPUT_DIR/policies"
RESULTS_DIR="$OUTPUT_DIR/results"
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
    local max_wait=${1:-30}
    echo -n "Waiting for server at http://localhost:8443"
    for i in $(seq 1 "$max_wait"); do
        if curl -s --max-time 2 -o /dev/null "http://localhost:8443/actuator/health" 2>/dev/null; then
            echo " ready (${i}s)"
            return 0
        fi
        echo -n "."
        sleep 1
    done
    echo " TIMEOUT after ${max_wait}s"
    return 1
}

# Generate policies
bash "$SCRIPT_DIR/generate-policies.sh" "$POLICY_DIR" 2>/dev/null

STANDARD_SUB=(-s '{"name":"alice","roles":["admin"],"department":"engineering","clearanceLevel":5}' -a '"read"' -r '"document"')
RBAC_SMALL_SUB=(-s '{"username":"bob","role":"test"}' -a '"write"' -r '{"type":"foo123"}')
RBAC_LARGE_SUB=(-s '{"username":"bob","role":"qa-berlin-junior"}' -a '"write"' -r '{"type":"engineering-london"}')
ABAC_SUB=(-s '{"username":"bob","department":"qa","location":"berlin","seniority":"junior"}' -a '"write"' -r '{"department":"engineering","location":"london"}')

run_remote_scenario() {
    local scenario=$1
    shift
    local sub=("$@")

    local scenario_dir="$POLICY_DIR/$scenario"
    if [ ! -d "$scenario_dir" ]; then
        echo "Skipping $scenario (not generated)"
        return
    fi

    # Stop previous server if running
    if [ -n "$SERVER_PID" ] && kill -0 "$SERVER_PID" 2>/dev/null; then
        kill "$SERVER_PID" 2>/dev/null || true
        wait "$SERVER_PID" 2>/dev/null || true
        SERVER_PID=""
        sleep 1
    fi

    # Start server with this scenario's policies
    echo "=== $scenario ==="
    cd "$scenario_dir"
    $SERVER_CMD server > "$RESULTS_DIR/$scenario-server.log" 2>&1 &
    SERVER_PID=$!
    cd "$SCRIPT_DIR"
    wait_for_server

    mkdir -p "$RESULTS_DIR/$scenario"
    $CLIENT_CMD benchmark --remote --url http://localhost:8443 "${sub[@]}" -c "$CONFIG_FILE" -o "$RESULTS_DIR/$scenario"
    echo
}

echo "=== SAPL 4.0 Remote Benchmark ==="
echo "Server:  $SERVER_CMD"
echo "Client:  $CLIENT_CMD"
echo "Config:  $CONFIG_FILE"
echo "Output:  $OUTPUT_DIR"
echo

# RBAC scenarios
run_remote_scenario "rbac-small" "${RBAC_SMALL_SUB[@]}"
run_remote_scenario "rbac-large" "${RBAC_LARGE_SUB[@]}"
run_remote_scenario "abac-equivalent" "${ABAC_SUB[@]}"

# Standard scenarios
for scenario in simple-1 simple-100; do
    run_remote_scenario "$scenario" "${STANDARD_SUB[@]}"
done

echo "=== Benchmark complete ==="
echo "Results: $RESULTS_DIR"
