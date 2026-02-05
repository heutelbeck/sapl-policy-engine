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

AUTH_TYPE="${1:-noauth}"
CREDENTIALS="${2:-}"
BASE_URL="${3:-https://localhost:8443}"
ENDPOINT="${BASE_URL}/api/pdp/decide"

echo "=== SAPL Node Streaming Subscription Test ==="
echo "Endpoint: ${ENDPOINT}"
echo "Auth Type: ${AUTH_TYPE}"
echo ""
echo "This will open a streaming connection. Press Ctrl+C to stop."
echo "Try modifying policies while this is running to see live updates!"
echo ""

# Build curl command based on auth type
CURL_CMD="curl -s -k -N -X POST ${ENDPOINT}"

case "${AUTH_TYPE}" in
    noauth)
        echo "Using: No authentication"
        ;;
    basic)
        if [[ -z "${CREDENTIALS}" ]]; then
            echo "Error: Basic auth requires credentials in format 'username:password'"
            exit 1
        fi
        CURL_CMD="${CURL_CMD} -u ${CREDENTIALS}"
        echo "Using: Basic auth"
        ;;
    apikey)
        if [[ -z "${CREDENTIALS}" ]]; then
            echo "Error: API key auth requires the API key"
            exit 1
        fi
        CURL_CMD="${CURL_CMD} -H 'Authorization: Bearer ${CREDENTIALS}'"
        echo "Using: API key auth"
        ;;
    *)
        echo "Unknown auth type: ${AUTH_TYPE}"
        echo "Supported: noauth, basic, apikey"
        exit 1
        ;;
esac

echo ""
echo "--- Starting streaming subscription ---"
echo "Subject: StreamingUser, Action: eat, Resource: apple"
echo ""

# Use SSE streaming endpoint (Server-Sent Events)
eval "${CURL_CMD}" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "subject": "StreamingUser",
    "action": "eat",
    "resource": "apple"
  }' | while IFS= read -r line; do
    if [[ -n "${line}" ]]; then
        # Try to parse as JSON for pretty printing
        if echo "${line}" | jq . 2>/dev/null; then
            :
        else
            echo "${line}"
        fi
    fi
done

echo ""
echo "=== Streaming ended ==="
