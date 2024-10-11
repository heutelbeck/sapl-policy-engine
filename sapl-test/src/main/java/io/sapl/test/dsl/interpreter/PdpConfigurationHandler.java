/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sapl.test.dsl.interpreter;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.grammar.sapltest.PdpCombiningAlgorithm;
import io.sapl.test.grammar.sapltest.PdpVariables;
import io.sapl.test.integration.SaplIntegrationTestFixture;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PdpConfigurationHandler {

    private final ValueInterpreter              valueInterpreter;
    private final CombiningAlgorithmInterpreter combiningAlgorithmInterpreter;

    public SaplTestFixture applyPdpConfigurationToFixture(SaplIntegrationTestFixture integrationTestFixture,
            final PdpVariables pdpVariables, final PdpCombiningAlgorithm pdpCombiningAlgorithm) {
        if (pdpVariables != null
                && pdpVariables.getPdpVariables() instanceof io.sapl.test.grammar.sapltest.Object object) {
            final var pdpEnvironmentVariables = valueInterpreter.destructureObject(object);
            integrationTestFixture = integrationTestFixture
                    .withPDPVariables(packageJsonNodesInVal(pdpEnvironmentVariables));
        }

        if (pdpCombiningAlgorithm != null && pdpCombiningAlgorithm.isCombiningAlgorithmDefined()) {
            final var pdpPolicyCombiningAlgorithm = combiningAlgorithmInterpreter
                    .interpretPdpCombiningAlgorithm(pdpCombiningAlgorithm.getCombiningAlgorithm());
            integrationTestFixture = integrationTestFixture
                    .withPDPPolicyCombiningAlgorithm(pdpPolicyCombiningAlgorithm);
        }

        return integrationTestFixture;
    }

    private Map<String, Val> packageJsonNodesInVal(Map<String, JsonNode> originalMap) {
        final var newMap = new HashMap<String, Val>();
        originalMap.forEach((key, json) -> newMap.put(key, Val.of(json)));
        return newMap;
    }
}
