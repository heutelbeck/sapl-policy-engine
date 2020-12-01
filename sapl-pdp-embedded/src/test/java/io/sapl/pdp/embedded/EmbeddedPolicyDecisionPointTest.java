/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.pdp.embedded;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.multisubscription.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.multisubscription.MultiAuthorizationSubscription;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

public class EmbeddedPolicyDecisionPointTest {

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private PolicyDecisionPoint pdp;

	@Before
	public void setUp() throws Exception {
		pdp = PolicyDecisionPointFactory.resourcesPolicyDecisionPoint(List.of(new TestPIP()), new ArrayList<>());
	}

	@Test
	public void decide_withEmptyRequest_shouldReturnDeny() {
		AuthorizationSubscription emptyAuthzSubscription = new AuthorizationSubscription(JSON.nullNode(),
				JSON.nullNode(), JSON.nullNode(), JSON.nullNode());
		final Flux<AuthorizationDecision> authzDecisionFlux = pdp.decide(emptyAuthzSubscription);
		StepVerifier.create(authzDecisionFlux)
				.expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.DENY).thenCancel().verify();
	}

	@Test
	public void decide_withAllowedAction_shouldReturnPermit() {
		AuthorizationSubscription simpleAuthzSubscription = new AuthorizationSubscription(JSON.textNode("willi"),
				JSON.textNode("read"), JSON.textNode("something"), JSON.nullNode());
		final Flux<AuthorizationDecision> authzDecisionFlux = pdp.decide(simpleAuthzSubscription);
		StepVerifier.create(authzDecisionFlux)
				.expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.PERMIT).thenCancel()
				.verify();
	}

	@Test
	public void decide_withForbiddenAction_shouldReturnDeny() {
		AuthorizationSubscription simpleAuthzSubscription = new AuthorizationSubscription(JSON.textNode("willi"),
				JSON.textNode("write"), JSON.textNode("something"), JSON.nullNode());
		final Flux<AuthorizationDecision> authzDecisionFlux = pdp.decide(simpleAuthzSubscription);
		StepVerifier.create(authzDecisionFlux)
				.expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.DENY).thenCancel().verify();
	}

	@Test
	public void decide_withEmptyMultiSubscription_shouldReturnIndeterminate() {
		final MultiAuthorizationSubscription multiAuthzSubscription = new MultiAuthorizationSubscription();

		final Flux<IdentifiableAuthorizationDecision> flux = pdp.decide(multiAuthzSubscription);
		StepVerifier.create(flux)
				.expectNextMatches(iad -> iad.getAuthorizationSubscriptionId() == null
						&& iad.getAuthorizationDecision().equals(AuthorizationDecision.INDETERMINATE))
				.thenCancel().verify();
	}

	@Test
	public void decide_withMultiSubscription_shouldReturnDecision() {
		final MultiAuthorizationSubscription multiAuthzSubscription = new MultiAuthorizationSubscription()
				.addAuthorizationSubscription("id", "willi", "read", "something");

		final Flux<IdentifiableAuthorizationDecision> flux = pdp.decide(multiAuthzSubscription);
		StepVerifier.create(flux).expectNextMatches(iad -> iad.getAuthorizationSubscriptionId().equals("id")
				&& iad.getAuthorizationDecision().equals(AuthorizationDecision.PERMIT)).thenCancel().verify();
	}

	@Test
	public void decide_withMultiSubscriptionContainingTwoSubscriptions_shouldReturnTwoDecisions() {
		final MultiAuthorizationSubscription multiAuthzSubscription = new MultiAuthorizationSubscription()
				.addAuthorizationSubscription("id1", "willi", "read", "something")
				.addAuthorizationSubscription("id2", "willi", "write", "something");

		final Flux<IdentifiableAuthorizationDecision> flux = pdp.decide(multiAuthzSubscription);
		StepVerifier.create(flux).expectNextMatches(iad -> {
			if (iad.getAuthorizationSubscriptionId().equals("id1")) {
				return iad.getAuthorizationDecision().equals(AuthorizationDecision.PERMIT);
			} else if (iad.getAuthorizationSubscriptionId().equals("id2")) {
				return iad.getAuthorizationDecision().equals(AuthorizationDecision.DENY);
			} else {
				throw new IllegalStateException("Invalid subscription id: " + iad.getAuthorizationSubscriptionId());
			}
		}).expectNextMatches(iad -> {
			if (iad.getAuthorizationSubscriptionId().equals("id1")) {
				return iad.getAuthorizationDecision().equals(AuthorizationDecision.PERMIT);
			} else if (iad.getAuthorizationSubscriptionId().equals("id2")) {
				return iad.getAuthorizationDecision().equals(AuthorizationDecision.DENY);
			} else {
				throw new IllegalStateException("Invalid subscription id: " + iad.getAuthorizationSubscriptionId());
			}
		}).thenCancel().verify();
	}

}
