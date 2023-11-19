package io.sapl.test.dsl.setup;

import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.dsl.interpreter.DefaultExpectStepConstructor;
import io.sapl.test.dsl.interpreter.DefaultTestFixtureConstructor;
import io.sapl.test.dsl.interpreter.DefaultVerifyStepConstructor;
import io.sapl.test.dsl.interpreter.DefaultWhenStepConstructor;
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
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DefaultStepConstructor implements StepConstructor {

    private final DefaultTestFixtureConstructor defaultTestFixtureConstructor;
    private final DefaultWhenStepConstructor whenStepBuilder;
    private final DefaultExpectStepConstructor expectStepBuilder;
    private final DefaultVerifyStepConstructor verifyStepBuilder;

    @Override
    public GivenOrWhenStep buildTestFixture(final List<FixtureRegistration> fixtureRegistrations, final TestSuite testSuite, final io.sapl.test.grammar.sAPLTest.Object environment, final boolean needsMocks) {
        return defaultTestFixtureConstructor.buildTestFixture(fixtureRegistrations, testSuite, environment, needsMocks);
    }

    @Override
    public WhenStep constructWhenStep(final List<GivenStep> givenSteps, GivenOrWhenStep givenOrWhenStep) {
        return whenStepBuilder.constructWhenStep(givenSteps, givenOrWhenStep);
    }

    @Override
    public ExpectStep constructExpectStep(final TestCase testCase, final WhenStep whenStep) {
        return expectStepBuilder.constructExpectStep(testCase, whenStep);
    }

    @Override
    public VerifyStep constructVerifyStep(final TestCase testCase, final ExpectOrVerifyStep expectOrVerifyStep) {
        return verifyStepBuilder.constructVerifyStep(testCase, expectOrVerifyStep);
    }
}
