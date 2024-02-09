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

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.dsl.interfaces.IntegrationTestPolicyResolver;
import io.sapl.test.dsl.interfaces.UnitTestPolicyResolver;
import io.sapl.test.grammar.sapltest.IntegrationTestSuite;
import io.sapl.test.grammar.sapltest.PoliciesByIdentifier;
import io.sapl.test.grammar.sapltest.PoliciesByInputString;
import io.sapl.test.grammar.sapltest.TestSuite;
import io.sapl.test.grammar.sapltest.UnitTestSuite;
import io.sapl.test.integration.SaplIntegrationTestFixture;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class TestSuiteInterpreter {

    private final ValueInterpreter              valueInterpreter;
    private final CombiningAlgorithmInterpreter combiningAlgorithmInterpreter;
    private final UnitTestPolicyResolver        customUnitTestPolicyResolver;
    private final IntegrationTestPolicyResolver customIntegrationTestPolicyResolver;

    SaplTestFixture getFixtureFromTestSuite(final TestSuite testSuite) {
        SaplTestFixture saplTestFixture;
        if (testSuite instanceof UnitTestSuite unitTestSuite) {
            saplTestFixture = getUnitTestFixtureFromUnitTestSuite(unitTestSuite);
        } else if (testSuite instanceof IntegrationTestSuite integrationTestSuite) {
            saplTestFixture = getIntegrationTestFixtureFromIntegrationTestSuite(integrationTestSuite);
        } else {
            throw new SaplTestException("Unknown type of TestSuite");
        }

        return saplTestFixture;
    }

    private SaplTestFixture getUnitTestFixtureFromUnitTestSuite(final UnitTestSuite unitTestSuite) {
        if (customUnitTestPolicyResolver == null) {
            return SaplUnitTestFixtureFactory.create(unitTestSuite.getPolicyName());
        } else {
            return SaplUnitTestFixtureFactory.createFromInputString(
                    customUnitTestPolicyResolver.resolvePolicyByIdentifier(unitTestSuite.getPolicyName()));
        }
    }

    private SaplTestFixture getIntegrationTestFixtureFromIntegrationTestSuite(
            final IntegrationTestSuite integrationTestSuite) {
        final var                  policyResolverConfig = integrationTestSuite.getConfiguration();
        SaplIntegrationTestFixture integrationTestFixture;

        if (policyResolverConfig instanceof PoliciesByIdentifier policiesByIdentifier) {
            integrationTestFixture = handlePoliciesByIdentifier(policiesByIdentifier);
        } else if (policyResolverConfig instanceof PoliciesByInputString policiesByInputString) {
            integrationTestFixture = handlePoliciesByInputString(policiesByInputString);
        } else {
            throw new SaplTestException("Unknown type of PolicyResolverConfig");
        }

        if (integrationTestSuite.getPdpVariables() instanceof io.sapl.test.grammar.sapltest.Object pdpVariables) {
            final var pdpEnvironmentVariables = valueInterpreter.destructureObject(pdpVariables);
            integrationTestFixture = integrationTestFixture
                    .withPDPVariables(packageJsonNodesInVal(pdpEnvironmentVariables));
        }

        if (integrationTestSuite.isCombiningAlgorithmDefined()) {
            final var pdpPolicyCombiningAlgorithm = combiningAlgorithmInterpreter
                    .interpretPdpCombiningAlgorithm(integrationTestSuite.getCombiningAlgorithm());
            integrationTestFixture.withPDPPolicyCombiningAlgorithm(pdpPolicyCombiningAlgorithm);
        }

        return integrationTestFixture;
    }

    private Map<String, Val> packageJsonNodesInVal(Map<String, JsonNode> originalMap) {
        var newMap = new HashMap<String, Val>();
        originalMap.forEach((key, json) -> newMap.put(key, Val.of(json)));
        return newMap;
    }

    private SaplIntegrationTestFixture handlePoliciesByIdentifier(final PoliciesByIdentifier policiesByIdentifier) {
        SaplIntegrationTestFixture integrationTestFixture;
        final var                  identifier = policiesByIdentifier.getIdentifier();

        if (customIntegrationTestPolicyResolver == null) {
            integrationTestFixture = SaplIntegrationTestFixtureFactory.create(identifier);
        } else {
            final var config = customIntegrationTestPolicyResolver.resolveConfigurationByIdentifier(identifier);
            integrationTestFixture = SaplIntegrationTestFixtureFactory
                    .createFromInputStrings(config.getDocumentInputStrings(), config.getPDPConfigurationInputString());
        }
        return integrationTestFixture;
    }

    private SaplIntegrationTestFixture handlePoliciesByInputString(final PoliciesByInputString policiesByInputString) {
        SaplIntegrationTestFixture integrationTestFixture;
        final var                  pdpConfig = policiesByInputString.getPdpConfiguration();
        final var                  policies  = policiesByInputString.getPolicies();

        if (policies == null || policies.size() < 2) {
            throw new SaplTestException("No policies to test integration for");
        }

        if (customIntegrationTestPolicyResolver == null) {
            integrationTestFixture = SaplIntegrationTestFixtureFactory.create(pdpConfig, policies);
        } else {
            final var saplDocumentStrings = policies.stream()
                    .map(customIntegrationTestPolicyResolver::resolvePolicyByIdentifier).toList();

            integrationTestFixture = SaplIntegrationTestFixtureFactory.createFromInputStrings(saplDocumentStrings,
                    customIntegrationTestPolicyResolver.resolvePDPConfigurationByIdentifier(pdpConfig));
        }
        return integrationTestFixture;
    }
}
