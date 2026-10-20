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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.BiConsumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.BeforeLeaveEvent;
import com.vaadin.flow.router.BeforeLeaveListener;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.VaadinServletResponse;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import io.sapl.vaadin.VaadinPep.LifecycleBeforeLeavePepBuilder;
import io.sapl.vaadin.base.SecurityHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Lifecycle before leave PEP builder")
class LifecycleBeforeLeavePepBuilderTests {

    private static MockedStatic<SecurityHelper> securityHelperMock;
    private static MockedStatic<Notification>   notificationMockedStatic;

    @Mock
    private ReactivePolicyDecisionPoint        pdpMock;
    @Mock
    private VaadinConstraintEnforcementService enforcementServiceMock;

    @Mock
    private VaadinServletRequest  servletRequest;
    @Mock
    private VaadinServletResponse servletResponse;
    @Mock
    private HttpServletRequest    httpServletRequest;
    @Mock
    private HttpServletResponse   httpServletResponse;

    LifecycleBeforeLeavePepBuilder sut;

    @BeforeAll
    static void beforeAll() {
        var subject = JsonNodeFactory.instance.objectNode();
        subject.put("username", "dummy");
        securityHelperMock = mockStatic(SecurityHelper.class);
        securityHelperMock.when(SecurityHelper::getSubject).thenReturn(subject);
        notificationMockedStatic = mockStatic(Notification.class);
        mockSpringContextHolderAuthentication();
    }

    @AfterAll
    static void afterAll() {
        securityHelperMock.close();
        notificationMockedStatic.close();
    }

    @BeforeEach
    void setupTest() {
        sut = new LifecycleBeforeLeavePepBuilder(pdpMock, enforcementServiceMock);
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
    }

    @Test
    void when_buildTwice_then_exceptionIsThrown() {
        // GIVEN
        // WHEN
        sut.build();
        // THEN
        assertThatThrownBy(() -> sut.build()).isInstanceOf(AccessDeniedException.class)
                .hasMessage("Builder has already been build. The builder can only be used once.");
    }

    @Test
    void when_newInstanceWithoutAuthentication_then_NoUsernameIsAdded() {
        // GIVEN
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);

        // WHEN

