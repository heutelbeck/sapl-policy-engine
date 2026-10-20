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
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.security.access.AccessDeniedException;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.Command;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import io.sapl.vaadin.base.SecurityHelper;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@DisplayName("Vaadin PEP single PEP builder")
class VaadinPepSinglePepBuilderTests {

    private static MockedStatic<SecurityHelper> securityHelperMock;
    ComponentEventListener<DetachEvent>         listener;

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

    @Test
    void when_VaadinSinglePepBuilderSubjectIsCalled_then_internalSubjectIsSet() {
        // GIVEN
        Component                                           component              = mock(Component.class);
        @SuppressWarnings("unchecked")
        VaadinPep.VaadinSinglePepBuilder<Object, Component> vaadinSinglePepBuilder = (VaadinPep.VaadinSinglePepBuilder<Object, Component>) mock(
                VaadinPep.VaadinSinglePepBuilder.class, withSettings()
                        // useConstructor() -> calls actual constructor which will create a VaadinPep
                        // object
                        .useConstructor(mock(ReactivePolicyDecisionPoint.class),
                                mock(VaadinConstraintEnforcementService.class), component)
                        // CALLS_REAL_METHODS needed for the onDecisionDo() call, to fill
                        // decisionListenerList
                        .defaultAnswer(CALLS_REAL_METHODS));
        // WHEN
        vaadinSinglePepBuilder.subject("subject");

        // THEN
        assertThat(vaadinSinglePepBuilder.vaadinPep.getAuthorizationSubscription().subject())
                .isEqualTo(Value.of("subject"));
    }

    @Test
    void when_VaadinSinglePepBuilderEnvironmentIsCalled_then_internalEnvironmentIsSet() {
        // GIVEN
        Component                                           component              = mock(Component.class);
        @SuppressWarnings("unchecked")
        VaadinPep.VaadinSinglePepBuilder<Object, Component> vaadinSinglePepBuilder = (VaadinPep.VaadinSinglePepBuilder<Object, Component>) mock(
                VaadinPep.VaadinSinglePepBuilder.class, withSettings()
                        // useConstructor() -> calls actual constructor which will create a VaadinPep
                        // object
                        .useConstructor(mock(ReactivePolicyDecisionPoint.class),
                                mock(VaadinConstraintEnforcementService.class), component)
                        // CALLS_REAL_METHODS needed for the onDecisionDo() call, to fill
                        // decisionListenerList
                        .defaultAnswer(CALLS_REAL_METHODS));
        // WHEN
        vaadinSinglePepBuilder.environment("environment");

        // THEN
        assertThat(vaadinSinglePepBuilder.vaadinPep.getAuthorizationSubscription().environment())
                .isEqualTo(Value.of("environment"));
    }

