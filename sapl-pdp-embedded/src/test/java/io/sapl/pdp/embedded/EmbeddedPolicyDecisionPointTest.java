package io.sapl.pdp.embedded;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Response;
import io.sapl.api.pdp.multirequest.IdentifiableAction;
import io.sapl.api.pdp.multirequest.IdentifiableResource;
import io.sapl.api.pdp.multirequest.IdentifiableResponse;
import io.sapl.api.pdp.multirequest.IdentifiableSubject;
import io.sapl.api.pdp.multirequest.MultiRequest;
import io.sapl.api.pdp.multirequest.MultiResponse;
import io.sapl.api.pdp.multirequest.RequestElements;
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
	public void decide_withEmptyRequest_shouldReturnDeny() {
		final Response response = pdp.decide(null, null, null);
		assertThat("Wrong decision for allowed action.", response.getDecision(), is(Decision.DENY));
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
	public void multiDecide_withoutRequest_shouldReturnOneIndeterminateResponse() {
		final MultiRequest multiRequest = new MultiRequest();

		final MultiResponse multiResponse = pdp.multiDecide(multiRequest);

		assertThat("Wrong number of responses", multiResponse.size(), is(1));
		assertThat("Wrong response", multiResponse.getResponseForRequestWithId(""), is(Response.indeterminate()));
	}

	@Test
	public void multiDecide_withOneRequest_shouldReturnOneResponse() {
		final MultiRequest multiRequest = new MultiRequest();
		multiRequest.addSubject(new IdentifiableSubject("sub", "willi"));
		multiRequest.addAction(new IdentifiableAction("act", "read"));
		multiRequest.addResource(new IdentifiableResource("res", "something"));
		multiRequest.addRequest("req", new RequestElements("sub", "act", "res"));

		final MultiResponse multiResponse = pdp.multiDecide(multiRequest);

		assertThat("Wrong number of responses", multiResponse.size(), is(1));
		assertThat("Wrong response", multiResponse.getResponseForRequestWithId("req"), is(Response.permit()));
	}

	@Test
	public void multiDecide_withTwoRequests_shouldReturnTwoResponses() {
		final MultiRequest multiRequest = new MultiRequest();
		multiRequest.addSubject(new IdentifiableSubject("sub", "willi"));
		multiRequest.addAction(new IdentifiableAction("act1", "read"));
		multiRequest.addAction(new IdentifiableAction("act2", "write"));
		multiRequest.addResource(new IdentifiableResource("res", "something"));
		multiRequest.addRequest("req1", new RequestElements("sub", "act1", "res"));
		multiRequest.addRequest("req2", new RequestElements("sub", "act2", "res"));

		final MultiResponse multiResponse = pdp.multiDecide(multiRequest);

		assertThat("Wrong number of responses", multiResponse.size(), is(2));
		assertThat("Wrong response for request 1", multiResponse.getResponseForRequestWithId("req1"), is(Response.permit()));
		assertThat("Wrong response for request 2", multiResponse.getResponseForRequestWithId("req2"), is(Response.deny()));
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

	@Test
	public void reactiveMultiDecide_withEmptyRequest_shouldReturnOneIndeterminateResponse() {
		final MultiRequest multiRequest = new MultiRequest();

		final Flux<IdentifiableResponse> responseFlux = pdp.reactiveMultiDecide(multiRequest);

		StepVerifier.create(responseFlux)
				.expectNextMatches(pair -> pair.getResponse().getDecision() == Decision.INDETERMINATE)
				.verifyComplete();
	}

	@Test
	public void reactiveMultiDecide_withOneRequest_shouldReturnOneResponse() {
		final MultiRequest multiRequest = new MultiRequest();
		multiRequest.addSubject(new IdentifiableSubject("sub", "willi"));
		multiRequest.addAction(new IdentifiableAction("act", "read"));
		multiRequest.addResource(new IdentifiableResource("res", "something"));
		multiRequest.addRequest("req", new RequestElements("sub", "act", "res"));

		final Flux<IdentifiableResponse> responseFlux = pdp.reactiveMultiDecide(multiRequest);

		StepVerifier.create(responseFlux)
				.expectNextMatches(pair -> pair.getRequestId().equals("req") && pair.getResponse().getDecision() == Decision.PERMIT)
				.thenCancel()
				.verify();
	}

	@Test
	public void reactiveMultiDecide_withTwoRequests_shouldReturnTwoResponses() {
		final MultiRequest multiRequest = new MultiRequest();
		multiRequest.addSubject(new IdentifiableSubject("sub", "willi"));
		multiRequest.addAction(new IdentifiableAction("act1", "read"));
		multiRequest.addAction(new IdentifiableAction("act2", "write"));
		multiRequest.addResource(new IdentifiableResource("res", "something"));
		multiRequest.addRequest("req1", new RequestElements("sub", "act1", "res"));
		multiRequest.addRequest("req2", new RequestElements("sub", "act2", "res"));

		final Flux<IdentifiableResponse> responseFlux = pdp.reactiveMultiDecide(multiRequest);

        StepVerifier.create(responseFlux)
				.expectNextMatches(pair -> {
					final String requestId = pair.getRequestId();
					final Response response = pair.getResponse();
					return response.getDecision() == (requestId.equals("req1") ? Decision.PERMIT : Decision.DENY);
				})
				.expectNextMatches(pair -> {
					final String requestId = pair.getRequestId();
					final Response response = pair.getResponse();
					return response.getDecision() == (requestId.equals("req1") ? Decision.PERMIT : Decision.DENY);
				})
                .thenCancel()
				.verify();
	}
}
