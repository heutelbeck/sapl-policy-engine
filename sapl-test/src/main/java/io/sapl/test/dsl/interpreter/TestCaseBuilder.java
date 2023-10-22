package io.sapl.test.dsl.interpreter;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interfaces.ExpectStepBuilder;
import io.sapl.test.dsl.interfaces.VerifyStepBuilder;
import io.sapl.test.dsl.interfaces.WhenStepBuilder;
import io.sapl.test.grammar.sAPLTest.Object;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.grammar.sAPLTest.TestException;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.steps.ExpectOrVerifyStep;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;

@RequiredArgsConstructor
public class TestCaseBuilder {

    private final TestFixtureBuilder testFixtureBuilder;
    private final WhenStepBuilder whenStepBuilder;
    private final ExpectStepBuilder expectStepBuilder;
    private final VerifyStepBuilder verifyStepBuilder;

    DynamicTest constructTestCase(final TestSuite testSuite, final TestCase testCase) {
        if (testSuite == null || testCase == null || testCase.getName() == null) {
            throw new SaplTestException("Encountered error during test setup");
        }

        return DynamicTest.dynamicTest(testCase.getName(), () -> buildTest(testSuite, testCase));
    }

    private void buildTest(final TestSuite testSuite, final TestCase testCase) {
        final var environment = testCase.getEnvironment();
        final var fixtureRegistrations = testCase.getRegistrations();
        final var givenSteps = testCase.getGivenSteps();

        final var environmentVariables = environment instanceof Object object ? object : null;

        final var needsMocks = givenSteps != null && !givenSteps.isEmpty();
        final var testFixture = testFixtureBuilder.buildTestFixture(fixtureRegistrations, testSuite, environmentVariables, needsMocks);

        if (testCase.getExpect() instanceof TestException) {
            Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(() ->
                    whenStepBuilder.constructWhenStep(givenSteps, testFixture));
        } else {

            final var whenStep = whenStepBuilder.constructWhenStep(givenSteps, testFixture);
            final var expectStep = expectStepBuilder.constructExpectStep(testCase, whenStep);
            final var verifyStep = verifyStepBuilder.constructVerifyStep(testCase, (ExpectOrVerifyStep) expectStep);

            verifyStep.verify();
        }
    }
}
