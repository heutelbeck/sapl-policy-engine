package io.sapl.test.dsl.factories;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.sapl.test.integration.SaplIntegrationTestFixture;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SaplIntegrationTestFixtureFactoryTest {

    @Test
    void create_constructsInstanceOfSaplIntegrationTestFixtureWithPolicyPath_returnsSaplIntegrationTestFixture() {
        final var result = SaplIntegrationTestFixtureFactory.create("path");

        assertInstanceOf(SaplIntegrationTestFixture.class, result);
    }

    @Test
    void create_constructsInstanceOfSaplIntegrationTestFixtureWithPDPConfigAndPaths_returnsSaplIntegrationTestFixture() {
        final var result = SaplIntegrationTestFixtureFactory.create("pdpConfigPath", Collections.emptyList());

        assertInstanceOf(SaplIntegrationTestFixture.class, result);
    }

    @Test
    void createFromInputStrings_constructsInstanceOfSaplIntegrationTestFixtureWithInputStrings_returnsSaplIntegrationTestFixture() {
        final var result = SaplIntegrationTestFixtureFactory.createFromInputStrings(List.of(), "");

        assertInstanceOf(SaplIntegrationTestFixture.class, result);
    }
}