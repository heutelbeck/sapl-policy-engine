package io.sapl.vaadin;

import static io.sapl.api.interpreter.Val.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.UI;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.constraints.api.ConsumerConstraintHandlerProvider;
import io.sapl.spring.constraints.api.RunnableConstraintHandlerProvider;
import io.sapl.vaadin.constraint.VaadinFunctionConstraintHandlerProvider;
import reactor.core.publisher.Mono;

public class VaadinConstraintEnforcementServiceTests {

	List<VaadinFunctionConstraintHandlerProvider> globalVaadinFunctionProvider;
	List<ConsumerConstraintHandlerProvider<UI>> globalConsumerProviders;
	List<RunnableConstraintHandlerProvider> globalRunnableProviders;
	VaadinConstraintEnforcementService sut;
	VaadinConstraintHandlerBundle vaadinConstraintHandlerBundle = new VaadinConstraintHandlerBundle();

	@BeforeEach
	void beforeEach() {
		globalVaadinFunctionProvider = new ArrayList<>();
		globalConsumerProviders = new ArrayList<>();
		globalRunnableProviders = new ArrayList<>();

		sut = new VaadinConstraintEnforcementService(globalVaadinFunctionProvider, globalConsumerProviders,
				globalRunnableProviders);
	}

	@Test
	void when_addGlobalRunnableProviders_then_GlobalRunnableProviderSizeIsOne() {
		// GIVEN
		var provider = mock(RunnableConstraintHandlerProvider.class);

		// WHEN
		sut.addGlobalRunnableProviders(provider);

		// THEN
		assertEquals(1, globalRunnableProviders.size());
	}

	@Test
	void when_addGlobalVaadinFunctionProvider_then_GlobalVaadinFunctionProviderSizeIsOne() {
		// GIVEN
		var provider = mock(VaadinFunctionConstraintHandlerProvider.class);

		// WHEN
		sut.addGlobalVaadinFunctionProvider(provider);

		// THEN
		assertEquals(1, globalVaadinFunctionProvider.size());
	}

	@Test
	void when_addGlobalConsumerProviders_then_GlobalConsumerProvidersSizeIsOne() {
		// GIVEN
		@SuppressWarnings("unchecked") // suppress mock
		ConsumerConstraintHandlerProvider<UI> provider = mock(ConsumerConstraintHandlerProvider.class);

		// WHEN
		sut.addGlobalConsumerProviders(provider);

		// THEN
		assertEquals(1, globalConsumerProviders.size());
	}

	@Test
	void when_enforceConstraintsOfDecisionWithEmptyDecisionAndNoProvider_then_DoNothingAndReturnMonoWithDecision() {
		// GIVEN
		var decisionMock = mock(AuthorizationDecision.class);
		var uiMock = mock(UI.class);
		var vaadinPepMock = mock(VaadinPep.class);

		// WHEN
		Mono<AuthorizationDecision> returnValue = sut.enforceConstraintsOfDecision(decisionMock, uiMock, vaadinPepMock);

		// THEN
		verify(decisionMock, times(1)).getAdvice();
		verify(decisionMock, times(1)).getObligations();
		assertEquals(decisionMock, returnValue.block());
	}

	@Test
	void when_enforceConstraintsOfDecisionWithObligationInDecisionAndNoHandler_then_ThrowReturnEmptyDecisionWithDeny() {
		// GIVEN
		var decisionMock = mock(AuthorizationDecision.class);
		addObligation(decisionMock);
		var uiMock = mock(UI.class);
		var vaadinPepMock = mock(VaadinPep.class);

		// WHEN
		Mono<AuthorizationDecision> returnValue = sut.enforceConstraintsOfDecision(decisionMock, uiMock, vaadinPepMock);

		// THEN
		assertEquals(Decision.DENY, returnValue.block().getDecision());
		assertEquals(Optional.empty(), returnValue.block().getObligations());
	}

