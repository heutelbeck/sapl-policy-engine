package io.sapl.vaadin;

import static io.sapl.api.interpreter.Val.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterListener;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.Location;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.vaadin.VaadinPep.LifecycleBeforeEnterPepBuilder;
import io.sapl.vaadin.base.SecurityHelper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class LifecycleBeforeEnterPepBuilderTests {

	private static MockedStatic<SecurityHelper> securityHelperMock;
	LifecycleBeforeEnterPepBuilder sut;

	@BeforeAll
	static void beforeAll() {
		var subject = JSON.objectNode();
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
		var pdpMock = mock(PolicyDecisionPoint.class);
		var enforcementServiceMock = mock(VaadinConstraintEnforcementService.class);
		sut = new LifecycleBeforeEnterPepBuilder(pdpMock, enforcementServiceMock);
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
		var rolesNode = JSON.arrayNode().add("admin");
		// WHEN
		sut.build();

		// THEN
		assertEquals(rolesNode, sut.vaadinPep.getAuthorizationSubscription().getSubject().get("roles"));
		assertTrue(sut.isBuild);
	}

	@Test
	void when_buildTwice_then_exceptionIsThrown() {
		// GIVEN
		// WHEN
		sut.build();
		Exception exception = assertThrows(AccessDeniedException.class, sut::build);

		// THEN
		assertEquals("Builder has already been build. The builder can only be used once.", exception.getMessage());
	}
	

	@Test
	void when_ResourceIsDefined() {
		// GIVEN
		var beforeEnterEventMock = mock(BeforeEnterEvent.class);
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
		var resourceNode = JSON.arrayNode().add("Resource");
		// WHEN
		sut.resource(resourceNode);
		
		// THEN
		assertEquals(resourceNode, sut.vaadinPep.getAuthorizationSubscription().getResource());
	}

	@Test
	void when_beforeEnterWithEmptyDecisionEventListenerList_thenEventGetUIIsNotCalled() {
		// GIVEN
		var listener = sut.build();
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
		var sut = new VaadinPep.LifecycleBeforeEnterPepBuilder(pdpMock, enforcementServiceMock);
		@SuppressWarnings("unchecked") // suppress mock
		BiConsumer<AuthorizationDecision, BeforeEvent> biConsumerMock = mock(BiConsumer.class);
		sut.onDenyDo(biConsumerMock);
		BeforeEnterListener listener = sut.build();

		// WHEN
		listener.beforeEnter(beforeEnterEventMock);

		// THEN
		verify(beforeEnterEventMock, times(1)).getUI();
		verify(enforcementServiceMock, times(1)).enforceConstraintsOfDecision(any(), any(), any());
		verify(biConsumerMock, times(1)).accept(any(AuthorizationDecision.class), any(BeforeEvent.class));
	}

	private PolicyDecisionPoint mockPdp() {
		var pdpMock = mock(PolicyDecisionPoint.class);
		var authzFlux = Flux.just(new AuthorizationDecision(Decision.PERMIT));
		when(pdpMock.decide(any(AuthorizationSubscription.class))).thenReturn(authzFlux);
		return pdpMock;
	}

	private BeforeEnterEvent mockBeforeEnterEvent() {
		var beforeEnterEventMock = mock(BeforeEnterEvent.class);
		var locationMock = mock(Location.class);
		when(locationMock.getFirstSegment()).thenReturn("admin-page");
		doReturn(locationMock).when(beforeEnterEventMock).getLocation();
		var mockedUI = UIMock.getMockedUI();
		when(beforeEnterEventMock.getUI()).thenReturn(mockedUI);
		return beforeEnterEventMock;
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
}
