package io.sapl.test.services;

import io.sapl.test.grammar.sAPLTest.Attribute;
import io.sapl.test.grammar.sAPLTest.AttributeWithParameters;
import io.sapl.test.grammar.sAPLTest.Function;
import io.sapl.test.grammar.sAPLTest.FunctionInvokedOnce;
import io.sapl.test.grammar.sAPLTest.GivenStep;
import io.sapl.test.grammar.sAPLTest.VirtualTime;
import io.sapl.test.interfaces.WhenStepBuilder;
import io.sapl.test.steps.GivenOrWhenStep;
import io.sapl.test.steps.WhenStep;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class WhenStepBuilderServiceDefaultImpl implements WhenStepBuilder {

    private final FunctionInterpreter functionInterpreter;
    private final AttributeInterpreter attributeInterpreter;

    @Override
    public WhenStep constructWhenStep(final List<GivenStep> givenSteps, final GivenOrWhenStep givenOrWhenStep) {
        if (givenSteps == null || givenSteps.isEmpty()) {
            return givenOrWhenStep;
        }

        return applyGivenSteps(givenSteps, givenOrWhenStep);
    }

    private WhenStep applyGivenSteps(final List<GivenStep> givenSteps, GivenOrWhenStep fixtureWithMocks) {
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
