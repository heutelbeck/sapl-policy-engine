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

# run-sapl3.sh - Runs the SAPL 3.0 comparison benchmark.
#
# Requires: sapl-benchmark3 project built at ~/git/sapl-benchmark3/
#   cd ~/git/sapl-benchmark3 && mvn package -q
#
# Usage:
#   ./run-sapl3.sh <output-dir> [--large]
#
# Examples:
#   ./run-sapl3.sh /tmp/bench-sapl3
#   ./run-sapl3.sh /tmp/bench-sapl3-large --large

BENCHMARK3_JAR="$HOME/git/sapl-benchmark3/target/sapl-benchmark3-1.0.0.jar"

if [ $# -lt 1 ] || [ $# -gt 2 ]; then
    echo "Usage: $0 <output-dir> [--large]"
    exit 1
fi

if [ ! -f "$BENCHMARK3_JAR" ]; then
    echo "Error: sapl-benchmark3 JAR not found at $BENCHMARK3_JAR"
    echo "Build it first: cd ~/git/sapl-benchmark3 && mvn package -q"
    exit 1
fi

OUTPUT_DIR=$1
LARGE_FLAG=${2:-}

echo "=== SAPL 3.0 Comparison Benchmark ==="
echo "JAR:    $BENCHMARK3_JAR"
echo "Output: $OUTPUT_DIR"
echo

java -jar "$BENCHMARK3_JAR" "$OUTPUT_DIR" $LARGE_FLAG
