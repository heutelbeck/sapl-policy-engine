package io.sapl.test.services;

import io.sapl.test.grammar.sAPLTest.AuthorizationSubscription;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.interfaces.ExpectStepBuilder;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.WhenStep;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class ExpectStepBuilderDefaultImpl implements ExpectStepBuilder {

    private final ValInterpreter valInterpreter;

    @Override
    public ExpectStep constructExpectStep(TestCase testCase, WhenStep whenStep) {
        final var authorizationSubscription = getAuthorizationSubscriptionFromDSL(testCase.getWhenStep().getAuthorizationSubscription());
        return whenStep.when(authorizationSubscription);
    }

    private io.sapl.api.pdp.AuthorizationSubscription getAuthorizationSubscriptionFromDSL(AuthorizationSubscription authorizationSubscription) {
        final var subject = valInterpreter.getValFromReturnValue(authorizationSubscription.getSubject());
        final var action = valInterpreter.getValFromReturnValue(authorizationSubscription.getAction());
        final var resource = valInterpreter.getValFromReturnValue(authorizationSubscription.getResource());

        return io.sapl.api.pdp.AuthorizationSubscription.of(subject.get(), action.get(), resource.get());
    }

}
