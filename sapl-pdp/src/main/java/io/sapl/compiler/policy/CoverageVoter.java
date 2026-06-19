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
package io.sapl.compiler.policy;

import io.sapl.api.model.*;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.document.VoteResult;
import io.sapl.compiler.document.VoteResultWithCoverage;
import io.sapl.compiler.document.Voter;
import io.sapl.compiler.expressions.StratifiedBooleanOperationCompiler;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.policy.PolicyCompiler.IndexedCompiledCondition;
import lombok.val;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.sapl.api.model.StreamOperator.evalChild;
import static io.sapl.api.model.StreamOperator.mergeDependencies;

/**
 * Snapshot-driven coverage-instrumented evaluator for a single policy.
 * Walks the body conditions once per round in stratum order (constants, then
 * pure operators, then streams), accumulates dependencies and per-condition
 * hits, and dispatches to the constraints voter when the body resolves to
 * {@link Value#TRUE}. The body is the Kleene strong three-valued AND of its
 * conditions: a {@link Value#FALSE} dominates and short-circuits regardless of
 * position, an {@link ErrorValue} is carried forward so a later FALSE can still
 * dominate, and only an all-TRUE body dispatches. Evaluating the always
 * resolvable constant and pure strata first lets such a FALSE dominate before
 * any stream is subscribed or awaited, matching production. Two implementations
 * select the body-walk strategy at compile time:
 * <ul>
 * <li>{@link Lazy} subscribes lazily, stopping at the first {@link Value#FALSE}
 * or incomplete child. Minimal subscription set; convergence may take multiple
 * trigger-loop rounds when independent dependencies are missing.</li>
 * <li>{@link Eager} walks every condition unconditionally to gather the full
 * subscription set, then resolves the body value. Larger subscription set;
 * single-round convergence once the snapshot is sufficient.</li>
 * </ul>
 * Observable {@link VoteResultWithCoverage} is identical across both
 * variants; only the per-pass subscription set differs.
 *
 * @since 4.1.0
 */
public interface CoverageVoter {

    VoteResultWithCoverage evaluate(EvaluationContext ctx);

    /**
     * Body value resolved to {@link Value#TRUE}: dispatch to
     * {@code constraintsVoter}, merge its dependencies, wrap as
     * {@link VoteResultWithCoverage}. Body value resolved to
     * {@link ErrorValue}: emit error vote with the accumulated body
     * coverage. Body value resolved to {@link Value#FALSE}: emit abstain
     * vote with the accumulated body coverage.
     */
    static VoteResultWithCoverage finishWithVote(Value bodyValue, Map<SubscriptionKey, List<Occurrence>> deps,
            List<Coverage.ConditionHit> hits, long totalConditions, Voter constraintsVoter, VoterMetadata metadata,
            EvaluationContext ctx) {
        val policyCoverage = new Coverage.PolicyCoverage(metadata, new Coverage.BodyCoverage(hits, totalConditions));
        if (bodyValue instanceof ErrorValue err) {
            return new VoteResultWithCoverage(new VoteResult(Vote.error(err, metadata), deps), policyCoverage);
        }
        if (Value.FALSE.equals(bodyValue)) {
            return new VoteResultWithCoverage(new VoteResult(Vote.abstain(metadata), deps), policyCoverage);
        }
        val constraintsResult = constraintsVoter.evaluate(ctx);
        mergeDependencies(deps, constraintsResult.dependencies());
        return new VoteResultWithCoverage(new VoteResult(constraintsResult.vote(), deps), policyCoverage);
    }

    /**
     * Orders the body conditions by stratum (constants, then pure operators,
     * then streams), preserving source order within each stratum. A constant
     * FALSE folds the body at compile time in production and a pure FALSE is
     * always resolvable, so walking the sync strata first lets such a FALSE
     * dominate and short-circuit before any stream is subscribed or awaited.
     *
     * @param conditions the body conditions in source order
     * @return the conditions reordered by stratum
     */
    static List<IndexedCompiledCondition> stratify(List<IndexedCompiledCondition> conditions) {
        val constants = new ArrayList<IndexedCompiledCondition>();
        val pures     = new ArrayList<IndexedCompiledCondition>();
        val streams   = new ArrayList<IndexedCompiledCondition>();
        for (val condition : conditions) {
            switch (condition.expression()) {
            case Value ignored          -> constants.add(condition);
            case PureOperator ignored   -> pures.add(condition);
            case StreamOperator ignored -> streams.add(condition);
            }
        }
        val ordered = new ArrayList<IndexedCompiledCondition>(conditions.size());
        ordered.addAll(constants);
        ordered.addAll(pures);
        ordered.addAll(streams);
        return ordered;
    }

