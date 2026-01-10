/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import io.sapl.api.pdp.traced.TracedDecision;
import io.sapl.api.pdp.traced.TracedDecisionInterceptor;
import io.sapl.api.pdp.traced.TracedPdpDecision;
import io.sapl.api.pdp.traced.TracedPolicyDecisionPoint;
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
    private final List<TracedDecisionInterceptor>     interceptors;

    public DynamicPolicyDecisionPoint(CompiledPDPConfigurationSource pdpConfigurationSource, IdFactory idFactory) {
        this(pdpConfigurationSource, idFactory, context -> Mono.just(DEFAULT_PDP_ID), DEFAULT_TIMESTAMP_SUPPLIER,
                List.of());
    }

    public DynamicPolicyDecisionPoint(CompiledPDPConfigurationSource pdpConfigurationSource,
            IdFactory idFactory,
            Function<ContextView, Mono<String>> pdpIdExtractor) {
        this(pdpConfigurationSource, idFactory, pdpIdExtractor, DEFAULT_TIMESTAMP_SUPPLIER, List.of());
    }

    public DynamicPolicyDecisionPoint(CompiledPDPConfigurationSource pdpConfigurationSource,
            IdFactory idFactory,
            Supplier<String> timestampSupplier) {
        this(pdpConfigurationSource, idFactory, context -> Mono.just(DEFAULT_PDP_ID), timestampSupplier, List.of());
    }

    public DynamicPolicyDecisionPoint(CompiledPDPConfigurationSource pdpConfigurationSource,
            IdFactory idFactory,
            Function<ContextView, Mono<String>> pdpIdExtractor,
            Supplier<String> timestampSupplier) {
        this(pdpConfigurationSource, idFactory, pdpIdExtractor, timestampSupplier, List.of());
    }

    /**
     * Creates a DynamicPolicyDecisionPoint with all configuration options.
     *
     * @param pdpConfigurationSource
     * the source for PDP configurations
     * @param idFactory
     * the factory for generating subscription IDs
     * @param pdpIdExtractor
     * function to extract PDP ID from Reactor context
     * @param timestampSupplier
     * supplier for decision timestamps
     * @param interceptors
     * list of interceptors to apply to traced decisions (will be sorted by
     * priority)
     */
    public DynamicPolicyDecisionPoint(CompiledPDPConfigurationSource pdpConfigurationSource,
            IdFactory idFactory,
            Function<ContextView, Mono<String>> pdpIdExtractor,
            Supplier<String> timestampSupplier,
            List<TracedDecisionInterceptor> interceptors) {
        this.pdpConfigurationSource = pdpConfigurationSource;
        this.idFactory              = idFactory;
        this.pdpIdExtractor         = pdpIdExtractor;
        this.timestampSupplier      = timestampSupplier;
        // Sort interceptors by priority (lower values execute first)
        this.interceptors = interceptors.stream().sorted().toList();
    }

    @Override
    public Flux<TracedDecision> decideTraced(AuthorizationSubscription authorizationSubscription) {
        val subscriptionId = idFactory.newRandom();
        return pdpId().flatMapMany(pdpConfigurationSource::getPDPConfigurations)
                .switchMap(optionalConfig -> optionalConfig
                        .map(config -> decide(authorizationSubscription, config, subscriptionId))
                        .orElseGet(() -> Flux.just(noConfigDecision(authorizationSubscription, subscriptionId))))
                .map(this::applyInterceptors);
    }

    @Override
    public Flux<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription) {
        return decideTraced(authorizationSubscription).map(TracedDecision::authorizationDecision);
    }

    /**
     * Synchronous, blocking authorization decision using the pure evaluation path.
     * <p>
     * This method is optimized for high-throughput one-shot authorization
     * decisions. It uses a pre-evaluated expression
     * path that avoids reactive subscription overhead for simple policies without
     * attribute streaming. Falls back to
     * reactive evaluation for policies with attribute access.
     *
     * @param authorizationSubscription
     * the authorization request
     *
     * @return the authorization decision
     */
    @Override
    public AuthorizationDecision decideOnceBlocking(AuthorizationSubscription authorizationSubscription) {
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

        val matchingDocs   = (MatchingDocuments) retrievalResult;
        val policies       = matchingDocs.matches();
        val totalDocuments = matchingDocs.totalDocuments();
        val combinedExpr   = getCombinedExpression(algorithmName, algorithm, policies, totalDocuments);

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

    /**
     * Synchronous, blocking authorization decision with full trace information
     * using the pure evaluation path.
     * <p>
     * This method combines the performance benefits of the pure evaluation path
     * with comprehensive tracing for
     * debugging and auditing.
     *
     * @param authorizationSubscription
     * the authorization request
     *
     * @return the traced decision containing both the authorization result and
     * evaluation trace
     */
    @Override
    public TracedDecision decideOnceBlockingTraced(AuthorizationSubscription authorizationSubscription) {
        val pdpConfiguration = pdpConfigurationSource.getCurrentConfiguration(DEFAULT_PDP_ID)
                .orElseThrow(() -> new IllegalStateException("No PDP configuration available"));

        val subscriptionId    = idFactory.newRandom();
        val evaluationContext = evaluationContext(authorizationSubscription, pdpConfiguration, subscriptionId);
        val algorithm         = pdpConfiguration.combiningAlgorithm();
        val algorithmName     = toSaplSyntax(algorithm);

        val retrievalResult = pdpConfiguration.policyRetrievalPoint().getMatchingDocuments(authorizationSubscription,
                evaluationContext);

        if (retrievalResult instanceof RetrievalError(String documentName, ErrorValue error)) {
            return applyInterceptors(
                    indeterminateDecisionWithRetrievalError(evaluationContext, algorithmName, documentName, error));
        }

        val matchingDocs   = (MatchingDocuments) retrievalResult;
        val policies       = matchingDocs.matches();
        val totalDocuments = matchingDocs.totalDocuments();
        val combinedExpr   = getCombinedExpression(algorithmName, algorithm, policies, totalDocuments);

        if (combinedExpr instanceof Value value) {
            return applyInterceptors(new TracedDecision(value));
        }
        if (combinedExpr instanceof PureExpression pureExpr) {
            return applyInterceptors(new TracedDecision(pureExpr.evaluate(evaluationContext)));
        }
        if (combinedExpr instanceof StreamExpression(Flux<Value> stream)) {
            return applyInterceptors(stream.contextWrite(ctx -> ctx.put(EvaluationContext.class, evaluationContext))
                    .map(TracedDecision::new).blockFirst());
        }
        return applyInterceptors(noConfigDecision(authorizationSubscription, subscriptionId));
    }

    private CompiledExpression getCombinedExpression(String algorithmName, CombiningAlgorithm algorithm,
            List<CompiledPolicy> policies, int totalDocuments) {
        return switch (algorithm) {
        case DENY_OVERRIDES      ->
            CombiningAlgorithmCompiler.denyOverridesPreMatched(algorithmName, policies, totalDocuments);
        case PERMIT_OVERRIDES    ->
            CombiningAlgorithmCompiler.permitOverridesPreMatched(algorithmName, policies, totalDocuments);
        case DENY_UNLESS_PERMIT  ->
            CombiningAlgorithmCompiler.denyUnlessPermitPreMatched(algorithmName, policies, totalDocuments);
        case PERMIT_UNLESS_DENY  ->
            CombiningAlgorithmCompiler.permitUnlessDenyPreMatched(algorithmName, policies, totalDocuments);
        case ONLY_ONE_APPLICABLE ->
            CombiningAlgorithmCompiler.onlyOneApplicablePreMatched(algorithmName, policies, totalDocuments);
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
        case RetrievalError(String documentName, ErrorValue error)               ->
            Flux.just(indeterminateDecisionWithRetrievalError(evaluationContext, algorithmName, documentName, error));
        case MatchingDocuments(List<CompiledPolicy> matches, int totalDocuments) ->
            evaluateMatchingPolicies(matches, totalDocuments, evaluationContext, algorithmName, algorithm);
        };
    }

    private Flux<TracedDecision> evaluateMatchingPolicies(List<CompiledPolicy> policies, int totalDocuments,
            EvaluationContext evaluationContext, String algorithmName, CombiningAlgorithm algorithm) {
        val combinedExpression = getCombinedExpression(algorithmName, algorithm, policies, totalDocuments);
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
        val trace = TracedPdpDecision.builder().pdpId("unknown").configurationId("no-security")
                .subscriptionId(subscriptionId).subscription(subscription).timestamp("unknown").algorithm("none")
                .decision(AuthorizationDecision.INDETERMINATE.decision()).build();
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

    /**
     * Applies all registered interceptors to a traced decision in priority order.
     * <p>
     * Interceptors are applied sequentially, with each receiving the result of the
     * previous interceptor. Lower priority
     * values execute first (MIN_VALUE executes first, MAX_VALUE executes last). The
     * ReportingDecisionInterceptor uses
     * MAX_VALUE to ensure it runs last and captures all modifications.
     *
     * @param decision
     * the traced decision to process
     *
     * @return the potentially modified traced decision
     */
    private TracedDecision applyInterceptors(TracedDecision decision) {
        if (interceptors.isEmpty()) {
            return decision;
        }
        var result = decision;
        for (val interceptor : interceptors) {
            result = interceptor.apply(result);
        }
        return result;
    }

}
