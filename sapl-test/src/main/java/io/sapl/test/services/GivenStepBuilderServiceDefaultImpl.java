package io.sapl.test.services;

import static io.sapl.hamcrest.Matchers.anyVal;
import static io.sapl.hamcrest.Matchers.val;
import static io.sapl.test.Imports.arguments;
import static io.sapl.test.Imports.parentValue;
import static io.sapl.test.Imports.whenAttributeParams;
import static io.sapl.test.Imports.whenParentValue;
import static org.hamcrest.CoreMatchers.is;

import io.sapl.api.interpreter.Val;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.LoggingFunctionLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.interpreter.InitializationException;
import io.sapl.test.Imports;

import io.sapl.test.grammar.sAPLTest.*;
import io.sapl.test.interfaces.GivenStepBuilder;
import io.sapl.test.mocking.function.models.FunctionParameters;
import io.sapl.test.steps.GivenOrWhenStep;
import io.sapl.test.steps.WhenStep;
import io.sapl.test.unit.SaplUnitTestFixture;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.eclipse.emf.common.util.EList;
import org.hamcrest.Matcher;

public final class GivenStepBuilderServiceDefaultImpl implements GivenStepBuilder {

    private final Object testPip;

    public GivenStepBuilderServiceDefaultImpl(final Object testPip) {
        this.testPip = testPip;
    }

    @Override
    public WhenStep constructWhenStep(TestCase testCase, SaplUnitTestFixture fixture) throws InitializationException {
        final var givenSteps = testCase.getGivenSteps();
        final var fixtureRegistrations = givenSteps.stream().filter(givenStep -> givenStep instanceof Pip || givenStep instanceof Library).toList();

        if (givenSteps.isEmpty()) {
            return fixture.constructTestCase();
        }

        handleFixtureRegistrations(fixture, fixtureRegistrations);
        var fixtureWithMocks = (GivenOrWhenStep) fixture.constructTestCaseWithMocks();
        return applyGivenSteps(givenSteps, fixtureWithMocks);
    }

    private GivenOrWhenStep applyGivenSteps(EList<GivenStep> givenSteps, GivenOrWhenStep fixtureWithMocks) {
        for (GivenStep givenStep : givenSteps) {
            if (givenStep instanceof Function function) {
                fixtureWithMocks = interpretFunction(fixtureWithMocks, function);
            } else if (givenStep instanceof FunctionInvokedOnce functionInvokedOnce) {
                fixtureWithMocks = interpretFunctionInvokedOnce(fixtureWithMocks, functionInvokedOnce);
            } else if (givenStep instanceof Attribute attribute) {
                fixtureWithMocks = interpretAttribute(fixtureWithMocks, attribute);
            } else if (givenStep instanceof AttributeWithParameters attributeWithParameters) {
                fixtureWithMocks = interpretAttributeWithParameters(fixtureWithMocks, attributeWithParameters);
            }
            else if (givenStep instanceof VirtualTime) {
                fixtureWithMocks = fixtureWithMocks.withVirtualTime();
            }
        }
        return fixtureWithMocks;
    }

    private GivenOrWhenStep interpretAttribute(GivenOrWhenStep initial, Attribute attribute) {
        final var importName = attribute.getImportName();

        if (attribute.getReturn() == null) {
            return initial.givenAttribute(importName);
        } else {
            final var values = attribute.getReturn().stream().map(this::getValFromReturnValue).toArray(Val[]::new);

            final var duration = Duration.ofSeconds(Optional.ofNullable(attribute.getAmount()).map(TemporalAmount::getSeconds).orElse(0));
            if(duration.isZero()) {
                return initial.givenAttribute(importName, values);
            }
            return initial.givenAttribute(importName, duration, values);
        }
    }

    private GivenOrWhenStep interpretAttributeWithParameters(GivenOrWhenStep initial, AttributeWithParameters attributeWithParameters) {
        final var importName = attributeWithParameters.getImportName();
        final var parentValueMatcher = getFunctionParameterMatcherMatcherFunction(attributeWithParameters.getParentMatcher());
        final var returnValue = getValFromReturnValue(attributeWithParameters.getReturn());
        final var arguments = attributeWithParameters.getParameters();
        if(arguments.isEmpty()) {
            return initial.givenAttribute(importName, whenParentValue(parentValueMatcher), returnValue);
        }
        final var args = arguments.stream().map(this::getValMatcherFromVal).toArray(Matcher[]::new);
        return initial.givenAttribute(importName, whenAttributeParams(parentValue(parentValueMatcher), arguments(args)), returnValue);
    }

    private GivenOrWhenStep interpretFunction(GivenOrWhenStep initial, Function function) {
        final var importName = function.getImportName();
        final var returnValue = getValFromReturnValue(function.getReturnValue());
        var timesCalled = 0;
        if(function.getAmount() instanceof Multiple multiple) {
            timesCalled = multiple.getAmount();
        } else if(function.getAmount() instanceof Once) {
            timesCalled = 1;
        }
        final var parameters = interpretFunctionParameters(function.getParameters());

        if (timesCalled == 0) {
            if(parameters != null) {
                return initial.givenFunction(importName, parameters, returnValue);
            }
            return initial.givenFunction(importName, returnValue);
        } else {
            final var verification = Imports.times(timesCalled);
            if(parameters != null) {
                return initial.givenFunction(importName, parameters, returnValue, verification);
            }
            return initial.givenFunction(importName, returnValue, verification);
        }
    }

    private FunctionParameters interpretFunctionParameters(io.sapl.test.grammar.sAPLTest.FunctionParameters functionParameters) {
        if(functionParameters == null) {
            return null;
        }

        final var matchers = functionParameters.getMatchers().stream().map(this::getFunctionParameterMatcherMatcherFunction).toArray(Matcher[]::new);

        return new FunctionParameters(matchers);
    }

    private Matcher<Val> getFunctionParameterMatcherMatcherFunction(FunctionParameterMatcher functionParameterMatcher) {
        if (functionParameterMatcher instanceof Equals equals) {
            return is(getValFromReturnValue(equals.getValue()));
        } else if (functionParameterMatcher instanceof Any) {
            return anyVal();
        }
        return null;
    }

    private GivenOrWhenStep interpretFunctionInvokedOnce(GivenOrWhenStep initial, FunctionInvokedOnce function) {
        final var importName = function.getImportName();
        final var returnValues = function.getReturn().stream().map(this::getValFromReturnValue).toArray(Val[]::new);

        if (returnValues.length == 1) {
            return initial.givenFunctionOnce(importName, returnValues[0]);
        } else
            return initial.givenFunctionOnce(importName, returnValues);
    }

    private void handleFixtureRegistrations(SaplUnitTestFixture fixture, List<GivenStep> fixtureRegistrations) throws InitializationException {
        if (fixtureRegistrations == null) {
            return;
        }
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

    private Val getValFromReturnValue(io.sapl.test.grammar.sAPLTest.Val value) {
        if (value instanceof IntVal intVal) {
            return Val.of(intVal.getValue());
        } else if (value instanceof StringVal stringVal) {
            return Val.of(stringVal.getValue());
        } else if (value instanceof BoolVal boolVal) {
            return Val.of(boolVal.isIsTrue());
        }
        return null;
    }

    private Matcher<Val> getValMatcherFromVal(io.sapl.test.grammar.sAPLTest.Val value) {
        if (value instanceof IntVal intVal) {
            return val(intVal.getValue());
        } else if (value instanceof StringVal stringVal) {
            return val(stringVal.getValue());
        } else if (value instanceof BoolVal boolVal) {
            return val(boolVal.isIsTrue());
        }
        return null;
    }
}
