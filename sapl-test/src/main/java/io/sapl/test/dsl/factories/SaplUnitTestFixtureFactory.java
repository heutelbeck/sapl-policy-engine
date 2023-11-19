package io.sapl.test.dsl.factories;

import io.sapl.test.unit.SaplUnitTestFixture;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SaplUnitTestFixtureFactory {
    public static SaplUnitTestFixture create(final String saplDocumentName) {
        return new SaplUnitTestFixture(saplDocumentName);
    }

    public static SaplUnitTestFixture createFromInputString(final String input) {
        return new SaplUnitTestFixture(input, false);
    }
}
