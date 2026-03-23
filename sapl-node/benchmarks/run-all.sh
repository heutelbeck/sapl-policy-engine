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

# run-all.sh - Runs the complete benchmark suite across all engines.
#
# Runs SAPL 4.0 (embedded), SAPL 3.0 (comparison), and OPA (RBAC) benchmarks
# on the same machine for a fair cross-engine comparison.
#
# Usage:
#   ./run-all.sh <sapl4-command> <output-dir> [config]
#
# Arguments:
#   sapl4-command  "java -jar path/to/sapl-node.jar" or path to native binary
#   output-dir     Root directory for all results
#   config         quick | standard | scientific (default: standard)
#
# Examples:
#   ./run-all.sh "java -jar ../target/sapl-node-4.0.0-SNAPSHOT.jar" /tmp/benchmark-results
#   ./run-all.sh ../target/sapl /tmp/benchmark-results-native scientific
#
# Prerequisites:
#   - sapl-node built:        mvn package -pl sapl-node -DskipTests
#   - sapl-node test-compiled: mvn test-compile -pl sapl-node
#   - sapl-benchmark3 built:  cd ~/git/sapl-benchmark3 && mvn package -q
#   - OPA on PATH:            nix develop ./opa  (or install manually)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ $# -lt 2 ] || [ $# -gt 3 ]; then
    echo "Usage: $0 <sapl4-command> <output-dir> [quick|standard|scientific]"
    exit 1
fi

SAPL_CMD=$1
OUTPUT_DIR=$2
CONFIG=${3:-standard}

echo "================================================================"
echo "  SAPL Benchmark Suite"
echo "================================================================"
echo "SAPL 4 command: $SAPL_CMD"
echo "Config:         $CONFIG"
echo "Output:         $OUTPUT_DIR"
echo "================================================================"
echo

# SAPL 4.0
echo ">>> SAPL 4.0 Embedded Benchmark"
echo "================================================================"
bash "$SCRIPT_DIR/run-sapl4.sh" "$SAPL_CMD" "$OUTPUT_DIR/sapl4" "$CONFIG"
echo

# SAPL 3.0
echo ">>> SAPL 3.0 Comparison Benchmark"
echo "================================================================"
if [ -f "$HOME/git/sapl-benchmark3/target/sapl-benchmark3-1.0.0.jar" ]; then
    bash "$SCRIPT_DIR/run-sapl3.sh" "$OUTPUT_DIR/sapl3"
else
    echo "Skipping: sapl-benchmark3 JAR not found."
    echo "Build it: cd ~/git/sapl-benchmark3 && mvn package -q"
fi
echo

# OPA
echo ">>> OPA RBAC Benchmark"
echo "================================================================"
if command -v opa &> /dev/null; then
    bash "$SCRIPT_DIR/run-opa.sh" "$OUTPUT_DIR/opa"
else
    echo "Skipping: opa not found on PATH."
    echo "Install: nix develop $SCRIPT_DIR/opa"
fi
echo

echo "================================================================"
echo "  Benchmark Suite Complete"
echo "================================================================"
echo "SAPL 4 results: $OUTPUT_DIR/sapl4/results/"
echo "SAPL 3 results: $OUTPUT_DIR/sapl3/results/"
echo "OPA results:    $OUTPUT_DIR/opa/"
echo "================================================================"