	@Test
	void when_enforceConstraintsOfDecisionWithObligationInDecisionAndRunnableHandler_then_runnableIsCalledAndDecisionIsReturned() {
		// GIVEN
		var decisionMock = mock(AuthorizationDecision.class);
		addObligation(decisionMock);
		var uiMock = mock(UI.class);
		var vaadinPepMock = mock(VaadinPep.class);
		RunnableConstraintHandlerProvider runnableConstraintHandlerProviderMock = mock(
				RunnableConstraintHandlerProvider.class);
		Runnable runnableMock = mock(Runnable.class);
		when(runnableConstraintHandlerProviderMock.getHandler(any())).thenReturn(runnableMock);
		when(runnableConstraintHandlerProviderMock.isResponsible(any())).thenReturn(true);
		sut.addGlobalRunnableProviders(runnableConstraintHandlerProviderMock);

		// WHEN
		Mono<AuthorizationDecision> returnValue = sut.enforceConstraintsOfDecision(decisionMock, uiMock, vaadinPepMock);

		// THEN
		verify(runnableMock, times(1)).run();
		assertEquals(decisionMock, returnValue.block());
	}

	@Test
	void when_enforceConstraintsOfDecisionWithObligationInDecisionAndNullRunnableHandler_then_throwAccessDeniedAndReturnDenied() {
		// GIVEN
		var decisionMock = mock(AuthorizationDecision.class);
		addObligation(decisionMock);
		var uiMock = mock(UI.class);
		var vaadinPepMock = mock(VaadinPep.class);
		var runnableConstraintHandlerProviderMock = mock(RunnableConstraintHandlerProvider.class);
		when(runnableConstraintHandlerProviderMock.getHandler(any())).thenReturn(null);
		when(runnableConstraintHandlerProviderMock.isResponsible(any())).thenReturn(true);
		sut.addGlobalRunnableProviders(runnableConstraintHandlerProviderMock);

		// WHEN
		Mono<AuthorizationDecision> returnValue = sut.enforceConstraintsOfDecision(decisionMock, uiMock, vaadinPepMock);

		// THEN
		assertEquals(Decision.DENY, returnValue.block().getDecision());
	}

	@Test
	void when_enforceConstraintsOfDecisionWithAdviceInDecisionAndNullRunnableHandler_then_resumeWorkflowAndReturnAuthroizationDecision() {
		// GIVEN
		var decisionMock = mock(AuthorizationDecision.class);
		addAdvice(decisionMock);
		var uiMock = mock(UI.class);
		var vaadinPepMock = mock(VaadinPep.class);
		var runnableConstraintHandlerProviderMock = mock(RunnableConstraintHandlerProvider.class);
		when(runnableConstraintHandlerProviderMock.getHandler(any())).thenReturn(null);
		when(runnableConstraintHandlerProviderMock.isResponsible(any())).thenReturn(true);
		sut.addGlobalRunnableProviders(runnableConstraintHandlerProviderMock);

		// WHEN
		Mono<AuthorizationDecision> returnValue = sut.enforceConstraintsOfDecision(decisionMock, uiMock, vaadinPepMock);

		// THEN
		assertEquals(decisionMock, returnValue.block());
	}

	@Test
	void when_enforceConstraintsOfDecisionWithObligationInDecisionAndNullVaadinConstraintHandler_then_throwAccessDeniedAndReturnDenied() {
		// GIVEN
		var decisionMock = mock(AuthorizationDecision.class);
		addObligation(decisionMock);
		var uiMock = mock(UI.class);
		var vaadinPepMock = mock(VaadinPep.class);
		var vaadinFunctionConstraintHandlerProviderMock = mock(VaadinFunctionConstraintHandlerProvider.class);
		when(vaadinFunctionConstraintHandlerProviderMock.getHandler(any())).thenReturn(null);
		when(vaadinFunctionConstraintHandlerProviderMock.isResponsible(any())).thenReturn(true);
		sut.addGlobalVaadinFunctionProvider(vaadinFunctionConstraintHandlerProviderMock);

		// WHEN
		Mono<AuthorizationDecision> returnValue = sut.enforceConstraintsOfDecision(decisionMock, uiMock, vaadinPepMock);

		// THEN
		assertEquals(Decision.DENY, returnValue.block().getDecision());
	}

