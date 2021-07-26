/**
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
package io.sapl.grammar.ide;

import java.util.List;

import org.eclipse.xtext.testing.TestCompletionConfiguration;
import org.junit.jupiter.api.Test;

public class AuthorizationSubscriptionItemsCompletionTests extends CompletionTests {
	@Test
	public void testCompletion_AuthorizationSubscriptionItemsInTargetExpression() {
		testCompletion((TestCompletionConfiguration it) -> {
			String policy = "policy \"test\" permit ";
			it.setModel(policy);
			it.setColumn(policy.length());
			List<String> expected = List.of("advice", "obligation", "transform", "where", "action", "environment",
					"resource", "subject");
			it.setExpectedCompletionItems(createCompletionString(expected, it));
		});
	}

	@Test
	public void testCompletion_AuthorizationSubscriptionItemsInBody() {
		testCompletion((TestCompletionConfiguration it) -> {
			String policy = "policy \"test\" permit where ";
			it.setModel(policy);
			it.setColumn(policy.length());
			List<String> expected = List.of("var", "action", "environment", "resource", "subject");
			it.setExpectedCompletionItems(createCompletionString(expected, it));
		});
	}
}
