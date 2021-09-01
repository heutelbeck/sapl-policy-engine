package io.sapl.test.unit;

import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;

public class StepBuilderTest {

	@Test
	void test_NotApplicableDecisionWhenNotMatchingPolicyInUnitTest() {
		DefaultSAPLInterpreter interpreter = new DefaultSAPLInterpreter();
		SAPL document = interpreter.parse("policy \"test\" permit action == \"read\"");

		AuthorizationSubscription authzSub = AuthorizationSubscription.of("willi", "not_matching", "something");
		
		StepBuilder.newBuilderAtWhenStep(document, 
				new AnnotationAttributeContext(), 
				new AnnotationFunctionContext(), 
				new HashMap<>())
			.when(authzSub)
			.expectNotApplicable()
			.verify();
		
		
	}
	
	@Test
	void test_matchResultNotBoolean() {
		SAPL document = Mockito.mock(SAPL.class);
		Mockito.when(document.matches(Mockito.any())).thenReturn(Val.errorMono("test"));

		AuthorizationSubscription authzSub = AuthorizationSubscription.of("willi", "not_matching", "something");
		
		StepBuilder.newBuilderAtWhenStep(document, 
				new AnnotationAttributeContext(), 
				new AnnotationFunctionContext(), 
				new HashMap<>())
			.when(authzSub)
			.expectNotApplicable()
			.verify();
		
		
	}

}
