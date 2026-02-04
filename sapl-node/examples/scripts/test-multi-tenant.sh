#!/usr/bin/env bash
#
# Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
#
# SPDX-License-Identifier: Apache-2.0
#
# Test SAPL Node Multi-Tenant Routing
#
# This script demonstrates multi-tenant pdpId routing by sending requests
# as different users with different pdpId assignments.
#
# Prerequisites:
#   - SAPL Node running with multi-tenant configuration (MultiDirectory or Bundle source)
#   - Multiple users configured with different pdpId values
#   - curl and jq installed
#
# Usage:
#   ./test-multi-tenant.sh [BASE_URL]
#
# This script is interactive and will prompt for user credentials.
#

set -euo pipefail

BASE_URL="${1:-https://localhost:8443}"
ENDPOINT="${BASE_URL}/api/pdp/decide"

echo "=== SAPL Node Multi-Tenant Test ==="
echo "Endpoint: ${ENDPOINT}"
echo ""
echo "This test demonstrates tenant isolation by sending the same request"
echo "through different authenticated users with different pdpId assignments."
echo ""

# Function to send request
send_request() {
    local USER=$1
    local PASS=$2
    local LABEL=$3

    echo "--- ${LABEL} ---"
    HTTP_CODE=$(curl -s -k -o /tmp/sapl-response.json -w "%{http_code}" \
      -X POST "${ENDPOINT}" \
      -u "${USER}:${PASS}" \
      -H "Content-Type: application/json" \
      -d '{
        "subject": "TestUser",
        "action": "read",
        "resource": "data"
      }')

    echo "HTTP Status: ${HTTP_CODE}"
    if [[ "${HTTP_CODE}" == "200" ]]; then
        echo "Decision:"
        cat /tmp/sapl-response.json | jq '.decision'
    else
        echo "Response:"
        cat /tmp/sapl-response.json
    fi
    echo ""
}

# Interactive credential entry
echo "Enter credentials for Tenant A (production - expect DENY):"
echo "Username:"
read -r USER_A
echo "Password:"
read -rs PASS_A
echo ""

echo "Enter credentials for Tenant B (staging - expect PERMIT):"
echo "Username:"
read -r USER_B
echo "Password:"
read -rs PASS_B
echo ""

echo "=== Sending identical requests through different tenants ==="
echo ""

# Send same request through both tenants
send_request "${USER_A}" "${PASS_A}" "Tenant A (production)"
send_request "${USER_B}" "${PASS_B}" "Tenant B (staging)"

echo "=== Comparison ==="
echo "If tenant isolation is working correctly:"
echo "  - Tenant A (production with strict policy) should return DENY"
echo "  - Tenant B (staging with permissive policy) should return PERMIT"
echo ""

# Offer to repeat
echo "Press Enter to run again, or 'q' to quit:"
while read -r INPUT && [[ "${INPUT}" != "q" ]]; do
    send_request "${USER_A}" "${PASS_A}" "Tenant A (production)"
    send_request "${USER_B}" "${PASS_B}" "Tenant B (staging)"
    echo "Press Enter to run again, or 'q' to quit:"
done

echo ""
echo "=== Test completed ==="
