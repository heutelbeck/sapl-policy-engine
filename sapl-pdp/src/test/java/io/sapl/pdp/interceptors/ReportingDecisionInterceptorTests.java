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
package io.sapl.pdp.interceptors;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.CombiningAlgorithm.VotingMode;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.Outcome;
import io.sapl.ast.PolicySetVoterMetadata;
import io.sapl.ast.PolicyVoterMetadata;
import io.sapl.compiler.document.TimestampedVote;
import io.sapl.compiler.document.Vote;
import io.sapl.pdp.VoteInterceptor;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("ReportingDecisionInterceptor")
class ReportingDecisionInterceptorTests {

    private static final String                    DUMMY_TIMESTAMP       = "2026-01-01T00:00:00Z";
    private static final String                    DUMMY_SUBSCRIPTION_ID = "sub-123";
    private static final AuthorizationSubscription DUMMY_SUBSCRIPTION    = AuthorizationSubscription.of("testUser",
            "read", "testResource");
    private static final CombiningAlgorithm        DENY_OVERRIDES        = new CombiningAlgorithm(
            VotingMode.PRIORITY_DENY, DefaultDecision.ABSTAIN, ErrorHandling.PROPAGATE);

    @Nested
    @DisplayName("when intercepting votes")
    class WhenIntercepting {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        @DisplayName("executes without exception for logging flag combinations")
        void whenInterceptWithLoggingFlagsThenNoException(String description, boolean prettyPrint, boolean trace,
                boolean json, boolean text, boolean subscribe, boolean unsubscribe, Decision decision) {
            val interceptor = new ReportingDecisionInterceptor(prettyPrint, trace, json, text, subscribe, unsubscribe);
            val vote        = createTimestampedVote(decision);

            interceptor.intercept(vote, DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION);
        }

        private static Stream<Arguments> whenInterceptWithLoggingFlagsThenNoException() {
            return Stream.of(
                    arguments("all logging disabled", false, false, false, false, false, false, Decision.PERMIT),
                    arguments("trace logging enabled", false, true, false, false, false, false, Decision.PERMIT),
                    arguments("JSON report enabled", false, false, true, false, false, false, Decision.DENY),
                    arguments("text report enabled", false, false, false, true, false, false, Decision.INDETERMINATE),
                    arguments("all logging enabled", false, true, true, true, true, true, Decision.PERMIT));
        }

        @Test
        @DisplayName("handles vote with contributing votes")
        void whenInterceptWithContributingVotesThenNoException() {
            val interceptor = new ReportingDecisionInterceptor(false, false, false, true, false, false);
            val policyVoter = new PolicyVoterMetadata("test-policy", "pdp", "config", null, Outcome.PERMIT, false);
            val policyVote  = Vote.tracedVote(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED,
                    policyVoter, List.of());

            val setVoter        = new PolicySetVoterMetadata("test-set", "pdp", "config", null, DENY_OVERRIDES,
                    Outcome.PERMIT, false);
            val vote            = Vote.combinedVote(AuthorizationDecision.PERMIT, setVoter, List.of(policyVote),
                    Outcome.PERMIT);
            val timestampedVote = new TimestampedVote(vote, DUMMY_TIMESTAMP);

            interceptor.intercept(timestampedVote, DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION);
        }

    }

    @Nested
    @DisplayName("when handling lifecycle events")
    class WhenLifecycleEvents {

        @Test
        @DisplayName("logs subscription event when enabled")
        void whenOnSubscribeWithLoggingEnabledThenNoException() {
            val interceptor = new ReportingDecisionInterceptor(false, false, false, false, true, false);

            interceptor.onSubscribe(DUMMY_SUBSCRIPTION_ID, DUMMY_SUBSCRIPTION);
        }

        @Test
        @DisplayName("logs unsubscription event when enabled")
        void whenOnUnsubscribeWithLoggingEnabledThenNoException() {
            val interceptor = new ReportingDecisionInterceptor(false, false, false, false, false, true);

            interceptor.onUnsubscribe(DUMMY_SUBSCRIPTION_ID);
        }

    }

    @Nested
    @DisplayName("when ordering interceptors")
    class WhenOrdering {

        @Test
        @DisplayName("returns highest priority to execute last")
        void whenGetPriorityThenReturnsMaxValue() {
            val interceptor = new ReportingDecisionInterceptor(false, false, false, false, false, false);

            assertThat(interceptor.priority()).isEqualTo(Integer.MAX_VALUE);
        }

        @Test
        @DisplayName("implements VoteInterceptor interface")
        void whenCreatedThenImplementsInterface() {
            val interceptor = new ReportingDecisionInterceptor(false, false, false, false, false, false);

            assertThat(interceptor).isInstanceOf(VoteInterceptor.class);
        }

        @Test
        @DisplayName("sorts after lower-priority interceptors")
        void whenComparedWithOtherInterceptorThenOrderedByPriority() {
            val reportingInterceptor     = new ReportingDecisionInterceptor(false, false, false, false, false, false);
            val lowerPriorityInterceptor = new VoteInterceptor() {
                                             @Override
                                             public void intercept(TimestampedVote vote, String subscriptionId,
                                                     AuthorizationSubscription authorizationSubscription) {
                                                 // no-op
                                             }

                                             @Override
                                             public int priority() {
                                                 return 100;
                                             }
                                         };

            assertThat(reportingInterceptor.compareTo(lowerPriorityInterceptor)).isPositive();
            assertThat(lowerPriorityInterceptor.compareTo(reportingInterceptor)).isNegative();
        }

    }

    private static TimestampedVote createTimestampedVote(Decision decision) {
        val authzDecision = switch (decision) {
                          case PERMIT         -> AuthorizationDecision.PERMIT;
                          case DENY           -> AuthorizationDecision.DENY;
                          case INDETERMINATE  -> AuthorizationDecision.INDETERMINATE;
                          case NOT_APPLICABLE -> AuthorizationDecision.NOT_APPLICABLE;
                          };
        val voter         = new PolicySetVoterMetadata("test-set", "cthulhu-pdp", "test-security", null, DENY_OVERRIDES,
                Outcome.PERMIT, false);
        val vote          = new Vote(authzDecision, List.of(), List.of(), List.of(), voter, Outcome.PERMIT);
        return new TimestampedVote(vote, DUMMY_TIMESTAMP);
    }
}
