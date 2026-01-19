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
package io.sapl.compiler.combining;

import io.sapl.api.model.*;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.CombiningAlgorithm.DefaultDecision;
import io.sapl.ast.CombiningAlgorithm.ErrorHandling;
import io.sapl.ast.Outcome;
import io.sapl.ast.PolicySet;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.pdp.*;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@UtilityClass
public class PriorityVoteCompiler {
    public static VoterAndCoverage compilePolicySet(PolicySet policySet,
            List<? extends CompiledDocument> compiledPolicies, CompiledExpression isApplicable,
            VoterMetadata voterMetadata, Decision priorityDecision, DefaultDecision defaultDecision,
            ErrorHandling errorHandling) {
        val voter    = compileVoter(compiledPolicies, voterMetadata, priorityDecision, defaultDecision, errorHandling);
        val coverage = compileCoverageStream(policySet, isApplicable, compiledPolicies, voterMetadata, priorityDecision,
                defaultDecision, errorHandling);
        return new VoterAndCoverage(voter, coverage);
    }

    private record ClassifiedPolicies(
            List<Vote> foldableVotes,
            List<CompiledDocument> purePolicies,
            List<CompiledDocument> streamPolicies) {}

    private static Voter compileVoter(List<? extends CompiledDocument> compiledPolicies, VoterMetadata voterMetadata,
            Decision priorityDecision, DefaultDecision defaultDecision, ErrorHandling errorHandling) {

        val classified      = classifyPoliciesByEvaluationStrategy(compiledPolicies);
        val accumulatorVote = PriorityBasedVoteCombiner.combineMultipleVotes(classified.foldableVotes(),
                priorityDecision, voterMetadata);

        if (classified.purePolicies().isEmpty() && classified.streamPolicies().isEmpty()) {
            return finalizeVote(accumulatorVote, defaultDecision, errorHandling);
        }

        if (classified.streamPolicies().isEmpty()) {
            return new PurePriorityVoter(accumulatorVote, classified.purePolicies(), priorityDecision, defaultDecision,
                    errorHandling, voterMetadata);
        }
        return new StreamPriorityVoter(accumulatorVote, classified.purePolicies(), classified.streamPolicies(),
                priorityDecision, defaultDecision, errorHandling, voterMetadata);
    }

    private static ClassifiedPolicies classifyPoliciesByEvaluationStrategy(
            List<? extends CompiledDocument> compiledPolicies) {
        var foldableVotes  = new ArrayList<Vote>();
        var purePolicies   = new ArrayList<CompiledDocument>();
        var streamPolicies = new ArrayList<CompiledDocument>();

        for (var policy : compiledPolicies) {
            val isApplicable = policy.isApplicable();
            val voter        = policy.voter();

            if (isApplicable instanceof Value constantApplicable) {
                if (constantApplicable instanceof BooleanValue(var b) && !b) {
                    continue; // constant FALSE - not applicable, skip
                }
                // constant TRUE or ERROR
                if (voter instanceof Vote vote) {
                    foldableVotes.add(vote);
                } else if (voter instanceof StreamVoter) {
                    streamPolicies.add(policy);
                } else {
                    purePolicies.add(policy);
                }
            } else if (voter instanceof StreamVoter) {
                streamPolicies.add(policy);
            } else {
                purePolicies.add(policy);
            }
        }
        return new ClassifiedPolicies(foldableVotes, purePolicies, streamPolicies);
    }

    record PurePriorityVoter(
            Vote accumulatorVote,
            List<CompiledDocument> documents,
            Decision priorityDecision,
            DefaultDecision defaultDecision,
            ErrorHandling errorHandling,
            VoterMetadata voterMetadata) implements PureVoter {
        @Override
        public Vote vote(EvaluationContext ctx) {
            val vote = combinePureVoters(accumulatorVote, documents, priorityDecision, voterMetadata, ctx);
            return finalizeVote(vote, defaultDecision, errorHandling);
        }
    }

