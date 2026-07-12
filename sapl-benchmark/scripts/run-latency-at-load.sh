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

# Latency-at-load benchmark: rate-limited RSocket sweep per scenario.
# Measures per-request service time at controlled load fractions.
# Runs JVM server, and native server if available.
#
# Usage: run-latency-at-load.sh [quick|full] [output-dir]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

QUALITY=${1:-quick}
OUTPUT_DIR=${2:-$SCRIPT_DIR/../results}

load_quality "$QUALITY"
load_experiment "latency-at-load"
log_env

if [ "$QUALITY" = "full" ]; then
    SCENARIOS=("${SCENARIOS_FULL[@]}")
    LOAD_PCTS=("${LOAD_PCTS_FULL[@]}")
else
    SCENARIOS=("${SCENARIOS_QUICK[@]}")
    LOAD_PCTS=("${LOAD_PCTS_QUICK[@]}")
fi

SCENARIO_DIR="/tmp/sapl-benchmark-scenarios"
rm -rf "$SCENARIO_DIR"
mkdir -p "$SCENARIO_DIR"

# HTTP latency-at-load arm (wrk2). loadtest's HTTP client cannot saturate a fast
# server, so HTTP latency is driven by wrk2 at controlled rates instead.
LUA_SCRIPT="$SCRIPT_DIR/lib/sapl-wrk.lua"
HTTP_URL="http://127.0.0.1:8080/api/pdp/decide-once"
HTTP_LAT_CONNECTIONS="${HTTP_LAT_CONNECTIONS:-256}"

# Low-pause collector for the latency-experiment JVM server. Measured: default G1
# and large-heap G1 both spike the tail with ~1s full-GC pauses; ZGC keeps pauses
# off the latency tail. Override via LATENCY_SERVER_GC. JVM server only (the native
# image uses its own collector).
LATENCY_SERVER_GC="${LATENCY_SERVER_GC:--XX:+UseZGC -Xmx32g}"
RSOCKET_SATURATION_WARMUP_SECONDS="${RSOCKET_SATURATION_WARMUP_SECONDS:-10}"
RSOCKET_SATURATION_MEASUREMENT_SECONDS="${RSOCKET_SATURATION_MEASUREMENT_SECONDS:-5}"

trap_cleanup

echo "Exporting scenarios..."
for scenario in "${SCENARIOS[@]}"; do
    java -jar "$SAPL4_BENCH_JAR" --scenario="$scenario" --indexing=SMTDD --export="$SCENARIO_DIR/$scenario" 2>/dev/null
    echo "  $scenario -> $SCENARIO_DIR/$scenario"
done
echo ""

if [ "${#LATENCY_AT_LOAD_RUNTIMES[@]}" -gt 0 ]; then
    RUNTIMES=("${LATENCY_AT_LOAD_RUNTIMES[@]}")
else
    RUNTIMES=(jvm)
    $HAS_NATIVE && RUNTIMES+=(native)
fi

