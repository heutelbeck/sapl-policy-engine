package io.sapl.test.dsl.interpreter;


import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.test.SaplTestException;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AuthorizationSubscriptionInterpreter {

    private final ValInterpreter valInterpreter;

    AuthorizationSubscription constructAuthorizationSubscription(final io.sapl.test.grammar.sAPLTest.AuthorizationSubscription authorizationSubscription) {
        if (authorizationSubscription == null) {
            throw new SaplTestException("AuthorizationSubscription is null");
        }

        final var subject = valInterpreter.getValFromValue(authorizationSubscription.getSubject());
        final var action = valInterpreter.getValFromValue(authorizationSubscription.getAction());
        final var resource = valInterpreter.getValFromValue(authorizationSubscription.getResource());

        final var environmentValue = authorizationSubscription.getEnvironment();

        if (environmentValue == null) {
            return AuthorizationSubscription.of(subject.get(), action.get(), resource.get());
        }

        final var environment = valInterpreter.getValFromValue(environmentValue);

        return AuthorizationSubscription.of(subject.get(), action.get(), resource.get(), environment.get());
    }
}
