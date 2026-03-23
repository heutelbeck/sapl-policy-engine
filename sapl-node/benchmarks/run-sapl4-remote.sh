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

# run-sapl4-remote.sh - Benchmarks a running SAPL 4.0 PDP server via HTTP.
#
# Prerequisites: Start a server first, e.g.:
#   sapl server --dir ./policies --server.port=8443
#
# Usage:
#   ./run-sapl4-remote.sh <sapl-command> <output-dir> [config] [url]
#
# Arguments:
#   sapl-command   "java -jar path/to/sapl-node.jar" or path to native binary
#   output-dir     Directory for results
#   config         quick | standard | scientific (default: standard)
#   url            Remote PDP URL (default: http://localhost:8443)
#
# Examples:
#   ./run-sapl4-remote.sh "java -jar ../target/sapl-node-4.0.0-SNAPSHOT.jar" /tmp/bench-remote
#   ./run-sapl4-remote.sh ../target/sapl /tmp/bench-remote standard https://pdp.example.com

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ $# -lt 2 ] || [ $# -gt 4 ]; then
    echo "Usage: $0 <sapl-command> <output-dir> [quick|standard|scientific] [url]"
    exit 1
fi

SAPL_CMD=$1
OUTPUT_DIR=$2
CONFIG_NAME=${3:-standard}
URL=${4:-http://localhost:8443}

if [ -f "$CONFIG_NAME" ]; then
    CONFIG_FILE="$CONFIG_NAME"
elif [ -f "$SCRIPT_DIR/configs/$CONFIG_NAME.json" ]; then
    CONFIG_FILE="$SCRIPT_DIR/configs/$CONFIG_NAME.json"
else
    echo "Error: Config not found: $CONFIG_NAME"
    exit 1
fi

RESULTS_DIR="$OUTPUT_DIR/results"
mkdir -p "$RESULTS_DIR"

echo "=== SAPL 4.0 Remote Benchmark ==="
echo "Command: $SAPL_CMD"
echo "URL:     $URL"
echo "Config:  $CONFIG_FILE"
echo "Output:  $OUTPUT_DIR"
echo

STANDARD_SUB=(-s '{"name":"alice","roles":["admin"],"department":"engineering","clearanceLevel":5}' -a '"read"' -r '"document"')

echo "=== remote ==="
$SAPL_CMD benchmark \
    --remote --url "$URL" \
    "${STANDARD_SUB[@]}" \
    -c "$CONFIG_FILE" \
    -o "$RESULTS_DIR/remote"
echo

echo "=== Benchmark complete ==="
echo "Results: $RESULTS_DIR"
