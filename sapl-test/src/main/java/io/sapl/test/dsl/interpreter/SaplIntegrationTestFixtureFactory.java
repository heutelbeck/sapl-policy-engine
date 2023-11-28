package io.sapl.test.dsl.interpreter;

import io.sapl.test.integration.SaplIntegrationTestFixture;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
class SaplIntegrationTestFixtureFactory {
    public static SaplIntegrationTestFixture create(final String policyPath) {
        return new SaplIntegrationTestFixture(policyPath);
    }

    public static SaplIntegrationTestFixture create(final String pdpConfigPath, final List<String> policyPaths) {
        return new SaplIntegrationTestFixture(pdpConfigPath, policyPaths);
    }

    public static SaplIntegrationTestFixture createFromInputStrings(final List<String> documentInputStrings, final String pdpConfigInputString) {
        return new SaplIntegrationTestFixture(documentInputStrings, pdpConfigInputString);
    }
}
