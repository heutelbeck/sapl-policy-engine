package io.sapl.pdp.remote;

import static io.sapl.api.pdp.multirequest.IdentifiableAction.READ_ID;
import static io.sapl.api.pdp.multirequest.IdentifiableSubject.AUTHENTICATION_ID;

import java.util.Collection;
import java.util.Collections;

import org.junit.Ignore;
import org.junit.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.api.pdp.multirequest.IdentifiableAction;
import io.sapl.api.pdp.multirequest.IdentifiableResponse;
import io.sapl.api.pdp.multirequest.IdentifiableSubject;
import io.sapl.api.pdp.multirequest.MultiRequest;
import io.sapl.api.pdp.multirequest.RequestElements;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@Ignore // To run these tests, make sure the PDPServerApplication has been started
public class RemotePolicyDecisionPointTest {
	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Test
	public void sendSingleRequest() {
		final Request simpleRequest = new Request(JSON.textNode("willi"), JSON.textNode("test-read"),
				JSON.textNode("something"), JSON.nullNode());
		final RemotePolicyDecisionPoint pdp = new RemotePolicyDecisionPoint("localhost", 8443);
		final Flux<Response> decideFlux = pdp.subscribe(simpleRequest);
		StepVerifier.create(decideFlux).expectNext(Response.permit()).thenCancel().verify();
	}

	@Test
	public void sendMultiRequest() {
		final Collection<GrantedAuthority> authorities = Collections
				.singletonList(new SimpleGrantedAuthority("TESTER"));
		final Authentication authentication = new UsernamePasswordAuthenticationToken("Reactor", null, authorities);

		final MultiRequest multiRequest = new MultiRequest();
		multiRequest.addSubject(new IdentifiableSubject(AUTHENTICATION_ID, authentication));
		multiRequest.addAction(new IdentifiableAction(READ_ID, "test-read"));
		multiRequest.addResource("heartBeatData");
		multiRequest.addResource("bloodPressureData");
		multiRequest.addRequest("requestId_1", new RequestElements(AUTHENTICATION_ID, READ_ID, "heartBeatData"));
		multiRequest.addRequest("requestId_2", new RequestElements(AUTHENTICATION_ID, READ_ID, "bloodPressureData"));

		final RemotePolicyDecisionPoint pdp = new RemotePolicyDecisionPoint("localhost", 8443);
		final Flux<IdentifiableResponse> decideFlux = pdp.subscribe(multiRequest);
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
}
