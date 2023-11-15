package io.sapl.test.dsl.setup;

import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.dsl.interpreter.DefaultExpectStepConstructor;
import io.sapl.test.dsl.interpreter.DefaultTestFixtureBuilder;
import io.sapl.test.dsl.interpreter.DefaultVerifyStepConstructor;
import io.sapl.test.dsl.interpreter.DefaultWhenStepConstructor;
import io.sapl.test.grammar.sAPLTest.FixtureRegistration;
import io.sapl.test.grammar.sAPLTest.GivenStep;
import io.sapl.test.grammar.sAPLTest.Object;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.steps.ExpectOrVerifyStep;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.GivenOrWhenStep;
import io.sapl.test.steps.VerifyStep;
import io.sapl.test.steps.WhenStep;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DefaultStepConstructor implements StepConstructor {

    private final DefaultExpectStepConstructor expectStepBuilder;
    private final DefaultTestFixtureBuilder defaultTestFixtureBuilder;
    private final DefaultVerifyStepConstructor verifyStepBuilder;
    private final DefaultWhenStepConstructor whenStepBuilder;

    @Override
    public ExpectStep constructExpectStep(final TestCase testCase, final WhenStep whenStep) {
        return expectStepBuilder.constructExpectStep(testCase, whenStep);
    }

    @Override
    public GivenOrWhenStep buildTestFixture(final List<FixtureRegistration> fixtureRegistrations, final TestSuite testSuite, final Object environment, final boolean needsMocks) {
        return defaultTestFixtureBuilder.buildTestFixture(fixtureRegistrations, testSuite, environment, needsMocks);
    }

    @Override
    public VerifyStep constructVerifyStep(final TestCase testCase, final ExpectOrVerifyStep expectOrVerifyStep) {
        return verifyStepBuilder.constructVerifyStep(testCase, expectOrVerifyStep);
    }

    @Override
    public WhenStep constructWhenStep(final List<GivenStep> givenSteps, GivenOrWhenStep givenOrWhenStep) {
        return whenStepBuilder.constructWhenStep(givenSteps, givenOrWhenStep);
    }
}
