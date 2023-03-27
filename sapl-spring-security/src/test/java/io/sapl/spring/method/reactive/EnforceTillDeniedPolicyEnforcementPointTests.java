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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;

import org.junit.jupiter.api.BeforeAll;
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
import io.sapl.spring.constraints.api.FilterPredicateConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;
import io.sapl.spring.constraints.api.MethodInvocationConstraintHandlerProvider;
import io.sapl.spring.constraints.api.RequestHandlerProvider;
import io.sapl.spring.constraints.api.RunnableConstraintHandlerProvider;
import io.sapl.spring.constraints.api.SubscriptionHandlerProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

class EnforceTillDeniedPolicyEnforcementPointTests {

	private final static ObjectMapper MAPPER = new ObjectMapper();

	private final static JsonNodeFactory JSON = JsonNodeFactory.instance;

	List<RunnableConstraintHandlerProvider> globalRunnableProviders;

	List<ConsumerConstraintHandlerProvider<?>> globalConsumerProviders;

	List<SubscriptionHandlerProvider> globalSubscriptionHandlerProviders;

	List<RequestHandlerProvider> globalRequestHandlerProviders;

	List<MappingConstraintHandlerProvider<?>> globalMappingHandlerProviders;

	List<ErrorMappingConstraintHandlerProvider> globalErrorMappingHandlerProviders;

	List<ErrorHandlerProvider> globalErrorHandlerProviders;

	List<FilterPredicateConstraintHandlerProvider> globalFilterPredicateProviders;

	List<MethodInvocationConstraintHandlerProvider> globalInvocationHandlerProviders;

