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
