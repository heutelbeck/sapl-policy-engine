package io.sapl.api.pdp;

import java.util.function.Function;

@FunctionalInterface
public interface TracedDecisionInterceptor
		extends Function<TracedDecision, TracedDecision>, Comparable<TracedDecisionInterceptor> {
	default Integer getPriority() {
		return 0;
	}

	@Override
	default int compareTo(TracedDecisionInterceptor other) {
		return getPriority().compareTo(other.getPriority());
	}
}
