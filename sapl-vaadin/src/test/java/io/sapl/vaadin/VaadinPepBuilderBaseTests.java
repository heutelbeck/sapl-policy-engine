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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.Command;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.vaadin.base.SecurityHelper;

@DisplayName("Vaadin PEP builder base")
class VaadinPepBuilderBaseTests {

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

    /**
     * Mock class to check VaadinPepBuilderBase interface.
     */
    static class VaadinPepBuilderMock implements VaadinPep.VaadinPepBuilderBase<VaadinPepBuilderMock, Component> {
        BiConsumer<AuthorizationDecision, Component> lastBiConsumer;

        @Override
        public VaadinPepBuilderMock onDecisionDo(BiConsumer<AuthorizationDecision, Component> biConsumer) {
            this.lastBiConsumer = biConsumer;
            return self();
        }

        @Override
        public VaadinPepBuilderMock onPermitDo(BiConsumer<AuthorizationDecision, Component> biConsumer) {
            return onDecisionDo((authznDecision, component) -> {
                if (authznDecision.decision() == Decision.PERMIT) {
                    biConsumer.accept(authznDecision, component);
                }
            });
        }

        @Override
        public VaadinPepBuilderMock onDenyDo(BiConsumer<AuthorizationDecision, Component> biConsumer) {
            return onDecisionDo((authznDecision, component) -> {
                if (authznDecision.decision() == Decision.DENY) {
                    biConsumer.accept(authznDecision, component);
                }
            });
        }
    }

    /**
     * This test case checks the default implementation of
     * {@link VaadinPep.VaadinPepBuilderBase#onDecisionDo(Consumer)}. The function
     * onDecisionDo(Consumer) should internally call the onDecisionDo(BiConsumer)
     * (Which is implemented in the VaadinPepBuilderMock above). When the BiConsumer
     * is accepted, it should accept the Consumer. This behavior is checked.
     **/
    @Test
    void when_VaadinPepBuilderBaseOnDecisionDoConsumer_then_ConsumerAcceptedByBiConsumer() {
        // GIVEN
        VaadinPepBuilderMock            vaadinPepBuilderMock = new VaadinPepBuilderMock();
        @SuppressWarnings("unchecked")
        Consumer<AuthorizationDecision> consumer             = (Consumer<AuthorizationDecision>) mock(Consumer.class);
        var                             ad                   = AuthorizationDecision.PERMIT;

        // WHEN
        vaadinPepBuilderMock.onDecisionDo(consumer);
        vaadinPepBuilderMock.lastBiConsumer.accept(ad, mock(Component.class)); // Simulate Decision

        // THEN
        verify(consumer).accept(ad);
    }

    /**
     * This test case checks the default implementation of
     * {@link VaadinPep.VaadinPepBuilderBase#onPermitDo(Consumer)}. The function
     * onPermitDo(Consumer) should internally call the onDecisionDo(BiConsumer)
     * (Which is implemented in the VaadinPepBuilderMock above). When the BiConsumer
     * is accepted and the decision is PERMIT, it should accept the Consumer. This
     * behavior is checked.
     **/
    @Test
    void when_VaadinPepBuilderBaseOnPermitDoConsumerWithPermit_then_ConsumerAcceptedByBiConsumer() {
        // GIVEN
        VaadinPepBuilderMock vaadinPepBuilderMock = new VaadinPepBuilderMock();
        @SuppressWarnings("unchecked")
        Consumer<Component>  consumer             = (Consumer<Component>) mock(Consumer.class);
        var                  ad                   = AuthorizationDecision.PERMIT;
        Component            component            = mock(Component.class);

        // WHEN
        vaadinPepBuilderMock.onPermitDo(consumer);
        vaadinPepBuilderMock.lastBiConsumer.accept(ad, component); // Simulate Decision

        // THEN
        verify(consumer).accept(component);
    }

