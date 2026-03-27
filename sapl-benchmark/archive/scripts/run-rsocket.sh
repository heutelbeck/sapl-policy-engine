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

# RSocket server load test.
#
# Usage: ./run-rsocket.sh <output-dir> [jvm|native|both] [quick|standard]
#
# Environment:
#   SAPL_JAR, SAPL_NATIVE, SERVER_CPUS, CLIENT_CPUS

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

if [ $# -lt 1 ]; then
    echo "Usage: $0 <output-dir> [jvm|native|both] [quick|standard]"
    exit 1
fi

OUTPUT_DIR=$1
SERVER_TYPE=${2:-both}
CONFIG=${3:-quick}
SAPL_JAR="${SAPL_JAR:-$SCRIPT_DIR/../target/sapl-node-4.0.0-SNAPSHOT.jar}"
SAPL_JAR_CMD="java -jar $SAPL_JAR"
SAPL_NATIVE_CMD="${SAPL_NATIVE:-sapl}"
POLICY_DIR="$OUTPUT_DIR/policies"
SCPUS="${SERVER_CPUS:-$(server_cpus 8)}"
CCPUS="${CLIENT_CPUS:-$(client_cpus 8)}"

case "$CONFIG" in
    quick)    CONN_LEVELS=(4) ; VT=256 ;;
    standard) CONN_LEVELS=(1 2 4 8) ; VT=256 ;;
    *)        echo "Unknown config: $CONFIG"; exit 1 ;;
esac

mkdir -p "$OUTPUT_DIR"
trap_cleanup

echo "================================================================"
echo "  RSocket Server Load Test ($CONFIG)"
echo "================================================================"
log_env
echo "Server CPUs: $SCPUS"
echo "Client CPUs: $CCPUS"
echo ""

# Generate policies
if [ ! -d "$POLICY_DIR/rbac-small" ]; then
    $SAPL_JAR_CMD generate-benchmark-policies "$POLICY_DIR" 2>/dev/null
fi

run_rsocket_test() {
    local server_cmd=$1
    local label=$2
    local results_dir="$OUTPUT_DIR/$label"
    mkdir -p "$results_dir"

    echo ">>> $label"
    echo "----------------------------------------------------------------"

    start_server "$server_cmd" "$POLICY_DIR/rbac-small" true
    pin_server "$SCPUS"

    wait_cool

    for C in "${CONN_LEVELS[@]}"; do
        local total=$((C * VT))
        echo "  --- ${C} connections x ${VT} VT = ${total} total ---"

        run_pinned "$CCPUS" $SAPL_JAR_CMD loadtest --rsocket --host 127.0.0.1 --port 7000 \
            --connections "$C" --vt-per-connection "$VT" \
            --warmup-seconds "$DEFAULT_WARMUP_SECONDS" --measurement-seconds "$DEFAULT_MEASURE_SECONDS" \
            --label "$label server=$SCPUS client=$CCPUS ${C}conn x ${VT}vt" \
            "${RBAC_SMALL_SUB[@]}" \
            -o "$results_dir/${C}conn" 2>&1
        echo "    $(pkg_temp)C"
        echo ""
    done

    stop_server
    echo ""
}

if [ "$SERVER_TYPE" = "jvm" ] || [ "$SERVER_TYPE" = "both" ]; then
    run_rsocket_test "$SAPL_JAR_CMD" "rsocket-jvm"
fi

if [ "$SERVER_TYPE" = "native" ] || [ "$SERVER_TYPE" = "both" ]; then
    if $HAS_NATIVE; then
        run_rsocket_test "$SAPL_NATIVE_CMD" "rsocket-native"
    else
        echo "Native binary not found, skipping."
    fi
fi

echo "================================================================"
echo "  RSocket load test complete. Results: $OUTPUT_DIR"
echo "================================================================"
