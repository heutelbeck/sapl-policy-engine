package io.sapl.pdp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.interpreter.InitializationException;

class PolicyDecisionPointFactoryTest {

	@Test
	void test_factory_methods() throws InitializationException {
		assertThat(PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(), notNullValue());
		assertThat(PolicyDecisionPointFactory.filesystemPolicyDecisionPoint("src/main/resources/policies"),
				notNullValue());
		assertThat(PolicyDecisionPointFactory.filesystemPolicyDecisionPoint("src/main/resources/policies",
				Collections.singletonList(new TestPIP()), Collections.emptyList()), notNullValue());

		assertThat(PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(Collections.singletonList(new TestPIP()),
				Collections.singletonList(new FilterFunctionLibrary())), notNullValue());

		assertThat(PolicyDecisionPointFactory.resourcesPolicyDecisionPoint(), notNullValue());
		assertThat(PolicyDecisionPointFactory.resourcesPolicyDecisionPoint(Collections.singletonList(new TestPIP()),
				Collections.emptyList()), notNullValue());
	}

}
