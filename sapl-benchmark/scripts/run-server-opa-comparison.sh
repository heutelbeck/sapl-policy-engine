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

# OPA vs SAPL HTTP comparison. Same RBAC policy, same hardware, same wrk settings.
# Sweeps: OPA, SAPL JVM, SAPL native (if available) x cores x connections.
#
# Requires OPA binary. Use the nix flake in scripts/opa/:
#   nix develop ./scripts/opa --command ./scripts/run-server-opa-comparison.sh quick /path/to/results
#
# Usage: run-server-opa-comparison.sh [quick|full] [output-dir]

export LC_NUMERIC=C

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

QUALITY=${1:-quick}
OUTPUT_DIR=${2:-$SCRIPT_DIR/../results}

load_quality "$QUALITY"
load_experiment "server-http"
log_env

if ! $HAS_WRK; then
    echo "ERROR: wrk not found."
    exit 1
fi

if ! command -v "$OPA_BINARY" &>/dev/null; then
    echo "ERROR: OPA binary not found: $OPA_BINARY"
    echo "Run inside OPA nix shell: nix develop ./scripts/opa --command ./scripts/run-server-opa-comparison.sh"
    exit 1
fi

OPA_POLICY="$SCRIPT_DIR/opa/rbac.rego"
OPA_SUB="$SCRIPT_DIR/opa/subscription.json"
OPA_LUA="$SCRIPT_DIR/lib/opa-wrk.lua"
OPA_URL="http://127.0.0.1:8181/v1/data/rbac/allow"

SAPL_LUA="$SCRIPT_DIR/lib/sapl-wrk.lua"
SAPL_URL="http://127.0.0.1:8443/api/pdp/decide-once"
SCENARIO_DIR="/tmp/sapl-benchmark-scenarios"

trap_cleanup

RUN_TIMESTAMP=$(timestamp)
OUTDIR="$OUTPUT_DIR/server-opa-comparison-${QUALITY}-${RUN_TIMESTAMP}"
mkdir -p "$OUTDIR"

# Export SAPL rbac-opa scenario
rm -rf "$SCENARIO_DIR/rbac-opa"
java -jar "$SAPL4_BENCH_JAR" --scenario=rbac-opa --export="$SCENARIO_DIR/rbac-opa" 2>/dev/null
SAPL_SUB="$SCENARIO_DIR/rbac-opa/subscription.json"

# Detect SAPL runtimes
SAPL_RUNTIMES=()
[ -f "$SAPL_NODE_JAR" ] && SAPL_RUNTIMES+=(jvm)
[ -x "$SAPL_NATIVE" ] && SAPL_RUNTIMES+=(native)

