package io.sapl.spring.method.reactive;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.constraints.ReactiveConstraintEnforcementService;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class EnforceTillDeniedPolicyEnforcementPointTests {

	@Test
	void when_subscribingTwice_Fails() {
		var constraintsService = new ReactiveConstraintEnforcementService(List.of());
		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var data = Flux.just(1, 2, 3);
		Flux<Object> sut = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);
		sut.blockLast();
		assertThrows(IllegalStateException.class, () -> sut.blockLast());
	}

	@Test
	void when_onlyOnePermit_thenAllSignalsGetThrough() {
		var constraintsService = new ReactiveConstraintEnforcementService(List.of());
		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var data = Flux.just(1, 2, 3);
		Flux<Object> sut = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);
		StepVerifier.create(sut).expectNext(1, 2, 3).verifyComplete();
	}

	@Test
	void when_onlyOneDeny_thenNoSignalsAndAccessDenied() {
		var constraintsService = new ReactiveConstraintEnforcementService(List.of());
		var decisions = Flux.just(AuthorizationDecision.DENY);
		var data = Flux.just(1, 2, 3);
		Flux<Object> sut = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);
		StepVerifier.create(sut).expectError(AccessDeniedException.class).verify();
	}

	@Test
	void when_onlyOneDeny_thenNoSignalsAndAccessDeniedOnErrorContinueDoesNotLeakData() {
		var constraintsService = new ReactiveConstraintEnforcementService(List.of());
		var decisions = Flux.just(AuthorizationDecision.DENY);
		var data = Flux.just(1, 2, 3);
		Flux<Object> sut = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);
		StepVerifier.create(sut.onErrorContinue((a, b) -> {
		})).expectError(AccessDeniedException.class).verify();
	}

	@Test
	void when_firstPermitThenDeny_thenDignalssPassThroughTillDenied() throws InterruptedException {
		StepVerifier.withVirtualTime(this::scenario_firstPermitThenDeny_thenDignalssPassThroughTillDenied)
				.thenAwait(Duration.ofMillis(200L)).expectNext(1, 2).expectError(AccessDeniedException.class).verify();
	}

	private Flux<Object> scenario_firstPermitThenDeny_thenDignalssPassThroughTillDenied() {
		var constraintsService = new ReactiveConstraintEnforcementService(List.of());
		var decisions = Flux.just(AuthorizationDecision.PERMIT, AuthorizationDecision.DENY)
				.delayElements(Duration.ofMillis(50L));
		var data = Flux.just(1, 2, 3).delayElements(Duration.ofMillis(20L));
		return EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);
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
