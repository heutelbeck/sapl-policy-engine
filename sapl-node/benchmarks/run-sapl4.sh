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

# run-sapl4.sh - Benchmarks SAPL 4.0 embedded PDP across all policy scenarios.
#
# Usage:
#   ./run-sapl4.sh <sapl-command> <output-dir> [config]
#
# Arguments:
#   sapl-command   "java -jar path/to/sapl-node.jar" or path to native binary
#   output-dir     Directory for generated policies and results
#   config         quick | standard | scientific (default: standard)
#                  Or path to a custom JSON config file
#
# Examples:
#   ./run-sapl4.sh "java -jar ../target/sapl-node-4.0.0-SNAPSHOT.jar" /tmp/bench-jvm
#   ./run-sapl4.sh ../target/sapl /tmp/bench-native quick
#   ./run-sapl4.sh "java -jar ../target/sapl-node-4.0.0-SNAPSHOT.jar" /tmp/bench scientific

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ $# -lt 2 ] || [ $# -gt 3 ]; then
    echo "Usage: $0 <sapl-command> <output-dir> [quick|standard|scientific|config-path]"
    exit 1
fi

SAPL_CMD=$1
OUTPUT_DIR=$2
CONFIG_NAME=${3:-standard}

# Resolve config file
if [ -f "$CONFIG_NAME" ]; then
    CONFIG_FILE="$CONFIG_NAME"
elif [ -f "$SCRIPT_DIR/configs/$CONFIG_NAME.json" ]; then
    CONFIG_FILE="$SCRIPT_DIR/configs/$CONFIG_NAME.json"
else
    echo "Error: Config not found: $CONFIG_NAME"
    echo "Available: quick, standard, scientific, or a path to a JSON file"
    exit 1
fi

POLICY_DIR="$OUTPUT_DIR/policies"
RESULTS_DIR="$OUTPUT_DIR/results"

echo "=== SAPL 4.0 Benchmark ==="
echo "Command: $SAPL_CMD"
echo "Config:  $CONFIG_FILE"
echo "Output:  $OUTPUT_DIR"
echo

# Generate policies (determine if --large is needed based on config)
LARGE_FLAG=""
if grep -q '"simple-1000\|"5000\|"10000' "$CONFIG_FILE" 2>/dev/null; then
    LARGE_FLAG="--large"
fi
bash "$SCRIPT_DIR/generate-policies.sh" "$SAPL_CMD" "$POLICY_DIR" $LARGE_FLAG

echo

# Standard subscription for policy count/complexity scenarios
STANDARD_SUB=(-s '{"name":"alice","roles":["admin"],"department":"engineering","clearanceLevel":5}' -a '"read"' -r '"document"')

# RBAC subscriptions
RBAC_SMALL_SUB=(-s '{"username":"bob","role":"test"}' -a '"write"' -r '{"type":"foo123"}')
RBAC_LARGE_SUB=(-s '{"username":"bob","role":"qa-berlin-junior"}' -a '"write"' -r '{"type":"engineering-london"}')
ABAC_SUB=(-s '{"username":"bob","department":"qa","location":"berlin","seniority":"junior"}' -a '"write"' -r '{"department":"engineering","location":"london"}')

run_scenario() {
    local scenario=$1
    shift
    local sub=("$@")

    local scenario_dir="$POLICY_DIR/$scenario"
    if [ ! -d "$scenario_dir" ]; then
        echo "Skipping $scenario (not generated)"
        return
    fi

    echo "=== $scenario ==="
    mkdir -p "$RESULTS_DIR/$scenario"
    $SAPL_CMD benchmark \
        --dir "$scenario_dir" \
        "${sub[@]}" \
        -c "$CONFIG_FILE" \
        -o "$RESULTS_DIR/$scenario"
    echo
}

# Policy count scaling
for scenario in empty simple-1 simple-100 simple-500 simple-1000 simple-5000 simple-10000; do
    run_scenario "$scenario" "${STANDARD_SUB[@]}"
done

# Policy complexity
for scenario in complex-1 complex-100 complex-1000 complex-5000 complex-10000; do
    run_scenario "$scenario" "${STANDARD_SUB[@]}"
done

# All-match (worst-case combining)
for scenario in all-match-100 all-match-1000; do
    run_scenario "$scenario" "${STANDARD_SUB[@]}"
done

# RBAC vs ABAC
run_scenario "rbac-small" "${RBAC_SMALL_SUB[@]}"
run_scenario "rbac-large" "${RBAC_LARGE_SUB[@]}"
run_scenario "abac-equivalent" "${ABAC_SUB[@]}"

echo "=== Benchmark complete ==="
echo "Results: $RESULTS_DIR"
echo "Reports: $RESULTS_DIR/*/  (*.md, *.csv, *.json)"
