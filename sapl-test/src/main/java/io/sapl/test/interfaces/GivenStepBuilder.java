package io.sapl.test.interfaces;

import io.sapl.test.grammar.sAPLTest.GivenStep;
import io.sapl.test.steps.GivenOrWhenStep;
import io.sapl.test.steps.WhenStep;
import java.util.List;

public interface GivenStepBuilder {
    WhenStep constructWhenStep(List<GivenStep> givenSteps, GivenOrWhenStep fixture);
}
