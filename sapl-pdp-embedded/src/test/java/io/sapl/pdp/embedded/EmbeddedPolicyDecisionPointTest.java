package io.sapl.pdp.embedded;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.api.pdp.multirequest.IdentifiableResponse;
import io.sapl.api.pdp.multirequest.MultiRequest;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class EmbeddedPolicyDecisionPointTest {

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private EmbeddedPolicyDecisionPoint pdp;

	@Before
	public void setUp() throws Exception {
		pdp = EmbeddedPolicyDecisionPoint.builder().withResourcePDPConfigurationProvider()
				.withResourcePolicyRetrievalPoint().withPolicyInformationPoint(new TestPIP()).build();
	}

	@Test
	public void decide_withEmptyRequest_shouldReturnDeny() {
		Request emptyRequest = new Request(JSON.nullNode(), JSON.nullNode(), JSON.nullNode(), JSON.nullNode());
		final Flux<Response> responseFlux = pdp.decide(emptyRequest);
		StepVerifier.create(responseFlux).expectNextMatches(response -> response.getDecision() == Decision.DENY)
				.thenCancel().verify();
	}

	@Test
	public void decide_withAllowedAction_shouldReturnPermit() {
		Request simpleRequest = new Request(JSON.textNode("willi"), JSON.textNode("read"), JSON.textNode("something"),
				JSON.nullNode());
		final Flux<Response> responseFlux = pdp.decide(simpleRequest);
		StepVerifier.create(responseFlux).expectNextMatches(response -> response.getDecision() == Decision.PERMIT)
				.thenCancel().verify();
	}

	@Test
	public void decide_withForbiddenAction_shouldReturnDeny() {
		Request simpleRequest = new Request(JSON.textNode("willi"), JSON.textNode("write"), JSON.textNode("something"),
				JSON.nullNode());
		final Flux<Response> responseFlux = pdp.decide(simpleRequest);
		StepVerifier.create(responseFlux).expectNextMatches(response -> response.getDecision() == Decision.DENY)
				.thenCancel().verify();
	}

	@Test
	public void decide_withEmptyMultiRequest_shouldReturnIndeterminateResponse() {
		final MultiRequest multiRequest = new MultiRequest();

		final Flux<IdentifiableResponse> flux = pdp.decide(multiRequest);
		StepVerifier.create(flux).expectNextMatches(
				response -> response.getRequestId() == null && response.getResponse().equals(Response.INDETERMINATE))
				.thenCancel().verify();
	}

	@Test
	public void decide_withMultiRequest_shouldReturnResponse() {
		final MultiRequest multiRequest = new MultiRequest().addRequest("req", "willi", "read", "something");

		final Flux<IdentifiableResponse> flux = pdp.decide(multiRequest);
		StepVerifier.create(flux).expectNextMatches(
				response -> response.getRequestId().equals("req") && response.getResponse().equals(Response.PERMIT))
				.thenCancel().verify();
	}

	@Test
	public void decide_withMultiRequestContainingTwoRequests_shouldReturnTwoResponses() {
		final MultiRequest multiRequest = new MultiRequest().addRequest("req1", "willi", "read", "something")
				.addRequest("req2", "willi", "write", "something");

		final Flux<IdentifiableResponse> flux = pdp.decide(multiRequest);
		StepVerifier.create(flux).expectNextMatches(response -> {
			if (response.getRequestId().equals("req1")) {
				return response.getResponse().equals(Response.PERMIT);
			}
			else if (response.getRequestId().equals("req2")) {
				return response.getResponse().equals(Response.DENY);
			}
			else {
				throw new IllegalStateException("Invalid request id: " + response.getRequestId());
			}
		}).expectNextMatches(response -> {
			if (response.getRequestId().equals("req1")) {
				return response.getResponse().equals(Response.PERMIT);
			}
			else if (response.getRequestId().equals("req2")) {
				return response.getResponse().equals(Response.DENY);
			}
			else {
				throw new IllegalStateException("Invalid request id: " + response.getRequestId());
			}
		}).thenCancel().verify();
	}

}
