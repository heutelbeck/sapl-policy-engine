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

# Warmup calibration: find minimum warmup for stable throughput.
# Sweeps warmup iterations (1-5) x warmup time (3-45s) with fixed 30s measurement.
# Usage: calibrate-warmup-time.sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

REFERENCE_OPS=26797842

OUTDIR="$SCRIPT_DIR/../results/calibrate-warmup-$(timestamp)"
mkdir -p "$OUTDIR"
LOGFILE="$OUTDIR/output.log"

WARMUP_ITERS_SWEEP=(1 2 3 5)
WARMUP_TIME_SWEEP=(3 5 10 15 30 45)
MEASURE_TIME=30
THREADS=8

TOTAL_STEPS=$(( ${#WARMUP_ITERS_SWEEP[@]} * ${#WARMUP_TIME_SWEEP[@]} ))
CURRENT_STEP=0

log() {
    echo "$@" | tee -a "$LOGFILE"
}

log "================================================================"
log "  Warmup Calibration: minimum warmup for stable throughput"
log "  Scenario:     baseline / decideOnceBlocking / ${THREADS}t"
log "  Warmup iters: ${WARMUP_ITERS_SWEEP[*]}"
log "  Warmup times: ${WARMUP_TIME_SWEEP[*]}s"
log "  Measurement:  ${MEASURE_TIME}s (fixed)"
log "  Reference:    $REFERENCE_OPS ops/s"
log "  Total runs:   $TOTAL_STEPS"
log "  Output:       $OUTDIR"
log "================================================================"
log ""

SUMMARY="$OUTDIR/summary.csv"
echo "warmup_iterations,warmup_time_s,fork1_ops,fork2_ops,mean_ops,vs_reference_pct" > "$SUMMARY"

for wi in "${WARMUP_ITERS_SWEEP[@]}"; do
    for wt in "${WARMUP_TIME_SWEEP[@]}"; do
        CURRENT_STEP=$((CURRENT_STEP + 1))
        pct=$((CURRENT_STEP * 100 / TOTAL_STEPS))
        cpu_range=$(server_cpus 8)
        run_dir="$OUTDIR/wi${wi}_wt${wt}"
        mkdir -p "$run_dir"

        log "================================================================"
        log "  Step $CURRENT_STEP of $TOTAL_STEPS ($pct%)"
        log "  Warmup: ${wi} x ${wt}s, Measurement: ${MEASURE_TIME}s"
        log "================================================================"
        wait_cool

        run_pinned "$cpu_range" java -jar "$SAPL4_BENCH_JAR" \
            --scenario=baseline \
            --method=decideOnceBlocking \
            -t "$THREADS" \
            --warmup-iterations="$wi" \
            --warmup-time="$wt" \
            --measurement-time="$MEASURE_TIME" \
            --convergence-threshold=50 \
            --convergence-window=2 \
            --max-forks=2 \
            --heap=32g \
            -o "$run_dir" 2>&1 | tee -a "$LOGFILE"

        scores=($(python3 "$BENCH_PY" parse-score \
            "$run_dir/baseline_decideOnceBlocking_8t_fork1.json" \
            "$run_dir/baseline_decideOnceBlocking_8t_fork2.json"))
        fork1=${scores[0]}
        fork2=${scores[1]}
        mean=$(( (fork1 + fork2) / 2 ))
        diff_pct=$(python3 "$BENCH_PY" ref-diff "$mean" "$REFERENCE_OPS")

        echo "$wi,$wt,$fork1,$fork2,$mean,$diff_pct" >> "$SUMMARY"
        log ""
        log "  Result: wi=${wi} wt=${wt}s -> ${mean} ops/s (${diff_pct}% vs reference)"
        log ""
    done
done

log "================================================================"
log "  Warmup Calibration Complete"
log "  Summary: $SUMMARY"
log "================================================================"
log ""

log "Results vs reference ($REFERENCE_OPS ops/s):"
log ""
column -t -s',' "$SUMMARY" | tee -a "$LOGFILE"
