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

# run-opa.sh - Benchmarks OPA on the same RBAC policy used in the OPA documentation.
#
# This provides a direct comparison point: same policy logic, same data, same
# deny-case query, measured on the same hardware as the SAPL benchmarks.
#
# Requires: OPA binary on PATH.
#   Option 1: nix develop ./opa   (uses the flake in benchmarks/opa/)
#   Option 2: Install OPA manually (https://www.openpolicyagent.org/docs/latest/#1-download-opa)
#
# Usage:
#   ./run-opa.sh <output-dir> [count]
#
# Arguments:
#   output-dir   Directory for results
#   count        Number of benchmark repetitions (default: 10)
#
# Examples:
#   nix develop ./opa --command bash -c "./run-opa.sh /tmp/bench-opa"
#   ./run-opa.sh /tmp/bench-opa 20

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OPA_DIR="$SCRIPT_DIR/opa"

if [ $# -lt 1 ] || [ $# -gt 2 ]; then
    echo "Usage: $0 <output-dir> [count]"
    exit 1
fi

if ! command -v opa &> /dev/null; then
    echo "Error: opa not found on PATH"
    echo "Install via: nix develop $OPA_DIR"
    echo "         or: https://www.openpolicyagent.org/docs/latest/#1-download-opa"
    exit 1
fi

OUTPUT_DIR=$1
COUNT=${2:-10}

mkdir -p "$OUTPUT_DIR"

echo "=== OPA RBAC Benchmark ==="
echo "OPA version: $(opa version | head -1)"
echo "Policy: $OPA_DIR/rbac.rego"
echo "Count: $COUNT repetitions"
echo "Output: $OUTPUT_DIR"
echo

echo "--- RBAC deny case (matching OPA docs benchmark) ---"
echo "Query: data.rbac.allow (evaluates to false: bob/test role requests write)"
echo

opa bench \
    --data "$OPA_DIR/rbac.rego" \
    --count "$COUNT" \
    --benchmem \
    --format json \
    'data.rbac.allow' > "$OUTPUT_DIR/opa-rbac-bench.json"

echo "JSON results saved to: $OUTPUT_DIR/opa-rbac-bench.json"
echo

# Also run in pretty format for human-readable output
echo "--- Results (pretty) ---"
opa bench \
    --data "$OPA_DIR/rbac.rego" \
    --count "$COUNT" \
    --benchmem \
    'data.rbac.allow' | tee "$OUTPUT_DIR/opa-rbac-bench.txt"

echo
echo "=== OPA Benchmark complete ==="
