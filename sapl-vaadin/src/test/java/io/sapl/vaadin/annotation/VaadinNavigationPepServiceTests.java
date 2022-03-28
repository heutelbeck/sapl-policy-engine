package io.sapl.vaadin.annotation;

import static io.sapl.api.interpreter.Val.JSON;
import static io.sapl.vaadin.annotation.VaadinNavigationPepService.NavigationType.REROUTE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterListener;
import com.vaadin.flow.router.BeforeLeaveEvent;
import com.vaadin.flow.router.BeforeLeaveListener;

import io.sapl.vaadin.PepBuilderService;
import io.sapl.vaadin.VaadinPep;
import io.sapl.vaadin.annotation.annotations.OnDenyNavigate;
import io.sapl.vaadin.base.VaadinAuthorizationSubscriptionBuilderService;

@SuppressWarnings({"unchecked", "rawtypes"})
public class VaadinNavigationPepServiceTests {
	static Authentication authentication;

	@BeforeAll
	static void beforeAll() {
		var subject = JSON.objectNode();
		subject.put("username", "dummy");
		mockSpringContextHolderAuthentication();
	}

	@AfterAll
	static void afterAll() {
	}

	private static void mockSpringContextHolderAuthentication() {
		authentication = Mockito.mock(Authentication.class);
		SecurityContext securityContext = Mockito.mock(SecurityContext.class);
		Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
		SecurityContextHolder.setContext(securityContext);
	}

	@OnDenyNavigate(subject = "'test subject'")
	static
	class TargetWithSubject {}

	@Test
	void when_beforeEnterIsCalled_thenSubjectIsSetFromAnnotation() {
		//GIVEN
		// mock Livecycle builder
		var beforeEnterBuilder = mock(VaadinPep.LifecycleBeforeEnterPepBuilder.class);
		when(beforeEnterBuilder.build()).thenReturn((BeforeEnterListener) event -> {});

		// mock pep builder
		var pepBuilderService = mock(PepBuilderService.class);
		when(pepBuilderService.getLifecycleBeforeEnterPepBuilder()).thenReturn(beforeEnterBuilder);

		// authorization subscription builder
		var vaadinAuthorizationSubscriptionBuilderService = mock(VaadinAuthorizationSubscriptionBuilderService.class);
		var vaadinNavigationPepService = new VaadinNavigationPepService(pepBuilderService, vaadinAuthorizationSubscriptionBuilderService);

		// mock event and target class
		BeforeEnterEvent beforeEnterEventMock = mock(BeforeEnterEvent.class);
		// noinspection rawtypes
		when(beforeEnterEventMock.getNavigationTarget()).thenReturn((Class) TargetWithSubject.class);

		//WHEN
		vaadinNavigationPepService.beforeEnter(beforeEnterEventMock);

		//THEN
		verify(vaadinAuthorizationSubscriptionBuilderService).retrieveSubject(authentication, "'test subject'");
	}

	@OnDenyNavigate(action = "'test action'", resource = "'test resource'", environment = "'test environment'",
			navigation=REROUTE, onLifecycleEvent = VaadinNavigationPepService.LifecycleType.LEAVE
	)
	static class TargetWithRerouteAndLeaveAnnotation {}

	@OnDenyNavigate(action = "'test action'", resource = "'test resource'", environment = "'test environment'",
			navigation=REROUTE, onLifecycleEvent = VaadinNavigationPepService.LifecycleType.ENTER
	)
	static class TargetWithRerouteAndEnterAnnotation {}

	@Test
	void when_beforeLeaveIsCalled_thenActionIsSetFromAnnotation() {
		//GIVEN
		// mock Livecycle builder
		var beforeLeaveBuilder = mock(VaadinPep.LifecycleBeforeLeavePepBuilder.class);
		when(beforeLeaveBuilder.build()).thenReturn((BeforeLeaveListener) event -> {});

		// mock pep builder
		var pepBuilderService = mock(PepBuilderService.class);
		when(pepBuilderService.getLifecycleBeforeLeavePepBuilder()).thenReturn(beforeLeaveBuilder);

		// authorization subscription builder
		var vaadinAuthorizationSubscriptionBuilderService = mock(VaadinAuthorizationSubscriptionBuilderService.class);
		var vaadinNavigationPepService = new VaadinNavigationPepService(pepBuilderService, vaadinAuthorizationSubscriptionBuilderService);

		// mock event and target class
		BeforeLeaveEvent beforeLeaveEventMock = mock(BeforeLeaveEvent.class);
		//noinspection unchecked
		when(beforeLeaveEventMock.getNavigationTarget()).thenReturn((Class) TargetWithRerouteAndLeaveAnnotation.class);

		//WHEN
		vaadinNavigationPepService.beforeLeave(beforeLeaveEventMock);

		//THEN
		verify(vaadinAuthorizationSubscriptionBuilderService).evaluateExpressionStringToJson("'test action'", null);
	}

