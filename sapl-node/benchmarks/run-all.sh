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

# run-all.sh - Runs the complete benchmark suite across all engines and modes.
#
# Embedded: SAPL 4 (JVM + native), OPA, SAPL 3
# Remote (wrk): SAPL JVM server, SAPL native server, OPA server
# Remote (SAPL client): all server/client permutations (JVM/native)
#
# Usage:
#   ./run-all.sh <output-dir> [config]
#
# Arguments:
#   output-dir   Root directory for all results
#   config       quick | standard | scientific (default: standard)
#
# Environment:
#   SAPL_JAR     Path to sapl-node JAR (default: ../target/sapl-node-4.0.0-SNAPSHOT.jar)
#   SAPL_NATIVE  Path to native sapl binary (default: sapl on PATH)
#
# Prerequisites:
#   - sapl-node built:        mvn package -pl sapl-node -DskipTests
#   - sapl-node test-compiled: mvn test-compile -pl sapl-node
#   - For native benchmarks:  native binary built (nix develop ~/.dotfiles#graalvm)
#   - For SAPL 3:             cd ~/git/sapl-benchmark3 && mvn package -q
#   - For OPA:                nix develop ./opa (or opa on PATH)
#   - For remote wrk:         wrk on PATH
#
# Examples:
#   ./run-all.sh /tmp/benchmark-results quick
#   ./run-all.sh /tmp/benchmark-results scientific

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ $# -lt 1 ] || [ $# -gt 2 ]; then
    echo "Usage: $0 <output-dir> [quick|standard|scientific]"
    exit 1
fi

OUTPUT_DIR=$1
CONFIG=${2:-standard}

export SAPL_JAR="${SAPL_JAR:-$SCRIPT_DIR/../target/sapl-node-4.0.0-SNAPSHOT.jar}"
export SAPL_NATIVE="${SAPL_NATIVE:-sapl}"
SAPL_JAR_CMD="java -jar $SAPL_JAR"

HAS_NATIVE=false
if command -v "$SAPL_NATIVE" &> /dev/null || [ -x "$SAPL_NATIVE" ]; then HAS_NATIVE=true; fi

HAS_OPA=false
if command -v opa &> /dev/null; then HAS_OPA=true; fi

HAS_WRK=false
if command -v wrk &> /dev/null; then HAS_WRK=true; fi

HAS_SAPL3=false
if [ -f "$HOME/git/sapl-benchmark3/target/sapl-benchmark3-1.0.0.jar" ]; then HAS_SAPL3=true; fi

echo "================================================================"
echo "  SAPL Benchmark Suite"
echo "================================================================"
echo "Config:         $CONFIG"
echo "Output:         $OUTPUT_DIR"
echo "SAPL JAR:       $SAPL_JAR"
echo "SAPL native:    $( $HAS_NATIVE && echo "$SAPL_NATIVE" || echo "not found (skipping native)" )"
echo "OPA:            $( $HAS_OPA && echo "$(opa version 2>/dev/null | head -1)" || echo "not found (skipping OPA)" )"
echo "wrk:            $( $HAS_WRK && echo "available" || echo "not found (skipping wrk remote)" )"
echo "SAPL 3:         $( $HAS_SAPL3 && echo "available" || echo "not found (skipping)" )"
echo "================================================================"
echo

# ── Embedded Benchmarks ──────────────────────────────────────────────

echo ">>> [1/7] SAPL 4.0 Embedded (JVM)"
echo "================================================================"
bash "$SCRIPT_DIR/run-sapl4.sh" "$SAPL_JAR_CMD" "$OUTPUT_DIR/embedded-jvm" "$CONFIG"
echo

if $HAS_NATIVE; then
    echo ">>> [2/7] SAPL 4.0 Embedded (native)"
    echo "================================================================"
    bash "$SCRIPT_DIR/run-sapl4.sh" "$SAPL_NATIVE" "$OUTPUT_DIR/embedded-native" "$CONFIG"
    echo
else
    echo ">>> [2/7] SAPL 4.0 Embedded (native) - SKIPPED"
    echo
fi

if $HAS_OPA; then
    echo ">>> [3/7] OPA Embedded"
    echo "================================================================"
    bash "$SCRIPT_DIR/run-opa.sh" "$OUTPUT_DIR/opa-embedded"
    echo
