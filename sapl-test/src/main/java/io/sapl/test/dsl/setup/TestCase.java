package io.sapl.test.dsl.setup;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.dsl.interfaces.TestNode;
import io.sapl.test.grammar.sAPLTest.TestException;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.steps.ExpectOrVerifyStep;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.Assertions;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class TestCase implements TestNode, Runnable {

    private final String identifier;
    private final StepConstructor stepConstructor;
    private final TestSuite testSuite;
    private final io.sapl.test.grammar.sAPLTest.TestCase dslTestCase;

    public static TestCase from(final StepConstructor stepConstructor, final TestSuite testSuite, io.sapl.test.grammar.sAPLTest.TestCase testCase) {
        if (stepConstructor == null || testSuite == null || testCase == null) {
            throw new SaplTestException("One or more parameter(s) are null");
        }

        final var name = testCase.getName();

        if (name == null) {
            throw new SaplTestException("Name of the test case is null");
        }

        return new TestCase(name, stepConstructor, testSuite, testCase);
    }

    @Override
    public void run() {
        final var environment = dslTestCase.getEnvironment();
        final var fixtureRegistrations = dslTestCase.getRegistrations();
        final var givenSteps = dslTestCase.getGivenSteps();

        final var environmentVariables = environment instanceof io.sapl.test.grammar.sAPLTest.Object object ? object : null;

        final var needsMocks = givenSteps != null && !givenSteps.isEmpty();
        final var testFixture = stepConstructor.buildTestFixture(fixtureRegistrations, testSuite, environmentVariables, needsMocks);

        if (dslTestCase.getExpect() instanceof TestException) {
            Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(() ->
                    stepConstructor.constructWhenStep(givenSteps, testFixture));
        } else {

            final var whenStep = stepConstructor.constructWhenStep(givenSteps, testFixture);
            final var expectStep = stepConstructor.constructExpectStep(dslTestCase, whenStep);
            final var verifyStep = stepConstructor.constructVerifyStep(dslTestCase, (ExpectOrVerifyStep) expectStep);

            verifyStep.verify();
        }
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }
}
