package io.sapl.test.services;

import io.sapl.test.SaplTestFixture;
import io.sapl.test.grammar.sAPLTest.IntegrationTestSuite;
import io.sapl.test.grammar.sAPLTest.Object;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.grammar.sAPLTest.UnitTestSuite;
import io.sapl.test.services.constructorwrappers.SaplIntegrationTestFixtureConstructorWrapper;
import io.sapl.test.services.constructorwrappers.SaplUnitTestFixtureConstructorWrapper;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TestSuiteInterpreter {

    private final ValInterpreter valInterpreter;

    private final SaplUnitTestFixtureConstructorWrapper saplUnitTestFixtureConstructorWrapper;
    private final SaplIntegrationTestFixtureConstructorWrapper saplIntegrationTestFixtureConstructorWrapper;

    SaplTestFixture getFixtureFromTestSuite(final TestSuite testSuite, final Object environment) {
        SaplTestFixture saplTestFixture;
        if (testSuite instanceof UnitTestSuite unitTestSuite) {
            saplTestFixture = saplUnitTestFixtureConstructorWrapper.create(unitTestSuite.getPolicy());
        } else if (testSuite instanceof IntegrationTestSuite integrationTestSuite) {
            var integrationTestFixture = saplIntegrationTestFixtureConstructorWrapper.create(integrationTestSuite.getPolicyFolder());

            if (integrationTestSuite.getPdpVariables() instanceof Object pdpVariables) {
                final var pdpEnvironmentVariables = valInterpreter.destructureObject(pdpVariables);
                integrationTestFixture = integrationTestFixture.withPDPVariables(pdpEnvironmentVariables);
            }

            saplTestFixture = integrationTestFixture;

        } else {
            throw new RuntimeException("Unsupported type of TestSuite");
        }
        final var environmentVariables = valInterpreter.destructureObject(environment);
        if (environmentVariables != null) {
            environmentVariables.forEach(saplTestFixture::registerVariable);
        }
        return saplTestFixture;
    }
}