	@Test
	void when_enforceConstraintsOfDecisionWithAdviceInDecisionAndNullVaadinConstraintHandler_then_resumeWorkflowAndReturnAuthorizationDecision() {
		// GIVEN
		var decisionMock = mock(AuthorizationDecision.class);
		addAdvice(decisionMock);
		var uiMock = mock(UI.class);
		var vaadinPepMock = mock(VaadinPep.class);
		var vaadinFunctionConstraintHandlerProviderMock = mock(VaadinFunctionConstraintHandlerProvider.class);
		@SuppressWarnings("unchecked") // suppress mock
		Function<UI, Mono<Boolean>> functionMock = mock(Function.class);
		Mono<Boolean> monoMock = Mono.just(true);
		when(functionMock.apply(any())).thenReturn(monoMock);
		when(vaadinFunctionConstraintHandlerProviderMock.getHandler(any())).thenReturn(functionMock);
		when(vaadinFunctionConstraintHandlerProviderMock.isResponsible(any())).thenReturn(true);
		sut.addGlobalVaadinFunctionProvider(vaadinFunctionConstraintHandlerProviderMock);

		// WHEN
		Mono<AuthorizationDecision> returnValue = sut.enforceConstraintsOfDecision(decisionMock, uiMock, vaadinPepMock);

		// THEN
		assertEquals(decisionMock, returnValue.block());
	}

	@Test
	void when_enforceConstraintsOfDecisionWithAdviceInDecisionAndNullVaadinConstraintHandler_then_resumeWorkflowAndDenyAuthroizationDecision() {
		// GIVEN
		var decisionMock = mock(AuthorizationDecision.class);
		addAdvice(decisionMock);
		var uiMock = mock(UI.class);
		var vaadinPepMock = mock(VaadinPep.class);
		var vaadinFunctionConstraintHandlerProviderMock = mock(VaadinFunctionConstraintHandlerProvider.class);
		when(vaadinFunctionConstraintHandlerProviderMock.getHandler(any())).thenReturn(null);
		when(vaadinFunctionConstraintHandlerProviderMock.isResponsible(any())).thenReturn(true);
		sut.addGlobalVaadinFunctionProvider(vaadinFunctionConstraintHandlerProviderMock);

		// WHEN
		Mono<AuthorizationDecision> returnValue = sut.enforceConstraintsOfDecision(decisionMock, uiMock, vaadinPepMock);

		// THEN
		assertEquals(Decision.DENY, returnValue.block().getDecision());
	}

	@Test
	@SuppressWarnings("unchecked") // suppress mocks
	void when_enforceConstraintsOfDecisionWithObligationInDecisionAndConsumerConstraintHandler_then_runnableIsCalledAndDecisionIsReturned() {
		// GIVEN
		var decisionMock = mock(AuthorizationDecision.class);
		addObligation(decisionMock);
		var uiMock = mock(UI.class);
		var vaadinPepMock = mock(VaadinPep.class);
		ConsumerConstraintHandlerProvider<UI> consumerConstraintHandlerProviderMock = mock(
				ConsumerConstraintHandlerProvider.class);
		Consumer<UI> consumerMock = mock(Consumer.class);
		when(consumerConstraintHandlerProviderMock.getHandler(any())).thenReturn(consumerMock);
		when(consumerConstraintHandlerProviderMock.isResponsible(any())).thenReturn(true);
		sut.addGlobalConsumerProviders(consumerConstraintHandlerProviderMock);

		// WHEN
		Mono<AuthorizationDecision> returnValue = sut.enforceConstraintsOfDecision(decisionMock, uiMock, vaadinPepMock);

		// THEN
		verify(consumerMock, times(1)).accept(any());
		assertEquals(decisionMock, returnValue.block());
	}

