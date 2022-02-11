/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import io.sapl.grammar.sapl.CombiningAlgorithm;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.pdp.config.PDPConfiguration;
import io.sapl.pdp.config.PDPConfigurationProvider;
import io.sapl.prp.PolicyRetrievalPoint;
import io.sapl.prp.PolicyRetrievalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

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
				return retrieveAndCombineDocuments(pdpConfiguration.getDocumentsCombinator())
						.contextWrite(buildSubscriptionScopedContext(pdpConfiguration, authzSubscription));
			} else {
				return Flux.just(AuthorizationDecision.INDETERMINATE);
			}
		};
	}

	private Function<Context, Context> buildSubscriptionScopedContext(
			PDPConfiguration pdpConfiguration,
			AuthorizationSubscription authzSubscription) {
		return ctx -> {
			ctx = AuthorizationContext.setAttributeContext(ctx, pdpConfiguration.getAttributeContext());
			ctx = AuthorizationContext.setFunctionContext(ctx, pdpConfiguration.getFunctionContext());
			ctx = AuthorizationContext.setVariables(ctx, pdpConfiguration.getVariables());
			ctx = AuthorizationContext.setSubscriptionVariables(ctx, authzSubscription);
			return ctx;
		};
	}

	private Flux<AuthorizationDecision> retrieveAndCombineDocuments(CombiningAlgorithm documentsCombinator) {
		return policyRetrievalPoint.retrievePolicies().switchMap(combineDocuments(documentsCombinator));
	}

	private Function<? super PolicyRetrievalResult, Publisher<? extends AuthorizationDecision>> combineDocuments(
			CombiningAlgorithm documentsCombinator) {
		return policyRetrievalResult -> {
			if (policyRetrievalResult.isPrpValidState())
				return documentsCombinator.combineMatchingDocuments(policyRetrievalResult);

			return Flux.just(AuthorizationDecision.INDETERMINATE);
		};
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
			final String                                  subscriptionId                = identifiableAuthzSubscription
					.getAuthorizationSubscriptionId();
			final AuthorizationSubscription               authzSubscription             = identifiableAuthzSubscription
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
