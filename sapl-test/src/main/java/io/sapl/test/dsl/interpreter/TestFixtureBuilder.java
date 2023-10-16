package io.sapl.test.dsl.interpreter;

import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.LoggingFunctionLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.interpreter.InitializationException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.grammar.sAPLTest.FunctionLibrary;
import io.sapl.test.grammar.sAPLTest.GivenStep;
import io.sapl.test.grammar.sAPLTest.Library;
import io.sapl.test.grammar.sAPLTest.Object;
import io.sapl.test.grammar.sAPLTest.Pip;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.steps.GivenOrWhenStep;
import java.util.List;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TestFixtureBuilder {

    private final java.lang.Object testPip;
    private final TestSuiteInterpreter testSuiteInterpreter;

    public GivenOrWhenStep buildTestFixture(final List<GivenStep> givenSteps, final TestSuite testSuite, final Object environment) throws InitializationException {
        var saplTestFixture = testSuiteInterpreter.getFixtureFromTestSuite(testSuite, environment);

        if (givenSteps == null || givenSteps.isEmpty()) {
            return (GivenOrWhenStep) saplTestFixture.constructTestCase();
        }

        final var fixtureRegistrations = givenSteps.stream().filter(isFixtureRegistration()).toList();

        givenSteps.removeAll(fixtureRegistrations);
        handleFixtureRegistrations(saplTestFixture, fixtureRegistrations);
        return (GivenOrWhenStep) saplTestFixture.constructTestCaseWithMocks();
    }

    private static Predicate<GivenStep> isFixtureRegistration() {
        return givenStep -> givenStep instanceof Pip || givenStep instanceof Library;
    }

    private void handleFixtureRegistrations(final SaplTestFixture fixture, final List<GivenStep> fixtureRegistrations) throws InitializationException {
        for (var fixtureRegistration : fixtureRegistrations) {
            if (fixtureRegistration instanceof Library library) {
                fixture.registerFunctionLibrary(getFunctionLibrary(library.getLibrary()));
            } else if (fixtureRegistration instanceof Pip) {
                fixture.registerPIP(testPip);
            }
        }
    }


    private java.lang.Object getFunctionLibrary(final FunctionLibrary functionLibrary) {
        return switch (functionLibrary) {
            case FILTER -> new FilterFunctionLibrary();
            case LOGGING -> new LoggingFunctionLibrary();
            case STANDARD -> new StandardFunctionLibrary();
            case TEMPORAL -> new TemporalFunctionLibrary();
        };
    }
}
