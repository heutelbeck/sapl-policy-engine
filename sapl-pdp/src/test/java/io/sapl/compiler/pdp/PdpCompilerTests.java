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
package io.sapl.compiler.pdp;

import static io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision.ABSTAIN;
import static io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling.PROPAGATE;
import static io.sapl.api.pdp.CombiningAlgorithm.VotingMode.FIRST;
import static io.sapl.api.pdp.CombiningAlgorithm.VotingMode.PRIORITY_DENY;
import static io.sapl.api.pdp.CombiningAlgorithm.VotingMode.PRIORITY_PERMIT;
import static io.sapl.api.pdp.CombiningAlgorithm.VotingMode.UNIQUE;

import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.CombiningAlgorithm.VotingMode;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.compiler.model.Coverage;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.sapl.util.SaplTesting.compilationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("PdpCompiler")
class PdpCompilerTests {

    private static final String VALID_POLICY = """
            policy "test-policy"
            permit
            """;

    private static final String POLICY_A = """
            policy "policy-a"
            permit
            """;

    private static final String POLICY_B = """
            policy "policy-b"
            deny
            """;

    private static final String DUPLICATE_NAME_POLICY = """
            policy "duplicate"
            permit
            """;

    private static final String INVALID_POLICY = "this is not valid SAPL syntax!!!";

