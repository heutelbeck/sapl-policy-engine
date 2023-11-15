package io.sapl.test.dsl.factories;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.sapl.test.integration.SaplIntegrationTestFixture;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class SaplIntegrationTestFixtureFactoryTest {

    @Test
    void create_constructsInstanceOfSaplIntegrationTestFixtureWithPolicyPath_returnsSaplIntegrationTestFixtureInstance() {
        final var result = SaplIntegrationTestFixtureFactory.create("path");

        assertInstanceOf(SaplIntegrationTestFixture.class, result);
    }

    @Test
    void create_constructsInstanceOfSaplIntegrationTestFixtureWithPDPConfigAndPaths_returnsSaplIntegrationTestFixtureInstance() {
        final var result = SaplIntegrationTestFixtureFactory.create("pdpConfigPath", Collections.emptyList());

        assertInstanceOf(SaplIntegrationTestFixture.class, result);
    }
}