	@OnDenyNavigate(action = "'test action'", resource = "'test resource'", environment = "'test environment'",
			navigation=REROUTE, onLifecycleEvent = VaadinNavigationPepService.LifecycleType.BOTH
	)
	static class TargetWithRerouteAndBothAnnotation {}

	@Test
	void when_beforeLeaveIsCalledWithBothAnnotation_thenActionIsSetFromAnnotation() {
		//GIVEN
		// mock Livecycle builder
		var beforeLeaveBuilder = mock(VaadinPep.LifecycleBeforeLeavePepBuilder.class);
		when(beforeLeaveBuilder.build()).thenReturn((BeforeLeaveListener) event -> {});

		// mock pep builder
		var pepBuilderService = mock(PepBuilderService.class);
		when(pepBuilderService.getLifecycleBeforeLeavePepBuilder()).thenReturn(beforeLeaveBuilder);

		// authorization subscription builder
		var vaadinAuthorizationSubscriptionBuilderService = mock(VaadinAuthorizationSubscriptionBuilderService.class);
		var vaadinNavigationPepService = new VaadinNavigationPepService(pepBuilderService, vaadinAuthorizationSubscriptionBuilderService);

		// mock event and target class
		BeforeLeaveEvent beforeLeaveEventMock = mock(BeforeLeaveEvent.class);
		//noinspection unchecked
		when(beforeLeaveEventMock.getNavigationTarget()).thenReturn((Class) TargetWithRerouteAndBothAnnotation.class);

		//WHEN
		vaadinNavigationPepService.beforeLeave(beforeLeaveEventMock);

		//THEN
		verify(vaadinAuthorizationSubscriptionBuilderService).evaluateExpressionStringToJson("'test action'", null);
	}

	@Test
	void when_beforeEnterIsCalledWithBothAnnotation_thenActionIsSetFromAnnotation() {
		//GIVEN
		// mock Livecycle builder
		var beforeEnterBuilder = mock(VaadinPep.LifecycleBeforeEnterPepBuilder.class);
		when(beforeEnterBuilder.build()).thenReturn((BeforeEnterListener) event -> {});

		// mock pep builder
		var pepBuilderService = mock(PepBuilderService.class);
		when(pepBuilderService.getLifecycleBeforeEnterPepBuilder()).thenReturn(beforeEnterBuilder);

		// authorization subscription builder
		var vaadinAuthorizationSubscriptionBuilderService = mock(VaadinAuthorizationSubscriptionBuilderService.class);
		var vaadinNavigationPepService = new VaadinNavigationPepService(pepBuilderService, vaadinAuthorizationSubscriptionBuilderService);

		// mock event and target class
		BeforeEnterEvent beforeEnterEventMock = mock(BeforeEnterEvent.class);
		//noinspection unchecked
		when(beforeEnterEventMock.getNavigationTarget()).thenReturn((Class) TargetWithRerouteAndBothAnnotation.class);

		//WHEN
		vaadinNavigationPepService.beforeEnter(beforeEnterEventMock);

		//THEN
		verify(vaadinAuthorizationSubscriptionBuilderService).evaluateExpressionStringToJson("'test action'", null);
	}

	static class TargetWithoutAnnotation {}

	@Test
	void when_beforeLeaveIsCalledWithoutAnnotation_thenBuildIsNotCalled() {
		//GIVEN
		// mock Livecycle builder
		var beforeLeaveBuilder = mock(VaadinPep.LifecycleBeforeLeavePepBuilder.class);
		when(beforeLeaveBuilder.build()).thenReturn((BeforeLeaveListener) event -> {});

		// mock pep builder
		var pepBuilderService = mock(PepBuilderService.class);
		when(pepBuilderService.getLifecycleBeforeLeavePepBuilder()).thenReturn(beforeLeaveBuilder);

		// authorization subscription builder
		var vaadinAuthorizationSubscriptionBuilderService = mock(VaadinAuthorizationSubscriptionBuilderService.class);
		var vaadinNavigationPepService = new VaadinNavigationPepService(pepBuilderService, vaadinAuthorizationSubscriptionBuilderService);

		// mock event and target class
		BeforeLeaveEvent beforeLeaveEventMock = mock(BeforeLeaveEvent.class);
		//noinspection unchecked
		when(beforeLeaveEventMock.getNavigationTarget()).thenReturn((Class) TargetWithoutAnnotation.class);

		//WHEN
		vaadinNavigationPepService.beforeLeave(beforeLeaveEventMock);

		//THEN
		verify(beforeLeaveBuilder, times(0)).build();
	}

