package io.sapl.vaadin;

import static io.sapl.api.interpreter.Val.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.server.Command;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.api.ConsumerConstraintHandlerProvider;
import io.sapl.spring.constraints.api.RunnableConstraintHandlerProvider;
import io.sapl.vaadin.base.SecurityHelper;
import io.sapl.vaadin.constraint.VaadinFunctionConstraintHandlerProvider;

class VaadinPepBuilderTests {

	private static MockedStatic<SecurityHelper> securityHelperMock;

	@BeforeAll
	static void beforeAll() {
		var subject = JSON.objectNode();
		subject.put("username", "dummy");
		securityHelperMock = mockStatic(SecurityHelper.class);
		securityHelperMock.when(SecurityHelper::getSubject).thenReturn(subject);
	}

	@AfterAll
	static void afterAll() {
		securityHelperMock.close();
	}

	@Test
	void when_VaadinPepBuilderAddVaadinFunctionConstraintHandlerProvidersIsCalled_then_VaadinFunctionConstraintHandlerListIsExpanded() {
		// GIVEN
		@SuppressWarnings("unchecked")
		VaadinPep.VaadinPepBuilder<Object, Component> vaadinPepBuilder = (VaadinPep.VaadinPepBuilder<Object, Component>)mock(
				VaadinPep.VaadinPepBuilder.class,
				withSettings()
						// useConstructor() -> calls actual constructor which will create a VaadinPep object
						.useConstructor(
								mock(PolicyDecisionPoint.class),
								mock(VaadinConstraintEnforcementService.class),
								mock(Component.class)
						)
						// CALLS_REAL_METHODS needed for the onDecisionDo() call, to fill decisionListenerList
						.defaultAnswer(CALLS_REAL_METHODS)
		);
		VaadinFunctionConstraintHandlerProvider vaadinFunctionConstraintHandlerProvider = mock(VaadinFunctionConstraintHandlerProvider.class);
		// WHEN
		vaadinPepBuilder.addVaadinFunctionConstraintHandlerProvider(vaadinFunctionConstraintHandlerProvider);

		// THEN
		assertEquals(1, vaadinPepBuilder.vaadinPep.getLocalVaadinFunctionProvider().size());
		if (vaadinPepBuilder.vaadinPep.getLocalVaadinFunctionProvider().size() > 0)
			assertEquals(vaadinFunctionConstraintHandlerProvider, vaadinPepBuilder.vaadinPep.getLocalVaadinFunctionProvider().get(0));
	}

	@Test
	void when_VaadinPepBuilderAddConsumerConstraintHandlerProvidersIsCalled_then_ConsumerConstraintHandlerProvidersListIsExpanded() {
		// GIVEN
		@SuppressWarnings("unchecked")
		VaadinPep.VaadinPepBuilder<Object, Component> vaadinPepBuilder = (VaadinPep.VaadinPepBuilder<Object, Component>)mock(
				VaadinPep.VaadinPepBuilder.class,
				withSettings()
						// useConstructor() -> calls actual constructor which will create a VaadinPep object
						.useConstructor(
								mock(PolicyDecisionPoint.class),
								mock(VaadinConstraintEnforcementService.class),
								mock(Component.class)
						)
						// CALLS_REAL_METHODS needed for the onDecisionDo() call, to fill decisionListenerList
						.defaultAnswer(CALLS_REAL_METHODS)
		);
		@SuppressWarnings("unchecked")
		ConsumerConstraintHandlerProvider<UI> consumerConstraintHandlerProvider =
				(ConsumerConstraintHandlerProvider<UI>)mock(ConsumerConstraintHandlerProvider.class);
		// WHEN
		vaadinPepBuilder.addConsumerConstraintHandlerProvider(consumerConstraintHandlerProvider);

		// THEN
		assertEquals(1, vaadinPepBuilder.vaadinPep.getLocalConsumerProviders().size());
		if (vaadinPepBuilder.vaadinPep.getLocalConsumerProviders().size() > 0)
			assertEquals(consumerConstraintHandlerProvider, vaadinPepBuilder.vaadinPep.getLocalConsumerProviders().get(0));
	}

