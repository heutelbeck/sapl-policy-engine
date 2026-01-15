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

import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.compiler.pdp.PDPDecision;
import io.sapl.compiler.pdp.StreamDecisionMaker;
import io.sapl.compiler.policyset.PolicySetDecision;
import lombok.val;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.sapl.util.SaplTesting.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for FirstApplicableCompiler covering short-circuit optimization,
 * pure evaluation, and streaming evaluation paths.
 */
@DisplayName("FirstApplicableCompiler")
class FirstApplicableCompilerTests {

    record PureTestCase(
            String description,
            String policySet,
            String subscription,
            Decision expectedDecision,
            Class<? extends PolicySetDecision> expectedStratum,
            List<String> contributingPolicies) {

        @Override
        public @NonNull String toString() {
            return description;
        }
    }

    record StreamTestCase(
            String description,
            String policySet,
            String subscription,
            Map<String, Value[]> attributes,
            Decision expectedDecision,
            List<String> contributingPolicies) {

        @Override
        public @NonNull String toString() {
            return description;
        }
    }

    static Stream<PureTestCase> pureTestCases() {
        return Stream.of(new PureTestCase("short-circuit: first policy permits", """
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
                """, Decision.PERMIT, PolicySetDecision.class, List.of("always-permit")),

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
                        """, Decision.DENY, PolicySetDecision.class, List.of("body-not-applicable", "fallback-deny")),

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
                        """, Decision.DENY, PolicySetDecision.class, List.of("never-matches", "always-deny")),

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
                        """, Decision.PERMIT, PolicySetDecision.class, List.of("captain-only")),

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
                        """, Decision.PERMIT, PolicySetDecision.class, List.of("captain-only", "sergeant-fallback")),

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
                        """, Decision.DENY, PolicySetDecision.class,
                        List.of("captain-only", "sergeant-fallback", "default-deny")),

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
                        """, Decision.PERMIT, PolicySetDecision.class, List.of("wizards-reading")),

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
                        """, Decision.DENY, PolicySetDecision.class, List.of("wizards-reading", "default-deny")),

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
                        """, Decision.PERMIT, PolicySetDecision.class, List.of("check-allowed")),

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
                        """, Decision.PERMIT, PolicySetDecision.class, List.of("managers-permit")),

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
                        """, Decision.DENY, PolicySetDecision.class, List.of("managers-permit", "fallback")),

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
                        """, Decision.PERMIT, PolicySetDecision.class, List.of("admin-access", "same-department")),

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
                        """, Decision.PERMIT, PolicySetDecision.class, List.of("level-check")),

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
                        """, Decision.DENY, PolicySetDecision.class, List.of("level-check", "fallback")),

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
                        """, Decision.NOT_APPLICABLE, PolicySetDecision.class,
                        List.of("never-matches-1", "never-matches-2")),

                new PureTestCase("error in target expression propagates as INDETERMINATE", """
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
                        """, Decision.INDETERMINATE, PolicySetDecision.class, List.of("error-policy")),

                new PureTestCase("short-circuit loop completion: all policies have target=true but body=NOT_APPLICABLE",
                        """
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
                                """, Decision.NOT_APPLICABLE, PolicySetDecision.class, List.of("first", "second")));
    }

    // --- Stream evaluation test cases ---

    static Stream<StreamTestCase> streamTestCases() {
        return Stream.of(new StreamTestCase("attribute in body permits", """
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
                """, Map.of("test.attr", new Value[] { Value.TRUE }), Decision.PERMIT, List.of("time-check")),

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
                        """, Map.of("test.action", new Value[] { Value.of("run") }), Decision.DENY,
                        List.of("default-deny")),

                // --- Policy set variable with attribute (streaming) ---
                // Note: streaming variables can only be used in policy body (where), not in
                // target

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
                        """, Map.of("test.time", new Value[] { Value.of("day") }), Decision.PERMIT,
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
                        """, Map.of("test.time", new Value[] { Value.of("night") }), Decision.DENY,
                        List.of("fallback")),

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
                        """, Map.of("test.attr", new Value[] { Value.TRUE }), Decision.DENY,
                        List.of("never-matches", "fallback")),

                new StreamTestCase("stream path: error in target expression propagates as INDETERMINATE", """
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
                        """, Map.of("test.attr", new Value[] { Value.TRUE }), Decision.INDETERMINATE,
                        List.of("error-policy")),

                new StreamTestCase("stream path: non-boolean target (via boolean guard) propagates as INDETERMINATE",
                        """
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
                                """, Map.of("test.attr", new Value[] { Value.TRUE }), Decision.INDETERMINATE,
                        List.of("number-target")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pureTestCases")
    @DisplayName("Pure evaluation")
    void pureEvaluation(PureTestCase testCase) {
        val compiled = compilePolicySet(testCase.policySet());

        val ctx    = subscriptionContext(testCase.subscription());
        val result = evaluatePolicySet(compiled, ctx);
        assertDecisionHasAllTheseContributing(result, testCase.contributingPolicies());
        assertThat(result.authorizationDecision().decision()).isEqualTo(testCase.expectedDecision());

        // TODO: Re-enable coverage check once coverage stream is implemented
        // val resultWithCoverage = evaluatePolicySetWithCoverage(compiled, ctx);
        // assertThat(resultWithCoverage.decision()).isEqualTo(result);
    }

    void assertDecisionHasAllTheseContributing(PolicySetDecision decision, List<String> expectedNames) {
        val actual = decision.metadata().contributingPolicyDecisions().stream()
                .map(contribution -> contribution.metadata().source().name()).toList();
        assertThat(actual).containsExactlyInAnyOrder(expectedNames.toArray(new String[0]));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("streamTestCases")
    @DisplayName("Stream evaluation")
    void streamEvaluation(StreamTestCase testCase) {
        val attrBroker = attributeBroker(testCase.attributes());
        val compiled   = compilePolicySet(testCase.policySet(), attrBroker);

        assertThat(compiled.applicabilityAndDecision()).as("Expected stream stratum")
                .isInstanceOf(StreamDecisionMaker.class);

        val streamDecisionMaker = (StreamDecisionMaker) compiled.applicabilityAndDecision();
        val subscription        = parseSubscription(testCase.subscription());
        val ctx                 = evaluationContext(subscription, attrBroker);

        StepVerifier
                .create(streamDecisionMaker.decide(List.of()).contextWrite(c -> c.put(EvaluationContext.class, ctx)))
                .assertNext(pdpDecision -> {
                    val result = (PolicySetDecision) pdpDecision;
                    assertDecisionHasAllTheseContributing(result, testCase.contributingPolicies());
                    assertThat(result.authorizationDecision().decision()).isEqualTo(testCase.expectedDecision());
                }).verifyComplete();

        // TODO: Re-enable coverage check once coverage stream is implemented
        // StepVerifier.create(compiled.coverage().contextWrite(c ->
        // c.put(EvaluationContext.class, ctx)))
        // .assertNext(resultWithCoverage ->
        // assertThat(resultWithCoverage.decision().authorizationDecision().decision())
        // .isEqualTo(testCase.expectedDecision()))
        // .verifyComplete();
    }

}