    record Lazy(List<IndexedCompiledCondition> bodyConditions, Voter constraintsVoter, VoterMetadata metadata)
            implements CoverageVoter {

        public Lazy {
            bodyConditions = stratify(bodyConditions);
        }

        @Override
        public VoteResultWithCoverage evaluate(EvaluationContext ctx) {
            val   deps            = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(bodyConditions.size());
            val   hits            = new ArrayList<Coverage.ConditionHit>(bodyConditions.size());
            val   totalConditions = (long) bodyConditions.size();
            Value firstError      = null;

            for (val condition : bodyConditions) {
                val raw = evalChild(condition.expression(), ctx, deps);
                if (raw == null) {
                    val partial = new Coverage.PolicyCoverage(metadata,
                            new Coverage.BodyCoverage(hits, totalConditions));
                    return new VoteResultWithCoverage(new VoteResult(null, deps), partial);
                }
                val v = StratifiedBooleanOperationCompiler.asBoolean(raw, condition.location());
                hits.add(new Coverage.ConditionHit(v, condition.location(), condition.statementId()));
                if (Value.FALSE.equals(v)) {
                    return finishWithVote(Value.FALSE, deps, hits, totalConditions, constraintsVoter, metadata, ctx);
                }
                if (v instanceof ErrorValue && firstError == null) {
                    firstError = v;
                }
            }
            val bodyValue = firstError != null ? firstError : Value.TRUE;
            return finishWithVote(bodyValue, deps, hits, totalConditions, constraintsVoter, metadata, ctx);
        }
    }

    record Eager(List<IndexedCompiledCondition> bodyConditions, Voter constraintsVoter, VoterMetadata metadata)
            implements CoverageVoter {

        public Eager {
            bodyConditions = stratify(bodyConditions);
        }

        @Override
        public VoteResultWithCoverage evaluate(EvaluationContext ctx) {
            val   deps            = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(bodyConditions.size());
            val   hits            = new ArrayList<Coverage.ConditionHit>(bodyConditions.size());
            val   totalConditions = (long) bodyConditions.size();
            Value firstError      = null;

            // Constants and pures are synchronous and come first. A FALSE here decides the
            // body before any stream is gathered, so an unused stream is never subscribed.
            int firstStream = bodyConditions.size();
            for (int i = 0; i < bodyConditions.size(); i++) {
                val condition = bodyConditions.get(i);
                if (condition.expression() instanceof StreamOperator) {
                    firstStream = i;
                    break;
                }
                val v = StratifiedBooleanOperationCompiler.asBoolean(evalChild(condition.expression(), ctx, deps),
                        condition.location());
                hits.add(new Coverage.ConditionHit(v, condition.location(), condition.statementId()));
                if (Value.FALSE.equals(v)) {
                    return finishWithVote(Value.FALSE, deps, hits, totalConditions, constraintsVoter, metadata, ctx);
                }
                if (v instanceof ErrorValue && firstError == null) {
                    firstError = v;
                }
            }

            // Gather every stream dependency up front, then resolve in order. Only FALSE
            // short circuits. A carried error still loses to a later FALSE.
            val streamValues = new ArrayList<Value>(bodyConditions.size() - firstStream);
            for (int i = firstStream; i < bodyConditions.size(); i++) {
                streamValues.add(evalChild(bodyConditions.get(i).expression(), ctx, deps));
            }
            for (int i = 0; i < streamValues.size(); i++) {
                val v = streamValues.get(i);
                if (v == null) {
                    val partial = new Coverage.PolicyCoverage(metadata,
                            new Coverage.BodyCoverage(hits, totalConditions));
                    return new VoteResultWithCoverage(new VoteResult(null, deps), partial);
                }
                val condition = bodyConditions.get(firstStream + i);
                val coerced   = StratifiedBooleanOperationCompiler.asBoolean(v, condition.location());
                hits.add(new Coverage.ConditionHit(coerced, condition.location(), condition.statementId()));
                if (Value.FALSE.equals(coerced)) {
                    return finishWithVote(Value.FALSE, deps, hits, totalConditions, constraintsVoter, metadata, ctx);
                }
                if (coerced instanceof ErrorValue && firstError == null) {
                    firstError = coerced;
                }
            }
            val bodyValue = firstError != null ? firstError : Value.TRUE;
            return finishWithVote(bodyValue, deps, hits, totalConditions, constraintsVoter, metadata, ctx);
        }
    }

}