    /**
     * This test case checks the default implementation of
     * {@link VaadinPep.VaadinPepBuilderBase#onPermitDo(Consumer)}. The function
     * onPermitDo(Consumer) should internally call the onDecisionDo(BiConsumer)
     * (Which is implemented in the VaadinPepBuilderMock above). When the BiConsumer
     * is accepted and the decision is DENY, it should NOT accept the Consumer. This
     * behavior is checked.
     **/
    @Test
    void when_VaadinPepBuilderBaseOnPermitDoConsumerWithDeny_then_ConsumerNotAcceptedByBiConsumer() {
        // GIVEN
        VaadinPepBuilderMock vaadinPepBuilderMock = new VaadinPepBuilderMock();
        @SuppressWarnings("unchecked")
        Consumer<Component>  consumer             = (Consumer<Component>) mock(Consumer.class);
        var                  ad                   = AuthorizationDecision.DENY;

        // WHEN
        vaadinPepBuilderMock.onPermitDo(consumer);
        vaadinPepBuilderMock.lastBiConsumer.accept(ad, mock(Component.class)); // Simulate decision

        // THEN
        verify(consumer, times(0)).accept(any(Component.class)); // When DENY occurs, consumer should not have been
                                                                 // called
    }

    /**
     * This test case checks the default implementation of
     * {@link VaadinPep.VaadinPepBuilderBase#onPermitDo(Runnable)}. The function
     * onPermitDo(Runnable) should internally call the onDecisionDo(BiConsumer)
     * (Which is implemented in the VaadinPepBuilderMock above). When the BiConsumer
     * is accepted and the decision is PERMIT, it should run the Runnable. This
     * behavior is checked.
     **/
    @Test
    void when_VaadinPepBuilderBaseOnPermitDoRunnableWithPermit_then_RunnableCalledByBiConsumer() {
        // GIVEN
        VaadinPepBuilderMock vaadinPepBuilderMock = new VaadinPepBuilderMock();
        var                  ad                   = AuthorizationDecision.PERMIT;
        Runnable             runnable             = mock(Runnable.class);

        // WHEN
        vaadinPepBuilderMock.onPermitDo(runnable);
        vaadinPepBuilderMock.lastBiConsumer.accept(ad, mock(Component.class)); // Simulate decision

        // THEN
        verify(runnable).run(); // When PERMIT occurs, runnable should have been called
    }

    /**
     * This test case checks the default implementation of
     * {@link VaadinPep.VaadinPepBuilderBase#onPermitDo(Runnable)}. The function
     * onPermitDo(Runnable) should internally call the onDecisionDo(BiConsumer)
     * (Which is implemented in the VaadinPepBuilderMock above). When the BiConsumer
     * is accepted and the decision is DENY, it should NOT run the Runnable. This
     * behavior is checked.
     **/
    @Test
    void when_VaadinPepBuilderBaseOnPermitDoRunnableWithDeny_then_RunnableNotCalledByBiConsumer() {
        // GIVEN
        VaadinPepBuilderMock vaadinPepBuilderMock = new VaadinPepBuilderMock();
        var                  ad                   = AuthorizationDecision.DENY;
        Runnable             runnable             = mock(Runnable.class);

        // WHEN
        vaadinPepBuilderMock.onPermitDo(runnable);
        vaadinPepBuilderMock.lastBiConsumer.accept(ad, mock(Component.class)); // Simulate decision

        // THEN
        verify(runnable, times(0)).run(); // When DENY occurs, runnable should not have been called
    }

    /**
     * This test case checks the default implementation of
     * {@link VaadinPep.VaadinPepBuilderBase#onDenyDo(Consumer)}. The function
     * onDenyDo(Consumer) should internally call the onDecisionDo(BiConsumer) (Which
     * is implemented in the VaadinPepBuilderMock above). When the BiConsumer is
     * accepted and the decision is DENY, it should accept the Consumer. This
     * behavior is checked.
     **/
    @Test
    void when_VaadinPepBuilderBaseOnDenyDoConsumerWithDeny_then_ConsumerAcceptedByBiConsumer() {
        // GIVEN
        VaadinPepBuilderMock vaadinPepBuilderMock = new VaadinPepBuilderMock();
        @SuppressWarnings("unchecked")
        Consumer<Component>  consumer             = (Consumer<Component>) mock(Consumer.class);
        var                  ad                   = AuthorizationDecision.DENY;
        Component            component            = mock(Component.class);

        // WHEN
        vaadinPepBuilderMock.onDenyDo(consumer);
        vaadinPepBuilderMock.lastBiConsumer.accept(ad, component); // Simulate Decision

        // THEN
        verify(consumer).accept(component); // When PERMIT occurs, consumer should be called
    }

