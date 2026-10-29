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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.server.Command;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import io.sapl.spring.pep.constraints.ConstraintHandler;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.ScopedConstraintHandler;
import io.sapl.vaadin.base.SecurityHelper;
import io.sapl.vaadin.constraint.VaadinFunctionConstraintHandlerProvider;
import io.sapl.vaadin.constraint.VaadinSignals;

@DisplayName("Vaadin PEP builder")
class VaadinPepBuilderTests {

    private static MockedStatic<SecurityHelper> securityHelperMock;

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
    void when_VaadinPepBuilderAddVaadinFunctionConstraintHandlerProvidersIsCalled_then_VaadinFunctionConstraintHandlerListIsExpanded() {
        // GIVEN
        @SuppressWarnings("unchecked")
        VaadinPep.VaadinPepBuilder<Object, Component> vaadinPepBuilder                        = (VaadinPep.VaadinPepBuilder<Object, Component>) mock(
                VaadinPep.VaadinPepBuilder.class, withSettings()
                        // useConstructor() -> calls actual constructor which will create a VaadinPep
                        // object
                        .useConstructor(mock(ReactivePolicyDecisionPoint.class),
                                mock(VaadinConstraintEnforcementService.class), mock(Component.class))
                        // CALLS_REAL_METHODS needed for the onDecisionDo() call, to fill
                        // decisionListenerList
                        .defaultAnswer(CALLS_REAL_METHODS));
        VaadinFunctionConstraintHandlerProvider       vaadinFunctionConstraintHandlerProvider = mock(
                VaadinFunctionConstraintHandlerProvider.class);
        // WHEN
        vaadinPepBuilder.addVaadinFunctionConstraintHandlerProvider(vaadinFunctionConstraintHandlerProvider);

        // THEN
        assertThat(vaadinPepBuilder.vaadinPep.getLocalVaadinFunctionProvider()).hasSize(1);
        if (vaadinPepBuilder.vaadinPep.getLocalVaadinFunctionProvider().size() > 0)
            assertThat(vaadinPepBuilder.vaadinPep.getLocalVaadinFunctionProvider().get(0))
                    .isEqualTo(vaadinFunctionConstraintHandlerProvider);
    }

    @Test
    void when_VaadinPepBuilderAddConstraintHandlerProviderIsCalled_then_ConstraintHandlerProvidersListIsExpanded() {
        // GIVEN
        @SuppressWarnings("unchecked")
        VaadinPep.VaadinPepBuilder<Object, Component> vaadinPepBuilder          = (VaadinPep.VaadinPepBuilder<Object, Component>) mock(
                VaadinPep.VaadinPepBuilder.class, withSettings()
                        // useConstructor() -> calls actual constructor which will create a VaadinPep
                        // object
                        .useConstructor(mock(ReactivePolicyDecisionPoint.class),
                                mock(VaadinConstraintEnforcementService.class), mock(Component.class))
                        // CALLS_REAL_METHODS needed for the onDecisionDo() call, to fill
                        // decisionListenerList
                        .defaultAnswer(CALLS_REAL_METHODS));
        ConstraintHandlerProvider                     constraintHandlerProvider = mock(ConstraintHandlerProvider.class);

        // WHEN
        vaadinPepBuilder.addConstraintHandlerProvider(constraintHandlerProvider);

        // THEN
        assertThat(vaadinPepBuilder.vaadinPep.getLocalConstraintHandlerProviders())
                .containsExactly(constraintHandlerProvider);
    }

