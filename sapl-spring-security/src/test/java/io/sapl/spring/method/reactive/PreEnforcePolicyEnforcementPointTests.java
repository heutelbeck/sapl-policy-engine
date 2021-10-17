package io.sapl.spring.method.reactive;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.constraints.AbstractConstraintHandler;
import io.sapl.spring.constraints.ReactiveConstraintEnforcementService;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class PreEnforcePolicyEnforcementPointTests {
	private final static JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Test
	void when_Deny_ErrorIsRaisedAndStreamCompleteEvenWithOnErrorContinue() {
		var constraintsService = new ReactiveConstraintEnforcementService(List.of());
		var mapper = new ObjectMapper();
		var decisions = Flux.just(AuthorizationDecision.DENY);
		Flux<Object> resourceAccessPoint = Flux.just(1, 2, 3);
		var onErrorContinue = errorAndCauseConsumer();
		var doOnError = errorConsumer();
		var sut = new PreEnforcePolicyEnforcementPoint(constraintsService, mapper).enforce(decisions,
				resourceAccessPoint, Integer.class);

		StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue))
				.expectError(AccessDeniedException.class).verify();

		// onErrorContinue is only invoked, if there is a recoverable operator upstream
		// here there is no 'cause' event from the RAP that could be handed over to the
		// errorAndCauseConsumer
		verify(onErrorContinue, times(0)).accept(any(), any());
		// the error can still be consumed via doOnError
		verify(doOnError, times(1)).accept(any());
	}

	@Test
	void when_Permit_AccessIsGranted() {
		var constraintsService = new ReactiveConstraintEnforcementService(List.of());
		var mapper = new ObjectMapper();
		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		Flux<Object> resourceAccessPoint = Flux.just(1, 2, 3);
		var onErrorContinue = errorAndCauseConsumer();
		var doOnError = errorConsumer();
		var sut = new PreEnforcePolicyEnforcementPoint(constraintsService, mapper).enforce(decisions,
				resourceAccessPoint, Integer.class);

		StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue)).expectNext(1, 2, 3)
				.verifyComplete();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(0)).accept(any());
	}

	@Test
	void when_PermitWithObligations_and_allObligationsSucceed_then_AccessIsGranted() {
		var handler = spy(new AbstractConstraintHandler(1) {
			@Override
			public Consumer<Subscription> onSubscribe(JsonNode constraint) {
				return s -> {
				};
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		});
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));
		var mapper = new ObjectMapper();
		var decisions = decisionFluxOnePermitWithObligation();
		Flux<Object> resourceAccessPoint = Flux.just(1, 2, 3);
		var onErrorContinue = errorAndCauseConsumer();
		var doOnError = errorConsumer();
		var sut = new PreEnforcePolicyEnforcementPoint(constraintsService, mapper).enforce(decisions,
				resourceAccessPoint, Integer.class);

		StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue)).expectNext(1, 2, 3)
				.verifyComplete();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(0)).accept(any());
		verify(handler, times(1)).onSubscribe(any());

	}

	@Test
	void when_PermitWithObligations_then_ObligationsAreApplied_and_AccessIsGranted() {
		var handler = spy(new AbstractConstraintHandler(1) {
			@Override
			@SuppressWarnings("unchecked")
			public Function<Integer, Integer> onNextMap(JsonNode constraint) {
				return s -> s + constraint.asInt();
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		});
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));
		var mapper = new ObjectMapper();
		var decisions = decisionFluxOnePermitWithObligation();
		Flux<Object> resourceAccessPoint = Flux.just(1, 2, 3);
		var onErrorContinue = errorAndCauseConsumer();
		var doOnError = errorConsumer();
		var sut = new PreEnforcePolicyEnforcementPoint(constraintsService, mapper).enforce(decisions,
				resourceAccessPoint, Integer.class);

		StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue)).expectNext(10001, 10002, 10003)
				.verifyComplete();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(0)).accept(any());
		verify(handler, times(1)).onNextMap(any());
	}

	@Test
	void when_PermitWithObligations_and_oneObligationFailsMidStream_thenAccessIsDeniedOnFailure_notRecoverable_noCauseLeaks() {
		var handler = spy(new AbstractConstraintHandler(1) {
			@Override
			@SuppressWarnings("unchecked")
			public Function<Integer, Integer> onNextMap(JsonNode constraint) {
				return s -> {
					if (s == 2)
						throw new IllegalArgumentException("I FAILED TO OBLIGE");
					return s + constraint.asInt();
				};
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}
		});
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));
		var mapper = new ObjectMapper();
		var decisions = decisionFluxOnePermitWithObligation();
		Flux<Object> resourceAccessPoint = Flux.just(1, 2, 3);
		var onErrorContinue = errorAndCauseConsumer();
		var doOnError = errorConsumer();
		var sut = new PreEnforcePolicyEnforcementPoint(constraintsService, mapper).enforce(decisions,
				resourceAccessPoint, Integer.class);

		StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue)).expectNext(10001)
				.expectError(AccessDeniedException.class).verify();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(1)).accept(any());
		verify(handler, times(1)).onNextMap(any());
	}

	@Test
	void when_PermitWithResource_thenAccessIsGrantedAndOnlyResourceFromPolicyInStream() {
		var handler = spy(new AbstractConstraintHandler(1) {
			@Override
			@SuppressWarnings("unchecked")
			public Function<Integer, Integer> onNextMap(JsonNode constraint) {
				return s -> s + constraint.asInt();
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}
		});
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));
		var mapper = new ObjectMapper();
		var obligations = JSON.arrayNode();
		obligations.add(JSON.numberNode(420));
		var decisions = Flux
				.just(AuthorizationDecision.PERMIT.withObligations(obligations).withResource(JSON.numberNode(69)));
		Flux<Object> resourceAccessPoint = Flux.just(1, 2, 3);
		var onErrorContinue = errorAndCauseConsumer();
		var doOnError = errorConsumer();
		var sut = new PreEnforcePolicyEnforcementPoint(constraintsService, mapper).enforce(decisions,
				resourceAccessPoint, Integer.class);

		StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue)).expectNext(489).verifyComplete();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(0)).accept(any());
		verify(handler, times(1)).onNextMap(any());
	}

	@Test
	void when_PermitWithResource_and_typeMismatch_thenAccessIsGrantedAndOnlyResourceFromPolicyInStream() {
		var constraintsService = new ReactiveConstraintEnforcementService(List.of());
		var mapper = new ObjectMapper();
		var decisions = Flux.just(AuthorizationDecision.PERMIT.withResource(JSON.textNode("I CAUSE A TYPE MISMATCH")));
		Flux<Object> resourceAccessPoint = Flux.just(1, 2, 3);
		var onErrorContinue = errorAndCauseConsumer();
		var doOnError = errorConsumer();
		var sut = new PreEnforcePolicyEnforcementPoint(constraintsService, mapper).enforce(decisions,
				resourceAccessPoint, Integer.class);

		StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue))
				.expectError(AccessDeniedException.class).verify();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(1)).accept(any());
	}

	public Flux<AuthorizationDecision> decisionFluxOnePermitWithObligation() {
		var plus10000 = JSON.numberNode(10000L);
		var obligation = JSON.arrayNode();
		obligation.add(plus10000);
		return Flux.just(AuthorizationDecision.PERMIT.withObligations(obligation));
	}

	@SuppressWarnings("unchecked")
	private BiConsumer<Throwable, Object> errorAndCauseConsumer() {
		return (BiConsumer<Throwable, Object>) mock(BiConsumer.class);
	}

	@SuppressWarnings("unchecked")
	private Consumer<Throwable> errorConsumer() {
		return (Consumer<Throwable>) mock(Consumer.class);
	}
}
