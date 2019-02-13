package io.sapl.prp.resources;

import java.io.IOException;
import java.net.URISyntaxException;

import org.junit.Test;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pip.AttributeException;
import io.sapl.pdp.embedded.EmbeddedPolicyDecisionPoint;

public class ResourcesPolicyRetrievalPointTest {

	@Test
	public void loadPolicies()
			throws IOException, URISyntaxException, PolicyEvaluationException, FunctionException, AttributeException {
		EmbeddedPolicyDecisionPoint.builder().withResourcePolicyRetrievalPoint().build();
	}
}
