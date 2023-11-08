package io.sapl.test.dsl.setup;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.grammar.sAPLTest.TestException;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.steps.ExpectOrVerifyStep;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.Assertions;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Test implements TestNode, Runnable {

    private final String identifier;
    private final StepConstructor stepConstructor;
    private final TestSuite testSuite;
    private final TestCase testCase;

    public static Test from(final StepConstructor stepConstructor, final TestSuite testSuite, TestCase testCase) {
        if (stepConstructor == null || testSuite == null || testCase == null) {
            throw new SaplTestException("One or more parameter(s) are null");
        }

        final var name = testCase.getName();

        if (name == null) {
            throw new SaplTestException("Name of the test case is null");
        }

        return new Test(name, stepConstructor, testSuite, testCase);
    }

    @Override
    public void run() {
        final var environment = testCase.getEnvironment();
        final var fixtureRegistrations = testCase.getRegistrations();
        final var givenSteps = testCase.getGivenSteps();

        final var environmentVariables = environment instanceof io.sapl.test.grammar.sAPLTest.Object object ? object : null;

        final var needsMocks = givenSteps != null && !givenSteps.isEmpty();
        final var testFixture = stepConstructor.buildTestFixture(fixtureRegistrations, testSuite, environmentVariables, needsMocks);

        if (testCase.getExpect() instanceof TestException) {
            Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(() ->
                    stepConstructor.constructWhenStep(givenSteps, testFixture));
        } else {

            final var whenStep = stepConstructor.constructWhenStep(givenSteps, testFixture);
            final var expectStep = stepConstructor.constructExpectStep(testCase, whenStep);
            final var verifyStep = stepConstructor.constructVerifyStep(testCase, (ExpectOrVerifyStep) expectStep);

            verifyStep.verify();
        }
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }
}
