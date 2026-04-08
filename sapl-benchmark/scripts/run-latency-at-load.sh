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
    RATE_STEPS=("${RATE_STEPS_FULL[@]}")
else
    SCENARIOS=("${SCENARIOS_QUICK[@]}")
    RATE_STEPS=("${RATE_STEPS_QUICK[@]}")
fi

SCENARIO_DIR="/tmp/sapl-benchmark-scenarios"
rm -rf "$SCENARIO_DIR"
mkdir -p "$SCENARIO_DIR"

trap_cleanup

echo "Exporting scenarios..."
for scenario in "${SCENARIOS[@]}"; do
    java -jar "$SAPL4_BENCH_JAR" --scenario="$scenario" --indexing=SMTDD --export="$SCENARIO_DIR/$scenario" 2>/dev/null
    echo "  $scenario -> $SCENARIO_DIR/$scenario"
done
echo ""

RUNTIMES=(jvm)
$HAS_NATIVE && RUNTIMES+=(native)

TOTAL_STEPS=$(( ${#RUNTIMES[@]} * ${#SCENARIOS[@]} * (${#RATE_STEPS[@]} + 1) ))
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
    echo "  Rate steps: ${RATE_STEPS[*]}"
    echo "  Transport:  RSocket ($RSOCKET_CONNECTIONS conns x $RSOCKET_CONCURRENCY concurrent)"
    echo "  Server:     $SERVER_PCORES P-cores"
    echo "  Measure:    ${MEASUREMENT_TIME}s per step"
    echo "  Total:      $TOTAL_STEPS runs across all runtimes"
    echo "  Output:     $OUTDIR"
    echo "================================================================"
    echo ""

    local_cpu_range=$(server_cpus "$SERVER_PCORES")

    for scenario in "${SCENARIOS[@]}"; do
        stop_server
        wait_cool

        echo "Starting $runtime server: $scenario on CPUs $local_cpu_range"
        if [ "$runtime" = "jvm" ]; then
            taskset -c "$local_cpu_range" java \
                -XX:ActiveProcessorCount=$((SERVER_PCORES * 2)) \
                -jar "$SAPL_NODE_JAR" server \
                --io.sapl.node.allow-no-auth=true \
                --io.sapl.pdp.embedded.policies-path="$SCENARIO_DIR/$scenario" \
                --io.sapl.pdp.embedded.config-path="$SCENARIO_DIR/$scenario" \
                --sapl.pdp.rsocket.enabled=true \
                --sapl.pdp.rsocket.port=7000 \
                --logging.level.root=WARN \
                >/dev/null 2>&1 &
        else
            taskset -c "$local_cpu_range" "$SAPL_NATIVE" server \
                --io.sapl.node.allow-no-auth=true \
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
            if curl -sf http://127.0.0.1:8443/actuator/health >/dev/null 2>&1; then
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

        # Warmup the server with a saturation burst
        echo "  Warming up server..."
        client_cpu=$(client_cpus)
        run_pinned "$client_cpu" java -jar "$SAPL_NODE_JAR" loadtest \
            --rsocket --host localhost --port 7000 \
            --connections "$RSOCKET_CONNECTIONS" \
            --vt-per-connection "$RSOCKET_CONCURRENCY" \
            --warmup-seconds 10 --measurement-seconds 3 \
            --machine-readable \
            -f "$SCENARIO_DIR/$scenario/subscription.json" 2>/dev/null

        # Rate sweep
        for rate in "${RATE_STEPS[@]}"; do
            CURRENT_STEP=$((CURRENT_STEP + 1))
            pct=$((CURRENT_STEP * 100 / TOTAL_STEPS))
            echo "  [$CURRENT_STEP/$TOTAL_STEPS] ($pct%) $scenario / $runtime / $rate req/s"
            wait_cool

            prefix="${scenario}_${runtime}_${rate}r"

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
        echo "  [$CURRENT_STEP/$TOTAL_STEPS] ($pct%) $scenario / $runtime / saturation"
        wait_cool

        prefix="${scenario}_${runtime}_saturation"
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
        stop_server
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
