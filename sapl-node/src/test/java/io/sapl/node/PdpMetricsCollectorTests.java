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

import static io.sapl.node.PdpMetricsCollector.METRIC_DECISIONS;
import static io.sapl.node.PdpMetricsCollector.METRIC_FIRST_DECISION_LATENCY;
import static io.sapl.node.PdpMetricsCollector.METRIC_SUBSCRIPTIONS_ACTIVE;
import static io.sapl.node.PdpMetricsCollector.METRIC_SUBSCRIPTION_DURATION;
import static io.sapl.node.PdpMetricsCollector.TAG_DECISION;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.compiler.document.TracedVote;
import io.sapl.compiler.document.Vote;
import io.sapl.ast.Outcome;
import io.sapl.compiler.pdp.PdpVoterMetadata;
import lombok.val;

import java.time.Instant;

@DisplayName("PdpMetricsCollector")
class PdpMetricsCollectorTests {

    private SimpleMeterRegistry meterRegistry;
    private PdpMetricsCollector interceptor;

    private static final AuthorizationSubscription SUBSCRIPTION = AuthorizationSubscription.of("subject", "action",
            "resource");

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        interceptor   = new PdpMetricsCollector(true, meterRegistry);
    }

    @Nested
    @DisplayName("decision counting")
    class DecisionCounting {

        @ParameterizedTest(name = "counts {0} decisions")
        @MethodSource("allDecisions")
        @DisplayName("increments counter for each decision type")
        void whenDecisionEmittedThenCounterIncrements(Decision decision) {
            val vote = voteWithDecision(decision);

            interceptor.onDecision(vote, Instant.parse("2026-02-13T00:00:00Z"), "sub-1", SUBSCRIPTION);
            interceptor.onDecision(vote, Instant.parse("2026-02-13T00:00:00Z"), "sub-1", SUBSCRIPTION);

            val count = meterRegistry.counter(METRIC_DECISIONS, TAG_DECISION, decision.name()).count();
            assertThat(count).isEqualTo(2.0);
        }

        static Stream<Decision> allDecisions() {
            return Stream.of(Decision.PERMIT, Decision.DENY, Decision.INDETERMINATE, Decision.NOT_APPLICABLE);
        }

    }

    @Nested
    @DisplayName("active subscriptions gauge")
    class ActiveSubscriptions {

        @Test
        @DisplayName("tracks active subscription count")
        void whenSubscriptionsStartAndEndThenGaugeReflectsCount() {
            interceptor.onSubscribe("sub-1", SUBSCRIPTION, "test-pdp");
            interceptor.onSubscribe("sub-2", SUBSCRIPTION, "test-pdp");

            assertThat(gaugeValue()).isEqualTo(2.0);

            interceptor.onUnsubscribe("sub-1");

            assertThat(gaugeValue()).isEqualTo(1.0);

            interceptor.onUnsubscribe("sub-2");

            assertThat(gaugeValue()).isEqualTo(0.0);
        }

        private double gaugeValue() {
            return meterRegistry.get(METRIC_SUBSCRIPTIONS_ACTIVE).gauge().value();
        }

    }

    @Nested
    @DisplayName("latency tracking")
    class LatencyTracking {

        @Test
        @DisplayName("records first decision latency only once per subscription")
        void whenMultipleDecisionsThenRecordsFirstOnly() {
            interceptor.onSubscribe("sub-1", SUBSCRIPTION, "test-pdp");

            interceptor.onDecision(voteWithDecision(Decision.PERMIT), Instant.parse("2026-02-13T00:00:00Z"), "sub-1",
                    SUBSCRIPTION);
            interceptor.onDecision(voteWithDecision(Decision.DENY), Instant.parse("2026-02-13T00:00:00Z"), "sub-1",
                    SUBSCRIPTION);

            val timer = meterRegistry.find(METRIC_FIRST_DECISION_LATENCY).timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("records subscription duration on unsubscribe")
        void whenUnsubscribedThenRecordsDuration() {
            interceptor.onSubscribe("sub-1", SUBSCRIPTION, "test-pdp");
            interceptor.onDecision(voteWithDecision(Decision.PERMIT), Instant.parse("2026-02-13T00:00:00Z"), "sub-1",
                    SUBSCRIPTION);
            interceptor.onUnsubscribe("sub-1");

            val timer = meterRegistry.find(METRIC_SUBSCRIPTION_DURATION).timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }

    }

    @Nested
    @DisplayName("cleanup")
    class Cleanup {

        @Test
        @DisplayName("cleans up state on unsubscribe")
        void whenUnsubscribedThenNewSubscriptionWithSameIdStartsFresh() {
            interceptor.onSubscribe("sub-1", SUBSCRIPTION, "test-pdp");
            interceptor.onDecision(voteWithDecision(Decision.PERMIT), Instant.parse("2026-02-13T00:00:00Z"), "sub-1",
                    SUBSCRIPTION);
            interceptor.onUnsubscribe("sub-1");

            interceptor.onSubscribe("sub-1", SUBSCRIPTION, "test-pdp");
            interceptor.onDecision(voteWithDecision(Decision.DENY), Instant.parse("2026-02-13T00:00:00Z"), "sub-1",
                    SUBSCRIPTION);
            interceptor.onUnsubscribe("sub-1");

            val firstDecisionTimer = meterRegistry.find(METRIC_FIRST_DECISION_LATENCY).timer();
            assertThat(firstDecisionTimer).isNotNull();
            assertThat(firstDecisionTimer.count()).isEqualTo(2);
        }

    }

    private static TracedVote voteWithDecision(Decision decision) {
        val authzDecision = switch (decision) {
                          case PERMIT         -> AuthorizationDecision.PERMIT;
                          case DENY           -> AuthorizationDecision.DENY;
                          case SUSPEND        -> AuthorizationDecision.SUSPEND;
                          case INDETERMINATE  -> AuthorizationDecision.INDETERMINATE;
                          case NOT_APPLICABLE -> AuthorizationDecision.NOT_APPLICABLE;
                          };
        val voterMetadata = new PdpVoterMetadata("pdp", "default", "config-1", null, Outcome.PERMIT, false);
        val vote          = new Vote(authzDecision, List.of(), List.of(), voterMetadata, voterMetadata.outcome());
        return TracedVote.of(vote, Instant.parse("2026-02-13T00:00:00Z"));
    }

}
