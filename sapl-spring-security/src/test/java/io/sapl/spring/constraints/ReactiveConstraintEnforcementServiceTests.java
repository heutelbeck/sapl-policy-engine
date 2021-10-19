package io.sapl.spring.constraints;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

public class ReactiveConstraintEnforcementServiceTests {
	private final static ObjectMapper MAPPER = new ObjectMapper();
	private final static JsonNodeFactory JSON = JsonNodeFactory.instance;
	private final static JsonNode CONSTRAINT;

	static {
		try {
			CONSTRAINT = MAPPER.readValue("\"primitive constraint\"", JsonNode.class);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	@BeforeAll
	public static void init() {
		Hooks.onOperatorDebug();
	}

	@Test
	void when_decisionContainsObligationAndThereIsNoHandler_thenAccessIsDenied() {
		var obligations = arrayOf(CONSTRAINT);
		var authzDecision = AuthorizationDecision.PERMIT.withObligations(obligations);
		Flux<Object> resourceAccessPoint = Flux.just(1, 2, 3);

		var constraintEnforcementService = new ReactiveConstraintEnforcementService(Collections.emptyList());
		var protectedResource = constraintEnforcementService.enforceConstraintsOnResourceAccessPoint(authzDecision,
				resourceAccessPoint);

		StepVerifier.create(protectedResource).expectErrorMatches(
				error -> error instanceof AccessDeniedException && containsAll(error.getMessage(), CONSTRAINT.asText()))
				.verify();
	}

	@Test
	void when_decisionContainsObligationAndThereIsNoResponsibleHandler_thenAccessIsDenied() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(false);

		var obligations = arrayOf(CONSTRAINT);
		var authzDecision = AuthorizationDecision.PERMIT.withObligations(obligations);
		Flux<Object> resourceAccessPoint = Flux.just(1, 2, 3);

		var constraintEnforcementService = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		var protectedResource = constraintEnforcementService.enforceConstraintsOnResourceAccessPoint(authzDecision,
				resourceAccessPoint);

		StepVerifier.create(protectedResource).expectErrorMatches(
				error -> error instanceof AccessDeniedException && containsAll(error.getMessage(), CONSTRAINT.asText()))
				.verify();

		verify(mockHandler, times(1)).isResponsible(any());
	}

	@Test
	void when_decisionContainsAdviceAndThereIsNoResponsibleHandler_thenAccessIsGranted() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(false);

		var advice = arrayOf(CONSTRAINT);
		var authzDecision = AuthorizationDecision.PERMIT.withAdvice(advice);
		Flux<Object> resourceAccessPoint = Flux.just(1, 2, 3);

		var constraintEnforcementService = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		var protectedResource = constraintEnforcementService.enforceConstraintsOnResourceAccessPoint(authzDecision,
				resourceAccessPoint);

		StepVerifier.create(protectedResource).expectNext(1, 2, 3).verifyComplete();

		verify(mockHandler, times(1)).isResponsible(any());
		verify(mockHandler, times(0)).applyAdvice(any(), any());
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_decisionContainsObligationAndThereIsAnHandler_thenHandlerIsInvokedAndAccessIsGranted() {
		var mockOnSubscribtionConsumer = mock(Consumer.class);
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.applyObligation(any(), any())).thenCallRealMethod();
		when(mockHandler.applyAdvice(any(), any())).thenCallRealMethod();
		when(mockHandler.onSubscribe(any())).thenReturn(mockOnSubscribtionConsumer);

		var obligations = arrayOf(CONSTRAINT);
		var authzDecision = AuthorizationDecision.PERMIT.withObligations(obligations);
		Flux<Object> resourceAccessPoint = Flux.just(1, 2, 3);

		var constraintEnforcementService = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		var protectedResource = constraintEnforcementService.enforceConstraintsOnResourceAccessPoint(authzDecision,
				resourceAccessPoint);

		StepVerifier.create(protectedResource).expectNext(1, 2, 3).verifyComplete();

		verify(mockHandler, times(1)).isResponsible(any());
		verify(mockHandler, times(1)).applyObligation(any(), any());
		verify(mockOnSubscribtionConsumer, times(1)).accept(any());
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_decisionContainsAdviceAndThereIsAnHandler_thenHandlerIsInvokedAndAccessIsGranted() {
		var mockOnSubscribtionConsumer = mock(Consumer.class);
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.applyObligation(any(), any())).thenCallRealMethod();
		when(mockHandler.applyAdvice(any(), any())).thenCallRealMethod();
		when(mockHandler.onSubscribe(any())).thenReturn(mockOnSubscribtionConsumer);

		var obligations = arrayOf(CONSTRAINT);
		var authzDecision = AuthorizationDecision.PERMIT.withAdvice(obligations);
		Flux<Object> resourceAccessPoint = Flux.just(1, 2, 3);

		var constraintEnforcementService = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		var protectedResource = constraintEnforcementService.enforceConstraintsOnResourceAccessPoint(authzDecision,
				resourceAccessPoint);

		StepVerifier.create(protectedResource).expectNext(1, 2, 3).verifyComplete();

		verify(mockHandler, times(1)).isResponsible(any());
		verify(mockHandler, times(1)).applyAdvice(any(), any());
		verify(mockOnSubscribtionConsumer, times(1)).accept(any());
	}

