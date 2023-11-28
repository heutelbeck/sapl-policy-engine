package io.sapl.test.dsl.interpreter;

import io.sapl.test.unit.SaplUnitTestFixture;
import lombok.experimental.UtilityClass;

@UtilityClass
class SaplUnitTestFixtureFactory {
    public static SaplUnitTestFixture create(final String saplDocumentName) {
        return new SaplUnitTestFixture(saplDocumentName);
    }

    public static SaplUnitTestFixture createFromInputString(final String input) {
        return new SaplUnitTestFixture(input, false);
    }
}
