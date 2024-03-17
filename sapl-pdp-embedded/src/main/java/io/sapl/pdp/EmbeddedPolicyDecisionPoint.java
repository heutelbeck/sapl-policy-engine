/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
import io.sapl.interpreter.CombinedDecision;
import io.sapl.interpreter.combinators.CombiningAlgorithmFactory;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.pdp.config.PDPConfiguration;
import io.sapl.pdp.config.PDPConfigurationProvider;
import io.sapl.prp.PolicyRetrievalResult;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

@RequiredArgsConstructor
public class EmbeddedPolicyDecisionPoint implements PolicyDecisionPoint {

    private final PDPConfigurationProvider configurationProvider;

    @Override
    public Flux<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription) {
        return decideTraced(authorizationSubscription).map(TracedDecision::getAuthorizationDecision)
                .distinctUntilChanged();
    }

    public Flux<TracedDecision> decideTraced(AuthorizationSubscription authorizationSubscription) {
        return configurationProvider.pdpConfiguration().switchMap(decideSubscription(authorizationSubscription));
    }

    private Function<? super PDPConfiguration, Publisher<? extends TracedDecision>> decideSubscription(
            AuthorizationSubscription authorizationSubscription) {
        return pdpConfiguration -> {
            var subscription = pdpConfiguration.subscriptionInterceptorChain().apply(authorizationSubscription);
            return retrieveAndCombineDocuments(pdpConfiguration, subscription)
                    .map(pdpConfiguration.decisionInterceptorChain())
                    .contextWrite(buildSubscriptionScopedContext(pdpConfiguration, authorizationSubscription));
        };
    }

    private Function<Context, Context> buildSubscriptionScopedContext(PDPConfiguration pdpConfiguration,
            AuthorizationSubscription authorizationSubscription) {
        return ctx -> {
            ctx = AuthorizationContext.setAttributeContext(ctx, pdpConfiguration.attributeContext());
            ctx = AuthorizationContext.setFunctionContext(ctx, pdpConfiguration.functionContext());
            ctx = AuthorizationContext.setVariables(ctx, pdpConfiguration.variables());
            ctx = AuthorizationContext.setSubscriptionVariables(ctx, authorizationSubscription);
            return ctx;
        };
    }

    private Flux<PDPDecision> retrieveAndCombineDocuments(PDPConfiguration pdpConfiguration,
            AuthorizationSubscription authorizationSubscription) {
        if (pdpConfiguration.policyRetrievalPoint().isConsistent()) {
            return pdpConfiguration.policyRetrievalPoint().retrievePolicies()
                    .flatMapMany(combineDocuments(pdpConfiguration, authorizationSubscription));
        }
        return Flux
                .just(PDPDecision.of(authorizationSubscription,
                        CombinedDecision.error(pdpConfiguration.documentsCombinator(),
                                "Policy Retrieval Point in inconsistent state."),
                        PolicyRetrievalResult.invalidPrpResult()));
    }

    private Function<PolicyRetrievalResult, Flux<PDPDecision>> combineDocuments(PDPConfiguration pdpConfiguration,
            AuthorizationSubscription authorizationSubscription) {
        return policyRetrievalResult -> {
            if (policyRetrievalResult.isPrpInconsistent() || policyRetrievalResult.isRetrievalWithErrors()) {
                var combinedDecision = CombinedDecision.of(AuthorizationDecision.INDETERMINATE,
                        pdpConfiguration.documentsCombinator());
                return Flux.just(PDPDecision.of(authorizationSubscription, combinedDecision, policyRetrievalResult));
            }
            var matchingDocuments  = policyRetrievalResult.getMatchingDocuments();
            var combiningAlgorithm = CombiningAlgorithmFactory
                    .documentsCombiningAlgorithm(pdpConfiguration.documentsCombinator());
            return combiningAlgorithm.combinePreMatchedDocuments(matchingDocuments).map(combinedDecision -> PDPDecision
                    .of(authorizationSubscription, combinedDecision, policyRetrievalResult));
        };
    }

    @Override
    public Flux<IdentifiableAuthorizationDecision> decide(
            MultiAuthorizationSubscription multiAuthorizationSubscription) {
        if (multiAuthorizationSubscription.hasAuthorizationSubscriptions()) {
            final List<Flux<IdentifiableAuthorizationDecision>> identifiableAuthorizationDecisionFluxes = createIdentifiableAuthorizationDecisionFluxes(
                    multiAuthorizationSubscription);
            return Flux.merge(identifiableAuthorizationDecisionFluxes);
        }
        return Flux.just(IdentifiableAuthorizationDecision.INDETERMINATE);
    }

    @Override
    public Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiAuthorizationSubscription) {
        if (multiAuthorizationSubscription.hasAuthorizationSubscriptions()) {
            final List<Flux<IdentifiableAuthorizationDecision>> identifiableAuthorizationDecisionFluxes = createIdentifiableAuthorizationDecisionFluxes(
                    multiAuthorizationSubscription);
            return Flux.combineLatest(identifiableAuthorizationDecisionFluxes, this::collectAuthorizationDecisions);
        }
        return Flux.just(MultiAuthorizationDecision.indeterminate());
    }

    private List<Flux<IdentifiableAuthorizationDecision>> createIdentifiableAuthorizationDecisionFluxes(
            Iterable<IdentifiableAuthorizationSubscription> multiDecision) {
        final List<Flux<IdentifiableAuthorizationDecision>> identifiableAuthorizationDecisionFluxes = new ArrayList<>();
        for (IdentifiableAuthorizationSubscription identifiableAuthorizationSubscription : multiDecision) {
            final String                                  subscriptionId                        = identifiableAuthorizationSubscription
                    .authorizationSubscriptionId();
            final AuthorizationSubscription               authorizationSubscription             = identifiableAuthorizationSubscription
                    .authorizationSubscription();
            final Flux<IdentifiableAuthorizationDecision> identifiableAuthorizationDecisionFlux = decide(
                    authorizationSubscription)
                    .map(authorizationDecision -> new IdentifiableAuthorizationDecision(subscriptionId,
                            authorizationDecision));
            identifiableAuthorizationDecisionFluxes.add(identifiableAuthorizationDecisionFlux);
        }
        return identifiableAuthorizationDecisionFluxes;
    }

    private MultiAuthorizationDecision collectAuthorizationDecisions(Object[] values) {
        final MultiAuthorizationDecision multiAuthorizationDecision = new MultiAuthorizationDecision();
        for (Object value : values) {
            IdentifiableAuthorizationDecision ir = (IdentifiableAuthorizationDecision) value;
            multiAuthorizationDecision.setAuthorizationDecisionForSubscriptionWithId(
                    ir.getAuthorizationSubscriptionId(), ir.getAuthorizationDecision());
        }
        return multiAuthorizationDecision;
    }

    public void destroy() {
        configurationProvider.destroy();
    }
}
