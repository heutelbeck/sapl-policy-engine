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
package io.sapl.pdp;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.reactivestreams.Publisher;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.IdentifiableAuthorizationSubscription;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.pdp.config.PDPConfiguration;
import io.sapl.pdp.config.PDPConfigurationProvider;
import io.sapl.prp.PolicyRetrievalPoint;
import io.sapl.prp.PolicyRetrievalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@RequiredArgsConstructor
public class EmbeddedPolicyDecisionPoint implements PolicyDecisionPoint {

	private final PDPConfigurationProvider configurationProvider;

	private final PolicyRetrievalPoint policyRetrievalPoint;

	@Override
	public Flux<AuthorizationDecision> decide(AuthorizationSubscription authzSubscription) {
		log.debug("- START DECISION: {}", authzSubscription);
		return configurationProvider.pdpConfiguration().switchMap(decideSubscription(authzSubscription))
				.distinctUntilChanged();
	}

	private Function<? super PDPConfiguration, Publisher<? extends AuthorizationDecision>> decideSubscription(
			AuthorizationSubscription authzSubscription) {
		return pdpConfiguration -> {
			if (pdpConfiguration.isValid()) {
				return Flux.just(pdpConfiguration.getPdpScopedEvaluationContext())
						.map(createSubscriptionScope(authzSubscription))
						.switchMap(retrieveAndCombineDocuments(pdpConfiguration));
			}
			else {
				return Flux.just(AuthorizationDecision.INDETERMINATE);
			}
		};
	}

	private Function<EvaluationContext, Publisher<? extends AuthorizationDecision>> retrieveAndCombineDocuments(
			PDPConfiguration pdpConfiguration) {
		return subscriptionScopedEvaluationContext -> policyRetrievalPoint
				.retrievePolicies(subscriptionScopedEvaluationContext)
				.switchMap(combineDocuments(pdpConfiguration, subscriptionScopedEvaluationContext));
	}

	private Function<? super PolicyRetrievalResult, Publisher<? extends AuthorizationDecision>> combineDocuments(
			PDPConfiguration pdpConfiguration, EvaluationContext subscriptionScopedEvaluationContext) {
		return policyRetrievalResult -> {
			if (policyRetrievalResult.isPrpValidState()) {
				return pdpConfiguration.getDocumentsCombinator().combineMatchingDocuments(policyRetrievalResult,
						subscriptionScopedEvaluationContext);
			}
			else {
				return Flux.just(AuthorizationDecision.INDETERMINATE);
			}
		};
	}

	private Function<? super EvaluationContext, ? extends EvaluationContext> createSubscriptionScope(
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
