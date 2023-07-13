package io.sapl.test.interfaces;

import io.sapl.interpreter.InitializationException;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.steps.WhenStep;
import io.sapl.test.unit.SaplUnitTestFixture;

public interface GivenStepBuilder {
    WhenStep constructWhenStep(TestCase testCase, SaplUnitTestFixture fixture) throws InitializationException;
}
