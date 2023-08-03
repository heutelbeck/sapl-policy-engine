package io.sapl.test.services;

import io.sapl.test.interfaces.ExpectStepBuilder;
import io.sapl.test.interfaces.GivenStepBuilder;
import io.sapl.test.interfaces.SaplTestDslInterpreter;
import io.sapl.test.interfaces.TestProvider;
import io.sapl.test.interfaces.VerifyStepBuilder;
import io.sapl.test.unit.SaplUnitTestFixture;
import io.sapl.test.utils.ClasspathHelper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import reactor.core.Exceptions;

public final class TestBuilderServiceDefaultImpl {

    private final TestProvider testProvider;
    private final GivenStepBuilder givenStepBuilder;
    private final ExpectStepBuilder expectStepBuilder;
    private final VerifyStepBuilder verifyStepBuilder;
    private final SaplTestDslInterpreter saplTestDslInterpreter;

    public TestBuilderServiceDefaultImpl(TestProvider testProvider, GivenStepBuilder givenStepBuilder, ExpectStepBuilder expectStepBuilder, VerifyStepBuilder verifyStepBuilder, SaplTestDslInterpreter saplTestDslInterpreter) {
        this.testProvider = testProvider;
        this.givenStepBuilder = givenStepBuilder;
        this.expectStepBuilder = expectStepBuilder;
        this.verifyStepBuilder = verifyStepBuilder;
        this.saplTestDslInterpreter = saplTestDslInterpreter;
    }

    public void buildTest(final String fileName) {

        final var input = findFileOnClasspath(fileName);
        final var saplTest = saplTestDslInterpreter.loadAsResource(input);

        saplTest.getElements().forEach(testSuite -> {
            testSuite.getTestCases().forEach(testCase -> testProvider.addTestCase(testCase.getName(), () -> {
                final var fixture = new SaplUnitTestFixture(testSuite.getPolicy());

                final var givenOrWhenStep = givenStepBuilder.constructWhenStep(testCase, fixture);
                final var expectStep = expectStepBuilder.constructExpectStep(testCase, givenOrWhenStep);
                final var verifyStep = verifyStepBuilder.constructVerifyStep(testCase, expectStep);

                verifyStep.verify();
            }));
        });
    }

    private String findFileOnClasspath(String filename) {
        Path path = ClasspathHelper.findPathOnClasspath(getClass().getClassLoader(), filename);
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }
    }
}
