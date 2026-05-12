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

API_KEY="${1:-}"
BASE_URL="${2:-https://localhost:8443}"
ENDPOINT="${BASE_URL}/api/pdp/decide"

if [[ -z "${API_KEY}" ]]; then
    echo "Usage: $0 API_KEY [BASE_URL]"
    echo ""
    echo "Example: $0 sapl_7A7ByyQd6U_5nTv3KXXLPiZ8JzHQywF9gww2v0iuA3j"
    echo ""
    echo "API keys must start with 'sapl_' prefix."
    exit 1
fi

echo "=== SAPL Node API Key Test ==="
echo "Endpoint: ${ENDPOINT}"
echo "API Key: ${API_KEY:0:15}..."
echo ""

# Test 1: Valid API key - PERMIT request
echo "--- Test 1: PERMIT request with valid API key ---"
HTTP_CODE=$(curl -s -k -o /tmp/sapl-response.json -w "%{http_code}" \
  -X POST "${ENDPOINT}" \
  -H "Authorization: Bearer ${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "Willi",
    "action": "eat",
    "resource": "apple"
  }')

echo "HTTP Status: ${HTTP_CODE}"
if [[ "${HTTP_CODE}" == "200" ]]; then
    cat /tmp/sapl-response.json | jq .
else
    echo "Response:"
    cat /tmp/sapl-response.json
fi
echo ""

# Test 2: Invalid API key
echo "--- Test 2: Request with invalid API key ---"
HTTP_CODE=$(curl -s -k -o /tmp/sapl-response.json -w "%{http_code}" \
  -X POST "${ENDPOINT}" \
  -H "Authorization: Bearer sapl_invalid_key_12345678901234567890" \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "Willi",
    "action": "eat",
    "resource": "apple"
  }')

echo "HTTP Status: ${HTTP_CODE} (expected: 401)"
if [[ "${HTTP_CODE}" == "401" ]]; then
    echo "Authentication correctly rejected"
else
    echo "Response:"
    cat /tmp/sapl-response.json
fi
echo ""

# Test 3: DENY request
echo "--- Test 3: DENY request with valid API key ---"
curl -s -k -X POST "${ENDPOINT}" \
  -H "Authorization: Bearer ${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "Villain",
    "action": "destroy",
    "resource": "world"
  }' | jq .
echo ""

# Interactive mode
echo "--- Interactive Mode ---"
echo "Enter subject (or 'q' to quit):"
while read -r SUBJECT && [[ "${SUBJECT}" != "q" ]]; do
    echo "Enter action:"
    read -r ACTION
    echo "Enter resource:"
    read -r RESOURCE

    echo ""
    echo "Sending request: subject=${SUBJECT}, action=${ACTION}, resource=${RESOURCE}"
    curl -s -k -X POST "${ENDPOINT}" \
      -H "Authorization: Bearer ${API_KEY}" \
      -H "Content-Type: application/json" \
      -d "{
        \"subject\": \"${SUBJECT}\",
        \"action\": \"${ACTION}\",
        \"resource\": \"${RESOURCE}\"
      }" | jq .

    echo ""
    echo "Enter subject (or 'q' to quit):"
done

echo ""
echo "=== Tests completed ==="
