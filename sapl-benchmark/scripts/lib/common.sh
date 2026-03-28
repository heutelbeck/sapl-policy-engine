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

# No set -e. Server stop/kill can return non-zero harmlessly.

# ---------------------------------------------------------------------------
# Binary paths (override via environment variables)
# ---------------------------------------------------------------------------

SCRIPT_DIR="${SCRIPT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
SAPL_NODE_JAR="${SAPL_NODE_JAR:-$SCRIPT_DIR/../../sapl-node/target/sapl-node-4.0.0-SNAPSHOT.jar}"
SAPL_NATIVE="${SAPL_NATIVE:-$SCRIPT_DIR/../../sapl-node/target/sapl}"
SAPL4_BENCH_JAR="${SAPL4_BENCH_JAR:-$SCRIPT_DIR/../sapl-benchmark-sapl4/target/sapl-benchmark-sapl4-4.0.0-SNAPSHOT.jar}"
SAPL3_BENCH_JAR="${SAPL3_BENCH_JAR:-$SCRIPT_DIR/../sapl-benchmark-sapl3/target/sapl-benchmark-sapl3-4.0.0-SNAPSHOT.jar}"
OPA_BINARY="${OPA_BINARY:-opa}"
CONFIG_DIR="${CONFIG_DIR:-/tmp/sapl-benchmark-policies}"

COOL_TARGET=50

# ---------------------------------------------------------------------------
# Profile defaults
# ---------------------------------------------------------------------------

profile_defaults() {
    local profile=${1:-quick}
    case "$profile" in
        quick)
            WARMUP_ITERATIONS=1
            WARMUP_TIME=3
            MEASUREMENT_TIME=10
            CONVERGENCE_THRESHOLD=50
            CONVERGENCE_WINDOW=2
            MAX_FORKS=2
            WRK_WARMUP_TIME=3
            WRK_MEASURE_TIME=10
            WRK_CONVERGE=false
            CORE_SWEEP=(1 2 4 6 8)
            CONN_SWEEP=(32 64 128 256)
            THREAD_SWEEP=(1 2 4 8)
            RSOCKET_VT=256
            SCENARIOS=(rbac rbac-large simple-1 simple-100 simple-500 simple-1000 complex-1 complex-100 complex-1000)
            METHODS=(decideOnceBlocking)
            LATENCY=true
            ;;
        base)
            WARMUP_ITERATIONS=1
            WARMUP_TIME=3
            MEASUREMENT_TIME=30
            CONVERGENCE_THRESHOLD=2
            CONVERGENCE_WINDOW=3
            MAX_FORKS=5
            WRK_WARMUP_TIME=3
            WRK_MEASURE_TIME=30
            WRK_CONVERGE=true
            CORE_SWEEP=(1 2 4 6 8)
            CONN_SWEEP=(32 64 128 256)
            THREAD_SWEEP=(1 2 4 8)
            RSOCKET_VT=256
            SCENARIOS=(rbac rbac-large simple-1 simple-100 simple-500 simple-1000 complex-1 complex-100 complex-1000)
            METHODS=(decideOnceBlocking)
            LATENCY=true
            ;;
        rigorous)
            WARMUP_ITERATIONS=1
            WARMUP_TIME=3
            MEASUREMENT_TIME=30
            CONVERGENCE_THRESHOLD=2
            CONVERGENCE_WINDOW=3
            MAX_FORKS=50
            WRK_WARMUP_TIME=3
            WRK_MEASURE_TIME=30
            WRK_CONVERGE=true
            CORE_SWEEP=(1 2 4 6 8)
            CONN_SWEEP=(32 64 128 256)
            THREAD_SWEEP=(1 2 4 8)
            RSOCKET_VT=256
            SCENARIOS=(rbac rbac-large simple-1 simple-100 simple-500 simple-1000 complex-1 complex-100 complex-1000)
            METHODS=(decideOnceBlocking)
            LATENCY=true
            ;;
        *)
            echo "Unknown profile: $profile. Use quick, base, or rigorous."
            exit 1
            ;;
    esac
    echo "Profile: $profile"
}

