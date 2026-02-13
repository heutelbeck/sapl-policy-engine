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

import static io.sapl.node.MetricsVoteInterceptor.METRIC_DECISIONS;
import static io.sapl.node.MetricsVoteInterceptor.METRIC_SUBSCRIPTIONS_ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.Outcome;
import io.sapl.compiler.document.TimestampedVote;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.pdp.PdpVoterMetadata;
import io.sapl.pdp.VoteInterceptor;
import io.sapl.spring.pdp.embedded.EmbeddedPDPProperties;
import lombok.val;

@DisplayName("MetricsConfiguration")
class MetricsConfigurationTests {

    private static final AuthorizationSubscription SUBSCRIPTION = AuthorizationSubscription.of("subject", "action",
            "resource");

    @EnableConfigurationProperties(EmbeddedPDPProperties.class)
    static class TestConfiguration {

    }

    @Nested
    @DisplayName("when metrics enabled")
    class WhenMetricsEnabled {

        @Test
        @DisplayName("registers interceptor that records decision metrics")
        void whenMetricsEnabled_thenInterceptorRecordsDecisions() {
            contextRunner(true).run(context -> {
                assertThat(context).hasSingleBean(VoteInterceptor.class);

                val interceptor = context.getBean(VoteInterceptor.class);
                val registry    = context.getBean(MeterRegistry.class);

                interceptor.onSubscribe("sub-1", SUBSCRIPTION);
                interceptor.intercept(voteWithDecision(Decision.PERMIT), "sub-1", SUBSCRIPTION);
                interceptor.onUnsubscribe("sub-1");

                assertThat(registry.find(METRIC_DECISIONS).counter()).isNotNull()
                        .satisfies(counter -> assertThat(counter.count()).isEqualTo(1.0));
                assertThat(registry.find(METRIC_SUBSCRIPTIONS_ACTIVE).gauge()).isNotNull();
            });
        }

    }

    @Nested
    @DisplayName("when metrics disabled")
    class WhenMetricsDisabled {

        @Test
        @DisplayName("interceptor is a no-op and records no metrics")
        void whenMetricsDisabled_thenNoMetricsRecorded() {
            contextRunner(false).run(context -> {
                val interceptor = context.getBean(VoteInterceptor.class);
                val registry    = context.getBean(MeterRegistry.class);

                interceptor.onSubscribe("sub-1", SUBSCRIPTION);
                interceptor.intercept(voteWithDecision(Decision.PERMIT), "sub-1", SUBSCRIPTION);
                interceptor.onUnsubscribe("sub-1");

                assertThat(registry.find(METRIC_DECISIONS).counter()).isNull();
                assertThat(registry.find(METRIC_SUBSCRIPTIONS_ACTIVE).gauge()).isNull();
            });
        }

    }

    private static ApplicationContextRunner contextRunner(boolean metricsEnabled) {
        return new ApplicationContextRunner().withUserConfiguration(TestConfiguration.class, MetricsConfiguration.class)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues("io.sapl.pdp.embedded.metrics-enabled=" + metricsEnabled);
    }

    private static TimestampedVote voteWithDecision(Decision decision) {
        val authzDecision = switch (decision) {
                          case PERMIT         -> AuthorizationDecision.PERMIT;
                          case DENY           -> AuthorizationDecision.DENY;
                          case INDETERMINATE  -> AuthorizationDecision.INDETERMINATE;
                          case NOT_APPLICABLE -> AuthorizationDecision.NOT_APPLICABLE;
                          };
        val voterMetadata = new PdpVoterMetadata("pdp", "default", "config-1", null, Outcome.PERMIT, false);
        val vote          = new Vote(authzDecision, List.of(), List.of(), List.of(), voterMetadata,
                voterMetadata.outcome());
        return new TimestampedVote(vote, "2026-02-13T00:00:00Z");
    }

}
