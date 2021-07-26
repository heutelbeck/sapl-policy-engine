package io.sapl.grammar.ide;

import java.util.List;

import org.eclipse.xtext.testing.TestCompletionConfiguration;
import org.junit.jupiter.api.Test;

public class VariableCompletionTests extends CompletionTests {
	@Test
	public void testCompletion_SuggestVariableInBody() {
		testCompletion((TestCompletionConfiguration it) -> {
			String policy = "policy \"test\" permit where var foo = 5; var bar = 6; ";
			it.setModel(policy);
			it.setColumn(policy.length());
			List<String> expected = List.of("advice", "obligation", "transform", "var", "action", "bar", "environment",
					"foo", "resource", "subject");
			it.setExpectedCompletionItems(createCompletionString(expected, it));
		});
	}

	@Test
	public void testCompletion_SuggestVariableInBodyAfterSubject() {
		testCompletion((TestCompletionConfiguration it) -> {
			String policy = "policy \"test\" permit where var foo = 5; var bar = 6; subject.attribute == ";
			it.setModel(policy);
			it.setColumn(policy.length());
			List<String> expected = List.of("action", "bar", "environment", "foo", "resource", "subject");
			it.setExpectedCompletionItems(createCompletionString(expected, it));
		});
	}

	@Test
	public void testCompletion_SuggestVariableInBody_NotSuggestOutOfScopeVariable() {
		testCompletion((TestCompletionConfiguration it) -> {
			String policy = "policy \"test\" permit where var foo = 5; ; var bar = 6;";
			String cursor = "policy \"test\" permit where var foo = 5; ";
			it.setModel(policy);
			it.setColumn(cursor.length());
			List<String> expected = List.of("advice", "obligation", "transform", "var", "action", "environment", "foo",
					"resource", "subject");
			it.setExpectedCompletionItems(createCompletionString(expected, it));
		});
	}

	@Test
	public void testCompletion_SuggestVariableInBodyAfterSubject_NotSuggestOutOfScopeVariable() {
		testCompletion((TestCompletionConfiguration it) -> {
			String policy = "policy \"test\" permit where var foo = 5; subject.attribute == ; var bar = 6;";
			String cursor = "policy \"test\" permit where var foo = 5; subject.attribute == ";
			it.setModel(policy);
			it.setColumn(cursor.length());
			List<String> expected = List.of("action", "environment", "foo", "resource", "subject");
			it.setExpectedCompletionItems(createCompletionString(expected, it));
		});
	}
}
