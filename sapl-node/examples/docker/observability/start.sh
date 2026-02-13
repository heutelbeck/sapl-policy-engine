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
PROJECT_ROOT="${SCRIPT_DIR}/../../../.."
SAPL_NODE_DIR="${PROJECT_ROOT}/sapl-node"
JAR="${SAPL_NODE_DIR}/target/sapl-node-4.0.0-SNAPSHOT.jar"

SAPL_PID=""
LOAD_PID=""

cleanup() {
    echo ""
    echo "Stopping..."
    [[ -n "${LOAD_PID}" ]] && kill "${LOAD_PID}" 2>/dev/null || true
    [[ -n "${SAPL_PID}" ]] && kill "${SAPL_PID}" 2>/dev/null || true
    docker compose -f "${SCRIPT_DIR}/docker-compose.yml" down 2>/dev/null || true
    echo "Done."
}
trap cleanup EXIT

# -- Prerequisites -----------------------------------------------------------

for cmd in java mvn docker curl; do
    if ! command -v "${cmd}" &>/dev/null; then
        echo "Error: ${cmd} is required but not found."
        exit 1
    fi
done

# -- Build -------------------------------------------------------------------

if [[ ! -f "${JAR}" ]]; then
    echo "Building sapl-node JAR (this may take a few minutes)..."
    mvn -f "${PROJECT_ROOT}/pom.xml" package -pl sapl-node -am -DskipTests -q
fi
echo "JAR: ${JAR}"

# -- Start Prometheus + Grafana -----------------------------------------------

echo "Starting Prometheus and Grafana..."
docker compose -f "${SCRIPT_DIR}/docker-compose.yml" up -d

# -- Start sapl-node ----------------------------------------------------------

echo "Starting sapl-node..."
java -jar "${JAR}" \
    --spring.config.additional-location="file:${SCRIPT_DIR}/config/" \
    --io.sapl.pdp.embedded.config-path="${SCRIPT_DIR}/policies" \
    --io.sapl.pdp.embedded.policies-path="${SCRIPT_DIR}/policies" \
    > /dev/null 2>&1 &
SAPL_PID=$!

echo "Waiting for sapl-node (PID ${SAPL_PID})..."
for i in $(seq 1 60); do
    if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "sapl-node is ready."
        break
    fi
    if ! kill -0 "${SAPL_PID}" 2>/dev/null; then
        echo "Error: sapl-node failed to start. Run without start.sh to see logs:"
        echo "  java -jar ${JAR} --spring.config.additional-location=file:${SCRIPT_DIR}/config/ \\"
        echo "    --io.sapl.pdp.embedded.config-path=${SCRIPT_DIR}/policies \\"
        echo "    --io.sapl.pdp.embedded.policies-path=${SCRIPT_DIR}/policies"
        exit 1
    fi
    sleep 1
done

# -- Start load generator -----------------------------------------------------

echo "Starting load generator..."
"${SCRIPT_DIR}/load-generator.sh" &
LOAD_PID=$!

# -- Print URLs ---------------------------------------------------------------

echo ""
echo "============================================"
echo "  SAPL Observability Demo"
echo "============================================"
echo ""
echo "  sapl-node        http://localhost:8080"
echo "  Prometheus        http://localhost:9090"
echo "  Grafana           http://localhost:3000"
echo "                    (login: admin / admin)"
echo ""
echo "  Prometheus metrics:"
echo "    http://localhost:8080/actuator/prometheus"
echo ""
echo "  Health check:"
echo "    http://localhost:8080/actuator/health"
echo ""
echo "  Manual test:"
echo "    curl -X POST http://localhost:8080/api/pdp/decide-once \\"
echo "      -H 'Content-Type: application/json' \\"
echo "      -d '{\"subject\":\"alice\",\"action\":\"read\",\"resource\":\"documents\"}'"
echo ""
echo "  Press Ctrl+C to stop all services."
echo "============================================"
echo ""

wait "${SAPL_PID}"
