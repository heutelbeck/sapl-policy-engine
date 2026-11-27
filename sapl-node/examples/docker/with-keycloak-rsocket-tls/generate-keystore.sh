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

KEYSTORE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/tls"
KEYSTORE_FILE="$KEYSTORE_DIR/keystore.p12"
ALIAS="sapl-node"
PASSWORD="changeit"
SAN="DNS:sapl-node,DNS:localhost,IP:127.0.0.1"
DNAME="CN=sapl-node, OU=SAPL, O=Heutelbeck, L=Hagen, ST=NRW, C=DE"
VALIDITY_DAYS=365

mkdir -p "$KEYSTORE_DIR"

if [ -f "$KEYSTORE_FILE" ]; then
    echo "Keystore already exists at $KEYSTORE_FILE."
    echo "Delete it manually if you want to regenerate."
    exit 0
fi

keytool -genkeypair -alias "$ALIAS" -keyalg RSA -keysize 2048 -validity "$VALIDITY_DAYS" -dname "$DNAME" -ext "SAN=$SAN" -keystore "$KEYSTORE_FILE" -storetype PKCS12 -storepass "$PASSWORD" -keypass "$PASSWORD"

echo
echo "Keystore created at $KEYSTORE_FILE"
echo "Alias:    $ALIAS"
echo "Password: $PASSWORD (storepass = keypass)"
echo
echo "The docker-compose.yml mounts ./tls into the sapl-node container at /pdp/tls."
echo "The consumer reaches the PDP via the internal name 'sapl-node', which is"
echo "covered by the SAN in this certificate."
echo
echo "Start the demo with: docker compose up --build"
