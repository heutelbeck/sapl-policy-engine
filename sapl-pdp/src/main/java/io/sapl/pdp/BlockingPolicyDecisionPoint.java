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
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.StreamingPolicyDecisionPoint;
import io.sapl.api.stream.Stream;
import io.sapl.attributes.store.AttributeStore;
import io.sapl.compiler.document.VoteWithCoverage;

/**
 * Reactor-free policy decision point implementing
 * {@link StreamingPolicyDecisionPoint}. Drives the compiled PDP voter
 * (and the engine-internal coverage voter) against a per-evaluation
 * {@link AttributeStore} subscription using the SAPL
 * {@link Stream} primitive; consumers block on each
 * {@link Stream#awaitNext()} call. Intended substrate for sapl-test
 * and other tooling consumers that should not pull Reactor as a
 * transitive dependency.
 */
public final class BlockingPolicyDecisionPoint implements StreamingPolicyDecisionPoint {

    private static final String ERROR_NOT_YET_IMPLEMENTED = "BlockingPolicyDecisionPoint not yet implemented.";

    @Override
    public AuthorizationDecision decideOnce(AuthorizationSubscription authorizationSubscription, String pdpId) {
        // TODO: implement decideOnce. Mirror ReactivePolicyDecisionPoint.voteSync /
        // evaluateOnceSync: fetch the CompiledPdp for pdpId, drive the production
        // voter through Voters.awaitFirstVote against the AttributeStore, return
        // the resulting AuthorizationDecision (INDETERMINATE on missing config).
        throw new UnsupportedOperationException(ERROR_NOT_YET_IMPLEMENTED);
    }

    @Override
    public Stream<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription, String pdpId) {
        // TODO: implement streaming decide for a single subscription. Open the
        // AttributeStore subscription, push each round's decision into a
        // LatestSlotStream, wrap with
        // Streams.distinctUntilChanged(s, e -> AuthorizationDecision.INDETERMINATE)
        // so transient evaluation failures surface as INDETERMINATE rather than
        // silent completion.
        throw new UnsupportedOperationException(ERROR_NOT_YET_IMPLEMENTED);
    }

    @Override
    public Stream<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiSubscription,
            String pdpId) {
        // TODO: implement streaming multi-decide. Cannot use the simple
        // distinctUntilChanged helper here: emission is per-identifier, not
        // per-bundle. Each round produces a MultiAuthorizationDecision; the
        // method must diff against the previous bundle and emit only the
        // IdentifiableAuthorizationDecision entries whose decision changed.
        // Mirror ReactivePolicyDecisionPoint.identifiableChanges(prev, current),
        // which itself carries a verification TODO regarding subscription removals
        // and dynamic subscription set changes. The diff state is held in an
        // AtomicReference<MultiAuthorizationDecision>; the pump thread does the
        // diff and pushes one IdentifiableAuthorizationDecision per change into
        // the output Stream.
        throw new UnsupportedOperationException(ERROR_NOT_YET_IMPLEMENTED);
    }

    @Override
    public Stream<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiSubscription,
            String pdpId) {
        // TODO: implement streaming decideAll. Open the AttributeStore subscription,
        // run evaluateRound across all sub-subscriptions on each snapshot, push the
        // bundled MultiAuthorizationDecision into a LatestSlotStream, wrap with
        // Streams.distinctUntilChanged(s, e ->
        // MultiAuthorizationDecision.indeterminate())
        // so the transport surfaces INDETERMINATE on transient evaluation failure.
        // The whole-bundle equals on MultiAuthorizationDecision compares the
        // decisions map by value, which is the correct dedupe key here.
        throw new UnsupportedOperationException(ERROR_NOT_YET_IMPLEMENTED);
    }

    /**
     * One-shot synchronous evaluation with coverage information for a
     * specific tenant. Engine-internal: not part of the
     * {@link StreamingPolicyDecisionPoint} contract; consumed by
     * sapl-test and tooling that need branch-coverage data alongside
     * the decision.
     */
    public VoteWithCoverage decideOnceWithCoverage(AuthorizationSubscription authorizationSubscription, String pdpId) {
        // TODO: implement decideOnceWithCoverage. Drive CompiledPdp.coverageVoter()
        // through a future-based blocking primitive against the AttributeStore,
        // return the VoteResultWithCoverage payload as VoteWithCoverage.
        throw new UnsupportedOperationException(ERROR_NOT_YET_IMPLEMENTED);
    }

    /**
     * Streaming evaluation with coverage information for a specific
     * tenant. Engine-internal: not part of the
     * {@link StreamingPolicyDecisionPoint} contract. Each round emits
     * a {@link VoteWithCoverage}; coverage emissions are NOT
     * deduplicated because consumers depend on observing every round's
     * branch hits.
     */
    public Stream<VoteWithCoverage> decideWithCoverage(AuthorizationSubscription authorizationSubscription,
            String pdpId) {
        // TODO: implement streaming decideWithCoverage. Open the AttributeStore
        // subscription, push every round's VoteWithCoverage into a QueueStream
        // (not LatestSlotStream: coverage data accumulates per round and a slow
        // consumer must observe every emission). Do NOT apply distinctUntilChanged.
        throw new UnsupportedOperationException(ERROR_NOT_YET_IMPLEMENTED);
    }

    @Override
    public AuthorizationDecision decideOnce(AuthorizationSubscription authorizationSubscription) {
        return decideOnce(authorizationSubscription, DEFAULT_PDP_ID);
    }

    @Override
    public Stream<AuthorizationDecision> decide(AuthorizationSubscription authorizationSubscription) {
        return decide(authorizationSubscription, DEFAULT_PDP_ID);
    }

    @Override
    public Stream<IdentifiableAuthorizationDecision> decide(MultiAuthorizationSubscription multiSubscription) {
        return decide(multiSubscription, DEFAULT_PDP_ID);
    }

    @Override
    public Stream<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiSubscription) {
        return decideAll(multiSubscription, DEFAULT_PDP_ID);
    }

    public VoteWithCoverage decideOnceWithCoverage(AuthorizationSubscription authorizationSubscription) {
        return decideOnceWithCoverage(authorizationSubscription, DEFAULT_PDP_ID);
    }

    public Stream<VoteWithCoverage> decideWithCoverage(AuthorizationSubscription authorizationSubscription) {
        return decideWithCoverage(authorizationSubscription, DEFAULT_PDP_ID);
    }
}
