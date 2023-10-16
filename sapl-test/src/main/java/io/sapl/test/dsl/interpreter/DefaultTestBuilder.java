package io.sapl.test.dsl.interpreter;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interfaces.ExpectStepBuilder;
import io.sapl.test.dsl.interfaces.JUnitDynamicTestBuilder;
import io.sapl.test.dsl.interfaces.SaplTestInterpreter;
import io.sapl.test.dsl.interfaces.VerifyStepBuilder;
import io.sapl.test.dsl.interfaces.WhenStepBuilder;
import io.sapl.test.grammar.sAPLTest.Object;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.grammar.sAPLTest.TestException;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.steps.ExpectOrVerifyStep;
import io.sapl.test.utils.DocumentHelper;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;

@RequiredArgsConstructor
public final class DefaultTestBuilder implements JUnitDynamicTestBuilder {

    private final TestFixtureBuilder testFixtureBuilder;
    private final WhenStepBuilder whenStepBuilder;
    private final ExpectStepBuilder expectStepBuilder;
    private final VerifyStepBuilder verifyStepBuilder;
    private final SaplTestInterpreter saplTestInterpreter;

    @Override
    public List<DynamicTest> buildTests(final String fileName) {
        if (fileName == null) {
            return Collections.emptyList();
        }

        final var input = DocumentHelper.findFileOnClasspath(fileName);

        final var dynamicTestList = new LinkedList<DynamicTest>();

        if (input == null) {
            return dynamicTestList;
        }

        final var saplTest = saplTestInterpreter.loadAsResource(input);

        if (saplTest == null) {
            return dynamicTestList;
        }

        final var testSuites = saplTest.getElements();

        if (testSuites == null || testSuites.isEmpty()) {
            return dynamicTestList;
        }

        testSuites.forEach(testSuite -> {
            final var testCases = testSuite.getTestCases();
            if (testCases == null || testCases.isEmpty()) {
                return;
            }
            testCases.forEach(testCase -> dynamicTestList.add(addDynamicTestCase(testSuite, testCase)));
        });
        return dynamicTestList;
    }

    private DynamicTest addDynamicTestCase(final TestSuite testSuite, final TestCase testCase) {
        return DynamicTest.dynamicTest(testCase.getName(), () -> {
            final var environment = testCase.getEnvironment();
            final var givenSteps = testCase.getGivenSteps();

            final var environmentVariables = environment instanceof Object object ? object : null;

            final var testFixture = testFixtureBuilder.buildTestFixture(givenSteps, testSuite, environmentVariables);


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
}
