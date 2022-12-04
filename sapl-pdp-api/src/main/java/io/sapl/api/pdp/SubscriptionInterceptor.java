package io.sapl.api.pdp;

import java.util.function.Function;

@FunctionalInterface
public interface SubscriptionInterceptor extends Function<AuthorizationSubscription, AuthorizationSubscription> {
}