    @Test
    void when_VaadinPepBuilderAddConstraintHandlerIsCalled_then_ConstraintHandlerProvidersListIsExpanded() {
        // GIVEN
        @SuppressWarnings("unchecked")
        VaadinPep.VaadinPepBuilder<Object, Component> vaadinPepBuilder = (VaadinPep.VaadinPepBuilder<Object, Component>) mock(
                VaadinPep.VaadinPepBuilder.class, withSettings()
                        // useConstructor() -> calls actual constructor which will create a VaadinPep
                        // object
                        .useConstructor(mock(ReactivePolicyDecisionPoint.class),
                                mock(VaadinConstraintEnforcementService.class), mock(Component.class))
                        // CALLS_REAL_METHODS needed for the onDecisionDo() call, to fill
                        // decisionListenerList
                        .defaultAnswer(CALLS_REAL_METHODS));
        @SuppressWarnings("unchecked")
        Predicate<Value>                              predicate        = (Predicate<Value>) mock(Predicate.class);
        @SuppressWarnings("unchecked")
        Function<Value, Consumer<UI>>                 getHandler       = (Function<Value, Consumer<UI>>) mock(
                Function.class);
        @SuppressWarnings("unchecked")
        Consumer<UI>                                  consumer         = (Consumer<UI>) mock(Consumer.class);
        Value                                         constraint       = Value.of("test");
        UI                                            ui               = mock(UI.class);
        when(getHandler.apply(constraint)).thenReturn(consumer);

        // WHEN
        vaadinPepBuilder.addConstraintHandler(predicate, getHandler);

        // THEN
        List<ConstraintHandlerProvider> localProviders = vaadinPepBuilder.vaadinPep
                .getLocalConstraintHandlerProviders();
        assertThat(localProviders).hasSize(1);

        when(predicate.test(constraint)).thenReturn(Boolean.TRUE);
        List<ScopedConstraintHandler> handlers = localProviders.get(0).getConstraintHandlers(constraint,
                VaadinSignals.SUPPORTED_SIGNALS);
        assertThat(handlers).hasSize(1).first().satisfies(
                scoped -> assertThat(scoped.signalType()).isEqualTo(VaadinSignals.UI_SIGNAL),
                scoped -> assertThat(scoped.handler()).isInstanceOf(ConstraintHandler.Consumer.class));

        @SuppressWarnings("unchecked")
        ConstraintHandler.Consumer<UI> handler = (ConstraintHandler.Consumer<UI>) handlers.get(0).handler();
        handler.accept(ui);
        verify(consumer).accept(ui);

        when(predicate.test(constraint)).thenReturn(Boolean.FALSE);
        assertThat(localProviders.get(0).getConstraintHandlers(constraint, VaadinSignals.SUPPORTED_SIGNALS)).isEmpty();
    }

    @Test
    void when_VaadinPepBuilderOnDenyNotifyIsCalledAndDenyOccurs_then_NotificationShowIsCalled() {
        // GIVEN
        Component                                     component           = getComponentMockWithUI();
        @SuppressWarnings("unchecked")
        VaadinPep.VaadinPepBuilder<Object, Component> vaadinPepBuilder    = (VaadinPep.VaadinPepBuilder<Object, Component>) mock(
                VaadinPep.VaadinPepBuilder.class, withSettings()
                        // useConstructor() -> calls actual constructor which will create a VaadinPep
                        // object
                        .useConstructor(mock(ReactivePolicyDecisionPoint.class),
                                mock(VaadinConstraintEnforcementService.class), component)
                        .defaultAnswer(CALLS_REAL_METHODS));
        String                                        notificationMessage = "message";
        Notification                                  notification        = mock(Notification.class);
        MockedStatic<Notification>                    notificationMock    = mockStatic(Notification.class);                      // Static
                                                                                                                                 // mock
                                                                                                                                 // for
                                                                                                                                 // "Notification.show()"
        notificationMock.when(() -> Notification.show(notificationMessage)).thenReturn(notification); // On show()
                                                                                                      // return a
                                                                                                      // notification
                                                                                                      // mock

        // WHEN
        vaadinPepBuilder.onDenyNotify(notificationMessage);
        vaadinPepBuilder.vaadinPep.handleDecision(AuthorizationDecision.DENY); // Simulate decision

        // THEN
        notificationMock.verify(() -> Notification.show(notificationMessage));

        notificationMock.close();
    }

    @Test
    void when_VaadinPepBuilderOnDenyNotifyIsCalledAndPermitOccurs_then_NotificationShowIsNotCalled() {
        // GIVEN
        Component                                     component           = getComponentMockWithUI();
        @SuppressWarnings("unchecked")
        VaadinPep.VaadinPepBuilder<Object, Component> vaadinPepBuilder    = (VaadinPep.VaadinPepBuilder<Object, Component>) mock(
                VaadinPep.VaadinPepBuilder.class, withSettings()
                        // useConstructor() -> calls actual constructor which will create a VaadinPep
                        // object
                        .useConstructor(mock(ReactivePolicyDecisionPoint.class),
                                mock(VaadinConstraintEnforcementService.class), component)
                        .defaultAnswer(CALLS_REAL_METHODS));
        String                                        notificationMessage = "message";
        Notification                                  notification        = mock(Notification.class);
        MockedStatic<Notification>                    notificationMock    = mockStatic(Notification.class);                      // Static
                                                                                                                                 // mock
                                                                                                                                 // for
                                                                                                                                 // "Notification.show()"
        notificationMock.when(() -> Notification.show(notificationMessage)).thenReturn(notification); // On show()
                                                                                                      // return a
                                                                                                      // notification
                                                                                                      // mock

        // WHEN
        vaadinPepBuilder.onDenyNotify(notificationMessage);
        vaadinPepBuilder.vaadinPep.handleDecision(AuthorizationDecision.PERMIT); // Simulate decision

        // THEN
        notificationMock.verify(() -> Notification.show(any(String.class)), times(0));

        notificationMock.close();
    }