        // THEN
        assertThat(sut.isBuilt).isFalse();
    }

    @Test
    void when_beforeLeaveWithEmptyDecisionEventListenerList_thenEventGetUIIsNotCalled() {
        // GIVEN
        mockSpringContextHolderAuthentication();
        BeforeLeaveListener listener             = sut.build();
        var                 beforeLeaveEventMock = mock(BeforeLeaveEvent.class);

        // WHEN
        listener.beforeLeave(beforeLeaveEventMock);

        // THEN
        verify(beforeLeaveEventMock, times(0)).getUI();
    }

    @Test
    void when_onDenyDoWithDeny_thenBiConsumerIsAccepted() {
        // GIVEN
        mockSpringContextHolderAuthentication();
        var permittingPdp             = mockPdp();
        var denyingEnforcementService = mockNextVaadinConstraintEnforcementService(Decision.DENY);
        sut = new LifecycleBeforeLeavePepBuilder(permittingPdp, denyingEnforcementService);

        var beforeLeaveEventMock = mockBeforeLeaveEvent();

        // Methods on SUT
        @SuppressWarnings("unchecked") // suppress mock
        BiConsumer<AuthorizationDecision, BeforeEvent> biConsumerMock = mock(BiConsumer.class);
        sut.onDenyDo(biConsumerMock);
        BeforeLeaveListener listener = sut.build();

        // WHEN
        listener.beforeLeave(beforeLeaveEventMock);

        // THEN
        verify(beforeLeaveEventMock, times(1)).getUI();
        verify(denyingEnforcementService, times(1)).enforceConstraintsOfDecision(any(), any(), any());
        verify(biConsumerMock, times(1)).accept(any(AuthorizationDecision.class), any(BeforeEvent.class));
    }

    @Test
    void when_onDenyDoWithPermit_thenBiConsumerIsNotAccepted() {
        // GIVEN
        mockSpringContextHolderAuthentication();
        var permittingPdp                = mockPdp();
        var permittingEnforcementService = mockNextVaadinConstraintEnforcementService(Decision.PERMIT);
        sut = new LifecycleBeforeLeavePepBuilder(permittingPdp, permittingEnforcementService);

        var beforeLeaveEventMock = mockBeforeLeaveEvent();

        // Methods on SUT
        @SuppressWarnings("unchecked") // suppress mock
        BiConsumer<AuthorizationDecision, BeforeEvent> biConsumerMock = mock(BiConsumer.class);
        sut.onDenyDo(biConsumerMock);
        BeforeLeaveListener listener = sut.build();

        // WHEN
        listener.beforeLeave(beforeLeaveEventMock);

        // THEN
        verify(beforeLeaveEventMock, times(1)).getUI();
        verify(permittingEnforcementService, times(1)).enforceConstraintsOfDecision(any(), any(), any());
        verify(biConsumerMock, times(0)).accept(any(AuthorizationDecision.class), any(BeforeEvent.class));
    }

    @Test
    void when_onPermitDoWithDeny_thenBiConsumerIsAccepted() {
        // GIVEN
        mockSpringContextHolderAuthentication();
        var permittingPdp             = mockPdp();
        var denyingEnforcementService = mockNextVaadinConstraintEnforcementService(Decision.DENY);
        sut = new LifecycleBeforeLeavePepBuilder(permittingPdp, denyingEnforcementService);

        var beforeLeaveEventMock = mockBeforeLeaveEvent();

        // Methods on SUT
        @SuppressWarnings("unchecked") // suppress mock
        BiConsumer<AuthorizationDecision, BeforeEvent> biConsumerMock = mock(BiConsumer.class);
        sut.onPermitDo(biConsumerMock);
        BeforeLeaveListener listener = sut.build();

        // WHEN
        listener.beforeLeave(beforeLeaveEventMock);

        // THEN
        verify(beforeLeaveEventMock, times(1)).getUI();
        verify(denyingEnforcementService, times(1)).enforceConstraintsOfDecision(any(), any(), any());
        verify(biConsumerMock, times(0)).accept(any(AuthorizationDecision.class), any(BeforeEvent.class));
    }

    @Test
    void when_onPermitDoWithPermit_thenBiConsumerIsNotAccepted() {
        // GIVEN
        mockSpringContextHolderAuthentication();
        var permittingPdp                = mockPdp();
        var permittingEnforcementService = mockNextVaadinConstraintEnforcementService(Decision.PERMIT);
        sut = new LifecycleBeforeLeavePepBuilder(permittingPdp, permittingEnforcementService);

        var beforeLeaveEventMock = mockBeforeLeaveEvent();

        // Methods on SUT
        @SuppressWarnings("unchecked") // suppress mock
        BiConsumer<AuthorizationDecision, BeforeEvent> biConsumerMock = mock(BiConsumer.class);
        sut.onPermitDo(biConsumerMock);
        BeforeLeaveListener listener = sut.build();

        // WHEN
        listener.beforeLeave(beforeLeaveEventMock);

        // THEN
        verify(beforeLeaveEventMock, times(1)).getUI();
        verify(permittingEnforcementService, times(1)).enforceConstraintsOfDecision(any(), any(), any());
        verify(biConsumerMock, times(1)).accept(any(AuthorizationDecision.class), any(BeforeEvent.class));
    }

    @Test
    void when_subject_thenAuthorizationDecisionWithSubject() {
        // GIVEN
        var permittingPdp                = mockPdp();
        var permittingEnforcementService = mockNextVaadinConstraintEnforcementService(Decision.PERMIT);
        sut = new LifecycleBeforeLeavePepBuilder(permittingPdp, permittingEnforcementService);

        // WHEN
        sut.subject("Test subject");

        // THEN
        assertThat(sut.vaadinPep.getAuthorizationSubscription().subject()).isEqualTo(Value.of("Test subject"));
    }

    @Test
    void when_environment_thenAuthorizationDecisionWithEnvironment() {
        // GIVEN
        var permittingPdp                = mockPdp();
        var permittingEnforcementService = mockNextVaadinConstraintEnforcementService(Decision.PERMIT);
        sut = new LifecycleBeforeLeavePepBuilder(permittingPdp, permittingEnforcementService);

        // WHEN
        sut.environment("Test environment");

        // THEN
        assertThat(sut.vaadinPep.getAuthorizationSubscription().environment()).isEqualTo(Value.of("Test environment"));
    }

    @Test
    void when_beforeLeaveOnDenyRedirectWithDeny_then_ForwardToOnEventIsUsed() {
        // GIVEN
        var permittingPdp             = mockPdp();
        var denyingEnforcementService = mockNextVaadinConstraintEnforcementService(Decision.DENY);
        sut = new LifecycleBeforeLeavePepBuilder(permittingPdp, denyingEnforcementService);

        mockSpringContextHolderAuthentication();
        var beforeLeaveEventMock = mockBeforeLeaveEvent();

        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "saplVaadin");
        node.put("id", "showNotification");
        node.put("message", "text message");

        // Methods on SUT
        sut.onDenyRedirectTo("/");
        BeforeLeaveListener listener = sut.build();

        // WHEN
        listener.beforeLeave(beforeLeaveEventMock);

        // THEN
        verify(beforeLeaveEventMock, times(1)).getUI();
        verify(denyingEnforcementService, times(1)).enforceConstraintsOfDecision(any(), any(), any());
        verify(beforeLeaveEventMock, times(1)).forwardTo(anyString());
    }

    @Test
    void when_beforeLeaveOnDenyRedirectWithPermit_then_ForwardToOnEventIsNotUsed() {
        // GIVEN
        var permittingPdp                = mockPdp();
        var permittingEnforcementService = mockNextVaadinConstraintEnforcementService(Decision.PERMIT);
        sut = new LifecycleBeforeLeavePepBuilder(permittingPdp, permittingEnforcementService);

        mockSpringContextHolderAuthentication();

        var beforeLeaveEventMock = mockBeforeLeaveEvent();

        // Methods on SUT
        sut.onDenyRedirectTo("/");
        BeforeLeaveListener listener = sut.build();

        // WHEN
        listener.beforeLeave(beforeLeaveEventMock);

        // THEN
        verify(beforeLeaveEventMock, times(1)).getUI();
        verify(permittingEnforcementService, times(1)).enforceConstraintsOfDecision(any(), any(), any());
        verify(beforeLeaveEventMock, times(0)).forwardTo(anyString());
    }

    @Test
    void when_beforeLeaveOnDenyRerouteWithPermit_then_rerouteToOnEventIsNotUsed() {
        // GIVEN
        var permittingPdp                = mockPdp();
        var permittingEnforcementService = mockNextVaadinConstraintEnforcementService(Decision.PERMIT);
        sut = new LifecycleBeforeLeavePepBuilder(permittingPdp, permittingEnforcementService);

        var beforeLeaveEventMock = mockBeforeLeaveEvent();

        // Methods on SUT
        sut.onDenyRerouteTo("/");
        BeforeLeaveListener listener = sut.build();

        // WHEN
        listener.beforeLeave(beforeLeaveEventMock);

        // THEN
        verify(beforeLeaveEventMock, times(1)).getUI();
        verify(permittingEnforcementService, times(1)).enforceConstraintsOfDecision(any(), any(), any());
        verify(beforeLeaveEventMock, times(0)).rerouteTo(anyString());
    }

    @Test
    void when_beforeLeaveOnDenyRerouteWithDeny_then_rerouteToOnEventIsNotUsed() {
        // GIVEN
        var permittingPdp             = mockPdp();
        var denyingEnforcementService = mockNextVaadinConstraintEnforcementService(Decision.DENY);
        sut = new LifecycleBeforeLeavePepBuilder(permittingPdp, denyingEnforcementService);

        var beforeLeaveEventMock = mockBeforeLeaveEvent();

        // Methods on SUT
        sut.onDenyRerouteTo("/");
        BeforeLeaveListener listener = sut.build();

        // WHEN
        listener.beforeLeave(beforeLeaveEventMock);

        // THEN
        verify(beforeLeaveEventMock, times(1)).getUI();
        verify(denyingEnforcementService, times(1)).enforceConstraintsOfDecision(any(), any(), any());
        verify(beforeLeaveEventMock, times(1)).rerouteTo(anyString());
    }

    @Test
    void when_beforeLeaveOnDenyLogoutWithDeny_then_logoutOnLogoutMockIsUsed() {
        // GIVEN
        var permittingPdp             = mockPdp();
        var denyingEnforcementService = mockNextVaadinConstraintEnforcementService(Decision.DENY);
        sut = new LifecycleBeforeLeavePepBuilder(permittingPdp, denyingEnforcementService);

        var beforeLeaveEventMock = mockBeforeLeaveEvent();

        // Methods on SUT
        sut.onDenyLogout();
        BeforeLeaveListener listener = sut.build();

        // WHEN
        try (val currentRequest = mockStatic(VaadinServletRequest.class);
                val currentResponse = mockStatic(VaadinServletResponse.class);
                val logoutHandlers = mockConstruction(SecurityContextLogoutHandler.class)) {
            currentRequest.when(VaadinServletRequest::getCurrent).thenReturn(servletRequest);
            currentResponse.when(VaadinServletResponse::getCurrent).thenReturn(servletResponse);
            when(servletRequest.getHttpServletRequest()).thenReturn(httpServletRequest);
            when(servletResponse.getHttpServletResponse()).thenReturn(httpServletResponse);

            listener.beforeLeave(beforeLeaveEventMock);

            verify(beforeLeaveEventMock, times(1)).getUI();
            verify(denyingEnforcementService, times(1)).enforceConstraintsOfDecision(any(), any(), any());
            assertThat(logoutHandlers.constructed()).singleElement()
                    .satisfies(handler -> verify(handler).logout(httpServletRequest, httpServletResponse, null));
        }
    }

    @Test
    void when_beforeLeaveOnDenyNotify_then_notificationIsShown() {
        // GIVEN
        mockSpringContextHolderAuthentication();
        var permittingPdp             = mockPdp();
        var denyingEnforcementService = mockNextVaadinConstraintEnforcementService(Decision.DENY);
        sut = new LifecycleBeforeLeavePepBuilder(permittingPdp, denyingEnforcementService);
        var beforeLeaveEventMock = mockBeforeLeaveEvent();
        var notificationMock     = mockNotification();

        // Methods on SUT
        sut.onDenyNotify();
        BeforeLeaveListener listener = sut.build();

        // WHEN
        listener.beforeLeave(beforeLeaveEventMock);

        // THEN
        verify(beforeLeaveEventMock, times(2)).getUI(); // called in startSubscriptionOnce() and onDenyNotify()
        verify(notificationMock, times(1)).addThemeVariants(any());
        verify(denyingEnforcementService, times(1)).enforceConstraintsOfDecision(any(), any(), any());
    }

    private Notification mockNotification() {
        var notificationMock = mock(Notification.class);
        notificationMockedStatic.when(() -> Notification.show(anyString())).thenReturn(notificationMock);
        doNothing().when(notificationMock).addThemeVariants(any());
        return notificationMock;
    }

    private static void mockSpringContextHolderAuthentication() {
        var authentication  = mock(Authentication.class);
        var securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    private VaadinConstraintEnforcementService mockNextVaadinConstraintEnforcementService(Decision decision) {
        var decisionEnforcementService = mock(VaadinConstraintEnforcementService.class);
        var monoMock                   = Mono
                .just(new AuthorizationDecision(decision, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED));
        when(decisionEnforcementService.enforceConstraintsOfDecision(any(), any(), any())).thenReturn(monoMock);
        return decisionEnforcementService;
    }

    private BeforeLeaveEvent mockBeforeLeaveEvent() {
        var beforeLeaveEventMock = mock(BeforeLeaveEvent.class);
        var locationMock         = mock(Location.class);
        when(locationMock.getFirstSegment()).thenReturn("admin-page");
        doReturn(locationMock).when(beforeLeaveEventMock).getLocation();
        var mockedUI = UIMock.getMockedUI();
        when(beforeLeaveEventMock.getUI()).thenReturn(mockedUI);
        doReturn(String.class).when(beforeLeaveEventMock).getNavigationTarget();
        return beforeLeaveEventMock;
    }

    private ReactivePolicyDecisionPoint mockPdp() {
        ReactivePolicyDecisionPoint permittingPdp = mock(ReactivePolicyDecisionPoint.class);
        Flux<AuthorizationDecision> authzFlux     = Flux.just(AuthorizationDecision.PERMIT);
        when(permittingPdp.decide(any(AuthorizationSubscription.class))).thenReturn(authzFlux);
        return permittingPdp;
    }

}
