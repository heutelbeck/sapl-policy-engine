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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterListener;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.Location;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import io.sapl.vaadin.VaadinPep.LifecycleBeforeEnterPepBuilder;
import io.sapl.vaadin.base.SecurityHelper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Lifecycle before enter PEP builder")
class LifecycleBeforeEnterPepBuilderTests {

    private static MockedStatic<SecurityHelper> securityHelperMock;

    @Mock
    private ReactivePolicyDecisionPoint        pdpMock;
    @Mock
    private VaadinConstraintEnforcementService enforcementServiceMock;

    LifecycleBeforeEnterPepBuilder sut;

    @BeforeAll
    static void beforeAll() {
        var subject = JsonNodeFactory.instance.objectNode();
        subject.put("username", "dummy");
        securityHelperMock = mockStatic(SecurityHelper.class);
        securityHelperMock.when(SecurityHelper::getSubject).thenReturn(subject);
        List<String> userRoles = new ArrayList<>();
        userRoles.add("admin");
        securityHelperMock.when(SecurityHelper::getUserRoles).thenReturn(userRoles);
        mockSpringContextHolderAuthentication();
    }

    @AfterAll
    static void afterAll() {
        securityHelperMock.close();
    }

    @BeforeEach
    void setup() {
        sut = new LifecycleBeforeEnterPepBuilder(pdpMock, enforcementServiceMock);
    }

    @Test
    void when_newInstance_then_isBuiltIsFalse() {
        // GIVEN

        // WHEN
        // THEN
        assertThat(sut.isBuilt).isFalse();
    }

    @Test
    void when_build_then_isBuiltIsTrue() {
        // GIVEN
        // WHEN
        sut.build();

        // THEN
        assertThat(sut.isBuilt).isTrue();
        assertThat(sut.vaadinPep.getAuthorizationSubscription().subject()).isNotNull();
    }

    @Test
    void when_buildTwice_then_exceptionIsThrown() {
        // GIVEN
        // WHEN
        sut.build();

        // THEN
        assertThatThrownBy(sut::build).isInstanceOf(AccessDeniedException.class)
                .hasMessage("Builder has already been build. The builder can only be used once.");
    }

    @Test
    void when_ResourceIsDefined() {
        // GIVEN
        var beforeEnterEventMock  = mock(BeforeEnterEvent.class);
        var beforeEnterEventMock2 = mock(BeforeEnterEvent.class);
        doReturn(String.class).when(beforeEnterEventMock).getNavigationTarget();
        doReturn(String.class).when(beforeEnterEventMock2).getNavigationTarget();
        sut.setResourceByNavigationTargetIfNotDefined(beforeEnterEventMock);

        // WHEN
        sut.setResourceByNavigationTargetIfNotDefined(beforeEnterEventMock2);

        // THEN
        verify(beforeEnterEventMock2, times(0)).getNavigationTarget();
    }

    @Test

    void when_ResourceIsDefined_itIsSavedInSubscription() {
        // GIVEN
        var resourceValue = Value.ofArray(Value.of("Resource"));
        // WHEN
        sut.resource(resourceValue);

        // THEN
        assertThat(sut.vaadinPep.getAuthorizationSubscription().resource()).isEqualTo(resourceValue);
    }

    @Test
    void when_beforeEnterWithEmptyDecisionEventListenerList_thenEventGetUIIsNotCalled() {
        // GIVEN
        var listener             = sut.build();
        var beforeEnterEventMock = mock(BeforeEnterEvent.class);

        // WHEN
        listener.beforeEnter(beforeEnterEventMock);

        // THEN
        verify(beforeEnterEventMock, times(0)).getUI();
    }

    @Test
    void when_onDenyDoWithDeny_thenBiConsumerIsAccepted() {
        // GIVEN
        mockSpringContextHolderAuthentication();
        var pdpMock = mockPdp();

        var beforeEnterEventMock = mockBeforeEnterEvent();
        doReturn(String.class).when(beforeEnterEventMock).getNavigationTarget();

        var enforcementServiceMock = mockNextVaadinConstraintEnforcementService(Decision.DENY);

        // Methods on SUT
        var pepUnderTest = new VaadinPep.LifecycleBeforeEnterPepBuilder(pdpMock, enforcementServiceMock);

        @SuppressWarnings("unchecked") // suppress mock
        BiConsumer<AuthorizationDecision, BeforeEvent> biConsumerMock = mock(BiConsumer.class);
        pepUnderTest.onDenyDo(biConsumerMock);
        BeforeEnterListener listener = pepUnderTest.build();

        // WHEN
        listener.beforeEnter(beforeEnterEventMock);

        // THEN
        verify(beforeEnterEventMock, times(1)).getUI();
        verify(enforcementServiceMock, times(1)).enforceConstraintsOfDecision(any(), any(), any());
        verify(biConsumerMock, times(1)).accept(any(AuthorizationDecision.class), any(BeforeEvent.class));
    }

    private ReactivePolicyDecisionPoint mockPdp() {
        var pdpMock   = mock(ReactivePolicyDecisionPoint.class);
        var authzFlux = Flux.just(AuthorizationDecision.PERMIT);
        when(pdpMock.decide(any(AuthorizationSubscription.class))).thenReturn(authzFlux);
        return pdpMock;
    }

    private BeforeEnterEvent mockBeforeEnterEvent() {
        var beforeEnterEventMock = mock(BeforeEnterEvent.class);
        var locationMock         = mock(Location.class);
        when(locationMock.getFirstSegment()).thenReturn("admin-page");
        doReturn(locationMock).when(beforeEnterEventMock).getLocation();
        var mockedUI = UIMock.getMockedUI();
        when(beforeEnterEventMock.getUI()).thenReturn(mockedUI);
        return beforeEnterEventMock;
    }

    private static void mockSpringContextHolderAuthentication() {
        var authentication  = Mockito.mock(Authentication.class);
        var securityContext = Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    private VaadinConstraintEnforcementService mockNextVaadinConstraintEnforcementService(Decision decision) {
        var enforcementServiceMock = mock(VaadinConstraintEnforcementService.class);
        var monoMock               = Mono
                .just(new AuthorizationDecision(decision, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED));
        when(enforcementServiceMock.enforceConstraintsOfDecision(any(), any(), any())).thenReturn(monoMock);
        return enforcementServiceMock;
    }
}
