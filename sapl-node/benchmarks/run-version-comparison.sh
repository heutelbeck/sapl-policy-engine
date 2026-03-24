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

# run-version-comparison.sh - Quick SAPL 3 vs SAPL 4 embedded comparison.
#
# Runs both engines on the same scenarios with matching JMH parameters.
# Produces per-scenario throughput numbers for a direct improvement factor.
#
# Usage:
#   ./run-version-comparison.sh <output-dir>
#
# Prerequisites:
#   - sapl-node built: mvn package -pl sapl-node -DskipTests
#   - sapl-benchmark3 built: cd ~/git/sapl-benchmark3 && mvn package -q
#
# Environment:
#   SAPL_JAR        Path to sapl-node JAR
#   SAPL3_JAR       Path to sapl-benchmark3 JAR

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ $# -ne 1 ]; then
    echo "Usage: $0 <output-dir>"
    exit 1
fi

OUTPUT_DIR=$1

SAPL_JAR="${SAPL_JAR:-$SCRIPT_DIR/../target/sapl-node-4.0.0-SNAPSHOT.jar}"
SAPL3_JAR="${SAPL3_JAR:-$HOME/git/sapl-benchmark3/target/sapl-benchmark3-1.0.0.jar}"
SAPL_JAR_CMD="java -jar $SAPL_JAR"

# Quick config matching sapl-benchmark3's hardcoded params (1w x 1s, 2m x 2s, 1 thread)
CONFIG_FILE="$SCRIPT_DIR/configs/quick.json"

SCENARIOS="empty simple-1 simple-100 simple-500 complex-1 complex-100 all-match-100 rbac-small rbac-large abac-equivalent"

STANDARD_SUB=(-s '{"name":"alice","roles":["admin"],"department":"engineering","clearanceLevel":5}' -a '"read"' -r '"document"')
RBAC_SMALL_SUB=(-s '{"username":"bob","role":"test"}' -a '"write"' -r '{"type":"foo123"}')
RBAC_LARGE_SUB=(-s '{"username":"bob","role":"qa-berlin-junior"}' -a '"write"' -r '{"type":"engineering-london"}')
ABAC_SUB=(-s '{"username":"bob","department":"qa","location":"berlin","seniority":"junior"}' -a '"write"' -r '{"department":"engineering","location":"london"}')

echo "================================================================"
echo "  SAPL Version Comparison (3.0 vs 4.0)"
echo "================================================================"
echo "SAPL 4 JAR: $SAPL_JAR"
echo "SAPL 3 JAR: $SAPL3_JAR"
echo "Output:     $OUTPUT_DIR"
echo "================================================================"
echo

if [ ! -f "$SAPL3_JAR" ]; then
    echo "Error: sapl-benchmark3 JAR not found at $SAPL3_JAR"
    echo "Build it: cd ~/git/sapl-benchmark3 && mvn package -q"
    exit 1
fi

# Phase 1: Generate SAPL 4 policies
POLICY_DIR="$OUTPUT_DIR/policies"
bash "$SCRIPT_DIR/generate-policies.sh" "$SAPL_JAR_CMD" "$POLICY_DIR"

# Phase 2: SAPL 4 embedded (quick config, blocking only)
echo
echo ">>> SAPL 4.0 Embedded"
echo "================================================================"
SAPL4_DIR="$OUTPUT_DIR/sapl4"

for scenario in $SCENARIOS; do
    scenario_dir="$POLICY_DIR/$scenario"
    if [ ! -d "$scenario_dir" ]; then
        echo "  Skipping $scenario"
        continue
    fi
    echo "  === $scenario ==="
    mkdir -p "$SAPL4_DIR/$scenario"
    case "$scenario" in
        rbac-small)      $SAPL_JAR_CMD benchmark --dir "$scenario_dir" "${RBAC_SMALL_SUB[@]}" -c "$CONFIG_FILE" -o "$SAPL4_DIR/$scenario" ;;
        rbac-large)      $SAPL_JAR_CMD benchmark --dir "$scenario_dir" "${RBAC_LARGE_SUB[@]}" -c "$CONFIG_FILE" -o "$SAPL4_DIR/$scenario" ;;
        abac-equivalent) $SAPL_JAR_CMD benchmark --dir "$scenario_dir" "${ABAC_SUB[@]}" -c "$CONFIG_FILE" -o "$SAPL4_DIR/$scenario" ;;
        *)               $SAPL_JAR_CMD benchmark --dir "$scenario_dir" "${STANDARD_SUB[@]}" -c "$CONFIG_FILE" -o "$SAPL4_DIR/$scenario" ;;
    esac
done

# Phase 3: SAPL 3 (uses its own policy generator and JMH params aligned to quick)
echo
echo ">>> SAPL 3.0 Embedded"
echo "================================================================"
java -jar "$SAPL3_JAR" "$OUTPUT_DIR/sapl3"

echo
echo "================================================================"
echo "  Version Comparison Complete"
echo "  SAPL 4: $SAPL4_DIR"
echo "  SAPL 3: $OUTPUT_DIR/sapl3"
echo "================================================================"
