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
package io.sapl.compiler.combining;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.model.Coverage.PolicySetCoverage;
import io.sapl.compiler.model.Coverage.TargetHit;
import io.sapl.compiler.pdp.StreamVoter;
import io.sapl.compiler.pdp.Vote;
import lombok.val;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.sapl.util.SaplTesting.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.time.Duration;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Tests for FirstApplicableCompiler covering short-circuit optimization,
 * pure evaluation, and streaming evaluation paths.
 */
@DisplayName("FirstApplicableCompiler")
class FirstVoteCompilerTests {

    static final Duration TIMEOUT = Duration.ofSeconds(5);

    // Target hit expectations - we only check type and value, not location
    static final TargetHit BLANK        = Coverage.BLANK_TARGET_HIT;
    static final TargetHit TARGET_TRUE  = new Coverage.TargetResult(Value.TRUE, null);
    static final TargetHit TARGET_FALSE = new Coverage.TargetResult(Value.FALSE, null);
    static final TargetHit TARGET_ERROR = new Coverage.TargetResult(Value.error(""), null);

    void assertVoteHasAllTheseContributing(Vote vote, List<String> expectedNames) {
        val actual = vote.contributingVotes().stream().map(d -> d.voter().name()).toList();
        assertThat(actual).containsExactlyInAnyOrder(expectedNames.toArray(new String[0]));
    }

