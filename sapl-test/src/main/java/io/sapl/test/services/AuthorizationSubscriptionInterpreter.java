package io.sapl.test.services;

import io.sapl.test.grammar.sAPLTest.AuthorizationSubscription;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AuthorizationSubscriptionInterpreter {

    private final ValInterpreter valInterpreter;

    io.sapl.api.pdp.AuthorizationSubscription getAuthorizationSubscriptionFromDSL(final AuthorizationSubscription authorizationSubscription) {
        final var subject = valInterpreter.getValFromReturnValue(authorizationSubscription.getSubject());
        final var action = valInterpreter.getValFromReturnValue(authorizationSubscription.getAction());
        final var resource = valInterpreter.getValFromReturnValue(authorizationSubscription.getResource());

        final var environmentValue = authorizationSubscription.getEnvironment();

        if (environmentValue == null) {
            return io.sapl.api.pdp.AuthorizationSubscription.of(subject.get(), action.get(), resource.get());
        }

        final var environment = valInterpreter.getValFromReturnValue(environmentValue);
        return io.sapl.api.pdp.AuthorizationSubscription.of(subject.get(), action.get(), resource.get(), environment.get());
    }
}
