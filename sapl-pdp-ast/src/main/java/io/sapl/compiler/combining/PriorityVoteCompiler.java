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

import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.CombiningAlgorithm.DefaultDecision;
import io.sapl.ast.CombiningAlgorithm.ErrorHandling;
import io.sapl.ast.PolicySet;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.pdp.Vote;
import io.sapl.compiler.pdp.Voter;
import io.sapl.compiler.policy.CompiledPolicy;
import io.sapl.compiler.pdp.VoteWithCoverage;
import io.sapl.ast.VoterMetadata;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.List;

@UtilityClass
public class PriorityVoteCompiler {
    public static VoterAndCoverage compilePolicySet(PolicySet policySet, List<CompiledPolicy> compiledPolicies,
            CompiledExpression isApplicable, VoterMetadata voterMetadata, Decision priorityDecision,
            DefaultDecision defaultDecision, ErrorHandling errorHandling) {
        val voter    = compileVoter(compiledPolicies, voterMetadata, policySet.location(), priorityDecision,
                defaultDecision, errorHandling);
        val coverage = compileCoverageStream(policySet, isApplicable, compiledPolicies, voterMetadata, priorityDecision,
                defaultDecision, errorHandling);
        return new VoterAndCoverage(voter, coverage);
    }

    private static Voter compileVoter(List<CompiledPolicy> compiledPolicies, VoterMetadata voterMetadata,
            @NonNull SourceLocation location, Decision priorityDecision, DefaultDecision defaultDecision,
            ErrorHandling errorHandling) {
        val index = StratifiedDocumentIndex.of(compiledPolicies, priorityDecision, voterMetadata);

        // Check if we can constant-fold this voter. If no other stratum beyond the
        // constant stratum is filled with policies, the vote calculated by the Index is
        // already the final one.
        if (index.isFullyConstant()) {
            return finalizeVote(index.accumulatorVote(), defaultDecision, errorHandling);
        }

        if (!index.hasStreamingPolicies()) {
            // TODO Stream Path
        }
        // TODO PURE PATH
        throw new SaplCompilerException("Unimplemented %s, %s, %s, %s, %s, %s".formatted(compiledPolicies,
                voterMetadata, location, priorityDecision, defaultDecision, errorHandling));
    }

    private static Vote finalizeVote(Vote accumulatedVote, DefaultDecision defaultDecision,
            ErrorHandling errorHandling) {
        if (accumulatedVote.authorizationDecision().decision() == Decision.NOT_APPLICABLE) {
            return switch (defaultDecision) {
            case ABSTAIN -> accumulatedVote;
            case DENY    -> replaceDecision(accumulatedVote, Decision.DENY);
            case PERMIT  -> replaceDecision(accumulatedVote, Decision.PERMIT);
            };
        }
        if (accumulatedVote.authorizationDecision().decision() == Decision.INDETERMINATE) {
            return switch (errorHandling) {
            case ABSTAIN   -> replaceDecision(accumulatedVote, Decision.NOT_APPLICABLE);
            case PROPAGATE -> accumulatedVote;
            };
        }
        return accumulatedVote;
    }

    private static Vote replaceDecision(Vote accumulatedVote, Decision decision) {
        val originalAuthorizationDecision = accumulatedVote.authorizationDecision();
        val newAuthorizationDecision      = new AuthorizationDecision(decision,
                originalAuthorizationDecision.obligations(), originalAuthorizationDecision.advice(),
                originalAuthorizationDecision.resource());
        return new Vote(newAuthorizationDecision, accumulatedVote.errors(), accumulatedVote.contributingAttributes(),
                accumulatedVote.contributingVotes(), accumulatedVote.voter());
    }

    private static Flux<VoteWithCoverage> compileCoverageStream(PolicySet policySet, CompiledExpression isApplicable,
            List<CompiledPolicy> compiledPolicies, VoterMetadata voterMetadata, Decision priorityDecision,
            DefaultDecision defaultDecision, ErrorHandling errorHandling) {
        throw new SaplCompilerException("Unimplemented %s, %s, %s, %s, %s, %s, %s".formatted(policySet, isApplicable,
                compiledPolicies, voterMetadata, priorityDecision, defaultDecision, errorHandling));
    }

}
