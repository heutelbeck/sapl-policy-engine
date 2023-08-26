package io.sapl.test.services;

import io.sapl.test.grammar.sAPLTest.RepeatedExpect;
import io.sapl.test.grammar.sAPLTest.SingleExpect;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.interfaces.VerifyStepBuilder;
import io.sapl.test.steps.ExpectOrVerifyStep;
import io.sapl.test.steps.VerifyStep;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class VerifyStepBuilderServiceDefaultImpl implements VerifyStepBuilder {

    private final ExpectInterpreter expectInterpreter;

    @Override
    public VerifyStep constructVerifyStep(final TestCase testCase, final ExpectOrVerifyStep expectStep) {
        final var expect = testCase.getExpect();

        if (expect instanceof SingleExpect singleExpect) {
            return expectInterpreter.interpretSingleExpect(expectStep, singleExpect);
        } else if (expect instanceof RepeatedExpect repeatedExpect) {
            return expectInterpreter.interpretRepeatedExpect(expectStep, repeatedExpect);
        } else {
            throw new RuntimeException("TestCase does not contain known expect");
        }
    }
}
