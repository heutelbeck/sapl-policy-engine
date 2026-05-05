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
 * Walks the body conditions once per round, accumulates dependencies and
 * per-condition hits, and dispatches when
 * the body resolves to {@link Value#TRUE}. Two implementations select
 * the body-walk strategy at compile time:
 * <ul>
 * <li>{@link Lazy} short-circuits at the first {@link ErrorValue},
 * {@link Value#FALSE}, or incomplete child. Minimal subscription set;
 * convergence may take multiple trigger-loop rounds when independent
 * dependencies are missing.</li>
 * <li>{@link Eager} walks every condition unconditionally to gather the
 * full subscription set, then applies the same in-order short-circuit
 * semantic to produce the body value. Larger subscription set;
 * single-round convergence once the snapshot is sufficient.</li>
 * </ul>
 * Observable {@link VoteResultWithCoverage} is identical across both
 * variants; only the per-pass subscription set differs.
 *
 * @since 4.2.0
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

    record Lazy(List<IndexedCompiledCondition> bodyConditions, Voter constraintsVoter, VoterMetadata metadata)
            implements CoverageVoter {

        @Override
        public VoteResultWithCoverage evaluate(EvaluationContext ctx) {
            val   deps            = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(bodyConditions.size());
            val   hits            = new ArrayList<Coverage.ConditionHit>(bodyConditions.size());
            val   totalConditions = (long) bodyConditions.size();
            Value bodyValue       = Value.TRUE;

            for (val condition : bodyConditions) {
                val v = evalChild(condition.expression(), ctx, deps);
                if (v == null) {
                    val partial = new Coverage.PolicyCoverage(metadata,
                            new Coverage.BodyCoverage(hits, totalConditions));
                    return new VoteResultWithCoverage(new VoteResult(null, deps), partial);
                }
                hits.add(new Coverage.ConditionHit(v, condition.location(), condition.statementId()));
                if (v instanceof ErrorValue || Value.FALSE.equals(v)) {
                    bodyValue = v;
                    break;
                }
            }
            return finishWithVote(bodyValue, deps, hits, totalConditions, constraintsVoter, metadata, ctx);
        }
    }

    record Eager(List<IndexedCompiledCondition> bodyConditions, Voter constraintsVoter, VoterMetadata metadata)
            implements CoverageVoter {

        @Override
        public VoteResultWithCoverage evaluate(EvaluationContext ctx) {
            val deps            = HashMap.<SubscriptionKey, List<Occurrence>>newHashMap(bodyConditions.size());
            val totalConditions = (long) bodyConditions.size();

            // Phase 1: walk every condition to gather the full dep set; may contain nulls.
            val values = new ArrayList<Value>(bodyConditions.size());
            for (val condition : bodyConditions) {
                values.add(evalChild(condition.expression(), ctx, deps));
            }

            // Phase 2: in-order short-circuit scan; matches the lazy hit sequence and body
            // value.
            val   hits      = new ArrayList<Coverage.ConditionHit>(bodyConditions.size());
            Value bodyValue = Value.TRUE;
            for (int i = 0; i < bodyConditions.size(); i++) {
                val v = values.get(i);
                if (v == null) {
                    val partial = new Coverage.PolicyCoverage(metadata,
                            new Coverage.BodyCoverage(hits, totalConditions));
                    return new VoteResultWithCoverage(new VoteResult(null, deps), partial);
                }
                val condition = bodyConditions.get(i);
                hits.add(new Coverage.ConditionHit(v, condition.location(), condition.statementId()));
                if (v instanceof ErrorValue || Value.FALSE.equals(v)) {
                    bodyValue = v;
                    break;
                }
            }
            return finishWithVote(bodyValue, deps, hits, totalConditions, constraintsVoter, metadata, ctx);
        }
    }

    /**
     * Placeholder coverage voter for combining algorithms whose coverage
     * path has not yet been migrated to the snapshot model. Throws on
     * {@link #evaluate(EvaluationContext)} so callers fail loudly with
     * a message naming the unmigrated algorithm rather than silently
     * producing degraded results.
     */
    record NotMigrated(String algorithmName) implements CoverageVoter {
        @Override
        public VoteResultWithCoverage evaluate(EvaluationContext ctx) {
            throw new UnsupportedOperationException(
                    "CoverageVoter not yet migrated for combining algorithm " + algorithmName);
        }
    }
}
