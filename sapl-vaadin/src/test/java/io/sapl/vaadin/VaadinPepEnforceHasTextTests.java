package io.sapl.vaadin;

import static io.sapl.api.interpreter.Val.JSON;
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
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.server.Command;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.vaadin.base.SecurityHelper;

class VaadinPepEnforceHasTextTests {

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
	 * Mock class to check EnforceHasText interface.
	 */
	static class VaadinPepBuilderHasTextMock implements
			VaadinPep.EnforceHasText<VaadinPepBuilderHasTextMock, Button> {
		BiConsumer<AuthorizationDecision, Button> lastBiConsumer;

		@Override
		public VaadinPepBuilderHasTextMock onDecisionDo(BiConsumer<AuthorizationDecision, Button> biConsumer) {
			this.lastBiConsumer = biConsumer;
			return self();
		}

		@Override
		public VaadinPepBuilderHasTextMock onPermitDo(BiConsumer<AuthorizationDecision, Button> biConsumer) {
			return onDecisionDo((authznDecision, component)->{
				if ( authznDecision.getDecision() == Decision.PERMIT ){
					biConsumer.accept(authznDecision, component);
				}
			});
		}

		@Override
		public VaadinPepBuilderHasTextMock onDenyDo(BiConsumer<AuthorizationDecision, Button> biConsumer) {
			return onDecisionDo((authznDecision, component)->{
				if ( authznDecision.getDecision() == Decision.DENY ){
					biConsumer.accept(authznDecision, component);
				}
			});
		}
	}

	@Test
	void when_EnforceHasTextOnDecisionSetTextWithPermit_then_ComponentSetTextIsCalledWithPermitText() {
		// GIVEN
		VaadinPepBuilderHasTextMock vaadinPepBuilderHasTextMock = new VaadinPepBuilderHasTextMock();
		AuthorizationDecision ad = mock(AuthorizationDecision.class);
		when(ad.getDecision()).thenReturn(Decision.PERMIT);
		Button button = getButtonMockWithUI();
		String permitText = "permit";

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
		AuthorizationDecision ad = mock(AuthorizationDecision.class);
		when(ad.getDecision()).thenReturn(Decision.DENY);
		Button button = getButtonMockWithUI();
		String denyText = "deny";

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
		AuthorizationDecision ad = mock(AuthorizationDecision.class);
		when(ad.getDecision()).thenReturn(Decision.PERMIT);
		Button button = getButtonMockWithUI();
		String permitText = "permit";

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
		AuthorizationDecision ad = mock(AuthorizationDecision.class);
		when(ad.getDecision()).thenReturn(Decision.DENY);
		Button button = getButtonMockWithUI();
		String permitText = "permit";

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
		AuthorizationDecision ad = mock(AuthorizationDecision.class);
		when(ad.getDecision()).thenReturn(Decision.DENY);
		Button button = getButtonMockWithUI();
		String denyText = "deny";

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
		AuthorizationDecision ad = mock(AuthorizationDecision.class);
		when(ad.getDecision()).thenReturn(Decision.PERMIT);
		Button button = getButtonMockWithUI();
		String denyText = "deny";

		// WHEN
		vaadinPepBuilderHasTextMock.onDenySetText(denyText);
		vaadinPepBuilderHasTextMock.lastBiConsumer.accept(ad, button); // Simulate decision

		// THEN
		verify(button, times(0)).setText(denyText);
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
