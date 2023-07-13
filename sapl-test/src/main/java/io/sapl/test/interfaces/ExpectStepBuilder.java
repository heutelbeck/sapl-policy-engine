package io.sapl.test.interfaces;


import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.WhenStep;

public interface ExpectStepBuilder {
    ExpectStep constructExpectStep(TestCase testCase, WhenStep whenStep);
}
