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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.server.Command;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.vaadin.base.SecurityHelper;

@DisplayName("Vaadin PEP enforce has enabled")
class VaadinPepEnforceHasEnabledTests {

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
     * Mock class to check EnforceHasEnabled interface.
     */
    static class VaadinPepBuilderMock implements VaadinPep.EnforceHasEnabled<VaadinPepBuilderMock, Component> {
        BiConsumer<AuthorizationDecision, Component> lastBiConsumer;

        @Override
        public VaadinPepBuilderMock onDecisionDo(BiConsumer<AuthorizationDecision, Component> biConsumer) {
            this.lastBiConsumer = biConsumer;
            return self();
        }

        @Override
        public VaadinPepBuilderMock onPermitDo(BiConsumer<AuthorizationDecision, Component> biConsumer) {
            return onDecisionDo(biConsumer);
        }

        @Override
        public VaadinPepBuilderMock onDenyDo(BiConsumer<AuthorizationDecision, Component> biConsumer) {
            return onDecisionDo(biConsumer);
        }
    }

    @Test
    void when_EnforceHasEnabledOnDecisionEnableOrDisableWithPermit_then_ComponentIsEnabled() {
        // GIVEN
        VaadinPepBuilderMock vaadinPepBuilderMock = new VaadinPepBuilderMock();
        var                  ad                   = AuthorizationDecision.PERMIT;
        Button               button               = getButtonMockWithUI();

        // WHEN
        vaadinPepBuilderMock.onDecisionEnableOrDisable();
        vaadinPepBuilderMock.lastBiConsumer.accept(ad, button); // Simulate decision

        // THEN
        verify(button).setEnabled(true);
    }

    @Test
    void when_EnforceHasEnabledOnDecisionEnableOrDisableWithDeny_then_ComponentIsDisabled() {
        // GIVEN
        VaadinPepBuilderMock vaadinPepBuilderMock = new VaadinPepBuilderMock();
        var                  ad                   = AuthorizationDecision.DENY;
        Button               button               = getButtonMockWithUI();

        // WHEN
        vaadinPepBuilderMock.onDecisionEnableOrDisable();
        vaadinPepBuilderMock.lastBiConsumer.accept(ad, button); // Simulate decision

        // THEN
        verify(button).setEnabled(false);
    }

    Button getButtonMockWithUI() {
        Button button = mock(Button.class);
        UI     ui     = mock(UI.class);

        // Mock UI access() function to immediately call the lambda that is passed to it
        when(ui.access(any(Command.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Command.class).execute();
            return null;
        });
        Optional<UI> o = Optional.of(ui);
        when(button.isAttached()).thenReturn(true);
        when(button.getUI()).thenReturn(o);
        return button;
    }
}