# ---------------------------------------------------------------------------
# Environment detection
# ---------------------------------------------------------------------------

detect_cpu_topology() {
    TOTAL_CPUS=$(nproc)
    P_CORE_CPUS=""
    E_CORE_CPUS=""
    local max_freq_threshold=5000000

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

    if [ -z "$P_CORE_CPUS" ]; then
        P_CORE_CPUS="0-$((TOTAL_CPUS - 1))"
        E_CORE_CPUS=""
    fi

    local threads_per_core=$(lscpu 2>/dev/null | grep -i "thread.*per\|pro Kern" | awk '{print $NF}')
    threads_per_core=${threads_per_core:-1}
    local p_cpu_count=$(echo "$P_CORE_CPUS" | tr ',' '\n' | wc -l)
    P_CORE_COUNT=$((p_cpu_count / threads_per_core))
    E_CORE_COUNT=$((TOTAL_CPUS - p_cpu_count))

    echo "CPU: ${P_CORE_COUNT} P-cores, ${E_CORE_COUNT} E-cores, ${TOTAL_CPUS} logical"
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
        echo "Warning: No CPU package temperature sensor found."
    fi
}

check_tools() {
    PINNING_AVAILABLE=false
    command -v taskset &>/dev/null && PINNING_AVAILABLE=true

    HAS_WRK=false
    command -v wrk &>/dev/null && HAS_WRK=true

    HAS_NATIVE=false
    [ -x "${SAPL_NATIVE}" ] && HAS_NATIVE=true
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
    local temp=$(pkg_temp)
    if [ "$temp" -le "$target" ]; then return; fi
    echo -n "  Cooling to ${target}C (${temp}C)... "
    while [ "$(pkg_temp)" -gt "$target" ]; do sleep 1; done
    echo "$(pkg_temp)C"
}

# ---------------------------------------------------------------------------
# CPU pinning
# ---------------------------------------------------------------------------

server_cpus() {
    local n_pcores=$1
    echo "0-$((n_pcores * 2 - 1))"
}

