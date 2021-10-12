package io.sapl.spring.method.reactive;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.constraints.ReactiveConstraintEnforcementService;
import reactor.core.publisher.Flux;

public class EnforceTillDeniedPolicyEnforcementPointTests {

	@Test
	@Disabled
	public void alternatingAdvice() throws InterruptedException {
		var constraintsService = new ReactiveConstraintEnforcementService(List.of());
		var decisions = decisionFluxWithChangeingAdvice();
		var data = Flux.interval(Duration.ofMillis(100L));
		EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService).take(10).blockLast();
	}

	public Flux<AuthorizationDecision> decisionFluxWithChangeingAdvice() {
		var json = JsonNodeFactory.instance;
		var advicePlus10000 = json.numberNode(10000L);
		var advicePlus50000 = json.numberNode(50000L);
		var firstAdvice = json.arrayNode();
		firstAdvice.add(advicePlus10000);
		var secondAdvice = json.arrayNode();
		secondAdvice.add(advicePlus50000);

		return Flux
				.just(AuthorizationDecision.PERMIT.withAdvice(firstAdvice),
						AuthorizationDecision.PERMIT.withAdvice(secondAdvice))
				.repeat().delayElements(Duration.ofMillis(500L));
	}
}
