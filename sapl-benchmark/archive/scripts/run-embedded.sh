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

# Embedded PDP benchmark (JMH for JVM, timing loops for native).
#
# Usage: ./run-embedded.sh <output-dir> [quick|standard|scientific]
#
# Environment:
#   SAPL_JAR     Path to sapl-node JAR
#   SAPL_NATIVE  Path to native binary

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

if [ $# -lt 1 ]; then
    echo "Usage: $0 <output-dir> [quick|standard|scientific]"
    exit 1
fi

OUTPUT_DIR=$1
CONFIG=${2:-quick}
SAPL_JAR="${SAPL_JAR:-$SCRIPT_DIR/../target/sapl-node-4.0.0-SNAPSHOT.jar}"
SAPL_JAR_CMD="java -jar $SAPL_JAR"
SAPL_NATIVE_CMD="${SAPL_NATIVE:-sapl}"
POLICY_DIR="$OUTPUT_DIR/policies"
CONFIG_FILE="$SCRIPT_DIR/configs/$CONFIG.json"

if [ ! -f "$CONFIG_FILE" ]; then
    echo "Error: Config not found: $CONFIG_FILE"
    exit 1
fi

SCENARIOS="empty simple-1 simple-100 simple-500 complex-1 complex-100 all-match-100 rbac-small rbac-large abac-equivalent"

mkdir -p "$OUTPUT_DIR"

echo "================================================================"
echo "  Embedded PDP Benchmark ($CONFIG)"
echo "================================================================"
log_env

# Generate policies
if [ ! -d "$POLICY_DIR/rbac-small" ]; then
    echo "Generating policies..."
    $SAPL_JAR_CMD generate-benchmark-policies "$POLICY_DIR"
    echo ""
fi

# JVM benchmarks
echo ">>> JVM (JMH)"
echo "================================================================"
for scenario in $SCENARIOS; do
    scenario_dir="$POLICY_DIR/$scenario"
    if [ ! -d "$scenario_dir" ]; then continue; fi

    results_dir="$OUTPUT_DIR/jvm/$scenario"
    mkdir -p "$results_dir"

    echo "  --- $scenario ---"
    sub=($(sub_for_scenario "$scenario"))
    $SAPL_JAR_CMD benchmark --dir "$scenario_dir" "${sub[@]}" -c "$CONFIG_FILE" -o "$results_dir" 2>&1 | tail -5
    echo ""
done

# Native benchmarks
if $HAS_NATIVE; then
    echo ">>> Native (AOT)"
    echo "================================================================"
    for scenario in $SCENARIOS; do
        scenario_dir="$POLICY_DIR/$scenario"
        if [ ! -d "$scenario_dir" ]; then continue; fi

        results_dir="$OUTPUT_DIR/native/$scenario"
        mkdir -p "$results_dir"

        echo "  --- $scenario ---"
        sub=($(sub_for_scenario "$scenario"))
        $SAPL_NATIVE_CMD benchmark --dir "$scenario_dir" "${sub[@]}" -c "$CONFIG_FILE" -o "$results_dir" 2>&1 | tail -5
        echo ""
    done
else
    echo "Native binary not found, skipping native benchmarks."
fi

echo "================================================================"
echo "  Embedded benchmark complete. Results: $OUTPUT_DIR"
echo "================================================================"
