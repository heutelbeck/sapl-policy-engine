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

import io.sapl.api.pdp.AuthDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.AuthSubscription;
import io.sapl.api.pdp.multisubscription.IdentifiableAuthDecision;
import io.sapl.api.pdp.multisubscription.MultiAuthSubscription;
import io.sapl.api.pdp.multisubscription.MultiAuthDecision;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * To run these tests, save src/test/resources/policies/test_policies.sapl under
 * ~/sapl/policies/ and start the PDPServerApplication (in sapl-pdp-server).
 */
@Ignore
public class RemotePolicyDecisionPointTest {

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private final String host = "localhost";

	private final int port = 8443;

	private final String clientKey = "YJidgyT2mfdkbmL";

	private final String clientSecret = "Fa4zvYQdiwHZVXh";

	@Test
	public void sendSingleSubscription() {
		final AuthSubscription simpleAuthSubscription = new AuthSubscription(JSON.textNode("willi"),
				JSON.textNode("test-read"), JSON.textNode("something"), JSON.nullNode());
		final RemotePolicyDecisionPoint pdp = new RemotePolicyDecisionPoint(host, port, clientKey, clientSecret);
		final Flux<AuthDecision> decideFlux = pdp.decide(simpleAuthSubscription);
		StepVerifier.create(decideFlux).expectNext(AuthDecision.PERMIT).thenCancel().verify();
	}

	@Test
	public void sendMultiSubscriptionReceiveSeparately() {
		final Collection<GrantedAuthority> authorities = Collections
				.singletonList(new SimpleGrantedAuthority("TESTER"));
		final Authentication authentication = new UsernamePasswordAuthenticationToken("Reactor", null, authorities);

		final MultiAuthSubscription multiAuthSubscription = new MultiAuthSubscription()
				.addAuthSubscription("subscriptionId_1", authentication, "test-read", "heartBeatData")
				.addAuthSubscription("subscriptionId_2", authentication, "test-read", "bloodPressureData");

		final RemotePolicyDecisionPoint pdp = new RemotePolicyDecisionPoint(host, port, clientKey, clientSecret);
		final Flux<IdentifiableAuthDecision> decideFlux = pdp.decide(multiAuthSubscription);
		StepVerifier.create(decideFlux).expectNextMatches(iad -> {
			if (iad.getAuthSubscriptionId().equals("subscriptionId_1")) {
				return iad.getAuthDecision().equals(AuthDecision.PERMIT);
			}
			else if (iad.getAuthSubscriptionId().equals("subscriptionId_2")) {
				return iad.getAuthDecision().equals(AuthDecision.DENY);
			}
			else {
				throw new IllegalStateException("Invalid subscription id: " + iad.getAuthSubscriptionId());
			}
		}).expectNextMatches(iad -> {
			if (iad.getAuthSubscriptionId().equals("subscriptionId_1")) {
				return iad.getAuthDecision().equals(AuthDecision.PERMIT);
			}
			else if (iad.getAuthSubscriptionId().equals("subscriptionId_2")) {
				return iad.getAuthDecision().equals(AuthDecision.DENY);
			}
			else {
				throw new IllegalStateException("Invalid subscription id: " + iad.getAuthSubscriptionId());
			}
		}).thenCancel().verify();
	}

	@Test
	public void sendMultiSubscriptionReceiveAll() {
		final Collection<GrantedAuthority> authorities = Collections
				.singletonList(new SimpleGrantedAuthority("TESTER"));
		final Authentication authentication = new UsernamePasswordAuthenticationToken("Reactor", null, authorities);

		final MultiAuthSubscription multiAuthSubscription = new MultiAuthSubscription()
				.addAuthSubscription("subscriptionId_1", authentication, "test-read", "heartBeatData")
				.addAuthSubscription("subscriptionId_2", authentication, "test-read", "bloodPressureData");

		final RemotePolicyDecisionPoint pdp = new RemotePolicyDecisionPoint(host, port, clientKey, clientSecret);
		final Flux<MultiAuthDecision> multiDecisionFlux = pdp.decideAll(multiAuthSubscription);
		StepVerifier.create(multiDecisionFlux).expectNextMatches(
				multiAuthDecision -> multiAuthDecision.isAccessPermittedForSubscriptionWithId("subscriptionId_1")
						&& multiAuthDecision.getDecisionForSubscriptionWithId("subscriptionId_2") == Decision.DENY)
				.thenCancel().verify();
	}

}