	@BeforeAll
	public static void beforeAll() {
		// this eliminates excessive logging of dropped errors in case of onErrorStop()
		// downstream.
		Hooks.onErrorDropped(err -> {
		});
	}

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
	void when_subscribingTwice_Fails() {
		var constraintsService = buildConstraintHandlerService();
		var decisions          = Flux.just(AuthorizationDecision.PERMIT);
		var data               = Flux.just(1, 2, 3);
		var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
				Integer.class);
		sut.blockLast();
		assertThrows(IllegalStateException.class, sut::blockLast);
	}

	@Test
	void when_onlyOnePermit_thenAllSignalsGetThrough() {
		var constraintsService = buildConstraintHandlerService();
		var decisions          = Flux.just(AuthorizationDecision.PERMIT);
		var data               = Flux.just(1, 2, 3);
		var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
				Integer.class);
		StepVerifier.create(sut).expectNext(1, 2, 3).verifyComplete();
	}

	@Test
	void when_permitWithResource_thenReplaceAndComplete() {
		var constraintsService = buildConstraintHandlerService();
		var decisions          = Flux.just(AuthorizationDecision.PERMIT.withResource(JSON.numberNode(69)));
		var data               = Flux.just(1, 2, 3);
		var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
				Integer.class);
		StepVerifier.create(sut).expectNext(69).verifyComplete();
	}

	@Test
	void when_permitWithResource_typeMismatch_thenDenyAndComplete() {
		var constraintsService = buildConstraintHandlerService();
		var decisions          = Flux.just(AuthorizationDecision.PERMIT.withResource(JSON.textNode("NOT A NUMBER")));
		var data               = Flux.just(1, 2, 3);
		var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
				Integer.class);
		StepVerifier.create(sut).expectError(AccessDeniedException.class).verify();
	}

	@Test
	void when_endlessPermits_thenAllSignalsGetThrough() {
		StepVerifier.withVirtualTime(this::scenario_when_endlessPermits_thenAllSignalsGetThrough)
				.thenAwait(Duration.ofMillis(300L)).expectNext(1, 2, 3).verifyComplete();
	}

	private Flux<Integer> scenario_when_endlessPermits_thenAllSignalsGetThrough() {
		var constraintsService = buildConstraintHandlerService();
		var decisions          = Flux.just(AuthorizationDecision.PERMIT).repeat().delayElements(Duration.ofMillis(5L));
		var data               = Flux.just(1, 2, 3).delayElements(Duration.ofMillis(30L));
		return EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService, Integer.class);
	}

	@Test
	void when_onlyOneDeny_thenNoSignalsAndAccessDenied() {
		var constraintsService = buildConstraintHandlerService();
		var decisions          = Flux.just(AuthorizationDecision.DENY);
		var data               = Flux.just(1, 2, 3);
		var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
				Integer.class);
		StepVerifier.create(sut).expectError(AccessDeniedException.class).verify();
	}

	@Test
	void when_onlyOneDeny_thenNoSignalsAndAccessDeniedOnErrorContinueDoesNotLeakData() {
		var constraintsService = buildConstraintHandlerService();
		var decisions          = Flux.just(AuthorizationDecision.DENY);
		var data               = Flux.just(1, 2, 3);
		var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
				Integer.class);
		StepVerifier.create(sut.onErrorContinue((a, b) -> {
		})).expectError(AccessDeniedException.class).verify();
	}

	@Test
	void when_firstPermitThenDeny_thenSignalsPassThroughTillDenied() {
		StepVerifier.withVirtualTime(this::scenario_firstPermitThenDeny_thenSignalsPassThroughTillDenied)
				.thenAwait(Duration.ofMillis(200L)).expectNext(1, 2).expectError(AccessDeniedException.class).verify();
	}

	private Flux<Integer> scenario_firstPermitThenDeny_thenSignalsPassThroughTillDenied() {
		var constraintsService = buildConstraintHandlerService();
		var decisions          = Flux.just(AuthorizationDecision.PERMIT, AuthorizationDecision.DENY)
				.delayElements(Duration.ofMillis(50L));
		var data               = Flux.just(1, 2, 3).delayElements(Duration.ofMillis(20L));
		return EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService, Integer.class);
	}

	@Test
	void when_constraintsPresent_thenTheseAreHandledAndUpdated() {
		StepVerifier.withVirtualTime(this::scenario_when_constraintsPresent_thenTheseAreHandledAndUpdated)
				.thenAwait(Duration.ofMillis(1000L))
				.expectNext(10000, 10001, 10002, 10003, 10004, 50005, 50006, 50007, 50008, 50009).verifyComplete();
	}

	Flux<Integer> scenario_when_constraintsPresent_thenTheseAreHandledAndUpdated() {
		var handler = new MappingConstraintHandlerProvider<Integer>() {

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
				return number -> number + constraint.asInt();
			}

		};
		globalMappingHandlerProviders.add(handler);
		var constraintsService = buildConstraintHandlerService();
		var decisions          = decisionFluxWithChangingAdvice().delayElements(Duration.ofMillis(270L));
		var data               = Flux.range(0, 10).delayElements(Duration.ofMillis(50L));
		return EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService, Integer.class);

	}

	@Test
	void when_handlerMapsToNull_thenElementsAreDropped() {
		var handler = new MappingConstraintHandlerProvider<Integer>() {

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
				return number -> (number % 2 == 0) ? number : null;
			}

		};
		globalMappingHandlerProviders.add(handler);
		var json            = JsonNodeFactory.instance;
		var advicePlus10000 = json.numberNode(10000L);
		var firstAdvice     = json.arrayNode();
		firstAdvice.add(advicePlus10000);

		var decisions          = Flux.just(AuthorizationDecision.PERMIT.withAdvice(firstAdvice));
		var constraintsService = buildConstraintHandlerService();

		var data = Flux.range(0, 10);
		var sut  = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService, Integer.class);
		StepVerifier.create(sut).expectNext(0, 2, 4, 6, 8).verifyComplete();

	}

	@Test
	void when_handlerCancel_thenHandlerIsCalled() {
		var handler = spy(new RunnableConstraintHandlerProvider() {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Signal getSignal() {
				return Signal.ON_CANCEL;
			}

			@Override
			public Runnable getHandler(JsonNode constraint) {
				return this::run;
			}

			public void run() {
				// NOOP
			}

		});
		globalRunnableProviders.add(handler);
		var json            = JsonNodeFactory.instance;
		var advicePlus10000 = json.numberNode(10000L);
		var firstAdvice     = json.arrayNode();
		firstAdvice.add(advicePlus10000);
		var decisions          = Flux.just(AuthorizationDecision.PERMIT.withAdvice(firstAdvice));
		var constraintsService = buildConstraintHandlerService();

		var data = Flux.range(0, 10);
		var sut  = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService, Integer.class);
		StepVerifier.create(sut.take(5)).expectNext(0, 1, 2, 3, 4).verifyComplete();
		verify(handler, times(1)).run();
	}

	@Test
	void when_error_thenErrorMappedAndPropagated() {
		var handler = spy(new ErrorMappingConstraintHandlerProvider() {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Function<Throwable, Throwable> getHandler(JsonNode constraint) {
				return this::apply;
			}

			public Throwable apply(Throwable t) {
				return new IOException("LEGAL", t);
			}

		});
		this.globalErrorMappingHandlerProviders.add(handler);
		var decisions          = decisionFluxOnePermitWithObligation();
		var constraintsService = buildConstraintHandlerService();
		var data               = Flux.range(0, 10).map(x -> {
									if (x == 5)
										throw new RuntimeException("ILLEGAL");
									return x;
								});
		var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
				Integer.class);

		StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4)
				.expectErrorMatches(error -> error instanceof IOException && "LEGAL".equals(error.getMessage()))
				.verify();

		verify(handler, times(1)).apply(any());
	}

	@Test
	void when_onNextObligationFails_thenAccessDenied() {
		var handler = spy(new ConsumerConstraintHandlerProvider<Integer>() {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Class<Integer> getSupportedType() {
				return Integer.class;
			}

			@Override
			public Consumer<Integer> getHandler(JsonNode constraint) {
				return this::accept;
			}

			public void accept(Integer i) {
				throw new RuntimeException("I FAILED TO OBLIGE");
			}

		});
		this.globalConsumerProviders.add(handler);
		var decisions          = decisionFluxOnePermitWithObligation();
		var constraintsService = buildConstraintHandlerService();
		var data               = Flux.range(0, 10);
		var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
				Integer.class);

		StepVerifier.create(sut).expectError(AccessDeniedException.class).verify();
		verify(handler, times(1)).accept(any());
	}

	@Test
	void when_onErrorObligationFails_thenAccessDenied() {
		var handler = spy(new ErrorHandlerProvider() {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Consumer<Throwable> getHandler(JsonNode constraint) {
				return this::accept;
			}

			public void accept(Throwable t) {
				throw new RuntimeException("I FAILED TO OBLIGE");
			}

		});
		this.globalErrorHandlerProviders.add(handler);
		var decisions          = decisionFluxOnePermitWithObligation();
		var constraintsService = buildConstraintHandlerService();
		var data               = Flux.range(0, 10).map(x -> {
									if (x == 5)
										throw new RuntimeException("ILLEGAL");
									return x;
								});
		var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
				Integer.class);

		StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4).expectError(AccessDeniedException.class).verify();
		verify(handler, times(2)).accept(any());
	}

	@Test
	void when_onSubscribeObligationFails_thenAccessDenied() {
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
				throw new IllegalStateException("I FAILED TO OBLIGE");
			}

		});
		this.globalSubscriptionHandlerProviders.add(handler);
		var decisions          = decisionFluxOnePermitWithObligation();
		var constraintsService = buildConstraintHandlerService();
		var data               = Flux.range(0, 10);
		var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
				Integer.class);

		StepVerifier.create(sut).expectError(AccessDeniedException.class).verify();
		verify(handler, times(1)).accept(any());
	}

	@Test
	void when_onRequestObligationFails_thenAccessDenied() {
		var handler = spy(new RequestHandlerProvider() {
			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public LongConsumer getHandler(JsonNode constraint) {
				return this::accept;
			}

			public void accept(Long l) {
				throw new RuntimeException("I FAILED TO OBLIGE");
			}
		});
		this.globalRequestHandlerProviders.add(handler);
		var decisions          = decisionFluxOnePermitWithObligation();
		var constraintsService = buildConstraintHandlerService();
		var data               = Flux.range(0, 10);
		var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
				Integer.class);

		StepVerifier.create(sut).expectError(RuntimeException.class).verify();
		verify(handler, times(1)).accept(any());
	}

	@Test
	void when_onCompleteObligationFails_thenAccessDenied() {
		var handler = spy(new RunnableConstraintHandlerProvider() {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Signal getSignal() {
				return Signal.ON_COMPLETE;
			}

			@Override
			public Runnable getHandler(JsonNode constraint) {
				return this::run;
			}

			public void run() {
				throw new IllegalStateException("I FAILED TO OBLIGE");
			}

		});
		this.globalRunnableProviders.add(handler);
		var decisions          = decisionFluxOnePermitWithObligation();
		var constraintsService = buildConstraintHandlerService();
		var data               = Flux.range(0, 10);
		var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
				Integer.class);

		StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).expectError(AccessDeniedException.class)
				.verify();
		verify(handler, times(1)).run();
	}

	@Test
	void when_onNextAdviceFails_thenAccessIsGranted() {
		var handler = spy(new ConsumerConstraintHandlerProvider<Integer>() {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Class<Integer> getSupportedType() {
				return Integer.class;
			}

			@Override
			public Consumer<Integer> getHandler(JsonNode constraint) {
				return this::accept;
			}

			public void accept(Integer i) {
				throw new RuntimeException("I FAILED TO OBLIGE");
			}

		});
		this.globalConsumerProviders.add(handler);
		var decisions          = decisionFluxOnePermitWithAdvice();
		var constraintsService = buildConstraintHandlerService();
		var data               = Flux.range(0, 10);
		var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
				Integer.class);

		StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
		verify(handler, times(10)).accept(any());
	}

	@Test
	void when_onErrorAdviceFails_thenOriginalErrorSignal() {
		var handler = spy(new ErrorHandlerProvider() {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Consumer<Throwable> getHandler(JsonNode constraint) {
				return this::accept;
			}

			public void accept(Throwable t) {
				throw new RuntimeException("I FAILED TO OBLIGE");
			}

		});
		this.globalErrorHandlerProviders.add(handler);
		var decisions          = decisionFluxOnePermitWithAdvice();
		var constraintsService = buildConstraintHandlerService();
		var data               = Flux.range(0, 10).map(x -> {
									if (x == 5)
										throw new RuntimeException("ILLEGAL");
									return x;
								});
		var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
				Integer.class);

		StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4).expectErrorMatches(err -> "ILLEGAL".equals(err.getMessage()))
				.verify();
		verify(handler, times(1)).accept(any());
	}

	@Test
	void when_onSubscribeAdviceFails_thenAccessGranted() {
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
				throw new IllegalStateException("I FAILED TO OBLIGE");
			}

		});
		this.globalSubscriptionHandlerProviders.add(handler);
		var decisions          = decisionFluxOnePermitWithAdvice();
		var constraintsService = buildConstraintHandlerService();
		var data               = Flux.range(0, 10);
		var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
				Integer.class);

		StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
		verify(handler, times(1)).accept(any());
	}

	@Test
	void when_onRequestAdviceFails_thenAccessGranted() {
		var handler = spy(new RequestHandlerProvider() {
			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public LongConsumer getHandler(JsonNode constraint) {
				return this::accept;
			}

			public void accept(Long l) {
				throw new RuntimeException("I FAILED TO OBLIGE");
			}
		});
		this.globalRequestHandlerProviders.add(handler);
		var decisions          = decisionFluxOnePermitWithAdvice();
		var constraintsService = buildConstraintHandlerService();
		var data               = Flux.range(0, 10);
		var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
				Integer.class);

		StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
		verify(handler, times(1)).accept(any());
	}

	@Test
	void when_onCancelAdviceFails_thenFluxIsJustComplete() {
		var handler = spy(new RunnableConstraintHandlerProvider() {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Signal getSignal() {
				return Signal.ON_CANCEL;
			}

			@Override
			public Runnable getHandler(JsonNode constraint) {
				return this::run;
			}

			public void run() {
				throw new IllegalStateException("I FAILED TO OBLIGE");
			}

		});
		this.globalRunnableProviders.add(handler);
		var decisions          = decisionFluxOnePermitWithAdvice();
		var constraintsService = buildConstraintHandlerService();
		var data               = Flux.range(0, 10);
		var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
				Integer.class);

		StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
	}

	@Test
	void when_onCompleteAdviceFails_thenAccessGranted() {
		var handler = spy(new RunnableConstraintHandlerProvider() {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Signal getSignal() {
				return Signal.ON_COMPLETE;
			}

			@Override
			public Runnable getHandler(JsonNode constraint) {
				return this::run;
			}

			public void run() {
				throw new IllegalStateException("I FAILED TO OBLIGE");
			}

		});
		this.globalRunnableProviders.add(handler);
		var decisions          = decisionFluxOnePermitWithAdvice();
		var constraintsService = buildConstraintHandlerService();
		var data               = Flux.range(0, 10);
		var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
				Integer.class);

		StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
		verify(handler, times(1)).run();
	}

	@Test
	void when_onNextObligationFailsByMissing_thenAccessDenied() {
		var decisions          = decisionFluxOnePermitWithObligation();
		var constraintsService = buildConstraintHandlerService();
		var data               = Flux.range(0, 10);
		var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
				Integer.class);

		StepVerifier.create(sut).expectError(AccessDeniedException.class).verify();
	}

	@Test
	void when_onSubscribeObligationFailsByMissing_thenAccessDenied() {
		var decisions          = decisionFluxOnePermitWithObligation();
		var constraintsService = buildConstraintHandlerService();
		var data               = Flux.range(0, 10);
		var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
				Integer.class);
		StepVerifier.create(sut).expectError(AccessDeniedException.class).verify();
	}

	@Test
	void when_onRequestObligationFailsByMissing_thenAccessDenied() {
		var decisions          = decisionFluxOnePermitWithObligation();
		var constraintsService = buildConstraintHandlerService();
		var data               = Flux.range(0, 10);
		var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
				Integer.class);

		StepVerifier.create(sut).expectError(AccessDeniedException.class).verify();
	}

	@Test
	void when_onCancelObligationFails_thenFluxIsJustComplete() {
		var handler = spy(new RunnableConstraintHandlerProvider() {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

			@Override
			public Signal getSignal() {
				return Signal.ON_CANCEL;
			}

			@Override
			public Runnable getHandler(JsonNode constraint) {
				return this::run;
			}

			public void run() {
				throw new IllegalStateException("I FAILED TO OBLIGE");
			}

		});
		this.globalRunnableProviders.add(handler);
		var decisions          = decisionFluxOnePermitWithObligation();
		var constraintsService = buildConstraintHandlerService();
		var data               = Flux.range(0, 10);
		var sut                = EnforceTillDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService,
				Integer.class);

		StepVerifier.create(sut.take(1)).expectNext(0).verifyComplete();
		verify(handler, times(1)).run();
	}

	public Flux<AuthorizationDecision> decisionFluxOnePermitWithObligation() {
		var json       = JsonNodeFactory.instance;
		var plus10000  = json.numberNode(10000L);
		var obligation = json.arrayNode();
		obligation.add(plus10000);
		return Flux.just(AuthorizationDecision.PERMIT.withObligations(obligation));
	}

	public Flux<AuthorizationDecision> decisionFluxOnePermitWithAdvice() {
		var json      = JsonNodeFactory.instance;
		var plus10000 = json.numberNode(10000L);
		var advice    = json.arrayNode();
		advice.add(plus10000);
		return Flux.just(AuthorizationDecision.PERMIT.withAdvice(advice));
	}

	public Flux<AuthorizationDecision> decisionFluxWithChangingAdvice() {
		var json            = JsonNodeFactory.instance;
		var advicePlus10000 = json.numberNode(10000L);
		var advicePlus50000 = json.numberNode(50000L);
		var firstAdvice     = json.arrayNode();
		firstAdvice.add(advicePlus10000);
		var secondAdvice = json.arrayNode();
		secondAdvice.add(advicePlus50000);

		return Flux.just(AuthorizationDecision.PERMIT.withAdvice(firstAdvice),
				AuthorizationDecision.PERMIT.withAdvice(secondAdvice));
	}

}
