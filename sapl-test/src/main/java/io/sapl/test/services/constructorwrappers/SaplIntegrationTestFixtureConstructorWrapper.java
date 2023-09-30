package io.sapl.test.services.constructorwrappers;

import io.sapl.test.integration.SaplIntegrationTestFixture;
import java.util.List;

public class SaplIntegrationTestFixtureConstructorWrapper {
    public SaplIntegrationTestFixture create(final String policyPath) {
        return new SaplIntegrationTestFixture(policyPath);
    }

    public SaplIntegrationTestFixture create(final String pdpConfigPath, final List<String> policyPaths) {
        return new SaplIntegrationTestFixture(pdpConfigPath, policyPaths);
    }
}
