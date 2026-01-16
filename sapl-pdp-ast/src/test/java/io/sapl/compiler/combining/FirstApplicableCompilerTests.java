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
import io.sapl.compiler.model.Coverage.TargetHit;
import io.sapl.compiler.pdp.StreamDecisionMaker;
import io.sapl.compiler.policyset.PolicySetDecision;
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

import java.time.Duration;

/**
 * Tests for FirstApplicableCompiler covering short-circuit optimization,
 * pure evaluation, and streaming evaluation paths.
 */
@DisplayName("FirstApplicableCompiler")
class FirstApplicableCompilerTests {

    static final Duration TIMEOUT = Duration.ofSeconds(5);

    // Target hit expectations - we only check type and value, not location
    static final TargetHit BLANK        = Coverage.BLANK_TARGET_HIT;
    static final TargetHit TARGET_TRUE  = new Coverage.TargetResult(Value.TRUE, null);
    static final TargetHit TARGET_FALSE = new Coverage.TargetResult(Value.FALSE, null);
    static final TargetHit TARGET_ERROR = new Coverage.TargetResult(Value.error(""), null);

    void assertDecisionHasAllTheseContributing(PolicySetDecision decision, List<String> expectedNames) {
        val actual = decision.metadata().contributingPolicyDecisions().stream()
                .map(contribution -> contribution.metadata().source().name()).toList();
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
        @DisplayName("empty policy set throws IllegalArgumentException from parser")
        void emptyPolicySetThrows() {
            assertThatThrownBy(() -> compilePolicySet("""
                    set "empty"
                    first-applicable
                    """)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Expected a policy set document");
        }

        @Test
        @DisplayName("single policy permit")
        void singlePolicyPermit() {
            val compiled = compilePolicySet("""
                    set "single"
                    first-applicable

                    policy "only-one"
                    permit
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySet(compiled, ctx);

            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.PERMIT);
            assertDecisionHasAllTheseContributing(result, List.of("only-one"));
        }

        @Test
        @DisplayName("single policy NOT_APPLICABLE")
        void singlePolicyNotApplicable() {
            val compiled = compilePolicySet("""
                    set "single"
                    first-applicable

                    policy "never-matches"
                    permit
                    where
                      false;
                    """);
            val ctx      = subscriptionContext("""
                    { "subject": "alice", "action": "read", "resource": "data" }
                    """);
            val result   = evaluatePolicySet(compiled, ctx);

            assertThat(result.authorizationDecision().decision()).isEqualTo(Decision.NOT_APPLICABLE);
            assertDecisionHasAllTheseContributing(result, List.of("never-matches"));
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
                    // --- No policy set target (BLANK_TARGET_HIT) ---

                    new PureTestCase("short-circuit: first policy permits", """
                            set "guild-access"
                            first-applicable

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
                            first-applicable

                            policy "body-not-applicable"
                            permit
                            where
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
                            first-applicable

                            policy "never-matches"
                            permit
                            where
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
                            first-applicable

                            policy "captain-only"
                            permit
                            where
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
                            first-applicable

                            policy "captain-only"
                            permit
                            where
                              subject == "Vimes";

                            policy "sergeant-fallback"
                            permit
                            where
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
                            first-applicable

                            policy "captain-only"
                            permit
                            where
                              subject == "Vimes";

                            policy "sergeant-fallback"
                            permit
                            where
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
                            first-applicable

                            policy "wizards-reading"
                            permit
                            where
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
                            first-applicable

                            policy "wizards-reading"
                            permit
                            where
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

                    // --- Policy set variable tests ---

                    new PureTestCase("set variable: constant folds to short-circuit", """
                            set "constant-var"
                            first-applicable

                            var allowed = true;

                            policy "check-allowed"
                            permit
                            where
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
                            first-applicable

                            var isManager = subject.role == "manager";

                            policy "managers-permit"
                            permit
                            where
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
                            first-applicable

                            var isManager = subject.role == "manager";

                            policy "managers-permit"
                            permit
                            where
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
                            first-applicable

                            var dept = subject.department;
                            var isAdmin = subject.role == "admin";

                            policy "admin-access"
                            permit
                            where
                              isAdmin;

                            policy "same-department"
                            permit
                            where
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
                            first-applicable

                            var requiredLevel = 5;

                            policy "level-check"
                            permit
                            where
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
                            first-applicable

                            var requiredLevel = 5;

                            policy "level-check"
                            permit
                            where
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

                    // --- Error and edge case tests ---

                    new PureTestCase("all policies NOT_APPLICABLE returns NOT_APPLICABLE", """
                            set "no-match"
                            first-applicable

                            policy "never-matches-1"
                            permit
                            where
                              subject == "nobody";

                            policy "never-matches-2"
                            permit
                            where
                              subject == "ghost";
                            """, """
                            {
                                "subject": "alice",
                                "action": "read",
                                "resource": "data"
                            }
                            """, Decision.NOT_APPLICABLE, BLANK, List.of("never-matches-1", "never-matches-2")),

                    new PureTestCase("error in policy body propagates as INDETERMINATE", """
                            set "error-target"
                            first-applicable

                            policy "error-policy"
                            permit
                            where
                              subject.missing.deeply.nested;

                            policy "fallback"
                            deny
                            """, """
                            {
                                "subject": "simple-string",
                                "action": "read",
                                "resource": "data"
                            }
                            """, Decision.INDETERMINATE, BLANK, List.of("error-policy")),

                    new PureTestCase(
                            "short-circuit loop completion: all policies have target=true but body=NOT_APPLICABLE", """
                                    set "all-not-applicable-folded"
                                    first-applicable

                                    policy "first"
                                    permit
                                    where
                                      false;

                                    policy "second"
                                    permit
                                    where
                                      false;
                                    """, """
                                    {
                                        "subject": "alice",
                                        "action": "read",
                                        "resource": "data"
                                    }
                                    """, Decision.NOT_APPLICABLE, BLANK, List.of("first", "second")),

                    // --- Policy set target tests (TargetResult) ---

                    new PureTestCase("set target: static true, policies evaluated", """
                            set "static-target-true"
                            first-applicable
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
                            first-applicable
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
                            first-applicable
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
                            first-applicable
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

                    new PureTestCase("set target: error in target, INDETERMINATE", """
                            set "error-in-set-target"
                            first-applicable
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
                            first-applicable
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
            assertDecisionHasAllTheseContributing(result, testCase.contributingPolicies());

            assertThat(resultWithCoverage.decision().authorizationDecision().decision())
                    .isEqualTo(testCase.expectedDecision());
            assertThat(resultWithCoverage.coverage().policyCoverages()).hasSize(testCase.contributingPolicies().size());
            assertTargetHitMatches(resultWithCoverage.coverage().targetHit(), testCase.expectedTargetHit());
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
                    // --- No policy set target (BLANK_TARGET_HIT) ---

                    new StreamTestCase("attribute in body permits", """
                            set "time-based-access"
                            first-applicable

                            policy "time-check"
                            permit
                            where
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
                            first-applicable

                            policy "wizards-reading"
                            permit
                            where
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

                    // --- Policy set variable with attribute (streaming) ---

                    new StreamTestCase("set variable: attribute in body makes set streaming", """
                            set "streaming-var"
                            first-applicable

                            var currentTime = <test.time>;

                            policy "time-check"
                            permit
                            where
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
                            first-applicable

                            var currentTime = <test.time>;

                            policy "time-check"
                            permit
                            where
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
                            first-applicable

                            policy "never-matches"
                            permit
                            where
                              false;

                            policy "fallback"
                            deny
                            where
                              <test.attr>;
                            """, """
                            {
                                "subject": "alice",
                                "action": "read",
                                "resource": "data"
                            }
                            """, Map.of("test.attr", new Value[] { Value.TRUE }), Decision.DENY, BLANK,
                            List.of("never-matches", "fallback")),

                    new StreamTestCase("stream path: error in body propagates as INDETERMINATE", """
                            set "error-target-stream"
                            first-applicable

                            policy "error-policy"
                            permit
                            where
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
                            List.of("error-policy")),

                    new StreamTestCase("stream path: non-boolean in body propagates as INDETERMINATE", """
                            set "non-boolean-target-stream"
                            first-applicable

                            policy "number-target"
                            permit
                            where
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

                    // --- Policy set target with streaming policies (TargetResult) ---

                    new StreamTestCase("set target: runtime true with streaming policy", """
                            set "target-with-stream"
                            first-applicable
                            for subject == "alice"

                            policy "stream-policy"
                            permit
                            where
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
                            first-applicable
                            for subject == "bob"

                            policy "stream-policy"
                            permit
                            where
                              <test.attr>;
                            """, """
                            {
                                "subject": "alice",
                                "action": "read",
                                "resource": "data"
                            }
                            """, Map.of("test.attr", new Value[] { Value.TRUE }), Decision.NOT_APPLICABLE, TARGET_FALSE,
                            List.of()),

                    new StreamTestCase("set target: runtime error with streaming policy, INDETERMINATE", """
                            set "target-error-stream"
                            first-applicable
                            for subject.missing.field

                            policy "stream-policy"
                            permit
                            where
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
            val attrBroker          = attributeBroker(testCase.attributes());
            val compiled            = compilePolicySet(testCase.policySet(), attrBroker);
            val streamDecisionMaker = (StreamDecisionMaker) compiled.applicabilityAndDecision();
            val subscription        = parseSubscription(testCase.subscription());
            val ctx                 = evaluationContext(subscription, attrBroker);

            assertThat(compiled.applicabilityAndDecision()).as("Expected stream stratum")
                    .isInstanceOf(StreamDecisionMaker.class);

            StepVerifier.create(
                    streamDecisionMaker.decide(List.of()).contextWrite(c -> c.put(EvaluationContext.class, ctx)))
                    .assertNext(pdpDecision -> {
                        val result = (PolicySetDecision) pdpDecision;
                        assertThat(result.authorizationDecision().decision()).isEqualTo(testCase.expectedDecision());
                        assertDecisionHasAllTheseContributing(result, testCase.contributingPolicies());
                    }).expectComplete().verify(TIMEOUT);

            StepVerifier.create(compiled.coverage().contextWrite(c -> c.put(EvaluationContext.class, ctx)))
                    .assertNext(resultWithCoverage -> {
                        assertThat(resultWithCoverage.decision().authorizationDecision().decision())
                                .isEqualTo(testCase.expectedDecision());
                        assertThat(resultWithCoverage.coverage().policyCoverages())
                                .hasSize(testCase.contributingPolicies().size());
                        assertTargetHitMatches(resultWithCoverage.coverage().targetHit(), testCase.expectedTargetHit());
                    }).expectComplete().verify(TIMEOUT);
        }
    }
}