    @Test
    @SuppressWarnings("unchecked") // Suppress here because not allowed at (Consumer<? super
                                   // AuthorizationDecision>)any(Consumer.class)
    void when_SubscriptionIsStartedWithConstraints_then_EnforceConstraintsOfDecision() {
        // GIVEN
        Component  component  = getComponentMockWithUI(true);
        Disposable disposable = mock(Disposable.class);
        when(disposable.isDisposed()).thenReturn(false);

        Flux<AuthorizationDecision> flux = createFluxWithAuthorizationDescription();
        ReactivePolicyDecisionPoint pdp  = mock(ReactivePolicyDecisionPoint.class);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(flux);
        VaadinConstraintEnforcementService nextVaadinConstraintEnforcementServiceMock = mock(
                VaadinConstraintEnforcementService.class);
        when(nextVaadinConstraintEnforcementServiceMock.enforceConstraintsOfDecision(any(), any(), any()))
                .thenReturn(Mono.just(AuthorizationDecision.PERMIT));

        VaadinPep.VaadinSinglePepBuilder<Object, Component> vaadinSinglePepBuilder = (VaadinPep.VaadinSinglePepBuilder<Object, Component>) mock(
                VaadinPep.VaadinSinglePepBuilder.class, withSettings()
                        // useConstructor() -> calls actual constructor which will create a VaadinPep
                        // object
                        .useConstructor(pdp, nextVaadinConstraintEnforcementServiceMock, component)
                        .defaultAnswer(CALLS_REAL_METHODS));
        vaadinSinglePepBuilder.onDecisionDo(decision -> {});

        // WHEN
        vaadinSinglePepBuilder.build();

        // THEN
        verify(nextVaadinConstraintEnforcementServiceMock, times(1)).enforceConstraintsOfDecision(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked") // because of mocks
    void when_buildIsCalledWithoutAttachedComponentAndAttachListenerIsCalled_then_subscriptionIsStarted() {
        // GIVEN
        Component component = getComponentMockWithUI(false);

        Flux<AuthorizationDecision> flux = createFluxWithAuthorizationDescription();
        ReactivePolicyDecisionPoint pdp  = mock(ReactivePolicyDecisionPoint.class);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(flux);
        VaadinConstraintEnforcementService nextVaadinConstraintEnforcementServiceMock = mock(
                VaadinConstraintEnforcementService.class);
        when(nextVaadinConstraintEnforcementServiceMock.enforceConstraintsOfDecision(any(), any(), any()))
                .thenReturn(Mono.just(AuthorizationDecision.PERMIT));
        AttachEvent                                         eventMock              = mock(AttachEvent.class);
        VaadinPep.VaadinSinglePepBuilder<Object, Component> vaadinSinglePepBuilder = (VaadinPep.VaadinSinglePepBuilder<Object, Component>) mock(
                VaadinPep.VaadinSinglePepBuilder.class, withSettings()
                        // useConstructor() -> calls actual constructor which will create a VaadinPep
                        // object
                        .useConstructor(pdp, nextVaadinConstraintEnforcementServiceMock, component)
                        .defaultAnswer(CALLS_REAL_METHODS));
        vaadinSinglePepBuilder.onDecisionDo(decision -> {});
        doAnswer(invocation -> {
            invocation.getArgument(0, ComponentEventListener.class).onComponentEvent(eventMock);
            return null;
        }).when(component).addAttachListener(any());

        // WHEN
        vaadinSinglePepBuilder.build();

        // THEN
        verify(pdp).decide(any(AuthorizationSubscription.class));
        verify(nextVaadinConstraintEnforcementServiceMock, times(1)).enforceConstraintsOfDecision(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked") // because of mocks
    void when_buildIsCalledWithoutAttachedComponentAndAttachListenerIsCalledWithoutOptionalUI_then_AccessDeniedException() {
        // GIVEN
        Component component = mock(Component.class);

        when(component.isAttached()).thenReturn(false);

        ReactivePolicyDecisionPoint                         pdp                                        = mock(
                ReactivePolicyDecisionPoint.class);
        VaadinConstraintEnforcementService                  nextVaadinConstraintEnforcementServiceMock = mock(
                VaadinConstraintEnforcementService.class);
        AttachEvent                                         eventMock                                  = mock(
                AttachEvent.class);
        VaadinPep.VaadinSinglePepBuilder<Object, Component> vaadinSinglePepBuilder                     = (VaadinPep.VaadinSinglePepBuilder<Object, Component>) mock(
                VaadinPep.VaadinSinglePepBuilder.class, withSettings()
                        // useConstructor() -> calls actual constructor which will create a VaadinPep
                        // object
                        .useConstructor(pdp, nextVaadinConstraintEnforcementServiceMock, component)
                        .defaultAnswer(CALLS_REAL_METHODS));
        vaadinSinglePepBuilder.onDecisionDo(decision -> {});
        doAnswer(invocation -> {
            invocation.getArgument(0, ComponentEventListener.class).onComponentEvent(eventMock);
            return null;
        }).when(component).addAttachListener(any());

        // WHEN + THEN
        assertThatThrownBy(vaadinSinglePepBuilder::build).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @SuppressWarnings("unchecked") // because of mocks
    void when_buildIsCalledDetachListenerIsCalled_then_stopSubscription() {
        // GIVEN
        Component component = mock(Component.class);
        UI        ui        = mock(UI.class);

        Optional<UI> optionalUI = Optional.of(ui);
        when(component.getUI()).thenReturn(optionalUI);
        when(component.isAttached()).thenReturn(true);
        Disposable disposable = mock(Disposable.class);
        when(disposable.isDisposed()).thenReturn(false);
        Flux<AuthorizationDecision> fluxmock       = mock(Flux.class);
        Flux<Object>                objectFluxmock = mock(Flux.class);
        when(objectFluxmock.subscribe(any(Consumer.class))).thenReturn(disposable);
        when(fluxmock.flatMap(any())).thenReturn(objectFluxmock);
        ReactivePolicyDecisionPoint pdp = mock(ReactivePolicyDecisionPoint.class);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(fluxmock);
        VaadinConstraintEnforcementService                  nextVaadinConstraintEnforcementServiceMock = mock(
                VaadinConstraintEnforcementService.class);
        DetachEvent                                         detachEventMock                            = mock(
                DetachEvent.class);
        VaadinPep.VaadinSinglePepBuilder<Object, Component> vaadinSinglePepBuilder                     = (VaadinPep.VaadinSinglePepBuilder<Object, Component>) mock(
                VaadinPep.VaadinSinglePepBuilder.class, withSettings()
                        // useConstructor() -> calls actual constructor which will create a VaadinPep
                        // object
                        .useConstructor(pdp, nextVaadinConstraintEnforcementServiceMock, component)
                        .defaultAnswer(CALLS_REAL_METHODS));
        vaadinSinglePepBuilder.onDecisionDo(decision -> {});

        doAnswer(invocation -> {
            listener = invocation.getArgument(0, ComponentEventListener.class);
            return null;
        }).when(component).addDetachListener(any());
        vaadinSinglePepBuilder.build();
        // WHEN
        listener.onComponentEvent(detachEventMock);

        // THEN
        verify(disposable).isDisposed();
    }

    @Test
    void when_buildIsCalledWithAttachedComponentAndWithoutOptionalUI_then_AccessDeniedException() {
        // GIVEN
        Component component = mock(Component.class);

        when(component.isAttached()).thenReturn(true);

        ReactivePolicyDecisionPoint                         pdp                                        = mock(
                ReactivePolicyDecisionPoint.class);
        VaadinConstraintEnforcementService                  nextVaadinConstraintEnforcementServiceMock = mock(
                VaadinConstraintEnforcementService.class);
        @SuppressWarnings("unchecked") // because of Mock
        VaadinPep.VaadinSinglePepBuilder<Object, Component> vaadinSinglePepBuilder                     = (VaadinPep.VaadinSinglePepBuilder<Object, Component>) mock(
                VaadinPep.VaadinSinglePepBuilder.class, withSettings()
                        // useConstructor() -> calls actual constructor which will create a VaadinPep
                        // object
                        .useConstructor(pdp, nextVaadinConstraintEnforcementServiceMock, component)
                        .defaultAnswer(CALLS_REAL_METHODS));
        vaadinSinglePepBuilder.onDecisionDo(decision -> {});

        // WHEN + THEN
        assertThatThrownBy(vaadinSinglePepBuilder::build).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @SuppressWarnings("unchecked") // Suppress here because not allowed at (Consumer<? super
                                   // AuthorizationDecision>)any(Consumer.class)
    void when_VaadinSinglePepBuilderBuildIsCalled_then_AccessDeniedExceptionIsRaised() {
        // GIVEN
        Component  component  = getComponentMockWithUI(true);
        Disposable disposable = mock(Disposable.class);
        when(disposable.isDisposed()).thenReturn(false);

        Flux<AuthorizationDecision> flux = getFluxMock(disposable);
        ReactivePolicyDecisionPoint pdp  = mock(ReactivePolicyDecisionPoint.class);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(flux);
        VaadinConstraintEnforcementService nextVaadinConstraintEnforcementServiceMock = mock(
                VaadinConstraintEnforcementService.class);

        VaadinPep.VaadinSinglePepBuilder<Object, Component> vaadinSinglePepBuilder = (VaadinPep.VaadinSinglePepBuilder<Object, Component>) mock(
                VaadinPep.VaadinSinglePepBuilder.class, withSettings()
                        // useConstructor() -> calls actual constructor which will create a VaadinPep
                        // object
                        .useConstructor(pdp, nextVaadinConstraintEnforcementServiceMock, component)
                        .defaultAnswer(CALLS_REAL_METHODS));
        vaadinSinglePepBuilder.onDecisionDo(decision -> {});

        // WHEN
        vaadinSinglePepBuilder.build();

        // THEN
        verify(flux).subscribe((Consumer<? super AuthorizationDecision>) any(Consumer.class));
    }

    @Test
    @SuppressWarnings("unchecked") // Suppress here because not allowed at (Consumer<? super
                                   // AuthorizationDecision>)any(Consumer.class)
    void when_VaadinSinglePepBuilderBuildIsCalledWithNotAttachedComponent_then_StartSubscriptionIsAddedAsAttachListener() {
        // GIVEN
        Component  component  = mock(Component.class);
        Disposable disposable = mock(Disposable.class);
        when(disposable.isDisposed()).thenReturn(false);

        Flux<AuthorizationDecision> flux = getFluxMock(disposable);
        ReactivePolicyDecisionPoint pdp  = mock(ReactivePolicyDecisionPoint.class);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(flux);

        VaadinPep.VaadinSinglePepBuilder<Object, Component> vaadinSinglePepBuilder = (VaadinPep.VaadinSinglePepBuilder<Object, Component>) mock(
                VaadinPep.VaadinSinglePepBuilder.class, withSettings()
                        // useConstructor() -> calls actual constructor which will create a VaadinPep
                        // object
                        .useConstructor(pdp, mock(VaadinConstraintEnforcementService.class), component)
                        .defaultAnswer(CALLS_REAL_METHODS));
        vaadinSinglePepBuilder.onDecisionDo(decision -> {});

        // WHEN
        vaadinSinglePepBuilder.build();

        // THEN
        verify(component).addAttachListener(any(ComponentEventListener.class));
    }

    @Test
    void when_VaadinSinglePepBuilderBuildIsCalledTwice_then_AccessDeniedExceptionIsRaised() {
        // GIVEN
        Component  component  = getComponentMockWithUI(true);
        Disposable disposable = mock(Disposable.class);
        when(disposable.isDisposed()).thenReturn(false);

        Flux<AuthorizationDecision> flux = getFluxMock(disposable);
        ReactivePolicyDecisionPoint pdp  = mock(ReactivePolicyDecisionPoint.class);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(flux);

        @SuppressWarnings("unchecked")
        VaadinPep.VaadinSinglePepBuilder<Object, Component> vaadinSinglePepBuilder = (VaadinPep.VaadinSinglePepBuilder<Object, Component>) mock(
                VaadinPep.VaadinSinglePepBuilder.class, withSettings()
                        // useConstructor() -> calls actual constructor which will create a VaadinPep
                        // object
                        .useConstructor(pdp, mock(VaadinConstraintEnforcementService.class), component)
                        .defaultAnswer(CALLS_REAL_METHODS));
        // dummy action to avoid error message because of missing DENY handler
        vaadinSinglePepBuilder.onDenyDo(x -> {});
        vaadinSinglePepBuilder.build();

        // THEN
        assertThatThrownBy(vaadinSinglePepBuilder::build).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void when_VaadinSinglePepBuilderBuildIsCalledAndNoDenyRulePresent_then_AccessDeniedExceptionIsRaised() {
        // GIVEN
        Component  component  = getComponentMockWithUI(true);
        Disposable disposable = mock(Disposable.class);
        when(disposable.isDisposed()).thenReturn(false);

        Flux<AuthorizationDecision> flux = getFluxMock(disposable);
        ReactivePolicyDecisionPoint pdp  = mock(ReactivePolicyDecisionPoint.class);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(flux);

        @SuppressWarnings("unchecked")
        VaadinPep.VaadinSinglePepBuilder<Object, Component> vaadinSinglePepBuilder = (VaadinPep.VaadinSinglePepBuilder<Object, Component>) mock(
                VaadinPep.VaadinSinglePepBuilder.class, withSettings()
                        // useConstructor() -> calls actual constructor which will create a VaadinPep
                        // object
                        .useConstructor(pdp, mock(VaadinConstraintEnforcementService.class), component)
                        .defaultAnswer(CALLS_REAL_METHODS));
        // WHEN + THEN
        assertThatThrownBy(vaadinSinglePepBuilder::build).isInstanceOf(AccessDeniedException.class);
    }

    Component getComponentMockWithUI(boolean isAttached) {
        Component component = mock(Component.class);
        UI        ui        = mock(UI.class);

        // Mock UI access() function to immediately call the lambda that is passed to it
        when(ui.access(any(Command.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Command.class).execute();
            return null;
        });
        Optional<UI> o = Optional.of(ui);
        when(component.isAttached()).thenReturn(isAttached);
        when(component.getUI()).thenReturn(o);
        return component;
    }

    Flux<AuthorizationDecision> getFluxMock(Disposable disposable) {
        @SuppressWarnings("unchecked")
        Flux<AuthorizationDecision> f = (Flux<AuthorizationDecision>) mock(Flux.class, invocation -> {
            if (Disposable.class.equals(invocation.getMethod().getReturnType())) {
                return disposable;
            }
            return invocation.getMock();
        });
        return f;
    }

    private Flux<AuthorizationDecision> createFluxWithAuthorizationDescription() {
        return Flux.just(AuthorizationDecision.PERMIT);
    }
}
