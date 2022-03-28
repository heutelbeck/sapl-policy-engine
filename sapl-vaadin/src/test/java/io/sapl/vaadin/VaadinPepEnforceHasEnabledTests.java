package io.sapl.vaadin;

import static io.sapl.api.interpreter.Val.JSON;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.server.Command;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.vaadin.base.SecurityHelper;

class VaadinPepEnforceHasEnabledTests {

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

	/**
	 * Mock class to check EnforceHasEnabled interface.
	 */
	static class VaadinPepBuilderMock implements
			VaadinPep.EnforceHasEnabled<VaadinPepBuilderMock, Component>
	{
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
		AuthorizationDecision ad = mock(AuthorizationDecision.class);
		when(ad.getDecision()).thenReturn(Decision.PERMIT);
		Button button = getButtonMockWithUI();

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
		AuthorizationDecision ad = mock(AuthorizationDecision.class);
		when(ad.getDecision()).thenReturn(Decision.DENY);
		Button button = getButtonMockWithUI();

		// WHEN
		vaadinPepBuilderMock.onDecisionEnableOrDisable();
		vaadinPepBuilderMock.lastBiConsumer.accept(ad, button); // Simulate decision

		// THEN
		verify(button).setEnabled(false);
	}

	Button getButtonMockWithUI() {
		Button button = mock(Button.class);
		UI ui = mock(UI.class);

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