	@Test
	void when_beforeEnterIsCalledWithoutAnnotation_thenBuildIsNotCalled() {
		//GIVEN
		// mock Livecycle builder
		var beforeEnterBuilder = mock(VaadinPep.LifecycleBeforeEnterPepBuilder.class);
		when(beforeEnterBuilder.build()).thenReturn((BeforeEnterListener) event -> {});

		// mock pep builder
		var pepBuilderService = mock(PepBuilderService.class);
		when(pepBuilderService.getLifecycleBeforeEnterPepBuilder()).thenReturn(beforeEnterBuilder);

		// authorization subscription builder
		var vaadinAuthorizationSubscriptionBuilderService = mock(VaadinAuthorizationSubscriptionBuilderService.class);
		var vaadinNavigationPepService = new VaadinNavigationPepService(pepBuilderService, vaadinAuthorizationSubscriptionBuilderService);

		// mock event and target class
		BeforeEnterEvent beforeEnterEventMock = mock(BeforeEnterEvent.class);
		// noinspection rawtypes
		when(beforeEnterEventMock.getNavigationTarget()).thenReturn((Class) TargetWithoutAnnotation.class);

		//WHEN
		vaadinNavigationPepService.beforeEnter(beforeEnterEventMock);

		//THEN
		verify(beforeEnterBuilder, times(0)).build();
	}

	@Test
	void when_beforeLeaveIsCalledOnEnterAnnotation_thenBuildIsNotCalled() {
		//GIVEN
		// mock Livecycle builder
		var beforeLeaveBuilder = mock(VaadinPep.LifecycleBeforeLeavePepBuilder.class);
		when(beforeLeaveBuilder.build()).thenReturn((BeforeLeaveListener) event -> {});

		// mock pep builder
		var pepBuilderService = mock(PepBuilderService.class);
		when(pepBuilderService.getLifecycleBeforeLeavePepBuilder()).thenReturn(beforeLeaveBuilder);

		// authorization subscription builder
		var vaadinAuthorizationSubscriptionBuilderService = mock(VaadinAuthorizationSubscriptionBuilderService.class);
		var vaadinNavigationPepService = new VaadinNavigationPepService(pepBuilderService, vaadinAuthorizationSubscriptionBuilderService);

		// mock event and target class
		BeforeLeaveEvent beforeLeaveEventMock = mock(BeforeLeaveEvent.class);
		//noinspection unchecked
		when(beforeLeaveEventMock.getNavigationTarget()).thenReturn((Class) TargetWithRerouteAndEnterAnnotation.class);

		//WHEN
		vaadinNavigationPepService.beforeLeave(beforeLeaveEventMock);

		//THEN
		verify(beforeLeaveBuilder, times(0)).build();
	}

	@Test
	void when_beforeEnterIsCalledOnEnterAnnotation_thenBuildIsNotCalled() {
		//GIVEN
		// mock Livecycle builder
		var beforeEnterBuilder = mock(VaadinPep.LifecycleBeforeEnterPepBuilder.class);
		when(beforeEnterBuilder.build()).thenReturn((BeforeEnterListener) event -> {});

		// mock pep builder
		var pepBuilderService = mock(PepBuilderService.class);
		when(pepBuilderService.getLifecycleBeforeEnterPepBuilder()).thenReturn(beforeEnterBuilder);

		// authorization subscription builder
		var vaadinAuthorizationSubscriptionBuilderService = mock(VaadinAuthorizationSubscriptionBuilderService.class);
		var vaadinNavigationPepService = new VaadinNavigationPepService(pepBuilderService, vaadinAuthorizationSubscriptionBuilderService);

		// mock event and target class
		BeforeEnterEvent beforeEnterEventMock = mock(BeforeEnterEvent.class);
		// noinspection rawtypes
		when(beforeEnterEventMock.getNavigationTarget()).thenReturn((Class) TargetWithRerouteAndLeaveAnnotation.class);

		//WHEN
		vaadinNavigationPepService.beforeEnter(beforeEnterEventMock);

		//THEN
		verify(beforeEnterBuilder, times(0)).build();
	}

}
