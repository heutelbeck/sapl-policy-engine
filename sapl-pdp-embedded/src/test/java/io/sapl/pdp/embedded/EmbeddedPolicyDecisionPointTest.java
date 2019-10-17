package io.sapl.pdp.embedded;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthDecision;
import io.sapl.api.pdp.AuthSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.multisubscription.IdentifiableAuthDecision;
import io.sapl.api.pdp.multisubscription.MultiAuthSubscription;
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
		AuthSubscription emptyAuthSubscription = new AuthSubscription(JSON.nullNode(), JSON.nullNode(), JSON.nullNode(),
				JSON.nullNode());
		final Flux<AuthDecision> authDecisionFlux = pdp.decide(emptyAuthSubscription);
		StepVerifier.create(authDecisionFlux)
				.expectNextMatches(authDecision -> authDecision.getDecision() == Decision.DENY).thenCancel().verify();
	}

	@Test
	public void decide_withAllowedAction_shouldReturnPermit() {
		AuthSubscription simpleAuthSubscription = new AuthSubscription(JSON.textNode("willi"), JSON.textNode("read"),
				JSON.textNode("something"), JSON.nullNode());
		final Flux<AuthDecision> authDecisionFlux = pdp.decide(simpleAuthSubscription);
		StepVerifier.create(authDecisionFlux)
				.expectNextMatches(authDecision -> authDecision.getDecision() == Decision.PERMIT).thenCancel().verify();
	}

	@Test
	public void decide_withForbiddenAction_shouldReturnDeny() {
		AuthSubscription simpleAuthSubscription = new AuthSubscription(JSON.textNode("willi"), JSON.textNode("write"),
				JSON.textNode("something"), JSON.nullNode());
		final Flux<AuthDecision> authDecisionFlux = pdp.decide(simpleAuthSubscription);
		StepVerifier.create(authDecisionFlux)
				.expectNextMatches(authDecision -> authDecision.getDecision() == Decision.DENY).thenCancel().verify();
	}

	@Test
	public void decide_withEmptyMultiSubscription_shouldReturnIndeterminate() {
		final MultiAuthSubscription multiAuthSubscription = new MultiAuthSubscription();

		final Flux<IdentifiableAuthDecision> flux = pdp.decide(multiAuthSubscription);
		StepVerifier.create(flux).expectNextMatches(
				iad -> iad.getAuthSubscriptionId() == null && iad.getAuthDecision().equals(AuthDecision.INDETERMINATE))
				.thenCancel().verify();
	}

	@Test
	public void decide_withMultiSubscription_shouldReturnDecision() {
		final MultiAuthSubscription multiAuthSubscription = new MultiAuthSubscription().addAuthSubscription("id",
				"willi", "read", "something");

		final Flux<IdentifiableAuthDecision> flux = pdp.decide(multiAuthSubscription);
		StepVerifier.create(flux).expectNextMatches(
				iad -> iad.getAuthSubscriptionId().equals("id") && iad.getAuthDecision().equals(AuthDecision.PERMIT))
				.thenCancel().verify();
	}

	@Test
	public void decide_withMultiSubscriptionContainingTwoSubscriptions_shouldReturnTwoDecisions() {
		final MultiAuthSubscription multiAuthSubscription = new MultiAuthSubscription()
				.addAuthSubscription("id1", "willi", "read", "something")
				.addAuthSubscription("id2", "willi", "write", "something");

		final Flux<IdentifiableAuthDecision> flux = pdp.decide(multiAuthSubscription);
		StepVerifier.create(flux).expectNextMatches(iad -> {
			if (iad.getAuthSubscriptionId().equals("id1")) {
				return iad.getAuthDecision().equals(AuthDecision.PERMIT);
			}
			else if (iad.getAuthSubscriptionId().equals("id2")) {
				return iad.getAuthDecision().equals(AuthDecision.DENY);
			}
			else {
				throw new IllegalStateException("Invalid subscription id: " + iad.getAuthSubscriptionId());
			}
		}).expectNextMatches(iad -> {
			if (iad.getAuthSubscriptionId().equals("id1")) {
				return iad.getAuthDecision().equals(AuthDecision.PERMIT);
			}
			else if (iad.getAuthSubscriptionId().equals("id2")) {
				return iad.getAuthDecision().equals(AuthDecision.DENY);
			}
			else {
				throw new IllegalStateException("Invalid subscription id: " + iad.getAuthSubscriptionId());
			}
		}).thenCancel().verify();
	}

}
