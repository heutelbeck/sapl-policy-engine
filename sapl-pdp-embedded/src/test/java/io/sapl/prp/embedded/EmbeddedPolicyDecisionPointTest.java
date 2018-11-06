package io.sapl.prp.embedded;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Response;
import io.sapl.pdp.embedded.EmbeddedPolicyDecisionPoint;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class EmbeddedPolicyDecisionPointTest {

	private EmbeddedPolicyDecisionPoint pdp;

	@Before
	public void setUp() throws Exception {
		pdp = new EmbeddedPolicyDecisionPoint();
	}

	@Test
	public void decide_withAllowedAction_shouldReturnPermit() {
		final Response response = pdp.decide("willi", "read", "something");
		assertThat("Wrong decision for allowed action.", response.getDecision(), is(Decision.PERMIT));
	}

	@Test
	public void decide_withForbiddenAction_shouldReturnDeny() {
		final Response response = pdp.decide("willi", "write", "something");
		assertThat("Wrong decision for forbidden action.", response.getDecision(), is(Decision.DENY));
	}

	@Test
	public void reactiveDecide_withAllowedAction_shouldReturnPermit() {
		final Flux<Response> response = pdp.reactiveDecide("willi", "read", "something");
		StepVerifier.create(response)
				.expectNextMatches(resp -> resp.getDecision() == Decision.PERMIT)
				// activate the next line, run the test and change the action in target/test-classes/policies/policy_1.sapl
				// from read to write
//				.expectNextMatches(resp -> resp.getDecision() == Decision.DENY)
				.thenCancel()
				.verify();
	}

	@Test
	public void reactiveDecide_withForbiddenAction_shouldReturnDeny() {
		final Flux<Response> response = pdp.reactiveDecide("willi", "write", "something");
		StepVerifier.create(response)
				.expectNextMatches(resp -> resp.getDecision() == Decision.DENY)
				.thenCancel()
				.verify();
	}
}
