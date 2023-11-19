package io.sapl.test.dsl.interfaces;

public interface IntegrationTestPolicyResolver {
    String resolvePolicyByIdentifier(String identifier);

    String resolvePDPConfigByIdentifier(String identifier);

    IntegrationTestConfiguration resolveConfigByIdentifier(String identifier);
}
