#!/bin/bash
#
# Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

set -e

echo "Cleaning SAPL Eclipse plugin caches..."

# Maven/Tycho caches
rm -rf ~/.m2/repository/.cache && echo "  Removed: ~/.m2/repository/.cache"
rm -rf ~/.m2/repository/.meta && echo "  Removed: ~/.m2/repository/.meta"
rm -rf ~/.m2/repository/p2 && echo "  Removed: ~/.m2/repository/p2"
rm -rf ~/.m2/repository/io/sapl/sapl-eclipse-thirdparty && echo "  Removed: ~/.m2/repository/io/sapl/sapl-eclipse-thirdparty"
rm -rf ~/.m2/repository/org/eclipse/lsp4j && echo "  Removed: ~/.m2/repository/org/eclipse/lsp4j"

# p2 caches
rm -rf ~/.p2/pool/plugins/io.sapl* && echo "  Removed: ~/.p2/pool/plugins/io.sapl*"
rm -rf ~/.p2/pool/plugins/sapl* && echo "  Removed: ~/.p2/pool/plugins/sapl*"
rm -rf ~/.p2/pool/features/io.sapl* && echo "  Removed: ~/.p2/pool/features/io.sapl*"
rm -rf ~/.p2/org.eclipse.equinox.p2.repository && echo "  Removed: ~/.p2/org.eclipse.equinox.p2.repository"
rm -rf ~/.p2/org.eclipse.equinox.p2.core && echo "  Removed: ~/.p2/org.eclipse.equinox.p2.core"

# Target directories in this project
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
rm -rf "$SCRIPT_DIR/sapl-eclipse-ui/target" && echo "  Removed: sapl-eclipse-ui/target"
rm -rf "$SCRIPT_DIR/sapl-test-eclipse-ui/target" && echo "  Removed: sapl-test-eclipse-ui/target"
rm -rf "$SCRIPT_DIR/sapl-eclipse-feature/target" && echo "  Removed: sapl-eclipse-feature/target"
rm -rf "$SCRIPT_DIR/sapl-eclipse-repository/target" && echo "  Removed: sapl-eclipse-repository/target"
rm -rf "$SCRIPT_DIR/sapl-eclipse-thirdparty/target" && echo "  Removed: sapl-eclipse-thirdparty/target"

echo ""
echo "Cache cleanup complete."
echo "Remember to start Eclipse with -clean flag after reinstalling."