    /**
     * This test case checks the default implementation of
     * {@link VaadinPep.VaadinPepBuilderBase#onDenyDo(Consumer)}. The function
     * onDenyDo(Consumer) should internally call the onDecisionDo(BiConsumer) (Which
     * is implemented in the VaadinPepBuilderMock above). When the BiConsumer is
     * accepted and the decision is PERMIT, it should NOT accept the Consumer. This
     * behavior is checked.
     **/
    @Test
    void when_VaadinPepBuilderBaseOnDenyDoConsumerWithPermit_then_ConsumerNotAcceptedByBiConsumer() {
        // GIVEN
        VaadinPepBuilderMock vaadinPepBuilderMock = new VaadinPepBuilderMock();
        @SuppressWarnings("unchecked")
        Consumer<Component>  consumer             = (Consumer<Component>) mock(Consumer.class);
        var                  ad                   = AuthorizationDecision.PERMIT;

        // WHEN
        vaadinPepBuilderMock.onDenyDo(consumer);
        vaadinPepBuilderMock.lastBiConsumer.accept(ad, mock(Component.class)); // Simulate decision

        // THEN
        verify(consumer, times(0)).accept(any(Component.class)); // When DENY occurs, consumer should not have been
                                                                 // called
    }

    /**
     * This test case checks the default implementation of
     * {@link VaadinPep.VaadinPepBuilderBase#onDenyDo(Runnable)}. The function
     * onDenyDo(Runnable) should internally call the onDecisionDo(BiConsumer) (Which
     * is implemented in the VaadinPepBuilderMock above). When the BiConsumer is
     * accepted and the decision is DENY, it should run the Runnable. This behavior
     * is checked.
     **/
    @Test
    void when_VaadinPepBuilderBaseOnDenyDoRunnableWithDeny_then_RunnableCalledByBiConsumer() {
        // GIVEN
        VaadinPepBuilderMock vaadinPepBuilderMock = new VaadinPepBuilderMock();
        var                  ad                   = AuthorizationDecision.DENY;
        Runnable             runnable             = mock(Runnable.class);

        // WHEN
        vaadinPepBuilderMock.onDenyDo(runnable);
        vaadinPepBuilderMock.lastBiConsumer.accept(ad, mock(Component.class)); // Simulate decision

        // THEN
        verify(runnable).run(); // When DENY occurs, runnable should have been called
    }

    /**
     * This test case checks the default implementation of
     * {@link VaadinPep.VaadinPepBuilderBase#onDenyDo(Runnable)}. The function
     * onDenyDo(Runnable) should internally call the onDecisionDo(BiConsumer) (Which
     * is implemented in the VaadinPepBuilderMock above). When the BiConsumer is
     * accepted and the decision is PERMIT, it should NOT run the Runnable. This
     * behavior is checked.
     **/
    @Test
    void when_VaadinPepBuilderBaseOnDenyDoRunnableWithPermit_then_RunnableNotCalledByBiConsumer() {
        // GIVEN
        VaadinPepBuilderMock vaadinPepBuilderMock = new VaadinPepBuilderMock();
        var                  ad                   = AuthorizationDecision.PERMIT;
        Runnable             runnable             = mock(Runnable.class);

        // WHEN
        vaadinPepBuilderMock.onDenyDo(runnable);
        vaadinPepBuilderMock.lastBiConsumer.accept(ad, mock(Component.class)); // Simulate decision

        // THEN
        verify(runnable, times(0)).run(); // When PERMIT occurs, runnable should not have been called
    }

    @Test
    void when_VaadinPepBuilderBaseOnDecisionVisibleOrHiddenWithPermit_then_ComponentIsVisible() {
        // GIVEN
        VaadinPepBuilderMock vaadinPepBuilderMock = new VaadinPepBuilderMock();
        var                  ad                   = AuthorizationDecision.PERMIT;
        Component            component            = getComponentMockWithUI();

        // WHEN
        vaadinPepBuilderMock.onDecisionVisibleOrHidden();
        vaadinPepBuilderMock.lastBiConsumer.accept(ad, component); // Simulate decision

        // THEN
        verify(component).setVisible(true);
    }

    @Test
    void when_VaadinPepBuilderBaseOnDecisionVisibleOrHiddenWithDeny_then_ComponentIsNotVisible() {
        // GIVEN
        VaadinPepBuilderMock vaadinPepBuilderMock = new VaadinPepBuilderMock();
        var                  ad                   = AuthorizationDecision.DENY;
        Component            component            = getComponentMockWithUI();

        // WHEN
        vaadinPepBuilderMock.onDecisionVisibleOrHidden();
        vaadinPepBuilderMock.lastBiConsumer.accept(ad, component); // Simulate decision

        // THEN
        verify(component).setVisible(false);
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
