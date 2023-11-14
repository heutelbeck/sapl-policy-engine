package io.sapl.test.dsl.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interpreter.constructorwrappers.SaplIntegrationTestFixtureConstructorWrapper;
import io.sapl.test.dsl.interpreter.constructorwrappers.SaplUnitTestFixtureConstructorWrapper;
import io.sapl.test.grammar.sAPLTest.CombiningAlgorithm;
import io.sapl.test.grammar.sAPLTest.IntegrationTestSuite;
import io.sapl.test.grammar.sAPLTest.Object;
import io.sapl.test.grammar.sAPLTest.PolicyFolder;
import io.sapl.test.grammar.sAPLTest.PolicyResolverConfig;
import io.sapl.test.grammar.sAPLTest.PolicySet;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.grammar.sAPLTest.UnitTestSuite;
import io.sapl.test.grammar.sAPLTest.Value;
import io.sapl.test.integration.SaplIntegrationTestFixture;
import io.sapl.test.unit.SaplUnitTestFixture;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestSuiteInterpreterTest {
    @Mock
    private ValInterpreter valInterpreterMock;
    @Mock
    private PDPCombiningAlgorithmInterpreter pdpCombiningAlgorithmInterpreterMock;
    @Mock
    private SaplUnitTestFixtureConstructorWrapper saplUnitTestFixtureConstructorWrapperMocK;
    @Mock
    private SaplIntegrationTestFixtureConstructorWrapper saplIntegrationTestFixtureConstructorWrapperMock;
    @InjectMocks
    private TestSuiteInterpreter testSuiteInterpreter;

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
            when(saplUnitTestFixtureConstructorWrapperMocK.create("fooPolicy")).thenReturn(saplUnitTestFixtureMock);

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
            when(saplUnitTestFixtureConstructorWrapperMocK.create("fooPolicy")).thenReturn(saplUnitTestFixtureMock);

            final var environmentMock = mock(Object.class);
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
            when(saplUnitTestFixtureConstructorWrapperMocK.create("fooPolicy")).thenReturn(saplUnitTestFixtureMock);


            final var environmentMock = mock(Object.class);
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

            assertEquals("No valid Policy Resolver Config", exception.getMessage());
        }

        @Test
        void getFixtureFromTestSuite_handlesNullEnvironmentVariablesAndNullPdpVariablesForPolicySet_returnsSaplIntegrationTestFixture() {
            final var integrationTestSuite = mock(IntegrationTestSuite.class);

            final var policySetConfigMock = mock(PolicySet.class);
            when(policySetConfigMock.getPdpConfig()).thenReturn("fooFolder");
            when(policySetConfigMock.getPolicies()).thenReturn(null);
            when(integrationTestSuite.getConfig()).thenReturn(policySetConfigMock);

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            when(saplIntegrationTestFixtureConstructorWrapperMock.create("fooFolder", null)).thenReturn(saplIntegrationTestFixtureMock);

            when(integrationTestSuite.getPdpVariables()).thenReturn(null);

            when(integrationTestSuite.getCombiningAlgorithm()).thenReturn(null);

            when(valInterpreterMock.destructureObject(null)).thenReturn(null);

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(integrationTestSuite, null);

            assertEquals(saplIntegrationTestFixtureMock, result);

            verifyNoInteractions(saplIntegrationTestFixtureMock);
        }

        @Test
        void getFixtureFromTestSuite_handlesNullEnvironmentVariablesAndNullPdpVariables_returnsSaplIntegrationTestFixture() {
            final var integrationTestSuite = mock(IntegrationTestSuite.class);

            final var policyFolderConfig = mock(PolicyFolder.class);
            when(policyFolderConfig.getPolicyFolder()).thenReturn("fooFolder");
            when(integrationTestSuite.getConfig()).thenReturn(policyFolderConfig);

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            when(saplIntegrationTestFixtureConstructorWrapperMock.create("fooFolder")).thenReturn(saplIntegrationTestFixtureMock);

            when(integrationTestSuite.getPdpVariables()).thenReturn(null);

            when(integrationTestSuite.getCombiningAlgorithm()).thenReturn(null);

            when(valInterpreterMock.destructureObject(null)).thenReturn(null);

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(integrationTestSuite, null);

            assertEquals(saplIntegrationTestFixtureMock, result);

            verifyNoInteractions(saplIntegrationTestFixtureMock);
        }

        @Test
        void getFixtureFromTestSuite_handlesPdpCombiningAlgorithm_returnsSaplIntegrationTestFixture() {
            final var integrationTestSuite = mock(IntegrationTestSuite.class);

            final var policyFolderConfig = mock(PolicyFolder.class);
            when(policyFolderConfig.getPolicyFolder()).thenReturn("fooFolder");
            when(integrationTestSuite.getConfig()).thenReturn(policyFolderConfig);

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            when(saplIntegrationTestFixtureConstructorWrapperMock.create("fooFolder")).thenReturn(saplIntegrationTestFixtureMock);

            when(integrationTestSuite.getPdpVariables()).thenReturn(null);

            final var combiningAlgorithmMock = mock(CombiningAlgorithm.class);
            when(integrationTestSuite.getCombiningAlgorithm()).thenReturn(combiningAlgorithmMock);

            final var pdpCombiningAlgorithmMock = mock(PolicyDocumentCombiningAlgorithm.class);
            when(pdpCombiningAlgorithmInterpreterMock.interpretPdpCombiningAlgorithm(combiningAlgorithmMock)).thenReturn(pdpCombiningAlgorithmMock);

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

            final var policyFolderConfig = mock(PolicyFolder.class);
            when(policyFolderConfig.getPolicyFolder()).thenReturn("fooFolder");
            when(integrationTestSuite.getConfig()).thenReturn(policyFolderConfig);

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            when(saplIntegrationTestFixtureConstructorWrapperMock.create("fooFolder")).thenReturn(saplIntegrationTestFixtureMock);

            final var pdpVariablesMock = mock(Value.class);
            when(integrationTestSuite.getPdpVariables()).thenReturn(pdpVariablesMock);

            when(integrationTestSuite.getCombiningAlgorithm()).thenReturn(null);

            final var environmentMock = mock(Object.class);
            when(valInterpreterMock.destructureObject(environmentMock)).thenReturn(Collections.emptyMap());

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(integrationTestSuite, environmentMock);

            assertEquals(saplIntegrationTestFixtureMock, result);

            verifyNoInteractions(saplIntegrationTestFixtureMock);
        }

        @Test
        void getFixtureFromTestSuite_handlesMultipleEnvironmentVariablesAndMultiplePdpVariables_returnsSaplIntegrationTestFixture() {
            final var integrationTestSuite = mock(IntegrationTestSuite.class);

            final var policyFolderConfig = mock(PolicyFolder.class);
            when(policyFolderConfig.getPolicyFolder()).thenReturn("fooFolder");
            when(integrationTestSuite.getConfig()).thenReturn(policyFolderConfig);

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            when(saplIntegrationTestFixtureConstructorWrapperMock.create("fooFolder")).thenReturn(saplIntegrationTestFixtureMock);

            final var pdpVariablesMock = mock(Object.class);
            when(integrationTestSuite.getPdpVariables()).thenReturn(pdpVariablesMock);

            when(integrationTestSuite.getCombiningAlgorithm()).thenReturn(null);

            final var pdpEnvironmentVariablesMock = Collections.<String, JsonNode>emptyMap();
            when(valInterpreterMock.destructureObject(pdpVariablesMock)).thenReturn(pdpEnvironmentVariablesMock);

            when(saplIntegrationTestFixtureMock.withPDPVariables(pdpEnvironmentVariablesMock)).thenReturn(saplIntegrationTestFixtureMock);

            final var environmentMock = mock(Object.class);
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