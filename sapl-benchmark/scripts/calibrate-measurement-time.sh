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

# Measurement calibration: find minimum measurement duration for stable CoV.
# Fixed warmup 1x3s, sweeps measurement time with 3 forks for CoV calculation.
# Usage: calibrate-measurement-time.sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

REFERENCE_OPS=26797842

OUTDIR="$SCRIPT_DIR/../results/calibrate-measurement-$(timestamp)"
mkdir -p "$OUTDIR"
LOGFILE="$OUTDIR/output.log"

MEASURE_SWEEP=(5 10 15 30 60 120)
WARMUP_ITERS=1
WARMUP_TIME=3
THREADS=8
FORKS=3

TOTAL_STEPS=${#MEASURE_SWEEP[@]}
CURRENT_STEP=0

log() {
    echo "$@" | tee -a "$LOGFILE"
}

log "================================================================"
log "  Measurement Calibration: minimum duration for stable CoV"
log "  Scenario:     baseline / decideOnceBlocking / ${THREADS}t"
log "  Warmup:       ${WARMUP_ITERS} x ${WARMUP_TIME}s (fixed)"
log "  Measurement:  ${MEASURE_SWEEP[*]}s"
log "  Forks:        $FORKS per config"
log "  Reference:    $REFERENCE_OPS ops/s"
log "  Total runs:   $TOTAL_STEPS"
log "  Output:       $OUTDIR"
log "================================================================"
log ""

SUMMARY="$OUTDIR/summary.csv"
echo "measurement_time_s,fork1_ops,fork2_ops,fork3_ops,mean_ops,cov_pct,vs_reference_pct" > "$SUMMARY"

for mt in "${MEASURE_SWEEP[@]}"; do
    CURRENT_STEP=$((CURRENT_STEP + 1))
    pct=$((CURRENT_STEP * 100 / TOTAL_STEPS))
    cpu_range=$(server_cpus 8)
    run_dir="$OUTDIR/mt${mt}"
    mkdir -p "$run_dir"

    log "================================================================"
    log "  Step $CURRENT_STEP of $TOTAL_STEPS ($pct%)"
    log "  Measurement: ${mt}s (warmup: ${WARMUP_ITERS} x ${WARMUP_TIME}s)"
    log "================================================================"
    wait_cool

    run_pinned "$cpu_range" java -jar "$SAPL4_BENCH_JAR" \
        --scenario=baseline \
        --method=decideOnceBlocking \
        -t "$THREADS" \
        --warmup-iterations="$WARMUP_ITERS" \
        --warmup-time="$WARMUP_TIME" \
        --measurement-time="$mt" \
        --convergence-threshold=50 \
        --convergence-window=2 \
        --max-forks="$FORKS" \
        --heap=32g \
        -o "$run_dir" 2>&1 | tee -a "$LOGFILE"

    scores=""
    for i in $(seq 1 $FORKS); do
        s=$(python3 -c "import json; d=json.load(open('$run_dir/baseline_decideOnceBlocking_8t_fork${i}.json'))[0]; print(f\"{d['primaryMetric']['score']:.0f}\")" 2>/dev/null || echo "0")
        scores="$scores $s"
    done

    python3 -c "
import math
scores = [int(x) for x in '$scores'.split() if int(x) > 0]
n = len(scores)
mean = sum(scores) / n
std = math.sqrt(sum((x - mean)**2 for x in scores) / (n - 1)) if n > 1 else 0
cov = (std / mean * 100) if mean > 0 else 0
ref = $REFERENCE_OPS
diff = (mean - ref) / ref * 100
vals = ','.join(str(s) for s in scores)
while vals.count(',') < 2:
    vals += ',0'
print(f'$mt,{vals},{mean:.0f},{cov:.2f},{diff:+.1f}')
" >> "$SUMMARY"

    log ""
done

log "================================================================"
log "  Measurement Calibration Complete"
log "  Summary: $SUMMARY"
log "================================================================"
log ""

log "Results vs reference ($REFERENCE_OPS ops/s):"
log ""
column -t -s',' "$SUMMARY" | tee -a "$LOGFILE"
