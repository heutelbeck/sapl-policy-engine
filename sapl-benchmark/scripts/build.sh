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

# Build all benchmark binaries and copy them to a stable location.
# Builds JVM JARs always. Builds native image if native-image is available.
#
# Usage: build.sh
#
# Output directory: sapl-benchmark/bin/
#   sapl-node.jar                  Spring Boot fat JAR (server + benchmark CLI)
#   sapl-benchmark-sapl4.jar       JMH benchmark runner (forks(1), flat classpath)
#   sapl                           Native binary (when GraalVM available)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."
BIN_DIR="$SCRIPT_DIR/../bin"

mkdir -p "$BIN_DIR"

echo "================================================================"
echo "  SAPL Benchmark Build"
echo "  Output: $BIN_DIR"
echo "================================================================"
echo ""

# Step 1: Build JVM JARs
echo "Building JVM JARs..."
mvn package -pl sapl-node,sapl-benchmark/sapl-benchmark-sapl4 -am -DskipTests -Dlicense.skip -q -f "$REPO_ROOT/pom.xml"
if [ $? -ne 0 ]; then
    echo "ERROR: JVM build failed"
    exit 1
fi

# Copy JVM artifacts to stable location
cp "$REPO_ROOT/sapl-node/target/sapl-node-"*"-SNAPSHOT.jar" "$BIN_DIR/sapl-node.jar"
cp "$REPO_ROOT/sapl-benchmark/sapl-benchmark-sapl4/target/sapl-benchmark-sapl4-"*"-SNAPSHOT.jar" "$BIN_DIR/sapl-benchmark-sapl4.jar"

echo "  sapl-node.jar              $(du -h "$BIN_DIR/sapl-node.jar" | cut -f1)"
echo "  sapl-benchmark-sapl4.jar   $(du -h "$BIN_DIR/sapl-benchmark-sapl4.jar" | cut -f1)"

# Step 2: Build native image (if GraalVM available)
if command -v native-image &>/dev/null; then
    echo ""
    echo "Building native image..."

    mvn package -pl sapl-node -am -Pnative -DskipTests -Dlicense.skip -q -f "$REPO_ROOT/pom.xml"
    if [ $? -ne 0 ]; then
        echo "ERROR: Native build failed"
        exit 1
    fi

    cp "$REPO_ROOT/sapl-node/target/sapl" "$BIN_DIR/sapl"
    chmod +x "$BIN_DIR/sapl"
    echo "  sapl (native)              $(du -h "$BIN_DIR/sapl" | cut -f1)"

else
    echo ""
    echo "Skipping native build (native-image not found)."
    echo "For native, run inside GraalVM shell: nix develop ~/.dotfiles#graalvm --command ./scripts/build.sh"
fi

echo ""
echo "================================================================"
echo "  Build complete"
echo "  Binaries: $BIN_DIR"
echo "================================================================"
ls -lh "$BIN_DIR/"
