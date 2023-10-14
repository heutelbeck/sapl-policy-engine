package io.sapl.test.services;

import io.sapl.test.grammar.sAPLTest.AuthorizationSubscription;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AuthorizationSubscriptionInterpreter {

    private final ValInterpreter valInterpreter;

    io.sapl.api.pdp.AuthorizationSubscription getAuthorizationSubscriptionFromDSL(final AuthorizationSubscription authorizationSubscription) {
        final var subject = valInterpreter.getValFromValue(authorizationSubscription.getSubject());
        final var action = valInterpreter.getValFromValue(authorizationSubscription.getAction());
        final var resource = valInterpreter.getValFromValue(authorizationSubscription.getResource());

        final var environmentValue = authorizationSubscription.getEnvironment();

        if (environmentValue == null) {
            return io.sapl.api.pdp.AuthorizationSubscription.of(subject.get(), action.get(), resource.get());
        }

        final var environment = valInterpreter.getValFromValue(environmentValue);
        return io.sapl.api.pdp.AuthorizationSubscription.of(subject.get(), action.get(), resource.get(), environment.get());
    }
}
