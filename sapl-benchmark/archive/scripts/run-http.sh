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

# HTTP server load test with wrk and Java loadtest tool.
#
# Usage: ./run-http.sh <output-dir> [jvm|native|both] [quick|standard]
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
    quick)    CONCURRENCIES=(64) ;;
    standard) CONCURRENCIES=(4 16 64 128 256) ;;
    *)        echo "Unknown config: $CONFIG"; exit 1 ;;
esac

mkdir -p "$OUTPUT_DIR"
trap_cleanup

echo "================================================================"
echo "  HTTP Server Load Test ($CONFIG)"
echo "================================================================"
log_env
echo "Server CPUs: $SCPUS"
echo "Client CPUs: $CCPUS"
echo ""

# Generate policies
if [ ! -d "$POLICY_DIR/rbac-small" ]; then
    $SAPL_JAR_CMD generate-benchmark-policies "$POLICY_DIR" 2>/dev/null
fi

WRK_LUA="$OUTPUT_DIR/wrk-rbac.lua"
cat > "$WRK_LUA" << 'LUA'
wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"
wrk.body = '{"subject":{"username":"bob","role":"test"},"action":"write","resource":{"type":"foo123"}}'
LUA

run_http_test() {
    local server_cmd=$1
    local label=$2
    local results_dir="$OUTPUT_DIR/$label"
    mkdir -p "$results_dir"

    echo ">>> $label"
    echo "----------------------------------------------------------------"

    start_server "$server_cmd" "$POLICY_DIR/rbac-small"
    pin_server "$SCPUS"

    wait_cool

    for C in "${CONCURRENCIES[@]}"; do
        echo "  --- ${C} concurrent ---"

        # wrk (if available)
        if $HAS_WRK; then
            run_pinned "$CCPUS" wrk -t2 -c"$C" -d5s -s "$WRK_LUA" http://127.0.0.1:8443/api/pdp/decide-once >/dev/null 2>&1
            WRK_OUT=$(run_pinned "$CCPUS" wrk -t2 -c"$C" -d${DEFAULT_MEASURE_SECONDS}s --latency -s "$WRK_LUA" http://127.0.0.1:8443/api/pdp/decide-once 2>&1)
            WRK_RPS=$(echo "$WRK_OUT" | grep "Requests/sec" | awk '{print $2}')
            WRK_LAT=$(echo "$WRK_OUT" | grep "Latency" | head -1 | awk '{print $2}')
            echo "    wrk:  ${WRK_RPS}/s (lat: $WRK_LAT)"
        fi

        # Java loadtest
        run_pinned "$CCPUS" $SAPL_JAR_CMD loadtest --url http://127.0.0.1:8443 \
            --concurrency "$C" \
            --warmup-seconds "$DEFAULT_WARMUP_SECONDS" --measurement-seconds "$DEFAULT_MEASURE_SECONDS" \
            --label "$label server=$SCPUS client=$CCPUS ${C}conns" \
            "${RBAC_SMALL_SUB[@]}" \
            -o "$results_dir" 2>&1
        echo ""
    done

    stop_server
    echo ""
}

if [ "$SERVER_TYPE" = "jvm" ] || [ "$SERVER_TYPE" = "both" ]; then
    run_http_test "$SAPL_JAR_CMD" "http-jvm"
fi

if [ "$SERVER_TYPE" = "native" ] || [ "$SERVER_TYPE" = "both" ]; then
    if $HAS_NATIVE; then
        run_http_test "$SAPL_NATIVE_CMD" "http-native"
    else
        echo "Native binary not found, skipping."
    fi
fi

echo "================================================================"
echo "  HTTP load test complete. Results: $OUTPUT_DIR"
echo "================================================================"
