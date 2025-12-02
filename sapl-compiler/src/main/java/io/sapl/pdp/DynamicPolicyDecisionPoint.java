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
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureExpression;
import io.sapl.api.model.StreamExpression;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.TracedPolicyDecisionPoint;
import io.sapl.api.pdp.internal.DecisionMetadata;
import io.sapl.api.pdp.internal.TracedDecision;
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
import java.util.Map;
import java.util.function.Function;

public class DynamicPolicyDecisionPoint implements TracedPolicyDecisionPoint {
    private final CompiledPDPConfigurationSource      pdpConfigurationSource;
    private final IdFactory                           idFactory;
    private final Function<ContextView, Mono<String>> pdpIdExtractor;

    public DynamicPolicyDecisionPoint(CompiledPDPConfigurationSource pdpConfigurationSource, IdFactory idFactory) {
        this.pdpConfigurationSource = pdpConfigurationSource;
        this.idFactory              = idFactory;
        this.pdpIdExtractor         = context -> Mono.just("default");
    }

    public DynamicPolicyDecisionPoint(CompiledPDPConfigurationSource pdpConfigurationSource,
            IdFactory idFactory,
            Function<ContextView, Mono<String>> pdpIdExtractor) {
        this.pdpConfigurationSource = pdpConfigurationSource;
        this.idFactory              = idFactory;
        this.pdpIdExtractor         = pdpIdExtractor;
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
     * Synchronous authorization decision for high-throughput scenarios.
     * <p>
     * This method bypasses Reactor's subscription overhead by reading the
     * configuration
     * directly from a lock-free cache. For pure policies (no attribute streams),
     * the
     * entire evaluation is synchronous. For policies with attribute access, it
     * falls
     * back to blocking on the attribute streams while still avoiding configuration
     * subscription overhead.
     * <p>
     * Use this method when:
     * <ul>
     * <li>Making many one-shot authorization decisions</li>
     * <li>Configuration changes are rare</li>
     * <li>You need maximum throughput rather than reactive updates</li>
     * </ul>
     *
     * @param authorizationSubscription
     * the authorization subscription to evaluate
     *
     * @return the authorization decision
     */
    public AuthorizationDecision decidePure(AuthorizationSubscription authorizationSubscription) {
        return decidePure(authorizationSubscription, "default");
    }

    /**
     * Synchronous authorization decision for a specific PDP configuration.
     *
     * @param authorizationSubscription
     * the authorization subscription to evaluate
     * @param pdpId
     * the PDP identifier for configuration lookup
     *
     * @return the authorization decision
     */
    public AuthorizationDecision decidePure(AuthorizationSubscription authorizationSubscription, String pdpId) {
        val configOpt = pdpConfigurationSource.getCurrentConfiguration(pdpId);
        if (configOpt.isEmpty()) {
            return AuthorizationDecision.INDETERMINATE;
        }
        return evaluatePure(authorizationSubscription, configOpt.get());
    }

    /**
     * Minimal overhead decision path for benchmarking - skips subscription ID
     * generation.
     * <p>
     * This method is intended for performance testing to isolate the cost of
     * UUID generation. It uses a static subscription ID which means tracing
     * will not work correctly. Do not use in production.
     *
     * @param authorizationSubscription
     * the authorization subscription to evaluate
     *
     * @return the authorization decision
     */
    public AuthorizationDecision decidePureMinimal(AuthorizationSubscription authorizationSubscription) {
        val configOpt = pdpConfigurationSource.getCurrentConfiguration("default");
        if (configOpt.isEmpty()) {
            return AuthorizationDecision.INDETERMINATE;
        }
        return evaluatePureMinimal(authorizationSubscription, configOpt.get());
    }

    private AuthorizationDecision evaluatePureMinimal(AuthorizationSubscription authorizationSubscription,
            CompiledPDPConfiguration config) {
        // Static subscription ID - avoids UUID.randomUUID() contention
        val evaluationContext = EvaluationContext.of(config.pdpId(), config.configurationId(), "benchmark",
                authorizationSubscription, config.variables(), config.functionBroker(), config.attributeBroker());
        val retrievalResult   = config.policyRetrievalPoint().getMatchingDocuments(authorizationSubscription,
                evaluationContext);

        if (retrievalResult instanceof RetrievalError) {
            return AuthorizationDecision.INDETERMINATE;
        }

        val policies     = ((MatchingDocuments) retrievalResult).matches();
        val combinedExpr = getCombinedExpression(config.combiningAlgorithm(), policies);

        if (combinedExpr instanceof Value value) {
            return AuthorizationDecision.of(value);
        }
        if (combinedExpr instanceof PureExpression pureExpr) {
            return AuthorizationDecision.of(pureExpr.evaluate(evaluationContext));
        }
        if (combinedExpr instanceof StreamExpression stream) {
            return stream.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evaluationContext))
                    .map(AuthorizationDecision::of).blockFirst();
        }
        return AuthorizationDecision.INDETERMINATE;
    }

    private AuthorizationDecision evaluatePure(AuthorizationSubscription authorizationSubscription,
            CompiledPDPConfiguration config) {
        val subscriptionId    = idFactory.newRandom();
        val evaluationContext = evaluationContext(authorizationSubscription, config, subscriptionId);
        val retrievalResult   = config.policyRetrievalPoint().getMatchingDocuments(authorizationSubscription,
                evaluationContext);

        if (retrievalResult instanceof RetrievalError) {
            return AuthorizationDecision.INDETERMINATE;
        }

        val policies     = ((MatchingDocuments) retrievalResult).matches();
        val combinedExpr = getCombinedExpression(config.combiningAlgorithm(), policies);

        // Value extends CompiledExpression, so check more specific types first
        if (combinedExpr instanceof Value value) {
            return AuthorizationDecision.of(value);
        }
        if (combinedExpr instanceof PureExpression pureExpr) {
            return AuthorizationDecision.of(pureExpr.evaluate(evaluationContext));
        }
        if (combinedExpr instanceof StreamExpression stream) {
            // Fallback for policies with attribute access - minimal reactive overhead
            // Still faster than full reactive path (no configuration sink subscription)
            return stream.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evaluationContext))
                    .map(AuthorizationDecision::of).blockFirst();
        }
        return AuthorizationDecision.INDETERMINATE;
    }

    private CompiledExpression getCombinedExpression(io.sapl.api.pdp.CombiningAlgorithm algorithm,
            List<CompiledPolicy> policies) {
        return switch (algorithm) {
        case DENY_OVERRIDES      -> CombiningAlgorithmCompiler.denyOverridesPreMatched(policies);
        case PERMIT_OVERRIDES    -> CombiningAlgorithmCompiler.permitOverridesPreMatched(policies);
        case DENY_UNLESS_PERMIT  -> CombiningAlgorithmCompiler.denyUnlessPermitPreMatched(policies);
        case PERMIT_UNLESS_DENY  -> CombiningAlgorithmCompiler.permitUnlessDenyPreMatched(policies);
        case ONLY_ONE_APPLICABLE -> CombiningAlgorithmCompiler.onlyOneApplicablePreMatched(policies);
        };
    }

    private Flux<TracedDecision> decide(AuthorizationSubscription authorizationSubscription,
            CompiledPDPConfiguration pdpConfiguration, String subscriptionId) {
        val evaluationContext      = evaluationContext(authorizationSubscription, pdpConfiguration, subscriptionId);
        val combiningAlgorithmName = pdpConfiguration.combiningAlgorithm().name();
        return switch (pdpConfiguration.combiningAlgorithm()) {
        case DENY_OVERRIDES      -> decideDenyOverrides(pdpConfiguration, evaluationContext, combiningAlgorithmName);
        case DENY_UNLESS_PERMIT  -> decideDenyUnlessPermit(pdpConfiguration, evaluationContext, combiningAlgorithmName);
        case PERMIT_OVERRIDES    -> decidePermitOverrides(pdpConfiguration, evaluationContext, combiningAlgorithmName);
        case PERMIT_UNLESS_DENY  -> decidePermitUnlessDeny(pdpConfiguration, evaluationContext, combiningAlgorithmName);
        case ONLY_ONE_APPLICABLE ->
            decideOnlyOneApplicable(pdpConfiguration, evaluationContext, combiningAlgorithmName);
        };
    }

    private Flux<TracedDecision> decideOnlyOneApplicable(CompiledPDPConfiguration pdpConfiguration,
            EvaluationContext evaluationContext, String combiningAlgorithmName) {
        return evaluateCombiningAlgorithm(pdpConfiguration, evaluationContext, combiningAlgorithmName,
                CombiningAlgorithmCompiler::onlyOneApplicablePreMatched);
    }

    private Flux<TracedDecision> decidePermitUnlessDeny(CompiledPDPConfiguration pdpConfiguration,
            EvaluationContext evaluationContext, String combiningAlgorithmName) {
        return evaluateCombiningAlgorithm(pdpConfiguration, evaluationContext, combiningAlgorithmName,
                CombiningAlgorithmCompiler::permitUnlessDenyPreMatched);
    }

    private Flux<TracedDecision> decideDenyUnlessPermit(CompiledPDPConfiguration pdpConfiguration,
            EvaluationContext evaluationContext, String combiningAlgorithmName) {
        return evaluateCombiningAlgorithm(pdpConfiguration, evaluationContext, combiningAlgorithmName,
                CombiningAlgorithmCompiler::denyUnlessPermitPreMatched);
    }

    private Flux<TracedDecision> decidePermitOverrides(CompiledPDPConfiguration pdpConfiguration,
            EvaluationContext evaluationContext, String combiningAlgorithmName) {
        return evaluateCombiningAlgorithm(pdpConfiguration, evaluationContext, combiningAlgorithmName,
                CombiningAlgorithmCompiler::permitOverridesPreMatched);
    }

    private Flux<TracedDecision> decideDenyOverrides(CompiledPDPConfiguration pdpConfiguration,
            EvaluationContext evaluationContext, String combiningAlgorithmName) {
        return evaluateCombiningAlgorithm(pdpConfiguration, evaluationContext, combiningAlgorithmName,
                CombiningAlgorithmCompiler::denyOverridesPreMatched);
    }

    private Flux<TracedDecision> evaluateCombiningAlgorithm(CompiledPDPConfiguration pdpConfiguration,
            EvaluationContext evaluationContext, String combiningAlgorithmName,
            Function<List<CompiledPolicy>, CompiledExpression> combiningAlgorithm) {
        val retrievalResult = pdpConfiguration.policyRetrievalPoint()
                .getMatchingDocuments(evaluationContext.authorizationSubscription(), evaluationContext);
        return switch (retrievalResult) {
        case RetrievalError error                            ->
            Flux.just(indeterminateDecision(evaluationContext, combiningAlgorithmName));
        case MatchingDocuments(List<CompiledPolicy> matches) ->
            evaluateMatchingPolicies(matches, evaluationContext, combiningAlgorithmName, combiningAlgorithm);
        };
    }

    private Flux<TracedDecision> evaluateMatchingPolicies(List<CompiledPolicy> policies,
            EvaluationContext evaluationContext, String combiningAlgorithmName,
            Function<List<CompiledPolicy>, CompiledExpression> combiningAlgorithm) {
        val combinedExpression = combiningAlgorithm.apply(policies);
        return ExpressionCompiler.compiledExpressionToFlux(combinedExpression)
                .contextWrite(context -> context.put(EvaluationContext.class, evaluationContext))
                .map(value -> toTracedDecision(AuthorizationDecision.of(value), evaluationContext,
                        combiningAlgorithmName));
    }

    private EvaluationContext evaluationContext(AuthorizationSubscription authorizationSubscription,
            CompiledPDPConfiguration pdpConfiguration, String subscriptionId) {
        return EvaluationContext.of(pdpConfiguration.pdpId(), pdpConfiguration.configurationId(), subscriptionId,
                authorizationSubscription, pdpConfiguration.variables(), pdpConfiguration.functionBroker(),
                pdpConfiguration.attributeBroker());
    }

    private Mono<String> pdpId() {
        return Mono.deferContextual(pdpIdExtractor);
    }

    private TracedDecision noConfigDecision(AuthorizationSubscription subscription, String subscriptionId) {
        val metadata = new DecisionMetadata("unknown", "no-config", subscriptionId, subscription, Instant.now(),
                List.of(), List.of(), Map.of(), "NONE");
        return new TracedDecision(AuthorizationDecision.INDETERMINATE, metadata);
    }

    private TracedDecision indeterminateDecision(EvaluationContext evaluationContext, String combiningAlgorithmName) {
        return toTracedDecision(AuthorizationDecision.INDETERMINATE, evaluationContext, combiningAlgorithmName);
    }

    private TracedDecision toTracedDecision(AuthorizationDecision decision, EvaluationContext evaluationContext,
            String combiningAlgorithmName) {
        // TODO: Extract attributes and errors from Value metadata once Phase 3 is
        // implemented
        val metadata = new DecisionMetadata(evaluationContext.pdpId(), evaluationContext.configurationId(),
                evaluationContext.subscriptionId(), evaluationContext.authorizationSubscription(), Instant.now(),
                List.of(),  // attributes - to be populated from Value metadata
                List.of(),  // errors - to be populated from Value metadata
                Map.of(),   // documentDecisions - to be populated from combining algorithm
                combiningAlgorithmName);
        return new TracedDecision(decision, metadata);
    }

}
