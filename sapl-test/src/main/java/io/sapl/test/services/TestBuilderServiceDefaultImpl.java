package io.sapl.test.services;

import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.grammar.sAPLTest.TestException;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.interfaces.ExpectStepBuilder;
import io.sapl.test.interfaces.SaplTestDslInterpreter;
import io.sapl.test.interfaces.TestProvider;
import io.sapl.test.interfaces.VerifyStepBuilder;
import io.sapl.test.interfaces.WhenStepBuilder;
import io.sapl.test.steps.ExpectOrVerifyStep;
import io.sapl.test.unit.SaplUnitTestFixture;
import io.sapl.test.utils.ClasspathHelper;
import java.nio.file.Files;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.Assertions;

@RequiredArgsConstructor
public final class TestBuilderServiceDefaultImpl {

    private final TestProvider testProvider;
    private final TestFixtureBuilder testFixtureBuilder;
    private final WhenStepBuilder whenStepBuilder;
    private final ExpectStepBuilder expectStepBuilder;
    private final VerifyStepBuilder verifyStepBuilder;
    private final SaplTestDslInterpreter saplTestDslInterpreter;


    public void buildTest(final String fileName) {
        final var input = findFileOnClasspath(fileName);

        if (input == null) {
            return;
        }

        final var saplTest = saplTestDslInterpreter.loadAsResource(input);

        if (saplTest == null) {
            return;
        }

        final var testSuites = saplTest.getElements();

        if (testSuites == null || testSuites.isEmpty()) {
            return;
        }

        testSuites.forEach(testSuite -> {
            final var testCases = testSuite.getTestCases();
            if (testCases == null || testCases.isEmpty()) {
                return;
            }
            testCases.forEach(testCase -> addDynamicTestCase(testSuite, testCase));
        });
    }

    private void addDynamicTestCase(TestSuite testSuite, TestCase testCase) {
        testProvider.addTestCase(testCase.getName(), () -> {
            final var fixture = new SaplUnitTestFixture(testSuite.getPolicy());
            final var givenSteps = testCase.getGivenSteps();

            final var testFixture = testFixtureBuilder.buildTestFixture(givenSteps, fixture);

            if (testCase.getExpect() instanceof TestException) {
                Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(() ->
                        whenStepBuilder.constructWhenStep(givenSteps, testFixture));
            } else {

                final var whenStep = whenStepBuilder.constructWhenStep(givenSteps, testFixture);
                final var expectStep = expectStepBuilder.constructExpectStep(testCase, whenStep);
                final var verifyStep = verifyStepBuilder.constructVerifyStep(testCase, (ExpectOrVerifyStep) expectStep);

                verifyStep.verify();
            }
        });
    }

    private String findFileOnClasspath(final String filename) {
        if (filename == null) {
            return null;
        }

        final var path = ClasspathHelper.findPathOnClasspath(getClass().getClassLoader(), filename);

        try {
            return Files.readString(path);
        } catch (Exception e) {
            return null;
        }
    }
}
