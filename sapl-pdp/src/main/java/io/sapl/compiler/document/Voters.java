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
package io.sapl.compiler.document;

import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.attributes.broker.BrokerEvalLoops;
import io.sapl.attributes.broker.EvaluationException;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Vote-shaped wrappers around the generic eval-loop helpers in
 * {@link BrokerEvalLoops}: each method drives a Voter against an
 * {@link AttributeBroker} until the first round produces a non-null
 * vote, then returns that vote (or a wrapped form of it).
 */
@UtilityClass
public class Voters {

    /**
     * Bridges the callback-driven {@link AttributeBroker} to a synchronous
     * single-vote pickup. Subscribes to {@code initialDependencies},
     * runs {@code evaluator} on each fulfilled snapshot, and returns the
     * first non-null vote produced. The broker handle is released before
     * this method returns, regardless of outcome.
     *
     * @param broker the attribute broker to subscribe through
     * @param subscriptionId per-evaluation id for the broker
     * @param initialDependencies the keys to subscribe to initially
     * @param evaluator a callback that evaluates against a snapshot and
     * returns a fresh {@link VoteResult}; the next dependency set fed
     * back to the broker is taken from {@link VoteResult#dependencies()}
     * @return the first non-null vote produced by the evaluator
     * @throws InterruptedException if the calling thread is interrupted
     * while waiting for the first complete snapshot
     */
    public static Vote awaitFirstVote(AttributeBroker broker, String subscriptionId,
            Set<SubscriptionKey> initialDependencies,
            Function<Map<SubscriptionKey, AttributeSnapshot>, VoteResult> evaluator)
            throws InterruptedException, EvaluationException {
        return BrokerEvalLoops.awaitFirstResult(broker, subscriptionId, initialDependencies, evaluator,
                (r, snap) -> r.vote(), r -> r.dependencies().keySet());
    }

    /**
     * Traced variant of {@link #awaitFirstVote}. Returns the first
     * snapshot round whose {@link VoteResult#vote()} is non-null,
     * wrapped as a {@link TracedVote} carrying the emit timestamp
     * (read from {@code clock}) and the dependency-filtered snapshot.
     */
    public static TracedVote awaitFirstTracedVote(AttributeBroker broker, String subscriptionId,
            Set<SubscriptionKey> initialDependencies,
            Function<Map<SubscriptionKey, AttributeSnapshot>, VoteResult> evaluator, Clock clock)
            throws InterruptedException, EvaluationException {
        return BrokerEvalLoops.awaitFirstResult(broker, subscriptionId, initialDependencies, evaluator,
                (r, snap) -> r.vote() == null ? null
                        : new TracedVote(r.vote(), clock.instant(), r.dependencies(), readSnapshot(r, snap)),
                r -> r.dependencies().keySet());
    }

    /**
     * Coverage-instrumented analogue of {@link #awaitFirstVote}. Drives
     * a {@link io.sapl.compiler.policy.CoverageVoter}-backed evaluator
     * and returns the first {@link VoteResultWithCoverage} whose
     * {@link VoteResult#vote()} is non-null, packaged as a
     * {@link VoteWithCoverage}.
     */
    public static VoteWithCoverage awaitFirstVoteWithCoverage(AttributeBroker broker, String subscriptionId,
            Set<SubscriptionKey> initialDependencies,
            Function<Map<SubscriptionKey, AttributeSnapshot>, VoteResultWithCoverage> evaluator)
            throws InterruptedException, EvaluationException {
        return BrokerEvalLoops.awaitFirstResult(broker, subscriptionId, initialDependencies, evaluator,
                (r, snap) -> r.voteResult().vote() == null ? null
                        : new VoteWithCoverage(r.voteResult().vote(), r.coverage()),
                r -> r.voteResult().dependencies().keySet());
    }

    /**
     * Returns the snapshot entries for the keys in
     * {@code result.dependencies()}. Bounded by the dependency set.
     */
    public static Map<SubscriptionKey, AttributeSnapshot> readSnapshot(VoteResult result,
            Map<SubscriptionKey, AttributeSnapshot> snapshot) {
        val filtered = new HashMap<SubscriptionKey, AttributeSnapshot>(result.dependencies().size());
        for (val key : result.dependencies().keySet()) {
            val entry = snapshot.get(key);
            if (entry != null) {
                filtered.put(key, entry);
            }
        }
        return Map.copyOf(filtered);
    }
}
