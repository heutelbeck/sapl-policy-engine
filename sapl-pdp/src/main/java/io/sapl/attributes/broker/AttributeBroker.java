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
package io.sapl.attributes.broker;

import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Consumer contract between attribute sources and policy evaluation. Each
 * evaluation that needs attributes opens a {@link Subscription} with an initial
 * set of attribute keys and one evaluation callback. The broker manages source
 * invocations and routes changes; the callback evaluates the policy and reports
 * which keys it wants the broker to observe after that round.
 * <p>
 * The guarantees have separate purposes:
 * <ol>
 * <li><b>One stable input per round.</b> Each callback receives an immutable map
 * containing a value for every currently registered key. Later source changes
 * cannot modify that map while the policy uses it. A new callback starts a new
 * evaluation against a newly captured map; it does not resume an expression
 * using attribute results from the previous round.</li>
 * <li><b>Preserve source consistency.</b> For changes a source identifies as one
 * atomic update within a route, the broker installs all affected bindings before
 * an evaluation can capture the new view. Otherwise a correct repository transaction could still
 * produce a policy result from a combination that never existed. This does not
 * create a transaction across independent PIPs or repository routes. See
 * {@link AttributeRepository#observeState} for the source-side guarantees.</li>
 * <li><b>Observation sets are replaced in full.</b> The callback returns every
 * key it wants observed after that round, not a delta. For ordinary live keys,
 * this follows the dependencies the evaluation needed or touched, including
 * missing attributes. A loop can serve logical head dependencies from its own
 * cache instead of observing them here; see {@link BrokerEvalLoops}.
 * Adding a key attaches its source; removing a key releases only this consumer's
 * interest. Removal does not change repository data or by itself request
 * another evaluation.</li>
 * <li><b>Fulfillment before delivery.</b> An absent mailbox means the source has
 * not delivered a value yet. The callback waits until all registered keys are
 * fulfilled. A new missing dependency closes this gate again. A materialized
 * {@code UNDEFINED}, SAPL null or an error value is fulfilled data and is handled
 * by policy semantics. In particular, an unresolved external PIP can delay a
 * new round even if another key has changed.</li>
 * <li><b>Serialization without thread affinity.</b> Callbacks for one open
 * subscription never overlap. They run outside the broker lock but may use the
 * opening thread or a source notification thread. Separate subscriptions may
 * run concurrently. Slow callbacks can delay synchronous notification of other
 * consumers; there is no fairness or latency guarantee.</li>
 * <li><b>Changes can coalesce.</b> A callback already in progress retains its
 * captured inputs. Concurrent wake requests can collapse into a later callback
 * against newer inputs. There is no one-callback-per-change or
 * one-decision-per-commit guarantee. The broker does not decide which completed
 * policy results to publish or suppress.</li>
 * </ol>
 * The callback must handle evaluation failures without throwing onto a shared
 * source thread. The broker has no decision sink and cannot manufacture a
 * fail-closed policy decision for it. {@link BrokerEvalLoops} supplies the usual
 * evaluation-side guard and lifecycle handling.
 *
 * @since 4.1.0
 */
public interface AttributeBroker extends AutoCloseable {

    /**
     * Opens one consumer's observation. Initial dependencies and the callback
     * are installed together so a source change cannot reach a half-registered
     * consumer. If the initial keys are already fulfilled, opening requests a
     * callback immediately and may execute it inline before returning the
     * handle. A racing producer may own delivery instead; callers cannot rely
     * on a particular callback thread or completion before return. They must
     * be ready to receive results before obtaining the handle. Otherwise
     * delivery waits for the last missing value.
     * <p>
     * {@code onUpdate} receives a complete map for the current dependency set
     * and returns its full, non-empty effective observation set. Already evaluated
     * live attributes remain dependencies if their changes can affect the
     * result. For example, discovering B after A=true keeps both A and B until
     * the next round reevaluates them. If that round reads A=false and skips B,
     * it returns only A.
     * Cached head values can remain logical dependencies while the loop omits
     * them from the broker's effective observation set.
     * <p>
     * The broker applies additions and removals after the callback returns.
     * Adding a fulfilled key requests another round when the whole resulting
     * set is fulfilled, so a policy can complete without another publication.
     * Adding an unfulfilled key waits for its value. Removing keys alone does
     * not request a round. Shared source invocations are reference-counted;
     * releasing this handle need not stop a source still used by others.
     * <p>
     * Callbacks must return normally with a non-null, non-empty set. A thrown
     * runtime exception or a null or empty return is a programming error. The
     * PIP broker logs it and retains the previous dependencies as a last-resort
     * backstop, rather than throwing on the producer thread. This does not
     * produce a decision; the evaluation-side guard must report its own failure.
     * A completed evaluation with no remaining logical dependencies must handle
     * termination without passing an empty set to the broker; see
     * {@link BrokerEvalLoops#openWithHead}.
     *
     * @param subscriptionId non-blank identifier, unique among open subscriptions;
     * reusable after the previous subscription is closed
     * @param initialDependencies non-empty keys discovered by an initial evaluation;
     * an evaluation without attribute dependencies does not open a broker subscription
     * @param onUpdate serialized callback receiving the immutable current inputs
     * and returning the complete effective set of keys to observe afterwards
     * @return handle owned and eventually closed by the consumer
     * @throws IllegalArgumentException if {@code subscriptionId} is
     * blank or already in use, or if {@code initialDependencies} is
     * empty
     * @throws IllegalStateException if the broker has been closed
     */
    Subscription open(String subscriptionId, Set<SubscriptionKey> initialDependencies,
            Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate);

    /**
     * Closes all subscriptions and active invocations and rejects new opens.
     * Closing prevents later dispatches from selecting a callback, but does not
     * join callbacks already selected or executing. Such a callback may still
     * begin or finish after this method returns. Consumers must guard their
     * result publication if they require cancellation to suppress late output.
     */
    @Override
    void close();

    /**
     * Per-consumer lifecycle handle. The subscription's behaviour
     * (snapshot reads, dependency updates) lives entirely in the
     * callback wired at {@link AttributeBroker#open(String, Set, Function)}
     * time. This handle is solely for releasing the subscription when
     * the consumer is done.
     *
     * @since 4.1.0
     */
    interface Subscription extends AutoCloseable {

        /**
         * Detaches this consumer and releases its dependency references.
         * Idempotent and safe from any thread. Already selected or running
         * callbacks are not joined and may still begin or finish; subsequent
         * dependency changes returned by them are ignored after detachment.
         * A source with no remaining consumers can be released immediately or
         * after an implementation's grace period. This does not unbind its
         * repository value or cancel another consumer's observation.
         */
        @Override
        void close();
    }
}
