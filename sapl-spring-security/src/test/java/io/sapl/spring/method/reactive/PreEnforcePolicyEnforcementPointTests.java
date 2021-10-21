package io.sapl.spring.method.reactive;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.constraints.api.ConsumerConstraintHandlerProvider;
import io.sapl.spring.constraints.api.ErrorHandlerProvider;
import io.sapl.spring.constraints.api.ErrorMappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.RequestHandlerProvider;
import io.sapl.spring.constraints.api.RunnableConstraintHandlerProvider;
import io.sapl.spring.constraints.api.SubscriptionHandlerProvider;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class PreEnforcePolicyEnforcementPointTests {
	private final static JsonNodeFactory JSON = JsonNodeFactory.instance;
	private final static ObjectMapper MAPPER = new ObjectMapper();

	List<RunnableConstraintHandlerProvider> globalRunnableProviders;
	List<ConsumerConstraintHandlerProvider<?>> globalConsumerProviders;
	List<SubscriptionHandlerProvider> globalSubscriptionHandlerProviders;
	List<RequestHandlerProvider> globalRequestHandlerProviders;
	List<MappingConstraintHandlerProvider<?>> globalMappingHandlerProviders;
	List<ErrorMappingConstraintHandlerProvider> globalErrorMappingHandlerProviders;
	List<ErrorHandlerProvider> globalErrorHandlerProviders;

	@BeforeEach
	void beforeEach() {
		globalRunnableProviders = new LinkedList<>();
		globalConsumerProviders = new LinkedList<>();
		globalSubscriptionHandlerProviders = new LinkedList<>();
		globalRequestHandlerProviders = new LinkedList<>();
		globalMappingHandlerProviders = new LinkedList<>();
		globalErrorMappingHandlerProviders = new LinkedList<>();
		globalErrorHandlerProviders = new LinkedList<>();
	}

	private ConstraintEnforcementService buildConstraintHandlerService() {
		return new ConstraintEnforcementService(globalRunnableProviders, globalConsumerProviders,
				globalSubscriptionHandlerProviders, globalRequestHandlerProviders, globalMappingHandlerProviders,
				globalErrorMappingHandlerProviders, globalErrorHandlerProviders, MAPPER);
	}

	@Test
	void when_Deny_ErrorIsRaisedAndStreamCompleteEvenWithOnErrorContinue() {
		var constraintsService = buildConstraintHandlerService();
		var decisions = Flux.just(AuthorizationDecision.DENY);
		var resourceAccessPoint = Flux.just(1, 2, 3);
		var onErrorContinue = errorAndCauseConsumer();
		var doOnError = errorConsumer();
		var sut = new PreEnforcePolicyEnforcementPoint(constraintsService).enforce(decisions, resourceAccessPoint,
				Integer.class);

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
		var constraintsService = buildConstraintHandlerService();
		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var resourceAccessPoint = Flux.just(1, 2, 3);
		var onErrorContinue = errorAndCauseConsumer();
		var doOnError = errorConsumer();
		var sut = new PreEnforcePolicyEnforcementPoint(constraintsService).enforce(decisions, resourceAccessPoint,
				Integer.class);

		StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue)).expectNext(1, 2, 3)
				.verifyComplete();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(0)).accept(any());
	}

	@Test
	void when_PermitWithObligations_and_allObligationsSucceed_then_AccessIsGranted() {
		var handler = spy(new SubscriptionHandlerProvider() {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Consumer<Subscription> getHandler(JsonNode constraint) {
				return this::accept;
			}

			public void accept(Subscription s) {
				// NOOP
			}

		});
		this.globalSubscriptionHandlerProviders.add(handler);
		var constraintsService = buildConstraintHandlerService();
		var decisions = decisionFluxOnePermitWithObligation();
		var resourceAccessPoint = Flux.just(1, 2, 3);
		var onErrorContinue = errorAndCauseConsumer();
		var doOnError = errorConsumer();
		var sut = new PreEnforcePolicyEnforcementPoint(constraintsService).enforce(decisions, resourceAccessPoint,
				Integer.class);

		StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue)).expectNext(1, 2, 3)
				.verifyComplete();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(0)).accept(any());
		verify(handler, times(1)).accept(any());
	}

	@Test
	void when_PermitWithObligations_and_oneObligationFailsMidStream_thenAccessIsDeniedOnFailure_notRecoverable_noLeaks() {
		var handler = spy(new MappingConstraintHandlerProvider<Integer>() {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Class<Integer> getSupportedType() {
				return Integer.class;
			}

			@Override
			public Function<Integer, Integer> getHandler(JsonNode constraint) {
				return s -> {
					if (s == 2)
						throw new IllegalArgumentException("I FAILED TO OBLIGE");
					return s + constraint.asInt();
				};
			}
		});
		this.globalMappingHandlerProviders.add(handler);
		var constraintsService = buildConstraintHandlerService();
		var decisions = decisionFluxOnePermitWithObligation();
		var resourceAccessPoint = Flux.just(1, 2, 3);
		var onErrorContinue = errorAndCauseConsumer();
		var doOnError = errorConsumer();
		var sut = new PreEnforcePolicyEnforcementPoint(constraintsService).enforce(decisions, resourceAccessPoint,
				Integer.class);

		StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue)).expectNext(10001)
				.expectError(AccessDeniedException.class).verify();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(1)).accept(any());
	}

	@Test
	void when_PermitWithResource_thenAccessIsGrantedAndOnlyResourceFromPolicyInStream() {
		var handler = spy(new MappingConstraintHandlerProvider<Integer>() {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Class<Integer> getSupportedType() {
				return Integer.class;
			}

			@Override
			public Function<Integer, Integer> getHandler(JsonNode constraint) {
				return s -> {
					return s + constraint.asInt();
				};
			}
		});
		this.globalMappingHandlerProviders.add(handler);
		var constraintsService = buildConstraintHandlerService();
		var obligations = JSON.arrayNode();
		obligations.add(JSON.numberNode(420));
		var decisions = Flux
				.just(AuthorizationDecision.PERMIT.withObligations(obligations).withResource(JSON.numberNode(69)));
		var resourceAccessPoint = Flux.just(1, 2, 3);
		var onErrorContinue = errorAndCauseConsumer();
		var doOnError = errorConsumer();
		var sut = new PreEnforcePolicyEnforcementPoint(constraintsService).enforce(decisions, resourceAccessPoint,
				Integer.class);

		StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue)).expectNext(489).verifyComplete();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(0)).accept(any());
	}

	@Test
	void when_PermitWithResource_and_typeMismatch_thenAccessIsDenied() {
		var constraintsService = buildConstraintHandlerService();
		var decisions = Flux.just(AuthorizationDecision.PERMIT.withResource(JSON.textNode("I CAUSE A TYPE MISMATCH")));
		var resourceAccessPoint = Flux.just(1, 2, 3);
		var onErrorContinue = errorAndCauseConsumer();
		var doOnError = errorConsumer();
		var sut = new PreEnforcePolicyEnforcementPoint(constraintsService).enforce(decisions, resourceAccessPoint,
				Integer.class);

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
