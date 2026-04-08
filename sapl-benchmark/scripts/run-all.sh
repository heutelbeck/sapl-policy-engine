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

# Run all benchmarks: quick pass first for validation, then full pass for
# credible numbers. Skips full pass if quick fails.
#
# Usage: run-all.sh [output-dir]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUTPUT_DIR=${1:-$SCRIPT_DIR/../results}

FAILED=""

run() {
    local label=$1
    shift
    echo ""
    echo "################################################################"
    echo "  $label"
    echo "################################################################"
    echo ""
    if "$@"; then
        echo "  OK: $label"
    else
        echo "  FAILED: $label"
        FAILED="$FAILED  $label\n"
    fi
    echo ""
}

for quality in quick full; do
    echo ""
    echo "================================================================"
    echo "  QUALITY: $quality"
    echo "  Output:  $OUTPUT_DIR"
    echo "================================================================"

    # Embedded throughput
    run "$quality / embedded" \
        "$SCRIPT_DIR/run-embedded.sh" "$quality" "$OUTPUT_DIR" embedded

    # Embedded index comparison
    run "$quality / index-comparison" \
        "$SCRIPT_DIR/run-embedded.sh" "$quality" "$OUTPUT_DIR" index-comparison

    # Embedded latency (Cedar scenarios)
    run "$quality / latency-cedar" \
        "$SCRIPT_DIR/run-latency-bench.sh" "$quality" latency-cedar "$OUTPUT_DIR"

    # Embedded latency (hospital scaling)
    run "$quality / latency-hospital-scaling" \
        "$SCRIPT_DIR/run-latency-bench.sh" "$quality" latency-hospital-scaling "$OUTPUT_DIR"

    # Embedded latency (hospital index comparison)
    run "$quality / latency-hospital-index" \
        "$SCRIPT_DIR/run-latency-bench.sh" "$quality" latency-hospital-index "$OUTPUT_DIR"

    # Server HTTP
    run "$quality / server-http" \
        "$SCRIPT_DIR/run-server.sh" "$quality" "$OUTPUT_DIR" server-http

    # Server RSocket
    run "$quality / server-rsocket" \
        "$SCRIPT_DIR/run-server.sh" "$quality" "$OUTPUT_DIR" server-rsocket

    # Latency at load (rate sweep)
    run "$quality / latency-at-load" \
        "$SCRIPT_DIR/run-latency-at-load.sh" "$quality" "$OUTPUT_DIR"
done

echo ""
echo "================================================================"
echo "  All Benchmarks Complete"
echo "  Results: $OUTPUT_DIR"
if [ -n "$FAILED" ]; then
    echo ""
    echo "  FAILURES:"
    echo -e "$FAILED"
fi
echo "================================================================"
