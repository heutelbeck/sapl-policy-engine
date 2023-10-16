package io.sapl.test.dsl.interpreter.constructorwrappers;

import io.sapl.test.unit.SaplUnitTestFixture;

public class SaplUnitTestFixtureConstructorWrapper {
    public SaplUnitTestFixture create(final String saplDocumentName) {
        return new SaplUnitTestFixture(saplDocumentName);
    }
}
