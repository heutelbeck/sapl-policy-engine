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

import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.ast.Outcome;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.util.SaplTesting;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision.ABSTAIN;
import static io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling.PROPAGATE;
import static io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode.*;
import static io.sapl.util.SaplTesting.compilationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("PdpCompiler")
class PdpCompilerTests {

    private static Vote evaluate(CompiledPdp compiledPdp, AuthorizationSubscription subscription) {
        val ctx = EvaluationContext.of(compiledPdp.metadata().pdpId(), compiledPdp.metadata().configurationId(),
                "test-sub", subscription, SaplTesting.FUNCTION_BROKER);
        return compiledPdp.voter().evaluate(ctx).vote();
    }

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

    private static final AuthorizationSubscription DEFAULT_SUBSCRIPTION = AuthorizationSubscription.of(Value.NULL,
            Value.NULL, Value.NULL, Value.NULL);

    @Nested
    @DisplayName("error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("parsing failure throws SaplCompilerException")
        void whenParsingFailsThenThrowsSaplCompilerException() {
            val config  = new PDPConfiguration("test-pdp", "config-1",
                    new CombiningAlgorithm(PRIORITY_DENY, ABSTAIN, PROPAGATE), List.of(INVALID_POLICY),
                    new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
            val context = compilationContext();

            assertThatThrownBy(() -> PdpCompiler.compilePDPConfiguration(config, context))
                    .isInstanceOf(SaplCompilerException.class);
        }

        @Test
        @DisplayName("name collision throws SaplCompilerException")
        void whenNameCollisionThenThrowsSaplCompilerException() {
            val config  = new PDPConfiguration("test-pdp", "config-1",
                    new CombiningAlgorithm(PRIORITY_DENY, ABSTAIN, PROPAGATE),
                    List.of(DUPLICATE_NAME_POLICY, DUPLICATE_NAME_POLICY),
                    new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
            val context = compilationContext();

            assertThatThrownBy(() -> PdpCompiler.compilePDPConfiguration(config, context))
                    .isInstanceOf(SaplCompilerException.class).hasMessageContaining("duplicate");
        }

        @Test
        @DisplayName("FIRST algorithm at PDP level throws SaplCompilerException")
        void whenFirstAlgorithmThenThrowsSaplCompilerException() {
            val config  = new PDPConfiguration("test-pdp", "config-1",
                    new CombiningAlgorithm(FIRST, ABSTAIN, PROPAGATE), List.of(VALID_POLICY),
                    new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
            val context = compilationContext();

            assertThatThrownBy(() -> PdpCompiler.compilePDPConfiguration(config, context))
                    .isInstanceOf(SaplCompilerException.class).hasMessageContaining("FIRST");
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
        void whenNoPoliciesThenReturnsDefaultDecision(DefaultDecision defaultDecision, Decision expected) {
            val algorithm = new CombiningAlgorithm(VotingMode.PRIORITY_DENY, defaultDecision, ErrorHandling.PROPAGATE);
            val config    = new PDPConfiguration("test-pdp", "config-1", algorithm, List.of(),
                    new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));

            val compiledVoter = PdpCompiler.compilePDPConfiguration(config, compilationContext());

            assertThat(compiledVoter.voter()).satisfies(voter -> {
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
        void whenPriorityAlgorithmWithConflictThenPriorityWins(CombiningAlgorithm algorithm, Decision expected) {
            val config = new PDPConfiguration("test-pdp", "config-1", algorithm, List.of(POLICY_A, POLICY_B),
                    new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));

            val compiledVoter = PdpCompiler.compilePDPConfiguration(config, compilationContext());
            val vote          = evaluate(compiledVoter, DEFAULT_SUBSCRIPTION);

            assertThat(vote.authorizationDecision().decision()).isEqualTo(expected);
        }

        @Test
        @DisplayName("unique algorithm with single policy returns that policy's decision")
        void whenUniqueWithSinglePolicyThenReturnsPolicyDecision() {
            val config = new PDPConfiguration("test-pdp", "config-1",
                    new CombiningAlgorithm(UNIQUE, ABSTAIN, PROPAGATE), List.of(VALID_POLICY),
                    new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));

            val compiledVoter = PdpCompiler.compilePDPConfiguration(config, compilationContext());
            val vote          = evaluate(compiledVoter, DEFAULT_SUBSCRIPTION);

            assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("unique algorithm with multiple applicable policies returns INDETERMINATE")
        void whenUniqueWithMultipleApplicableThenReturnsIndeterminate() {
            val config = new PDPConfiguration("test-pdp", "config-1",
                    new CombiningAlgorithm(UNIQUE, ABSTAIN, PROPAGATE), List.of(POLICY_A, POLICY_B),
                    new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));

            val compiledVoter = PdpCompiler.compilePDPConfiguration(config, compilationContext());
            val vote          = evaluate(compiledVoter, DEFAULT_SUBSCRIPTION);

            assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }
    }

    @Nested
    @DisplayName("PDP voter metadata outcome")
    class PdpVoterMetadataOutcomeTests {

        private static final String POLICY_SUSPEND = """
                policy "policy-s"
                suspend
                """;

        static Stream<Arguments> pdpOutcomeCases() {
            return Stream.of(arguments(ABSTAIN, List.of(POLICY_A, POLICY_B), Outcome.PERMIT_OR_DENY),
                    arguments(ABSTAIN, List.of(POLICY_A, POLICY_SUSPEND), Outcome.PERMIT_OR_SUSPEND),
                    arguments(DefaultDecision.SUSPEND, List.of(POLICY_B), Outcome.DENY_OR_SUSPEND),
                    arguments(ABSTAIN, List.<String>of(), Outcome.PERMIT_OR_DENY_OR_SUSPEND));
        }

        @ParameterizedTest(name = "default {0} with documents {1} yields {2}")
        @MethodSource("pdpOutcomeCases")
        void whenCompilingPdpThenMetadataOutcomeIsUnionOfDocumentOutcomesAndDefault(DefaultDecision defaultDecision,
                List<String> documents, Outcome expected) {
            val config = new PDPConfiguration("test-pdp", "config-1",
                    new CombiningAlgorithm(PRIORITY_DENY, defaultDecision, PROPAGATE), documents,
                    new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));

            val compiledVoter = PdpCompiler.compilePDPConfiguration(config, compilationContext());

            assertThat(compiledVoter.metadata().outcome()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("policy set would-have-been outcome on target errors")
    class PolicySetWouldHaveBeenOutcomeTests {

        private static final String ERRORING_DENY_SUSPEND_SET = """
                set "erroring-set"
                priority deny or abstain
                for subject.missing.field
                policy "set-deny" deny
                policy "set-suspend" suspend
                """;

        private static final String ERRORING_PERMIT_SUSPEND_SET = """
                set "erroring-set"
                priority permit or abstain
                for subject.missing.field
                policy "set-permit" permit
                policy "set-suspend" suspend
                """;

        private static final AuthorizationSubscription TEXT_SUBJECT_SUBSCRIPTION = AuthorizationSubscription
                .of(Value.of("alice"), Value.NULL, Value.NULL, Value.NULL);

        @Test
        @DisplayName("erroring deny/suspend set blocks concrete PERMIT under priority suspend")
        void whenSetWithDenyAndSuspendPoliciesErrorsUnderPrioritySuspendThenIndeterminate() {
            val config = new PDPConfiguration("test-pdp", "config-1",
                    new CombiningAlgorithm(PRIORITY_SUSPEND, ABSTAIN, PROPAGATE),
                    List.of(ERRORING_DENY_SUSPEND_SET, VALID_POLICY),
                    new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));

            val compiledVoter = PdpCompiler.compilePDPConfiguration(config, compilationContext());
            val vote          = evaluate(compiledVoter, TEXT_SUBJECT_SUBSCRIPTION);

            assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }

        @Test
        @DisplayName("erroring permit/suspend set does not block concrete PERMIT under priority deny")
        void whenSetWithPermitAndSuspendPoliciesErrorsUnderPriorityDenyThenConcretePermitWins() {
            val config = new PDPConfiguration("test-pdp", "config-1",
                    new CombiningAlgorithm(PRIORITY_DENY, ABSTAIN, PROPAGATE),
                    List.of(ERRORING_PERMIT_SUSPEND_SET, VALID_POLICY),
                    new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));

            val compiledVoter = PdpCompiler.compilePDPConfiguration(config, compilationContext());
            val vote          = evaluate(compiledVoter, TEXT_SUBJECT_SUBSCRIPTION);

            assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
        }
    }

}
