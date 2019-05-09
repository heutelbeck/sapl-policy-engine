package io.sapl.prp.resources;

import java.io.IOException;
import java.net.URISyntaxException;

import org.junit.Test;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pip.AttributeException;
import io.sapl.pdp.embedded.EmbeddedPolicyDecisionPoint;
import io.sapl.api.pdp.PDPConfigurationException;

public class ResourcesPolicyRetrievalPointTest {

	@Test
	public void loadPolicies() throws IOException, URISyntaxException, FunctionException,
			AttributeException, PolicyEvaluationException, PDPConfigurationException {
		EmbeddedPolicyDecisionPoint.builder().withResourcePDPConfigurationProvider()
				.withResourcePolicyRetrievalPoint().build();
	}

}