    @Test
    void when_VaadinPepBuilderOnPermitNotifyIsCalledAndPermitOccurs_then_NotificationShowIsCalled() {
        // GIVEN
        Component                                     component           = getComponentMockWithUI();
        @SuppressWarnings("unchecked")
        VaadinPep.VaadinPepBuilder<Object, Component> vaadinPepBuilder    = (VaadinPep.VaadinPepBuilder<Object, Component>) mock(
                VaadinPep.VaadinPepBuilder.class, withSettings()
                        // useConstructor() -> calls actual constructor which will create a VaadinPep
                        // object
                        .useConstructor(mock(ReactivePolicyDecisionPoint.class),
                                mock(VaadinConstraintEnforcementService.class), component)
                        .defaultAnswer(CALLS_REAL_METHODS));
        String                                        notificationMessage = "message";
        Notification                                  notification        = mock(Notification.class);
        MockedStatic<Notification>                    notificationMock    = mockStatic(Notification.class);                      // Static
                                                                                                                                 // mock
                                                                                                                                 // for
                                                                                                                                 // "Notification.show()"
        notificationMock.when(() -> Notification.show(notificationMessage)).thenReturn(notification); // On show()
                                                                                                      // return a
                                                                                                      // notification
                                                                                                      // mock

        // WHEN
        vaadinPepBuilder.onPermitNotify(notificationMessage);
        vaadinPepBuilder.vaadinPep.handleDecision(AuthorizationDecision.PERMIT); // Simulate decision

        // THEN
        notificationMock.verify(() -> Notification.show(any(String.class)));

        notificationMock.close();
    }

    @Test
    void when_VaadinPepBuilderOnPermitNotifyIsCalledAndDenyOccurs_then_NotificationShowIsNotCalled() {
        // GIVEN
        Component                                     component           = getComponentMockWithUI();
        @SuppressWarnings("unchecked")
        VaadinPep.VaadinPepBuilder<Object, Component> vaadinPepBuilder    = (VaadinPep.VaadinPepBuilder<Object, Component>) mock(
                VaadinPep.VaadinPepBuilder.class, withSettings()
                        // useConstructor() -> calls actual constructor which will create a VaadinPep
                        // object
                        .useConstructor(mock(ReactivePolicyDecisionPoint.class),
                                mock(VaadinConstraintEnforcementService.class), component)
                        .defaultAnswer(CALLS_REAL_METHODS));
        String                                        notificationMessage = "message";
        Notification                                  notification        = mock(Notification.class);
        MockedStatic<Notification>                    notificationMock    = mockStatic(Notification.class);                      // Static
                                                                                                                                 // mock
                                                                                                                                 // for
                                                                                                                                 // "Notification.show()"
        notificationMock.when(() -> Notification.show(notificationMessage)).thenReturn(notification); // On show()
                                                                                                      // return a
                                                                                                      // notification
                                                                                                      // mock

        // WHEN
        vaadinPepBuilder.onPermitNotify(notificationMessage);
        vaadinPepBuilder.vaadinPep.handleDecision(AuthorizationDecision.DENY); // Simulate decision

        // THEN
        notificationMock.verify(() -> Notification.show(any(String.class)), times(0));

        notificationMock.close();
    }

    Component getComponentMockWithUI() {
        Component component = mock(Component.class);
        UI        ui        = mock(UI.class);

        // Mock UI access() function to immediately call the lambda that is passed to it
        when(ui.access(any(Command.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Command.class).execute();
            return null;
        });
        Optional<UI> o = Optional.of(ui);
        when(component.isAttached()).thenReturn(true);
        when(component.getUI()).thenReturn(o);
        return component;
    }
}
