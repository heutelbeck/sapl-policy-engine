package io.sapl.pdp.interceptors;

import org.reactivestreams.Publisher;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.DecisionInterceptor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class DecisionInterceptorChain implements DecisionInterceptor {

	@Override
	public Publisher<AuthorizationDecision> apply(AuthorizationDecision decision) {
		return Mono.deferContextual(ctx -> {
			System.out.println("Value of key: " + ctx.getOrEmpty("key"));
			return Mono.just(decision);
		});
	}

}