    void assertTargetHitMatches(TargetHit actual, TargetHit expected) {
        assertThat(actual.getClass()).isEqualTo(expected.getClass());
        if (actual instanceof Coverage.TargetResult actualResult
                && expected instanceof Coverage.TargetResult expectedResult) {
            if (expectedResult.match() instanceof ErrorValue) {
                assertThat(actualResult.match()).isInstanceOf(ErrorValue.class);
            } else {
                assertThat(actualResult.match()).isEqualTo(expectedResult.match());
            }
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("empty policy set throws IllegalArgumentException with syntax errors")
        void emptyPolicySetThrows() {
            assertThatThrownBy(() -> compilePolicySet("""
                    set "empty"
                    first or abstain errors propagate
                    """)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Syntax errors");
        }

        @Test
        @DisplayName("single policy permit")
        void whenSinglePolicyPermitsThenReturnsPermit() {
            val compiled = compilePolicySet("""
                    set "single"
                    first or abstain errors propagate

                    policy "only-one"
                    permit
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySet(compiled, ctx);

            assertThat(result).satisfies(r -> {
                assertThat(r.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
                assertVoteHasAllTheseContributing(r, List.of("only-one"));
            });
        }

        @Test
        @DisplayName("single policy NOT_APPLICABLE")
        void whenSinglePolicyNotApplicableThenReturnsNotApplicable() {
            val compiled = compilePolicySet("""
                    set "single"
                    first or abstain errors propagate

                    policy "never-matches"
                    permit
                      false;
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySet(compiled, ctx);

            assertThat(result).satisfies(r -> {
                assertThat(r.authorizationDecision().decision()).isEqualTo(Decision.NOT_APPLICABLE);
                assertVoteHasAllTheseContributing(r, List.of("never-matches"));
            });
        }
    }

    @Nested
    @DisplayName("Pure evaluation")
    class PureEvaluation {

        record PureTestCase(
                String description,
                String policySet,
                String subscription,
                Decision expectedDecision,
                TargetHit expectedTargetHit,
                List<String> contributingPolicies) {

            @Override
            public @NonNull String toString() {
                return description;
            }
        }

        static Stream<PureTestCase> pureTestCases() {
            return Stream.of(
                    // No policy set target (BLANK_TARGET_HIT)

                    new PureTestCase("short-circuit: first policy permits", """
                            set "guild-access"
                            first or abstain errors propagate

                            policy "always-permit"
                            permit

                            policy "fallback-deny"
                            deny
                            """, """
                            {
                                "subject": "alice",
                                "action": "read",
                                "resource": "data"
                            }
                            """, Decision.PERMIT, BLANK, List.of("always-permit")),

                    new PureTestCase("short-circuit: first policy body NOT_APPLICABLE, continues to next", """
                            set "test"
                            first or abstain errors propagate

                            policy "body-not-applicable"
                            permit
                              false;

                            policy "fallback-deny"
                            deny
                            """, """
                            {
                                "subject": "alice",
                                "action": "read",
                                "resource": "data"
                            }
                            """, Decision.DENY, BLANK, List.of("body-not-applicable", "fallback-deny")),

                    new PureTestCase("first policy target false, second applies", """
                            set "guild-access"
                            first or abstain errors propagate

                            policy "never-matches"
                            permit
                              false;

                            policy "always-deny"
                            deny
                            """, """
                            {
                                "subject": "alice",
                                "action": "read",
                                "resource": "data"
                            }
                            """, Decision.DENY, BLANK, List.of("never-matches", "always-deny")),

                    new PureTestCase("runtime target: first policy matches", """
                            set "watch-duties"
                            first or abstain errors propagate

                            policy "captain-only"
                            permit
                              subject == "Vimes";

                            policy "default-deny"
                            deny
                            """, """
                            {
                                "subject": "Vimes",
                                "action": "patrol",
                                "resource": "city"
                            }
                            """, Decision.PERMIT, BLANK, List.of("captain-only")),

                    new PureTestCase("runtime target: second policy matches", """
                            set "watch-duties"
                            first or abstain errors propagate

                            policy "captain-only"
                            permit
                              subject == "Vimes";

                            policy "sergeant-fallback"
                            permit
                              subject == "Colon";

                            policy "default-deny"
                            deny
                            """, """
                            {
                                "subject": "Colon",
                                "action": "patrol",
                                "resource": "city"
                            }
                            """, Decision.PERMIT, BLANK, List.of("captain-only", "sergeant-fallback")),

                    new PureTestCase("runtime target: no policy matches, falls through to default", """
                            set "watch-duties"
                            first or abstain errors propagate

                            policy "captain-only"
                            permit
                              subject == "Vimes";

                            policy "sergeant-fallback"
                            permit
                              subject == "Colon";

                            policy "default-deny"
                            deny
                            """, """
                            {
                                "subject": "Nobby",
                                "action": "patrol",
                                "resource": "city"
                            }
                            """, Decision.DENY, BLANK, List.of("captain-only", "sergeant-fallback", "default-deny")),

                    new PureTestCase("body condition matches", """
                            set "library-access"
                            first or abstain errors propagate

                            policy "wizards-reading"
                            permit
                              subject == "Rincewind";
                              action == "read";

                            policy "default-deny"
                            deny
                            """, """
                            {
                                "subject": "Rincewind",
                                "action": "read",
                                "resource": "book"
                            }
                            """, Decision.PERMIT, BLANK, List.of("wizards-reading")),

                    new PureTestCase("target matches but body fails, continues to next policy", """
                            set "library-access"
                            first or abstain errors propagate

                            policy "wizards-reading"
                            permit
                              subject == "Rincewind";
                              action == "read";

                            policy "default-deny"
                            deny
                            """, """
                            {
                                "subject": "Rincewind",
                                "action": "run",
                                "resource": "away"
                            }
                            """, Decision.DENY, BLANK, List.of("wizards-reading", "default-deny")),

                    // Policy set variable tests

                    new PureTestCase("set variable: constant folds to short-circuit", """
                            set "constant-var"
                            first or abstain errors propagate

                            var allowed = true;

                            policy "check-allowed"
                            permit
                              allowed;

                            policy "fallback"
                            deny
                            """, """
                            {
                                "subject": "alice",
                                "action": "read",
                                "resource": "data"
                            }
                            """, Decision.PERMIT, BLANK, List.of("check-allowed")),

                    new PureTestCase("set variable: subscription-dependent used in target", """
                            set "employee-access"
                            first or abstain errors propagate

                            var isManager = subject.role == "manager";

                            policy "managers-permit"
                            permit
                              isManager;

                            policy "fallback"
                            deny
                            """, """
                            {
                                "subject": { "name": "alice", "role": "manager" },
                                "action": "read",
                                "resource": "data"
                            }
                            """, Decision.PERMIT, BLANK, List.of("managers-permit")),

                    new PureTestCase("set variable: subscription-dependent, condition false", """
                            set "employee-access"
                            first or abstain errors propagate

                            var isManager = subject.role == "manager";

                            policy "managers-permit"
                            permit
                              isManager;

                            policy "fallback"
                            deny
                            """, """
                            {
                                "subject": { "name": "bob", "role": "employee" },
                                "action": "read",
                                "resource": "data"
                            }
                            """, Decision.DENY, BLANK, List.of("managers-permit", "fallback")),

                    new PureTestCase("set variable: multiple vars used across policies", """
                            set "multi-var"
                            first or abstain errors propagate

                            var dept = subject.department;
                            var isAdmin = subject.role == "admin";

                            policy "admin-access"
                            permit
                              isAdmin;

                            policy "same-department"
                            permit
                              dept == resource.department;

                            policy "fallback"
                            deny
                            """, """
                            {
                                "subject": { "department": "engineering", "role": "user" },
                                "action": "read",
                                "resource": { "department": "engineering" }
                            }
                            """, Decision.PERMIT, BLANK, List.of("admin-access", "same-department")),

                    new PureTestCase("set variable: used in policy body condition", """
                            set "body-var"
                            first or abstain errors propagate

                            var requiredLevel = 5;

                            policy "level-check"
                            permit
                              subject.level >= requiredLevel;

                            policy "fallback"
                            deny
                            """, """
                            {
                                "subject": { "level": 7 },
                                "action": "read",
                                "resource": "data"
                            }
                            """, Decision.PERMIT, BLANK, List.of("level-check")),

                    new PureTestCase("set variable: body condition fails, falls through", """
                            set "body-var"
                            first or abstain errors propagate

                            var requiredLevel = 5;

                            policy "level-check"
                            permit
                              subject.level >= requiredLevel;

                            policy "fallback"
                            deny
                            """, """
                            {
                                "subject": { "level": 3 },
                                "action": "read",
                                "resource": "data"
                            }
                            """, Decision.DENY, BLANK, List.of("level-check", "fallback")),

                    // Error and edge case tests

                    new PureTestCase("all policies NOT_APPLICABLE returns NOT_APPLICABLE", """
                            set "no-match"
                            first or abstain errors propagate

                            policy "never-matches-1"
                            permit
                              subject == "nobody";

                            policy "never-matches-2"
                            permit
                              subject == "ghost";
                            """, """
                            {
                                "subject": "alice",
                                "action": "read",
                                "resource": "data"
                            }
                            """, Decision.NOT_APPLICABLE, BLANK, List.of("never-matches-1", "never-matches-2")),

                    new PureTestCase("errors in policy body propagates as INDETERMINATE", """
                            set "errors-target"
                            first or abstain errors propagate

                            policy "errors-policy"
                            permit
                              subject.missing.deeply.nested;

                            policy "fallback"
                            deny
                            """, """
                            {
                                "subject": "simple-string",
                                "action": "read",
                                "resource": "data"
                            }
                            """, Decision.INDETERMINATE, BLANK, List.of("errors-policy")),

                    new PureTestCase(
                            "short-circuit loop completion: all policies have target=true but body=NOT_APPLICABLE", """
                                    set "all-not-applicable-folded"
                                    first or abstain errors propagate

                                    policy "first"
                                    permit
                                      false;

                                    policy "second"
                                    permit
                                      false;
                                    """, """
                                    {
                                        "subject": "alice",
                                        "action": "read",
                                        "resource": "data"
                                    }
                                    """, Decision.NOT_APPLICABLE, BLANK, List.of("first", "second")),

                    // Policy set target tests (TargetResult)

                    new PureTestCase("set target: static true, policies evaluated", """
                            set "static-target-true"
                            first or abstain errors propagate
                            for true

                            policy "inner"
                            permit
                            """, """
                            {
                                "subject": "alice",
                                "action": "read",
                                "resource": "data"
                            }
                            """, Decision.PERMIT, TARGET_TRUE, List.of("inner")),

                    new PureTestCase("set target: static false, NOT_APPLICABLE without evaluating policies", """
                            set "static-target-false"
                            first or abstain errors propagate
                            for false

                            policy "inner"
                            permit
                            """, """
                            {
                                "subject": "alice",
                                "action": "read",
                                "resource": "data"
                            }
                            """, Decision.NOT_APPLICABLE, TARGET_FALSE, List.of()),

                    new PureTestCase("set target: runtime true, policies evaluated", """
                            set "runtime-target-true"
                            first or abstain errors propagate
                            for subject == "alice"

                            policy "inner"
                            permit
                            """, """
                            {
                                "subject": "alice",
                                "action": "read",
                                "resource": "data"
                            }
                            """, Decision.PERMIT, TARGET_TRUE, List.of("inner")),

                    new PureTestCase("set target: runtime false, NOT_APPLICABLE", """
                            set "runtime-target-false"
                            first or abstain errors propagate
                            for subject == "bob"

                            policy "inner"
                            permit
                            """, """
                            {
                                "subject": "alice",
                                "action": "read",
                                "resource": "data"
                            }
                            """, Decision.NOT_APPLICABLE, TARGET_FALSE, List.of()),

                    new PureTestCase("set target: errors in target, INDETERMINATE", """
                            set "errors-in-set-target"
                            first or abstain errors propagate
                            for subject.missing.field

                            policy "inner"
                            permit
                            """, """
                            {
                                "subject": "simple-string",
                                "action": "read",
                                "resource": "data"
                            }
                            """, Decision.INDETERMINATE, TARGET_ERROR, List.of()),

                    new PureTestCase("set target: non-boolean result, INDETERMINATE", """
                            set "non-boolean-target"
                            first or abstain errors propagate
                            for subject

                            policy "inner"
                            permit
                            """, """
                            {
                                "subject": 42,
                                "action": "read",
                                "resource": "data"
                            }
                            """, Decision.INDETERMINATE, TARGET_ERROR, List.of()));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource
        void pureTestCases(PureTestCase testCase) {
            val compiled           = compilePolicySet(testCase.policySet());
            val ctx                = subscriptionContext(testCase.subscription());
            val result             = evaluatePolicySet(compiled, ctx);
            val resultWithCoverage = evaluatePolicySetWithCoverage(compiled, ctx);

            assertThat(result.authorizationDecision().decision()).isEqualTo(testCase.expectedDecision());
            assertVoteHasAllTheseContributing(result, testCase.contributingPolicies());

            assertThat(resultWithCoverage.vote().authorizationDecision().decision())
                    .isEqualTo(testCase.expectedDecision());
            val psCoverage = (PolicySetCoverage) resultWithCoverage.coverage();
            assertThat(psCoverage.policyCoverages()).hasSize(testCase.contributingPolicies().size());
            assertTargetHitMatches(psCoverage.targetHit(), testCase.expectedTargetHit());
        }
    }

    @Nested
    @DisplayName("Stream evaluation")
    class StreamEvaluation {

        record StreamTestCase(
                String description,
                String policySet,
                String subscription,
                Map<String, Value[]> attributes,
                Decision expectedDecision,
                TargetHit expectedTargetHit,
                List<String> contributingPolicies) {

            @Override
            public @NonNull String toString() {
                return description;
            }
        }

        static Stream<StreamTestCase> streamTestCases() {
            return Stream.of(
                    // No policy set target (BLANK_TARGET_HIT)

                    new StreamTestCase("attribute in body permits", """
                            set "time-based-access"
                            first or abstain errors propagate

                            policy "time-check"
                            permit
                              <test.attr>;

                            policy "fallback"
                            deny
                            """, """
                            {
                                "subject": "alice",
                                "action": "read",
                                "resource": "doc"
                            }
                            """, Map.of("test.attr", new Value[] { Value.TRUE }), Decision.PERMIT, BLANK,
                            List.of("time-check")),

                    new StreamTestCase("target matches but body fails, continues to next policy", """
                            set "library-access"
                            first or abstain errors propagate

                            policy "wizards-reading"
                            permit
                              subject == "Rincewind";
                              <test.action> == "read";

                            policy "default-deny"
                            deny
                            """, """
                            {
                                "subject": "Rincewind",
                                "action": "run",
                                "resource": "away"
                            }
                            """, Map.of("test.action", new Value[] { Value.of("run") }), Decision.DENY, BLANK,
                            List.of("wizards-reading", "default-deny")),

                    // Policy set variable with attribute (streaming)

                    new StreamTestCase("set variable: attribute in body makes set streaming", """
                            set "streaming-var"
                            first or abstain errors propagate

                            var currentTime = <test.time>;

                            policy "time-check"
                            permit
                              currentTime == "day";

                            policy "fallback"
                            deny
                            """, """
                            {
                                "subject": "alice",
                                "action": "read",
                                "resource": "data"
                            }
                            """, Map.of("test.time", new Value[] { Value.of("day") }), Decision.PERMIT, BLANK,
                            List.of("time-check")),

                    new StreamTestCase("set variable: streaming var body condition false", """
                            set "streaming-var"
                            first or abstain errors propagate

                            var currentTime = <test.time>;

                            policy "time-check"
                            permit
                              currentTime == "day";

                            policy "fallback"
                            deny
                            """, """
                            {
                                "subject": "alice",
                                "action": "read",
                                "resource": "data"
                            }
                            """, Map.of("test.time", new Value[] { Value.of("night") }), Decision.DENY, BLANK,
                            List.of("time-check", "fallback")),

                    new StreamTestCase("stream path: first policy target false, falls through to second", """
                            set "stream-target-false"
                            first or abstain errors propagate

                            policy "never-matches"
                            permit
                              false;

                            policy "fallback"
                            deny
                              <test.attr>;
                            """, """
                            {
                                "subject": "alice",
                                "action": "read",
                                "resource": "data"
                            }
                            """, Map.of("test.attr", new Value[] { Value.TRUE }), Decision.DENY, BLANK,
                            List.of("never-matches", "fallback")),

                    new StreamTestCase("stream path: errors in body propagates as INDETERMINATE", """
                            set "errors-target-stream"
                            first or abstain errors propagate

                            policy "errors-policy"
                            permit
                              subject.missing.deeply.nested;
                              <test.attr>;

                            policy "fallback"
                            deny
                            """, """
                            {
                                "subject": "simple-string",
                                "action": "read",
                                "resource": "data"
                            }
                            """, Map.of("test.attr", new Value[] { Value.TRUE }), Decision.INDETERMINATE, BLANK,
                            List.of("errors-policy")),

                    new StreamTestCase("stream path: non-boolean in body propagates as INDETERMINATE", """
                            set "non-boolean-target-stream"
                            first or abstain errors propagate

                            policy "number-target"
                            permit
                              subject;
                              <test.attr>;

                            policy "fallback"
                            deny
                            """, """
                            {
                                "subject": 42,
                                "action": "read",
                                "resource": "data"
                            }
                            """, Map.of("test.attr", new Value[] { Value.TRUE }), Decision.INDETERMINATE, BLANK,
                            List.of("number-target")),

                    // Policy set target with streaming policies (TargetResult)

                    new StreamTestCase("set target: runtime true with streaming policy", """
                            set "target-with-stream"
                            first or abstain errors propagate
                            for subject == "alice"

                            policy "stream-policy"
                            permit
                              <test.attr>;
                            """, """
                            {
                                "subject": "alice",
                                "action": "read",
                                "resource": "data"
                            }
                            """, Map.of("test.attr", new Value[] { Value.TRUE }), Decision.PERMIT, TARGET_TRUE,
                            List.of("stream-policy")),

                    new StreamTestCase("set target: runtime false with streaming policy, NOT_APPLICABLE", """
                            set "target-with-stream"
                            first or abstain errors propagate
                            for subject == "bob"

                            policy "stream-policy"
                            permit
                              <test.attr>;
                            """, """
                            {
                                "subject": "alice",
                                "action": "read",
                                "resource": "data"
                            }
                            """, Map.of("test.attr", new Value[] { Value.TRUE }), Decision.NOT_APPLICABLE, TARGET_FALSE,
                            List.of()),

                    new StreamTestCase("set target: runtime errors with streaming policy, INDETERMINATE", """
                            set "target-errors-stream"
                            first or abstain errors propagate
                            for subject.missing.field

                            policy "stream-policy"
                            permit
                              <test.attr>;
                            """, """
                            {
                                "subject": "simple-string",
                                "action": "read",
                                "resource": "data"
                            }
                            """, Map.of("test.attr", new Value[] { Value.TRUE }), Decision.INDETERMINATE, TARGET_ERROR,
                            List.of()));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource
        void streamTestCases(StreamTestCase testCase) {
            val attrBroker   = attributeBroker(testCase.attributes());
            val compiled     = compilePolicySet(testCase.policySet(), attrBroker);
            val streamVoter  = (StreamVoter) compiled.applicabilityAndVote();
            val subscription = parseSubscription(testCase.subscription());
            val ctx          = evaluationContext(subscription, attrBroker);

            assertThat(compiled.applicabilityAndVote()).as("Expected stream stratum").isInstanceOf(StreamVoter.class);

            StepVerifier.create(streamVoter.vote().contextWrite(c -> c.put(EvaluationContext.class, ctx)))
                    .assertNext(pdpVote -> {
                        assertThat(pdpVote.authorizationDecision().decision()).isEqualTo(testCase.expectedDecision());
                        assertVoteHasAllTheseContributing(pdpVote, testCase.contributingPolicies());
                    }).expectComplete().verify(TIMEOUT);

            StepVerifier.create(compiled.coverage().contextWrite(c -> c.put(EvaluationContext.class, ctx)))
                    .assertNext(resultWithCoverage -> {
                        assertThat(resultWithCoverage.vote().authorizationDecision().decision())
                                .isEqualTo(testCase.expectedDecision());
                        val psCoverage = (PolicySetCoverage) resultWithCoverage.coverage();
                        assertThat(psCoverage.policyCoverages()).hasSize(testCase.contributingPolicies().size());
                        assertTargetHitMatches(psCoverage.targetHit(), testCase.expectedTargetHit());
                    }).expectComplete().verify(TIMEOUT);
        }
    }

    @Nested
    @DisplayName("Default vote permutations")
    class DefaultDecisionPermutations {

        static Stream<Arguments> defaultDecisionCases() {
            return Stream.of(arguments("first or deny", "permit", Decision.DENY),
                    arguments("first or permit", "deny", Decision.PERMIT),
                    arguments("first or abstain", "permit", Decision.NOT_APPLICABLE),
                    arguments("first or deny errors propagate", "permit", Decision.DENY),
                    arguments("first or permit errors propagate", "deny", Decision.PERMIT));
        }

        @ParameterizedTest(name = "{0}: all NOT_APPLICABLE returns {2}")
        @MethodSource("defaultDecisionCases")
        @DisplayName("default decision when all NOT_APPLICABLE")
        void whenAllNotApplicableThenReturnsDefaultDecision(String algorithm, String entitlement,
                Decision expectedDecision) {
            val compiled = compilePolicySet("""
                    set "test"
                    %s

                    policy "never-matches"
                    %s
                      false;
                    """.formatted(algorithm, entitlement));
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySet(compiled, ctx);

            assertThat(result.authorizationDecision().decision()).isEqualTo(expectedDecision);
        }
    }

    @Nested
    @DisplayName("Attribute aggregation")
    class AttributeAggregation {

        @Test
        @DisplayName("aggregates attributes from chain of NOT_APPLICABLE policies with final PERMIT")
        void aggregatesAttributesFromNotApplicableChainWithFinalPermit() {
            // Policy A: test.attrA returns false -> NOT_APPLICABLE
            // Policy B: test.attrB returns false -> NOT_APPLICABLE
            // Policy C: test.attrC returns true -> PERMIT
            // All attributes should be aggregated in the final vote
            val attrBroker = attributeBroker(Map.of("test.attrA", new Value[] { Value.FALSE }, "test.attrB",
                    new Value[] { Value.FALSE }, "test.attrC", new Value[] { Value.TRUE }));
            val compiled   = compilePolicySet("""
                    set "attribute-chain"
                    first or abstain errors propagate

                    policy "policy-a"
                    permit
                      <test.attrA>;

                    policy "policy-b"
                    permit
                      <test.attrB>;

                    policy "policy-c"
                    permit
                      <test.attrC>;
                    """, attrBroker);

            val streamVoter  = (StreamVoter) compiled.applicabilityAndVote();
            val subscription = parseSubscription("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);

            StepVerifier.create(streamVoter.vote().contextWrite(c -> c.put(EvaluationContext.class, ctx)))
                    .assertNext(vote -> {
                        assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
                        assertVoteHasAllTheseContributing(vote, List.of("policy-a", "policy-b", "policy-c"));

                        // Verify all three attributes are aggregated from the tree
                        val aggregatedAttrs = vote.aggregatedContributingAttributes();
                        val attrNames = aggregatedAttrs.stream().map(attr -> attr.invocation().attributeName()).toList();
                        assertThat(attrNames).containsExactlyInAnyOrder("test.attrA", "test.attrB", "test.attrC");
                    }).expectComplete().verify(TIMEOUT);
        }

        @Test
        @DisplayName("aggregates attributes when first policy permits (short-circuit with attributes)")
        void aggregatesAttributesOnShortCircuit() {
            val attrBroker = attributeBroker(Map.of("test.attr1", new Value[] { Value.TRUE }));
            val compiled   = compilePolicySet("""
                    set "short-circuit"
                    first or abstain errors propagate

                    policy "permits-immediately"
                    permit
                      <test.attr1>;

                    policy "never-evaluated"
                    deny
                      <test.never>;
                    """, attrBroker);

            val streamVoter  = (StreamVoter) compiled.applicabilityAndVote();
            val subscription = parseSubscription("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);

            StepVerifier.create(streamVoter.vote().contextWrite(c -> c.put(EvaluationContext.class, ctx)))
                    .assertNext(vote -> {
                        assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
                        assertVoteHasAllTheseContributing(vote, List.of("permits-immediately"));

                        // Only the first policy's attribute should be aggregated (short-circuit)
                        val aggregatedAttrs = vote.aggregatedContributingAttributes();
                        val attrNames = aggregatedAttrs.stream().map(attr -> attr.invocation().attributeName()).toList();
                        assertThat(attrNames).containsExactly("test.attr1");
                    }).expectComplete().verify(TIMEOUT);
        }

        @Test
        @DisplayName("aggregates attributes from all NOT_APPLICABLE policies when none match")
        void aggregatesAttributesWhenAllNotApplicable() {
            // Both attributes return false -> all policies NOT_APPLICABLE
            val attrBroker = attributeBroker(
                    Map.of("test.attrX", new Value[] { Value.FALSE }, "test.attrY", new Value[] { Value.FALSE }));
            val compiled   = compilePolicySet("""
                    set "all-not-applicable"
                    first or abstain errors propagate

                    policy "policy-x"
                    permit
                      <test.attrX>;

                    policy "policy-y"
                    permit
                      <test.attrY>;
                    """, attrBroker);

            val streamVoter  = (StreamVoter) compiled.applicabilityAndVote();
            val subscription = parseSubscription("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val ctx          = evaluationContext(subscription, attrBroker);

            StepVerifier.create(streamVoter.vote().contextWrite(c -> c.put(EvaluationContext.class, ctx)))
                    .assertNext(vote -> {
                        assertThat(vote.authorizationDecision().decision()).isEqualTo(Decision.NOT_APPLICABLE);
                        assertVoteHasAllTheseContributing(vote, List.of("policy-x", "policy-y"));

                        // Both attributes should be aggregated even though all policies are
                        // NOT_APPLICABLE
                        val aggregatedAttrs = vote.aggregatedContributingAttributes();
                        val attrNames = aggregatedAttrs.stream().map(attr -> attr.invocation().attributeName()).toList();
                        assertThat(attrNames).containsExactlyInAnyOrder("test.attrX", "test.attrY");
                    }).expectComplete().verify(TIMEOUT);
        }
    }

    @Nested
    @DisplayName("Error handling permutations")
    class ErrorHandlingPermutations {

        @Test
        @DisplayName("errors abstain (default): set abstains on errors, returns NOT_APPLICABLE")
        void whenErrorsAbstainThenSetAbstainsOnError() {
            val compiled = compilePolicySet("""
                    set "test"
                    first or abstain

                    policy "errors-policy"
                    permit
                      subject.missing.field;

                    policy "fallback"
                    deny
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "simple-string", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySet(compiled, ctx);

            // Error makes set INDETERMINATE, errors abstain -> NOT_APPLICABLE
            // Fallback policy is NOT evaluated (errors stops first)
            assertThat(result).satisfies(r -> {
                assertThat(r.authorizationDecision().decision()).isEqualTo(Decision.NOT_APPLICABLE);
                assertVoteHasAllTheseContributing(r, List.of("errors-policy"));
            });
        }

        @Test
        @DisplayName("errors propagate: INDETERMINATE stops evaluation and returns INDETERMINATE")
        void whenErrorsPropagateThenIndeterminateStopsEvaluation() {
            val compiled = compilePolicySet("""
                    set "test"
                    first or abstain errors propagate

                    policy "errors-policy"
                    permit
                      subject.missing.field;

                    policy "fallback"
                    deny
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "simple-string", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySet(compiled, ctx);

            assertThat(result).satisfies(r -> {
                assertThat(r.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
                assertVoteHasAllTheseContributing(r, List.of("errors-policy"));
            });
        }

        static Stream<Arguments> errorsAbstainOverridesDefaultCases() {
            return Stream.of(arguments("first or deny", "permit"), arguments("first or permit", "deny"));
        }

        @ParameterizedTest(name = "{0}: errors causes abstain (NOT default)")
        @MethodSource("errorsAbstainOverridesDefaultCases")
        @DisplayName("errors abstain overrides default decision")
        void whenErrorsAbstainThenOverridesDefaultDecision(String algorithm, String entitlement) {
            val compiled = compilePolicySet("""
                    set "test"
                    %s

                    policy "errors-policy"
                    %s
                      subject.missing.field;
                    """.formatted(algorithm, entitlement));
            val ctx      = subscriptionContext("""
                    { "subject": "simple-string", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySet(compiled, ctx);

            // Error handling (abstain) takes precedence over default vote
            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.NOT_APPLICABLE);
        }

        static Stream<Arguments> errorsPropagateReturnsIndeterminateCases() {
            return Stream.of(arguments("first or deny errors propagate", "permit"),
                    arguments("first or permit errors propagate", "deny"));
        }

        @ParameterizedTest(name = "{0}: errors returns INDETERMINATE")
        @MethodSource("errorsPropagateReturnsIndeterminateCases")
        @DisplayName("errors propagate returns INDETERMINATE")
        void whenErrorsPropagateThenReturnsIndeterminate(String algorithm, String entitlement) {
            val compiled = compilePolicySet("""
                    set "test"
                    %s

                    policy "errors-policy"
                    %s
                      subject.missing.field;

                    policy "fallback"
                    %s
                    """.formatted(algorithm, entitlement, "permit".equals(entitlement) ? "deny" : "permit"));
            val ctx      = subscriptionContext("""
                    { "subject": "simple-string", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySet(compiled, ctx);

            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.INDETERMINATE);
        }
    }
}
