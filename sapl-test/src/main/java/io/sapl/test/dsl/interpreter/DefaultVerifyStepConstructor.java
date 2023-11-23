package io.sapl.test.dsl.interpreter;

import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sAPLTest.RepeatedExpect;
import io.sapl.test.grammar.sAPLTest.SingleExpect;
import io.sapl.test.grammar.sAPLTest.SingleExpectWithMatcher;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.steps.ExpectOrVerifyStep;
import io.sapl.test.steps.VerifyStep;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class DefaultVerifyStepConstructor {

    private final ExpectInterpreter expectInterpreter;

    public VerifyStep constructVerifyStep(final TestCase testCase, final ExpectOrVerifyStep expectStep) {
        final var expect = testCase.getExpect();

        if (expect instanceof SingleExpect singleExpect) {
            return expectInterpreter.interpretSingleExpect(expectStep, singleExpect);
        } else if (expect instanceof SingleExpectWithMatcher singleExpectWithMatcher) {
            return expectInterpreter.interpretSingleExpectWithMatcher(expectStep, singleExpectWithMatcher);
        } else if (expect instanceof RepeatedExpect repeatedExpect) {
            return expectInterpreter.interpretRepeatedExpect(expectStep, repeatedExpect);
        } else {
            throw new SaplTestException("Unknown type of ExpectChain");
        }
    }
}
