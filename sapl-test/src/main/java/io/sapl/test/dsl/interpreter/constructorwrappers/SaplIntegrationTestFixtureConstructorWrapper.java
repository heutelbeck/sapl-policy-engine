package io.sapl.test.dsl.interpreter.constructorwrappers;

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
