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
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.server.Command;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.vaadin.base.SecurityHelper;

class VaadinPepEnforceHasValueAndElementTests {

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
	 * Mock class to check EnforceHasValueAndElement interface.
	 */
	static class VaadinPepBuilderHasValueAndElementMock implements
			VaadinPep.EnforceHasValueAndElement<VaadinPepBuilderHasValueAndElementMock, TextField> {
		BiConsumer<AuthorizationDecision, TextField> lastBiConsumer;

		@Override
		public VaadinPepBuilderHasValueAndElementMock onDecisionDo(BiConsumer<AuthorizationDecision, TextField> biConsumer) {
			this.lastBiConsumer = biConsumer;
			return self();
		}

		@Override
		public VaadinPepBuilderHasValueAndElementMock onPermitDo(BiConsumer<AuthorizationDecision, TextField> biConsumer) {
			return onDecisionDo(biConsumer);
		}

		@Override
		public VaadinPepBuilderHasValueAndElementMock onDenyDo(BiConsumer<AuthorizationDecision, TextField> biConsumer) {
			return onDecisionDo(biConsumer);
		}
	}

	@Test
	void when_EnforceHasValueAndElementOnDecisionReadOnlyOrReadWrite_then_ComponentSetReadWrite() {
		// GIVEN
		VaadinPepBuilderHasValueAndElementMock vaadinPepBuilderHasValueAndElementMock = new VaadinPepBuilderHasValueAndElementMock();
		AuthorizationDecision ad = mock(AuthorizationDecision.class);
		when(ad.getDecision()).thenReturn(Decision.PERMIT);
		TextField textField = getTextFieldMockWithUI();

		// WHEN
		vaadinPepBuilderHasValueAndElementMock.onDecisionReadOnlyOrReadWrite();
		vaadinPepBuilderHasValueAndElementMock.lastBiConsumer.accept(ad, textField); // Simulate decision

		// THEN
		verify(textField, times(1)).setReadOnly(false);
	}


	@Test
	void when_EnforceHasValueAndElementOnDecisionReadOnlyOrReadWrite_then_ComponentSetReadOnly() {
		// GIVEN
		VaadinPepBuilderHasValueAndElementMock vaadinPepBuilderHasValueAndElementMock = new VaadinPepBuilderHasValueAndElementMock();
		AuthorizationDecision ad = mock(AuthorizationDecision.class);
		when(ad.getDecision()).thenReturn(Decision.DENY);
		TextField textField = getTextFieldMockWithUI();

		// WHEN
		vaadinPepBuilderHasValueAndElementMock.onDecisionReadOnlyOrReadWrite();
		vaadinPepBuilderHasValueAndElementMock.lastBiConsumer.accept(ad, textField); // Simulate decision

		// THEN
		verify(textField, times(1)).setReadOnly(true);
	}

	TextField getTextFieldMockWithUI() {
		TextField textField = mock(TextField.class);
		UI ui = mock(UI.class);

		// Mock UI access() function to immediately call the lambda that is passed to it
		when(ui.access(any(Command.class))).thenAnswer(invocation -> {
			invocation.getArgument(0, Command.class).execute();
			return null;
		});
		Optional<UI> o = Optional.of(ui);
		when(textField.isAttached()).thenReturn(true);
		when(textField.getUI()).thenReturn(o);
		return textField;
	}
}
