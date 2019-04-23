package io.sapl.pdp.remote;

import java.util.Collection;
import java.util.Collections;

import org.junit.Ignore;
import org.junit.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.api.pdp.multirequest.IdentifiableResponse;
import io.sapl.api.pdp.multirequest.MultiRequest;
import io.sapl.api.pdp.multirequest.MultiResponse;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * To run these tests, save src/test/resources/policies/test_policies.sapl under ~/sapl/policies/
 * and start the PDPServerApplication (in sapl-pdp-server).
 */
@Ignore
public class RemotePolicyDecisionPointTest {

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private final String host = "localhost";
	private final int port = 8443;
	private final String clientKey = "YJidgyT2mfdkbmL";
	private final String clientSecret = "Fa4zvYQdiwHZVXh";

	@Test
	public void sendSingleRequest() {
		final Request simpleRequest = new Request(JSON.textNode("willi"), JSON.textNode("test-read"),
				JSON.textNode("something"), JSON.nullNode());
		final RemotePolicyDecisionPoint pdp = new RemotePolicyDecisionPoint(host, port, clientKey, clientSecret);
		final Flux<Response> decideFlux = pdp.decide(simpleRequest);
		StepVerifier.create(decideFlux).expectNext(Response.permit()).thenCancel().verify();
	}

	@Test
	public void sendMultiRequestReceiveSeparately() {
		final Collection<GrantedAuthority> authorities = Collections
				.singletonList(new SimpleGrantedAuthority("TESTER"));
		final Authentication authentication = new UsernamePasswordAuthenticationToken("Reactor", null, authorities);

		final MultiRequest multiRequest = new MultiRequest()
				.addRequest("requestId_1", authentication, "test-read", "heartBeatData")
				.addRequest("requestId_2", authentication, "test-read", "bloodPressureData");

		final RemotePolicyDecisionPoint pdp = new RemotePolicyDecisionPoint(host, port, clientKey, clientSecret);
		final Flux<IdentifiableResponse> decideFlux = pdp.decide(multiRequest);
		StepVerifier.create(decideFlux).expectNextMatches(response -> {
			if (response.getRequestId().equals("requestId_1")) {
				return response.getResponse().equals(Response.permit());
			} else if (response.getRequestId().equals("requestId_2")) {
				return response.getResponse().equals(Response.deny());
			} else {
				throw new IllegalStateException("Invalid request id: " + response.getRequestId());
			}
		}).expectNextMatches(response -> {
			if (response.getRequestId().equals("requestId_1")) {
				return response.getResponse().equals(Response.permit());
			} else if (response.getRequestId().equals("requestId_2")) {
				return response.getResponse().equals(Response.deny());
			} else {
				throw new IllegalStateException("Invalid request id: " + response.getRequestId());
			}
		}).thenCancel().verify();
	}

	@Test
	public void sendMultiRequestReceiveAll() {
		final Collection<GrantedAuthority> authorities = Collections
				.singletonList(new SimpleGrantedAuthority("TESTER"));
		final Authentication authentication = new UsernamePasswordAuthenticationToken("Reactor", null, authorities);

		final MultiRequest multiRequest = new MultiRequest()
				.addRequest("requestId_1", authentication, "test-read", "heartBeatData")
				.addRequest("requestId_2", authentication, "test-read", "bloodPressureData");

		final RemotePolicyDecisionPoint pdp = new RemotePolicyDecisionPoint(host, port, clientKey, clientSecret);
		final Flux<MultiResponse> multiResponseFlux = pdp.decideAll(multiRequest);
		StepVerifier.create(multiResponseFlux).expectNextMatches(response ->
				response.isAccessPermittedForRequestWithId("requestId_1") &&
				response.getDecisionForRequestWithId("requestId_2") == Decision.DENY)
		.thenCancel().verify();
	}
}
