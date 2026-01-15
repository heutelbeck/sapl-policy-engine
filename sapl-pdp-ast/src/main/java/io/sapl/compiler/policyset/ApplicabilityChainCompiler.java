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
package io.sapl.compiler.policyset;

import io.sapl.api.model.AttributeRecord;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.StreamOperator;
import io.sapl.compiler.pdp.DecisionMaker;
import io.sapl.compiler.pdp.PDPDecision;
import io.sapl.compiler.pdp.PureDecisionMaker;
import io.sapl.compiler.pdp.StreamDecisionMaker;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Chains applicability checking with decision makers for policy sets.
 * <p>
 * This compiler wraps a decision maker (produced by a combining algorithm) with
 * applicability checking based on the target expression type. The resulting
 * {@link DecisionMaker} first evaluates applicability, then delegates to the
 * underlying decision maker if applicable.
 */
@UtilityClass
public class ApplicabilityChainCompiler {

    public static final String ERROR_UNEXPECTED_IS_APPLICABLE_TYPE = "Unexpected isApplicable type. Indicates implementation bug.";
    public static final String ERROR_STREAM_IN_PURE_CONTEXT        = "Stream decision maker in pure context. Indicates implementation bug.";

    /**
     * Wraps a decision maker with applicability checking based on the target
     * expression type.
     *
     * @param isApplicable the compiled target expression determining applicability
     * @param decisionMaker the decision maker from the combining algorithm
     * @param metadata the policy set metadata
     * @return a decision maker that combines applicability and decision evaluation
     */
    public static DecisionMaker compileApplicabilityAndDecision(CompiledExpression isApplicable,
            DecisionMaker decisionMaker, PolicySetMetadata metadata) {
        return switch (isApplicable) {
        case ErrorValue error                                                      ->
            PolicySetDecision.error(error, metadata, List.of());
        case BooleanValue(var b) when b                                            -> decisionMaker;
        case BooleanValue ignored                                                  ->
            PolicySetDecision.notApplicable(metadata, List.of());
        case PureOperator po when decisionMaker instanceof StreamDecisionMaker sdm ->
            new PureApplicabilityStreamPolicySet(po, sdm, metadata);
        case PureOperator po                                                       ->
            new ApplicabilityCheckingPurePolicySet(po, decisionMaker, metadata);
        case StreamOperator so                                                     ->
            new ApplicabilityCheckingStreamPolicySet(so, decisionMaker, metadata);
        default                                                                    ->
            PolicySetDecision.error(new ErrorValue(ERROR_UNEXPECTED_IS_APPLICABLE_TYPE), metadata, List.of());
        };
    }

    /**
     * Decision maker for pure applicability check with non-streaming decision.
     *
     * @param isApplicable the pure operator for applicability evaluation
     * @param decisionMaker the underlying decision maker
     * @param metadata the policy set metadata
     */
    record ApplicabilityCheckingPurePolicySet(
            PureOperator isApplicable,
            DecisionMaker decisionMaker,
            PolicySetMetadata metadata) implements PureDecisionMaker {

        @Override
        public PDPDecision decide(List<AttributeRecord> knownContributions, EvaluationContext ctx) {
            val applicabilityResult = isApplicable.evaluate(ctx);
            if (applicabilityResult instanceof ErrorValue error) {
                return PolicySetDecision.error(error, metadata, List.of());
            }
            if (applicabilityResult instanceof BooleanValue(var b) && b) {
                return switch (decisionMaker) {
                case PDPDecision pd              -> pd;
                case PureDecisionMaker pdm       -> pdm.decide(knownContributions, ctx);
                case StreamDecisionMaker ignored ->
                    PolicySetDecision.error(new ErrorValue(ERROR_STREAM_IN_PURE_CONTEXT), metadata, List.of());
                };
            }
            return PolicySetDecision.notApplicable(metadata, List.of());
        }
    }

    /**
     * Decision maker for streaming applicability check.
     *
     * @param isApplicable the stream operator for applicability evaluation
     * @param decisionMaker the underlying decision maker
     * @param metadata the policy set metadata
     */
    record ApplicabilityCheckingStreamPolicySet(
            StreamOperator isApplicable,
            DecisionMaker decisionMaker,
            PolicySetMetadata metadata) implements StreamDecisionMaker {

        @Override
        public Flux<PDPDecision> decide(List<AttributeRecord> knownContributions) {
            return isApplicable.stream().switchMap(tracedApplicability -> {
                val applicabilityValue = tracedApplicability.value();
                if (applicabilityValue instanceof ErrorValue error) {
                    return Flux.just(PolicySetDecision.error(error, metadata, List.of()));
                }
                if (applicabilityValue instanceof BooleanValue(var b) && b) {
                    return switch (decisionMaker) {
                    case PDPDecision pd          -> Flux.just(pd);
                    case PureDecisionMaker pdm   -> Flux.deferContextual(
                            ctxView -> Flux.just(pdm.decide(knownContributions, ctxView.get(EvaluationContext.class))));
                    case StreamDecisionMaker sdm -> sdm.decide(knownContributions);
                    };
                }
                return Flux.just(PolicySetDecision.notApplicable(metadata, List.of()));
            });
        }
    }

    /**
     * Decision maker for pure applicability check with streaming decision.
     *
     * @param isApplicable the pure operator for applicability evaluation
     * @param streamDecisionMaker the streaming decision maker
     * @param metadata the policy set metadata
     */
    record PureApplicabilityStreamPolicySet(
            PureOperator isApplicable,
            StreamDecisionMaker streamDecisionMaker,
            PolicySetMetadata metadata) implements StreamDecisionMaker {

        @Override
        public Flux<PDPDecision> decide(List<AttributeRecord> knownContributions) {
            return Flux.deferContextual(ctxView -> {
                val ctx                 = ctxView.get(EvaluationContext.class);
                val applicabilityResult = isApplicable.evaluate(ctx);
                if (applicabilityResult instanceof ErrorValue error) {
                    return Flux.just(PolicySetDecision.error(error, metadata, List.of()));
                }
                if (applicabilityResult instanceof BooleanValue(var b) && b) {
                    return streamDecisionMaker.decide(knownContributions);
                }
                return Flux.just(PolicySetDecision.notApplicable(metadata, List.of()));
            });
        }
    }
}