	@Test
	void when_enforceConstraintsOfDecisionWithObligationInDecisionAndNullConsumerConstraintHandler_then_throwAccessDeniedAndReturnDenied() {
		// GIVEN
		var decisionMock = mock(AuthorizationDecision.class);
		addObligation(decisionMock);
		var uiMock = mock(UI.class);
		var vaadinPepMock = mock(VaadinPep.class);
		@SuppressWarnings("unchecked") // suppress mock
		ConsumerConstraintHandlerProvider<UI> consumerConstraintHandlerProviderMock = mock(
				ConsumerConstraintHandlerProvider.class);
		when(consumerConstraintHandlerProviderMock.getHandler(any())).thenReturn(null);
		when(consumerConstraintHandlerProviderMock.isResponsible(any())).thenReturn(true);
		sut.addGlobalConsumerProviders(consumerConstraintHandlerProviderMock);

		// WHEN
		Mono<AuthorizationDecision> returnValue = sut.enforceConstraintsOfDecision(decisionMock, uiMock, vaadinPepMock);

		// THEN
		assertEquals(Decision.DENY, returnValue.block().getDecision());
	}

	@Test
	void when_enforceConstraintsOfDecisionWithAdviceInDecisionAndNullConsumerConstraintHandler_then_resumeWorkflowAndReturnAuthroizationDecision() {
		// GIVEN
		var decisionMock = mock(AuthorizationDecision.class);
		addAdvice(decisionMock);
		var uiMock = mock(UI.class);
		var vaadinPepMock = mock(VaadinPep.class);
		@SuppressWarnings("unchecked") // suppress mock
		ConsumerConstraintHandlerProvider<UI> consumerConstraintHandlerProviderMock = mock(
				ConsumerConstraintHandlerProvider.class);
		when(consumerConstraintHandlerProviderMock.getHandler(any())).thenReturn(null);
		when(consumerConstraintHandlerProviderMock.isResponsible(any())).thenReturn(true);
		sut.addGlobalConsumerProviders(consumerConstraintHandlerProviderMock);

		// WHEN
		Mono<AuthorizationDecision> returnValue = sut.enforceConstraintsOfDecision(decisionMock, uiMock, vaadinPepMock);

		// THEN
		assertEquals(decisionMock, returnValue.block());
	}

	@Test
	void when_enforceConstraintsOfDecisionWithAllProvidersButEmptyDecision_then_DoNothingAndReturnMonoWithDecision() {
		// GIVEN
		var decisionMock = mock(AuthorizationDecision.class);
		var uiMock = mock(UI.class);
		var vaadinPepMock = mock(VaadinPep.class);
		var runnableConstraintHandlerProviderMock = mock(
				RunnableConstraintHandlerProvider.class);
		sut.addGlobalRunnableProviders(runnableConstraintHandlerProviderMock);
		var vaadinFunctionConstraintHandlerProviderMock = mock(
				VaadinFunctionConstraintHandlerProvider.class);
		sut.addGlobalVaadinFunctionProvider(vaadinFunctionConstraintHandlerProviderMock);
		@SuppressWarnings("unchecked") // suppress mock
		ConsumerConstraintHandlerProvider<UI> consumerConstraintHandlerProviderMock = mock(
				ConsumerConstraintHandlerProvider.class);
		sut.addGlobalConsumerProviders(consumerConstraintHandlerProviderMock);

		// WHEN
		Mono<AuthorizationDecision> returnValue = sut.enforceConstraintsOfDecision(decisionMock, uiMock, vaadinPepMock);

		// THEN
		verify(decisionMock, times(1)).getAdvice();
		verify(decisionMock, times(1)).getObligations();
		assertEquals(decisionMock, returnValue.block());
	}

	private void addObligation(AuthorizationDecision decisionMock) {
		var obligations = JSON.arrayNode();
		obligations.add(JSON.textNode("obligation"));
		when(decisionMock.getObligations()).thenReturn(Optional.of(obligations));
	}

	private void addAdvice(AuthorizationDecision decisionMock) {
		var advice = JSON.arrayNode().add(JSON.textNode("advice"));
		when(decisionMock.getAdvice()).thenReturn(Optional.of(advice));
	}

}
