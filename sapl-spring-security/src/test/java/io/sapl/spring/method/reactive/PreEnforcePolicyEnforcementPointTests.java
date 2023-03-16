/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.spring.method.reactive;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import org.springframework.aop.framework.ReflectiveMethodInvocation;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.constraints.api.ConsumerConstraintHandlerProvider;
import io.sapl.spring.constraints.api.ErrorHandlerProvider;
import io.sapl.spring.constraints.api.ErrorMappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.FilterPredicateConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MethodInvocationConstraintHandlerProvider;
import io.sapl.spring.constraints.api.RequestHandlerProvider;
import io.sapl.spring.constraints.api.RunnableConstraintHandlerProvider;
import io.sapl.spring.constraints.api.SubscriptionHandlerProvider;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class PreEnforcePolicyEnforcementPointTests {

	private final static JsonNodeFactory JSON = JsonNodeFactory.instance;

	private final static ObjectMapper MAPPER = new ObjectMapper();

	List<RunnableConstraintHandlerProvider> globalRunnableProviders;

	List<ConsumerConstraintHandlerProvider<?>> globalConsumerProviders;

	List<SubscriptionHandlerProvider> globalSubscriptionHandlerProviders;

	List<RequestHandlerProvider> globalRequestHandlerProviders;

	List<MappingConstraintHandlerProvider<?>> globalMappingHandlerProviders;

	List<ErrorMappingConstraintHandlerProvider> globalErrorMappingHandlerProviders;

	List<ErrorHandlerProvider> globalErrorHandlerProviders;

	List<FilterPredicateConstraintHandlerProvider> globalFilterPredicateProviders;

	List<MethodInvocationConstraintHandlerProvider> globalInvocationHandlerProviders;

	@BeforeEach
	void beforeEach() {
		globalRunnableProviders            = new LinkedList<>();
		globalConsumerProviders            = new LinkedList<>();
		globalSubscriptionHandlerProviders = new LinkedList<>();
		globalRequestHandlerProviders      = new LinkedList<>();
		globalMappingHandlerProviders      = new LinkedList<>();
		globalErrorMappingHandlerProviders = new LinkedList<>();
		globalErrorHandlerProviders        = new LinkedList<>();
		globalFilterPredicateProviders     = new LinkedList<>();
		globalInvocationHandlerProviders   = new LinkedList<>();
	}

	private ConstraintEnforcementService buildConstraintHandlerService() {
		return new ConstraintEnforcementService(globalRunnableProviders, globalConsumerProviders,
				globalSubscriptionHandlerProviders, globalRequestHandlerProviders, globalMappingHandlerProviders,
				globalErrorMappingHandlerProviders, globalErrorHandlerProviders, globalFilterPredicateProviders,
				globalInvocationHandlerProviders, MAPPER);
	}

	@Test
	void when_Deny_ErrorIsRaisedAndStreamCompleteEvenWithOnErrorContinue() throws Throwable {
		var constraintsService  = buildConstraintHandlerService();
		var decisions           = Flux.just(AuthorizationDecision.DENY);
		var resourceAccessPoint = resourceAccessPointInvocation();
		var onErrorContinue     = errorAndCauseConsumer();
		var doOnError           = errorConsumer();
		var sut                 = new PreEnforcePolicyEnforcementPoint(constraintsService).enforce(decisions,
				resourceAccessPoint,
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
	void when_Permit_AccessIsGranted() throws Throwable {
		var constraintsService  = buildConstraintHandlerService();
		var decisions           = Flux.just(AuthorizationDecision.PERMIT);
		var resourceAccessPoint = resourceAccessPointInvocation();
		var onErrorContinue     = errorAndCauseConsumer();
		var doOnError           = errorConsumer();
		var sut                 = new PreEnforcePolicyEnforcementPoint(constraintsService).enforce(decisions,
				resourceAccessPoint,
				Integer.class);

		StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue)).expectNext(1, 2, 3)
				.verifyComplete();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(0)).accept(any());
	}

	@Test
	void when_PermitAndInvocationFails_thenFailureInStream() throws Throwable {
		var constraintsService  = buildConstraintHandlerService();
		var decisions           = Flux.just(AuthorizationDecision.PERMIT);
		var resourceAccessPoint = mock(ReflectiveMethodInvocation.class);
		doThrow(new IllegalArgumentException()).when(resourceAccessPoint).proceed();
		var onErrorContinue = errorAndCauseConsumer();
		var doOnError       = errorConsumer();
		var sut             = new PreEnforcePolicyEnforcementPoint(constraintsService).enforce(decisions,
				resourceAccessPoint,
				Integer.class);

		StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue))
				.expectError(IllegalArgumentException.class).verify();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(1)).accept(any());
	}

	@Test
	void when_PermitMethodInvocationObligationFail_thenAccessDenied() throws Throwable {
		var failingHandler = new MethodInvocationConstraintHandlerProvider() {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Consumer<ReflectiveMethodInvocation> getHandler(JsonNode constraint) {
				return invocation -> {
					throw new IllegalArgumentException();
				};
			}

		};
		globalInvocationHandlerProviders.add(failingHandler);
		var constraintsService  = buildConstraintHandlerService();
		var decisions           = decisionFluxOnePermitWithObligation();
		var resourceAccessPoint = resourceAccessPointInvocation();
		var onErrorContinue     = errorAndCauseConsumer();
		var doOnError           = errorConsumer();
		var sut                 = new PreEnforcePolicyEnforcementPoint(constraintsService).enforce(decisions,
				resourceAccessPoint,
				Integer.class);

		StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue))
				.expectError(AccessDeniedException.class).verify();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(1)).accept(any());
	}

	@Test
	void when_PermitMethodInvocationAdviceFail_thenAccessGranted() throws Throwable {
		var failingHandler = new MethodInvocationConstraintHandlerProvider() {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Consumer<ReflectiveMethodInvocation> getHandler(JsonNode constraint) {
				return invocation -> {
					throw new IllegalArgumentException();
				};
			}

		};
		globalInvocationHandlerProviders.add(failingHandler);
		var constraintsService  = buildConstraintHandlerService();
		var decisions           = decisionFluxOnePermitWithAdvice();
		var resourceAccessPoint = resourceAccessPointInvocation();
		var onErrorContinue     = errorAndCauseConsumer();
		var doOnError           = errorConsumer();
		var sut                 = new PreEnforcePolicyEnforcementPoint(constraintsService).enforce(decisions,
				resourceAccessPoint,
				Integer.class);

		StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue)).expectNext(1, 2, 3)
				.verifyComplete();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(0)).accept(any());
	}

	@Test
	void when_PermitWithObligations_and_allObligationsSucceed_then_AccessIsGranted() throws Throwable {
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
		var constraintsService  = buildConstraintHandlerService();
		var decisions           = decisionFluxOnePermitWithObligation();
		var resourceAccessPoint = resourceAccessPointInvocation();
		var onErrorContinue     = errorAndCauseConsumer();
		var doOnError           = errorConsumer();
		var sut                 = new PreEnforcePolicyEnforcementPoint(constraintsService).enforce(decisions,
				resourceAccessPoint,
				Integer.class);

		StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue)).expectNext(1, 2, 3)
				.verifyComplete();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(0)).accept(any());
		verify(handler, times(1)).accept(any());
	}

	@Test
	void when_PermitWithObligations_and_oneObligationFailsMidStream_thenAccessIsDeniedOnFailure_notRecoverable_noLeaks()
			throws Throwable {
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
		var constraintsService  = buildConstraintHandlerService();
		var decisions           = decisionFluxOnePermitWithObligation();
		var resourceAccessPoint = resourceAccessPointInvocation();
		var onErrorContinue     = errorAndCauseConsumer();
		var doOnError           = errorConsumer();
		var sut                 = new PreEnforcePolicyEnforcementPoint(constraintsService).enforce(decisions,
				resourceAccessPoint,
				Integer.class);

		StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue)).expectNext(10001)
				.expectError(AccessDeniedException.class).verify();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(1)).accept(any());
	}

	@Test
	void when_PermitWithResource_thenAccessIsGrantedAndOnlyResourceFromPolicyInStream() throws Throwable {
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
				return s -> s + constraint.asInt();
			}
		});
		this.globalMappingHandlerProviders.add(handler);
		var constraintsService = buildConstraintHandlerService();
		var obligations        = JSON.arrayNode();
		obligations.add(JSON.numberNode(420));
		var decisions           = Flux
				.just(AuthorizationDecision.PERMIT.withObligations(obligations).withResource(JSON.numberNode(69)));
		var resourceAccessPoint = resourceAccessPointInvocation();
		var onErrorContinue     = errorAndCauseConsumer();
		var doOnError           = errorConsumer();
		var sut                 = new PreEnforcePolicyEnforcementPoint(constraintsService).enforce(decisions,
				resourceAccessPoint,
				Integer.class);

		StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue)).expectNext(489).verifyComplete();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(0)).accept(any());
	}

	@Test
	void when_PermitWithResource_and_typeMismatch_thenAccessIsDenied() throws Throwable {
		var constraintsService  = buildConstraintHandlerService();
		var decisions           = Flux
				.just(AuthorizationDecision.PERMIT.withResource(JSON.textNode("I CAUSE A TYPE MISMATCH")));
		var resourceAccessPoint = resourceAccessPointInvocation();
		var onErrorContinue     = errorAndCauseConsumer();
		var doOnError           = errorConsumer();
		var sut                 = new PreEnforcePolicyEnforcementPoint(constraintsService).enforce(decisions,
				resourceAccessPoint,
				Integer.class);

		StepVerifier.create(sut.doOnError(doOnError).onErrorContinue(onErrorContinue))
				.expectError(AccessDeniedException.class).verify();

		verify(onErrorContinue, times(0)).accept(any(), any());
		verify(doOnError, times(1)).accept(any());
	}

	public Flux<AuthorizationDecision> decisionFluxOnePermitWithObligation() {
		var plus10000  = JSON.numberNode(10000L);
		var obligation = JSON.arrayNode();
		obligation.add(plus10000);
		return Flux.just(AuthorizationDecision.PERMIT.withObligations(obligation));
	}

	public Flux<AuthorizationDecision> decisionFluxOnePermitWithAdvice() {
		var plus10000 = JSON.numberNode(10000L);
		var advice    = JSON.arrayNode();
		advice.add(plus10000);
		return Flux.just(AuthorizationDecision.PERMIT.withAdvice(advice));
	}

	@SuppressWarnings("unchecked")
	private BiConsumer<Throwable, Object> errorAndCauseConsumer() {
		return (BiConsumer<Throwable, Object>) mock(BiConsumer.class);
	}

	@SuppressWarnings("unchecked")
	private Consumer<Throwable> errorConsumer() {
		return (Consumer<Throwable>) mock(Consumer.class);
	}

	public ReflectiveMethodInvocation resourceAccessPointInvocation() throws Throwable {
		var mock = mock(ReflectiveMethodInvocation.class);
		when(mock.proceed()).thenReturn(Flux.just(1, 2, 3));
		return mock;
	}
}
