package io.sapl.test.interfaces;

import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.steps.ExpectOrVerifyStep;
import io.sapl.test.steps.VerifyStep;

public interface VerifyStepBuilder {
    VerifyStep constructVerifyStep(TestCase testCase, ExpectOrVerifyStep expectOrVerifyStep);
}
