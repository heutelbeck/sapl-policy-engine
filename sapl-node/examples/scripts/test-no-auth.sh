#!/usr/bin/env bash
#
# Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
#
# SPDX-License-Identifier: Apache-2.0
#
# Test SAPL Node with no authentication
#
# This script sends authorization requests to a SAPL Node running without
# authentication for experimentation and validation.
#
# Prerequisites:
#   - SAPL Node running with allowNoAuth: true
#   - curl and jq installed
#
# Usage:
#   ./test-no-auth.sh [BASE_URL]
#
# Examples:
#   ./test-no-auth.sh                           # Uses default http://localhost:8443
#   ./test-no-auth.sh https://localhost:8443    # Custom URL with HTTPS
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
