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

# run-all.sh - Runs the complete SAPL benchmark suite.
#
# Three benchmarks:
#   1. SAPL Performance    - Full SAPL 4.0 characterization (embedded + HTTP + RSocket)
#   2. Version Comparison  - SAPL 3 vs 4 quick improvement proof
#   3. Engine Comparison   - SAPL vs OPA (fair HTTP + RSocket value prop)
#
# Usage:
#   ./run-all.sh <output-dir> [quick|standard|scientific]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ $# -lt 1 ] || [ $# -gt 2 ]; then
    echo "Usage: $0 <output-dir> [quick|standard|scientific]"
    exit 1
fi

OUTPUT_DIR=$1
CONFIG=${2:-standard}

echo "================================================================"
echo "  SAPL Benchmark Suite"
echo "================================================================"
echo "Config: $CONFIG"
echo "Output: $OUTPUT_DIR"
echo "================================================================"
echo

echo ">>> [1/3] SAPL Performance"
echo "================================================================"
bash "$SCRIPT_DIR/run-sapl-performance.sh" "$OUTPUT_DIR/performance" "$CONFIG"
echo

echo ">>> [2/3] Version Comparison (SAPL 3 vs 4)"
echo "================================================================"
bash "$SCRIPT_DIR/run-version-comparison.sh" "$OUTPUT_DIR/version-comparison"
echo

echo ">>> [3/3] Engine Comparison (SAPL vs OPA)"
echo "================================================================"
bash "$SCRIPT_DIR/run-engine-comparison.sh" "$OUTPUT_DIR/engine-comparison" "$CONFIG"
echo

echo "================================================================"
echo "  Benchmark Suite Complete"
echo "================================================================"
echo "  Performance:         $OUTPUT_DIR/performance/"
echo "  Version Comparison:  $OUTPUT_DIR/version-comparison/"
echo "  Engine Comparison:   $OUTPUT_DIR/engine-comparison/"
echo "================================================================"