	@Test
	void when_VaadinPepBuilderAddRunnableConstraintHandlerProvidersIsCalled_then_RunnableConstraintHandlerProvidersListIsExpanded() {
		// GIVEN
		@SuppressWarnings("unchecked")
		VaadinPep.VaadinPepBuilder<Object, Component> vaadinPepBuilder = (VaadinPep.VaadinPepBuilder<Object, Component>)mock(
				VaadinPep.VaadinPepBuilder.class,
				withSettings()
						// useConstructor() -> calls actual constructor which will create a VaadinPep object
						.useConstructor(
								mock(PolicyDecisionPoint.class),
								mock(VaadinConstraintEnforcementService.class),
								mock(Component.class)
						)
						.defaultAnswer(CALLS_REAL_METHODS)
		);
		RunnableConstraintHandlerProvider runnableConstraintHandlerProvider = mock(RunnableConstraintHandlerProvider.class);
		// WHEN
		vaadinPepBuilder.addRunnableConstraintHandlerProvider(runnableConstraintHandlerProvider);

		// THEN
		assertEquals(1, vaadinPepBuilder.vaadinPep.getLocalRunnableProviders().size());
		if (vaadinPepBuilder.vaadinPep.getLocalRunnableProviders().size() > 0)
			assertEquals(runnableConstraintHandlerProvider, vaadinPepBuilder.vaadinPep.getLocalRunnableProviders().get(0));
	}

	@Test
	void when_VaadinPepBuilderAddConstraintHandlerIsCalled_then_ConsumerConstraintHandlerProvidersListIsExpanded() {
		// GIVEN
		@SuppressWarnings("unchecked")
		VaadinPep.VaadinPepBuilder<Object, Component> vaadinPepBuilder = (VaadinPep.VaadinPepBuilder<Object, Component>)mock(
				VaadinPep.VaadinPepBuilder.class,
				withSettings()
						// useConstructor() -> calls actual constructor which will create a VaadinPep object
						.useConstructor(
								mock(PolicyDecisionPoint.class),
								mock(VaadinConstraintEnforcementService.class),
								mock(Component.class)
						)
						// CALLS_REAL_METHODS needed for the onDecisionDo() call, to fill decisionListenerList
						.defaultAnswer(CALLS_REAL_METHODS)
		);
		@SuppressWarnings("unchecked")
		Predicate<JsonNode> predicate = (Predicate<JsonNode>)mock(Predicate.class);
		@SuppressWarnings("unchecked")
		Function<JsonNode, Consumer<UI>> getHandler = (Function<JsonNode, Consumer<UI>>)mock(Function.class);
		JsonNode constraint = mock(JsonNode.class);

		// WHEN1
		vaadinPepBuilder.addConstraintHandler(predicate, getHandler);

		// THEN1
		List<ConsumerConstraintHandlerProvider<UI>> localConsumerProviders = vaadinPepBuilder.vaadinPep.getLocalConsumerProviders();
		assertEquals(1, localConsumerProviders.size());
		if (localConsumerProviders.size() > 0) {
			// WHEN2
			assertEquals(UI.class, localConsumerProviders.get(0).getSupportedType());
			localConsumerProviders.get(0).isResponsible(constraint);
			localConsumerProviders.get(0).getHandler(constraint);

			// THEN2
			verify(predicate).test(constraint);
			verify(getHandler).apply(constraint);
		}
	}

	@Test
	void when_VaadinPepBuilderOnDenyNotifyIsCalledAndDenyOccurs_then_NotificationShowIsCalled() {
		// GIVEN
		Component component = getComponentMockWithUI();
		@SuppressWarnings("unchecked")
		VaadinPep.VaadinPepBuilder<Object, Component> vaadinPepBuilder = (VaadinPep.VaadinPepBuilder<Object, Component>)mock(
				VaadinPep.VaadinPepBuilder.class,
				withSettings()
						// useConstructor() -> calls actual constructor which will create a VaadinPep object
						.useConstructor(
								mock(PolicyDecisionPoint.class),
								mock(VaadinConstraintEnforcementService.class),
								component
						)
						.defaultAnswer(CALLS_REAL_METHODS)
		);
		String notificationMessage = "message";
		Notification notification = mock(Notification.class);
		MockedStatic<Notification> notificationMock = mockStatic(Notification.class); // Static mock for "Notification.show()"
		notificationMock.when(() -> Notification.show(notificationMessage)).thenReturn(notification); // On show() return a notification mock

		// WHEN
		vaadinPepBuilder.onDenyNotify(notificationMessage);
		vaadinPepBuilder.vaadinPep.handleDecision(AuthorizationDecision.DENY); // Simulate decision

		// THEN
		notificationMock.verify(() -> Notification.show(notificationMessage));

		notificationMock.close();
	}

