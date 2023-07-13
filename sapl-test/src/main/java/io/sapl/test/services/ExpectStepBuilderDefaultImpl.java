package io.sapl.test.services;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.test.grammar.sAPLTest.AuthzSubscription;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.interfaces.ExpectStepBuilder;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.WhenStep;

public final class ExpectStepBuilderDefaultImpl implements ExpectStepBuilder {

    @Override
    public ExpectStep constructExpectStep(TestCase testCase, WhenStep whenStep) {
        if (testCase.getWhenStep().getAuthorizationSubscription() instanceof AuthzSubscription authzSubscription) {
            return whenStep.when(getAuthorizationSubscriptionFromDSL(authzSubscription));
        }
        return null;
    }

    private AuthorizationSubscription getAuthorizationSubscriptionFromDSL(AuthzSubscription authorizationSubscription) {
        return AuthorizationSubscription.of(authorizationSubscription.getSubject(), authorizationSubscription.getAction(), authorizationSubscription.getResource());
    }
}
