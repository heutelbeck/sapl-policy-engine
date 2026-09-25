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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.server.Command;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.vaadin.base.SecurityHelper;

@DisplayName("Vaadin PEP enforce has text")
class VaadinPepEnforceHasTextTests {

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
     * Mock class to check EnforceHasText interface.
     */
    static class VaadinPepBuilderHasTextMock implements VaadinPep.EnforceHasText<VaadinPepBuilderHasTextMock, Button> {
        BiConsumer<AuthorizationDecision, Button> lastBiConsumer;

        @Override
        public VaadinPepBuilderHasTextMock onDecisionDo(BiConsumer<AuthorizationDecision, Button> biConsumer) {
            this.lastBiConsumer = biConsumer;
            return self();
        }

        @Override
        public VaadinPepBuilderHasTextMock onPermitDo(BiConsumer<AuthorizationDecision, Button> biConsumer) {
            return onDecisionDo((authznDecision, component) -> {
                if (authznDecision.decision() == Decision.PERMIT) {
                    biConsumer.accept(authznDecision, component);
                }
            });
        }

        @Override
        public VaadinPepBuilderHasTextMock onDenyDo(BiConsumer<AuthorizationDecision, Button> biConsumer) {
            return onDecisionDo((authznDecision, component) -> {
                if (authznDecision.decision() == Decision.DENY) {
                    biConsumer.accept(authznDecision, component);
                }
            });
        }
    }

    @Test
    void when_EnforceHasTextOnDecisionSetTextWithPermit_then_ComponentSetTextIsCalledWithPermitText() {
        // GIVEN
        VaadinPepBuilderHasTextMock vaadinPepBuilderHasTextMock = new VaadinPepBuilderHasTextMock();
        var                         ad                          = AuthorizationDecision.PERMIT;
        Button                      button                      = getButtonMockWithUI();
        String                      permitText                  = "permit";

        // WHEN
        vaadinPepBuilderHasTextMock.onDecisionSetText(permitText, "deny");
        vaadinPepBuilderHasTextMock.lastBiConsumer.accept(ad, button); // Simulate decision

        // THEN
        verify(button).setText(permitText);
    }

    @Test
    void when_EnforceHasTextOnDecisionSetTextWithDeny_then_ComponentSetTextIsCalledWithDenyText() {
        // GIVEN
        VaadinPepBuilderHasTextMock vaadinPepBuilderHasTextMock = new VaadinPepBuilderHasTextMock();
        var                         ad                          = AuthorizationDecision.DENY;
        Button                      button                      = getButtonMockWithUI();
        String                      denyText                    = "deny";

        // WHEN
        vaadinPepBuilderHasTextMock.onDecisionSetText("permit", denyText);
        vaadinPepBuilderHasTextMock.lastBiConsumer.accept(ad, button); // Simulate decision

        // THEN
        verify(button).setText(denyText);
    }

    @Test
    void when_EnforceHasTextOnPermitSetTextWithPermit_then_ComponentSetTextIsCalled() {
        // GIVEN
        VaadinPepBuilderHasTextMock vaadinPepBuilderHasTextMock = new VaadinPepBuilderHasTextMock();
        var                         ad                          = AuthorizationDecision.PERMIT;
        Button                      button                      = getButtonMockWithUI();
        String                      permitText                  = "permit";

        // WHEN
        vaadinPepBuilderHasTextMock.onPermitSetText(permitText);
        vaadinPepBuilderHasTextMock.lastBiConsumer.accept(ad, button); // Simulate decision

        // THEN
        verify(button).setText(permitText);
    }

    @Test
    void when_EnforceHasTextOnPermitSetTextWithDeny_then_ComponentSetTextIsNotCalled() {
        // GIVEN
        VaadinPepBuilderHasTextMock vaadinPepBuilderHasTextMock = new VaadinPepBuilderHasTextMock();
        var                         ad                          = AuthorizationDecision.DENY;
        Button                      button                      = getButtonMockWithUI();
        String                      permitText                  = "permit";

        // WHEN
        vaadinPepBuilderHasTextMock.onPermitSetText(permitText);
        vaadinPepBuilderHasTextMock.lastBiConsumer.accept(ad, button); // Simulate decision

        // THEN
        verify(button, times(0)).setText(permitText);
    }

    @Test
    void when_EnforceHasTextOnDenySetTextWithDeny_then_ComponentSetTextIsCalled() {
        // GIVEN
        VaadinPepBuilderHasTextMock vaadinPepBuilderHasTextMock = new VaadinPepBuilderHasTextMock();
        var                         ad                          = AuthorizationDecision.DENY;
        Button                      button                      = getButtonMockWithUI();
        String                      denyText                    = "deny";

        // WHEN
        vaadinPepBuilderHasTextMock.onDenySetText(denyText);
        vaadinPepBuilderHasTextMock.lastBiConsumer.accept(ad, button); // Simulate decision

        // THEN
        verify(button).setText(denyText);
    }

    @Test
    void when_EnforceHasTextOnDenySetTextWithPermit_then_ComponentSetTextIsNotCalled() {
        // GIVEN
        VaadinPepBuilderHasTextMock vaadinPepBuilderHasTextMock = new VaadinPepBuilderHasTextMock();
        var                         ad                          = AuthorizationDecision.PERMIT;
        Button                      button                      = getButtonMockWithUI();
        String                      denyText                    = "deny";

        // WHEN
        vaadinPepBuilderHasTextMock.onDenySetText(denyText);
        vaadinPepBuilderHasTextMock.lastBiConsumer.accept(ad, button); // Simulate decision

        // THEN
        verify(button, times(0)).setText(denyText);
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
