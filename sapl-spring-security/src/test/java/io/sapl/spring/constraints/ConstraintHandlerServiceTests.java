package io.sapl.spring.constraints;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pep.ConstraintHandler;

class ConstraintHandlerServiceTests {
	public static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Test
	void whenNoConstraints_thenNoException() {
		var sut = new ConstraintHandlerService(List.of());
		var decision = AuthorizationDecision.PERMIT;
		assertDoesNotThrow(() -> {
			sut.handleAdvices(decision);
			sut.handleObligations(decision);
		});
	}

	@Test
	void whenHandlerForAdvicesFail_thenNoException() {
		var sut = new ConstraintHandlerService(List.of(new HandleAllAndFailConstraintHandler()));
		var advices = JSON.arrayNode();
		advices.add("advice1");
		var decision = AuthorizationDecision.PERMIT.withAdvices(advices);
		assertDoesNotThrow(() -> {
			sut.handleAdvices(decision);
		});
	}

	@Test
	void whenNoHandlerForAdvices_thenNoException() {
		var sut = new ConstraintHandlerService(List.of(new CanHandleNothingConstraintHandler()));
		var advices = JSON.arrayNode();
		advices.add("advice1");
		var decision = AuthorizationDecision.PERMIT.withAdvices(advices);
		assertDoesNotThrow(() -> {
			sut.handleAdvices(decision);
		});
	}
	
	@Test
	void whenHandlerForAdvicesSucceed_thenNoException() {
		var sut = new ConstraintHandlerService(List.of(new HandleAllWithSuccessConstraintHandler()));
		var advices = JSON.arrayNode();
		advices.add("advice1");
		var decision = AuthorizationDecision.PERMIT.withAdvices(advices);
		assertDoesNotThrow(() -> {
			sut.handleAdvices(decision);
		});
	}
	
	@Test
	void whenHandlerForObligationsFail_thenAccessDenied() {
		var sut = new ConstraintHandlerService(List.of(new HandleAllAndFailConstraintHandler()));
		var obligations = JSON.arrayNode();
		obligations.add("obligation1");
		var decision = AuthorizationDecision.PERMIT.withObligations(obligations);
		assertThrows(AccessDeniedException.class, () -> {
			sut.handleObligations(decision);
		});
	}

	@Test
	void whenNoHandlerForObligation_thenAccessDenied() {
		var sut = new ConstraintHandlerService(List.of(new CanHandleNothingConstraintHandler()));
		var obligations = JSON.arrayNode();
		obligations.add("obligation1");
		var decision = AuthorizationDecision.PERMIT.withObligations(obligations);
		assertThrows(AccessDeniedException.class, () -> {
			sut.handleObligations(decision);
		});
	}
	
	@Test
	void whenHandlerForObligationsSucceed_thenNoException() {
		var sut = new ConstraintHandlerService(List.of(new HandleAllWithSuccessConstraintHandler()));
		var obligations = JSON.arrayNode();
		obligations.add("obligation1");
		obligations.add("obligation2");
		var decision = AuthorizationDecision.PERMIT.withObligations(obligations);
		assertDoesNotThrow(() -> {
			sut.handleObligations(decision);
		});
	}

	@Test
	void whenHandlerForObligationsFail_thenAccessDeniedButAllPossibleHandlersAreTriggered() {
		var failHandler = mock(ConstraintHandler.class);
		when(failHandler.canHandle(any())).thenReturn(true);
		when(failHandler.handle(any())).thenReturn(false);		
		var successHandler = mock(ConstraintHandler.class);
		when(successHandler.canHandle(any())).thenReturn(true);
		when(successHandler.handle(any())).thenReturn(true);
		var sut = new ConstraintHandlerService(List.of(failHandler,successHandler));
		var obligations = JSON.arrayNode();
		obligations.add("obligation1");
		obligations.add("obligation2");
		var decision = AuthorizationDecision.PERMIT.withObligations(obligations);
		
		assertThrows(AccessDeniedException.class, () -> {
			sut.handleObligations(decision);
		});
		
		verify(failHandler,times(2)).handle(any());
		verify(successHandler,times(2)).handle(any());
	}

		
	protected static class HandleAllWithSuccessConstraintHandler implements ConstraintHandler {

		@Override
		public boolean handle(JsonNode constraint) {
			return true;
		}

		@Override
		public boolean canHandle(JsonNode constraint) {
			return true;
		}

	}

	protected static class HandleAllAndFailConstraintHandler implements ConstraintHandler {

		@Override
		public boolean handle(JsonNode constraint) {
			return false;
		}

		@Override
		public boolean canHandle(JsonNode constraint) {
			return true;
		}

	}

	protected static class CanHandleNothingConstraintHandler implements ConstraintHandler {

		@Override
		public boolean handle(JsonNode constraint) {
			return false;
		}

		@Override
		public boolean canHandle(JsonNode constraint) {
			return false;
		}

	}

}