    @Nested
    @DisplayName("error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("parsing failure returns INDETERMINATE with coverage")
        void whenParsingFails_thenReturnsIndeterminateWithCoverage() {
            val config = new PDPConfiguration("test-pdp", "config-1",
                    new CombiningAlgorithm(PRIORITY_DENY, ABSTAIN, PROPAGATE), List.of(INVALID_POLICY), Map.of());

            val compiledVoter = PdpCompiler.compilePDPConfiguration(config, compilationContext());

            assertThat(compiledVoter.pdpVoter()).satisfies(voter -> {
                assertThat(voter).isInstanceOf(Vote.class);
                val vote = (Vote) voter;
                assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
                assertThat(vote.errors()).isNotEmpty();
            });

            StepVerifier.create(compiledVoter.coverageStream()).assertNext(vwc -> {
                assertThat(vwc.vote().authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
                assertThat(vwc.coverage()).isInstanceOf(Coverage.PolicySetCoverage.class);
                val coverage = (Coverage.PolicySetCoverage) vwc.coverage();
                assertThat(coverage.targetHit()).isEqualTo(Coverage.BLANK_TARGET_HIT);
                assertThat(coverage.policyCoverages()).isEmpty();
            }).verifyComplete();
        }

        @Test
        @DisplayName("name collision returns INDETERMINATE with coverage")
        void whenNameCollision_thenReturnsIndeterminateWithCoverage() {
            val config = new PDPConfiguration("test-pdp", "config-1",
                    new CombiningAlgorithm(PRIORITY_DENY, ABSTAIN, PROPAGATE),
                    List.of(DUPLICATE_NAME_POLICY, DUPLICATE_NAME_POLICY), Map.of());

            val compiledVoter = PdpCompiler.compilePDPConfiguration(config, compilationContext());

            assertThat(compiledVoter.pdpVoter()).satisfies(voter -> {
                assertThat(voter).isInstanceOf(Vote.class);
                val vote = (Vote) voter;
                assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
                assertThat(vote.errors()).anyMatch(e -> e.message().contains("duplicate"));
            });

            StepVerifier.create(compiledVoter.coverageStream()).assertNext(vwc -> {
                assertThat(vwc.vote().authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
                assertThat(vwc.coverage()).isInstanceOf(Coverage.PolicySetCoverage.class);
            }).verifyComplete();
        }

        @Test
        @DisplayName("FIRST algorithm at PDP level returns INDETERMINATE with coverage")
        void whenFirstAlgorithmAtPdpLevel_thenReturnsIndeterminateWithCoverage() {
            val config = new PDPConfiguration("test-pdp", "config-1", new CombiningAlgorithm(FIRST, ABSTAIN, PROPAGATE),
                    List.of(VALID_POLICY), Map.of());

            val compiledVoter = PdpCompiler.compilePDPConfiguration(config, compilationContext());

            assertThat(compiledVoter.pdpVoter()).satisfies(voter -> {
                assertThat(voter).isInstanceOf(Vote.class);
                val vote = (Vote) voter;
                assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
                assertThat(vote.errors()).anyMatch(e -> e.message().contains("FIRST"));
            });

            StepVerifier.create(compiledVoter.coverageStream()).assertNext(vwc -> {
                assertThat(vwc.vote().authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
                assertThat(vwc.coverage()).isInstanceOf(Coverage.PolicySetCoverage.class);
                val coverage = (Coverage.PolicySetCoverage) vwc.coverage();
                assertThat(coverage.targetHit()).isEqualTo(Coverage.BLANK_TARGET_HIT);
            }).verifyComplete();
        }
    }

    @Nested
    @DisplayName("combining algorithm behavior")
    class CombiningAlgorithmBehaviorTests {

        static Stream<Arguments> defaultDecisionCases() {
            return Stream.of(arguments(DefaultDecision.DENY, Decision.DENY),
                    arguments(DefaultDecision.PERMIT, Decision.PERMIT),
                    arguments(DefaultDecision.ABSTAIN, Decision.NOT_APPLICABLE));
        }

        @ParameterizedTest(name = "empty policy set with {0} default returns {1}")
        @MethodSource("defaultDecisionCases")
        void whenNoPolicies_thenReturnsDefaultDecision(DefaultDecision defaultDecision, Decision expected) {
            val algorithm = new CombiningAlgorithm(VotingMode.PRIORITY_DENY, defaultDecision, ErrorHandling.PROPAGATE);
            val config    = new PDPConfiguration("test-pdp", "config-1", algorithm, List.of(), Map.of());

            val compiledVoter = PdpCompiler.compilePDPConfiguration(config, compilationContext());

            assertThat(compiledVoter.pdpVoter()).satisfies(voter -> {
                assertThat(voter).isInstanceOf(Vote.class);
                assertThat(((Vote) voter).authorizationDecision().decision()).isEqualTo(expected);
            });
        }

        static Stream<Arguments> priorityAlgorithmCases() {
            return Stream.of(arguments(new CombiningAlgorithm(PRIORITY_DENY, ABSTAIN, PROPAGATE), Decision.DENY),
                    arguments(new CombiningAlgorithm(PRIORITY_PERMIT, ABSTAIN, PROPAGATE), Decision.PERMIT));
        }

        @ParameterizedTest(name = "{0} with conflicting policies returns {1}")
        @MethodSource("priorityAlgorithmCases")
        void whenPriorityAlgorithmWithConflict_thenPriorityWins(CombiningAlgorithm algorithm, Decision expected) {
            val config = new PDPConfiguration("test-pdp", "config-1", algorithm, List.of(POLICY_A, POLICY_B), Map.of());

            val compiledVoter = PdpCompiler.compilePDPConfiguration(config, compilationContext());

            StepVerifier.create(compiledVoter.coverageStream())
                    .assertNext(vwc -> assertThat(vwc.vote().authorizationDecision().decision()).isEqualTo(expected))
                    .verifyComplete();
        }

        @Test
        @DisplayName("unique algorithm with single policy returns that policy's decision")
        void whenUniqueWithSinglePolicy_thenReturnsPolicyDecision() {
            val config = new PDPConfiguration("test-pdp", "config-1",
                    new CombiningAlgorithm(UNIQUE, ABSTAIN, PROPAGATE), List.of(VALID_POLICY), Map.of());

            val compiledVoter = PdpCompiler.compilePDPConfiguration(config, compilationContext());

            StepVerifier.create(compiledVoter.coverageStream())
                    .assertNext(
                            vwc -> assertThat(vwc.vote().authorizationDecision().decision()).isEqualTo(Decision.PERMIT))
                    .verifyComplete();
        }

        @Test
        @DisplayName("unique algorithm with multiple applicable policies returns INDETERMINATE")
        void whenUniqueWithMultipleApplicable_thenReturnsIndeterminate() {
            val config = new PDPConfiguration("test-pdp", "config-1",
                    new CombiningAlgorithm(UNIQUE, ABSTAIN, PROPAGATE), List.of(POLICY_A, POLICY_B), Map.of());

            val compiledVoter = PdpCompiler.compilePDPConfiguration(config, compilationContext());

            StepVerifier.create(compiledVoter.coverageStream()).assertNext(
                    vwc -> assertThat(vwc.vote().authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE))
                    .verifyComplete();
        }
    }

}
