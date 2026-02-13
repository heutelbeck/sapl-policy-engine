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

BASE_URL="${1:-http://localhost:8080}"
ENDPOINT="${BASE_URL}/api/pdp/decide-once"
INTERVAL="${2:-0.5}"

# Authorization requests that produce different decisions:
#   read + documents  -> PERMIT  (permit-read matches)
#   read + admin-panel -> DENY   (deny-admin-resources wins over permit-read)
#   write + documents -> DENY    (nothing permits, default decision)
#   delete + records  -> DENY    (nothing permits, default decision)

REQUESTS=(
  '{"subject":"alice","action":"read","resource":"documents"}'
  '{"subject":"bob","action":"read","resource":"documents"}'
  '{"subject":"alice","action":"read","resource":"admin-panel"}'
  '{"subject":"bob","action":"read","resource":"admin-settings"}'
  '{"subject":"charlie","action":"write","resource":"documents"}'
  '{"subject":"alice","action":"delete","resource":"records"}'
  '{"subject":"dave","action":"read","resource":"reports"}'
  '{"subject":"eve","action":"read","resource":"admin-dashboard"}'
)

echo "Load generator started (interval: ${INTERVAL}s)"
echo "Endpoint: ${ENDPOINT}"
echo ""

COUNT=0
while true; do
    IDX=$((COUNT % ${#REQUESTS[@]}))
    BODY="${REQUESTS[$IDX]}"

    RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST "${ENDPOINT}" \
        -H "Content-Type: application/json" \
        -d "${BODY}" 2>/dev/null) || true

    COUNT=$((COUNT + 1))

    if (( COUNT % 20 == 0 )); then
        echo "[load-generator] ${COUNT} requests sent"
    fi

    sleep "${INTERVAL}"
done
