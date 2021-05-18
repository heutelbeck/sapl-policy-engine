package io.sapl.test.unit;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

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

		AuthorizationSubscription authSub = AuthorizationSubscription.of("willi", "not_matching", "something");
		
		StepBuilder.newBuilderAtWhenStep(document, 
				new AnnotationAttributeContext(), 
				new AnnotationFunctionContext(), 
				new HashMap<>())
			.when(authSub)
			.expectNotApplicable()
			.verify();
		
		
	}

}