	@Test
	void when_handleForBlockingMethodInvocationOrAccessDenied_noObligation_and_noAdvice_then_returnTrue() {
		var sut = new ReactiveConstraintEnforcementService(List.of());
		var actual = sut.handleForBlockingMethodInvocationOrAccessDenied(AuthorizationDecision.PERMIT);
		assertThat(actual, is(true));
	}

	@Test
	void when_handleForBlockingMethodInvocationOrAccessDenied_withObligation_noHandler_then_returnFalse() {
		var sut = new ReactiveConstraintEnforcementService(List.of());
		var actual = sut.handleForBlockingMethodInvocationOrAccessDenied(
				AuthorizationDecision.PERMIT.withObligations(someConstraint()));
		assertThat(actual, is(false));
	}

	@Test
	void when_handleForBlockingMethodInvocationOrAccessDenied_withObligation_handlerFails_then_returnFalse() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.preBlockingMethodInvocationOrOnAccessDenied(any())).thenReturn(false);
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		var actual = sut.handleForBlockingMethodInvocationOrAccessDenied(
				AuthorizationDecision.PERMIT.withObligations(someConstraint()));
		assertThat(actual, is(false));
	}

	@Test
	void when_handleForBlockingMethodInvocationOrAccessDenied_withObligation_handlerSuccess_then_returnTrue() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.preBlockingMethodInvocationOrOnAccessDenied(any())).thenReturn(true);
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		var actual = sut.handleForBlockingMethodInvocationOrAccessDenied(
				AuthorizationDecision.PERMIT.withObligations(someConstraint()));
		assertThat(actual, is(true));
	}

	@Test
	void when_handleForBlockingMethodInvocationOrAccessDenied_withAdvice_noHandler_then_returnTrue() {
		var sut = new ReactiveConstraintEnforcementService(List.of());
		var actual = sut.handleForBlockingMethodInvocationOrAccessDenied(
				AuthorizationDecision.PERMIT.withAdvice(someConstraint()));
		assertThat(actual, is(true));
	}

	@Test
	void when_handleForBlockingMethodInvocationOrAccessDenied_withAdvice_handlerFails_then_returnTrue() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.preBlockingMethodInvocationOrOnAccessDenied(any())).thenReturn(false);
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		var actual = sut.handleForBlockingMethodInvocationOrAccessDenied(
				AuthorizationDecision.PERMIT.withAdvice(someConstraint()));
		assertThat(actual, is(true));
	}

	@Test
	void when_handleForBlockingMethodInvocationOrAccessDenied_withAdvice_handlerSuccess_then_returnTrue() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.preBlockingMethodInvocationOrOnAccessDenied(any())).thenReturn(true);
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		var actual = sut.handleForBlockingMethodInvocationOrAccessDenied(
				AuthorizationDecision.PERMIT.withAdvice(someConstraint()));
		assertThat(actual, is(true));
	}

	@Test
	void when_handleAfterBlockingMethodInvocation_noObligation_and_noAdvice_then_originalValue() {
		var sut = new ReactiveConstraintEnforcementService(List.of());
		var actual = sut.handleAfterBlockingMethodInvocation(AuthorizationDecision.PERMIT, 1, Integer.class);
		assertThat(actual, is(1));
	}

	@Test
	void when_handleAfterBlockingMethodInvocation_withObligation_noHandler_then_AccessDeniedException() {
		var sut = new ReactiveConstraintEnforcementService(List.of());
		assertThrows(AccessDeniedException.class, () -> sut.handleAfterBlockingMethodInvocation(
				AuthorizationDecision.PERMIT.withObligations(someConstraint()), 1, Integer.class));
	}

	@Test
	void when_handleAfterBlockingMethodInvocation_withObligation_handlerFails_then_AccessDeniedException() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.postBlockingMethodInvocation(any())).thenReturn(__ -> {
			throw new IllegalStateException("I FAILED TO OBLIGE");
		});
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertThrows(AccessDeniedException.class, () -> sut.handleAfterBlockingMethodInvocation(
				AuthorizationDecision.PERMIT.withObligations(someConstraint()), 1, Integer.class));
	}

	@Test
	void when_handleAfterBlockingMethodInvocation_withObligation_handlerIsNull_then_AccessDeniedException() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertThrows(AccessDeniedException.class, () -> sut.handleAfterBlockingMethodInvocation(
				AuthorizationDecision.PERMIT.withObligations(someConstraint()), 1, Integer.class));
	}

	@Test
	void when_handleAfterBlockingMethodInvocation_withObligation_handlerSuccess_then_returnNewValue() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		var expected = 42;
		when(mockHandler.postBlockingMethodInvocation(any())).thenReturn(__ -> expected);
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		var actual = sut.handleAfterBlockingMethodInvocation(
				AuthorizationDecision.PERMIT.withObligations(someConstraint()), 1, Integer.class);
		assertThat(actual, is(expected));
	}

	@Test
	void when_handleAfterBlockingMethodInvocation_withObligation_and_typeMissing_then_returnAccessDeniedException() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertThrows(AccessDeniedException.class, () -> sut.handleAfterBlockingMethodInvocation(
				AuthorizationDecision.PERMIT.withObligations(someConstraint()), 1, null));
	}

	@Test
	void when_handleAfterBlockingMethodInvocation_withAdvice_noHandler_then_returnOriginalValue() {
		var sut = new ReactiveConstraintEnforcementService(List.of());
		var actual = sut.handleAfterBlockingMethodInvocation(AuthorizationDecision.PERMIT.withAdvice(someConstraint()),
				1, Integer.class);
		assertThat(actual, is(1));
	}

	@Test
	void when_handleAfterBlockingMethodInvocation_withAdvice_handlerFails_then_returnOriginalValue() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.postBlockingMethodInvocation(any())).thenReturn(__ -> {
			throw new IllegalStateException("I FAILED TO OBLIGE");
		});
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		var actual = sut.handleAfterBlockingMethodInvocation(AuthorizationDecision.PERMIT.withAdvice(someConstraint()),
				1, Integer.class);
		assertThat(actual, is(1));
	}

	@Test
	void when_handleAfterBlockingMethodInvocation_withAdvice_handlerIsNull_then_returnOriginalValue() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		var actual = sut.handleAfterBlockingMethodInvocation(AuthorizationDecision.PERMIT.withAdvice(someConstraint()),
				1, Integer.class);
		assertThat(actual, is(1));
	}

	@Test
	void when_handleAfterBlockingMethodInvocation_withAdvice_handlerSuccess_then_returnNewValue() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		var expected = 42;
		when(mockHandler.postBlockingMethodInvocation(any())).thenReturn(__ -> expected);
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		var actual = sut.handleAfterBlockingMethodInvocation(AuthorizationDecision.PERMIT.withAdvice(someConstraint()),
				1, Integer.class);
		assertThat(actual, is(expected));
	}

	@Test
	void when_handleRunnableConstraints_withObligation_noHandler_then_AccessDenied() {
		var sut = new ReactiveConstraintEnforcementService(List.of());
		assertThrows(AccessDeniedException.class,
				() -> sut.handleOnCompleteConstraints(AuthorizationDecision.PERMIT.withObligations(someConstraint())));
	}

	@Test
	void when_handleRunnableConstraints_withObligation_handlerFailing_then_AccessDenied() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onComplete(any())).thenThrow(new IllegalStateException("I FAILED TO OBLIGE"));
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertThrows(AccessDeniedException.class,
				() -> sut.handleOnCompleteConstraints(AuthorizationDecision.PERMIT.withObligations(someConstraint())));
	}

	@Test
	void when_handleRunnableConstraints_withObligation_noMatchingHandler_then_AccessDenied() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(false);
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertThrows(AccessDeniedException.class,
				() -> sut.handleOnCompleteConstraints(AuthorizationDecision.PERMIT.withObligations(someConstraint())));
	}

	@Test
	@Disabled
	void when_handleRunnableConstraints_withObligation_matchingHandlerReturnsNull_then_AccessDenied() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onComplete(any())).thenReturn(null);
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertThrows(AccessDeniedException.class,
				() -> sut.handleOnCompleteConstraints(AuthorizationDecision.PERMIT.withObligations(someConstraint())));
	}

	@Test
	void when_handleRunnableConstraints_withObligation_matchingHandlerSuccess_then_Success() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onComplete(any())).thenReturn(() -> {
		});
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertDoesNotThrow(
				() -> sut.handleOnCompleteConstraints(AuthorizationDecision.PERMIT.withObligations(someConstraint())));
	}

	@Test
	void when_handleOnCancelConstraints_withObligation_matchingHandlerSuccess_then_Success() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onCancel(any())).thenReturn(() -> {
		});
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertDoesNotThrow(
				() -> sut.handleOnCancelConstraints(AuthorizationDecision.PERMIT.withObligations(someConstraint())));
	}

	@Test
	void when_handleRunnableConstraints_withObligation_matchingHandlerFails_then_AccessDenied() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onComplete(any())).thenThrow(new IllegalStateException("I FAILED TO OBLIGE"));
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertThrows(AccessDeniedException.class,
				() -> sut.handleOnCompleteConstraints(AuthorizationDecision.PERMIT.withObligations(someConstraint())));
	}

	@Test
	void when_handleRunnableConstraints_withNoConstraints_noHandler_then_Success() {
		var sut = new ReactiveConstraintEnforcementService(List.of());
		assertDoesNotThrow(() -> sut.handleOnCompleteConstraints(AuthorizationDecision.PERMIT));
	}

	@Test
	void when_handleRunnableConstraintsOnCancel_withNoConstraints_noHandler_then_Success() {
		var sut = new ReactiveConstraintEnforcementService(List.of());
		assertDoesNotThrow(() -> sut.handleOnCancelConstraints(AuthorizationDecision.PERMIT));
	}

	@Test
	void when_handleOnTerminateConstraints_withNoConstraints_noHandler_then_Success() {
		var sut = new ReactiveConstraintEnforcementService(List.of());
		assertDoesNotThrow(() -> sut.handleOnTerminateConstraints(AuthorizationDecision.PERMIT));
	}

	@Test
	void when_handleOnTerminateConstraints_withObligation_matchingHandlerSuccess_then_Success() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onTerminate(any())).thenReturn(() -> {
		});
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertDoesNotThrow(
				() -> sut.handleOnTerminateConstraints(AuthorizationDecision.PERMIT.withObligations(someConstraint())));
	}

	@Test
	void when_handleAfterTerminateConstraints_withNoConstraints_noHandler_then_Success() {
		var sut = new ReactiveConstraintEnforcementService(List.of());
		assertDoesNotThrow(() -> sut.handleAfterTerminateConstraints(AuthorizationDecision.PERMIT));
	}

	@Test
	void when_handleAfterTerminateConstraints_withObligation_matchingHandlerSuccess_then_Success() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.afterTerminate(any())).thenReturn(() -> {
		});
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertDoesNotThrow(() -> sut
				.handleAfterTerminateConstraints(AuthorizationDecision.PERMIT.withObligations(someConstraint())));
	}

	@Test
	void when_handleTransformingConstraints_withObligation_noHandler_then_AccessDenied() {
		var sut = new ReactiveConstraintEnforcementService(List.of());
		assertThrows(AccessDeniedException.class,
				() -> sut.handleOnNextConstraints(AuthorizationDecision.PERMIT.withObligations(someConstraint()), 1));
	}

	@Test
	void when_handleTransformingConstraints_withObligation_handlerFailing_then_AccessDenied() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onNextMap(any())).thenThrow(new IllegalStateException("I FAILED TO OBLIGE"));
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertThrows(AccessDeniedException.class,
				() -> sut.handleOnNextConstraints(AuthorizationDecision.PERMIT.withObligations(someConstraint()), 1));
	}

	@Test
	void when_handleTransformingConstraints_withObligation_noMatchingHandler_then_AccessDenied() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(false);
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertThrows(AccessDeniedException.class,
				() -> sut.handleOnNextConstraints(AuthorizationDecision.PERMIT.withObligations(someConstraint()), 1));
	}

	@Test
	@Disabled
	void when_handleTransformingConstraints_withObligation_matchingHandlerReturnsNull_then_AccessDenied() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onNextMap(any())).thenReturn(null);
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertThrows(AccessDeniedException.class,
				() -> sut.handleOnNextConstraints(AuthorizationDecision.PERMIT.withObligations(someConstraint()), 1));
	}

	@Test
	void when_handleTransformingConstraints_withObligation_matchingHandlerSuccess_then_Success() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onNextMap(any())).thenReturn(__ -> 2);
		when(mockHandler.onNext(any())).thenReturn(__ -> {
		});
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		var actual = sut.handleOnNextConstraints(AuthorizationDecision.PERMIT.withObligations(someConstraint()), 1);
		assertThat(actual, is(2));
	}

	@Test
	void when_handleTransformingConstraints_withObligation_matchingHandlerFails_then_AccessDenied() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onNextMap(any())).thenReturn(__ -> {
			throw new IllegalStateException("I FAILED TO OBLIGE");
		});
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertThrows(AccessDeniedException.class,
				() -> sut.handleOnNextConstraints(AuthorizationDecision.PERMIT.withObligations(someConstraint()), 1));
	}

	@Test
	void when_handleTransformingConstraints_withNoConstraints_noHandler_then_Success() {
		var sut = new ReactiveConstraintEnforcementService(List.of());
		var actual = sut.handleOnNextConstraints(AuthorizationDecision.PERMIT, 1);
		assertThat(actual, is(1));
	}

	@Test
	void when_handleOnErrorConstraints_withNoConstraints_noHandler_then_Success() {
		var sut = new ReactiveConstraintEnforcementService(List.of());
		var expected = new IOException();
		var actual = sut.handleOnErrorConstraints(AuthorizationDecision.PERMIT, expected);
		assertThat(actual, is(expected));
	}

	@Test
	void when_handleOnErrorConstraints_withObligation_matchingHandlerSuccess_then_Success() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		var expected = new IllegalArgumentException("TRANSFORMED");
		when(mockHandler.onErrorMap(any())).thenReturn(__ -> expected);
		when(mockHandler.onError(any())).thenReturn(__ -> {
		});
		var original = new IOException();
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		var actual = sut.handleOnErrorConstraints(AuthorizationDecision.PERMIT.withObligations(someConstraint()),
				original);
		assertThat(actual, is(expected));
	}

	@Test
	void when_handleConsumerConstraints_withObligation_handlerFailing_then_AccessDenied() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onSubscribe(any())).thenReturn(__ -> {
			throw new IllegalStateException("I FAILED TO OBLIGE");
		});
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertThrows(AccessDeniedException.class,
				() -> sut.handleOnSubscribeConstraints(AuthorizationDecision.PERMIT.withObligations(someConstraint()),
						mock(Subscription.class)));
	}

	@Test
	void when_handleConsumerConstraints_withObligation_noMatchingHandler_then_AccessDenied() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(false);
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertThrows(AccessDeniedException.class,
				() -> sut.handleOnSubscribeConstraints(AuthorizationDecision.PERMIT.withObligations(someConstraint()),
						mock(Subscription.class)));
	}

	@Test
	@Disabled
	void when_handleConsumerConstraints_withObligation_matchingHandlerReturnsNull_then_AccessDenied() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onSubscribe(any())).thenReturn(null);
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertThrows(AccessDeniedException.class,
				() -> sut.handleOnSubscribeConstraints(AuthorizationDecision.PERMIT.withObligations(someConstraint()),
						mock(Subscription.class)));
	}

	@Test
	void when_handleConsumerConstraints_withObligation_matchingHandlerSuccess_then_Success() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onSubscribe(any())).thenReturn(__ -> {
		});
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertDoesNotThrow(() -> sut.handleOnSubscribeConstraints(
				AuthorizationDecision.PERMIT.withObligations(someConstraint()), mock(Subscription.class)));
	}

	@Test
	void when_handleConsumerConstraints_withObligation_matchingHandlerFails_then_AccessDenied() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onSubscribe(any())).thenReturn(__ -> {
			throw new IllegalStateException("I FAILED TO OBLIGE");
		});
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertThrows(AccessDeniedException.class,
				() -> sut.handleOnSubscribeConstraints(AuthorizationDecision.PERMIT.withObligations(someConstraint()),
						mock(Subscription.class)));
	}

	@Test
	void when_handleConsumerConstraints_withNoConstraints_noHandler_then_Success() {
		var sut = new ReactiveConstraintEnforcementService(List.of());
		assertDoesNotThrow(
				() -> sut.handleOnSubscribeConstraints(AuthorizationDecision.PERMIT, mock(Subscription.class)));
	}

	@Test
	void when_handleOnRequestConstraints_withNoConstraints_noHandler_then_Success() {
		var sut = new ReactiveConstraintEnforcementService(List.of());
		assertDoesNotThrow(() -> sut.handleOnRequestConstraints(AuthorizationDecision.PERMIT, 1L));
	}

	@Test
	void when_handleOnRequestConstraints_withObligation_matchingHandlerSuccess_then_Success() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onRequest(any())).thenReturn(__ -> {
		});
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertDoesNotThrow(() -> sut
				.handleOnRequestConstraints(AuthorizationDecision.PERMIT.withObligations(someConstraint()), 1L));
	}

	@Test
	void when_handleConsumerConstraints_withObligation_noHandler_then_AccessDenied() {
		var sut = new ReactiveConstraintEnforcementService(List.of());
		assertThrows(AccessDeniedException.class,
				() -> sut.handleOnSubscribeConstraints(AuthorizationDecision.PERMIT.withObligations(someConstraint()),
						mock(Subscription.class)));
	}

	@Test
	void when_handleRunnableConstraints_withAdvice_noHandler_then_Success() {
		var sut = new ReactiveConstraintEnforcementService(List.of());
		assertDoesNotThrow(
				() -> sut.handleOnCompleteConstraints(AuthorizationDecision.PERMIT.withAdvice(someConstraint())));
	}

	@Test
	void when_handleRunnableConstraints_withAdvice_handlerFailing_then_Success() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onComplete(any())).thenThrow(new IllegalStateException("I FAILED TO OBLIGE"));
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertDoesNotThrow(
				() -> sut.handleOnCompleteConstraints(AuthorizationDecision.PERMIT.withAdvice(someConstraint())));
	}

	@Test
	void when_handleRunnableConstraints_withAdvice_noMatchingHandler_then_Success() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(false);
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertDoesNotThrow(
				() -> sut.handleOnCompleteConstraints(AuthorizationDecision.PERMIT.withAdvice(someConstraint())));
	}

	@Test
	void when_handleRunnableConstraints_withAdvice_matchingHandlerReturnsNull_then_Success() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onComplete(any())).thenReturn(null);
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertDoesNotThrow(
				() -> sut.handleOnCompleteConstraints(AuthorizationDecision.PERMIT.withAdvice(someConstraint())));
	}

	@Test
	void when_handleRunnableConstraints_withAdvice_matchingHandlerSuccess_then_Success() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onComplete(any())).thenReturn(() -> {
		});
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertDoesNotThrow(
				() -> sut.handleOnCompleteConstraints(AuthorizationDecision.PERMIT.withAdvice(someConstraint())));
	}

	@Test
	void when_handleRunnableConstraints_withAdvice_matchingHandlerFails_then_Success() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onComplete(any())).thenThrow(new IllegalStateException("I FAILED TO OBLIGE"));
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertDoesNotThrow(
				() -> sut.handleOnCompleteConstraints(AuthorizationDecision.PERMIT.withAdvice(someConstraint())));
	}

	@Test
	void when_handleTransformingConstraints_withAdvice_noHandler_then_SuccessOriginalValue() {
		var sut = new ReactiveConstraintEnforcementService(List.of());
		var actual = sut.handleOnNextConstraints(AuthorizationDecision.PERMIT.withAdvice(someConstraint()), 1);
		assertThat(actual, is(1));
	}

	@Test
	void when_handleTransformingConstraints_withAdvice_handlerFailing_then_SuccessOriginalValue() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onNextMap(any())).thenThrow(new IllegalStateException("I FAILED TO OBLIGE"));
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		var actual = sut.handleOnNextConstraints(AuthorizationDecision.PERMIT.withAdvice(someConstraint()), 1);
		assertThat(actual, is(1));
	}

	@Test
	void when_handleTransformingConstraints_withAdvice_noMatchingHandler_then_SuccessOriginalValue() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(false);
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		var actual = sut.handleOnNextConstraints(AuthorizationDecision.PERMIT.withAdvice(someConstraint()), 1);
		assertThat(actual, is(1));
	}

	@Test
	void when_handleTransformingConstraints_withAdvice_matchingHandlerReturnsNull_then_SuccessOriginalValue() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onNextMap(any())).thenReturn(null);
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		var actual = sut.handleOnNextConstraints(AuthorizationDecision.PERMIT.withAdvice(someConstraint()), 1);
		assertThat(actual, is(1));
	}

	@Test
	void when_handleTransformingConstraints_withAdvice_matchingHandlerSuccess_then_Success() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onNextMap(any())).thenReturn(__ -> 2);
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		var actual = sut.handleOnNextConstraints(AuthorizationDecision.PERMIT.withAdvice(someConstraint()), 1);
		assertThat(actual, is(2));
	}

	@Test
	void when_handleTransformingConstraints_withAdvice_matchingHandlerFails_then_SuccessOriginalValue() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onNextMap(any())).thenReturn(__ -> {
			throw new IllegalStateException("I FAILED TO OBLIGE");
		});
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		var actual = sut.handleOnNextConstraints(AuthorizationDecision.PERMIT.withAdvice(someConstraint()), 1);
		assertThat(actual, is(1));
	}

	@Test
	void when_handleConsumerConstraints_withAdvice_handlerFailing_then_Success() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onSubscribe(any())).thenReturn(__ -> {
			throw new IllegalStateException("I FAILED TO OBLIGE");
		});
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertDoesNotThrow(() -> sut.handleOnSubscribeConstraints(
				AuthorizationDecision.PERMIT.withAdvice(someConstraint()), mock(Subscription.class)));
	}

	@Test
	void when_handleConsumerConstraints_withAdvice_noMatchingHandler_then_Success() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(false);
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertDoesNotThrow(() -> sut.handleOnSubscribeConstraints(
				AuthorizationDecision.PERMIT.withAdvice(someConstraint()), mock(Subscription.class)));
	}

	@Test
	void when_handleConsumerConstraints_withObligation_matchingHandlerReturnsNull_then_Success() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onSubscribe(any())).thenReturn(null);
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertDoesNotThrow(() -> sut.handleOnSubscribeConstraints(
				AuthorizationDecision.PERMIT.withAdvice(someConstraint()), mock(Subscription.class)));
	}

	@Test
	void when_handleConsumerConstraints_withAdvice_matchingHandlerSuccess_then_Success() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onSubscribe(any())).thenReturn(__ -> {
		});
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertDoesNotThrow(() -> sut.handleOnSubscribeConstraints(
				AuthorizationDecision.PERMIT.withAdvice(someConstraint()), mock(Subscription.class)));
	}

	@Test
	void when_handleConsumerConstraints_withAdvice_matchingHandlerFails_then_Success() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		when(mockHandler.onSubscribe(any())).thenReturn(__ -> {
			throw new IllegalStateException("I FAILED TO OBLIGE");
		});
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		assertDoesNotThrow(() -> sut.handleOnSubscribeConstraints(
				AuthorizationDecision.PERMIT.withAdvice(someConstraint()), mock(Subscription.class)));
	}

	@Test
	void when_handleConsumerConstraints_withAdvice_noHandler_then_Success() {
		var sut = new ReactiveConstraintEnforcementService(List.of());
		assertDoesNotThrow(() -> sut.handleOnSubscribeConstraints(
				AuthorizationDecision.PERMIT.withAdvice(someConstraint()), mock(Subscription.class)));
	}

	@Test
	void when_handleAfterBlockingMethodInvocation_withAdvice_and_typeMissing_then_returnOroginalValue() {
		var mockHandler = mock(AbstractConstraintHandler.class);
		when(mockHandler.isResponsible(any())).thenReturn(true);
		var sut = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		var actual = sut.handleAfterBlockingMethodInvocation(AuthorizationDecision.PERMIT.withAdvice(someConstraint()),
				1, null);
		assertThat(actual, is(1));
	}

	private ArrayNode someConstraint() {
		var array = JSON.arrayNode();
		array.add("some constraint");
		return array;

	}

	private static boolean containsAll(String message, String... phrases) {
		for (var phrase : phrases)
			if (!message.contains(phrase))
				return false;
		return true;
	}

	private static ArrayNode arrayOf(JsonNode... nodes) {
		var array = MAPPER.createArrayNode();
		array.addAll(List.of(nodes));
		return array;
	}

}
