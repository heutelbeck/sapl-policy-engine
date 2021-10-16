package io.sapl.spring.method.reactive;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.constraints.AbstractConstraintHandler;
import io.sapl.spring.constraints.ReactiveConstraintEnforcementService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

public class EnforceRecoverableIfDeniedPolicyEnforcementPointTests {

	@BeforeAll
	public static void beforeAll() {
		// this eliminates excessive logging of dropped errors in case of onErrorStop()
		// downstream.
		Hooks.onErrorDropped(err -> {
		});
	}

	@Test
	void when_subscribingTwice_Fails() {
		var constraintsService = new ReactiveConstraintEnforcementService(List.of());
		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var data = Flux.just(1, 2, 3);
		Flux<Object> sut = EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);
		sut.blockLast();
		assertThrows(IllegalStateException.class, () -> sut.blockLast());
	}

	@Test
	void when_onlyOnePermit_thenAllSignalsGetThrough() {
		var constraintsService = new ReactiveConstraintEnforcementService(List.of());
		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var data = Flux.just(1, 2, 3);
		Flux<Object> sut = EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);
		StepVerifier.create(sut).expectNext(1, 2, 3).verifyComplete();
	}

	@Test
	void when_endlessPermits_thenAllSignalsGetThrough() {
		StepVerifier.withVirtualTime(this::scenario_when_endlessPermits_thenAllSignalsGetThrough)
				.thenAwait(Duration.ofMillis(300L)).expectNext(1, 2, 3).verifyComplete();
	}

	private Flux<Object> scenario_when_endlessPermits_thenAllSignalsGetThrough() {
		var constraintsService = new ReactiveConstraintEnforcementService(List.of());
		var decisions = Flux.just(AuthorizationDecision.PERMIT).repeat().delayElements(Duration.ofMillis(5L));
		var data = Flux.just(1, 2, 3).delayElements(Duration.ofMillis(30L));
		return EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);
	}

	@Test
	void when_onlyOneDeny_thenNoSignalsAndCompletesWithSourceButDuringRecoveryErrorConsumerTriggeredOnce() {
		var constraintsService = new ReactiveConstraintEnforcementService(List.of());
		var decisions = Flux.just(AuthorizationDecision.DENY);
		var data = Flux.just(1, 2, 3);
		Flux<Object> sut = EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);
		var errorConsumer = errorConsumer();
		StepVerifier.create(sut.onErrorContinue(errorConsumer)).verifyComplete();
		verify(errorConsumer, times(1)).accept(any(), any());
	}

	@Test
	void when_firstPermitThenDeny_thenSignalsPassThroughTillDeniedThenDropEvenIfonErrorContinue() {
		var errorConsumer = errorConsumer();
		StepVerifier
				.withVirtualTime(
						() -> scenario_firstPermitThenDeny_thenSignalsPassThroughTillDeniedThenDrop(errorConsumer))
				.thenAwait(Duration.ofMillis(200L)).expectNext(1, 2).verifyComplete();
		verify(errorConsumer, times(1)).accept(any(), any());
	}

	@SuppressWarnings("unchecked")
	private BiConsumer<Throwable, Object> errorConsumer() {
		return (BiConsumer<Throwable, Object>) mock(BiConsumer.class);
	}

	private Flux<Object> scenario_firstPermitThenDeny_thenSignalsPassThroughTillDeniedThenDrop(
			BiConsumer<Throwable, Object> errorConsumer) {
		var constraintsService = new ReactiveConstraintEnforcementService(List.of());
		var decisions = Flux.just(AuthorizationDecision.PERMIT, AuthorizationDecision.DENY)
				.delayElements(Duration.ofMillis(50L));
		var data = Flux.just(1, 2, 3).delayElements(Duration.ofMillis(20L));
		return EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService)
				.onErrorContinue(errorConsumer);
	}

	@Test
	void when_firstPermitThenDeny_thenSignalsPassThroughTillDeniedThenDropUntilNewPermit() {
		var errorConsumer = errorConsumer();
		StepVerifier
				.withVirtualTime(
						() -> scenario_firstPermitThenDeny_thenSignalsPassThroughTillDeniedThenDropUntilNewPermit(
								errorConsumer))
				.thenAwait(Duration.ofMillis(2000L)).expectNext(0, 1, 4, 5, 6, 7, 8, 9).verifyComplete();
		verify(errorConsumer, times(1)).accept(any(), any());
	}

	private Flux<Object> scenario_firstPermitThenDeny_thenSignalsPassThroughTillDeniedThenDropUntilNewPermit(
			BiConsumer<Throwable, Object> errorConsumer) {
		var constraintsService = new ReactiveConstraintEnforcementService(List.of());
		var decisions = Flux
				.just(AuthorizationDecision.PERMIT, AuthorizationDecision.DENY, AuthorizationDecision.PERMIT)
				.delayElements(Duration.ofMillis(50L));
		var data = Flux.range(0, 10).delayElements(Duration.ofMillis(20L));
		return EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService)
				.onErrorContinue(errorConsumer);
	}

	@Test
	void when_constraintsPresent_thenTheseAreHandledAndUpdated() {
		StepVerifier.withVirtualTime(this::scenario_when_constraintsPresent_thenTheseAreHandledAndUpdated)
				.thenAwait(Duration.ofMillis(1000L))
				.expectNext(10000, 10001, 10002, 10003, 10004, 50005, 50006, 50007, 50008, 50009).verifyComplete();
	}

	Flux<Object> scenario_when_constraintsPresent_thenTheseAreHandledAndUpdated() {
		var handler = new AbstractConstraintHandler(1) {

			@Override
			@SuppressWarnings("unchecked")
			public Function<Integer, Integer> onNextMap(JsonNode constraint) {
				return number -> number + constraint.asInt();
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		};

		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));
		var decisions = decisionFluxWithChangeingAdvice().delayElements(Duration.ofMillis(270L));
		var data = Flux.range(0, 10).delayElements(Duration.ofMillis(50L));
		return EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);

	}

	@Test
	void when_handlerMapsToNull_thenElementsAreDropped() {
		var handler = new AbstractConstraintHandler(1) {

			@Override
			@SuppressWarnings("unchecked")
			public Function<Integer, Integer> onNextMap(JsonNode constraint) {
				return number -> (number % 2 == 0) ? number : null;
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		};

		var json = JsonNodeFactory.instance;
		var advicePlus10000 = json.numberNode(10000L);
		var firstAdvice = json.arrayNode();
		firstAdvice.add(advicePlus10000);

		var decisions = Flux.just(AuthorizationDecision.PERMIT.withAdvice(firstAdvice));
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));

		var data = Flux.range(0, 10);
		Flux<Object> sut = EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);
		StepVerifier.create(sut).expectNext(0, 2, 4, 6, 8).verifyComplete();

	}

	@Test
	void when_handlerCancel_thenHandlerIsCalled() {
		var handler = spy(new AbstractConstraintHandler(1) {

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		});
		var json = JsonNodeFactory.instance;
		var advicePlus10000 = json.numberNode(10000L);
		var firstAdvice = json.arrayNode();
		firstAdvice.add(advicePlus10000);
		var decisions = Flux.just(AuthorizationDecision.PERMIT.withAdvice(firstAdvice));
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));

		var data = Flux.range(0, 10);
		Flux<Object> sut = EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);
		StepVerifier.create(sut.take(5)).expectNext(0, 1, 2, 3, 4).verifyComplete();
		verify(handler, times(1)).onRequest(any());
		verify(handler, times(1)).onCancel(any());
		verify(handler, times(5)).onNext(any());
	}

	@Test
	void when_error_thenErrorMappedAndPropagated() {
		var handler = spy(new AbstractConstraintHandler(1) {
			public Consumer<? super Throwable> onError(JsonNode constraint) {
				return null;
			}

			@Override
			public Function<Throwable, Throwable> onErrorMap(JsonNode constraint) {
				return t -> new IOException("LEGAL", t);
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		});
		var decisions = decisionFluxOnePermitWithObligation();
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));
		var data = Flux.range(0, 10).map(x -> {
			if (x == 5)
				throw new RuntimeException("ILLEGAL");
			return x;
		});
		Flux<Object> sut = EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);

		StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4)
				.expectErrorMatches(error -> error instanceof IOException && "LEGAL".equals(error.getMessage()))
				.verify();

		verify(handler, times(1)).onErrorMap(any());
	}

	@Test
	void when_onNextObligationFails_thenAccessDeniedAndMatchingElementIsDroppedErrorConsumerTriggeredDuringRecovery() {
		var handler = spy(new AbstractConstraintHandler(1) {

			@Override
			@SuppressWarnings("unchecked")
			public Consumer<Integer> onNext(JsonNode constraint) {
				return t -> {
					if (t == 5)
						throw new RuntimeException("I FAILED TO OBLIGE");
				};
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		});
		var decisions = decisionFluxOnePermitWithObligation();
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));
		var data = Flux.range(0, 10);
		Flux<Object> sut = EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);
		var errorConsumer = errorConsumer();
		StepVerifier.create(sut.onErrorContinue(errorConsumer)).expectNext(0, 1, 2, 3, 4, 6, 7, 8, 9).verifyComplete();
		verify(handler, times(10)).onNext(any());
		verify(errorConsumer, times(1)).accept(any(), any());
	}

	
	@Test
	void when_onSubscribeObligationFails_thenAccessDeniedMessageMapped() {
		var handler = spy(new AbstractConstraintHandler(1) {
			@Override
			public Consumer<Subscription> onSubscribe(JsonNode constraint) {
				return s -> { throw new RuntimeException(); };
			}

			@Override
			public Function<Throwable,Throwable> onErrorMap(JsonNode constraint) {
				return t -> new AccessDeniedException("CHANGED");
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		});
		var decisions = decisionFluxOnePermitWithObligation();
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));
		var data = Flux.range(0, 10);
		Flux<Object> sut = EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);
		StepVerifier.create(sut).expectErrorMatches(t->t.getMessage().equals("CHANGED")).verify();
		verify(handler, times(1)).onSubscribe(any());
	}

	@Test
	void when_onErrorObligationFails_thenAccessDeniedAndCompleteAsWeCannotRecoverFromDownstreamErrorsAnyhow() {
		var handler = spy(new AbstractConstraintHandler(1) {

			@Override
			public Consumer<? super Throwable> onError(JsonNode constraint) {
				return t -> {
					throw new RuntimeException("I FAILED TO OBLIGE");
				};
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		});
		var decisions = decisionFluxOnePermitWithObligation();
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));
		var data = Flux.range(0, 10).map(x -> {
			if (x == 5)
				throw new RuntimeException("ILLEGAL");
			return x;
		});
		Flux<Object> sut = EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);

		StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4).expectError(AccessDeniedException.class).verify();
		verify(handler, times(1)).onError(any());
	}

	@Test
	void when_upstreamError_thenTerminateWithError() {

		var decisions = Flux.just(AuthorizationDecision.PERMIT);
		var constraintsService = new ReactiveConstraintEnforcementService(List.of());
		var data = Flux.range(0, 10).map(x -> {
			if (x == 5)
				throw new RuntimeException("ILLEGAL");
			return x;
		});
		Flux<Object> sut = EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);

		StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4).expectError(RuntimeException.class).verify();
	}

	@Test
	void when_onSubscribeObligationFails_thenAllSignalsAreDropped() {
		var handler = spy(new AbstractConstraintHandler(1) {

			@Override
			public Consumer<? super Subscription> onSubscribe(JsonNode constraint) {
				return t -> {
					throw new RuntimeException("I FAILED TO OBLIGE");
				};
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		});
		var decisions = decisionFluxOnePermitWithObligation();
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));
		var data = Flux.range(0, 10);
		Flux<Object> sut = EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);

		StepVerifier.create(sut).expectError(AccessDeniedException.class).verify();
		verify(handler, times(1)).onSubscribe(any());
	}

	@Test
	void when_onSubscribeObligationFailsForFirstDecisionButSucceedsForSecond_thenAllSignalsSent() {
		var handler = spy(new AbstractConstraintHandler(1) {

			@Override
			public Consumer<? super Subscription> onSubscribe(JsonNode constraint) {
				return t -> {
					// Fail the first one
					if (constraint.asLong() == 10000L)
						throw new RuntimeException("I FAILED TO OBLIGE");
				};
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		});
		var decisions = decisionFluxWithChangeingAdvice();
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));
		var data = Flux.range(0, 10);
		Flux<Object> sut = EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);

		StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
		verify(handler, times(1)).onSubscribe(any());
	}

	@Test
	void when_onRequestObligationFails_thenImplicitlyAccessDeniedAndMessagesDroppedAsNoNewPermitComesInStreamCompletesEmptyAfterOnErrorContinue() {
		var handler = spy(new AbstractConstraintHandler(1) {

			@Override
			public Consumer<Long> onRequest(JsonNode constraint) {
				return t -> {
					throw new RuntimeException("I FAILED TO OBLIGE");
				};
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		});
		var errorConsumer = errorConsumer();
		var decisions = decisionFluxOnePermitWithObligation();
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));
		var data = Flux.range(0, 10);
		Flux<Object> sut = EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);

		StepVerifier.create(sut.onErrorContinue(errorConsumer)).verifyComplete();
		verify(handler, times(1)).onSubscribe(any());
		verify(errorConsumer, times(1)).accept(any(), any());
	}

	@Test
	void when_onCancelObligationFails_thenFluxIsJustComplete() {
		var handler = spy(new AbstractConstraintHandler(1) {

			@Override
			public Runnable onCancel(JsonNode constraint) {
				return () -> {
					throw new RuntimeException("I FAILED TO OBLIGE");
				};
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		});
		var decisions = decisionFluxOnePermitWithObligation();
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));
		var data = Flux.range(0, 10);
		Flux<Object> sut = EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);

		StepVerifier.create(sut.take(1)).expectNext(0).verifyComplete();
		verify(handler, times(1)).onCancel(any());
	}

	@Test
	void when_onCompleteObligationFails_thenImplicitlyAccessDeniedButNothingHappensAsDenyHereOnlyDropsMessages() {
		var handler = spy(new AbstractConstraintHandler(1) {

			@Override
			public Runnable onComplete(JsonNode constraint) {
				return () -> {
					throw new RuntimeException("I FAILED TO OBLIGE");
				};
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		});
		var decisions = decisionFluxOnePermitWithObligation();
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));
		var data = Flux.range(0, 10);
		Flux<Object> sut = EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);

		StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
		verify(handler, times(1)).onComplete(any());
	}

	@Test
	void when_onNextAdviceFails_thenAccessIsGranted() {
		var handler = spy(new AbstractConstraintHandler(1) {

			@Override
			public <T> Consumer<T> onNext(JsonNode constraint) {
				return t -> {
					throw new RuntimeException("I FAILED TO OBLIGE");
				};
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		});
		var decisions = decisionFluxOnePermitWithAdvice();
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));
		var data = Flux.range(0, 10);
		Flux<Object> sut = EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);

		StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
		verify(handler, times(10)).onNext(any());
	}

	@Test
	void when_onErrorAdviceFails_thenOriginalErrorSignal() {
		var handler = spy(new AbstractConstraintHandler(1) {

			@Override
			public Consumer<? super Throwable> onError(JsonNode constraint) {
				return t -> {
					throw new RuntimeException("I FAILED TO OBLIGE");
				};
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		});
		var decisions = decisionFluxOnePermitWithAdvice();
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));
		var data = Flux.range(0, 10).map(x -> {
			if (x == 5)
				throw new RuntimeException("ILLEGAL");
			return x;
		});
		Flux<Object> sut = EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);

		StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4).expectErrorMatches(err -> err.getMessage().equals("ILLEGAL"))
				.verify();
		verify(handler, times(1)).onError(any());
	}

	@Test
	void when_onSubscribeAdviceFails_thenAccessGranted() {
		var handler = spy(new AbstractConstraintHandler(1) {

			@Override
			public Consumer<? super Subscription> onSubscribe(JsonNode constraint) {
				return t -> {
					throw new RuntimeException("I FAILED TO OBLIGE");
				};
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		});
		var decisions = decisionFluxOnePermitWithAdvice();
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));
		var data = Flux.range(0, 10);
		Flux<Object> sut = EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);

		StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
		verify(handler, times(1)).onSubscribe(any());
	}

	@Test
	void when_onRequestAdviceFails_thenAccessGranted() {
		var handler = spy(new AbstractConstraintHandler(1) {

			@Override
			public Consumer<Long> onRequest(JsonNode constraint) {
				return t -> {
					throw new RuntimeException("I FAILED TO OBLIGE");
				};
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		});
		var decisions = decisionFluxOnePermitWithAdvice();
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));
		var data = Flux.range(0, 10);
		Flux<Object> sut = EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);

		StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
		verify(handler, times(1)).onSubscribe(any());
	}

	@Test
	void when_onCancelAdviceFails_thenFluxIsJustComplete() {
		var handler = spy(new AbstractConstraintHandler(1) {

			@Override
			public Runnable onCancel(JsonNode constraint) {
				return () -> {
					throw new RuntimeException("I FAILED TO OBLIGE");
				};
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		});
		var decisions = decisionFluxOnePermitWithAdvice();
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));
		var data = Flux.range(0, 10);
		Flux<Object> sut = EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);

		StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
	}

	@Test
	void when_onCompleteAdviceFails_thenAccessGranted() {
		var handler = spy(new AbstractConstraintHandler(1) {

			@Override
			public Runnable onComplete(JsonNode constraint) {
				return () -> {
					throw new RuntimeException("I FAILED TO OBLIGE");
				};
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		});
		var decisions = decisionFluxOnePermitWithAdvice();
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));
		var data = Flux.range(0, 10);
		Flux<Object> sut = EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);

		StepVerifier.create(sut).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).verifyComplete();
		verify(handler, times(1)).onComplete(any());
	}

	@Test
	void when_onNextObligationFailsByMissing_thenAccessDeniedImplicitlyAndMessagesAreDroppedRecoverableBynewPermit() {
		var decisions = Flux.concat(decisionFluxOnePermitWithObligation(), Flux.just(AuthorizationDecision.PERMIT));
		var constraintsService = new ReactiveConstraintEnforcementService(List.of());
		var data = Flux.range(0, 10);
		Flux<Object> sut = EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);
		var errorConsumer = errorConsumer();
		StepVerifier.create(sut.onErrorContinue(errorConsumer)).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
				.verifyComplete();
		verify(errorConsumer, times(1)).accept(any(), any());
	}

	@Test
	void when_onSubscribeObligationFailsByMissing_thenSignalsDroppedErrorConsumerTriggeredDuringRecovery() {
		var decisions = Flux.concat(decisionFluxOnePermitWithObligation(), Flux.just(AuthorizationDecision.PERMIT));
		var constraintsService = new ReactiveConstraintEnforcementService(List.of());
		var data = Flux.range(0, 10);
		Flux<Object> sut = EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);
		var errorConsumer = errorConsumer();
		StepVerifier.create(sut.onErrorContinue(errorConsumer)).expectNext(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
				.verifyComplete();
		verify(errorConsumer, times(1)).accept(any(), any());
	}

	@Test
	void when_onCancelObligationFailsByMissing_thenFluxIsJustComplete() {
		var handler = spy(new AbstractConstraintHandler(1) {

			@Override
			public Runnable onCancel(JsonNode constraint) {
				return () -> {
					throw new RuntimeException("I FAILED TO OBLIGE");
				};
			}

			@Override
			public boolean isResponsible(JsonNode constraint) {
				return true;
			}

		});
		var decisions = decisionFluxOnePermitWithObligation();
		var constraintsService = new ReactiveConstraintEnforcementService(List.of(handler));
		var data = Flux.range(0, 10);
		Flux<Object> sut = EnforceRecoverableIfDeniedPolicyEnforcementPoint.of(decisions, data, constraintsService);

		StepVerifier.create(sut.take(1)).expectNext(0).verifyComplete();
		verify(handler, times(1)).onCancel(any());
	}

	public Flux<AuthorizationDecision> decisionFluxOnePermitWithObligation() {
		var json = JsonNodeFactory.instance;
		var plus10000 = json.numberNode(10000L);
		var obligation = json.arrayNode();
		obligation.add(plus10000);
		return Flux.just(AuthorizationDecision.PERMIT.withObligations(obligation));
	}

	public Flux<AuthorizationDecision> decisionFluxOnePermitWithAdvice() {
		var json = JsonNodeFactory.instance;
		var plus10000 = json.numberNode(10000L);
		var advice = json.arrayNode();
		advice.add(plus10000);
		return Flux.just(AuthorizationDecision.PERMIT.withAdvice(advice));
	}

	public Flux<AuthorizationDecision> decisionFluxWithChangeingAdvice() {
		var json = JsonNodeFactory.instance;
		var advicePlus10000 = json.numberNode(10000L);
		var advicePlus50000 = json.numberNode(50000L);
		var firstAdvice = json.arrayNode();
		firstAdvice.add(advicePlus10000);
		var secondAdvice = json.arrayNode();
		secondAdvice.add(advicePlus50000);

		return Flux.just(AuthorizationDecision.PERMIT.withAdvice(firstAdvice),
				AuthorizationDecision.PERMIT.withAdvice(secondAdvice));
	}
}
