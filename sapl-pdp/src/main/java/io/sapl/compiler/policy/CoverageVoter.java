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
import java.util.List;
import java.util.Map;

import static io.sapl.api.model.StreamOperator.mergeDependencies;

/** Evaluates an authorization vote and its coverage for the same round. */
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

    /** Source-compatible adapter for callers supplying precompiled conditions. */
    record Lazy(List<IndexedCompiledCondition> bodyConditions, Voter constraintsVoter, VoterMetadata metadata)
            implements CoverageVoter {
        public Lazy {
            bodyConditions = List.copyOf(stratify(bodyConditions));
        }

        @Override
        public VoteResultWithCoverage evaluate(EvaluationContext ctx) {
            return PolicyCompiler.coverageForConditions(bodyConditions, constraintsVoter, metadata).evaluate(ctx);
        }
    }

    /** Source-compatible adapter using the same compiled body and dependency demand. */
    record Eager(List<IndexedCompiledCondition> bodyConditions, Voter constraintsVoter, VoterMetadata metadata)
            implements CoverageVoter {
        public Eager {
            bodyConditions = List.copyOf(stratify(bodyConditions));
        }

        @Override
        public VoteResultWithCoverage evaluate(EvaluationContext ctx) {
            return PolicyCompiler.coverageForConditions(bodyConditions, constraintsVoter, metadata).evaluate(ctx);
        }
    }
}
