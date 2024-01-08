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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.test.Helper;
import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.ParserUtil;
import io.sapl.test.dsl.interfaces.IntegrationTestConfiguration;
import io.sapl.test.dsl.interfaces.IntegrationTestPolicyResolver;
import io.sapl.test.dsl.interfaces.UnitTestPolicyResolver;
import io.sapl.test.grammar.sAPLTest.CombiningAlgorithmEnum;
import io.sapl.test.grammar.sAPLTest.IntegrationTestSuite;
import io.sapl.test.grammar.sAPLTest.PoliciesByIdentifier;
import io.sapl.test.grammar.sAPLTest.PoliciesByInputString;
import io.sapl.test.grammar.sAPLTest.PolicyResolverConfig;
import io.sapl.test.grammar.sAPLTest.StringLiteral;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.grammar.sAPLTest.Value;
import io.sapl.test.grammar.services.SAPLTestGrammarAccess;
import io.sapl.test.integration.SaplIntegrationTestFixture;
import io.sapl.test.unit.SaplUnitTestFixture;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.eclipse.emf.common.util.EList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestSuiteInterpreterTest {
    @Mock
    private ValInterpreter                   valInterpreterMock;
    @Mock
    private PDPCombiningAlgorithmInterpreter pdpCombiningAlgorithmInterpreterMock;

    private final MockedStatic<SaplUnitTestFixtureFactory>        saplUnitTestFixtureFactoryMockedStatic        = mockStatic(
            SaplUnitTestFixtureFactory.class);
    private final MockedStatic<SaplIntegrationTestFixtureFactory> saplIntegrationTestFixtureFactoryMockedStatic = mockStatic(
            SaplIntegrationTestFixtureFactory.class);

    private TestSuiteInterpreter testSuiteInterpreter;

    @BeforeEach
    void setUp() {
        testSuiteInterpreter = new TestSuiteInterpreter(valInterpreterMock, pdpCombiningAlgorithmInterpreterMock, null,
                null);
    }

    @AfterEach
    void tearDown() {
        saplUnitTestFixtureFactoryMockedStatic.close();
        saplIntegrationTestFixtureFactoryMockedStatic.close();
    }

    private <T extends TestSuite> T buildTestSuite(final String input) {
        return ParserUtil.buildExpression(input, SAPLTestGrammarAccess::getTestSuiteRule);
    }

    private void constructTestSuiteInterpreterWithCustomResolvers(
            final UnitTestPolicyResolver customUnitTestPolicyResolver,
            final IntegrationTestPolicyResolver customIntegrationTestPolicyResolver) {
        testSuiteInterpreter = new TestSuiteInterpreter(valInterpreterMock, pdpCombiningAlgorithmInterpreterMock,
                customUnitTestPolicyResolver, customIntegrationTestPolicyResolver);
    }

    @Test
    void getFixtureFromTestSuite_handlesNullTestSuite_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> testSuiteInterpreter.getFixtureFromTestSuite(null));

        assertEquals("Unknown type of TestSuite", exception.getMessage());
    }

    @Test
    void getFixtureFromTestSuite_handlesUnknownTypeOfTestSuite_throwsSaplTestException() {
        final var testSuiteMock = mock(TestSuite.class);
        final var exception     = assertThrows(SaplTestException.class,
                () -> testSuiteInterpreter.getFixtureFromTestSuite(testSuiteMock));

        assertEquals("Unknown type of TestSuite", exception.getMessage());
    }

    @Nested
    @DisplayName("Unit test cases")
    class UnitTestCases {
        @Test
        void getFixtureFromTestSuite_handlesDefaultUnitTestPolicyResolver_returnsSaplUnitTestFixture() {
            final var unitTestSuite = buildTestSuite(
                    "\"fooPolicy\" tests { scenario \"testCase\" when subject \"subject\" attempts action \"action\" on resource \"resource\" then expect single permit}");

            final var saplUnitTestFixtureMock = mock(SaplUnitTestFixture.class);
            saplUnitTestFixtureFactoryMockedStatic.when(() -> SaplUnitTestFixtureFactory.create("fooPolicy"))
                    .thenReturn(saplUnitTestFixtureMock);

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(unitTestSuite);

            assertEquals(saplUnitTestFixtureMock, result);
        }

        @Test
        void getFixtureFromTestSuite_handlesCustomUnitTestPolicyResolver_returnsSaplUnitTestFixture() {
            final var customUnitTestPolicyResolver = mock(UnitTestPolicyResolver.class);
            constructTestSuiteInterpreterWithCustomResolvers(customUnitTestPolicyResolver, null);

            final var unitTestSuite = buildTestSuite(
                    "\"fooPolicy\" tests { scenario \"testCase\" when subject \"subject\" attempts action \"action\" on resource \"resource\" then expect single permit}");

            when(customUnitTestPolicyResolver.resolvePolicyByIdentifier("fooPolicy")).thenReturn("resolvedPolicy");

            final var saplUnitTestFixtureMock = mock(SaplUnitTestFixture.class);
            saplUnitTestFixtureFactoryMockedStatic
                    .when(() -> SaplUnitTestFixtureFactory.createFromInputString("resolvedPolicy"))
                    .thenReturn(saplUnitTestFixtureMock);

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(unitTestSuite);

            assertEquals(saplUnitTestFixtureMock, result);
        }
    }

    @Nested
    @DisplayName("Integration test cases")
    class IntegrationTestCases {
        @Test
        void getFixtureFromTestSuite_handlesUnknownPolicyResolverConfig_throwsSaplTestException() {
            final var integrationTestSuite = mock(IntegrationTestSuite.class);

            final var unknownResolverConfig = mock(PolicyResolverConfig.class);
            when(integrationTestSuite.getConfig()).thenReturn(unknownResolverConfig);

            final var exception = assertThrows(SaplTestException.class,
                    () -> testSuiteInterpreter.getFixtureFromTestSuite(integrationTestSuite));

            assertEquals("Unknown type of PolicyResolverConfig", exception.getMessage());
        }

        @ParameterizedTest
        @MethodSource("earlyReturnListOfPolicies")
        void getFixtureFromTestSuite_handlesInvalidAmountOfPoliciesForPoliciesByInputString_throwsSaplTestException(
                final EList<String> policies) {
            final var integrationTestSuite = mock(IntegrationTestSuite.class);

            final var policiesByInputStringMock = mock(PoliciesByInputString.class);
            when(policiesByInputStringMock.getPdpConfig()).thenReturn("fooFolder");
            when(policiesByInputStringMock.getPolicies()).thenReturn(policies);
            when(integrationTestSuite.getConfig()).thenReturn(policiesByInputStringMock);

            final var exception = assertThrows(SaplTestException.class,
                    () -> testSuiteInterpreter.getFixtureFromTestSuite(integrationTestSuite));

            assertEquals("No policies to test integration for", exception.getMessage());
        }

        @ParameterizedTest
        @MethodSource("earlyReturnListOfPolicies")
        void getFixtureFromTestSuite_handlesInvalidAmountOfPoliciesForPoliciesByInputStringWithCustomIntegrationTestPolicyResolver_throwsSaplTestException(
                final EList<String> policies) {
            final var integrationTestPolicyResolver = mock(IntegrationTestPolicyResolver.class);
            constructTestSuiteInterpreterWithCustomResolvers(null, integrationTestPolicyResolver);
            final var integrationTestSuite = mock(IntegrationTestSuite.class);

            final var policiesByInputStringMock = mock(PoliciesByInputString.class);
            when(policiesByInputStringMock.getPdpConfig()).thenReturn("fooFolder");
            when(policiesByInputStringMock.getPolicies()).thenReturn(policies);
            when(integrationTestSuite.getConfig()).thenReturn(policiesByInputStringMock);

            final var exception = assertThrows(SaplTestException.class,
                    () -> testSuiteInterpreter.getFixtureFromTestSuite(integrationTestSuite));

            assertEquals("No policies to test integration for", exception.getMessage());
        }

        private static Stream<Arguments> earlyReturnListOfPolicies() {
            return Stream.of(null, Arguments.of(Helper.mockEList(Collections.emptyList())),
                    Arguments.of(Helper.mockEList(List.of("singlePolicy"))));
        }

        @Test
        void getFixtureFromTestSuite_handlesNullPdpVariablesForPolicySet_returnsSaplIntegrationTestFixture() {
            final var integrationTestSuite = buildTestSuite(
                    "test set of policies \"policy1\", \"policy2\" with pdp config \"fooFolder\" { scenario \"testCase\" when subject \"subject\" attempts action \"action\" on resource \"resource\" then expect single permit}");

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            saplIntegrationTestFixtureFactoryMockedStatic
                    .when(() -> SaplIntegrationTestFixtureFactory.create("fooFolder", List.of("policy1", "policy2")))
                    .thenReturn(saplIntegrationTestFixtureMock);

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(integrationTestSuite);

            assertEquals(saplIntegrationTestFixtureMock, result);

            verifyNoInteractions(saplIntegrationTestFixtureMock);
        }

        @Test
        void getFixtureFromTestSuite_handlesNullPdpVariablesForPolicySetWithCustomIntegrationTestPolicyResolver_returnsSaplIntegrationTestFixture() {
            final var integrationTestPolicyResolver = mock(IntegrationTestPolicyResolver.class);
            constructTestSuiteInterpreterWithCustomResolvers(null, integrationTestPolicyResolver);

            final var integrationTestSuite = buildTestSuite(
                    "test set of policies \"policy1\", \"policy2\" with pdp config \"fooFolder\" { scenario \"testCase\" when subject \"subject\" attempts action \"action\" on resource \"resource\" then expect single permit}");

            when(integrationTestPolicyResolver.resolvePDPConfigByIdentifier("fooFolder"))
                    .thenReturn("resolvedPdpConfig");

            when(integrationTestPolicyResolver.resolvePolicyByIdentifier("policy1")).thenReturn("resolvedPolicy1");
            when(integrationTestPolicyResolver.resolvePolicyByIdentifier("policy2")).thenReturn("resolvedPolicy2");

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            saplIntegrationTestFixtureFactoryMockedStatic
                    .when(() -> SaplIntegrationTestFixtureFactory
                            .createFromInputStrings(List.of("resolvedPolicy1", "resolvedPolicy2"), "resolvedPdpConfig"))
                    .thenReturn(saplIntegrationTestFixtureMock);

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(integrationTestSuite);

            assertEquals(saplIntegrationTestFixtureMock, result);

            verifyNoInteractions(saplIntegrationTestFixtureMock);
        }

        @Test
        void getFixtureFromTestSuite_handlesNullPdpVariables_returnsSaplIntegrationTestFixture() {
            final var integrationTestSuite = buildTestSuite(
                    "test set of policies with identifier \"fooFolder\" { scenario \"testCase\" when subject \"subject\" attempts action \"action\" on resource \"resource\" then expect single permit}");

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            saplIntegrationTestFixtureFactoryMockedStatic
                    .when(() -> SaplIntegrationTestFixtureFactory.create("fooFolder"))
                    .thenReturn(saplIntegrationTestFixtureMock);

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(integrationTestSuite);

            assertEquals(saplIntegrationTestFixtureMock, result);

            verifyNoInteractions(saplIntegrationTestFixtureMock);
        }

        @Test
        void getFixtureFromTestSuite_handlesNullPdpVariablesWithCustomIntegrationTestPolicyResolver_returnsSaplIntegrationTestFixture() {
            final var integrationTestPolicyResolver = mock(IntegrationTestPolicyResolver.class);
            constructTestSuiteInterpreterWithCustomResolvers(null, integrationTestPolicyResolver);

            final var integrationTestSuite = buildTestSuite(
                    "test set of policies with identifier \"fooFolder\" { scenario \"testCase\" when subject \"subject\" attempts action \"action\" on resource \"resource\" then expect single permit}");

            final var integrationTestConfigurationMock = mock(IntegrationTestConfiguration.class);
            when(integrationTestPolicyResolver.resolveConfigByIdentifier("fooFolder"))
                    .thenReturn(integrationTestConfigurationMock);

            when(integrationTestConfigurationMock.getDocumentInputStrings()).thenReturn(List.of("policy1", "policy2"));
            when(integrationTestConfigurationMock.getPDPConfigInputString()).thenReturn("pdpConfig");

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            saplIntegrationTestFixtureFactoryMockedStatic.when(() -> SaplIntegrationTestFixtureFactory
                    .createFromInputStrings(List.of("policy1", "policy2"), "pdpConfig"))
                    .thenReturn(saplIntegrationTestFixtureMock);

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(integrationTestSuite);

            assertEquals(saplIntegrationTestFixtureMock, result);

            verifyNoInteractions(saplIntegrationTestFixtureMock);
        }

        @Test
        void getFixtureFromTestSuite_handlesPdpCombiningAlgorithm_returnsSaplIntegrationTestFixture() {
            final var integrationTestSuite = buildTestSuite(
                    "test set of policies with identifier \"fooFolder\" using combining-algorithm only-one-applicable { scenario \"testCase\" when subject \"subject\" attempts action \"action\" on resource \"resource\" then expect single permit}");

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            saplIntegrationTestFixtureFactoryMockedStatic
                    .when(() -> SaplIntegrationTestFixtureFactory.create("fooFolder"))
                    .thenReturn(saplIntegrationTestFixtureMock);

            final var pdpCombiningAlgorithmMock = mock(PolicyDocumentCombiningAlgorithm.class);
            when(pdpCombiningAlgorithmInterpreterMock
                    .interpretPdpCombiningAlgorithm(CombiningAlgorithmEnum.ONLY_ONE_APPLICABLE))
                    .thenReturn(pdpCombiningAlgorithmMock);

            when(saplIntegrationTestFixtureMock.withPDPPolicyCombiningAlgorithm(pdpCombiningAlgorithmMock))
                    .thenReturn(saplIntegrationTestFixtureMock);

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(integrationTestSuite);

            assertEquals(saplIntegrationTestFixtureMock, result);

            verify(saplIntegrationTestFixtureMock, times(1)).withPDPPolicyCombiningAlgorithm(pdpCombiningAlgorithmMock);

            verifyNoMoreInteractions(saplIntegrationTestFixtureMock);
        }

        @Test
        void getFixtureFromTestSuite_handlesWrongPdpVariablesType_returnsSaplIntegrationTestFixture() {
            final var integrationTestSuite = mock(IntegrationTestSuite.class);

            final var policiesByIdentifierMock = mock(PoliciesByIdentifier.class);
            when(policiesByIdentifierMock.getIdentifier()).thenReturn("fooFolder");
            when(integrationTestSuite.getConfig()).thenReturn(policiesByIdentifierMock);

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            saplIntegrationTestFixtureFactoryMockedStatic
                    .when(() -> SaplIntegrationTestFixtureFactory.create("fooFolder"))
                    .thenReturn(saplIntegrationTestFixtureMock);

            final var pdpVariablesMock = mock(Value.class);
            when(integrationTestSuite.getPdpVariables()).thenReturn(pdpVariablesMock);

            when(integrationTestSuite.isCombiningAlgorithmDefined()).thenReturn(false);

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(integrationTestSuite);

            assertEquals(saplIntegrationTestFixtureMock, result);

            verifyNoInteractions(saplIntegrationTestFixtureMock);
        }

        @Test
        void getFixtureFromTestSuite_handlesMultiplePdpVariables_returnsSaplIntegrationTestFixture() {
            final var integrationTestSuite = buildTestSuite(
                    "test set of policies with identifier \"fooFolder\" using variables { \"foo\" : \"bar\" } { scenario \"testCase\" when subject \"subject\" attempts action \"action\" on resource \"resource\" then expect single permit}");

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            saplIntegrationTestFixtureFactoryMockedStatic
                    .when(() -> SaplIntegrationTestFixtureFactory.create("fooFolder"))
                    .thenReturn(saplIntegrationTestFixtureMock);

            final var pdpEnvironmentVariablesMock = Collections.<String, JsonNode>emptyMap();
            when(valInterpreterMock.destructureObject(any())).thenAnswer(invocationOnMock -> {
                final io.sapl.test.grammar.sAPLTest.Object environment = invocationOnMock.getArgument(0);

                assertEquals(1, environment.getMembers().size());
                final var pair = environment.getMembers().get(0);
                assertEquals("foo", pair.getKey());
                assertEquals("bar", ((StringLiteral) pair.getValue()).getString());
                return pdpEnvironmentVariablesMock;
            });

            when(saplIntegrationTestFixtureMock.withPDPVariables(pdpEnvironmentVariablesMock))
                    .thenReturn(saplIntegrationTestFixtureMock);

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(integrationTestSuite);

            assertEquals(saplIntegrationTestFixtureMock, result);

            verify(saplIntegrationTestFixtureMock, times(1)).withPDPVariables(pdpEnvironmentVariablesMock);
        }
    }
}
