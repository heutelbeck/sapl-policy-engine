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

# Summarize OOPSLA benchmark results into summary.csv and summary.md.
# Scans all *_decideOnceBlocking_1t.csv files in the given directory.
#
# Usage: summarize-oopsla.sh <results-directory>

set -e

RESULTS_DIR="${1:?Usage: summarize-oopsla.sh <results-directory>}"

if [ ! -d "$RESULTS_DIR" ]; then
    echo "Error: $RESULTS_DIR is not a directory"
    exit 1
fi

APPS=(tinytodo gdrive github)
ENTITY_COUNTS=(5 10 15 20 30 40 50)

median_of() {
    local values="$1"
    local count=$(echo "$values" | wc -l)
    local mid=$(( (count + 1) / 2 ))
    echo "$values" | sed -n "${mid}p"
}

TOTAL=$(ls "$RESULTS_DIR"/*_decideOnceBlocking_1t.csv 2>/dev/null | wc -l)

# Generate CSV
CSV="$RESULTS_DIR/summary.csv"
echo "app,entity_count,seeds,p50_median_ns,p90_median_ns,p99_median_ns,p999_median_ns,p50_min_ns,p50_max_ns,p99_min_ns,p99_max_ns" > "$CSV"

# Generate MD
MD="$RESULTS_DIR/summary.md"
cat > "$MD" << MDHEAD
# OOPSLA Benchmark Summary

**Completed:** $TOTAL runs | **Updated:** $(date -u '+%Y-%m-%d %H:%M:%S UTC')
**Directory:** $RESULTS_DIR

## Latency (median across seeds)

| App | n | Seeds | p50 (ns) | p90 (ns) | p99 (ns) | p99.9 (ns) | p50 range | p99 range |
|-----|---|-------|----------|----------|----------|------------|-----------|-----------|
MDHEAD

for app in "${APPS[@]}"; do
    for n in "${ENTITY_COUNTS[@]}"; do
        FILES=("$RESULTS_DIR"/${app}-${n}_seed*_decideOnceBlocking_1t.csv)
        [ ! -f "${FILES[0]}" ] && continue
        count=${#FILES[@]}

        p50s=$(grep "# Latency p50" "${FILES[@]}" | sed 's/.*: //' | sort -n)
        p90s=$(grep "# Latency p90" "${FILES[@]}" | sed 's/.*: //' | sort -n)
        p99s=$(grep "# Latency p99 " "${FILES[@]}" | sed 's/.*: //' | sort -n)
        p999s=$(grep "# Latency p99.9" "${FILES[@]}" | sed 's/.*: //' | sort -n)

        p50_med=$(median_of "$p50s")
        p90_med=$(median_of "$p90s")
        p99_med=$(median_of "$p99s")
        p999_med=$(median_of "$p999s")

        p50_min=$(echo "$p50s" | head -1)
        p50_max=$(echo "$p50s" | tail -1)
        p99_min=$(echo "$p99s" | head -1)
        p99_max=$(echo "$p99s" | tail -1)

        echo "${app},${n},${count},${p50_med},${p90_med},${p99_med},${p999_med},${p50_min},${p50_max},${p99_min},${p99_max}" >> "$CSV"
        echo "| ${app} | ${n} | ${count} | ${p50_med} | ${p90_med} | ${p99_med} | ${p999_med} | ${p50_min}-${p50_max} | ${p99_min}-${p99_max} |" >> "$MD"
    done
done

echo "" >> "$MD"
echo "## Raw file count by scenario" >> "$MD"
echo "" >> "$MD"
echo '```' >> "$MD"
for app in "${APPS[@]}"; do
    for n in "${ENTITY_COUNTS[@]}"; do
        count=$(ls "$RESULTS_DIR"/${app}-${n}_seed*_decideOnceBlocking_1t.csv 2>/dev/null | wc -l)
        [ "$count" -eq 0 ] && continue
        printf "  %-15s %3d seeds\n" "${app}-${n}" "$count" >> "$MD"
    done
done
echo '```' >> "$MD"

echo "Summary written:"
echo "  CSV: $CSV"
echo "  MD:  $MD"
echo ""
cat "$MD"
