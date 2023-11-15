package io.sapl.test.dsl.factories;

import io.sapl.test.unit.SaplUnitTestFixture;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SaplUnitTestFixtureFactory {
    public SaplUnitTestFixture create(final String saplDocumentName) {
        return new SaplUnitTestFixture(saplDocumentName);
    }
}
