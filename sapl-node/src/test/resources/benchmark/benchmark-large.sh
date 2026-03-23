#!/usr/bin/env bash
set -euo pipefail

# SAPL Benchmark - Large corpus (includes 1000, 5000, 10000 policy sets)
# Generates policies with --large flag and runs benchmarks across all scenarios.
# Warning: This takes significantly longer than the standard benchmark.
# Usage: benchmark-large.sh <sapl-binary> <output-dir>

if [ $# -ne 2 ]; then
    echo "Usage: $0 <sapl-command> <output-dir>"
    echo "  <sapl-command>  Path to sapl binary or 'java -jar <path>.jar'"
    echo "  <output-dir>    Directory for results (policies/ and results/ subdirs)"
    exit 1
fi

SAPL_CMD=$1
OUTPUT_DIR=$2
POLICY_DIR="$OUTPUT_DIR/policies"
RESULTS_DIR="$OUTPUT_DIR/results"
MODULE_DIR="$(cd "$(dirname "$0")/../../../.." && pwd)"

echo "=== SAPL Benchmark (large corpus) ==="
echo "Command: $SAPL_CMD"
echo "Output:  $OUTPUT_DIR"
echo

# Generate policies with --large
echo "Generating policy corpus (including large sets)..."
java -cp "$MODULE_DIR/target/test-classes:$MODULE_DIR/target/classes" io.sapl.node.cli.BenchmarkPolicyGenerator "$POLICY_DIR" --large
echo

SCENARIOS="empty simple-1 simple-10 simple-100 simple-500 simple-1000 simple-5000 simple-10000 complex-1 complex-10 complex-100 complex-1000 complex-5000 complex-10000 all-match-100 all-match-1000"

mkdir -p "$RESULTS_DIR"

for scenario in $SCENARIOS; do
    echo "=== $scenario ==="
    $SAPL_CMD benchmark \
        --dir "$POLICY_DIR/$scenario" \
        -s '{"name":"alice","roles":["admin"],"department":"engineering","clearanceLevel":5}' \
        -a '"read"' -r '"document"' \
        --warmup-iterations 3 --warmup-time 5 \
        --measurement-iterations 5 --measurement-time 5 \
        -o "$RESULTS_DIR/$scenario"
    echo
done

echo "=== Benchmark complete ==="
echo "Results in: $RESULTS_DIR"
echo "Reports:    $RESULTS_DIR/*/  (*.md, *.csv, *.json)"
