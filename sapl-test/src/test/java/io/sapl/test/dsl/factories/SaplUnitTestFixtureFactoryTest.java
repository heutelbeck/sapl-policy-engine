package io.sapl.test.dsl.factories;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.sapl.test.unit.SaplUnitTestFixture;
import org.junit.jupiter.api.Test;

class SaplUnitTestFixtureFactoryTest {

    @Test
    void create_constructsInstanceOfSaplUnitTestFixtureWithSaplDocumentName_returnsSaplUnitTestFixtureInstance() {
        final var result = SaplUnitTestFixtureFactory.create("documentName");

        assertInstanceOf(SaplUnitTestFixture.class, result);
    }
}