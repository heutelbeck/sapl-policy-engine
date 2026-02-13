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
package io.sapl.node;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.compiler.document.TimestampedVote;
import io.sapl.pdp.VoteInterceptor;
import lombok.val;

/**
 * Records PDP decision metrics for Prometheus via Micrometer.
 *
 * <p>
 * Captures the four golden signals for authorization decisions:
 * <ul>
 * <li>Traffic: decision counter by outcome (PERMIT, DENY, ...)</li>
 * <li>Latency: time to first decision per subscription</li>
 * <li>Saturation: active subscription count</li>
 * <li>Errors: INDETERMINATE decisions (subset of decision counter)</li>
 * </ul>
 * Additionally tracks subscription lifetime for operational insight.
 *
 * <p>
 * Gated by the {@code enabled} flag. When disabled, all callbacks return
 * immediately without touching the meter registry -- zero cost at runtime.
 * The flag is a final field so the JIT can eliminate the dead branches
 * entirely.
 */
class MetricsVoteInterceptor implements VoteInterceptor {

    static final String METRIC_DECISIONS              = "sapl.decisions";
    static final String METRIC_FIRST_DECISION_LATENCY = "sapl.decision.first.latency";
    static final String METRIC_SUBSCRIPTIONS_ACTIVE   = "sapl.subscriptions.active";
    static final String METRIC_SUBSCRIPTION_DURATION  = "sapl.subscription.duration";
    static final String TAG_DECISION                  = "decision";

    private final boolean       enabled;
    private final MeterRegistry meterRegistry;
    private final AtomicInteger activeSubscriptions;

    private final ConcurrentHashMap<String, Timer.Sample> subscriptionTimers     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean>      firstDecisionDelivered = new ConcurrentHashMap<>();

    MetricsVoteInterceptor(boolean enabled, MeterRegistry meterRegistry) {
        this.enabled             = enabled;
        this.meterRegistry       = meterRegistry;
        this.activeSubscriptions = enabled ? meterRegistry.gauge(METRIC_SUBSCRIPTIONS_ACTIVE, new AtomicInteger(0))
                : new AtomicInteger(0);
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public void onSubscribe(String subscriptionId, AuthorizationSubscription authorizationSubscription) {
        if (!enabled) {
            return;
        }
        activeSubscriptions.incrementAndGet();
        subscriptionTimers.put(subscriptionId, Timer.start(meterRegistry));
    }

    @Override
    public void intercept(TimestampedVote vote, String subscriptionId,
            AuthorizationSubscription authorizationSubscription) {
        if (!enabled) {
            return;
        }
        val decision = vote.vote().authorizationDecision().decision();
        meterRegistry.counter(METRIC_DECISIONS, TAG_DECISION, decision.name()).increment();

        if (firstDecisionDelivered.putIfAbsent(subscriptionId, Boolean.TRUE) == null) {
            val sample = subscriptionTimers.get(subscriptionId);
            if (sample != null) {
                sample.stop(meterRegistry.timer(METRIC_FIRST_DECISION_LATENCY));
            }
        }
    }

    @Override
    public void onUnsubscribe(String subscriptionId) {
        if (!enabled) {
            return;
        }
        activeSubscriptions.decrementAndGet();
        firstDecisionDelivered.remove(subscriptionId);
        val sample = subscriptionTimers.remove(subscriptionId);
        if (sample != null) {
            sample.stop(meterRegistry.timer(METRIC_SUBSCRIPTION_DURATION));
        }
    }

}
