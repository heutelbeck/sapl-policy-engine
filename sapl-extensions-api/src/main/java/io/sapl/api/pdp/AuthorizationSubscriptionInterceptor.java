package io.sapl.api.pdp;

import java.util.function.Function;

@FunctionalInterface
public interface AuthorizationSubscriptionInterceptor
		extends Function<AuthorizationSubscription, AuthorizationSubscription>,
		Comparable<AuthorizationSubscriptionInterceptor> {
	default Integer getPriority() {
		return 0;
	}

	@Override
	default int compareTo(AuthorizationSubscriptionInterceptor other) {
		return getPriority().compareTo(other.getPriority());
	}

}
