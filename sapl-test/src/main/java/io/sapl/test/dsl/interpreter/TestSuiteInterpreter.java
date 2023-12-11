/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.dsl.interfaces.IntegrationTestPolicyResolver;
import io.sapl.test.dsl.interfaces.UnitTestPolicyResolver;
import io.sapl.test.grammar.sAPLTest.IntegrationTestSuite;
import io.sapl.test.grammar.sAPLTest.PoliciesByIdentifier;
import io.sapl.test.grammar.sAPLTest.PoliciesByInputString;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.grammar.sAPLTest.UnitTestSuite;
import io.sapl.test.integration.SaplIntegrationTestFixture;
import java.util.Collections;
import java.util.Objects;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class TestSuiteInterpreter {

    private final ValInterpreter                   valInterpreter;
    private final PDPCombiningAlgorithmInterpreter pdpCombiningAlgorithmInterpreter;
    private final UnitTestPolicyResolver           customUnitTestPolicyResolver;
    private final IntegrationTestPolicyResolver    customIntegrationTestPolicyResolver;

    SaplTestFixture getFixtureFromTestSuite(final TestSuite testSuite,
            final io.sapl.test.grammar.sAPLTest.Object environment) {
        SaplTestFixture saplTestFixture;
        if (testSuite instanceof UnitTestSuite unitTestSuite) {
            saplTestFixture = getUnitTestFixtureFromUnitTestSuite(unitTestSuite);
        } else if (testSuite instanceof IntegrationTestSuite integrationTestSuite) {
            saplTestFixture = getIntegrationTestFixtureFromIntegrationTestSuite(integrationTestSuite);
        } else {
            throw new SaplTestException("Unknown type of TestSuite");
        }

        final var environmentVariables = valInterpreter.destructureObject(environment);

        if (environmentVariables != null) {
            environmentVariables.forEach(saplTestFixture::registerVariable);
        }

        return saplTestFixture;
    }

    private SaplTestFixture getUnitTestFixtureFromUnitTestSuite(final UnitTestSuite unitTestSuite) {
        if (customUnitTestPolicyResolver == null) {
            return SaplUnitTestFixtureFactory.create(unitTestSuite.getId());
        } else {
            return SaplUnitTestFixtureFactory.createFromInputString(
                    customUnitTestPolicyResolver.resolvePolicyByIdentifier(unitTestSuite.getId()));
        }
    }

    private SaplTestFixture getIntegrationTestFixtureFromIntegrationTestSuite(
            final IntegrationTestSuite integrationTestSuite) {
        final var                  policyResolverConfig = integrationTestSuite.getConfig();
        SaplIntegrationTestFixture integrationTestFixture;

        if (policyResolverConfig instanceof PoliciesByIdentifier policiesByIdentifier) {
            final var identifier = policiesByIdentifier.getIdentifier();

            if (customIntegrationTestPolicyResolver == null) {
                integrationTestFixture = SaplIntegrationTestFixtureFactory.create(identifier);
            } else {
                final var config = customIntegrationTestPolicyResolver.resolveConfigByIdentifier(identifier);
                integrationTestFixture = SaplIntegrationTestFixtureFactory
                        .createFromInputStrings(config.getDocumentInputStrings(), config.getPDPConfigInputString());
            }
        } else if (policyResolverConfig instanceof PoliciesByInputString policiesByInputString) {
            if (customIntegrationTestPolicyResolver == null) {
                integrationTestFixture = SaplIntegrationTestFixtureFactory.create(policiesByInputString.getPdpConfig(),
                        policiesByInputString.getPolicies());
            } else {
                final var pdpConfig = customIntegrationTestPolicyResolver
                        .resolvePDPConfigByIdentifier(policiesByInputString.getPdpConfig());
                final var policies  = Objects.requireNonNullElse(policiesByInputString.getPolicies(),
                        Collections.<String>emptyList());

                final var saplDocumentStrings = policies.stream()
                        .map(customIntegrationTestPolicyResolver::resolvePolicyByIdentifier).toList();

                integrationTestFixture = SaplIntegrationTestFixtureFactory.createFromInputStrings(saplDocumentStrings,
                        pdpConfig);
            }
        } else {
            throw new SaplTestException("Unknown type of PolicyResolverConfig");
        }

        if (integrationTestSuite.getPdpVariables() instanceof io.sapl.test.grammar.sAPLTest.Object pdpVariables) {
            final var pdpEnvironmentVariables = valInterpreter.destructureObject(pdpVariables);
            integrationTestFixture = integrationTestFixture.withPDPVariables(pdpEnvironmentVariables);
        }

        if (integrationTestSuite.isCombiningAlgorithmDefined()) {
            final var pdpPolicyCombiningAlgorithm = pdpCombiningAlgorithmInterpreter
                    .interpretPdpCombiningAlgorithm(integrationTestSuite.getCombiningAlgorithm());
            integrationTestFixture.withPDPPolicyCombiningAlgorithm(pdpPolicyCombiningAlgorithm);
        }

        return integrationTestFixture;
    }
}