# x2: each scenario runs both an RSocket arm and an HTTP arm, each (LOAD_PCTS + 1) steps.
TOTAL_STEPS=$(( ${#RUNTIMES[@]} * ${#SERVER_PCORES_SWEEP[@]} * ${#SCENARIOS[@]} * 2 * (${#LOAD_PCTS[@]} + 1) ))
CURRENT_STEP=0

run_rate_sweep() {
    local scenario=$1
    local runtime=$2
    local outdir=$3
    local sub_file="$SCENARIO_DIR/$scenario/subscription.json"
    local client_cpu=$(client_cpus)

    # Saturation run first
    CURRENT_STEP=$((CURRENT_STEP + 1))
    local pct=$((CURRENT_STEP * 100 / TOTAL_STEPS))
    echo "  [$CURRENT_STEP/$TOTAL_STEPS] ($pct%) $scenario / $runtime / saturation"
    wait_cool

    local sat_result
    sat_result=$(run_pinned "$client_cpu" java -jar "$SAPL4_BENCH_JAR" \
        --scenario="$scenario" --method=decideOnceBlocking --indexing=SMTDD \
        -t 1 --warmup-iterations=1 --warmup-time=3 \
        --measurement-time="$MEASUREMENT_TIME" --max-forks=1 \
        --convergence-threshold=100 --convergence-window=1 \
        --latency=false --heap=32g 2>&1 | grep "^THROUGHPUT:" | head -1)

    # Run loadtest at each rate step
    for rate in "${RATE_STEPS[@]}"; do
        CURRENT_STEP=$((CURRENT_STEP + 1))
        pct=$((CURRENT_STEP * 100 / TOTAL_STEPS))
        echo "  [$CURRENT_STEP/$TOTAL_STEPS] ($pct%) $scenario / $runtime / $rate req/s"
        wait_cool

        run_pinned "$client_cpu" java -jar "$SAPL_NODE_JAR" loadtest \
            --rsocket --host localhost --port 7000 \
            --connections "$RSOCKET_CONNECTIONS" \
            --vt-per-connection "$RSOCKET_CONCURRENCY" \
            --rate "$rate" \
            --warmup-seconds 5 \
            --measurement-seconds "$MEASUREMENT_TIME" \
            --machine-readable \
            -f "$sub_file" \
            -o "$outdir" \
            --label "${scenario}_${runtime}_${rate}" 2>/dev/null

        echo ""
    done
}

for runtime in "${RUNTIMES[@]}"; do
    RUN_TIMESTAMP=$(timestamp)
    OUTDIR="$OUTPUT_DIR/latency-at-load-${runtime}-${QUALITY}-${RUN_TIMESTAMP}"
    mkdir -p "$OUTDIR"

    if [ "$runtime" = "jvm" ]; then
        SERVER_CMD="java -jar $SAPL_NODE_JAR"
    else
        SERVER_CMD="$SAPL_NATIVE"
    fi

    echo "================================================================"
    echo "  Latency-at-Load Benchmark ($runtime)"
    echo "  Profile:    $QUALITY"
    echo "  Scenarios:  ${SCENARIOS[*]}"
    echo "  Load pcts:  ${LOAD_PCTS[*]}% (of measured saturation)"
    echo "  Transport:  RSocket ($RSOCKET_CONNECTIONS conns x $RSOCKET_CONCURRENCY concurrent) + HTTP (wrk2 -t$WRK_THREADS, $HTTP_LAT_CONNECTIONS conns)"
    echo "  Server GC:  ${LATENCY_SERVER_GC:-<jvm default>} (JVM server; native uses its own)"
    echo "  Server:     ${SERVER_PCORES_SWEEP[*]} P-cores"
    echo "  Measure:    ${MEASUREMENT_TIME}s per step"
    echo "  Total:      $TOTAL_STEPS runs across all runtimes"
    echo "  Output:     $OUTDIR"
    echo "================================================================"
    echo ""

    for pcores in "${SERVER_PCORES_SWEEP[@]}"; do
    cpu_range=$(server_cpus "$pcores")

    for scenario in "${SCENARIOS[@]}"; do
        stop_server
        wait_cool

        echo "Starting $runtime server: $scenario on CPUs $cpu_range (${pcores} P-cores)"
        if [ "$runtime" = "jvm" ]; then
            taskset -c "$cpu_range" java $LATENCY_SERVER_GC \
                -XX:ActiveProcessorCount=$((pcores * 2)) \
                -jar "$SAPL_NODE_JAR" server \
                --io.sapl.node.allow-no-auth=true \
                --io.sapl.pdp.embedded.metrics-enabled=false \
                --io.sapl.pdp.embedded.policies-path="$SCENARIO_DIR/$scenario" \
                --io.sapl.pdp.embedded.config-path="$SCENARIO_DIR/$scenario" \
                --sapl.pdp.rsocket.enabled=true \
                --sapl.pdp.rsocket.port=7000 \
                --logging.level.root=WARN \
                >/dev/null 2>&1 &
        else
            taskset -c "$cpu_range" "$SAPL_NATIVE" server \
                --io.sapl.node.allow-no-auth=true \
                --io.sapl.pdp.embedded.metrics-enabled=false \
                --io.sapl.pdp.embedded.policies-path="$SCENARIO_DIR/$scenario" \
                --io.sapl.pdp.embedded.config-path="$SCENARIO_DIR/$scenario" \
                --sapl.pdp.rsocket.enabled=true \
                --sapl.pdp.rsocket.port=7000 \
                --logging.level.root=WARN \
                >/dev/null 2>&1 &
        fi
        SERVER_PID=$!

        # Wait for both HTTP health and RSocket port
        max_wait=60
        started=false
        for i in $(seq 1 $max_wait); do
            if curl -sf http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
                if ss -tln | grep -q ":7000 " 2>/dev/null; then
                    echo "  Server started (PID $SERVER_PID, HTTP + RSocket)"
                    started=true
                    break
                fi
            fi
            sleep 1
        done

        if ! $started; then
            echo "  ERROR: Server did not start within ${max_wait}s, skipping $scenario"
            stop_server
            continue
        fi

        # Warmup + measure saturation throughput
        echo "  Warming up and measuring saturation..."
        client_cpu=$(client_cpus)
        warmup_output=$(run_pinned "$client_cpu" java -jar "$SAPL_NODE_JAR" loadtest \
            --rsocket --host localhost --port 7000 \
            --connections "$RSOCKET_CONNECTIONS" \
            --vt-per-connection "$RSOCKET_CONCURRENCY" \
            --warmup-seconds "$RSOCKET_SATURATION_WARMUP_SECONDS" \
            --measurement-seconds "$RSOCKET_SATURATION_MEASUREMENT_SECONDS" \
            --machine-readable \
            -f "$SCENARIO_DIR/$scenario/subscription.json" 2>/dev/null)
        peak_throughput=$(echo "$warmup_output" | grep "^THROUGHPUT:" | cut -d: -f2 | cut -d. -f1)
        peak_throughput=${peak_throughput:-1000000}
        echo "  Saturation: $peak_throughput req/s"
        echo "${scenario},${runtime},${pcores},${peak_throughput}" >> "$OUTDIR/saturation.csv"

        # Rate sweep at percentages of measured peak
        for load_pct in "${LOAD_PCTS[@]}"; do
            rate=$((peak_throughput * load_pct / 100))
            CURRENT_STEP=$((CURRENT_STEP + 1))
            pct=$((CURRENT_STEP * 100 / TOTAL_STEPS))
            echo "  [$CURRENT_STEP/$TOTAL_STEPS] ($pct%) $scenario / $runtime / ${pcores}P / ${load_pct}% = $rate req/s"
            wait_cool

            prefix="${scenario}_${runtime}_${pcores}p_${load_pct}pct"

            run_pinned "$client_cpu" java -jar "$SAPL_NODE_JAR" loadtest \
                --rsocket --host localhost --port 7000 \
                --connections "$RSOCKET_CONNECTIONS" \
                --vt-per-connection "$RSOCKET_CONCURRENCY" \
                --rate "$rate" \
                --warmup-seconds 0 \
                --measurement-seconds "$MEASUREMENT_TIME" \
                -f "$SCENARIO_DIR/$scenario/subscription.json" \
                -o "$OUTDIR" \
                --label "$prefix"

            echo ""
        done

        # Saturation reference
        CURRENT_STEP=$((CURRENT_STEP + 1))
        pct=$((CURRENT_STEP * 100 / TOTAL_STEPS))
        echo "  [$CURRENT_STEP/$TOTAL_STEPS] ($pct%) $scenario / $runtime / ${pcores}P / saturation"
        wait_cool

        prefix="${scenario}_${runtime}_${pcores}p_saturation"
        run_pinned "$client_cpu" java -jar "$SAPL_NODE_JAR" loadtest \
            --rsocket --host localhost --port 7000 \
            --connections "$RSOCKET_CONNECTIONS" \
            --vt-per-connection "$RSOCKET_CONCURRENCY" \
            --warmup-seconds 0 \
            --measurement-seconds "$MEASUREMENT_TIME" \
            -f "$SCENARIO_DIR/$scenario/subscription.json" \
            -o "$OUTDIR" \
            --label "$prefix"

        echo ""

        # ------------------------------------------------------------------
        # HTTP arm: same running server, port 8080, driven by wrk2 (loadtest's
        # HTTP client cannot saturate a fast server). Rates are held below the
        # measured HTTP saturation so the percentiles are meaningful.
        # ------------------------------------------------------------------
        http_sub="$SCENARIO_DIR/$scenario/subscription.json"
        # Warm the HTTP request path (JIT + GC) before any measured step. The
        # RSocket arm above warms shared code, but the servlet/JSON path is
        # distinct; without this the first rate step pays the warmup cost as tail.
        if $WRK_CONVERGE; then
            echo "  HTTP: warmup (convergence-based)..."
            converge_wrk "$HTTP_LAT_CONNECTIONS" "$HTTP_URL" "$LUA_SCRIPT" "$http_sub"
        else
            echo "  HTTP: warmup skipped"
        fi
        echo "  HTTP: measuring saturation (wrk2 -t$WRK_THREADS)..."
        wait_cool
        http_sat_out=$(SUBSCRIPTION_FILE="$http_sub" run_pinned "$client_cpu" \
            wrk2 -t"$WRK_THREADS" -c"$HTTP_LAT_CONNECTIONS" -d"${MEASUREMENT_TIME}"s -R 10000000 \
            -s "$LUA_SCRIPT" "$HTTP_URL" 2>&1)
        http_peak=$(parse_wrk_rps "$http_sat_out" | cut -d. -f1)
        http_peak=${http_peak:-1}
        echo "  HTTP saturation: $http_peak req/s"

        for load_pct in "${LOAD_PCTS[@]}"; do
            rate=$((http_peak * load_pct / 100))
            [ "$rate" -lt 1 ] && rate=1
            CURRENT_STEP=$((CURRENT_STEP + 1))
            pct=$((CURRENT_STEP * 100 / TOTAL_STEPS))
            echo "  [$CURRENT_STEP/$TOTAL_STEPS] ($pct%) $scenario / $runtime / ${pcores}P / HTTP / ${load_pct}% = $rate req/s"
            wait_cool

            http_out=$(SUBSCRIPTION_FILE="$http_sub" run_pinned "$client_cpu" \
                wrk2 -t"$WRK_THREADS" -c"$HTTP_LAT_CONNECTIONS" -d"${MEASUREMENT_TIME}"s -R "$rate" --latency \
                -s "$LUA_SCRIPT" "$HTTP_URL" 2>&1)
            http_rps=$(parse_wrk_rps "$http_out")
            IFS=: read -r l50 l90 l99 l999 lmax <<< "$(parse_wrk_latency5 "$http_out")"
            write_loadtest_csv "$OUTDIR/${scenario}_${runtime}_${pcores}p_${load_pct}pct_http.csv" \
                "${scenario}_${runtime}_${pcores}p_${load_pct}pct_http" "HTTP" \
                "$WRK_THREADS" "$HTTP_LAT_CONNECTIONS" "$MEASUREMENT_TIME" \
                "${http_rps:-0}" "$l50" "$l90" "$l99" "$l999" "$lmax"
            echo ""
        done

        # HTTP saturation reference
        CURRENT_STEP=$((CURRENT_STEP + 1))
        pct=$((CURRENT_STEP * 100 / TOTAL_STEPS))
        echo "  [$CURRENT_STEP/$TOTAL_STEPS] ($pct%) $scenario / $runtime / ${pcores}P / HTTP / saturation"
        wait_cool
        http_out=$(SUBSCRIPTION_FILE="$http_sub" run_pinned "$client_cpu" \
            wrk2 -t"$WRK_THREADS" -c"$HTTP_LAT_CONNECTIONS" -d"${MEASUREMENT_TIME}"s -R 10000000 --latency \
            -s "$LUA_SCRIPT" "$HTTP_URL" 2>&1)
        http_rps=$(parse_wrk_rps "$http_out")
        IFS=: read -r l50 l90 l99 l999 lmax <<< "$(parse_wrk_latency5 "$http_out")"
        write_loadtest_csv "$OUTDIR/${scenario}_${runtime}_${pcores}p_saturation_http.csv" \
            "${scenario}_${runtime}_${pcores}p_saturation_http" "HTTP" \
            "$WRK_THREADS" "$HTTP_LAT_CONNECTIONS" "$MEASUREMENT_TIME" \
            "${http_rps:-0}" "$l50" "$l90" "$l99" "$l999" "$lmax"
        echo ""

        stop_server
    done
    done

    echo "================================================================"
    echo "  Latency-at-Load Complete ($runtime)"
    echo "  Results: $OUTDIR"
    echo "================================================================"
    echo ""
done

echo "================================================================"
echo "  All Latency-at-Load Benchmarks Complete"
echo "  Results: $OUTPUT_DIR"
echo "================================================================"
