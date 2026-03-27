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

# Run all benchmarks: embedded, HTTP, and RSocket.
#
# Usage: ./run-all.sh <output-dir> [quick|standard|scientific]

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ $# -lt 1 ]; then
    echo "Usage: $0 <output-dir> [quick|standard|scientific]"
    exit 1
fi

OUTPUT_DIR=$1
CONFIG=${2:-quick}

echo "================================================================"
echo "  SAPL 4.0 Full Benchmark Suite ($CONFIG)"
echo "================================================================"
echo ""

"$SCRIPT_DIR/run-embedded.sh" "$OUTPUT_DIR/embedded" "$CONFIG"
echo ""

"$SCRIPT_DIR/run-http.sh" "$OUTPUT_DIR/http" both "$CONFIG"
echo ""

"$SCRIPT_DIR/run-rsocket.sh" "$OUTPUT_DIR/rsocket" both "$CONFIG"
echo ""

echo "================================================================"
echo "  Full benchmark suite complete."
echo "  Results: $OUTPUT_DIR"
echo "================================================================"
