package io.sapl.test.dsl.factories;

import io.sapl.test.integration.SaplIntegrationTestFixture;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SaplIntegrationTestFixtureFactory {
    public SaplIntegrationTestFixture create(final String policyPath) {
        return new SaplIntegrationTestFixture(policyPath);
    }

    public SaplIntegrationTestFixture create(final String pdpConfigPath, final List<String> policyPaths) {
        return new SaplIntegrationTestFixture(pdpConfigPath, policyPaths);
    }
}
