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
package io.sapl.pdp;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.compiler.document.VoteWithCoverage;

/**
 * Synchronous, Reactor-free policy decision point. Drives the
 * compiled PDP voter and coverage voter against a per-evaluation
 * {@link io.sapl.compiler.eval.AttributeStore} subscription with a
 * future-based blocking primitive (the same shape used by
 * {@code ReactivePolicyDecisionPoint.evaluateOnce}). Intended as the
 * substrate for sapl-test and other testing or tooling consumers
 * that have no use for streaming evaluation and should not pull
 * Reactor as a transitive dependency.
 */
public final class BlockingPolicyDecisionPoint {

    private static final String ERROR_NOT_YET_IMPLEMENTED = "BlockingPolicyDecisionPoint not yet implemented.";

    /**
     * One-shot synchronous evaluation. Returns the first complete
     * authorization decision for the subscription against the tenant's
     * compiled PDP.
     *
     * @param authorizationSubscription the authorization subscription
     * @param pdpId the tenant identifier
     * @return the authorization decision
     */
    public AuthorizationDecision decideOnce(AuthorizationSubscription authorizationSubscription, String pdpId) {
        // TODO: implement decideOnce: drive CompiledPdp.voter() through
        // Voters.awaitFirstVote against the AttributeStore, return the
        // resulting AuthorizationDecision.
        throw new UnsupportedOperationException(ERROR_NOT_YET_IMPLEMENTED);
    }

    /**
     * One-shot synchronous evaluation with coverage information.
     * Drives the compiled PDP's coverage voter and returns the vote
     * together with the recorded branch coverage.
     *
     * @param authorizationSubscription the authorization subscription
     * @param pdpId the tenant identifier
     * @return the vote with coverage
     */
    public VoteWithCoverage voteOnceWithCoverage(AuthorizationSubscription authorizationSubscription, String pdpId) {
        // TODO: implement voteOnceWithCoverage: drive
        // CompiledPdp.coverageVoter() through a future-based blocking
        // primitive against the AttributeStore, return the
        // VoteResultWithCoverage payload as VoteWithCoverage.
        throw new UnsupportedOperationException(ERROR_NOT_YET_IMPLEMENTED);
    }
}
