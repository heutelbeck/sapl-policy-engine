/**
 * Copyright © 2020 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.multisubscription.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.multisubscription.MultiAuthorizationSubscription;
import io.sapl.api.pdp.multisubscription.MultiAuthorizationDecision;
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
		final AuthorizationSubscription simpleAuthzSubscription = new AuthorizationSubscription(JSON.textNode("willi"),
				JSON.textNode("test-read"), JSON.textNode("something"), JSON.nullNode());
		final RemotePolicyDecisionPoint pdp = new RemotePolicyDecisionPoint(host, port, clientKey, clientSecret);
		final Flux<AuthorizationDecision> decideFlux = pdp.decide(simpleAuthzSubscription);
		StepVerifier.create(decideFlux).expectNext(AuthorizationDecision.PERMIT).thenCancel().verify();
	}

	@Test
	public void sendMultiSubscriptionReceiveSeparately() {
		final Collection<GrantedAuthority> authorities = Collections
				.singletonList(new SimpleGrantedAuthority("TESTER"));
		final Authentication authentication = new UsernamePasswordAuthenticationToken("Reactor", null, authorities);

		final MultiAuthorizationSubscription multiAuthzSubscription = new MultiAuthorizationSubscription()
				.addAuthorizationSubscription("subscriptionId_1", authentication, "test-read", "heartBeatData")
				.addAuthorizationSubscription("subscriptionId_2", authentication, "test-read", "bloodPressureData");

		final RemotePolicyDecisionPoint pdp = new RemotePolicyDecisionPoint(host, port, clientKey, clientSecret);
		final Flux<IdentifiableAuthorizationDecision> decideFlux = pdp.decide(multiAuthzSubscription);
		StepVerifier.create(decideFlux).expectNextMatches(iad -> {
			if (iad.getAuthorizationSubscriptionId().equals("subscriptionId_1")) {
				return iad.getAuthorizationDecision().equals(AuthorizationDecision.PERMIT);
			}
			else if (iad.getAuthorizationSubscriptionId().equals("subscriptionId_2")) {
				return iad.getAuthorizationDecision().equals(AuthorizationDecision.DENY);
			}
			else {
				throw new IllegalStateException("Invalid subscription id: " + iad.getAuthorizationSubscriptionId());
			}
		}).expectNextMatches(iad -> {
			if (iad.getAuthorizationSubscriptionId().equals("subscriptionId_1")) {
				return iad.getAuthorizationDecision().equals(AuthorizationDecision.PERMIT);
			}
			else if (iad.getAuthorizationSubscriptionId().equals("subscriptionId_2")) {
				return iad.getAuthorizationDecision().equals(AuthorizationDecision.DENY);
			}
			else {
				throw new IllegalStateException("Invalid subscription id: " + iad.getAuthorizationSubscriptionId());
			}
		}).thenCancel().verify();
	}

	@Test
	public void sendMultiSubscriptionReceiveAll() {
		final Collection<GrantedAuthority> authorities = Collections
				.singletonList(new SimpleGrantedAuthority("TESTER"));
		final Authentication authentication = new UsernamePasswordAuthenticationToken("Reactor", null, authorities);

		final MultiAuthorizationSubscription multiAuthzSubscription = new MultiAuthorizationSubscription()
				.addAuthorizationSubscription("subscriptionId_1", authentication, "test-read", "heartBeatData")
				.addAuthorizationSubscription("subscriptionId_2", authentication, "test-read", "bloodPressureData");

		final RemotePolicyDecisionPoint pdp = new RemotePolicyDecisionPoint(host, port, clientKey, clientSecret);
		final Flux<MultiAuthorizationDecision> multiDecisionFlux = pdp.decideAll(multiAuthzSubscription);
		StepVerifier.create(multiDecisionFlux).expectNextMatches(
				multiAuthzDecision -> multiAuthzDecision.isAccessPermittedForSubscriptionWithId("subscriptionId_1")
						&& multiAuthzDecision.getDecisionForSubscriptionWithId("subscriptionId_2") == Decision.DENY)
				.thenCancel().verify();
	}

}
