package io.sapl.vaadin;

import static io.sapl.api.interpreter.Val.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.security.access.AccessDeniedException;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.vaadin.VaadinPep.VaadinMultiButtonPepBuilder;
import io.sapl.vaadin.base.SecurityHelper;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class MultiBuilderTests {
	MultiBuilder sut;
	private PolicyDecisionPoint pdpMock;
	private VaadinConstraintEnforcementService vaadinConstraintEnforcementService;
	private static MockedStatic<SecurityHelper> securityHelperMock;
	private Disposable disposableMock;
	ComponentEventListener<DetachEvent> listener;
	private Consumer<IdentifiableAuthorizationDecision> subscribeConsumer; // Stores the lambda that is passed to
	// flux.subscribe() to test afterwards

	@BeforeAll
	static void beforeAll() {
		var subject = JSON.objectNode();
		subject.put("username", "dummy");
		securityHelperMock = mockStatic(SecurityHelper.class);
		securityHelperMock.when(SecurityHelper::getSubject).thenReturn(subject);
	}

	@AfterAll
	static void afterAll() {
		securityHelperMock.close();
	}

	@BeforeEach
	void setupTest() {
		pdpMock = mock(PolicyDecisionPoint.class);
		vaadinConstraintEnforcementService = mock(VaadinConstraintEnforcementService.class);
		sut = new MultiBuilder(pdpMock, vaadinConstraintEnforcementService);
	}

	@Test
	@SuppressWarnings("unchecked") // Suppress here because not allowed at (Consumer<? super
	// IdentifiableAuthorizationDecision>)any(Consumer.class)
	void when_BuildIsCalled_then_SubscribeIsCalled() {
		// GIVEN
		var buttonMock = getButtonMockWithUI();
		var fluxMock = getFluxMock();
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);

		// WHEN
		sut.subject("subject").with(buttonMock).action("action").resource("resource").environment("environment")
				.onDenyDo(__ -> {
				}).build();

		// THEN
		verify(fluxMock).subscribe((Consumer<? super IdentifiableAuthorizationDecision>) any(Consumer.class));
	}

	@Test
	@SuppressWarnings("unchecked") // suppress ComponentEventListener
	void when_BuildIsCalledWithNotAttachedComponent_then_StartMultiSubscriptionIsAddedToAsAttachListener() {
		// GIVEN
		var buttonMock = mock(Button.class);
		var fluxMock = getFluxMock();
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);
		var eventMock = mock(AttachEvent.class);
		var uiMock = mock(UI.class);

		when(buttonMock.getUI()).thenReturn(Optional.of(uiMock));
		doAnswer(invocation -> {
			invocation.getArgument(0, ComponentEventListener.class).onComponentEvent(eventMock);
			return null;
		}).when(buttonMock).addAttachListener(any());

		// WHEN
		sut.subject("subject").with(buttonMock).action("action").resource("resource").environment("environment")
				.onDenyDo(__ -> {
				}).build();

		// THEN
		verify(pdpMock).decide(any((MultiAuthorizationSubscription.class)));
		verify(buttonMock).addAttachListener(any(ComponentEventListener.class));
	}

	@Test
	@SuppressWarnings("unchecked") // suppress ComponentEventListener
	void when_BuildIsCalledWithNotAttachedComponentAndUIIsNotPresent_then_AccessDeniedExceptionIsThrown() {
		// GIVEN
		var buttonMock = mock(Button.class);
		var fluxMock = getFluxMock();
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);
		var eventMock = mock(AttachEvent.class);

		Optional<UI> optionalUI = mock(Optional.class);
		when(optionalUI.isPresent()).thenReturn(false);

		when(buttonMock.getUI()).thenReturn(optionalUI);
		doAnswer(invocation -> {
			invocation.getArgument(0, ComponentEventListener.class).onComponentEvent(eventMock);
			return null;
		}).when(buttonMock).addAttachListener(any());

		// WHEN + THEN
		assertThrows(AccessDeniedException.class, () -> sut.build(buttonMock));
	}

	@Test
	void when_BuildIsCalledTwice_then_AccessDeniedExceptionIsRaised() {
		// GIVEN
		var buttonMock = getButtonMockWithUI();
		var fluxMock = getFluxMock();
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);
		sut.with(buttonMock).onDenyDo(__ -> {
		}).action("action").resource("resource").build();

		// WHEN+THEN
		assertThrows(AccessDeniedException.class, () -> sut.build(buttonMock));
	}

	@Test
	void when_BuildIsCalledWithoutDenyRule_then_AccessDeniedExceptionIsRaised() {
		// GIVEN
		var buttonMock = getButtonMockWithUI();
		var fluxMock = getFluxMock();
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);

		// WHEN+THEN
		assertThrows(AccessDeniedException.class, () -> sut.with(buttonMock).build());
	}

	@Test
	void when_WithAndBuildIsCalled_then_ComponentIsAddedToVaadinPepArrayList() {
		// GIVEN
		var button = getButtonMockWithUI();
		var fluxMock = getFluxMock();
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);

		// WHEN
		sut.with(button).onDenyDo(__ -> {
		}).build();

		// THEN
		verify(button).isAttached();
		verify(button).getUI();
	}

	@Test
	void when_UnregisterPepIsCalled_then_ComponentIsRemovedFromVaadinPepArrayList() {
		// GIVEN
		var button = getButtonMockWithUI();
		var fluxMock = getFluxMock();
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);

		// Methods on SUT
		sut.with(button).onDenyDo(__ -> {
		}).build();

		// WHEN
		sut.unregisterPep(0);

		// THEN
		verify(button).addDetachListener(any());
		verify(pdpMock, times(1)).decide(any(MultiAuthorizationSubscription.class));
	}

	@Test
	@SuppressWarnings("unchecked") // mocks
	void when_buildIsCalledDetachListenerIsCalled_then_stopSubscription() {
		// GIVEN
		var buttonMock = getButtonMockWithUI();
		var detachEventMock = mock(DetachEvent.class);
		var fluxMock = mock(Flux.class);
		disposableMock = mock(Disposable.class);
		when(fluxMock.subscribe(any(Consumer.class))).thenReturn(disposableMock);
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);
		doAnswer(invocation -> {
			listener = invocation.getArgument(0, ComponentEventListener.class);
			return null;
		}).when(buttonMock).addDetachListener(any());
		sut.with(buttonMock).onDenyDo(__ -> {
		}).build();

		// WHEN
		listener.onComponentEvent(detachEventMock);

		// THEN
		verify(disposableMock).isDisposed();
	}

	@Test
	@SuppressWarnings("unchecked") // suppress at subscribe any consumer
	void when_UnregisterPepIsCalledWithHigherIndexThenLength_then_ComponentIsNotRemovedFromList() {
		// GIVEN
		var buttonMock = getButtonMockWithUI();
		var fluxMock = getFluxMock();
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);

		// Methods on SUT
		sut.with(buttonMock).onDenyDo(__ -> {
		}).build();

		// WHEN
		sut.unregisterPep(1);

		// THEN
		verify(buttonMock).addDetachListener(any());
		verify(pdpMock, times(2)).decide(any(MultiAuthorizationSubscription.class));
		verify(fluxMock, times(2)).subscribe((Consumer<? super IdentifiableAuthorizationDecision>) any(Consumer.class));

	}

	@Test
	@SuppressWarnings("unchecked") // suppress at subscribe any consumer
	void when_UnregisterPepIsCalledAndOtherPepsAreInList_then_SubscriptionIsRestartedAfterStopping() {
		// GIVEN
		var buttonMock = getButtonMockWithUI();
		var fluxMock = getFluxMock();
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);

		// Methods on SUT
		sut.with(buttonMock).onDenyDo(__ -> {
		}).and(mock(Span.class)).onDenyDo(__ -> {
		}).and(mock(TextField.class)).onDenyDo(__ -> {
		}).and(mock(Checkbox.class)).onDenyDo(__ -> {
		}).and(mock(Component.class)).onDenyDo(__ -> {
		}).and(mock(Button.class)).onDenyDo(__ -> {
		}).build();

		// WHEN
		sut.unregisterPep(0);
		// THEN
		verify(pdpMock).decide(any(MultiAuthorizationSubscription.class));
		verify(fluxMock).subscribe((Consumer<? super IdentifiableAuthorizationDecision>) any(Consumer.class));
	}

	@Test
	void when_AndIsCalledAfterBuild_then_ThrowAccessDeniedException() {
		// GIVEN
		var buttonMock = getButtonMockWithUI();
		var fluxMock = getFluxMock();
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);
		VaadinMultiButtonPepBuilder pepBuilder = sut.with(buttonMock).onDenyDo(__ -> {
		});
		pepBuilder.and();

		// WHEN
		AccessDeniedException exception = assertThrows(AccessDeniedException.class, pepBuilder::and);

		// THEN
		assertEquals("Builder has already been build. The builder can only be used once.", exception.getMessage());
	}

	/**
	 * This test checks if the multi subscription is stopped when
	 * {@link MultiBuilder#unregisterPep(int)} is called. Note: The disposable mock
	 * is returned from the flux.subscribe() (See getFluxMock()).
	 */
	@Test
	void when_UnregisterPepIsCalled_then_DisposableDisposeIsCalled() {
		// GIVEN
		disposableMock = mock(Disposable.class);
		when(disposableMock.isDisposed()).thenReturn(false);
		var buttonMock = getButtonMockWithUI();
		var fluxMock = getFluxMock();
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);

		// Methods on SUT
		sut.with(buttonMock).onDenyDo(__ -> {
		}).build();

		// WHEN
		sut.unregisterPep(0);

		// THEN
		verify(disposableMock).dispose();
	}

	@Test
	void when_UnregisterPepIsCalledWithDisposedDisposable_then_DisposableDisposeIsNotCalled() {
		// GIVEN
		disposableMock = mock(Disposable.class);
		when(disposableMock.isDisposed()).thenReturn(true);
		var buttonMock = getButtonMockWithUI();
		var fluxMock = getFluxMock();
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);

		// Methods on SUT
		sut.with(buttonMock).onDenyDo(__ -> {
		}).build();

		// WHEN
		sut.unregisterPep(0);

		// THEN
		verify(disposableMock, times(0)).dispose();
	}

	@Test
	void when_decisionOccurs_then_ConstraintsOfDecisionsAreEnforcedWithCorrectParameters() {
		// GIVEN
		var buttonMock = getButtonMockWithUI();
		var fluxMock = getFluxMock();
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);

		var iadMock = mock(IdentifiableAuthorizationDecision.class);
		when(iadMock.getAuthorizationSubscriptionId()).thenReturn("0"); // 0 = id of button
		when(iadMock.getAuthorizationDecision()).thenReturn(AuthorizationDecision.PERMIT);

		@SuppressWarnings("unchecked")
		var monoMock = (Mono<AuthorizationDecision>) mock(Mono.class);
		when(vaadinConstraintEnforcementService.enforceConstraintsOfDecision(any(AuthorizationDecision.class),
				any(UI.class), any(VaadinPep.class))).thenReturn(monoMock);

		// Methods on SUT
		sut.subject("subject").with(buttonMock).onDenyDo(__ -> {
		}).build();

		// WHEN
		subscribeConsumer.accept(iadMock);

		// THEN
		ArgumentCaptor<AuthorizationDecision> authorizationDecisionArgument = ArgumentCaptor
				.forClass(AuthorizationDecision.class);
		ArgumentCaptor<UI> uiArgument = ArgumentCaptor.forClass(UI.class);
		assertTrue(buttonMock.getUI().isPresent());
		verify(vaadinConstraintEnforcementService).enforceConstraintsOfDecision(authorizationDecisionArgument.capture(),
				uiArgument.capture(), any(VaadinPep.class));
		assertEquals(buttonMock.getUI().get(), uiArgument.getValue());
		assertEquals(Decision.PERMIT, authorizationDecisionArgument.getValue().getDecision());
	}

	@Test
	void when_decisionOccursWithoutID_then_ConstraintsOfDecisionsAreNotEnforced() {
		// GIVEN
		var buttonMock = getButtonMockWithUI();
		var fluxMock = getFluxMock();
		when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);

		IdentifiableAuthorizationDecision iadMock = mock(IdentifiableAuthorizationDecision.class);
		when(iadMock.getAuthorizationSubscriptionId()).thenReturn(null);
		when(iadMock.getAuthorizationDecision()).thenReturn(AuthorizationDecision.PERMIT);

		@SuppressWarnings("unchecked")
		var monoMock = (Mono<AuthorizationDecision>) mock(Mono.class);
		when(vaadinConstraintEnforcementService.enforceConstraintsOfDecision(any(AuthorizationDecision.class),
				any(UI.class), any(VaadinPep.class))).thenReturn(monoMock);

		// Methods on SUT
		sut.with(buttonMock).onDenyDo(__ -> {
		}).build();

		// WHEN
		subscribeConsumer.accept(iadMock);

		// THEN
		assertTrue(buttonMock.getUI().isPresent());
		verify(vaadinConstraintEnforcementService, times(0)).enforceConstraintsOfDecision(any(), any(),
				any(VaadinPep.class));
	}

	@Test
	void when_buildUIisNotPresent_then_AccessDeniedExceptionIsThrown() {
		// GIVEN

		Component component = mock(Component.class);
		@SuppressWarnings("unchecked")
		Optional<UI> optionalUI = (Optional<UI>) mock(Optional.class);
		when(component.isAttached()).thenReturn(true);
		when(component.getUI()).thenReturn(optionalUI);
		when(optionalUI.isPresent()).thenReturn(false);

		// WHEN + THEN
		assertThrows(AccessDeniedException.class, () -> sut.build(component));
	}

	Flux<IdentifiableAuthorizationDecision> getFluxMock() {
		@SuppressWarnings("unchecked")
		Flux<IdentifiableAuthorizationDecision> f = (Flux<IdentifiableAuthorizationDecision>) mock(Flux.class,
				invocation -> {
					if (Disposable.class.equals(invocation.getMethod().getReturnType())) {
						subscribeConsumer = invocation.getArgument(0); // Get the lambda that is passed to
						// flux.subscribe()
						return disposableMock;
					}
					return invocation.getMock();
				});
		return f;
	}

	Button getButtonMockWithUI() {
		Button button = mock(Button.class);
		UI ui = mock(UI.class);
		Optional<UI> o = Optional.of(ui);
		when(button.isAttached()).thenReturn(true);
		when(button.getUI()).thenReturn(o);
		return button;
	}
}
