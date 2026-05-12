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

BASE_URL="${1:-http://localhost:8443}"
ENDPOINT="${BASE_URL}/api/pdp/decide"

echo "=== SAPL Node No-Auth Test ==="
echo "Endpoint: ${ENDPOINT}"
echo ""

# Test 1: Simple PERMIT request
echo "--- Test 1: PERMIT request (action=eat, resource=apple) ---"
curl -s -k -X POST "${ENDPOINT}" \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "Willi",
    "action": "eat",
    "resource": "apple"
  }' | jq .
echo ""

# Test 2: Simple DENY request (no matching policy)
echo "--- Test 2: DENY request (action=destroy, resource=world) ---"
curl -s -k -X POST "${ENDPOINT}" \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "Villain",
    "action": "destroy",
    "resource": "world"
  }' | jq .
echo ""

# Test 3: Custom request (interactive)
echo "--- Test 3: Custom request ---"
echo "Enter subject (or press Enter for 'TestUser'):"
read -r SUBJECT
SUBJECT="${SUBJECT:-TestUser}"

echo "Enter action (or press Enter for 'read'):"
read -r ACTION
ACTION="${ACTION:-read}"

echo "Enter resource (or press Enter for 'document'):"
read -r RESOURCE
RESOURCE="${RESOURCE:-document}"

echo ""
echo "Sending request: subject=${SUBJECT}, action=${ACTION}, resource=${RESOURCE}"
curl -s -k -X POST "${ENDPOINT}" \
  -H "Content-Type: application/json" \
  -d "{
    \"subject\": \"${SUBJECT}\",
    \"action\": \"${ACTION}\",
    \"resource\": \"${RESOURCE}\"
  }" | jq .

echo ""
echo "=== Tests completed ==="
