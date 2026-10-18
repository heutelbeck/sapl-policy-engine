/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.vaadin;

import tools.jackson.databind.node.JsonNodeFactory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.junit.jupiter.api.DisplayName;
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
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import io.sapl.vaadin.VaadinPep.VaadinMultiButtonPepBuilder;
import io.sapl.vaadin.base.SecurityHelper;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@DisplayName("Multi builder")
class MultiBuilderTests {
    MultiBuilder                                        sut;
    private ReactivePolicyDecisionPoint                 pdpMock;
    private VaadinConstraintEnforcementService          vaadinConstraintEnforcementService;
    private static MockedStatic<SecurityHelper>         securityHelperMock;
    private Disposable                                  disposableMock;
    ComponentEventListener<DetachEvent>                 listener;
    private Consumer<IdentifiableAuthorizationDecision> subscribeConsumer;                 // Stores the lambda that is
                                                                                           // passed to
    // flux.subscribe() to test afterwards

    @BeforeAll
    static void beforeAll() {
        var subject = JsonNodeFactory.instance.objectNode();
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
        pdpMock                            = mock(ReactivePolicyDecisionPoint.class);
        vaadinConstraintEnforcementService = mock(VaadinConstraintEnforcementService.class);
        sut                                = new MultiBuilder(pdpMock, vaadinConstraintEnforcementService);
    }

    @Test
    @SuppressWarnings("unchecked") // Suppress here because not allowed at (Consumer<? super
    // IdentifiableAuthorizationDecision>)any(Consumer.class)
    void when_BuildIsCalled_then_SubscribeIsCalled() {
        // GIVEN
        var buttonMock = getButtonMockWithUI();
        var fluxMock   = getFluxMock();
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);

        // WHEN
        sut.subject("subject").with(buttonMock).action("action").resource("resource").environment("environment")
                .onDenyDo(x -> {}).build();