else
    echo ">>> [3/7] OPA Embedded - SKIPPED (opa not on PATH)"
    echo
fi

if $HAS_SAPL3; then
    echo ">>> [4/7] SAPL 3.0 Comparison"
    echo "================================================================"
    bash "$SCRIPT_DIR/run-sapl3.sh" "$OUTPUT_DIR/sapl3"
    echo
else
    echo ">>> [4/7] SAPL 3.0 Comparison - SKIPPED"
    echo
fi

# ── Remote Benchmarks (wrk - cross-engine comparison) ────────────────

if $HAS_WRK; then
    echo ">>> [5/7] Remote (wrk) - Cross-Engine Comparison"
    echo "================================================================"

    echo "--- SAPL JVM server + wrk ---"
    bash "$SCRIPT_DIR/run-remote-wrk.sh" sapl-jvm "$OUTPUT_DIR/remote-wrk" "$CONFIG"
    echo

    if $HAS_NATIVE; then
        echo "--- SAPL native server + wrk ---"
        bash "$SCRIPT_DIR/run-remote-wrk.sh" sapl-native "$OUTPUT_DIR/remote-wrk" "$CONFIG"
        echo
    fi

    if $HAS_OPA; then
        echo "--- OPA server + wrk ---"
        bash "$SCRIPT_DIR/run-remote-wrk.sh" opa "$OUTPUT_DIR/remote-wrk" "$CONFIG"
        echo
    fi
else
    echo ">>> [5/7] Remote (wrk) - SKIPPED (wrk not on PATH)"
    echo
fi

# ── Remote Benchmarks (SAPL client - all permutations) ───────────────

echo ">>> [6/7] Remote (SAPL client) - JVM server"
echo "================================================================"

echo "--- JVM server + JVM client ---"
bash "$SCRIPT_DIR/run-sapl4-remote.sh" "$SAPL_JAR_CMD" "$SAPL_JAR_CMD" "$OUTPUT_DIR/remote-jvm-jvm" "$CONFIG"
echo

if $HAS_NATIVE; then
    echo "--- JVM server + native client ---"
    bash "$SCRIPT_DIR/run-sapl4-remote.sh" "$SAPL_JAR_CMD" "$SAPL_NATIVE" "$OUTPUT_DIR/remote-jvm-native" "$CONFIG"
    echo
fi

if $HAS_NATIVE; then
    echo ">>> [7/7] Remote (SAPL client) - native server"
    echo "================================================================"

    echo "--- native server + JVM client ---"
    bash "$SCRIPT_DIR/run-sapl4-remote.sh" "$SAPL_NATIVE" "$SAPL_JAR_CMD" "$OUTPUT_DIR/remote-native-jvm" "$CONFIG"
    echo

    echo "--- native server + native client ---"
    bash "$SCRIPT_DIR/run-sapl4-remote.sh" "$SAPL_NATIVE" "$SAPL_NATIVE" "$OUTPUT_DIR/remote-native-native" "$CONFIG"
    echo
else
    echo ">>> [7/7] Remote (SAPL client) - native server - SKIPPED"
    echo
fi

echo "================================================================"
echo "  Benchmark Suite Complete"
echo "================================================================"
echo "Results: $OUTPUT_DIR/"
echo
echo "  Embedded JVM:        $OUTPUT_DIR/embedded-jvm/results/"
echo "  Embedded native:     $OUTPUT_DIR/embedded-native/results/"
echo "  OPA embedded:        $OUTPUT_DIR/opa-embedded/"
echo "  SAPL 3:              $OUTPUT_DIR/sapl3/"
echo "  Remote wrk:          $OUTPUT_DIR/remote-wrk/results/"
echo "  Remote JVM+JVM:      $OUTPUT_DIR/remote-jvm-jvm/results/"
echo "  Remote JVM+native:   $OUTPUT_DIR/remote-jvm-native/results/"
echo "  Remote native+JVM:   $OUTPUT_DIR/remote-native-jvm/results/"
echo "  Remote native+native:$OUTPUT_DIR/remote-native-native/results/"
echo "================================================================"
