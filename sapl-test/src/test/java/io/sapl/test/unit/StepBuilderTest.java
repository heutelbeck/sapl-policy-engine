/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
