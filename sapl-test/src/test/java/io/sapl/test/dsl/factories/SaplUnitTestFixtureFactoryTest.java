package io.sapl.test.dsl.factories;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.sapl.test.unit.SaplUnitTestFixture;
import org.junit.jupiter.api.Test;

class SaplUnitTestFixtureFactoryTest {

    @Test
    void create_constructsInstanceOfSaplUnitTestFixtureWithSaplDocumentName_returnsSaplUnitTestFixture() {
        final var result = SaplUnitTestFixtureFactory.create("documentName");

        assertInstanceOf(SaplUnitTestFixture.class, result);
    }

    @Test
    void createFromInputString_constructsInstanceOfSaplUnitTestFixtureWithInputString_returnsSaplUnitTestFixture() {
        final var result = SaplUnitTestFixtureFactory.createFromInputString("documentName");

        assertInstanceOf(SaplUnitTestFixture.class, result);
    }
}