	@Test
	void when_VaadinPepBuilderOnDenyNotifyIsCalledAndPermitOccurs_then_NotificationShowIsNotCalled() {
		// GIVEN
		Component component = getComponentMockWithUI();
		@SuppressWarnings("unchecked")
		VaadinPep.VaadinPepBuilder<Object, Component> vaadinPepBuilder = (VaadinPep.VaadinPepBuilder<Object, Component>)mock(
				VaadinPep.VaadinPepBuilder.class,
				withSettings()
						// useConstructor() -> calls actual constructor which will create a VaadinPep object
						.useConstructor(
								mock(PolicyDecisionPoint.class),
								mock(VaadinConstraintEnforcementService.class),
								component
						)
						.defaultAnswer(CALLS_REAL_METHODS)
		);
		String notificationMessage = "message";
		Notification notification = mock(Notification.class);
		MockedStatic<Notification> notificationMock = mockStatic(Notification.class); // Static mock for "Notification.show()"
		notificationMock.when(() -> Notification.show(notificationMessage)).thenReturn(notification); // On show() return a notification mock

		// WHEN
		vaadinPepBuilder.onDenyNotify(notificationMessage);
		vaadinPepBuilder.vaadinPep.handleDecision(AuthorizationDecision.PERMIT); // Simulate decision

		// THEN
		notificationMock.verify(() -> Notification.show(any(String.class)), times(0));

		notificationMock.close();
	}

	@Test
	void when_VaadinPepBuilderOnPermitNotifyIsCalledAndPermitOccurs_then_NotificationShowIsCalled() {
		// GIVEN
		Component component = getComponentMockWithUI();
		@SuppressWarnings("unchecked")
		VaadinPep.VaadinPepBuilder<Object, Component> vaadinPepBuilder = (VaadinPep.VaadinPepBuilder<Object, Component>)mock(
				VaadinPep.VaadinPepBuilder.class,
				withSettings()
						// useConstructor() -> calls actual constructor which will create a VaadinPep object
						.useConstructor(
								mock(PolicyDecisionPoint.class),
								mock(VaadinConstraintEnforcementService.class),
								component
						)
						.defaultAnswer(CALLS_REAL_METHODS)
		);
		String notificationMessage = "message";
		Notification notification = mock(Notification.class);
		MockedStatic<Notification> notificationMock = mockStatic(Notification.class); // Static mock for "Notification.show()"
		notificationMock.when(() -> Notification.show(notificationMessage)).thenReturn(notification); // On show() return a notification mock

		// WHEN
		vaadinPepBuilder.onPermitNotify(notificationMessage);
		vaadinPepBuilder.vaadinPep.handleDecision(AuthorizationDecision.PERMIT); // Simulate decision

		// THEN
		notificationMock.verify(() -> Notification.show(any(String.class)));

		notificationMock.close();
	}

	@Test
	void when_VaadinPepBuilderOnPermitNotifyIsCalledAndDenyOccurs_then_NotificationShowIsNotCalled() {
		// GIVEN
		Component component = getComponentMockWithUI();
		@SuppressWarnings("unchecked")
		VaadinPep.VaadinPepBuilder<Object, Component> vaadinPepBuilder = (VaadinPep.VaadinPepBuilder<Object, Component>)mock(
				VaadinPep.VaadinPepBuilder.class,
				withSettings()
						// useConstructor() -> calls actual constructor which will create a VaadinPep object
						.useConstructor(
								mock(PolicyDecisionPoint.class),
								mock(VaadinConstraintEnforcementService.class),
								component
						)
						.defaultAnswer(CALLS_REAL_METHODS)
		);
		String notificationMessage = "message";
		Notification notification = mock(Notification.class);
		MockedStatic<Notification> notificationMock = mockStatic(Notification.class); // Static mock for "Notification.show()"
		notificationMock.when(() -> Notification.show(notificationMessage)).thenReturn(notification); // On show() return a notification mock

		// WHEN
		vaadinPepBuilder.onPermitNotify(notificationMessage);
		vaadinPepBuilder.vaadinPep.handleDecision(AuthorizationDecision.DENY); // Simulate decision

		// THEN
		notificationMock.verify(() -> Notification.show(any(String.class)), times(0));

		notificationMock.close();
	}

	Component getComponentMockWithUI() {
		Component component = mock(Component.class);
		UI ui = mock(UI.class);

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
