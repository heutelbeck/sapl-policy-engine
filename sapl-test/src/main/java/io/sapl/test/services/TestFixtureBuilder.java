package io.sapl.test.services;

import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.LoggingFunctionLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.interpreter.InitializationException;
import io.sapl.test.grammar.sAPLTest.GivenStep;
import io.sapl.test.grammar.sAPLTest.Library;
import io.sapl.test.grammar.sAPLTest.Pip;
import io.sapl.test.steps.GivenOrWhenStep;
import io.sapl.test.unit.SaplUnitTestFixture;
import java.util.List;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TestFixtureBuilder {

    private final Object testPip;

    public GivenOrWhenStep buildTestFixture(List<GivenStep> givenSteps, SaplUnitTestFixture fixture) throws InitializationException {
        if (givenSteps == null || givenSteps.isEmpty()) {
            return (GivenOrWhenStep) fixture.constructTestCase();
        }

        final var fixtureRegistrations = givenSteps.stream().filter(isFixtureRegistration()).toList();

        givenSteps.removeAll(fixtureRegistrations);
        handleFixtureRegistrations(fixture, fixtureRegistrations);
        return (GivenOrWhenStep) fixture.constructTestCaseWithMocks();
    }

    private static Predicate<GivenStep> isFixtureRegistration() {
        return givenStep -> givenStep instanceof Pip || givenStep instanceof Library;
    }

    private void handleFixtureRegistrations(SaplUnitTestFixture fixture, List<GivenStep> fixtureRegistrations) throws InitializationException {
        for (var fixtureRegistration : fixtureRegistrations) {
            if (fixtureRegistration instanceof Library library) {
                fixture.registerFunctionLibrary(getFunctionLibrary(library.getLibrary()));
            } else if (fixtureRegistration instanceof Pip) {
                fixture.registerPIP(testPip);
            }
        }
    }

    private Object getFunctionLibrary(String functionLibrary) {
        return switch (functionLibrary) {
            case "FilterFunctionLibrary" -> new FilterFunctionLibrary();
            case "LoggingFunctionLibrary" -> new LoggingFunctionLibrary();
            case "StandardFunctionLibrary" -> new StandardFunctionLibrary();
            case "TemporalFunctionLibrary" -> new TemporalFunctionLibrary();
            default -> throw new IllegalStateException("Unexpected value: " + functionLibrary);
        };
    }
}
