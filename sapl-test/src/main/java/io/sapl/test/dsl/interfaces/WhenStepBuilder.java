package io.sapl.test.dsl.interfaces;

import io.sapl.test.grammar.sAPLTest.GivenStep;
import io.sapl.test.steps.GivenOrWhenStep;
import io.sapl.test.steps.WhenStep;
import java.util.List;

public interface WhenStepBuilder {
    WhenStep constructWhenStep(List<GivenStep> givenSteps, GivenOrWhenStep fixture);
}
