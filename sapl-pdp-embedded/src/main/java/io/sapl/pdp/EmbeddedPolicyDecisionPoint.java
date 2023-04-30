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
import io.sapl.api.pdp.TracedDecision;
import io.sapl.grammar.sapl.CombiningAlgorithm;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.CombinedDecision;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.pdp.config.PDPConfiguration;
import io.sapl.pdp.config.PDPConfigurationProvider;
import io.sapl.prp.PolicyRetrievalPoint;
import io.sapl.prp.PolicyRetrievalResult;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

@RequiredArgsConstructor
public class EmbeddedPolicyDecisionPoint implements PolicyDecisionPoint {

	private final PDPConfigurationProvider configurationProvider;
	private final PolicyRetrievalPoint     policyRetrievalPoint;

	@Override
	public Flux<AuthorizationDecision> decide(AuthorizationSubscription authzSubscription) {
		return decideTraced(authzSubscription).map(TracedDecision::getAuthorizationDecision);
	}

	public Flux<TracedDecision> decideTraced(AuthorizationSubscription authzSubscription) {
		return configurationProvider.pdpConfiguration().switchMap(decideSubscription(authzSubscription))
				.distinctUntilChanged();
	}

	private Function<? super PDPConfiguration, Publisher<? extends TracedDecision>> decideSubscription(
			AuthorizationSubscription authzSubscription) {
		return pdpConfiguration -> {
			var combiningAlgorithm = pdpConfiguration.getDocumentsCombinator();
			if (pdpConfiguration.isValid()) {
				var subscription = pdpConfiguration.getSubscriptionInterceptorChain().apply(authzSubscription);
				return retrieveAndCombineDocuments(pdpConfiguration.getDocumentsCombinator(), subscription)
						.map(pdpConfiguration.getDecisionInterceptorChain())
						.contextWrite(buildSubscriptionScopedContext(pdpConfiguration, authzSubscription));
			} else {
				var decision = CombinedDecision.error(
						combiningAlgorithm == null ? "Misconfigured PDP." : combiningAlgorithm.getName(),
						"PDP In Invalid State.");
				return Flux.just(PDPDecision.of(authzSubscription, decision));
			}
		};
	}

	private Function<Context, Context> buildSubscriptionScopedContext(PDPConfiguration pdpConfiguration,
			AuthorizationSubscription authzSubscription) {
		return ctx -> {
			ctx = AuthorizationContext.setAttributeContext(ctx, pdpConfiguration.getAttributeContext());
			ctx = AuthorizationContext.setFunctionContext(ctx, pdpConfiguration.getFunctionContext());
			ctx = AuthorizationContext.setVariables(ctx, pdpConfiguration.getVariables());
			ctx = AuthorizationContext.setSubscriptionVariables(ctx, authzSubscription);
			return ctx;
		};
	}

	private Flux<PDPDecision> retrieveAndCombineDocuments(CombiningAlgorithm documentsCombinator,
			AuthorizationSubscription authzSubscription) {
		return policyRetrievalPoint.retrievePolicies()
				.switchMap(combineDocuments(documentsCombinator, authzSubscription));
	}

	private Function<? super PolicyRetrievalResult, Publisher<? extends PDPDecision>> combineDocuments(
			CombiningAlgorithm documentsCombinator, AuthorizationSubscription authzSubscription) {
		return policyRetrievalResult -> {
			if (!policyRetrievalResult.isPrpValidState() || policyRetrievalResult.isErrorsInTarget()) {
				var combinedDecision = CombinedDecision.of(AuthorizationDecision.INDETERMINATE,
						"PRP Detected Error in Targets");
				return Flux.just(PDPDecision.of(authzSubscription, combinedDecision,
						policyRetrievalResult.getMatchingDocuments()));
			}
			var policyElements = policyRetrievalResult.getMatchingDocuments().stream().map(SAPL::getPolicyElement)
					.toList();
			return documentsCombinator.combinePolicies(policyElements).map(combinedDecision -> PDPDecision
					.of(authzSubscription, combinedDecision, policyRetrievalResult.getMatchingDocuments()));
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
