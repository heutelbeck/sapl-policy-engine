package io.sapl.test.dsl.interpreter;

import io.sapl.interpreter.InitializationException;
import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.dsl.ReflectionHelper;
import io.sapl.test.grammar.sAPLTest.CustomFunctionLibrary;
import io.sapl.test.grammar.sAPLTest.FixtureRegistration;
import io.sapl.test.grammar.sAPLTest.Object;
import io.sapl.test.grammar.sAPLTest.Pip;
import io.sapl.test.grammar.sAPLTest.SaplFunctionLibrary;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.steps.GivenOrWhenStep;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TestFixtureBuilder {

    private final TestSuiteInterpreter testSuiteInterpreter;
    private final FunctionLibraryInterpreter functionLibraryInterpreter;
    private final ReflectionHelper reflectionHelper;

    public GivenOrWhenStep buildTestFixture(final List<FixtureRegistration> fixtureRegistrations, final TestSuite testSuite, final Object environment, final boolean needsMocks) {
        var saplTestFixture = testSuiteInterpreter.getFixtureFromTestSuite(testSuite, environment);

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
                    final var functionLibrary = reflectionHelper.constructInstanceOfClass(customFunctionLibrary.getLibrary());
                    fixture.registerFunctionLibrary(functionLibrary);
                } else if (fixtureRegistration instanceof Pip pip) {
                    final var customPip = reflectionHelper.constructInstanceOfClass(pip.getPip());
                    fixture.registerPIP(customPip);
                }
            }
        } catch (InitializationException e) {
            throw new SaplTestException(e);
        }
    }
}
