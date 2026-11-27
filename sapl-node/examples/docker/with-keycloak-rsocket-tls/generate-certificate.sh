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

TLS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/tls"
CERTIFICATE="$TLS_DIR/server.pem"
PRIVATE_KEY="$TLS_DIR/server.key"
VALIDITY_DAYS=365

mkdir -p "$TLS_DIR"

if [ -f "$CERTIFICATE" ]; then
    echo "A certificate already exists at $CERTIFICATE."
    echo "Delete it manually if you want to regenerate."
    exit 0
fi

openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days "$VALIDITY_DAYS" -subj "/CN=sapl-node" -addext "subjectAltName=DNS:sapl-node,DNS:localhost,IP:127.0.0.1" -keyout "$PRIVATE_KEY" -out "$CERTIFICATE"

# The container runs as another user than the one who generated the key.
# Readable by all is acceptable for this development key only.
chmod 644 "$PRIVATE_KEY"

echo
echo "Certificate created at $CERTIFICATE"
echo "Private key created at $PRIVATE_KEY"
echo
echo "The docker-compose.yml mounts ./tls into the sapl-node container at /pdp/tls."
echo "The consumer reaches the PDP via the internal name 'sapl-node', which is"
echo "covered by the SAN in this certificate."
echo "Start the demo with: docker compose up --build"