    record StreamPriorityVoter(
            Vote accumulatorVote,
            List<CompiledDocument> pureDocuments,
            List<CompiledDocument> streamDocuments,
            Decision priorityDecision,
            DefaultDecision defaultDecision,
            ErrorHandling errorHandling,
            VoterMetadata voterMetadata) implements StreamVoter {
        @Override
        public Flux<Vote> vote() {
            return Flux.deferContextual(ctxView -> {
                val evalCtx      = ctxView.get(EvaluationContext.class);
                var pureVote     = combinePureVoters(accumulatorVote, pureDocuments, priorityDecision, voterMetadata,
                        evalCtx);
                val streamVoters = new ArrayList<Flux<Vote>>(streamDocuments.size() + 1);
                streamVoters.add(Flux.empty());
                for (CompiledDocument document : streamDocuments) {
                    Value isApplicable;
                    val   isApplicableExpression = document.isApplicable();
                    if (isApplicableExpression instanceof Value v) {
                        isApplicable = v;
                    } else {
                        isApplicable = ((PureOperator) isApplicableExpression).evaluate(evalCtx);
                    }

                    if (isApplicable instanceof ErrorValue error) {
                        var errorVote = Vote.error(error, document.metadata());
                        pureVote = PriorityBasedVoteCombiner.combineVotes(pureVote, errorVote, priorityDecision,
                                voterMetadata);
                    } else if (isApplicable instanceof BooleanValue(var b) && b) {
                        streamVoters.add(((StreamVoter) document.voter()).vote());
                    }
                }
                streamVoters.set(0, Flux.just(pureVote));
                return Flux
                        .combineLatest(streamVoters,
                                votes -> PriorityBasedVoteCombiner.combineMultipleVotes(asTypedList(votes),
                                        priorityDecision, voterMetadata))
                        .map(vote -> finalizeVote(vote, defaultDecision, errorHandling));
            });
        }

        @SuppressWarnings("unchecked")
        private static <T> List<T> asTypedList(Object[] array) {
            return (List<T>) (List<?>) Arrays.asList(array);
        }
    }

    private static Vote combinePureVoters(Vote accumulatorVote, List<CompiledDocument> documents,
            Decision priorityDecision, VoterMetadata voterMetadata, EvaluationContext ctx) {
        var vote = accumulatorVote;
        for (val document : documents) {
            Value isApplicable;
            val   isApplicableExpression = document.isApplicable();
            if (isApplicableExpression instanceof Value v) {
                isApplicable = v;
            } else {
                isApplicable = ((PureOperator) isApplicableExpression).evaluate(ctx);
            }

            if (isApplicable instanceof ErrorValue error) {
                var errorVote = Vote.error(error, document.metadata());
                vote = PriorityBasedVoteCombiner.combineVotes(vote, errorVote, priorityDecision, voterMetadata);
            } else if (isApplicable instanceof BooleanValue(var b) && b) {
                val  voter = document.voter();
                Vote newVote;
                if (voter instanceof Vote constantVote) {
                    newVote = constantVote;
                } else {
                    newVote = ((PureVoter) voter).vote(ctx);
                }
                vote = PriorityBasedVoteCombiner.combineVotes(vote, newVote, priorityDecision, voterMetadata);
            }
        }
        return vote;
    }

    private static Vote finalizeVote(Vote accumulatedVote, DefaultDecision defaultDecision,
            ErrorHandling errorHandling) {
        if (accumulatedVote.authorizationDecision().decision() == Decision.NOT_APPLICABLE) {
            return switch (defaultDecision) {
            case ABSTAIN -> accumulatedVote;
            case DENY    -> replaceDecision(accumulatedVote, Decision.DENY, Outcome.DENY);
            case PERMIT  -> replaceDecision(accumulatedVote, Decision.PERMIT, Outcome.PERMIT);
            };
        }
        if (accumulatedVote.authorizationDecision().decision() == Decision.INDETERMINATE) {
            return switch (errorHandling) {
            case ABSTAIN   -> replaceDecision(accumulatedVote, Decision.NOT_APPLICABLE, accumulatedVote.outcome());
            case PROPAGATE -> accumulatedVote;
            };
        }
        return accumulatedVote;
    }

    private static Vote replaceDecision(Vote accumulatedVote, Decision decision, Outcome outcome) {
        val originalAuthorizationDecision = accumulatedVote.authorizationDecision();
        val newAuthorizationDecision      = new AuthorizationDecision(decision,
                originalAuthorizationDecision.obligations(), originalAuthorizationDecision.advice(),
                originalAuthorizationDecision.resource());
        return new Vote(newAuthorizationDecision, accumulatedVote.errors(), accumulatedVote.contributingAttributes(),
                accumulatedVote.contributingVotes(), accumulatedVote.voter(), outcome);
    }

    private static Flux<VoteWithCoverage> compileCoverageStream(PolicySet policySet, CompiledExpression isApplicable,
            List<? extends CompiledDocument> compiledPolicies, VoterMetadata voterMetadata, Decision priorityDecision,
            DefaultDecision defaultDecision, ErrorHandling errorHandling) {
        throw new SaplCompilerException("Unimplemented %s, %s, %s, %s, %s, %s, %s".formatted(policySet, isApplicable,
                compiledPolicies, voterMetadata, priorityDecision, defaultDecision, errorHandling));
    }

}
