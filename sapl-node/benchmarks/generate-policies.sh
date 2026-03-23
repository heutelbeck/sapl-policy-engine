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
#   ./generate-policies.sh <output-dir> [--large]
#
# The standard corpus includes:
#   empty, simple-{1,10,100,500}, complex-{1,10,100}, all-match-100,
#   rbac-small, rbac-large, abac-equivalent
#
# With --large, additionally generates:
#   simple-{1000,5000,10000}, complex-{1000,5000,10000}, all-match-1000
#
# Requires: sapl-node must be compiled (mvn test-compile -pl sapl-node)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MODULE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

if [ $# -lt 1 ] || [ $# -gt 2 ]; then
    echo "Usage: $0 <output-dir> [--large]"
    exit 1
fi

OUTPUT_DIR=$1
LARGE_FLAG=${2:-}

# Verify compiled classes exist
if [ ! -d "$MODULE_DIR/target/test-classes" ]; then
    echo "Error: sapl-node test classes not found. Run: mvn test-compile -pl sapl-node"
    exit 1
fi

CLASSPATH="$MODULE_DIR/target/test-classes:$MODULE_DIR/target/classes"

# Add all dependency JARs from target/dependency if present, otherwise from Maven repo
if [ -d "$MODULE_DIR/target/dependency" ]; then
    CLASSPATH="$CLASSPATH:$MODULE_DIR/target/dependency/*"
fi

java -cp "$CLASSPATH" io.sapl.node.cli.BenchmarkPolicyGenerator "$OUTPUT_DIR" $LARGE_FLAG
