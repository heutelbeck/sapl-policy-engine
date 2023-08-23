package io.sapl.test.services;

import io.sapl.test.grammar.sAPLTest.Attribute;
import io.sapl.test.grammar.sAPLTest.AttributeWithParameters;
import io.sapl.test.grammar.sAPLTest.Function;
import io.sapl.test.grammar.sAPLTest.FunctionInvokedOnce;
import io.sapl.test.grammar.sAPLTest.GivenStep;
import io.sapl.test.grammar.sAPLTest.VirtualTime;
import io.sapl.test.interfaces.GivenStepBuilder;
import io.sapl.test.steps.GivenOrWhenStep;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class GivenStepBuilderServiceDefaultImpl implements GivenStepBuilder {

    private final FunctionInterpreter functionInterpreter;
    private final AttributeInterpreter attributeInterpreter;

    @Override
    public GivenOrWhenStep constructWhenStep(List<GivenStep> givenSteps, GivenOrWhenStep saplUnitTestFixture) {
        if (givenSteps == null || givenSteps.isEmpty()) {
            return saplUnitTestFixture;
        }

        return applyGivenSteps(givenSteps, saplUnitTestFixture);
    }

    private GivenOrWhenStep applyGivenSteps(List<GivenStep> givenSteps, GivenOrWhenStep fixtureWithMocks) {
        for (GivenStep givenStep : givenSteps) {
            if (givenStep instanceof Function function) {
                fixtureWithMocks = functionInterpreter.interpretFunction(fixtureWithMocks, function);
            } else if (givenStep instanceof FunctionInvokedOnce functionInvokedOnce) {
                fixtureWithMocks = functionInterpreter.interpretFunctionInvokedOnce(fixtureWithMocks, functionInvokedOnce);
            } else if (givenStep instanceof Attribute attribute) {
                fixtureWithMocks = attributeInterpreter.interpretAttribute(fixtureWithMocks, attribute);
            } else if (givenStep instanceof AttributeWithParameters attributeWithParameters) {
                fixtureWithMocks = attributeInterpreter.interpretAttributeWithParameters(fixtureWithMocks, attributeWithParameters);
            } else if (givenStep instanceof VirtualTime) {
                fixtureWithMocks = fixtureWithMocks.withVirtualTime();
            }
        }
        return fixtureWithMocks;
    }

}
