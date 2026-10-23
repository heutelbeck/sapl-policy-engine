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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;

import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import io.sapl.vaadin.base.SecurityHelper;

@DisplayName("PEP builder service")
@MockitoSettings(strictness = Strictness.LENIENT)
class PepBuilderServiceTests {

    @Mock
    private ReactivePolicyDecisionPoint         pdp;
    @Mock
    private VaadinConstraintEnforcementService  vaadinConstraintEnforcementService;
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
    void when_withComponentIsCalled_then_ComponentIsSetInComponentBuilder() {
        // GIVEN
        PepBuilderService pepBuilderService = new PepBuilderService(pdp, vaadinConstraintEnforcementService);
        Component         component         = mock(Component.class);
        // WHEN + THEN
        assertThat(pepBuilderService.with(component).component).isEqualTo(component);
    }

    @Test
    void when_withButtonIsCalled_then_ButtonIsSetInButtonBuilder() {
        // GIVEN
        PepBuilderService pepBuilderService = new PepBuilderService(pdp, vaadinConstraintEnforcementService);
        Button            button            = mock(Button.class);
        // WHEN + THEN
        assertThat(pepBuilderService.with(button).component).isEqualTo(button);
    }

    @Test
    void when_withTextFieldIsCalled_then_TextFieldIsSetInTextFieldBuilder() {
        // GIVEN
        PepBuilderService pepBuilderService = new PepBuilderService(pdp, vaadinConstraintEnforcementService);
        TextField         textField         = mock(TextField.class);
        // WHEN + THEN
        assertThat(pepBuilderService.with(textField).component).isEqualTo(textField);
    }

    @Test
    void when_withCheckboxIsCalled_then_CheckboxIsSetInCheckboxBuilder() {
        // GIVEN
        PepBuilderService pepBuilderService = new PepBuilderService(pdp, vaadinConstraintEnforcementService);
        Checkbox          checkbox          = mock(Checkbox.class);
        // WHEN + THEN
        assertThat(pepBuilderService.with(checkbox).component).isEqualTo(checkbox);
    }

    @Test
    void when_withSpanIsCalled_then_SpanIsSetInSpanBuilder() {
        // GIVEN
        PepBuilderService pepBuilderService = new PepBuilderService(pdp, vaadinConstraintEnforcementService);
        Span              span              = mock(Span.class);
        // WHEN + THEN
        assertThat(pepBuilderService.with(span).component).isEqualTo(span);
    }

    @Test
    void when_getMultiBuilder_then_returnMultibuilder() {
        // GIVEN
        PepBuilderService pepBuilderService = new PepBuilderService(pdp, vaadinConstraintEnforcementService);
        // WHEN
        MultiBuilder multiBuilder = pepBuilderService.getMultiBuilder();
        // THEN
        assertThat(multiBuilder.getClass()).isEqualTo(MultiBuilder.class);
    }

    @Test
    void when_getLifecycleBeforeEnterPepBuilder_then_returnLifecycleBeforeEnterPepBuilder() {
        // GIVEN
        PepBuilderService pepBuilderService = new PepBuilderService(pdp, vaadinConstraintEnforcementService);
        mockSpringContextHolderAuthentication();
        // WHEN
        VaadinPep.LifecycleBeforeEnterPepBuilder lifecycleBeforeEnterPepBuilder = pepBuilderService
                .getLifecycleBeforeEnterPepBuilder();
        // THEN
        assertThat(lifecycleBeforeEnterPepBuilder.getClass()).isEqualTo(VaadinPep.LifecycleBeforeEnterPepBuilder.class);
    }

    @Test
    void when_BeforeEnterBuilderBuild_then_isBuiltIsTrue() {
        // GIVEN
        PepBuilderService pepBuilderService = new PepBuilderService(pdp, vaadinConstraintEnforcementService);
        // WHEN
        mockSpringContextHolderAuthentication();
        VaadinPep.LifecycleBeforeEnterPepBuilder lifecycleBeforeEnterPepBuilder = pepBuilderService
                .getLifecycleBeforeEnterPepBuilder();
        lifecycleBeforeEnterPepBuilder.build();
        // THEN
        assertThat(lifecycleBeforeEnterPepBuilder.isBuilt).isTrue();
    }

    @Test
    void when_BeforeLeaveBuilderBuild_then_isBuiltIsTrue() {
        // GIVEN
        PepBuilderService pepBuilderService = new PepBuilderService(pdp, vaadinConstraintEnforcementService);
        // WHEN
        mockSpringContextHolderAuthentication();
        VaadinPep.LifecycleBeforeLeavePepBuilder lifecycleBeforeLeavePepBuilder = pepBuilderService
                .getLifecycleBeforeLeavePepBuilder();
        lifecycleBeforeLeavePepBuilder.build();
        // THEN
        assertThat(lifecycleBeforeLeavePepBuilder.isBuilt).isTrue();
    }

    private void mockSpringContextHolderAuthentication() {
        Authentication  authentication  = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }
}
