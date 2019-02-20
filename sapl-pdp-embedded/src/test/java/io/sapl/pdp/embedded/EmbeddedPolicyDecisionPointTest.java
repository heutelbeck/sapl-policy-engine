package io.sapl.pdp.embedded;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.api.pdp.multirequest.IdentifiableAction;
import io.sapl.api.pdp.multirequest.IdentifiableResource;
import io.sapl.api.pdp.multirequest.IdentifiableResponse;
import io.sapl.api.pdp.multirequest.IdentifiableSubject;
import io.sapl.api.pdp.multirequest.MultiRequest;
import io.sapl.api.pdp.multirequest.RequestElements;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class EmbeddedPolicyDecisionPointTest {

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private EmbeddedPolicyDecisionPoint pdp;

	@Before
	public void setUp() throws Exception {
		pdp = EmbeddedPolicyDecisionPoint.builder().withFilesystemPolicyRetrievalPoint("src/test/resources/policies")
				.withPolicyInformationPoint(new TestPIP()).build();
	}

	@After
	public void cleanUp() throws Exception {
		pdp.dispose();
	}

	@Test
	public void decide_withEmptyRequest_shouldReturnDeny() {
		Request emptyRequest = new Request(JSON.nullNode(), JSON.nullNode(), JSON.nullNode(), JSON.nullNode());
		final Flux<Response> responseFlux = pdp.subscribe(emptyRequest);
		StepVerifier.create(responseFlux).expectNextMatches(response -> response.getDecision() == Decision.DENY)
				.thenCancel().verify();
	}

	@Test
	public void decide_withAllowedAction_shouldReturnPermit() {
		Request simpleRequest = new Request(JSON.textNode("willi"), JSON.textNode("read"), JSON.textNode("something"),
				JSON.nullNode());
		final Flux<Response> responseFlux = pdp.subscribe(simpleRequest).log();
		StepVerifier.create(responseFlux).expectNextMatches(response -> response.getDecision() == Decision.PERMIT)
				.thenCancel().verify();
	}

	@Test
	public void decide_withForbiddenAction_shouldReturnDeny() {
		Request simpleRequest = new Request(JSON.textNode("willi"), JSON.textNode("write"), JSON.textNode("something"),
				JSON.nullNode());
		final Flux<Response> responseFlux = pdp.subscribe(simpleRequest).log();
		StepVerifier.create(responseFlux).expectNextMatches(response -> response.getDecision() == Decision.DENY)
				.thenCancel().verify();
	}

	@Test
	public void decide_withEmptyMultiRequest_shouldReturnIndeterminateResponse() {
		final MultiRequest multiRequest = new MultiRequest();

		final Flux<IdentifiableResponse> flux = pdp.subscribe(multiRequest);
		StepVerifier.create(flux).expectNextMatches(
				response -> response.getRequestId() == null && response.getResponse().equals(Response.indeterminate()))
				.thenCancel().verify();
	}

	@Test
	public void decide_withMultiRequest_shouldReturnResponse() {
		final MultiRequest multiRequest = new MultiRequest();
		multiRequest.addSubject(new IdentifiableSubject("sub", "willi"));
		multiRequest.addAction(new IdentifiableAction("act", "read"));
		multiRequest.addResource(new IdentifiableResource("res", "something"));
		multiRequest.addRequest("req", new RequestElements("sub", "act", "res"));

		final Flux<IdentifiableResponse> flux = pdp.subscribe(multiRequest);
		StepVerifier.create(flux).expectNextMatches(
				response -> response.getRequestId().equals("req") && response.getResponse().equals(Response.permit()))
				.thenCancel().verify();
	}

	@Test
	public void decide_withMultiRequestContainingTwoRequests_shouldReturnTwoResponses() {
		final MultiRequest multiRequest = new MultiRequest();
		multiRequest.addSubject(new IdentifiableSubject("sub", "willi"));
		multiRequest.addAction(new IdentifiableAction("act1", "read"));
		multiRequest.addAction(new IdentifiableAction("act2", "write"));
		multiRequest.addResource(new IdentifiableResource("res", "something"));
		multiRequest.addRequest("req1", new RequestElements("sub", "act1", "res"));
		multiRequest.addRequest("req2", new RequestElements("sub", "act2", "res"));

		final Flux<IdentifiableResponse> flux = pdp.subscribe(multiRequest);
		StepVerifier.create(flux).expectNextMatches(response -> {
			if (response.getRequestId().equals("req1")) {
				return response.getResponse().equals(Response.permit());
			} else if (response.getRequestId().equals("req2")) {
				return response.getResponse().equals(Response.deny());
			} else {
				throw new IllegalStateException("Invalid request id: " + response.getRequestId());
			}
		}).expectNextMatches(response -> {
			if (response.getRequestId().equals("req1")) {
				return response.getResponse().equals(Response.permit());
			} else if (response.getRequestId().equals("req2")) {
				return response.getResponse().equals(Response.deny());
			} else {
				throw new IllegalStateException("Invalid request id: " + response.getRequestId());
			}
		}).thenCancel().verify();
	}
}