client_cpus() {
    # Always E-cores only (validated: no throughput difference vs all remaining)
    echo "16-31"
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

start_sapl_server() {
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
        --logging.level.root=WARN \
        $rsocket_args \
        >/dev/null 2>&1 &
    SERVER_PID=$!

    local max_wait=30
    for i in $(seq 1 $max_wait); do
        if curl -sf http://127.0.0.1:8443/actuator/health >/dev/null 2>&1; then
            if [ "$enable_rsocket" = true ]; then
                if ss -tln | grep -q ":7000 " 2>/dev/null; then
                    echo "  Server started (PID $SERVER_PID, HTTP + RSocket)"
                    return 0
                fi
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

start_opa_server() {
    local policy_file=$1
    $OPA_BINARY run --server --addr :8181 "$policy_file" >/dev/null 2>&1 &
    SERVER_PID=$!

    local max_wait=10
    for i in $(seq 1 $max_wait); do
        if curl -sf http://127.0.0.1:8181/health >/dev/null 2>&1; then
            echo "  OPA started (PID $SERVER_PID)"
            return 0
        fi
        sleep 1
    done
    echo "  ERROR: OPA did not start within ${max_wait}s"
    return 1
}

stop_server() {
    if [ -n "${SERVER_PID:-}" ]; then
        kill "$SERVER_PID" 2>/dev/null
        wait "$SERVER_PID" 2>/dev/null || true
        SERVER_PID=""
    fi
    pkill -f "opa run" 2>/dev/null || true
    sleep 1
}

trap_cleanup() {
    trap 'stop_server' EXIT
}

# ---------------------------------------------------------------------------
# Scenario export
# ---------------------------------------------------------------------------

export_scenario() {
    local scenario=$1
    local target_dir=$2
    rm -rf "$target_dir"
    java -jar "$SAPL4_BENCH_JAR" --scenario="$scenario" --export="$target_dir"
}

# ---------------------------------------------------------------------------
# wrk helpers
# ---------------------------------------------------------------------------

converge_wrk() {
    local connections=$1
    local url=$2
    local lua_script=$3
    local sub_file=$4
    local client_cpu=$(client_cpus)
    local samples=()

    for i in $(seq 1 $MAX_WARMUP_ITERS); do
        local rps=$(SUBSCRIPTION_FILE="$sub_file" run_pinned "$client_cpu" wrk -t2 -c"$connections" -d${WRK_WARMUP_TIME}s -s "$lua_script" "$url" 2>&1 | grep "Requests/sec" | awk '{printf "%.0f", $2}')
        samples+=("$rps")
        local n=${#samples[@]}
        if [ "$n" -ge 3 ]; then
            local converged=true
            for j in 1 2; do
                local prev=${samples[$((n - 1 - j))]}
                if [ "$prev" -gt 0 ] 2>/dev/null; then
                    local delta=$(( (rps > prev ? rps - prev : prev - rps) * 100 / prev ))
                    if [ "$delta" -gt 5 ]; then converged=false; break; fi
                fi
            done
            if $converged; then
                echo "    Warmup converged after $i iterations ($rps req/s)"
                return 0
            fi
        fi
    done
    echo "    Warmup: used $MAX_WARMUP_ITERS iterations (last: $rps req/s)"
}

MAX_WARMUP_ITERS=15

parse_wrk_rps() {
    echo "$1" | grep "Requests/sec" | awk '{printf "%.2f", $2}'
}

wrk_latency_to_ns() {
    local val="$1"
    python3 -c "
v = '$val'
if v.endswith('us'): print(int(float(v[:-2]) * 1000))
elif v.endswith('ms'): print(int(float(v[:-2]) * 1000000))
elif v.endswith('s'): print(int(float(v[:-1]) * 1000000000))
else: print(0)
"
}

parse_wrk_latency() {
    local output="$1"
    local p50=$(echo "$output" | awk '/Latency Distribution/{found=1} found && /50%/{print $2; exit}')
    local p90=$(echo "$output" | awk '/Latency Distribution/{found=1} found && /90%/{print $2; exit}')
    local p99=$(echo "$output" | awk '/Latency Distribution/{found=1} found && /99%/{print $2; exit}')
    local p50_ns=$(wrk_latency_to_ns "$p50")
    local p90_ns=$(wrk_latency_to_ns "$p90")
    local p99_ns=$(wrk_latency_to_ns "$p99")
    echo "$p50_ns:$p90_ns:$p99_ns"
}

# ---------------------------------------------------------------------------
# Reporting
# ---------------------------------------------------------------------------

log_env() {
    echo "Environment:"
    echo "  CPU:     $(lscpu 2>/dev/null | grep -i 'model.*name\|Modellname' | sed 's/.*: *//')"
    echo "  Cores:   $P_CORE_COUNT P-cores + $E_CORE_COUNT E-cores ($TOTAL_CPUS logical)"
    echo "  JVM:     $(java -version 2>&1 | head -1)"
    echo "  OS:      $(uname -sr)"
    echo "  Pinning: $($PINNING_AVAILABLE && echo "available" || echo "NOT available")"
    echo "  wrk:     $($HAS_WRK && wrk --version 2>&1 | head -1 || echo "not found")"
    echo "  Native:  $($HAS_NATIVE && echo "$SAPL_NATIVE" || echo "not found")"
    echo "  Temp:    $(pkg_temp)C"
    echo ""
}

timestamp() {
    date +%Y%m%d-%H%M%S
}

# ---------------------------------------------------------------------------
# Initialization (runs on source)
# ---------------------------------------------------------------------------

detect_cpu_topology
detect_temp_sensor
check_tools
