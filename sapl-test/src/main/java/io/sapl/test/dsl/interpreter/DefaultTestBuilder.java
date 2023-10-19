package io.sapl.test.dsl.interpreter;

import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interfaces.ExpectStepBuilder;
import io.sapl.test.dsl.interfaces.JUnitDynamicTestBuilder;
import io.sapl.test.dsl.interfaces.SaplTestInterpreter;
import io.sapl.test.dsl.interfaces.VerifyStepBuilder;
import io.sapl.test.dsl.interfaces.WhenStepBuilder;
import io.sapl.test.grammar.sAPLTest.IntegrationTestSuite;
import io.sapl.test.grammar.sAPLTest.Object;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.grammar.sAPLTest.TestException;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.grammar.sAPLTest.UnitTestSuite;
import io.sapl.test.steps.ExpectOrVerifyStep;
import io.sapl.test.utils.DocumentHelper;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;

@RequiredArgsConstructor
public final class DefaultTestBuilder implements JUnitDynamicTestBuilder {

    private final TestFixtureBuilder testFixtureBuilder;
    private final WhenStepBuilder whenStepBuilder;
    private final ExpectStepBuilder expectStepBuilder;
    private final VerifyStepBuilder verifyStepBuilder;
    private final SaplTestInterpreter saplTestInterpreter;

    @Override
    public List<DynamicContainer> buildTests(final String fileName) {
        if (fileName == null) {
            return Collections.emptyList();
        }

        final var input = DocumentHelper.findFileOnClasspath(fileName);

        if (input == null) {
            return Collections.emptyList();
        }

        final var saplTest = saplTestInterpreter.loadAsResource(input);

        if (saplTest == null) {
            return Collections.emptyList();
        }

        final var testSuites = saplTest.getElements();

        if (testSuites == null || testSuites.isEmpty()) {
            return Collections.emptyList();
        }

        return testSuites.stream().map(testSuite -> {
            final var testCases = testSuite.getTestCases();
            if (testCases == null || testCases.isEmpty()) {
                return null;
            }

            final var name = testSuite instanceof UnitTestSuite unitTestSuite ? unitTestSuite.getPolicy() : testSuite instanceof IntegrationTestSuite ? "integrationTest" : "";

            return dynamicContainer(name, testCases.stream().map(testCase -> addDynamicTestCase(testSuite, testCase)));
        }).filter(Objects::nonNull).toList();
    }

    private DynamicTest addDynamicTestCase(final TestSuite testSuite, final TestCase testCase) {
        return DynamicTest.dynamicTest(testCase.getName(), () -> {
            final var environment = testCase.getEnvironment();
            final var fixtureRegistrations = testCase.getRegistrations();
            final var givenSteps = testCase.getGivenSteps();

            final var environmentVariables = environment instanceof Object object ? object : null;

            final var needsMocks = !givenSteps.isEmpty();
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
        });
    }
}
