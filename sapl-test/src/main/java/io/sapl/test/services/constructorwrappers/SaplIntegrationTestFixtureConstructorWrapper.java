package io.sapl.test.services.constructorwrappers;

import io.sapl.test.integration.SaplIntegrationTestFixture;

public class SaplIntegrationTestFixtureConstructorWrapper {
    public SaplIntegrationTestFixture create(final String policyPath) {
        return new SaplIntegrationTestFixture(policyPath);
    }
}
