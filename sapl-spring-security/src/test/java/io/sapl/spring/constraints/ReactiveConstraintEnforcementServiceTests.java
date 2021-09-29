package io.sapl.spring.constraints;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.pdp.AuthorizationDecision;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

public class ReactiveConstraintEnforcementServiceTests {
	private final static ObjectMapper MAPPER = new ObjectMapper();

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
		var authzDecision = AuthorizationDecision.PERMIT.withAdvices(advice);
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
		var authzDecision = AuthorizationDecision.PERMIT.withAdvices(obligations);
		Flux<Object> resourceAccessPoint = Flux.just(1, 2, 3);

		var constraintEnforcementService = new ReactiveConstraintEnforcementService(List.of(mockHandler));
		var protectedResource = constraintEnforcementService.enforceConstraintsOnResourceAccessPoint(authzDecision,
				resourceAccessPoint);

		StepVerifier.create(protectedResource).expectNext(1, 2, 3).verifyComplete();

		verify(mockHandler, times(1)).isResponsible(any());
		verify(mockHandler, times(1)).applyAdvice(any(), any());
		verify(mockOnSubscribtionConsumer, times(1)).accept(any());
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
