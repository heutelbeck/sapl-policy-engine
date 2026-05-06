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

import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.compiler.document.TimestampedVote;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.document.VoteWithCoverage;
import io.sapl.compiler.document.Voter;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.function.Supplier;

public record CompiledPdpVoter(
        PdpVoterMetadata voterMetadata,
        Voter pdpVoter,
        FunctionBroker functionBroker,
        Supplier<String> timestampSupplier) {

    private static final String ERROR_STREAMING_COVERAGE_NOT_YET_IMPLEMENTED = "PDP-level streaming voteWithCoverage not yet implemented";
    private static final String ERROR_STREAMING_NOT_YET_IMPLEMENTED = "PDP-level streaming vote not yet implemented";
    private static final String ERROR_VOTER_PRODUCED_NO_DECISION = "Voter produced no decision.";

    public TimestampedVote voteOnce(AuthorizationSubscription authorizationSubscription, String subscriptionId) {
        val vote = pdpVoter.evaluate(evaluationContext(authorizationSubscription, subscriptionId)).vote();
        if (vote == null) {
            return indeterminateVote();
        }
        return new TimestampedVote(vote, timestampSupplier.get());
    }

    private TimestampedVote indeterminateVote() {
        val error = new ErrorValue(ERROR_VOTER_PRODUCED_NO_DECISION);
        val vote  = Vote.error(error, voterMetadata);
        return new TimestampedVote(vote, timestampSupplier.get());
    }

    // TODO: implement PDP-level streaming vote pipeline (snapshot trigger
    // loop wrapped in Flux at the boundary). Until then, only one-shot
    // evaluations whose snapshot completes synchronously are supported.
    public Flux<TimestampedVote> vote(AuthorizationSubscription authorizationSubscription, String subscriptionId) {
        val vote = pdpVoter.evaluate(evaluationContext(authorizationSubscription, subscriptionId)).vote();
        if (vote == null) {
            return Flux.error(new UnsupportedOperationException(ERROR_STREAMING_NOT_YET_IMPLEMENTED));
        }
        return Flux.just(new TimestampedVote(vote, timestampSupplier.get()));
    }

    // TODO: implement PDP-level streaming voteWithCoverage pipeline.
    public Flux<VoteWithCoverage> voteWithCoverage(AuthorizationSubscription authorizationSubscription,
            String subscriptionId) {
        return Flux.error(new UnsupportedOperationException(ERROR_STREAMING_COVERAGE_NOT_YET_IMPLEMENTED));
    }

    private EvaluationContext evaluationContext(AuthorizationSubscription authorizationSubscription,
            String subscriptionId) {
        return EvaluationContext.of(voterMetadata().pdpId(), voterMetadata.configurationId(), subscriptionId,
                authorizationSubscription, functionBroker);
    }
}