        // THEN
        verify(fluxMock).subscribe((Consumer<? super IdentifiableAuthorizationDecision>) any(Consumer.class));
    }

    @Test
    @SuppressWarnings("unchecked") // suppress ComponentEventListener
    void when_BuildIsCalledWithNotAttachedComponent_then_StartMultiSubscriptionIsAddedToAsAttachListener() {
        // GIVEN
        var buttonMock = mock(Button.class);
        var fluxMock   = getFluxMock();
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);
        var eventMock = mock(AttachEvent.class);
        var uiMock    = mock(UI.class);

        when(buttonMock.getUI()).thenReturn(Optional.of(uiMock));
        doAnswer(invocation -> {
            invocation.getArgument(0, ComponentEventListener.class).onComponentEvent(eventMock);
            return null;
        }).when(buttonMock).addAttachListener(any());

        // WHEN
        sut.subject("subject").with(buttonMock).action("action").resource("resource").environment("environment")
                .onDenyDo(x -> {}).build();

        // THEN
        verify(pdpMock).decide(any((MultiAuthorizationSubscription.class)));
        verify(buttonMock).addAttachListener(any(ComponentEventListener.class));
    }

    @Test
    @SuppressWarnings("unchecked") // suppress ComponentEventListener
    void when_BuildIsCalledWithNotAttachedComponentAndUIIsNotPresent_then_AccessDeniedExceptionIsThrown() {
        // GIVEN
        var buttonMock = mock(Button.class);
        var fluxMock   = getFluxMock();
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
        assertThatThrownBy(() -> sut.build(buttonMock)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void when_BuildIsCalledTwice_then_AccessDeniedExceptionIsRaised() {
        // GIVEN
        var buttonMock = getButtonMockWithUI();
        var fluxMock   = getFluxMock();
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);
        sut.with(buttonMock).onDenyDo(x -> {}).action("action").resource("resource").build();

        // WHEN+THEN
        assertThatThrownBy(() -> sut.build(buttonMock)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void when_BuildIsCalledWithoutDenyRule_then_AccessDeniedExceptionIsRaised() {
        // GIVEN
        var buttonMock = getButtonMockWithUI();
        var fluxMock   = getFluxMock();
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);
        var sutWithMockButton = sut.with(buttonMock);
        // WHEN+THEN
        assertThatThrownBy(sutWithMockButton::build).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void when_WithAndBuildIsCalled_then_ComponentIsAddedToVaadinPepArrayList() {
        // GIVEN
        var button   = getButtonMockWithUI();
        var fluxMock = getFluxMock();
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);

        // WHEN
        sut.with(button).onDenyDo(x -> {}).build();

        // THEN
        verify(button).isAttached();
        verify(button).getUI();
    }

    @Test
    void when_UnregisterPepIsCalled_then_ComponentIsRemovedFromVaadinPepArrayList() {
        // GIVEN
        var button   = getButtonMockWithUI();
        var fluxMock = getFluxMock();
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);

        // Methods on SUT
        sut.with(button).onDenyDo(x -> {}).build();

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
        var buttonMock      = getButtonMockWithUI();
        var detachEventMock = mock(DetachEvent.class);
        var fluxMock        = mock(Flux.class);
        disposableMock = mock(Disposable.class);
        when(fluxMock.subscribe(any(Consumer.class))).thenReturn(disposableMock);
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);
        doAnswer(invocation -> {
            listener = invocation.getArgument(0, ComponentEventListener.class);
            return null;
        }).when(buttonMock).addDetachListener(any());
        sut.with(buttonMock).onDenyDo(x -> {}).build();

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
        var fluxMock   = getFluxMock();
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);

        // Methods on SUT
        sut.with(buttonMock).onDenyDo(x -> {}).build();

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
        var fluxMock   = getFluxMock();
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);

        // Methods on SUT
        sut.with(buttonMock).onDenyDo(x -> {}).and(mock(Span.class)).onDenyDo(x -> {}).and(mock(TextField.class))
                .onDenyDo(x -> {}).and(mock(Checkbox.class)).onDenyDo(x -> {}).and(mock(Component.class))
                .onDenyDo(x -> {}).and(mock(Button.class)).onDenyDo(x -> {}).build();

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
        var fluxMock   = getFluxMock();
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);
        VaadinMultiButtonPepBuilder pepBuilder = sut.with(buttonMock).onDenyDo(x -> {});
        pepBuilder.and();

        // WHEN + THEN
        assertThatThrownBy(pepBuilder::and).isInstanceOf(AccessDeniedException.class)
                .hasMessage("Builder has already been build. The builder can only be used once.");
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
        var fluxMock   = getFluxMock();
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);

        // Methods on SUT
        sut.with(buttonMock).onDenyDo(x -> {}).build();

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
        var fluxMock   = getFluxMock();
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);

        // Methods on SUT
        sut.with(buttonMock).onDenyDo(x -> {}).build();

        // WHEN
        sut.unregisterPep(0);

        // THEN
        verify(disposableMock, times(0)).dispose();
    }

    @Test
    void when_decisionOccurs_then_ConstraintsOfDecisionsAreEnforcedWithCorrectParameters() {
        // GIVEN
        var buttonMock = getButtonMockWithUI();
        var fluxMock   = getFluxMock();
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);

        var iad = new IdentifiableAuthorizationDecision("0", AuthorizationDecision.PERMIT); // 0 = id of button

        @SuppressWarnings("unchecked")
        var monoMock = (Mono<AuthorizationDecision>) mock(Mono.class);
        when(vaadinConstraintEnforcementService.enforceConstraintsOfDecision(any(AuthorizationDecision.class),
                any(UI.class), any(VaadinPep.class))).thenReturn(monoMock);

        // Methods on SUT
        sut.subject("subject").with(buttonMock).onDenyDo(x -> {}).build();

        // WHEN
        subscribeConsumer.accept(iad);

        // THEN
        ArgumentCaptor<AuthorizationDecision> authorizationDecisionArgument = ArgumentCaptor
                .forClass(AuthorizationDecision.class);
        ArgumentCaptor<UI>                    uiArgument                    = ArgumentCaptor.forClass(UI.class);
        assertThat(buttonMock.getUI()).isPresent();
        verify(vaadinConstraintEnforcementService).enforceConstraintsOfDecision(authorizationDecisionArgument.capture(),
                uiArgument.capture(), any(VaadinPep.class));
        assertThat(uiArgument.getValue()).isEqualTo(buttonMock.getUI().get());
        assertThat(authorizationDecisionArgument.getValue().decision()).isEqualTo(Decision.PERMIT);
    }

    @Test
    void when_decisionOccursWithoutID_then_ConstraintsOfDecisionsAreNotEnforced() {
        // GIVEN
        var buttonMock = getButtonMockWithUI();
        var fluxMock   = getFluxMock();
        when(pdpMock.decide(any(MultiAuthorizationSubscription.class))).thenReturn(fluxMock);

        var iad = new IdentifiableAuthorizationDecision("non-matching-id", AuthorizationDecision.PERMIT);

        @SuppressWarnings("unchecked")
        var monoMock = (Mono<AuthorizationDecision>) mock(Mono.class);
        when(vaadinConstraintEnforcementService.enforceConstraintsOfDecision(any(AuthorizationDecision.class),
                any(UI.class), any(VaadinPep.class))).thenReturn(monoMock);

        // Methods on SUT
        sut.with(buttonMock).onDenyDo(x -> {}).build();

        // WHEN
        subscribeConsumer.accept(iad);

        // THEN
        assertThat(buttonMock.getUI()).isPresent();
        verify(vaadinConstraintEnforcementService, times(0)).enforceConstraintsOfDecision(any(), any(),
                any(VaadinPep.class));
    }

    @Test
    void when_buildUIisNotPresent_then_AccessDeniedExceptionIsThrown() {
        // GIVEN

        Component    component  = mock(Component.class);
        @SuppressWarnings("unchecked")
        Optional<UI> optionalUI = (Optional<UI>) mock(Optional.class);
        when(component.isAttached()).thenReturn(true);
        when(component.getUI()).thenReturn(optionalUI);
        when(optionalUI.isPresent()).thenReturn(false);

        // WHEN + THEN
        assertThatThrownBy(() -> sut.build(component)).isInstanceOf(AccessDeniedException.class);
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
        Button       button = mock(Button.class);
        UI           ui     = mock(UI.class);
        Optional<UI> o      = Optional.of(ui);
        when(button.isAttached()).thenReturn(true);
        when(button.getUI()).thenReturn(o);
        return button;
    }
}
