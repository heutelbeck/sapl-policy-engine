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
package io.sapl.compiler.pdp;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.compiler.document.PureVoter;
import io.sapl.compiler.document.StreamVoter;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.document.VoteResult;
import io.sapl.compiler.document.VoteResultWithCoverage;
import io.sapl.compiler.document.Voter;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.policy.CoverageVoter;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.List;
import java.util.Map;

/**
 * Guards the root voter and the coverage voter of a compiled PDP against a
 * stack overflow while a subscription is evaluated. A document controls how deeply its evaluation
 * nests, for example through a long chain of variables, and the compiler
 * accepts such a document. The guard turns the overflow into an INDETERMINATE
 * vote for the subscription that was evaluated, so that the caller receives a
 * decision and its stream stays open for the next configuration, instead of an
 * error that escapes the PDP.
 */
@UtilityClass
class StackOverflowGuard {

    private static final String ERROR_EVALUATION_TOO_DEEP = "The policies nest too deeply to be evaluated, for example through a long chain of variables.";

    /**
     * @param voter the root voter of a PDP configuration
     * @param metadata the identity of the PDP configuration, which the vote of
     * an overflow names
     * @param functionReuse whether repeated pure calls share results for this round
     * @return the voter, which answers an overflow with an INDETERMINATE vote
     */
    static Voter guarded(Voter voter, PdpVoterMetadata metadata, boolean functionReuse) {
        val overflowVote = overflowVote(metadata);
        return switch (voter) {
        case Vote vote          -> vote;
        case PureVoter pure     -> new GuardedPureVoter(pure, overflowVote, functionReuse);
        case StreamVoter stream -> new GuardedStreamVoter(stream, overflowVote, functionReuse);
        };
    }

    /**
     * @param voter the coverage voter of a PDP configuration, which test runs
     * evaluate
     * @param metadata the identity of the PDP configuration, which the vote of
     * an overflow names
     * @param functionReuse whether repeated pure calls share results for this round
     * @return the voter, which answers an overflow with an INDETERMINATE vote
     * and no coverage of documents
     */
    static CoverageVoter guarded(CoverageVoter voter, PdpVoterMetadata metadata, boolean functionReuse) {
        return new GuardedCoverageVoter(voter, overflowVote(metadata), metadata, functionReuse);
    }

    private static Vote overflowVote(PdpVoterMetadata metadata) {
        return Vote.error(new ErrorValue(ERROR_EVALUATION_TOO_DEEP), metadata);
    }

    private static EvaluationContext evaluationContext(EvaluationContext context, boolean functionReuse) {
        if (functionReuse) {
            return context.newFunctionEvaluation();
        }
        return context;
    }

    /**
     * A coverage voter whose overflow is the given vote, covering no document.
     *
     * @param voter the guarded voter
     * @param overflowVote the vote of an overflow
     * @param functionReuse whether repeated pure calls share results for this round
     * @param metadata the identity of the PDP configuration
     */
    record GuardedCoverageVoter(
            CoverageVoter voter,
            Vote overflowVote,
            PdpVoterMetadata metadata,
            boolean functionReuse) implements CoverageVoter {

        @Override
        public VoteResultWithCoverage evaluate(EvaluationContext ctx) {
            try {
                return voter.evaluate(evaluationContext(ctx, functionReuse));
            } catch (StackOverflowError overflow) {
                return new VoteResultWithCoverage(new VoteResult(overflowVote, Map.of()),
                        new Coverage.PdpCoverage(metadata, List.of()));
            }
        }
    }

    /**
     * A pure voter whose overflow is the given vote.
     *
     * @param voter the guarded voter
     * @param overflowVote the vote of an overflow
     * @param functionReuse whether repeated pure calls share results for this round
     */
    record GuardedPureVoter(PureVoter voter, Vote overflowVote, boolean functionReuse) implements PureVoter {

        @Override
        public Vote vote(EvaluationContext ctx) {
            try {
                return voter.vote(evaluationContext(ctx, functionReuse));
            } catch (StackOverflowError overflow) {
                return overflowVote;
            }
        }
    }

    /**
     * A streaming voter whose overflow is the given vote. The vote carries no
     * dependencies, so the evaluation ends with it until the configuration
     * changes.
     *
     * @param voter the guarded voter
     * @param overflowVote the vote of an overflow
     * @param functionReuse whether repeated pure calls share results for this round
     */
    record GuardedStreamVoter(StreamVoter voter, Vote overflowVote, boolean functionReuse) implements StreamVoter {

        @Override
        public VoteResult evaluate(EvaluationContext ctx) {
            try {
                return voter.evaluate(evaluationContext(ctx, functionReuse));
            } catch (StackOverflowError overflow) {
                return new VoteResult(overflowVote, Map.of());
            }
        }
    }
}
