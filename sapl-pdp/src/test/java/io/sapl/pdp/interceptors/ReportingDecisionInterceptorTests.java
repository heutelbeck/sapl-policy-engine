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

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.internal.TracedDecision;
import io.sapl.api.pdp.internal.TracedDecisionInterceptor;
import io.sapl.api.pdp.internal.TracedPdpDecision;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReportingDecisionInterceptor")
class ReportingDecisionInterceptorTests {

    @Test
    @DisplayName("returns highest priority to execute last")
    void whenGetPriority_thenReturnsMaxValue() {
        val interceptor = new ReportingDecisionInterceptor(false, false, false, false);

        assertThat(interceptor.getPriority()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("passes through traced decision unchanged when all logging disabled")
    void whenApplyWithAllLoggingDisabled_thenTracedDecisionIsPassedThrough() {
        val interceptor    = new ReportingDecisionInterceptor(false, false, false, false);
        val tracedDecision = createTracedDecision(Decision.PERMIT);

        val result = interceptor.apply(tracedDecision);

        assertThat(result).isSameAs(tracedDecision);
    }

    @Test
    @DisplayName("passes through traced decision unchanged when trace logging enabled")
    void whenApplyWithTraceLogging_thenTracedDecisionIsPassedThrough() {
        val interceptor    = new ReportingDecisionInterceptor(false, true, false, false);
        val tracedDecision = createTracedDecision(Decision.PERMIT);

        val result = interceptor.apply(tracedDecision);

        assertThat(result).isSameAs(tracedDecision);
    }

    @Test
    @DisplayName("passes through traced decision unchanged when JSON report enabled")
    void whenApplyWithJsonReport_thenTracedDecisionIsPassedThrough() {
        val interceptor    = new ReportingDecisionInterceptor(false, false, true, false);
        val tracedDecision = createTracedDecision(Decision.DENY);

        val result = interceptor.apply(tracedDecision);

        assertThat(result).isSameAs(tracedDecision);
    }

    @Test
    @DisplayName("passes through traced decision unchanged when text report enabled")
    void whenApplyWithTextReport_thenTracedDecisionIsPassedThrough() {
        val interceptor    = new ReportingDecisionInterceptor(false, false, false, true);
        val tracedDecision = createTracedDecision(Decision.INDETERMINATE);

        val result = interceptor.apply(tracedDecision);

        assertThat(result).isSameAs(tracedDecision);
    }

    @Test
    @DisplayName("passes through traced decision unchanged when all logging enabled")
    void whenApplyWithAllLoggingEnabled_thenTracedDecisionIsPassedThrough() {
        val interceptor    = new ReportingDecisionInterceptor(true, true, true, true);
        val tracedDecision = createTracedDecision(Decision.PERMIT);

        val result = interceptor.apply(tracedDecision);

        assertThat(result).isSameAs(tracedDecision);
    }

    @Test
    @DisplayName("implements TracedDecisionInterceptor interface")
    void whenCreated_thenImplementsInterface() {
        val interceptor = new ReportingDecisionInterceptor(false, false, false, false);

        assertThat(interceptor).isInstanceOf(TracedDecisionInterceptor.class);
    }

    @Test
    @DisplayName("handles modified traced decision")
    void whenApplyWithModifiedDecision_thenNoException() {
        val interceptor    = new ReportingDecisionInterceptor(false, false, false, true);
        val tracedDecision = createTracedDecision(Decision.PERMIT).modified(AuthorizationDecision.DENY,
                "The stars are not right");

        val result = interceptor.apply(tracedDecision);

        assertThat(result).isSameAs(tracedDecision);
        assertThat(result.modifications()).contains("The stars are not right");
    }

    @Test
    @DisplayName("has correct comparison ordering based on priority")
    void whenComparedWithOtherInterceptor_thenOrderedByPriority() {
        val reportingInterceptor     = new ReportingDecisionInterceptor(false, false, false, false);
        val lowerPriorityInterceptor = new TracedDecisionInterceptor() {
                                         @Override
                                         public TracedDecision apply(TracedDecision tracedDecision) {
                                             return tracedDecision;
                                         }

                                         @Override
                                         public Integer getPriority() {
                                             return 100;
                                         }
                                     };

        // Reporting (MAX_VALUE) should come after lower priority (100)
        assertThat(reportingInterceptor.compareTo(lowerPriorityInterceptor)).isPositive();
        assertThat(lowerPriorityInterceptor.compareTo(reportingInterceptor)).isNegative();
    }

    private TracedDecision createTracedDecision(Decision decision) {
        val trace = TracedPdpDecision.builder().pdpId("cthulhu-pdp").configurationId("test-security")
                .subscriptionId("sub-001").subscription(AuthorizationSubscription.of("cultist", "summon", "elder-god"))
                .timestamp(Instant.now().toString()).algorithm("deny-overrides").decision(decision).totalDocuments(1)
                .build();
        return new TracedDecision(trace);
    }
}
