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

# Shared benchmark library. Source this from benchmark scripts:
#   SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
#   source "$SCRIPT_DIR/lib/common.sh"

# Note: no set -e here. Benchmark scripts must handle errors explicitly
# because server stop/kill can return non-zero harmlessly.

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

COOL_TARGET="${COOL_TARGET:-38}"
DEFAULT_MEASURE_SECONDS="${DEFAULT_MEASURE_SECONDS:-10}"
DEFAULT_WARMUP_SECONDS="${DEFAULT_WARMUP_SECONDS:-5}"
CONVERGE_THRESHOLD=5
CONVERGE_WINDOW=3
MAX_WARMUP_ITERS=15
WARMUP_INTERVAL=3

# ---------------------------------------------------------------------------
# Subscriptions (pre-defined for standard benchmark scenarios)
# ---------------------------------------------------------------------------

STANDARD_SUB=(-s '{"name":"alice","roles":["admin"],"department":"engineering","clearanceLevel":5}' -a '"read"' -r '"document"')
RBAC_SMALL_SUB=(-s '{"username":"bob","role":"test"}' -a '"write"' -r '{"type":"foo123"}')
RBAC_LARGE_SUB=(-s '{"username":"bob","role":"engineering-london-senior"}' -a '"write"' -r '{"type":"engineering-london"}')
ABAC_SUB=(-s '{"name":"alice","department":"engineering","location":"london","seniority":"senior"}' -a '"read"' -r '{"department":"engineering","location":"london"}')

sub_for_scenario() {
    local scenario=$1
    case "$scenario" in
        rbac-small)      echo "${RBAC_SMALL_SUB[@]}" ;;
        rbac-large)      echo "${RBAC_LARGE_SUB[@]}" ;;
        abac-equivalent) echo "${ABAC_SUB[@]}" ;;
        *)               echo "${STANDARD_SUB[@]}" ;;
    esac
}

# ---------------------------------------------------------------------------
# Environment detection
# ---------------------------------------------------------------------------

detect_cpu_topology() {
    TOTAL_CPUS=$(nproc)
    # Detect P-core count (cores with max freq > 5GHz are P-cores on Intel hybrid)
    P_CORE_CPUS=""
    E_CORE_CPUS=""
    local max_freq_threshold=5000000  # 5GHz in kHz

    if [ -f /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq ]; then
        for cpu_dir in /sys/devices/system/cpu/cpu[0-9]*/; do
            local cpu_id=$(basename "$cpu_dir" | sed 's/cpu//')
            local max_freq=$(cat "$cpu_dir/cpufreq/cpuinfo_max_freq" 2>/dev/null || echo 0)
            if [ "$max_freq" -gt "$max_freq_threshold" ]; then
                P_CORE_CPUS="${P_CORE_CPUS:+$P_CORE_CPUS,}$cpu_id"
            else
                E_CORE_CPUS="${E_CORE_CPUS:+$E_CORE_CPUS,}$cpu_id"
            fi
        done
    fi

    # Fallback: if no hybrid detected, all cores are P-cores
    if [ -z "$P_CORE_CPUS" ]; then
        P_CORE_CPUS="0-$((TOTAL_CPUS - 1))"
        E_CORE_CPUS=""
    fi

    # Count P-cores (physical, not logical)
    local threads_per_core=$(lscpu 2>/dev/null | grep -i "thread.*per\|pro Kern" | awk '{print $NF}')
    threads_per_core=${threads_per_core:-1}
    local p_cpu_count=$(echo "$P_CORE_CPUS" | tr ',' '\n' | wc -l)
    P_CORE_COUNT=$((p_cpu_count / threads_per_core))
    E_CORE_COUNT=$((TOTAL_CPUS - p_cpu_count))

    echo "CPU topology: ${P_CORE_COUNT} P-cores, ${E_CORE_COUNT} E-cores, ${TOTAL_CPUS} logical CPUs"
}

detect_temp_sensor() {
    TEMP_SENSOR=""
    for hwmon in /sys/class/hwmon/hwmon*/; do
        if grep -q "Package" "$hwmon"temp*_label 2>/dev/null; then
            local label_file=$(grep -l "Package" "$hwmon"temp*_label 2>/dev/null | head -1)
            TEMP_SENSOR="${label_file%_label}_input"
            break
        fi
    done
    if [ -z "$TEMP_SENSOR" ]; then
        echo "Warning: No CPU package temperature sensor found. Cooldown disabled."
    fi
}

check_tools() {
    local missing=""
    command -v taskset &>/dev/null || missing="$missing taskset"
    if [ -n "$missing" ]; then
        echo "Warning: Missing tools:$missing. CPU pinning disabled."
        PINNING_AVAILABLE=false
    else
        PINNING_AVAILABLE=true
    fi

    HAS_WRK=false
    command -v wrk &>/dev/null && HAS_WRK=true

    HAS_NATIVE=false
    local native="${SAPL_NATIVE:-sapl}"
    if command -v "$native" &>/dev/null || [ -x "$native" ]; then
        HAS_NATIVE=true
    fi
}

