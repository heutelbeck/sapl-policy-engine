package io.sapl.test.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.test.grammar.sAPLTest.IntegrationTestSuite;
import io.sapl.test.grammar.sAPLTest.Object;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.grammar.sAPLTest.UnitTestSuite;
import io.sapl.test.grammar.sAPLTest.Value;
import io.sapl.test.integration.SaplIntegrationTestFixture;
import io.sapl.test.services.constructorwrappers.SaplIntegrationTestFixtureConstructorWrapper;
import io.sapl.test.services.constructorwrappers.SaplUnitTestFixtureConstructorWrapper;
import io.sapl.test.unit.SaplUnitTestFixture;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TestSuiteInterpreterTest {

    private ValInterpreter valInterpreterMock;

    private SaplUnitTestFixtureConstructorWrapper saplUnitTestFixtureConstructorWrapperMocK;
    private SaplIntegrationTestFixtureConstructorWrapper saplIntegrationTestFixtureConstructorWrapperMock;

    private TestSuiteInterpreter testSuiteInterpreter;

    @BeforeEach
    void setUp() {
        valInterpreterMock = mock(ValInterpreter.class);
        saplUnitTestFixtureConstructorWrapperMocK = mock(SaplUnitTestFixtureConstructorWrapper.class);
        saplIntegrationTestFixtureConstructorWrapperMock = mock(SaplIntegrationTestFixtureConstructorWrapper.class);
        testSuiteInterpreter = new TestSuiteInterpreter(valInterpreterMock, saplUnitTestFixtureConstructorWrapperMocK, saplIntegrationTestFixtureConstructorWrapperMock);
    }

    @Test
    void getFixtureFromTestSuite_handlesNullTestSuite_throwsRuntimeException() {

        final var exception = assertThrows(RuntimeException.class, () -> testSuiteInterpreter.getFixtureFromTestSuite(null, null));

        assertEquals("Unsupported type of TestSuite", exception.getMessage());
    }


    @Test
    void getFixtureFromTestSuite_handlesUnknownTypeOfTestSuite_throwsRuntimeException() {
        final var testSuiteMock = mock(TestSuite.class);
        final var exception = assertThrows(RuntimeException.class, () -> testSuiteInterpreter.getFixtureFromTestSuite(testSuiteMock, null));

        assertEquals("Unsupported type of TestSuite", exception.getMessage());
    }

    @Nested
    @DisplayName("Unit test cases")
    class UnitTestCases {
        @Test
        void getFixtureFromTestSuite_handlesNullEnvironmentVariables_returnsSaplUnitTestFixture() {
            final var unitTestSuiteMock = mock(UnitTestSuite.class);

            when(unitTestSuiteMock.getPolicy()).thenReturn("fooPolicy");

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

            when(unitTestSuiteMock.getPolicy()).thenReturn("fooPolicy");

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

            when(unitTestSuiteMock.getPolicy()).thenReturn("fooPolicy");

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
        void getFixtureFromTestSuite_handlesNullEnvironmentVariablesAndNullPdpVariables_returnsSaplIntegrationTestFixture() {
            final var integrationTestSuite = mock(IntegrationTestSuite.class);

            when(integrationTestSuite.getPolicyFolder()).thenReturn("fooFolder");

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            when(saplIntegrationTestFixtureConstructorWrapperMock.create("fooFolder")).thenReturn(saplIntegrationTestFixtureMock);

            when(integrationTestSuite.getPdpVariables()).thenReturn(null);

            when(valInterpreterMock.destructureObject(null)).thenReturn(null);

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(integrationTestSuite, null);

            assertEquals(saplIntegrationTestFixtureMock, result);

            verifyNoInteractions(saplIntegrationTestFixtureMock);
        }

        @Test
        void getFixtureFromTestSuite_handlesEmptyEnvironmentVariablesAndWrongPdpVariablesType_returnsSaplIntegrationTestFixture() {
            final var integrationTestSuite = mock(IntegrationTestSuite.class);

            when(integrationTestSuite.getPolicyFolder()).thenReturn("fooFolder");

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            when(saplIntegrationTestFixtureConstructorWrapperMock.create("fooFolder")).thenReturn(saplIntegrationTestFixtureMock);

            final var pdpVariablesMock = mock(Value.class);
            when(integrationTestSuite.getPdpVariables()).thenReturn(pdpVariablesMock);

            final var environmentMock = mock(Object.class);
            when(valInterpreterMock.destructureObject(environmentMock)).thenReturn(Collections.emptyMap());

            final var result = testSuiteInterpreter.getFixtureFromTestSuite(integrationTestSuite, environmentMock);

            assertEquals(saplIntegrationTestFixtureMock, result);

            verifyNoInteractions(saplIntegrationTestFixtureMock);
        }

        @Test
        void getFixtureFromTestSuite_handlesMultipleEnvironmentVariablesAndMultiplePdpVariables_returnsSaplIntegrationTestFixture() {

            final var integrationTestSuite = mock(IntegrationTestSuite.class);

            when(integrationTestSuite.getPolicyFolder()).thenReturn("fooFolder");

            final var saplIntegrationTestFixtureMock = mock(SaplIntegrationTestFixture.class);
            when(saplIntegrationTestFixtureConstructorWrapperMock.create("fooFolder")).thenReturn(saplIntegrationTestFixtureMock);

            final var pdpVariablesMock = mock(Object.class);
            when(integrationTestSuite.getPdpVariables()).thenReturn(pdpVariablesMock);

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