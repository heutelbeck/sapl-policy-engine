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

# Convenience wrapper: enters the GraalVM Nix shell and runs build.sh.
# Builds JVM JARs + native image in one step.
#
# Requires: nix with flakes enabled, ~/.dotfiles#graalvm flake output.
# The shell definition is copied to lib/graalvm-shell.nix for reference.
# Non-NixOS users: install GraalVM and native-image manually, then run build.sh directly.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
exec nix develop ~/.dotfiles#graalvm --command "$SCRIPT_DIR/build.sh"
