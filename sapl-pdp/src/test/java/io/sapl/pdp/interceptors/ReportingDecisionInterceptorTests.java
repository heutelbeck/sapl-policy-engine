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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReportingDecisionInterceptor")
class ReportingDecisionInterceptorTests {

    private static final String             DUMMY_TIMESTAMP = "2026-01-01T00:00:00Z";
    private static final CombiningAlgorithm DENY_OVERRIDES  = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.ABSTAIN, ErrorHandling.PROPAGATE);

    @Test
    @DisplayName("returns highest priority to execute last")
    void whenGetPriorityThenReturnsMaxValue() {
        val interceptor = new ReportingDecisionInterceptor(false, false, false, false);

        assertThat(interceptor.priority()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("executes without exception when all logging disabled")
    void whenInterceptWithAllLoggingDisabledThenNoException() {
        val interceptor = new ReportingDecisionInterceptor(false, false, false, false);
        val vote        = createTimestampedVote(Decision.PERMIT);

        // Should not throw
        interceptor.intercept(vote);
    }

    @Test
    @DisplayName("executes without exception when trace logging enabled")
    void whenInterceptWithTraceLoggingThenNoException() {
        val interceptor = new ReportingDecisionInterceptor(false, true, false, false);
        val vote        = createTimestampedVote(Decision.PERMIT);

        // Should not throw
        interceptor.intercept(vote);
    }

    @Test
    @DisplayName("executes without exception when JSON report enabled")
    void whenInterceptWithJsonReportThenNoException() {
        val interceptor = new ReportingDecisionInterceptor(false, false, true, false);
        val vote        = createTimestampedVote(Decision.DENY);

        // Should not throw
        interceptor.intercept(vote);
    }

    @Test
    @DisplayName("executes without exception when text report enabled")
    void whenInterceptWithTextReportThenNoException() {
        val interceptor = new ReportingDecisionInterceptor(false, false, false, true);
        val vote        = createTimestampedVote(Decision.INDETERMINATE);

        // Should not throw
        interceptor.intercept(vote);
    }

    @Test
    @DisplayName("executes without exception when all logging enabled")
    void whenInterceptWithAllLoggingEnabledThenNoException() {
        val interceptor = new ReportingDecisionInterceptor(false, true, true, true);
        val vote        = createTimestampedVote(Decision.PERMIT);

        // Should not throw
        interceptor.intercept(vote);
    }

    @Test
    @DisplayName("implements VoteInterceptor interface")
    void whenCreatedThenImplementsInterface() {
        val interceptor = new ReportingDecisionInterceptor(false, false, false, false);

        assertThat(interceptor).isInstanceOf(VoteInterceptor.class);
    }

    @Test
    @DisplayName("handles vote with contributing votes")
    void whenInterceptWithContributingVotesThenNoException() {
        val interceptor = new ReportingDecisionInterceptor(false, false, false, true);
        val policyVoter = new PolicyVoterMetadata("test-policy", "pdp", "config", null, Outcome.PERMIT, false);
        val policyVote  = Vote.tracedVote(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED,
                policyVoter, List.of());

        val setVoter        = new PolicySetVoterMetadata("test-set", "pdp", "config", null, DENY_OVERRIDES,
                Outcome.PERMIT, false);
        val vote            = Vote.combinedVote(AuthorizationDecision.PERMIT, setVoter, List.of(policyVote),
                Outcome.PERMIT);
        val timestampedVote = new TimestampedVote(vote, DUMMY_TIMESTAMP);

        // Should not throw
        interceptor.intercept(timestampedVote);
    }

    @Test
    @DisplayName("has correct comparison ordering based on priority")
    void whenComparedWithOtherInterceptorThenOrderedByPriority() {
        val reportingInterceptor     = new ReportingDecisionInterceptor(false, false, false, false);
        val lowerPriorityInterceptor = new VoteInterceptor() {
                                         @Override
                                         public void intercept(TimestampedVote vote) {
                                             // no-op
                                         }

                                         @Override
                                         public int priority() {
                                             return 100;
                                         }
                                     };

        // Reporting (MAX_VALUE) should come after lower priority (100)
        assertThat(reportingInterceptor.compareTo(lowerPriorityInterceptor)).isPositive();
        assertThat(lowerPriorityInterceptor.compareTo(reportingInterceptor)).isNegative();
    }

    private TimestampedVote createTimestampedVote(Decision decision) {
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
