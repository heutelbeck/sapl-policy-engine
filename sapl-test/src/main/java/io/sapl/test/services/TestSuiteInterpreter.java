package io.sapl.test.services;

import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.grammar.sAPLTest.Object;
import io.sapl.test.grammar.sAPLTest.*;
import io.sapl.test.integration.SaplIntegrationTestFixture;
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
            final var policyResolverConfig = integrationTestSuite.getConfig();
            SaplIntegrationTestFixture integrationTestFixture;

            if (policyResolverConfig instanceof PolicyFolder policyFolderConfig) {
                integrationTestFixture = saplIntegrationTestFixtureConstructorWrapper.create(policyFolderConfig.getPolicyFolder());
            } else if (policyResolverConfig instanceof PolicySet policySetConfig) {
                integrationTestFixture = saplIntegrationTestFixtureConstructorWrapper.create(policySetConfig.getPdpConfig(), policySetConfig.getPolicies());
            } else {
                throw new RuntimeException("No valid Policy Resolver Config");
            }

            if (integrationTestSuite.getPdpVariables() instanceof Object pdpVariables) {
                final var pdpEnvironmentVariables = valInterpreter.destructureObject(pdpVariables);
                integrationTestFixture = integrationTestFixture.withPDPVariables(pdpEnvironmentVariables);
            }

            final var pdpCombiningAlgorithm = integrationTestSuite.getCombiningAlgorithm();
            if (pdpCombiningAlgorithm != null) {
                final var pdpPolicyCombiningAlgorithm = interpretPdpCombiningAlgorithm(pdpCombiningAlgorithm);
                integrationTestFixture.withPDPPolicyCombiningAlgorithm(pdpPolicyCombiningAlgorithm);
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

    private static PolicyDocumentCombiningAlgorithm interpretPdpCombiningAlgorithm(final CombiningAlgorithm combiningAlgorithm) {
        if (combiningAlgorithm instanceof DenyOverridesCombiningAlgorithm) {
            return PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES;
        } else if (combiningAlgorithm instanceof PermitOverridesCombiningAlgorithm) {
            return PolicyDocumentCombiningAlgorithm.PERMIT_OVERRIDES;
        } else if (combiningAlgorithm instanceof OnlyOneApplicableCombiningAlgorithm) {
            return PolicyDocumentCombiningAlgorithm.ONLY_ONE_APPLICABLE;
        } else if (combiningAlgorithm instanceof PermitUnlessDenyCombiningAlgorithm) {
            return PolicyDocumentCombiningAlgorithm.PERMIT_UNLESS_DENY;
        } else {
            return PolicyDocumentCombiningAlgorithm.DENY_UNLESS_PERMIT;
        }
    }
}
