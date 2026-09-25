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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.Optional;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.Command;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import io.sapl.vaadin.base.SecurityHelper;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@DisplayName("Vaadin PEP")
class VaadinPepTests {

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
    void when_VaadinPepHandleDecisionIsCalled_then_BiConsumerIsCalled() {
        // GIVEN
        Component                                     component        = mock(Component.class);
        @SuppressWarnings("unchecked")
        VaadinPep.VaadinPepBuilder<Object, Component> vaadinPepBuilder = (VaadinPep.VaadinPepBuilder<Object, Component>) mock(
                VaadinPep.VaadinPepBuilder.class, withSettings()
                        // useConstructor() -> calls actual constructor which will create a VaadinPep
                        // object
                        .useConstructor(mock(ReactivePolicyDecisionPoint.class),
                                mock(VaadinConstraintEnforcementService.class), component)
                        // CALLS_REAL_METHODS needed for the onDecisionDo() call, to fill
                        // decisionListenerList
                        .defaultAnswer(CALLS_REAL_METHODS));
        @SuppressWarnings("unchecked")
        BiConsumer<AuthorizationDecision, Component>  biConsumer       = (BiConsumer<AuthorizationDecision, Component>) mock(
                BiConsumer.class);
        vaadinPepBuilder.onDecisionDo(biConsumer);
        AuthorizationDecision ad = AuthorizationDecision.PERMIT;

        // WHEN
        vaadinPepBuilder.vaadinPep.handleDecision(ad);

        // THEN
        verify(biConsumer).accept(ad, component);
    }

    @Test
    void when_VaadinPepUnenforceIsCalled_then_DisposableDisposeIsCalled() {
        // GIVEN
        Component  component  = getComponentMockWithUI();
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
                        // CALLS_REAL_METHODS needed for the onDecisionDo() call, to fill
                        // decisionListenerList
                        .defaultAnswer(CALLS_REAL_METHODS));
        @SuppressWarnings("unchecked")
        BiConsumer<AuthorizationDecision, Component>        biConsumer             = (BiConsumer<AuthorizationDecision, Component>) mock(
                BiConsumer.class);
        vaadinSinglePepBuilder.onDecisionDo(biConsumer); // Add a consumer because build() doesn't start the
                                                         // subscription if the list is empty
        vaadinSinglePepBuilder.build();

        // WHEN
        vaadinSinglePepBuilder.vaadinPep.stopSubscription();

        // THEN
        verify(disposable).dispose();
    }

    @Test
    void when_VaadinPepUnenforceIsCalledWithDisposedDisposable_then_DisposableDisposeIsNotCalled() {
        // GIVEN
        Component  component  = getComponentMockWithUI();
        Disposable disposable = mock(Disposable.class);
        when(disposable.isDisposed()).thenReturn(true);

        Flux<AuthorizationDecision> flux = getFluxMock(disposable);
        ReactivePolicyDecisionPoint pdp  = mock(ReactivePolicyDecisionPoint.class);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(flux);

        @SuppressWarnings("unchecked")
        VaadinPep.VaadinSinglePepBuilder<Object, Component> vaadinSinglePepBuilder = (VaadinPep.VaadinSinglePepBuilder<Object, Component>) mock(
                VaadinPep.VaadinSinglePepBuilder.class, withSettings()
                        // useConstructor() -> calls actual constructor which will create a VaadinPep
                        // object
                        .useConstructor(pdp, mock(VaadinConstraintEnforcementService.class), component)
                        // CALLS_REAL_METHODS needed for the onDecisionDo() call, to fill
                        // decisionListenerList
                        .defaultAnswer(CALLS_REAL_METHODS));
        @SuppressWarnings("unchecked")
        BiConsumer<AuthorizationDecision, Component>        biConsumer             = (BiConsumer<AuthorizationDecision, Component>) mock(
                BiConsumer.class);
        vaadinSinglePepBuilder.onDecisionDo(biConsumer); // Add a consumer because build() doesn't start the
                                                         // subscription if the list is empty
        vaadinSinglePepBuilder.build();

        // WHEN
        vaadinSinglePepBuilder.vaadinPep.stopSubscription();

        // THEN
        verify(disposable, times(0)).dispose();
    }

    @Test
    void when_VaadinPepUnenforceIsCalledWithoutDisposable_then_DisposableDisposeIsNotCalled() {
        // GIVEN
        Component  component  = getComponentMockWithUI();
        Disposable disposable = mock(Disposable.class);

        Flux<AuthorizationDecision> flux = getFluxMock(null);
        ReactivePolicyDecisionPoint pdp  = mock(ReactivePolicyDecisionPoint.class);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(flux);

        @SuppressWarnings("unchecked")
        VaadinPep.VaadinSinglePepBuilder<Object, Component> vaadinSinglePepBuilder = (VaadinPep.VaadinSinglePepBuilder<Object, Component>) mock(
                VaadinPep.VaadinSinglePepBuilder.class, withSettings()
                        // useConstructor() -> calls actual constructor which will create a VaadinPep
                        // object
                        .useConstructor(pdp, mock(VaadinConstraintEnforcementService.class), component)
                        // CALLS_REAL_METHODS needed for the onDecisionDo() call, to fill
                        // decisionListenerList
                        .defaultAnswer(CALLS_REAL_METHODS));
        @SuppressWarnings("unchecked")
        BiConsumer<AuthorizationDecision, Component>        biConsumer             = (BiConsumer<AuthorizationDecision, Component>) mock(
                BiConsumer.class);
        vaadinSinglePepBuilder.onDecisionDo(biConsumer); // Add a consumer because build() doesn't start the
                                                         // subscription if the list is empty
        vaadinSinglePepBuilder.build();

        // WHEN
        vaadinSinglePepBuilder.vaadinPep.stopSubscription();

        // THEN
        verify(disposable, times(0)).dispose();
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
}
