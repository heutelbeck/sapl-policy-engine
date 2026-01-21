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

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.function.Supplier;

public record CompiledPdpVoter(
        PdpVoterMetadata voterMetadata,
        Voter pdpVoter,
        Flux<VoteWithCoverage> coverageStream,
        Map<String, Value> variables,
        AttributeBroker attributeBroker,
        FunctionBroker functionBroker,
        Supplier<String> timestampSupplier) {

    public TimestampedVote voteOnce(AuthorizationSubscription authorizationSubscription, String subscriptionId) {
        if (pdpVoter instanceof Vote vote) {
            return new TimestampedVote(vote, timestampSupplier.get());
        }
        val evaluationContext = evaluationContext(authorizationSubscription, subscriptionId);
        if (pdpVoter instanceof PureVoter pureVoter) {
            return new TimestampedVote(pureVoter.vote(evaluationContext), timestampSupplier.get());
        }
        return ((StreamVoter) pdpVoter).vote()
                .contextWrite(ctxView -> ctxView.put(EvaluationContext.class, evaluationContext)).take(1)
                .map(vote -> new TimestampedVote(vote, timestampSupplier.get())).blockFirst();
    }

    public Flux<TimestampedVote> vote(AuthorizationSubscription authorizationSubscription, String subscriptionId) {
        if (pdpVoter instanceof Vote vote) {
            return Flux.just(new TimestampedVote(vote, timestampSupplier.get()));
        }
        val evaluationContext = evaluationContext(authorizationSubscription, subscriptionId);
        if (pdpVoter instanceof PureVoter pureVoter) {
            return Flux.just(new TimestampedVote(pureVoter.vote(evaluationContext), timestampSupplier.get()));
        }
        return ((StreamVoter) pdpVoter).vote().map(vote -> new TimestampedVote(vote, timestampSupplier.get()))
                .contextWrite(ctxView -> ctxView.put(EvaluationContext.class, evaluationContext));
    }

    private EvaluationContext evaluationContext(AuthorizationSubscription authorizationSubscription,
            String subscriptionId) {
        return EvaluationContext.of(voterMetadata().pdpId(), voterMetadata.configurationId(), subscriptionId,
                authorizationSubscription, variables, functionBroker, attributeBroker, timestampSupplier);
    }
}
