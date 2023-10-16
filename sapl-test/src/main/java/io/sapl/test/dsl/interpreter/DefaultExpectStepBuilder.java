package io.sapl.test.dsl.interpreter;

import io.sapl.test.dsl.interfaces.ExpectStepBuilder;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.WhenStep;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class DefaultExpectStepBuilder implements ExpectStepBuilder {

    private final AuthorizationSubscriptionInterpreter authorizationSubscriptionInterpreter;

    @Override
    public ExpectStep constructExpectStep(final TestCase testCase, final WhenStep whenStep) {
        final var authorizationSubscription = testCase.getWhenStep().getAuthorizationSubscription();
        final var mappedAuthorizationSubscription = authorizationSubscriptionInterpreter.getAuthorizationSubscriptionFromDSL(authorizationSubscription);
        return whenStep.when(mappedAuthorizationSubscription);
    }
}
