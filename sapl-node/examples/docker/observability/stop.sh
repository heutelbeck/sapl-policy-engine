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

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "Stopping load generator..."
pkill -f "load-generator.sh" 2>/dev/null || true

echo "Stopping sapl-node..."
pkill -f "sapl-node-4.0.0-SNAPSHOT.jar" 2>/dev/null || true

echo "Stopping Prometheus and Grafana..."
docker compose -f "${SCRIPT_DIR}/docker-compose.yml" down 2>/dev/null || true

echo "Done."
