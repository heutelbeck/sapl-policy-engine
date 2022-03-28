package io.sapl.vaadin;

import static io.sapl.api.interpreter.Val.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.vaadin.base.SecurityHelper;

class PepBuilderServiceTests {

	private PolicyDecisionPoint pdp;
	private VaadinConstraintEnforcementService vaadinConstraintEnforcementService;
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

	@BeforeEach
	void setupTest() {
		pdp = mock(PolicyDecisionPoint.class);
		vaadinConstraintEnforcementService = mock(VaadinConstraintEnforcementService.class);
	}
	// *************************************************
	// *************** Integration tests ***************
	// *************************************************
	@Test
	void when_withComponentIsCalled_then_ComponentIsSetInComponentBuilder() {
		// GIVEN
		PepBuilderService pepBuilderService = new PepBuilderService(pdp, vaadinConstraintEnforcementService);
		Component component = mock(Component.class);
		// WHEN + THEN
		assertEquals(component, pepBuilderService.with(component).component);
	}

	@Test
	void when_withButtonIsCalled_then_ButtonIsSetInButtonBuilder() {
		// GIVEN
		PepBuilderService pepBuilderService = new PepBuilderService(pdp, vaadinConstraintEnforcementService);
		Button button = mock(Button.class);
		// WHEN + THEN
		assertEquals(button, pepBuilderService.with(button).component);
	}

	@Test
	void when_withTextFieldIsCalled_then_TextFieldIsSetInTextFieldBuilder() {
		// GIVEN
		PepBuilderService pepBuilderService = new PepBuilderService(pdp, vaadinConstraintEnforcementService);
		TextField textField = mock(TextField.class);
		// WHEN + THEN
		assertEquals(textField, pepBuilderService.with(textField).component);
	}

	@Test
	void when_withCheckboxIsCalled_then_CheckboxIsSetInCheckboxBuilder() {
		// GIVEN
		PepBuilderService pepBuilderService = new PepBuilderService(pdp, vaadinConstraintEnforcementService);
		Checkbox checkbox = mock(Checkbox.class);
		// WHEN + THEN
		assertEquals(checkbox, pepBuilderService.with(checkbox).component);
	}

	@Test
	void when_withSpanIsCalled_then_SpanIsSetInSpanBuilder() {
		// GIVEN
		PepBuilderService pepBuilderService = new PepBuilderService(pdp, vaadinConstraintEnforcementService);
		Span span = mock(Span.class);
		// WHEN + THEN
		assertEquals(span, pepBuilderService.with(span).component);
	}
	

	@Test
	void when_getMultiBuilder_then_returnMultibuilder() {
		// GIVEN
		PepBuilderService pepBuilderService = new PepBuilderService(pdp, vaadinConstraintEnforcementService);
		// WHEN
		MultiBuilder multiBuilder = pepBuilderService.getMultiBuilder();
		//THEN
		assertEquals(MultiBuilder.class, multiBuilder.getClass());
	}

	@Test
	void when_getLifecycleBeforeEnterPepBuilder_then_returnLifecycleBeforeEnterPepBuilder() {
		// GIVEN
		PepBuilderService pepBuilderService = new PepBuilderService(pdp, vaadinConstraintEnforcementService);
		mockSpringContextHolderAuthentication();
		//WHEN
		VaadinPep.LifecycleBeforeEnterPepBuilder lifecycleBeforeEnterPepBuilder = pepBuilderService.getLifecycleBeforeEnterPepBuilder();
		//THEN
		assertEquals(VaadinPep.LifecycleBeforeEnterPepBuilder.class, lifecycleBeforeEnterPepBuilder.getClass());
	}

	@Test
	void when_BeforeEnterBuilderBuild_then_isBuildIsTrue() {
		// GIVEN
		PepBuilderService pepBuilderService = new PepBuilderService(pdp, vaadinConstraintEnforcementService);
		// WHEN
		mockSpringContextHolderAuthentication();
		VaadinPep.LifecycleBeforeEnterPepBuilder lifecycleBeforeEnterPepBuilder = pepBuilderService.getLifecycleBeforeEnterPepBuilder();
		lifecycleBeforeEnterPepBuilder.build();
		//THEN
		assertTrue(lifecycleBeforeEnterPepBuilder.isBuild);
	}
	
	@Test
	void when_BeforeLeaveBuilderBuild_then_isBuildIsTrue() {
		// GIVEN
		PepBuilderService pepBuilderService = new PepBuilderService(pdp, vaadinConstraintEnforcementService);
		// WHEN
		mockSpringContextHolderAuthentication();
		VaadinPep.LifecycleBeforeLeavePepBuilder lifecycleBeforeLeavePepBuilder = pepBuilderService.getLifecycleBeforeLeavePepBuilder();
		lifecycleBeforeLeavePepBuilder.build();
		//THEN
		assertTrue(lifecycleBeforeLeavePepBuilder.isBuild);
	}

	private void mockSpringContextHolderAuthentication() {
		Authentication authentication = Mockito.mock(Authentication.class);
		SecurityContext securityContext = Mockito.mock(SecurityContext.class);
		Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
		SecurityContextHolder.setContext(securityContext);
	}
}
