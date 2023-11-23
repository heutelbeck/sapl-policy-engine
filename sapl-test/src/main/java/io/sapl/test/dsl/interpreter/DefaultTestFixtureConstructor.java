package io.sapl.test.dsl.interpreter;

import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.dsl.utils.ReflectionHelper;
import io.sapl.test.grammar.sAPLTest.CustomFunctionLibrary;
import io.sapl.test.grammar.sAPLTest.FixtureRegistration;
import io.sapl.test.grammar.sAPLTest.Pip;
import io.sapl.test.grammar.sAPLTest.SaplFunctionLibrary;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.steps.GivenOrWhenStep;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DefaultTestFixtureConstructor {

    private final TestSuiteInterpreter testSuiteInterpreter;
    private final FunctionLibraryInterpreter functionLibraryInterpreter;
    private final ReflectionHelper reflectionHelper;

    public GivenOrWhenStep buildTestFixture(final List<FixtureRegistration> fixtureRegistrations, final TestSuite testSuite, final io.sapl.test.grammar.sAPLTest.Object environment, final boolean needsMocks) {
        var saplTestFixture = testSuiteInterpreter.getFixtureFromTestSuite(testSuite, environment);

        if (saplTestFixture == null) {
            throw new SaplTestException("could not build test fixture");
        }

        if (fixtureRegistrations != null) {
            handleFixtureRegistrations(saplTestFixture, fixtureRegistrations);
        }

        final var givenOrWhenStep = needsMocks ? saplTestFixture.constructTestCaseWithMocks() : saplTestFixture.constructTestCase();
        return (GivenOrWhenStep) givenOrWhenStep;
    }

    private void handleFixtureRegistrations(final SaplTestFixture fixture, final List<FixtureRegistration> fixtureRegistrations) {
        try {
            for (var fixtureRegistration : fixtureRegistrations) {
                if (fixtureRegistration instanceof SaplFunctionLibrary saplFunctionLibrary) {
                    final var library = functionLibraryInterpreter.getFunctionLibrary(saplFunctionLibrary.getLibrary());
                    fixture.registerFunctionLibrary(library);
                } else if (fixtureRegistration instanceof CustomFunctionLibrary customFunctionLibrary) {
                    final var functionLibrary = reflectionHelper.constructInstanceOfClass(customFunctionLibrary.getFqn());
                    fixture.registerFunctionLibrary(functionLibrary);
                } else if (fixtureRegistration instanceof Pip pip) {
                    final var customPip = reflectionHelper.constructInstanceOfClass(pip.getFqn());
                    fixture.registerPIP(customPip);
                } else {
                    throw new SaplTestException("Unknown type of FixtureRegistration");
                }
            }
        } catch (Exception e) {
            throw new SaplTestException(e);
        }
    }
}
