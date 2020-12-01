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

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.multisubscription.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.multisubscription.IdentifiableAuthorizationSubscription;
import io.sapl.api.pdp.multisubscription.MultiAuthorizationDecision;
import io.sapl.api.pdp.multisubscription.MultiAuthorizationSubscription;
import io.sapl.api.prp.PolicyRetrievalPoint;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.pdp.embedded.config.PDPConfiguration;
import io.sapl.pdp.embedded.config.PDPConfigurationProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Slf4j
@RequiredArgsConstructor
public class EmbeddedPolicyDecisionPoint implements PolicyDecisionPoint {

	private final PDPConfigurationProvider configurationProvider;
	private final PolicyRetrievalPoint policyRetrievalPoint;

	@Override
	public Flux<AuthorizationDecision> decide(AuthorizationSubscription authzSubscription) {
		log.trace("|--------------------------------->");
		log.trace("|-- PDP AuthorizationSubscription: {}", authzSubscription);
		return configurationProvider.pdpConfiguration().switchMap(decideSubscription(authzSubscription))
				.distinctUntilChanged();
	}

	private Function<? super PDPConfiguration, Publisher<? extends AuthorizationDecision>> decideSubscription(
			AuthorizationSubscription authzSubscription) {
		return pdpConfiguration -> Flux.just(pdpConfiguration.getPdpScopedEvaluationContext())
				.map(createSubsctiptionScope(authzSubscription))
				.switchMap(retrieveAndCombineDocuments(pdpConfiguration));
	}

	private Function<EvaluationContext, Publisher<? extends AuthorizationDecision>> retrieveAndCombineDocuments(
			PDPConfiguration pdpConfiguration) {
		return subscriptionScopedEvaluationContext -> policyRetrievalPoint.retrievePolicies(subscriptionScopedEvaluationContext)
				.switchMap(combineDocuments(pdpConfiguration, subscriptionScopedEvaluationContext));
	}

	private Function<? super PolicyRetrievalResult, Publisher<? extends AuthorizationDecision>> combineDocuments(
			PDPConfiguration pdpConfiguration, EvaluationContext subscriptionScopedEvaluationContext) {
		return policyRetrievalResult -> pdpConfiguration.getDocumentsCombinator()
				.combineMatchingDocuments(policyRetrievalResult, subscriptionScopedEvaluationContext);
	}

	private Function<? super EvaluationContext, ? extends EvaluationContext> createSubsctiptionScope(
			AuthorizationSubscription authzSubscription) {
		return pdpScopedEvaluationContext -> pdpScopedEvaluationContext.forAuthorizationSubscription(authzSubscription);
	}

	@Override
	public Flux<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiAuthzSubscription) {
		if (multiAuthzSubscription.hasAuthorizationSubscriptions()) {
			final List<Flux<IdentifiableAuthorizationDecision>> identifiableAuthzDecisionFluxes = createIdentifiableAuthzDecisionFluxes(
					multiAuthzSubscription);
			return Flux.merge(identifiableAuthzDecisionFluxes);
		}
		return Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE);
	}

	@Override
	public Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiAuthzSubscription) {
		if (multiAuthzSubscription.hasAuthorizationSubscriptions()) {
			final List<Flux<IdentifiableAuthorizationDecision>> identifiableAuthzDecisionFluxes = createIdentifiableAuthzDecisionFluxes(
					multiAuthzSubscription);
			return Flux.combineLatest(identifiableAuthzDecisionFluxes, this::collectAuthorizationDecisions);
		}
		return Flux.just(MultiAuthorizationDecision.indeterminate());
	}

	private List<Flux<IdentifiableAuthorizationDecision>> createIdentifiableAuthzDecisionFluxes(
			Iterable<IdentifiableAuthorizationSubscription> multiDecision) {
		final List<Flux<IdentifiableAuthorizationDecision>> identifiableAuthzDecisionFluxes = new ArrayList<>();
		for (IdentifiableAuthorizationSubscription identifiableAuthzSubscription : multiDecision) {
			final String subscriptionId = identifiableAuthzSubscription.getAuthorizationSubscriptionId();
			final AuthorizationSubscription authzSubscription = identifiableAuthzSubscription
					.getAuthorizationSubscription();
			final Flux<IdentifiableAuthorizationDecision> identifiableAuthzDecisionFlux = decide(authzSubscription)
					.map(authzDecision -> new IdentifiableAuthorizationDecision(subscriptionId, authzDecision));
			identifiableAuthzDecisionFluxes.add(identifiableAuthzDecisionFlux);
		}
		return identifiableAuthzDecisionFluxes;
	}

	private MultiAuthorizationDecision collectAuthorizationDecisions(Object[] values) {
		final MultiAuthorizationDecision multiAuthzDecision = new MultiAuthorizationDecision();
		for (Object value : values) {
			IdentifiableAuthorizationDecision ir = (IdentifiableAuthorizationDecision) value;
			multiAuthzDecision.setAuthorizationDecisionForSubscriptionWithId(ir.getAuthorizationSubscriptionId(),
					ir.getAuthorizationDecision());
		}
		return multiAuthzDecision;
	}

	public void dispose() {
		configurationProvider.dispose();
		policyRetrievalPoint.dispose();
	}

}
