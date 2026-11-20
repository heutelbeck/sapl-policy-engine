#!/usr/bin/env bash
# Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

modules="${1:?SBOM modules are required.}"
shift

# CycloneDX requires online execution and packaged reactor dependencies.
# Its bound goals embed the SBOM in the current artifacts.
mvn -B verify -pl "$modules" -am -DskipTests "$@"
