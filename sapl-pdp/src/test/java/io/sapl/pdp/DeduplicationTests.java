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

import tools.jackson.databind.json.JsonMapper;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.CombiningAlgorithm.VotingMode;
import io.sapl.api.pdp.Decision;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static io.sapl.pdp.PdpTestHelper.configuration;
import static io.sapl.pdp.PdpTestHelper.subscription;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Diagnostic tests for verifying that {@code distinctUntilChanged} in
 * {@link DynamicPolicyDecisionPoint#decide} correctly suppresses duplicate
 * decisions from time-triggered policy re-evaluation.
 * <p>
 * Investigates whether {@code distinctUntilChanged} in the embedded PDP
 * correctly suppresses duplicate decisions when {@code <time.now>} triggers
 * re-evaluation every second. Result: deduplication WORKS in the embedded
 * PDP. The duplicates observed via HTTP endpoint are not reproduced here.
 */
@DisplayName("Deduplication diagnostics")
class DeduplicationTests {

    private static final CombiningAlgorithm DENY_UNLESS_PERMIT = new CombiningAlgorithm(VotingMode.PRIORITY_PERMIT,
            DefaultDecision.DENY, ErrorHandling.ABSTAIN);

    /**
     * Simplified version of the NestJS demo streaming heartbeat policy.
     * Removes {@code <jwt.token>} dependency but keeps the {@code <time.now>}
     * streaming attribute and obligation that trigger re-evaluation.
     * <p>
     * With a fixed clock at second=10, the condition
     * {@code second >= 0 && second < 20 || second >= 40} always evaluates to
     * true, producing PERMIT with obligation on every re-evaluation tick.
     */
    private static final String TIME_TRIGGERED_PERMIT_POLICY = """
            policy "permit with time trigger"
            permit
                action == "stream:heartbeat";
                resource == "heartbeat";
                var second = time.secondOf(<time.now>);
                second >= 0 && second < 20 || second >= 40;
            obligation
                {"type": "logAccess", "message": "Streaming heartbeat access"}
            """;

    /** Fixed clock at second=10, always in PERMIT range (0-19). */
    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-15T00:00:10Z");

    @Nested
    @DisplayName("AuthorizationDecision equality chain")
    class EqualityChain {

        @Test
        @DisplayName("structurally identical PERMIT decisions with obligations are equal")
        void whenStructurallyIdenticalPermitDecisionsWithObligationsThenEqual() {
            val ob1 = Value.ofArray(ObjectValue.builder().put("type", Value.of("logAccess"))
                    .put("message", Value.of("Streaming heartbeat access")).build());
            val ob2 = Value.ofArray(ObjectValue.builder().put("type", Value.of("logAccess"))
                    .put("message", Value.of("Streaming heartbeat access")).build());

            val d1 = new AuthorizationDecision(Decision.PERMIT, ob1, Value.EMPTY_ARRAY, Value.UNDEFINED);
            val d2 = new AuthorizationDecision(Decision.PERMIT, ob2, Value.EMPTY_ARRAY, Value.UNDEFINED);

            assertThat(d1).isEqualTo(d2).hasSameHashCodeAs(d2);
        }
    }

    @Nested
    @DisplayName("PDP evaluation produces equal decisions for same policy")
    class EvaluationEquality {

        @Test
        @DisplayName("consecutive gatherVotes produce equal AuthorizationDecisions for time-triggered policy")
        void whenConsecutiveVotesFromTimePolicyThenDecisionsEqual() {
            val mapper      = JsonMapper.builder().build();
            val fixedClock  = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
            val components  = PolicyDecisionPointBuilder.withDefaults(mapper, fixedClock).build();
            val voterSource = components.pdpVoterSource();
            val pdp         = (DynamicPolicyDecisionPoint) components.pdp();

            voterSource.loadConfiguration(configuration(DENY_UNLESS_PERMIT, TIME_TRIGGERED_PERMIT_POLICY), false);

            val sub       = subscription("user", "stream:heartbeat", "heartbeat");
            val decisions = pdp.gatherVotes(sub).map(tv -> tv.vote().authorizationDecision()).take(3).collectList()
                    .block(Duration.ofSeconds(10));

            assertThat(decisions).isNotNull().hasSizeGreaterThanOrEqualTo(2);

            val first = decisions.getFirst();
            assertThat(decisions).allSatisfy(d -> {
                assertThat(d.decision()).as("decision").isEqualTo(first.decision());
                assertThat(d.obligations()).as("obligations").isEqualTo(first.obligations());
                assertThat(d.advice()).as("advice").isEqualTo(first.advice());
                assertThat(d.resource()).as("resource").isEqualTo(first.resource());
                assertThat(d).as("full equality").isEqualTo(first);
            });
        }
    }

    @Nested
    @DisplayName("distinctUntilChanged suppresses duplicate decisions")
    class DistinctUntilChanged {

        @Test
        @DisplayName("fixed clock: identical decisions suppressed (virtual time)")
        void whenFixedClockReEvaluationThenDuplicatesSuppressedVirtualTime() {
            val mapper     = JsonMapper.builder().build();
            val fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
            val sub        = subscription("user", "stream:heartbeat", "heartbeat");

            StepVerifier.withVirtualTime(() -> {
                val components  = PolicyDecisionPointBuilder.withDefaults(mapper, fixedClock).build();
                val voterSource = components.pdpVoterSource();
                val pdp         = components.pdp();
                voterSource.loadConfiguration(configuration(DENY_UNLESS_PERMIT, TIME_TRIGGERED_PERMIT_POLICY), false);
                return pdp.decide(sub);
            }).assertNext(d -> assertThat(d.decision()).isEqualTo(Decision.PERMIT)).expectNoEvent(Duration.ofSeconds(5))
                    .thenCancel().verify();
        }

        @Test
        @DisplayName("fixed clock: identical decisions suppressed (real time)")
        void whenFixedClockReEvaluationThenDuplicatesSuppressedRealTime() {
            val mapper      = JsonMapper.builder().build();
            val fixedClock  = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
            val components  = PolicyDecisionPointBuilder.withDefaults(mapper, fixedClock).build();
            val voterSource = components.pdpVoterSource();
            val pdp         = components.pdp();

            voterSource.loadConfiguration(configuration(DENY_UNLESS_PERMIT, TIME_TRIGGERED_PERMIT_POLICY), false);

            val sub = subscription("user", "stream:heartbeat", "heartbeat");

            StepVerifier.create(pdp.decide(sub).take(Duration.ofSeconds(3)))
                    .assertNext(d -> assertThat(d.decision()).isEqualTo(Decision.PERMIT)).expectComplete()
                    .verify(Duration.ofSeconds(5));
        }

        @Test
        @DisplayName("system clock: gatherVotes re-emits when time attribute used in condition")
        void whenSystemClockWithTimeInConditionThenMultipleVotesEmitted() {
            val mapper      = JsonMapper.builder().build();
            val components  = PolicyDecisionPointBuilder.withDefaults(mapper, Clock.systemUTC()).build();
            val voterSource = components.pdpVoterSource();
            val pdp         = (DynamicPolicyDecisionPoint) components.pdp();

            voterSource.loadConfiguration(configuration(DENY_UNLESS_PERMIT, TIME_TRIGGERED_PERMIT_POLICY), false);

            val sub       = subscription("user", "stream:heartbeat", "heartbeat");
            val decisions = pdp.gatherVotes(sub).map(tv -> tv.vote().authorizationDecision())
                    .take(Duration.ofSeconds(5)).collectList().block(Duration.ofSeconds(10));

            assertThat(decisions).isNotNull()
                    .as("With 1-second polling and time used in condition, " + "expect multiple votes in 5 seconds")
                    .hasSizeGreaterThan(1);
        }

        @Test
        @DisplayName("system clock: decide() emits only distinct decisions despite time-triggered re-evaluation")
        void whenSystemClockReEvaluationThenDistinctDecisionsOnly() {
            val mapper      = JsonMapper.builder().build();
            val components  = PolicyDecisionPointBuilder.withDefaults(mapper, Clock.systemUTC()).build();
            val voterSource = components.pdpVoterSource();
            val pdp         = components.pdp();

            voterSource.loadConfiguration(configuration(DENY_UNLESS_PERMIT, TIME_TRIGGERED_PERMIT_POLICY), false);

            val sub       = subscription("user", "stream:heartbeat", "heartbeat");
            val decisions = pdp.decide(sub).take(Duration.ofSeconds(5)).collectList().block(Duration.ofSeconds(10));

            assertThat(decisions).isNotNull()
                    .as("distinctUntilChanged should suppress duplicate decisions. "
                            + "Expect at most 2 distinct decisions (PERMIT during 0-19/40-59s, DENY during 20-39s)")
                    .hasSizeLessThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("CompiledPdpVoter equality and config source deduplication")
    class ConfigDeduplication {

        private static final String SIMPLE_PERMIT = """
                policy "test"
                permit
                """;

        @Test
        @DisplayName("separately compiled CompiledPdpVoter instances from same config are not equal")
        void whenSameConfigCompiledTwiceThenNotEqual() {
            val mapper      = JsonMapper.builder().build();
            val components  = PolicyDecisionPointBuilder.withoutDefaults(mapper, Clock.systemUTC()).build();
            val voterSource = components.pdpVoterSource();
            val config      = configuration(DENY_UNLESS_PERMIT, SIMPLE_PERMIT);

            voterSource.loadConfiguration(config, false);
            val first = voterSource.getCurrentConfiguration("default");

            voterSource.loadConfiguration(config, false);
            val second = voterSource.getCurrentConfiguration("default");

            assertThat(first).isPresent();
            assertThat(second).isPresent();
            assertThat(first.get()).isNotEqualTo(second.get());
        }

        @Test
        @DisplayName("config source distinctUntilChanged does not suppress duplicate compilation")
        void whenSameConfigReloadedThenConfigSourceEmitsBothCompilations() {
            val mapper      = JsonMapper.builder().build();
            val components  = PolicyDecisionPointBuilder.withoutDefaults(mapper, Clock.systemUTC()).build();
            val voterSource = components.pdpVoterSource();
            val config      = configuration(DENY_UNLESS_PERMIT, SIMPLE_PERMIT);

            voterSource.loadConfiguration(config, false);

            StepVerifier.create(voterSource.getPDPConfigurations("default").take(2))
                    .assertNext(opt -> assertThat(opt).isPresent())
                    .then(() -> voterSource.loadConfiguration(config, false))
                    .assertNext(opt -> assertThat(opt).isPresent()).expectComplete().verify(Duration.ofSeconds(5));
        }

        @Test
        @DisplayName("decide() deduplicates despite config source emitting duplicate compilations")
        void whenSameConfigReloadedThenDecideDeduplicatesAtDecisionLevel() {
            val mapper      = JsonMapper.builder().build();
            val components  = PolicyDecisionPointBuilder.withoutDefaults(mapper, Clock.systemUTC()).build();
            val voterSource = components.pdpVoterSource();
            val pdp         = components.pdp();
            val config      = configuration(DENY_UNLESS_PERMIT, SIMPLE_PERMIT);

            voterSource.loadConfiguration(config, false);

            val sub = subscription("user", "read", "data");

            StepVerifier.create(pdp.decide(sub)).assertNext(d -> assertThat(d.decision()).isEqualTo(Decision.PERMIT))
                    .then(() -> voterSource.loadConfiguration(config, false)).expectNoEvent(Duration.ofMillis(500))
                    .thenCancel().verify(Duration.ofSeconds(5));
        }
    }
}
