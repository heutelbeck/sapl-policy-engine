package io.sapl.test.dsl.interpreter;

import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.dsl.factories.SaplIntegrationTestFixtureFactory;
import io.sapl.test.dsl.factories.SaplUnitTestFixtureFactory;
import io.sapl.test.grammar.sAPLTest.IntegrationTestSuite;
import io.sapl.test.grammar.sAPLTest.Object;
import io.sapl.test.grammar.sAPLTest.PolicyFolder;
import io.sapl.test.grammar.sAPLTest.PolicySet;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.grammar.sAPLTest.UnitTestSuite;
import io.sapl.test.integration.SaplIntegrationTestFixture;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TestSuiteInterpreter {

    private final ValInterpreter valInterpreter;
    private final PDPCombiningAlgorithmInterpreter pdpCombiningAlgorithmInterpreter;

    SaplTestFixture getFixtureFromTestSuite(final TestSuite testSuite, final Object environment) {
        SaplTestFixture saplTestFixture;
        if (testSuite instanceof UnitTestSuite unitTestSuite) {
            saplTestFixture = SaplUnitTestFixtureFactory.create(unitTestSuite.getId());
        } else if (testSuite instanceof IntegrationTestSuite integrationTestSuite) {
            final var policyResolverConfig = integrationTestSuite.getConfig();
            SaplIntegrationTestFixture integrationTestFixture;

            if (policyResolverConfig instanceof PolicyFolder policyFolderConfig) {
                integrationTestFixture = SaplIntegrationTestFixtureFactory.create(policyFolderConfig.getPolicyFolder());
            } else if (policyResolverConfig instanceof PolicySet policySetConfig) {
                integrationTestFixture = SaplIntegrationTestFixtureFactory.create(policySetConfig.getPdpConfig(), policySetConfig.getPolicies());
            } else {
                throw new SaplTestException("No valid Policy Resolver Config");
            }

            if (integrationTestSuite.getPdpVariables() instanceof Object pdpVariables) {
                final var pdpEnvironmentVariables = valInterpreter.destructureObject(pdpVariables);
                integrationTestFixture = integrationTestFixture.withPDPVariables(pdpEnvironmentVariables);
            }

            final var pdpCombiningAlgorithm = integrationTestSuite.getCombiningAlgorithm();
            if (pdpCombiningAlgorithm != null) {
                final var pdpPolicyCombiningAlgorithm = pdpCombiningAlgorithmInterpreter.interpretPdpCombiningAlgorithm(pdpCombiningAlgorithm);
                integrationTestFixture.withPDPPolicyCombiningAlgorithm(pdpPolicyCombiningAlgorithm);
            }

            saplTestFixture = integrationTestFixture;

        } else {
            throw new SaplTestException("Unknown type of TestSuite");
        }

        final var environmentVariables = valInterpreter.destructureObject(environment);

        if (environmentVariables != null) {
            environmentVariables.forEach(saplTestFixture::registerVariable);
        }

        return saplTestFixture;
    }
}
