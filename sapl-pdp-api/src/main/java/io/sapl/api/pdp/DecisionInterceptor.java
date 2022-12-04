package io.sapl.api.pdp;

import java.util.function.Function;

import org.reactivestreams.Publisher;

@FunctionalInterface
public interface DecisionInterceptor extends Function<AuthorizationDecision, Publisher<AuthorizationDecision>> {
}
