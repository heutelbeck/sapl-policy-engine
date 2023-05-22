/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.pdp;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.pdp.config.PDPConfiguration;
import io.sapl.pdp.config.PDPConfigurationProvider;
import io.sapl.pdp.config.filesystem.FileSystemVariablesAndCombinatorSource;
import io.sapl.pdp.config.fixed.FixedFunctionsAndAttributesPDPConfigurationProvider;
import io.sapl.prp.PolicyRetrievalPoint;
import io.sapl.prp.PolicyRetrievalResult;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class EmbeddedPolicyDecisionPointTest {

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private PolicyDecisionPoint pdp;

	@BeforeEach
	void setUp() throws Exception {
		pdp = PolicyDecisionPointFactory.resourcesPolicyDecisionPoint(List.of(new TestPIP()), new ArrayList<>());
	}

	@Test
	void decide_withInvalidConfig_shouldReturnIntermediate() {
		var configMock   = mock(PDPConfiguration.class);
		var providerMock = mock(PDPConfigurationProvider.class);
		var prpMock      = mock(PolicyRetrievalPoint.class);
		var embeddedPdp  = new EmbeddedPolicyDecisionPoint(providerMock, prpMock);

		when(providerMock.pdpConfiguration()).thenReturn(Flux.just(configMock));
		when(configMock.isValid()).thenReturn(Boolean.FALSE);

		var empty = new AuthorizationSubscription(JSON.nullNode(), JSON.nullNode(), JSON.nullNode(), JSON.nullNode());

		StepVerifier.create(embeddedPdp.decide(empty))
				.expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.INDETERMINATE).thenCancel()
				.verify();

		embeddedPdp.dispose();

		verify(providerMock, times(1)).dispose();
		verify(prpMock, times(1)).dispose();
	}

	@Test
	void decide_withEmptyRequest_shouldBeNotApplicable() {
		AuthorizationSubscription         emptyAuthzSubscription = new AuthorizationSubscription(JSON.nullNode(),
				JSON.nullNode(), JSON.nullNode(), JSON.nullNode());
		final Flux<AuthorizationDecision> authzDecisionFlux      = pdp.decide(emptyAuthzSubscription);
		StepVerifier.create(authzDecisionFlux)
				.expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.NOT_APPLICABLE).thenCancel()
				.verify();
	}

	@Test
	void decide_withAllowedAction_shouldReturnPermit() {
		AuthorizationSubscription         simpleAuthzSubscription = new AuthorizationSubscription(
				JSON.textNode("willi"), JSON.textNode("read"), JSON.textNode("something"), JSON.nullNode());
		final Flux<AuthorizationDecision> authzDecisionFlux       = pdp.decide(simpleAuthzSubscription);
		StepVerifier.create(authzDecisionFlux)
				.expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.DENY).thenCancel().verify();
	}

	@Test
	void decide_withInvalidPrpState_shouldReturnIntermediate() {
		var prpMock   = mock(PolicyRetrievalPoint.class);
		var prpResult = mock(PolicyRetrievalResult.class);

		var source   = new FileSystemVariablesAndCombinatorSource("src/test/resources/policies");
		var attrCtx  = new AnnotationAttributeContext();
		var funcCtx  = new AnnotationFunctionContext();
		var provider = new FixedFunctionsAndAttributesPDPConfigurationProvider(attrCtx, funcCtx, source, List.of(),
				List.of());

		var embeddedPdp = new EmbeddedPolicyDecisionPoint(provider, prpMock);

		when(prpMock.retrievePolicies()).thenReturn(Flux.just(prpResult));
		when(prpResult.isPrpValidState()).thenReturn(Boolean.FALSE);

		var simpleAuthzSubscription = new AuthorizationSubscription(JSON.textNode("willi"), JSON.textNode("read"),
				JSON.textNode("something"), JSON.nullNode());
		var authzDecisionFlux       = embeddedPdp.decide(simpleAuthzSubscription);
		StepVerifier.create(authzDecisionFlux)
				.expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.INDETERMINATE).thenCancel()
				.verify();
	}

	@Test
	void decide_withForbiddenAction_shouldReturnDeny() {
		AuthorizationSubscription         simpleAuthzSubscription = new AuthorizationSubscription(
				JSON.textNode("willi"), JSON.textNode("write"), JSON.textNode("something"), JSON.nullNode());
		final Flux<AuthorizationDecision> authzDecisionFlux       = pdp.decide(simpleAuthzSubscription);
		StepVerifier.create(authzDecisionFlux)
				.expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.DENY).thenCancel().verify();
	}

	@Test
	void decide_withEmptyMultiSubscription_shouldReturnIndeterminate() {
		final MultiAuthorizationSubscription multiAuthzSubscription = new MultiAuthorizationSubscription();

		final Flux<IdentifiableAuthorizationDecision> flux = pdp.decide(multiAuthzSubscription);
		StepVerifier.create(flux)
				.expectNextMatches(iad -> iad.getAuthorizationSubscriptionId() == null
						&& iad.getAuthorizationDecision().equals(AuthorizationDecision.INDETERMINATE))
				.thenCancel().verify();
	}

	@Test
	void decideAll_withEmptyMultiSubscription_shouldReturnIndeterminate() {
		final MultiAuthorizationSubscription multiAuthzSubscription = new MultiAuthorizationSubscription();

		final Flux<MultiAuthorizationDecision> flux = pdp.decideAll(multiAuthzSubscription);
		StepVerifier.create(flux).expectNextMatches(
				mad -> mad.getAuthorizationDecisionForSubscriptionWithId("").getDecision() == Decision.INDETERMINATE)
				.thenCancel().verify();
	}

	@Test
	void decide_withMultiSubscription_shouldReturnDecision() {
		final MultiAuthorizationSubscription multiAuthzSubscription = new MultiAuthorizationSubscription()
				.addAuthorizationSubscription("id", "willi", "read", "something");

		final Flux<IdentifiableAuthorizationDecision> flux = pdp.decide(multiAuthzSubscription);
		StepVerifier.create(flux).expectNextMatches(iad -> "id".equals(iad.getAuthorizationSubscriptionId())
				&& iad.getAuthorizationDecision().equals(AuthorizationDecision.DENY)).thenCancel().verify();
	}

	@Test
	void decide_withMultiSubscriptionContainingTwoSubscriptions_shouldReturnTwoDecisions() {
		final MultiAuthorizationSubscription multiAuthzSubscription = new MultiAuthorizationSubscription()
				.addAuthorizationSubscription("id1", "willi", "read", "something")
				.addAuthorizationSubscription("id2", "willi", "write", "something");

		final Flux<IdentifiableAuthorizationDecision> flux = pdp.decide(multiAuthzSubscription);
		StepVerifier.create(flux).expectNextMatches(iad -> {
			if ("id1".equals(iad.getAuthorizationSubscriptionId())) {
				return iad.getAuthorizationDecision().equals(AuthorizationDecision.PERMIT);
			} else if ("id2".equals(iad.getAuthorizationSubscriptionId())) {
				return iad.getAuthorizationDecision().equals(AuthorizationDecision.DENY);
			} else {
				throw new IllegalStateException("Invalid subscription id: " + iad.getAuthorizationSubscriptionId());
			}
		}).expectNextMatches(iad -> {
			if ("id1".equals(iad.getAuthorizationSubscriptionId())) {
				return iad.getAuthorizationDecision().equals(AuthorizationDecision.DENY);
			} else if ("id2".equals(iad.getAuthorizationSubscriptionId())) {
				return iad.getAuthorizationDecision().equals(AuthorizationDecision.DENY);
			} else {
				throw new IllegalStateException("Invalid subscription id: " + iad.getAuthorizationSubscriptionId());
			}
		}).thenCancel().verify();
	}

	@Test
	void decideAll_withMultiSubscriptionContainingTwoSubscriptions_shouldReturnTwoDecisions() {
		final MultiAuthorizationSubscription multiAuthzSubscription = new MultiAuthorizationSubscription()
				.addAuthorizationSubscription("id1", "willi", "read", "something")
				.addAuthorizationSubscription("id2", "willi", "write", "something");

		final Flux<MultiAuthorizationDecision> flux = pdp.decideAll(multiAuthzSubscription);
		StepVerifier.create(flux).expectNextMatches(mad -> {
			AuthorizationDecision ad1 = mad.getAuthorizationDecisionForSubscriptionWithId("id1");
			AuthorizationDecision ad2 = mad.getAuthorizationDecisionForSubscriptionWithId("id2");
			if (ad1 == null || ad2 == null)
				return false;

			if (ad1.getDecision() != Decision.DENY)
				return false;
			return ad2.getDecision() == Decision.DENY;
		}).thenCancel().verify();
	}

}