ENGINE_COUNT=$((1 + ${#SAPL_RUNTIMES[@]}))
TOTAL_STEPS=$(( ENGINE_COUNT * ${#CORE_SWEEP[@]} * ${#CONN_SWEEP[@]} ))
CURRENT_STEP=0

echo "================================================================"
echo "  OPA vs SAPL HTTP Comparison"
echo "  Quality:     $QUALITY"
echo "  Engines:     OPA ${SAPL_RUNTIMES[*]:+SAPL-}$(IFS=,; echo "${SAPL_RUNTIMES[*]}")"
echo "  Cores:       ${CORE_SWEEP[*]}"
echo "  Connections: ${CONN_SWEEP[*]}"
echo "  Measure:     ${WRK_MEASURE_TIME}s"
echo "  Converg:     CoV < ${CONVERGENCE_THRESHOLD}% over ${CONVERGENCE_WINDOW} forks (max ${MAX_FORKS})"
echo "  Total:       $TOTAL_STEPS runs"
echo "  Output:      $OUTDIR"
echo "================================================================"
echo ""

run_wrk_sweep() {
    local engine=$1
    local url=$2
    local lua=$3
    local sub_file=$4

    for pcores in "${CORE_SWEEP[@]}"; do
        for connections in "${CONN_SWEEP[@]}"; do
            CURRENT_STEP=$((CURRENT_STEP + 1))
            pct=$((CURRENT_STEP * 100 / TOTAL_STEPS))
            local prefix="${engine}_rbac-opa_${pcores}p_${connections}c"
            local client_cpu=$(client_cpus)

            echo "================================================================"
            echo "  Step $CURRENT_STEP of $TOTAL_STEPS ($pct%)"
            echo "  $engine / rbac-opa / ${pcores}P / ${connections}c"
            echo "================================================================"

            local throughputs=()
            local last_latency=""

            for fork_index in $(seq 1 "$MAX_FORKS"); do
                wait_cool

                local wrk_output
                wrk_output=$(SUBSCRIPTION_FILE="$sub_file" run_pinned "$client_cpu" wrk -t2 -c"$connections" -d${WRK_MEASURE_TIME}s --latency -s "$lua" "$url" 2>&1)

                local throughput
                throughput=$(parse_wrk_rps "$wrk_output")

                if [ -z "$throughput" ] || [ "$throughput" = "0.00" ]; then
                    echo "    Fork $fork_index: FAILED"
                    continue
                fi

                throughputs+=("$throughput")

                local latency_str
                latency_str=$(parse_wrk_latency "$wrk_output")
                if [ -n "$latency_str" ] && [ "$latency_str" != "::" ]; then
                    last_latency="$latency_str"
                fi

                python3 "$BENCH_PY" write-fork-json \
                    --output "$OUTDIR/${prefix}_fork${fork_index}.json" \
                    --score "$throughput" --unit "req/s" \
                    --benchmark "${engine}-http" --mode thrpt --transport http \
                    --cores "$pcores" --connections "$connections" \
                    --measurement-time "${WRK_MEASURE_TIME} s" \
                    --scenario "rbac-opa"

                local n=${#throughputs[@]}
                local cov="N/A"
                if [ "$n" -ge 2 ]; then
                    cov=$(compute_cov "${throughputs[@]}")
                fi
                printf "    Fork %d: %'.0f req/s (CoV: %s%%)\n" "$fork_index" "${throughput%.*}" "$cov"

                local converged
                converged=$(check_convergence "$CONVERGENCE_WINDOW" "${throughputs[@]}")
                if [ "$converged" = "true" ]; then
                    break
                fi
            done

            if [ ${#throughputs[@]} -eq 0 ]; then
                echo "    FAILED: no successful forks"
                continue
            fi

            python3 "$BENCH_PY" write-csv \
                --output "$OUTDIR/${prefix}.csv" \
                --throughputs "$(IFS=,; echo "${throughputs[*]}")" \
                --title "$engine HTTP Benchmark (rbac-opa)" \
                --unit throughput_req_s \
                --scenario "rbac-opa" --cores "$pcores" --connections "$connections" \
                --runtime "$engine" \
                --measurement "${WRK_MEASURE_TIME}s" \
                --convergence-threshold "$CONVERGENCE_THRESHOLD" \
                --convergence-window "$CONVERGENCE_WINDOW" \
                ${last_latency:+--latency "$last_latency"}

            echo ""
        done
    done
}

# Phase 1: OPA
echo "================================================================"
echo "  Phase 1: OPA"
echo "================================================================"
echo ""

for pcores in "${CORE_SWEEP[@]}"; do
    cpu_range=$(server_cpus "$pcores")
    stop_server
    wait_cool

    echo "  Starting OPA on CPUs $cpu_range (${pcores} P-cores)"
    taskset -c "$cpu_range" "$OPA_BINARY" run --server --addr :8181 "$OPA_POLICY" >/dev/null 2>&1 &
    SERVER_PID=$!

    started=false
    for i in $(seq 1 10); do
        if curl -sf http://127.0.0.1:8181/health >/dev/null 2>&1; then
            echo "  OPA started (PID $SERVER_PID)"
            started=true
            break
        fi
        sleep 1
    done

    if ! $started; then
        echo "  ERROR: OPA did not start"
        stop_server
        continue
    fi

    if $WRK_CONVERGE; then
        converge_wrk "${CONN_SWEEP[0]}" "$OPA_URL" "$OPA_LUA" "$OPA_SUB"
    fi

    for connections in "${CONN_SWEEP[@]}"; do
        CURRENT_STEP=$((CURRENT_STEP + 1))
        pct=$((CURRENT_STEP * 100 / TOTAL_STEPS))
        prefix="opa_rbac-opa_${pcores}p_${connections}c"
        client_cpu=$(client_cpus)

        echo "================================================================"
        echo "  Step $CURRENT_STEP of $TOTAL_STEPS ($pct%)"
        echo "  opa / rbac-opa / ${pcores}P / ${connections}c"
        echo "================================================================"

        throughputs=()
        last_latency=""

        for fork_index in $(seq 1 "$MAX_FORKS"); do
            wait_cool

            wrk_output=$(SUBSCRIPTION_FILE="$OPA_SUB" run_pinned "$client_cpu" wrk -t2 -c"$connections" -d${WRK_MEASURE_TIME}s --latency -s "$OPA_LUA" "$OPA_URL" 2>&1)
            throughput=$(parse_wrk_rps "$wrk_output")

            if [ -z "$throughput" ] || [ "$throughput" = "0.00" ]; then
                echo "    Fork $fork_index: FAILED"
                continue
            fi

            throughputs+=("$throughput")
            latency_str=$(parse_wrk_latency "$wrk_output")
            if [ -n "$latency_str" ] && [ "$latency_str" != "::" ]; then
                last_latency="$latency_str"
            fi

            python3 "$BENCH_PY" write-fork-json \
                --output "$OUTDIR/${prefix}_fork${fork_index}.json" \
                --score "$throughput" --unit "req/s" \
                --benchmark opa-http --mode thrpt --transport http \
                --cores "$pcores" --connections "$connections" \
                --measurement-time "${WRK_MEASURE_TIME} s" \
                --scenario "rbac-opa"

            n=${#throughputs[@]}
            cov="N/A"
            if [ "$n" -ge 2 ]; then
                cov=$(compute_cov "${throughputs[@]}")
            fi
            printf "    Fork %d: %'.0f req/s (CoV: %s%%)\n" "$fork_index" "${throughput%.*}" "$cov"

            converged=$(check_convergence "$CONVERGENCE_WINDOW" "${throughputs[@]}")
            if [ "$converged" = "true" ]; then break; fi
        done

        if [ ${#throughputs[@]} -gt 0 ]; then
            python3 "$BENCH_PY" write-csv \
                --output "$OUTDIR/${prefix}.csv" \
                --throughputs "$(IFS=,; echo "${throughputs[*]}")" \
                --title "OPA HTTP Benchmark (rbac-opa)" \
                --unit throughput_req_s \
                --scenario "rbac-opa" --cores "$pcores" --connections "$connections" \
                --runtime opa \
                --measurement "${WRK_MEASURE_TIME}s" \
                --convergence-threshold "$CONVERGENCE_THRESHOLD" \
                --convergence-window "$CONVERGENCE_WINDOW" \
                ${last_latency:+--latency "$last_latency"}
        fi
        echo ""
    done

    stop_server
done

# Phase 2: SAPL runtimes
for runtime in "${SAPL_RUNTIMES[@]}"; do
    echo "================================================================"
    echo "  Phase: SAPL $runtime"
    echo "================================================================"
    echo ""

    for pcores in "${CORE_SWEEP[@]}"; do
        cpu_range=$(server_cpus "$pcores")
        stop_server
        wait_cool

        echo "  Starting SAPL $runtime server on CPUs $cpu_range (${pcores} P-cores)"
        if [ "$runtime" = "jvm" ]; then
            taskset -c "$cpu_range" java -XX:ActiveProcessorCount=$((pcores * 2)) -jar "$SAPL_NODE_JAR" server \
                --io.sapl.node.allow-no-auth=true \
                --io.sapl.pdp.embedded.policies-path="$SCENARIO_DIR/rbac-opa" \
                --io.sapl.pdp.embedded.config-path="$SCENARIO_DIR/rbac-opa" \
                --logging.level.root=WARN \
                >/dev/null 2>&1 &
        else
            taskset -c "$cpu_range" "$SAPL_NATIVE" server \
                --io.sapl.node.allow-no-auth=true \
                --io.sapl.pdp.embedded.policies-path="$SCENARIO_DIR/rbac-opa" \
                --io.sapl.pdp.embedded.config-path="$SCENARIO_DIR/rbac-opa" \
                --logging.level.root=WARN \
                >/dev/null 2>&1 &
        fi
        SERVER_PID=$!

        started=false
        for i in $(seq 1 30); do
            if curl -sf http://127.0.0.1:8443/actuator/health >/dev/null 2>&1; then
                echo "  SAPL $runtime started (PID $SERVER_PID)"
                started=true
                break
            fi
            sleep 1
        done

        if ! $started; then
            echo "  ERROR: SAPL $runtime did not start"
            stop_server
            continue
        fi

        if $WRK_CONVERGE; then
            converge_wrk "${CONN_SWEEP[0]}" "$SAPL_URL" "$SAPL_LUA" "$SAPL_SUB"
        fi

        for connections in "${CONN_SWEEP[@]}"; do
            CURRENT_STEP=$((CURRENT_STEP + 1))
            pct=$((CURRENT_STEP * 100 / TOTAL_STEPS))
            prefix="sapl-${runtime}_rbac-opa_${pcores}p_${connections}c"
            client_cpu=$(client_cpus)

            echo "================================================================"
            echo "  Step $CURRENT_STEP of $TOTAL_STEPS ($pct%)"
            echo "  sapl-$runtime / rbac-opa / ${pcores}P / ${connections}c"
            echo "================================================================"

            throughputs=()
            last_latency=""

            for fork_index in $(seq 1 "$MAX_FORKS"); do
                wait_cool

                wrk_output=$(SUBSCRIPTION_FILE="$SAPL_SUB" run_pinned "$client_cpu" wrk -t2 -c"$connections" -d${WRK_MEASURE_TIME}s --latency -s "$SAPL_LUA" "$SAPL_URL" 2>&1)
                throughput=$(parse_wrk_rps "$wrk_output")

                if [ -z "$throughput" ] || [ "$throughput" = "0.00" ]; then
                    echo "    Fork $fork_index: FAILED"
                    continue
                fi

                throughputs+=("$throughput")
                latency_str=$(parse_wrk_latency "$wrk_output")
                if [ -n "$latency_str" ] && [ "$latency_str" != "::" ]; then
                    last_latency="$latency_str"
                fi

                python3 "$BENCH_PY" write-fork-json \
                    --output "$OUTDIR/${prefix}_fork${fork_index}.json" \
                    --score "$throughput" --unit "req/s" \
                    --benchmark "sapl-${runtime}-http" --mode thrpt --transport http \
                    --cores "$pcores" --connections "$connections" \
                    --measurement-time "${WRK_MEASURE_TIME} s" \
                    --scenario "rbac-opa"

                n=${#throughputs[@]}
                cov="N/A"
                if [ "$n" -ge 2 ]; then
                    cov=$(compute_cov "${throughputs[@]}")
                fi
                printf "    Fork %d: %'.0f req/s (CoV: %s%%)\n" "$fork_index" "${throughput%.*}" "$cov"

                converged=$(check_convergence "$CONVERGENCE_WINDOW" "${throughputs[@]}")
                if [ "$converged" = "true" ]; then break; fi
            done

            if [ ${#throughputs[@]} -gt 0 ]; then
                python3 "$BENCH_PY" write-csv \
                    --output "$OUTDIR/${prefix}.csv" \
                    --throughputs "$(IFS=,; echo "${throughputs[*]}")" \
                    --title "SAPL $runtime HTTP Benchmark (rbac-opa)" \
                    --unit throughput_req_s \
                    --scenario "rbac-opa" --cores "$pcores" --connections "$connections" \
                    --runtime "sapl-$runtime" \
                    --measurement "${WRK_MEASURE_TIME}s" \
                    --convergence-threshold "$CONVERGENCE_THRESHOLD" \
                    --convergence-window "$CONVERGENCE_WINDOW" \
                    ${last_latency:+--latency "$last_latency"}
            fi
            echo ""
        done

        stop_server
    done
done

python3 "$BENCH_PY" summarize "$OUTDIR"

echo "================================================================"
echo "  OPA vs SAPL Comparison Complete"
echo "  Results: $OUTDIR"
echo "================================================================"