# ---------------------------------------------------------------------------
# Thermal management
# ---------------------------------------------------------------------------

pkg_temp() {
    if [ -n "${TEMP_SENSOR:-}" ] && [ -f "$TEMP_SENSOR" ]; then
        echo $(( $(cat "$TEMP_SENSOR") / 1000 ))
    else
        echo 0
    fi
}

wait_cool() {
    local target=${1:-$COOL_TARGET}
    if [ -z "${TEMP_SENSOR:-}" ]; then return; fi
    echo -n "  Cooling to ${target}C... "
    while [ "$(pkg_temp)" -gt "$target" ]; do sleep 1; done
    echo "$(pkg_temp)C"
}

# ---------------------------------------------------------------------------
# CPU pinning
# ---------------------------------------------------------------------------

# Returns CPU range string for N P-cores (each with HT = 2 logical CPUs)
server_cpus() {
    local n_pcores=$1
    echo "0-$((n_pcores * 2 - 1))"
}

# Returns remaining CPUs for client (everything server doesn't use)
client_cpus() {
    local n_pcores=$1
    if [ "$n_pcores" -lt "$P_CORE_COUNT" ]; then
        echo "$((n_pcores * 2))-$((TOTAL_CPUS - 1))"
    else
        # Server has all P-cores, client gets E-cores only
        echo "$((P_CORE_COUNT * 2))-$((TOTAL_CPUS - 1))"
    fi
}

pin_server() {
    local cpus=$1
    if $PINNING_AVAILABLE && [ -n "${SERVER_PID:-}" ]; then
        taskset -apc "$cpus" "$SERVER_PID" >/dev/null 2>&1
    fi
}

run_pinned() {
    local cpus=$1
    shift
    if $PINNING_AVAILABLE; then
        taskset -c "$cpus" "$@"
    else
        "$@"
    fi
}

# ---------------------------------------------------------------------------
# Server lifecycle
# ---------------------------------------------------------------------------

SERVER_PID=""

start_server() {
    local cmd=$1
    local policy_dir=$2
    local enable_rsocket=${3:-false}

    local rsocket_args=""
    if [ "$enable_rsocket" = true ]; then
        rsocket_args="--sapl.pdp.rsocket.enabled=true --sapl.pdp.rsocket.port=7000"
    fi

    $cmd server \
        --io.sapl.node.allow-no-auth=true \
        --io.sapl.pdp.embedded.policies-path="$policy_dir" \
        --io.sapl.pdp.embedded.config-path="$policy_dir" \
        $rsocket_args \
        >/dev/null 2>&1 &
    SERVER_PID=$!

    # Wait for health endpoint + RSocket port if enabled
    local max_wait=30
    for i in $(seq 1 $max_wait); do
        if curl -sf http://127.0.0.1:8443/actuator/health >/dev/null 2>&1; then
            if [ "$enable_rsocket" = true ]; then
                if ss -tln | grep -q ":7000 " 2>/dev/null; then
                    echo "  Server started (PID $SERVER_PID, HTTP + RSocket)"
                    return 0
                fi
                # RSocket not ready yet, keep waiting
                continue
            fi
            echo "  Server started (PID $SERVER_PID)"
            return 0
        fi
        sleep 1
    done
    echo "  ERROR: Server did not start within ${max_wait}s"
    return 1
}

stop_server() {
    if [ -n "${SERVER_PID:-}" ]; then
        kill "$SERVER_PID" 2>/dev/null
        wait "$SERVER_PID" 2>/dev/null || true
        SERVER_PID=""
    fi
}

trap_cleanup() {
    trap 'stop_server' EXIT
}

# ---------------------------------------------------------------------------
# Reporting
# ---------------------------------------------------------------------------

log_env() {
    echo "Environment:"
    echo "  CPU:    $(lscpu 2>/dev/null | grep -i 'model.*name\|Modellname' | sed 's/.*: *//')"
    echo "  Cores:  $P_CORE_COUNT P-cores + $E_CORE_COUNT E-cores ($TOTAL_CPUS logical)"
    echo "  JVM:    $(java -version 2>&1 | head -1)"
    echo "  OS:     $(uname -sr)"
    if $PINNING_AVAILABLE; then
        echo "  Pinning: available (taskset)"
    else
        echo "  Pinning: NOT available"
    fi
    if $HAS_WRK; then
        echo "  wrk:    $(wrk --version 2>&1 | head -1)"
    fi
    if $HAS_NATIVE; then
        echo "  Native: ${SAPL_NATIVE:-sapl}"
    fi
    echo ""
}

# ---------------------------------------------------------------------------
# Initialization (runs on source)
# ---------------------------------------------------------------------------

detect_cpu_topology
detect_temp_sensor
check_tools
