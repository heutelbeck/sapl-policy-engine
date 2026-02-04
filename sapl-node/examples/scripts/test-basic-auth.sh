#!/usr/bin/env bash
#
# Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
#
# SPDX-License-Identifier: Apache-2.0
#
# Test SAPL Node with Basic Authentication
#
# This script sends authorization requests to a SAPL Node using HTTP Basic Auth
# for experimentation and validation.
#
# Prerequisites:
#   - SAPL Node running with allowBasicAuth: true and configured users
#   - curl and jq installed
#
# Usage:
#   ./test-basic-auth.sh [USERNAME] [PASSWORD] [BASE_URL]
#
# Examples:
#   ./test-basic-auth.sh myuser mypassword
#   ./test-basic-auth.sh myuser mypassword https://localhost:8443
#

set -euo pipefail

USERNAME="${1:-}"
PASSWORD="${2:-}"
BASE_URL="${3:-https://localhost:8443}"
ENDPOINT="${BASE_URL}/api/pdp/decide"

if [[ -z "${USERNAME}" || -z "${PASSWORD}" ]]; then
    echo "Usage: $0 USERNAME PASSWORD [BASE_URL]"
    echo ""
    echo "Example: $0 xwuUaRD65G '3j_PK71bjy!hN3*xq.xZqveU)t5hKLR_'"
    exit 1
fi

echo "=== SAPL Node Basic Auth Test ==="
echo "Endpoint: ${ENDPOINT}"
echo "Username: ${USERNAME}"
echo ""

# Test 1: Valid credentials - PERMIT request
echo "--- Test 1: PERMIT request with valid credentials ---"
HTTP_CODE=$(curl -s -k -o /tmp/sapl-response.json -w "%{http_code}" \
  -X POST "${ENDPOINT}" \
  -u "${USERNAME}:${PASSWORD}" \
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

# Test 2: Invalid credentials
echo "--- Test 2: Request with invalid credentials ---"
HTTP_CODE=$(curl -s -k -o /tmp/sapl-response.json -w "%{http_code}" \
  -X POST "${ENDPOINT}" \
  -u "${USERNAME}:wrongpassword" \
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
echo "--- Test 3: DENY request with valid credentials ---"
curl -s -k -X POST "${ENDPOINT}" \
  -u "${USERNAME}:${PASSWORD}" \
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
      -u "${USERNAME}:${PASSWORD}" \
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
