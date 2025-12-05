/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureExpression;
import io.sapl.api.model.StreamExpression;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.internal.TracedPolicyDecisionPoint;
import io.sapl.api.pdp.internal.TracedDecision;
import io.sapl.api.pdp.internal.TracedPdpDecision;
import io.sapl.api.prp.MatchingDocuments;
import io.sapl.api.prp.RetrievalError;
import io.sapl.compiler.CombiningAlgorithmCompiler;
import io.sapl.compiler.CompiledPolicy;
import io.sapl.compiler.ExpressionCompiler;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class DynamicPolicyDecisionPoint implements TracedPolicyDecisionPoint {

    public static final String DEFAULT_PDP_ID = "default";

    private static final Supplier<String> DEFAULT_TIMESTAMP_SUPPLIER = () -> Instant.now().toString();

    private final CompiledPDPConfigurationSource      pdpConfigurationSource;
    private final IdFactory                           idFactory;
    private final Function<ContextView, Mono<String>> pdpIdExtractor;
    private final Supplier<String>                    timestampSupplier;

    public DynamicPolicyDecisionPoint(CompiledPDPConfigurationSource pdpConfigurationSource, IdFactory idFactory) {
        this(pdpConfigurationSource, idFactory, context -> Mono.just(DEFAULT_PDP_ID), DEFAULT_TIMESTAMP_SUPPLIER);
    }

    public DynamicPolicyDecisionPoint(CompiledPDPConfigurationSource pdpConfigurationSource,
            IdFactory idFactory,
            Function<ContextView, Mono<String>> pdpIdExtractor) {
        this(pdpConfigurationSource, idFactory, pdpIdExtractor, DEFAULT_TIMESTAMP_SUPPLIER);
    }

    public DynamicPolicyDecisionPoint(CompiledPDPConfigurationSource pdpConfigurationSource,
            IdFactory idFactory,
            Supplier<String> timestampSupplier) {
        this(pdpConfigurationSource, idFactory, context -> Mono.just(DEFAULT_PDP_ID), timestampSupplier);
    }

    public DynamicPolicyDecisionPoint(CompiledPDPConfigurationSource pdpConfigurationSource,
            IdFactory idFactory,
            Function<ContextView, Mono<String>> pdpIdExtractor,
            Supplier<String> timestampSupplier) {
        this.pdpConfigurationSource = pdpConfigurationSource;
        this.idFactory              = idFactory;
        this.pdpIdExtractor         = pdpIdExtractor;
        this.timestampSupplier      = timestampSupplier;
    }

    @Override
    public Flux<TracedDecision> decideTraced(AuthorizationSubscription authorizationSubscription) {
        val subscriptionId = idFactory.newRandom();
        return pdpId().flatMapMany(pdpConfigurationSource::getPDPConfigurations)
                .switchMap(optionalConfig -> optionalConfig
                        .map(config -> decide(authorizationSubscription, config, subscriptionId))
                        .orElseGet(() -> Flux.just(noConfigDecision(authorizationSubscription, subscriptionId))));
    }

    @Override
    public Flux<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription) {
        return decideTraced(authorizationSubscription).map(TracedDecision::authorizationDecision);
    }

    /**
     * Blocking decision for simple API access patterns.
     * <p>
     * Uses a pre-evaluated expression path optimized for simple policies without
     * attribute streaming. Falls back to
     * reactive evaluation for policies with attribute access.
     *
     * @param authorizationSubscription
     * the authorization request
     *
     * @return the authorization decision
     */
    public AuthorizationDecision decideBlocking(AuthorizationSubscription authorizationSubscription) {
        val pdpConfiguration = pdpConfigurationSource.getCurrentConfiguration(DEFAULT_PDP_ID)
                .orElseThrow(() -> new IllegalStateException("No PDP configuration available"));

        val evaluationContext = evaluationContext(authorizationSubscription, pdpConfiguration, idFactory.newRandom());
        val algorithm         = pdpConfiguration.combiningAlgorithm();
        val algorithmName     = toSaplSyntax(algorithm);

        val retrievalResult = pdpConfiguration.policyRetrievalPoint().getMatchingDocuments(authorizationSubscription,
                evaluationContext);

        if (retrievalResult instanceof RetrievalError) {
            return AuthorizationDecision.INDETERMINATE;
        }

        val policies     = ((MatchingDocuments) retrievalResult).matches();
        val combinedExpr = getCombinedExpression(algorithmName, algorithm, policies);

        if (combinedExpr instanceof Value value) {
            return AuthorizationDecision.of(value);
        }
        if (combinedExpr instanceof PureExpression pureExpr) {
            return AuthorizationDecision.of(pureExpr.evaluate(evaluationContext));
        }
        if (combinedExpr instanceof StreamExpression(Flux<Value> stream)) {
            return stream.contextWrite(ctx -> ctx.put(EvaluationContext.class, evaluationContext))
                    .map(AuthorizationDecision::of).blockFirst();
        }
        return AuthorizationDecision.INDETERMINATE;
    }

    private CompiledExpression getCombinedExpression(String algorithmName, CombiningAlgorithm algorithm,
            List<CompiledPolicy> policies) {
        return switch (algorithm) {
        case DENY_OVERRIDES      -> CombiningAlgorithmCompiler.denyOverridesPreMatched(algorithmName, policies);
        case PERMIT_OVERRIDES    -> CombiningAlgorithmCompiler.permitOverridesPreMatched(algorithmName, policies);
        case DENY_UNLESS_PERMIT  -> CombiningAlgorithmCompiler.denyUnlessPermitPreMatched(algorithmName, policies);
        case PERMIT_UNLESS_DENY  -> CombiningAlgorithmCompiler.permitUnlessDenyPreMatched(algorithmName, policies);
        case ONLY_ONE_APPLICABLE -> CombiningAlgorithmCompiler.onlyOneApplicablePreMatched(algorithmName, policies);
        };
    }

    private Flux<TracedDecision> decide(AuthorizationSubscription authorizationSubscription,
            CompiledPDPConfiguration pdpConfiguration, String subscriptionId) {
        val evaluationContext = evaluationContext(authorizationSubscription, pdpConfiguration, subscriptionId);
        val algorithm         = pdpConfiguration.combiningAlgorithm();
        val algorithmName     = toSaplSyntax(algorithm);
        return decideCombining(pdpConfiguration, evaluationContext, algorithmName, algorithm);
    }

    private Flux<TracedDecision> decideCombining(CompiledPDPConfiguration pdpConfiguration,
            EvaluationContext evaluationContext, String algorithmName, CombiningAlgorithm algorithm) {
        val retrievalResult = pdpConfiguration.policyRetrievalPoint()
                .getMatchingDocuments(evaluationContext.authorizationSubscription(), evaluationContext);
        return switch (retrievalResult) {
        case RetrievalError(String documentName, ErrorValue error) ->
            Flux.just(indeterminateDecisionWithRetrievalError(evaluationContext, algorithmName, documentName, error));
        case MatchingDocuments(List<CompiledPolicy> matches)       ->
            evaluateMatchingPolicies(matches, evaluationContext, algorithmName, algorithm);
        };
    }

    private Flux<TracedDecision> evaluateMatchingPolicies(List<CompiledPolicy> policies,
            EvaluationContext evaluationContext, String algorithmName, CombiningAlgorithm algorithm) {
        val combinedExpression = getCombinedExpression(algorithmName, algorithm, policies);
        return ExpressionCompiler.compiledExpressionToFlux(combinedExpression)
                .contextWrite(context -> context.put(EvaluationContext.class, evaluationContext))
                .map(TracedDecision::new);
    }

    private EvaluationContext evaluationContext(AuthorizationSubscription authorizationSubscription,
            CompiledPDPConfiguration pdpConfiguration, String subscriptionId) {
        return EvaluationContext.of(pdpConfiguration.pdpId(), pdpConfiguration.configurationId(), subscriptionId,
                authorizationSubscription, pdpConfiguration.variables(), pdpConfiguration.functionBroker(),
                pdpConfiguration.attributeBroker(), timestampSupplier);
    }

    private Mono<String> pdpId() {
        return Mono.deferContextual(pdpIdExtractor);
    }

    private TracedDecision noConfigDecision(AuthorizationSubscription subscription, String subscriptionId) {
        val trace = TracedPdpDecision.builder().pdpId("unknown").configurationId("no-config")
                .subscriptionId(subscriptionId).subscription(subscription).timestamp("unknown").algorithm("none")
                .decision(AuthorizationDecision.INDETERMINATE.decision()).build();
        return new TracedDecision(trace);
    }

    private TracedDecision indeterminateDecision(EvaluationContext evaluationContext, String algorithmName) {
        val trace = TracedPdpDecision.builder().pdpId(evaluationContext.pdpId())
                .configurationId(evaluationContext.configurationId()).subscriptionId(evaluationContext.subscriptionId())
                .subscription(evaluationContext.authorizationSubscription()).timestamp(evaluationContext.timestamp())
                .algorithm(algorithmName).decision(AuthorizationDecision.INDETERMINATE.decision()).build();
        return new TracedDecision(trace);
    }

    private TracedDecision indeterminateDecisionWithRetrievalError(EvaluationContext evaluationContext,
            String algorithmName, String documentName, ErrorValue error) {
        val trace = TracedPdpDecision.builder().pdpId(evaluationContext.pdpId())
                .configurationId(evaluationContext.configurationId()).subscriptionId(evaluationContext.subscriptionId())
                .subscription(evaluationContext.authorizationSubscription()).timestamp(evaluationContext.timestamp())
                .algorithm(algorithmName).decision(AuthorizationDecision.INDETERMINATE.decision())
                .addRetrievalError(documentName, error).build();
        return new TracedDecision(trace);
    }

    private static String toSaplSyntax(CombiningAlgorithm algorithm) {
        return algorithm.name().toLowerCase().replace('_', '-');
    }

}
