package io.sapl.test.services.constructorwrappers;

import io.sapl.test.unit.SaplUnitTestFixture;

public class SaplUnitTestFixtureConstructorWrapper {
    public SaplUnitTestFixture create(final String saplDocumentName) {
        return new SaplUnitTestFixture(saplDocumentName);
    }
}
