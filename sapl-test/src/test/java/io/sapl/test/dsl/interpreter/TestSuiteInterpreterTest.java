package io.sapl.test.dsl.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import io.sapl.test.dsl.interfaces.IntegrationTestConfiguration;
import io.sapl.test.dsl.interfaces.IntegrationTestPolicyResolver;
import io.sapl.test.dsl.interfaces.UnitTestPolicyResolver;
import io.sapl.test.grammar.sAPLTest.CombiningAlgorithmEnum;
import io.sapl.test.grammar.sAPLTest.IntegrationTestSuite;
import io.sapl.test.grammar.sAPLTest.PoliciesByIdentifier;
import io.sapl.test.grammar.sAPLTest.PoliciesByInputString;
import io.sapl.test.grammar.sAPLTest.PolicyResolverConfig;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.grammar.sAPLTest.UnitTestSuite;
import io.sapl.test.grammar.sAPLTest.Value;
import io.sapl.test.integration.SaplIntegrationTestFixture;
import io.sapl.test.unit.SaplUnitTestFixture;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestSuiteInterpreterTest {
    @Mock
    private ValInterpreter valInterpreterMock;
    @Mock
    private PDPCombiningAlgorithmInterpreter pdpCombiningAlgorithmInterpreterMock;

    private final MockedStatic<SaplUnitTestFixtureFactory> saplUnitTestFixtureFactoryMockedStatic = mockStatic(SaplUnitTestFixtureFactory.class);
    private final MockedStatic<SaplIntegrationTestFixtureFactory> saplIntegrationTestFixtureFactoryMockedStatic = mockStatic(SaplIntegrationTestFixtureFactory.class);

    private TestSuiteInterpreter testSuiteInterpreter;

    @BeforeEach
    void setUp() {
        testSuiteInterpreter = new TestSuiteInterpreter(valInterpreterMock, pdpCombiningAlgorithmInterpreterMock, null, null);
    }

    @AfterEach
    void tearDown() {
        saplUnitTestFixtureFactoryMockedStatic.close();
        saplIntegrationTestFixtureFactoryMockedStatic.close();
    }

    private void constructTestSuiteInterpreterWithCustomResolvers(final UnitTestPolicyResolver customUnitTestPolicyResolver, final IntegrationTestPolicyResolver customIntegrationTestPolicyResolver) {
        testSuiteInterpreter = new TestSuiteInterpreter(valInterpreterMock, pdpCombiningAlgorithmInterpreterMock, customUnitTestPolicyResolver, customIntegrationTestPolicyResolver);
    }

    @Test
    void getFixtureFromTestSuite_handlesNullTestSuite_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class, () -> testSuiteInterpreter.getFixtureFromTestSuite(null, null));

        assertEquals("Unknown type of TestSuite", exception.getMessage());
    }


    @Test
    void getFixtureFromTestSuite_handlesUnknownTypeOfTestSuite_throwsSaplTestException() {
        final var testSuiteMock = mock(TestSuite.class);
        final var exception = assertThrows(SaplTestException.class, () -> testSuiteInterpreter.getFixtureFromTestSuite(testSuiteMock, null));

        assertEquals("Unknown type of TestSuite", exception.getMessage());
    }

    @Nested
    @DisplayName("Unit test cases")
    class UnitTestCases {
        @Test
        void getFixtureFromTestSuite_handlesNullEnvironmentVariables_returnsSaplUnitTestFixture() {
            final var unitTestSuiteMock = mock(UnitTestSuite.class);

            when(unitTestSuiteMock.getId()).thenReturn("fooPolicy");

            final var saplUnitTestFixtureMock = mock(SaplUnitTestFixture.class);
            saplUnitTestFixtureFactoryMockedStatic.when(() -> SaplUnitTestFixtureFactory.create("fooPolicy")).thenReturn(saplUnitTestFixtureMock);

            when(valInterpreterMock.destructureObject(null)).thenReturn(null);

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(unitTestSuiteMock, null);

            assertEquals(saplUnitTestFixtureMock, result);

            verifyNoInteractions(saplUnitTestFixtureMock);
        }

        @Test
        void getFixtureFromTestSuite_handlesEmptyEnvironmentVariables_returnsSaplUnitTestFixture() {
            final var unitTestSuiteMock = mock(UnitTestSuite.class);

            when(unitTestSuiteMock.getId()).thenReturn("fooPolicy");

            final var saplUnitTestFixtureMock = mock(SaplUnitTestFixture.class);
            saplUnitTestFixtureFactoryMockedStatic.when(() -> SaplUnitTestFixtureFactory.create("fooPolicy")).thenReturn(saplUnitTestFixtureMock);

            final var environmentMock = mock(io.sapl.test.grammar.sAPLTest.Object.class);
            when(valInterpreterMock.destructureObject(environmentMock)).thenReturn(Collections.emptyMap());

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(unitTestSuiteMock, environmentMock);

            assertEquals(saplUnitTestFixtureMock, result);

            verifyNoInteractions(saplUnitTestFixtureMock);
        }

        @Test
        void getFixtureFromTestSuite_handlesMultipleEnvironmentVariables_returnsSaplUnitTestFixture() {
            final var unitTestSuiteMock = mock(UnitTestSuite.class);

            when(unitTestSuiteMock.getId()).thenReturn("fooPolicy");

            final var saplUnitTestFixtureMock = mock(SaplUnitTestFixture.class);
            saplUnitTestFixtureFactoryMockedStatic.when(() -> SaplUnitTestFixtureFactory.create("fooPolicy")).thenReturn(saplUnitTestFixtureMock);


            final var environmentMock = mock(io.sapl.test.grammar.sAPLTest.Object.class);
            final var jsonNodeMock1 = mock(JsonNode.class);
            final var jsonNodeMock2 = mock(JsonNode.class);
            when(valInterpreterMock.destructureObject(environmentMock)).thenReturn(Map.of("key1", jsonNodeMock1, "key2", jsonNodeMock2));

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(unitTestSuiteMock, environmentMock);

            assertEquals(saplUnitTestFixtureMock, result);
            verify(saplUnitTestFixtureMock, times(1)).registerVariable("key1", jsonNodeMock1);
            verify(saplUnitTestFixtureMock, times(1)).registerVariable("key2", jsonNodeMock2);
        }

        @Test
        void getFixtureFromTestSuite_handlesMultipleEnvironmentVariablesAndCustomUnitTestPolicyResolver_returnsSaplUnitTestFixture() {
            final var customUnitTestPolicyResolver = mock(UnitTestPolicyResolver.class);
            constructTestSuiteInterpreterWithCustomResolvers(customUnitTestPolicyResolver, null);

            final var unitTestSuiteMock = mock(UnitTestSuite.class);

            when(unitTestSuiteMock.getId()).thenReturn("fooPolicy");

            when(customUnitTestPolicyResolver.resolvePolicyByIdentifier("fooPolicy")).thenReturn("resolvedPolicy");

            final var saplUnitTestFixtureMock = mock(SaplUnitTestFixture.class);
            saplUnitTestFixtureFactoryMockedStatic.when(() -> SaplUnitTestFixtureFactory.createFromInputString("resolvedPolicy")).thenReturn(saplUnitTestFixtureMock);


            final var environmentMock = mock(io.sapl.test.grammar.sAPLTest.Object.class);
            final var jsonNodeMock1 = mock(JsonNode.class);
            final var jsonNodeMock2 = mock(JsonNode.class);
            when(valInterpreterMock.destructureObject(environmentMock)).thenReturn(Map.of("key1", jsonNodeMock1, "key2", jsonNodeMock2));

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(unitTestSuiteMock, environmentMock);

            assertEquals(saplUnitTestFixtureMock, result);
            verify(saplUnitTestFixtureMock, times(1)).registerVariable("key1", jsonNodeMock1);
            verify(saplUnitTestFixtureMock, times(1)).registerVariable("key2", jsonNodeMock2);
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

            final var exception = assertThrows(SaplTestException.class, () -> testSuiteInterpreter.getFixtureFromTestSuite(integrationTestSuite, null));

            assertEquals("Unknown type of PolicyResolverConfig", exception.getMessage());
        }

        @Test
        void getFixtureFromTestSuite_handlesNullEnvironmentVariablesAndNullPdpVariablesForPolicySet_returnsSaplIntegrationTestFixture() {
            final var integrationTestSuite = mock(IntegrationTestSuite.class);

            final var policiesByInputStringMock = mock(PoliciesByInputString.class);
            when(policiesByInputStringMock.getPdpConfig()).thenReturn("fooFolder");
            when(policiesByInputStringMock.getPolicies()).thenReturn(null);
            when(integrationTestSuite.getConfig()).thenReturn(policiesByInputStringMock);

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            saplIntegrationTestFixtureFactoryMockedStatic.when(() -> SaplIntegrationTestFixtureFactory.create("fooFolder", null)).thenReturn(saplIntegrationTestFixtureMock);

            when(integrationTestSuite.getPdpVariables()).thenReturn(null);

            when(integrationTestSuite.isCombiningAlgorithmDefined()).thenReturn(false);

            when(valInterpreterMock.destructureObject(null)).thenReturn(null);

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(integrationTestSuite, null);

            assertEquals(saplIntegrationTestFixtureMock, result);

            verifyNoInteractions(saplIntegrationTestFixtureMock);
        }

        @Test
        void getFixtureFromTestSuite_handlesNullEnvironmentVariablesAndNullPdpVariablesForPolicySetWithCustomIntegrationTestPolicyResolver_returnsSaplIntegrationTestFixture() {
            final var integrationTestPolicyResolver = mock(IntegrationTestPolicyResolver.class);
            constructTestSuiteInterpreterWithCustomResolvers(null, integrationTestPolicyResolver);
            final var integrationTestSuite = mock(IntegrationTestSuite.class);

            final var policiesByInputStringMock = mock(PoliciesByInputString.class);
            when(policiesByInputStringMock.getPdpConfig()).thenReturn("fooFolder");
            final var policiesMock = Helper.mockEList(List.of("policy1", "policy2"));
            when(policiesByInputStringMock.getPolicies()).thenReturn(policiesMock);
            when(integrationTestSuite.getConfig()).thenReturn(policiesByInputStringMock);

            when(integrationTestPolicyResolver.resolvePDPConfigByIdentifier("fooFolder")).thenReturn("resolvedPdpConfig");

            when(integrationTestPolicyResolver.resolvePolicyByIdentifier("policy1")).thenReturn("resolvedPolicy1");
            when(integrationTestPolicyResolver.resolvePolicyByIdentifier("policy2")).thenReturn("resolvedPolicy2");

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            saplIntegrationTestFixtureFactoryMockedStatic.when(() -> SaplIntegrationTestFixtureFactory.createFromInputStrings(List.of("resolvedPolicy1", "resolvedPolicy2"), "resolvedPdpConfig")).thenReturn(saplIntegrationTestFixtureMock);

            when(integrationTestSuite.getPdpVariables()).thenReturn(null);

            when(integrationTestSuite.isCombiningAlgorithmDefined()).thenReturn(false);

            when(valInterpreterMock.destructureObject(null)).thenReturn(null);

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(integrationTestSuite, null);

            assertEquals(saplIntegrationTestFixtureMock, result);

            verifyNoInteractions(saplIntegrationTestFixtureMock);
        }

        @Test
        void getFixtureFromTestSuite_handlesNullEnvironmentVariablesAndNullPdpVariablesForPolicySetWithNullPoliciesAndWithCustomIntegrationTestPolicyResolver_returnsSaplIntegrationTestFixture() {
            final var integrationTestPolicyResolver = mock(IntegrationTestPolicyResolver.class);
            constructTestSuiteInterpreterWithCustomResolvers(null, integrationTestPolicyResolver);
            final var integrationTestSuite = mock(IntegrationTestSuite.class);

            final var policiesByInputStringMock = mock(PoliciesByInputString.class);
            when(policiesByInputStringMock.getPdpConfig()).thenReturn("fooFolder");
            when(policiesByInputStringMock.getPolicies()).thenReturn(null);
            when(integrationTestSuite.getConfig()).thenReturn(policiesByInputStringMock);

            when(integrationTestPolicyResolver.resolvePDPConfigByIdentifier("fooFolder")).thenReturn("resolvedPdpConfig");

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            saplIntegrationTestFixtureFactoryMockedStatic.when(() -> SaplIntegrationTestFixtureFactory.createFromInputStrings(Collections.emptyList(), "resolvedPdpConfig")).thenReturn(saplIntegrationTestFixtureMock);

            when(integrationTestSuite.getPdpVariables()).thenReturn(null);

            when(integrationTestSuite.isCombiningAlgorithmDefined()).thenReturn(false);

            when(valInterpreterMock.destructureObject(null)).thenReturn(null);

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(integrationTestSuite, null);

            assertEquals(saplIntegrationTestFixtureMock, result);

            verifyNoInteractions(saplIntegrationTestFixtureMock);
        }

        @Test
        void getFixtureFromTestSuite_handlesNullEnvironmentVariablesAndNullPdpVariables_returnsSaplIntegrationTestFixture() {
            final var integrationTestSuite = mock(IntegrationTestSuite.class);

            final var policiesByIdentifierMock = mock(PoliciesByIdentifier.class);
            when(policiesByIdentifierMock.getIdentifier()).thenReturn("fooFolder");
            when(integrationTestSuite.getConfig()).thenReturn(policiesByIdentifierMock);

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            saplIntegrationTestFixtureFactoryMockedStatic.when(() -> SaplIntegrationTestFixtureFactory.create("fooFolder")).thenReturn(saplIntegrationTestFixtureMock);

            when(integrationTestSuite.getPdpVariables()).thenReturn(null);

            when(integrationTestSuite.isCombiningAlgorithmDefined()).thenReturn(false);

            when(valInterpreterMock.destructureObject(null)).thenReturn(null);

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(integrationTestSuite, null);

            assertEquals(saplIntegrationTestFixtureMock, result);

            verifyNoInteractions(saplIntegrationTestFixtureMock);
        }

        @Test
        void getFixtureFromTestSuite_handlesNullEnvironmentVariablesAndNullPdpVariablesWithCustomIntegrationTestPolicyResolver_returnsSaplIntegrationTestFixture() {
            final var integrationTestPolicyResolver = mock(IntegrationTestPolicyResolver.class);
            constructTestSuiteInterpreterWithCustomResolvers(null, integrationTestPolicyResolver);

            final var integrationTestSuite = mock(IntegrationTestSuite.class);

            final var policiesByIdentifierMock = mock(PoliciesByIdentifier.class);
            when(policiesByIdentifierMock.getIdentifier()).thenReturn("fooFolder");
            when(integrationTestSuite.getConfig()).thenReturn(policiesByIdentifierMock);

            final var integrationTestConfigurationMock = mock(IntegrationTestConfiguration.class);
            when(integrationTestPolicyResolver.resolveConfigByIdentifier("fooFolder")).thenReturn(integrationTestConfigurationMock);

            when(integrationTestConfigurationMock.getDocumentInputStrings()).thenReturn(List.of("policy1", "policy2"));
            when(integrationTestConfigurationMock.getPDPConfigInputString()).thenReturn("pdpConfig");

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            saplIntegrationTestFixtureFactoryMockedStatic.when(() -> SaplIntegrationTestFixtureFactory.createFromInputStrings(List.of("policy1", "policy2"), "pdpConfig")).thenReturn(saplIntegrationTestFixtureMock);

            when(integrationTestSuite.getPdpVariables()).thenReturn(null);

            when(integrationTestSuite.isCombiningAlgorithmDefined()).thenReturn(false);

            when(valInterpreterMock.destructureObject(null)).thenReturn(null);

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(integrationTestSuite, null);

            assertEquals(saplIntegrationTestFixtureMock, result);

            verifyNoInteractions(saplIntegrationTestFixtureMock);
        }

        @Test
        void getFixtureFromTestSuite_handlesPdpCombiningAlgorithm_returnsSaplIntegrationTestFixture() {
            final var integrationTestSuite = mock(IntegrationTestSuite.class);

            final var policiesByIdentifierMock = mock(PoliciesByIdentifier.class);
            when(policiesByIdentifierMock.getIdentifier()).thenReturn("fooFolder");
            when(integrationTestSuite.getConfig()).thenReturn(policiesByIdentifierMock);

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            saplIntegrationTestFixtureFactoryMockedStatic.when(() -> SaplIntegrationTestFixtureFactory.create("fooFolder")).thenReturn(saplIntegrationTestFixtureMock);

            when(integrationTestSuite.getPdpVariables()).thenReturn(null);

            when(integrationTestSuite.isCombiningAlgorithmDefined()).thenReturn(true);
            when(integrationTestSuite.getCombiningAlgorithm()).thenReturn(CombiningAlgorithmEnum.ONLY_ONE_APPLICABLE);

            final var pdpCombiningAlgorithmMock = mock(PolicyDocumentCombiningAlgorithm.class);
            when(pdpCombiningAlgorithmInterpreterMock.interpretPdpCombiningAlgorithm(CombiningAlgorithmEnum.ONLY_ONE_APPLICABLE)).thenReturn(pdpCombiningAlgorithmMock);

            when(saplIntegrationTestFixtureMock.withPDPPolicyCombiningAlgorithm(pdpCombiningAlgorithmMock)).thenReturn(saplIntegrationTestFixtureMock);

            when(valInterpreterMock.destructureObject(null)).thenReturn(null);

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(integrationTestSuite, null);

            assertEquals(saplIntegrationTestFixtureMock, result);

            verify(saplIntegrationTestFixtureMock, times(1)).withPDPPolicyCombiningAlgorithm(pdpCombiningAlgorithmMock);

            verifyNoMoreInteractions(saplIntegrationTestFixtureMock);
        }

        @Test
        void getFixtureFromTestSuite_handlesEmptyEnvironmentVariablesAndWrongPdpVariablesType_returnsSaplIntegrationTestFixture() {
            final var integrationTestSuite = mock(IntegrationTestSuite.class);

            final var policiesByIdentifierMock = mock(PoliciesByIdentifier.class);
            when(policiesByIdentifierMock.getIdentifier()).thenReturn("fooFolder");
            when(integrationTestSuite.getConfig()).thenReturn(policiesByIdentifierMock);

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            saplIntegrationTestFixtureFactoryMockedStatic.when(() -> SaplIntegrationTestFixtureFactory.create("fooFolder")).thenReturn(saplIntegrationTestFixtureMock);

            final var pdpVariablesMock = mock(Value.class);
            when(integrationTestSuite.getPdpVariables()).thenReturn(pdpVariablesMock);

            when(integrationTestSuite.isCombiningAlgorithmDefined()).thenReturn(false);

            final var environmentMock = mock(io.sapl.test.grammar.sAPLTest.Object.class);
            when(valInterpreterMock.destructureObject(environmentMock)).thenReturn(Collections.emptyMap());

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(integrationTestSuite, environmentMock);

            assertEquals(saplIntegrationTestFixtureMock, result);

            verifyNoInteractions(saplIntegrationTestFixtureMock);
        }

        @Test
        void getFixtureFromTestSuite_handlesMultipleEnvironmentVariablesAndMultiplePdpVariables_returnsSaplIntegrationTestFixture() {
            final var integrationTestSuite = mock(IntegrationTestSuite.class);

            final var policiesByIdentifierMock = mock(PoliciesByIdentifier.class);
            when(policiesByIdentifierMock.getIdentifier()).thenReturn("fooFolder");
            when(integrationTestSuite.getConfig()).thenReturn(policiesByIdentifierMock);

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            saplIntegrationTestFixtureFactoryMockedStatic.when(() -> SaplIntegrationTestFixtureFactory.create("fooFolder")).thenReturn(saplIntegrationTestFixtureMock);

            final var pdpVariablesMock = mock(io.sapl.test.grammar.sAPLTest.Object.class);
            when(integrationTestSuite.getPdpVariables()).thenReturn(pdpVariablesMock);

            when(integrationTestSuite.isCombiningAlgorithmDefined()).thenReturn(false);

            final var pdpEnvironmentVariablesMock = Collections.<String, JsonNode>emptyMap();
            when(valInterpreterMock.destructureObject(pdpVariablesMock)).thenReturn(pdpEnvironmentVariablesMock);

            when(saplIntegrationTestFixtureMock.withPDPVariables(pdpEnvironmentVariablesMock)).thenReturn(saplIntegrationTestFixtureMock);

            final var environmentMock = mock(io.sapl.test.grammar.sAPLTest.Object.class);
            final var jsonNodeMock1 = mock(JsonNode.class);
            final var jsonNodeMock2 = mock(JsonNode.class);
            when(valInterpreterMock.destructureObject(environmentMock)).thenReturn(Map.of("key1", jsonNodeMock1, "key2", jsonNodeMock2));

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(integrationTestSuite, environmentMock);

            assertEquals(saplIntegrationTestFixtureMock, result);

            verify(saplIntegrationTestFixtureMock, times(1)).withPDPVariables(pdpEnvironmentVariablesMock);
            verify(saplIntegrationTestFixtureMock, times(1)).registerVariable("key1", jsonNodeMock1);
            verify(saplIntegrationTestFixtureMock, times(1)).registerVariable("key2", jsonNodeMock2);
        }
    }
}