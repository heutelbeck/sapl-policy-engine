package io.sapl.vaadin;

import static io.sapl.api.interpreter.Val.JSON;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.BiConsumer;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.BeforeLeaveEvent;
import com.vaadin.flow.router.BeforeLeaveListener;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.server.VaadinServletRequest;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.vaadin.VaadinPep.LifecycleBeforeLeavePepBuilder;
import io.sapl.vaadin.base.SecurityHelper;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class LifecycleBeforeLeavePepBuilderTests {

	private static MockedStatic<SecurityHelper> securityHelperMock;
	private static MockedStatic<Notification> notificationMockedStatic;
	LifecycleBeforeLeavePepBuilder sut;

	@BeforeAll
	static void beforeAll() {
		var subject = JSON.objectNode();
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
		var pdpMock = mock(PolicyDecisionPoint.class);
		var enforcementServiceMock = mock(VaadinConstraintEnforcementService.class);
		sut = new LifecycleBeforeLeavePepBuilder(pdpMock, enforcementServiceMock);
	}

	@Test
	void when_newInstance_then_isBuildIsFalse() {
		// GIVEN
		// WHEN
		// THEN
		assertFalse(sut.isBuild);
	}

	@Test
	void when_build_then_isBuildIsTrue() {
		// GIVEN
		// WHEN
		sut.build();

		// THEN
		assertTrue(sut.isBuild);
	}

	@Test
	void when_buildTwice_then_exceptionIsThrown() {
		// GIVEN
		// WHEN
		sut.build();
		Exception exception = assertThrows(AccessDeniedException.class, () -> sut.build());
		// THEN
		assertEquals("Builder has already been build. The builder can only be used once.", exception.getMessage());
	}

	@Test
	void when_newInstanceWithoutAuthentication_then_NoUsernameIsAdded() {
		// GIVEN
		SecurityContext securityContext = mock(SecurityContext.class);
		when(securityContext.getAuthentication()).thenReturn(null);
		SecurityContextHolder.setContext(securityContext);

		// WHEN

		// THEN
		assertFalse(sut.isBuild);
	}

	@Test
	void when_beforeLeaveWithEmptyDecisionEventListenerList_thenEventGetUIIsNotCalled() {
		// GIVEN
		mockSpringContextHolderAuthentication();
		BeforeLeaveListener listener = sut.build();
		var beforeLeaveEventMock = mock(BeforeLeaveEvent.class);

		// WHEN
		listener.beforeLeave(beforeLeaveEventMock);

		// THEN
		verify(beforeLeaveEventMock, times(0)).getUI();
	}

	@Test
	void when_onDenyDoWithDeny_thenBiConsumerIsAccepted() {
		// GIVEN
		mockSpringContextHolderAuthentication();
		var pdpMock = mockPdp();
		var enforcementServiceMock = mockNextVaadinConstraintEnforcementService(Decision.DENY);
		sut = new LifecycleBeforeLeavePepBuilder(pdpMock, enforcementServiceMock);

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
		verify(enforcementServiceMock, times(1)).enforceConstraintsOfDecision(any(), any(), any());
		verify(biConsumerMock, times(1)).accept(any(AuthorizationDecision.class), any(BeforeEvent.class));
	}

	@Test
	void when_onDenyDoWithPermit_thenBiConsumerIsNotAccepted() {
		// GIVEN
		mockSpringContextHolderAuthentication();
		var pdpMock = mockPdp();
		var enforcementServiceMock = mockNextVaadinConstraintEnforcementService(Decision.PERMIT);
		sut = new LifecycleBeforeLeavePepBuilder(pdpMock, enforcementServiceMock);

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
		verify(enforcementServiceMock, times(1)).enforceConstraintsOfDecision(any(), any(), any());
		verify(biConsumerMock, times(0)).accept(any(AuthorizationDecision.class), any(BeforeEvent.class));
	}

	@Test
	void when_onPermitDoWithDeny_thenBiConsumerIsAccepted() {
		// GIVEN
		mockSpringContextHolderAuthentication();
		var pdpMock = mockPdp();
		var enforcementServiceMock = mockNextVaadinConstraintEnforcementService(Decision.DENY);
		sut = new LifecycleBeforeLeavePepBuilder(pdpMock, enforcementServiceMock);

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
		verify(enforcementServiceMock, times(1)).enforceConstraintsOfDecision(any(), any(), any());
		verify(biConsumerMock, times(0)).accept(any(AuthorizationDecision.class), any(BeforeEvent.class));
	}

	@Test
	void when_onPermitDoWithPermit_thenBiConsumerIsNotAccepted() {
		// GIVEN
		mockSpringContextHolderAuthentication();
		var pdpMock = mockPdp();
		var enforcementServiceMock = mockNextVaadinConstraintEnforcementService(Decision.PERMIT);
		sut = new LifecycleBeforeLeavePepBuilder(pdpMock, enforcementServiceMock);

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
		verify(enforcementServiceMock, times(1)).enforceConstraintsOfDecision(any(), any(), any());
		verify(biConsumerMock, times(1)).accept(any(AuthorizationDecision.class), any(BeforeEvent.class));
	}

	@Test
	void when_subject_thenAuthorizationDecisionWithSubject() {
		// GIVEN
		var pdpMock = mockPdp();
		var enforcementServiceMock = mockNextVaadinConstraintEnforcementService(Decision.PERMIT);
		sut = new LifecycleBeforeLeavePepBuilder(pdpMock, enforcementServiceMock);

		// WHEN
		sut.subject("Test subject");

		// THEN
		assertEquals("Test subject", sut.vaadinPep.getAuthorizationSubscription().getSubject().asText());
	}

	@Test
	void when_environment_thenAuthorizationDecisionWithEnvironment() {
		// GIVEN
		var pdpMock = mockPdp();
		var enforcementServiceMock = mockNextVaadinConstraintEnforcementService(Decision.PERMIT);
		sut = new LifecycleBeforeLeavePepBuilder(pdpMock, enforcementServiceMock);

		// WHEN
		sut.environment("Test environment");

		// THEN
		assertEquals("Test environment", sut.vaadinPep.getAuthorizationSubscription().getEnvironment().asText());
	}

	@Test
	void when_beforeLeaveOnDenyRedirectWithDeny_then_ForwardToOnEventIsUsed() {
		// GIVEN
		var pdpMock = mockPdp();
		var enforcementServiceMock = mockNextVaadinConstraintEnforcementService(Decision.DENY);
		sut = new LifecycleBeforeLeavePepBuilder(pdpMock, enforcementServiceMock);

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
		verify(enforcementServiceMock, times(1)).enforceConstraintsOfDecision(any(), any(), any());
		verify(beforeLeaveEventMock, times(1)).forwardTo(anyString());
	}

	@Test
	void when_beforeLeaveOnDenyRedirectWithPermit_then_ForwardToOnEventIsNotUsed() {
		// GIVEN
		var pdpMock = mockPdp();
		var enforcementServiceMock = mockNextVaadinConstraintEnforcementService(Decision.PERMIT);
		sut = new LifecycleBeforeLeavePepBuilder(pdpMock, enforcementServiceMock);

		mockSpringContextHolderAuthentication();

		var beforeLeaveEventMock = mockBeforeLeaveEvent();

		// Methods on SUT
		sut.onDenyRedirectTo("/");
		BeforeLeaveListener listener = sut.build();

		// WHEN
		listener.beforeLeave(beforeLeaveEventMock);

		// THEN
		verify(beforeLeaveEventMock, times(1)).getUI();
		verify(enforcementServiceMock, times(1)).enforceConstraintsOfDecision(any(), any(), any());
		verify(beforeLeaveEventMock, times(0)).forwardTo(anyString());
	}

	@Test
	void when_beforeLeaveOnDenyRerouteWithPermit_then_rerouteToOnEventIsNotUsed() {
		// GIVEN
		var pdpMock = mockPdp();
		var enforcementServiceMock = mockNextVaadinConstraintEnforcementService(Decision.PERMIT);
		sut = new LifecycleBeforeLeavePepBuilder(pdpMock, enforcementServiceMock);

		var beforeLeaveEventMock = mockBeforeLeaveEvent();

		// Methods on SUT
		sut.onDenyRerouteTo("/");
		BeforeLeaveListener listener = sut.build();

		// WHEN
		listener.beforeLeave(beforeLeaveEventMock);

		// THEN
		verify(beforeLeaveEventMock, times(1)).getUI();
		verify(enforcementServiceMock, times(1)).enforceConstraintsOfDecision(any(), any(), any());
		verify(beforeLeaveEventMock, times(0)).rerouteTo(anyString());
	}

	@Test
	void when_beforeLeaveOnDenyRerouteWithDeny_then_rerouteToOnEventIsNotUsed() {
		// GIVEN
		var pdpMock = mockPdp();
		var enforcementServiceMock = mockNextVaadinConstraintEnforcementService(Decision.DENY);
		sut = new LifecycleBeforeLeavePepBuilder(pdpMock, enforcementServiceMock);

		var beforeLeaveEventMock = mockBeforeLeaveEvent();

		// Methods on SUT
		sut.onDenyRerouteTo("/");
		BeforeLeaveListener listener = sut.build();

		// WHEN
		listener.beforeLeave(beforeLeaveEventMock);

		// THEN
		verify(beforeLeaveEventMock, times(1)).getUI();
		verify(enforcementServiceMock, times(1)).enforceConstraintsOfDecision(any(), any(), any());
		verify(beforeLeaveEventMock, times(1)).rerouteTo(anyString());
	}

	@Test
	void when_beforeLeaveOnDenyLogoutWithDeny_then_logoutOnLogoutMockIsUsed() {
		// GIVEN
		var pdpMock = mockPdp();
		var enforcementServiceMock = mockNextVaadinConstraintEnforcementService(Decision.DENY);
		sut = new LifecycleBeforeLeavePepBuilder(pdpMock, enforcementServiceMock);

		var beforeLeaveEventMock = mockBeforeLeaveEvent();
		mockLogout();

		// Methods on SUT
		sut.onDenyLogout();
		BeforeLeaveListener listener = sut.build();

		// WHEN
		listener.beforeLeave(beforeLeaveEventMock);

		// THEN
		verify(beforeLeaveEventMock, times(1)).getUI();
		verify(enforcementServiceMock, times(1)).enforceConstraintsOfDecision(any(), any(), any());
	}

	@Test
	void when_beforeLeaveOnDenyNotify_then_notificationIsShown() {
		// GIVEN
		mockSpringContextHolderAuthentication();
		var pdpMock = mockPdp();
		var enforcementServiceMock = mockNextVaadinConstraintEnforcementService(Decision.DENY);
		sut = new LifecycleBeforeLeavePepBuilder(pdpMock, enforcementServiceMock);
		var beforeLeaveEventMock = mockBeforeLeaveEvent();
		var notificationMock = mockNotification();

		// Methods on SUT
		sut.onDenyNotify();
		BeforeLeaveListener listener = sut.build();

		// WHEN
		listener.beforeLeave(beforeLeaveEventMock);

		// THEN
		verify(beforeLeaveEventMock, times(2)).getUI(); // called in startSubscriptionOnce() and onDenyNotify()
		verify(notificationMock, times(1)).addThemeVariants(any());
		verify(enforcementServiceMock, times(1)).enforceConstraintsOfDecision(any(), any(), any());
	}

	private Notification mockNotification() {
		var notificationMock = mock(Notification.class);
		notificationMockedStatic.when(() -> Notification.show(anyString())).thenReturn(notificationMock);
		doNothing().when(notificationMock).addThemeVariants(any());
		return notificationMock;
	}

	private static void mockSpringContextHolderAuthentication() {
		var authentication = Mockito.mock(Authentication.class);
		var securityContext = Mockito.mock(SecurityContext.class);
		when(securityContext.getAuthentication()).thenReturn(authentication);
		SecurityContextHolder.setContext(securityContext);
	}

	private VaadinConstraintEnforcementService mockNextVaadinConstraintEnforcementService(Decision decision) {
		var enforcementServiceMock = mock(VaadinConstraintEnforcementService.class);
		var monoMock = Mono.just(new AuthorizationDecision(decision));
		when(enforcementServiceMock.enforceConstraintsOfDecision(any(), any(), any())).thenReturn(monoMock);
		return enforcementServiceMock;
	}

	private BeforeLeaveEvent mockBeforeLeaveEvent() {
		var beforeLeaveEventMock = mock(BeforeLeaveEvent.class);
		var locationMock = mock(Location.class);
		when(locationMock.getFirstSegment()).thenReturn("admin-page");
		doReturn(locationMock).when(beforeLeaveEventMock).getLocation();
		var mockedUI = UIMock.getMockedUI();
		when(beforeLeaveEventMock.getUI()).thenReturn(mockedUI);
		doReturn(String.class).when(beforeLeaveEventMock).getNavigationTarget();
		return beforeLeaveEventMock;
	}

	private PolicyDecisionPoint mockPdp() {
		PolicyDecisionPoint pdpMock = mock(PolicyDecisionPoint.class);
		Flux<AuthorizationDecision> authzFlux = Flux.just(new AuthorizationDecision(Decision.PERMIT));
		when(pdpMock.decide(any(AuthorizationSubscription.class))).thenReturn(authzFlux);
		return pdpMock;
	}

	private void mockLogout() {
		var logoutMock = mock(SecurityContextLogoutHandler.class);
		doNothing().when(logoutMock).logout(any(), any(), any());
		MockedStatic<VaadinServletRequest> utilities = Mockito.mockStatic(VaadinServletRequest.class);
		var request = mock(VaadinServletRequest.class);
		utilities.when(VaadinServletRequest::getCurrent).thenReturn(request);
		HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
		when(request.getHttpServletRequest()).thenReturn(httpServletRequest);
	}

	Flux<AuthorizationDecision> mockFlux() {
		Disposable disposable = mock(Disposable.class);
		@SuppressWarnings("unchecked") // suppress mock
		Flux<AuthorizationDecision> f = mock(Flux.class, invocation -> {
			if (Disposable.class.equals(invocation.getMethod().getReturnType()))
				return disposable;
			return invocation.getMock();
		});
		return f;
	}
}
