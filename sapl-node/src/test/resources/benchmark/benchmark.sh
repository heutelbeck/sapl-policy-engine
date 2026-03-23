#!/usr/bin/env bash
set -euo pipefail

# SAPL Benchmark - Standard corpus
# Generates policies and runs benchmarks across all scenarios.
# Usage: benchmark.sh <sapl-binary> <output-dir>
# Example: benchmark.sh "java -jar sapl-node.jar" ./benchmark-results
#          benchmark.sh ./target/sapl ./benchmark-results-native

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

SUB='-s {"name":"alice","roles":["admin"],"department":"engineering","clearanceLevel":5} -a "read" -r "document"'

echo "=== SAPL Benchmark ==="
echo "Command: $SAPL_CMD"
echo "Output:  $OUTPUT_DIR"
echo

# Generate policies
echo "Generating policy corpus..."
java -cp "$MODULE_DIR/target/test-classes:$MODULE_DIR/target/classes" io.sapl.node.cli.BenchmarkPolicyGenerator "$POLICY_DIR"
echo

SCENARIOS="empty simple-1 simple-10 simple-100 simple-500 complex-1 complex-10 complex-100 all-match-100"

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
