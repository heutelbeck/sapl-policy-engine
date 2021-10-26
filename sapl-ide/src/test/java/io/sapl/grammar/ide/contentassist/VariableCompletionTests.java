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
package io.sapl.grammar.ide.contentassist;

import java.util.List;

import org.eclipse.xtext.testing.TestCompletionConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Tests regarding the auto completion of variables
 */
public class VariableCompletionTests extends CompletionTests {
	@Test
	public void testCompletion_SuggestVariableInBody() {
		testCompletion((TestCompletionConfiguration it) -> {
			String policy = "policy \"test\" permit where var foo = 5; var bar = 6; ";
			it.setModel(policy);
			it.setColumn(policy.length());
			it.setAssertCompletionList(completionList -> {
				var expected = List.of("advice", "obligation", "transform", "var", "action", "bar", "environment",
						"foo", "resource", "subject");
				assertProposalsSimple(expected, completionList);
			});
		});
	}

	@Test
	public void testCompletion_SuggestVariableInBodyAfterSubject() {
		testCompletion((TestCompletionConfiguration it) -> {
			String policy = "policy \"test\" permit where var foo = 5; var bar = 6; subject.attribute == ";
			it.setModel(policy);
			it.setColumn(policy.length());
			it.setAssertCompletionList(completionList -> {
				var expected = List.of("action", "bar", "environment", "foo", "resource", "subject");
				assertProposalsSimple(expected, completionList);
			});
		});
	}

	@Test
	public void testCompletion_SuggestVariableInBody_NotSuggestOutOfScopeVariable() {
		testCompletion((TestCompletionConfiguration it) -> {
			String policy = "policy \"test\" permit where var foo = 5; ; var bar = 6;";
			String cursor = "policy \"test\" permit where var foo = 5; ";
			it.setModel(policy);
			it.setColumn(cursor.length());
			it.setAssertCompletionList(completionList -> {
				var expected = List.of("advice", "obligation", "transform", "var", "action", "environment", "foo",
						"resource", "subject");
				assertProposalsSimple(expected, completionList);
			});
		});
	}

	@Test
	public void testCompletion_SuggestVariableInBodyAfterSubject_NotSuggestOutOfScopeVariable() {
		testCompletion((TestCompletionConfiguration it) -> {
			String policy = "policy \"test\" permit where var foo = 5; subject.attribute == ; var bar = 6;";
			String cursor = "policy \"test\" permit where var foo = 5; subject.attribute == ";
			it.setModel(policy);
			it.setColumn(cursor.length());
			it.setAssertCompletionList(completionList -> {
				var expected = List.of("action", "environment", "foo", "resource", "subject");
				assertProposalsSimple(expected, completionList);
			});
		});
	}
}
