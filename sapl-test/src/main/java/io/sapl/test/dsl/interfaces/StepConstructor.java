package io.sapl.test.dsl.interfaces;

import io.sapl.test.grammar.sAPLTest.FixtureRegistration;
import io.sapl.test.grammar.sAPLTest.GivenStep;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.steps.ExpectOrVerifyStep;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.GivenOrWhenStep;
import io.sapl.test.steps.VerifyStep;
import io.sapl.test.steps.WhenStep;
import java.util.List;

public interface StepConstructor {
    GivenOrWhenStep buildTestFixture(List<FixtureRegistration> fixtureRegistrations, TestSuite testSuite, io.sapl.test.grammar.sAPLTest.Object environment, boolean needsMocks);

    WhenStep constructWhenStep(List<GivenStep> givenSteps, GivenOrWhenStep fixture);

    ExpectStep constructExpectStep(TestCase testCase, WhenStep whenStep);

    VerifyStep constructVerifyStep(TestCase testCase, ExpectOrVerifyStep expectOrVerifyStep);
}
