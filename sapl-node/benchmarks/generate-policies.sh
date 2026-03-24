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

set -euo pipefail

# generate-policies.sh - Generates the standardized SAPL benchmark policy corpus.
#
# Usage:
#   ./generate-policies.sh <sapl-command> <output-dir> [--large]
#
# Examples:
#   ./generate-policies.sh "java -jar ../target/sapl-node-4.0.0-SNAPSHOT.jar" /tmp/policies
#   ./generate-policies.sh ../target/sapl /tmp/policies --large

if [ $# -lt 2 ] || [ $# -gt 3 ]; then
    echo "Usage: $0 <sapl-command> <output-dir> [--large]"
    exit 1
fi

SAPL_CMD=$1
OUTPUT_DIR=$2
LARGE_FLAG=${3:-}

$SAPL_CMD generate-benchmark-policies "$OUTPUT_DIR" $LARGE_FLAG
