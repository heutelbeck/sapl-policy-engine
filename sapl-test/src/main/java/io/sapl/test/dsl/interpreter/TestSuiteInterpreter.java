package io.sapl.test.dsl.interpreter;

import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.dsl.factories.SaplIntegrationTestFixtureFactory;
import io.sapl.test.dsl.factories.SaplUnitTestFixtureFactory;
import io.sapl.test.dsl.interfaces.IntegrationTestPolicyResolver;
import io.sapl.test.dsl.interfaces.UnitTestPolicyResolver;
import io.sapl.test.grammar.sAPLTest.IntegrationTestSuite;
import io.sapl.test.grammar.sAPLTest.PolicyFolder;
import io.sapl.test.grammar.sAPLTest.PolicySet;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.grammar.sAPLTest.UnitTestSuite;
import io.sapl.test.integration.SaplIntegrationTestFixture;
import java.util.Collections;
import java.util.Objects;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TestSuiteInterpreter {

    private final ValInterpreter valInterpreter;
    private final PDPCombiningAlgorithmInterpreter pdpCombiningAlgorithmInterpreter;
    private final UnitTestPolicyResolver customUnitTestPolicyResolver;
    private final IntegrationTestPolicyResolver customIntegrationTestPolicyResolver;

    SaplTestFixture getFixtureFromTestSuite(final TestSuite testSuite, final io.sapl.test.grammar.sAPLTest.Object environment) {
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
            return SaplUnitTestFixtureFactory.createFromInputString(customUnitTestPolicyResolver.resolvePolicyByIdentifier(unitTestSuite.getId()));
        }
    }

    private SaplTestFixture getIntegrationTestFixtureFromIntegrationTestSuite(final IntegrationTestSuite integrationTestSuite) {
        final var policyResolverConfig = integrationTestSuite.getConfig();
        SaplIntegrationTestFixture integrationTestFixture;

        if (policyResolverConfig instanceof PolicyFolder policyFolderConfig) {
            final var identifier = policyFolderConfig.getPolicyFolder();

            if (customIntegrationTestPolicyResolver == null) {
                integrationTestFixture = SaplIntegrationTestFixtureFactory.create(identifier);
            } else {
                final var config = customIntegrationTestPolicyResolver.resolveConfigByIdentifier(identifier);
                integrationTestFixture = SaplIntegrationTestFixtureFactory.createFromInputStrings(config.getDocumentInputStrings(), config.getPDPConfigInputString());
            }
        } else if (policyResolverConfig instanceof PolicySet policySetConfig) {
            if (customIntegrationTestPolicyResolver == null) {
                integrationTestFixture = SaplIntegrationTestFixtureFactory.create(policySetConfig.getPdpConfig(), policySetConfig.getPolicies());
            } else {
                final var pdpConfig = customIntegrationTestPolicyResolver.resolvePDPConfigByIdentifier(policySetConfig.getPdpConfig());
                final var policies = Objects.requireNonNullElse(policySetConfig.getPolicies(), Collections.<String>emptyList());

                final var saplDocumentStrings = policies.stream().map(customIntegrationTestPolicyResolver::resolvePolicyByIdentifier).toList();

                integrationTestFixture = SaplIntegrationTestFixtureFactory.createFromInputStrings(saplDocumentStrings, pdpConfig);
            }
        } else {
            throw new SaplTestException("No valid Policy Resolver Config");
        }

        if (integrationTestSuite.getPdpVariables() instanceof io.sapl.test.grammar.sAPLTest.Object pdpVariables) {
            final var pdpEnvironmentVariables = valInterpreter.destructureObject(pdpVariables);
            integrationTestFixture = integrationTestFixture.withPDPVariables(pdpEnvironmentVariables);
        }

        final var pdpCombiningAlgorithm = integrationTestSuite.getCombiningAlgorithm();

        if (pdpCombiningAlgorithm != null) {
            final var pdpPolicyCombiningAlgorithm = pdpCombiningAlgorithmInterpreter.interpretPdpCombiningAlgorithm(pdpCombiningAlgorithm);
            integrationTestFixture.withPDPPolicyCombiningAlgorithm(pdpPolicyCombiningAlgorithm);
        }

        return integrationTestFixture;
    }
}
