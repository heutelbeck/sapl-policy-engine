package io.sapl.test.dsl.interpreter;

import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.WhenStep;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class DefaultExpectStepConstructor {

    private final AuthorizationSubscriptionInterpreter authorizationSubscriptionInterpreter;

    ExpectStep constructExpectStep(final TestCase testCase, final WhenStep whenStep) {
        if (testCase == null || whenStep == null) {
            throw new SaplTestException("TestCase or whenStep is null");
        }

        final var dslWhenStep = testCase.getWhenStep();

        if (dslWhenStep == null) {
            throw new SaplTestException("TestCase does not contain a whenStep");
        }

        final var authorizationSubscription = dslWhenStep.getAuthorizationSubscription();

        if (authorizationSubscription == null) {
            throw new SaplTestException("No AuthorizationSubscription found");
        }

        final var mappedAuthorizationSubscription = authorizationSubscriptionInterpreter.constructAuthorizationSubscription(authorizationSubscription);

        return whenStep.when(mappedAuthorizationSubscription);
    }
}
