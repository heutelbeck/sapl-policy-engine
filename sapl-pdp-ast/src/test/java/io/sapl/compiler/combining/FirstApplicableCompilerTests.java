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

// TODO: Re-enable after PolicySet refactoring is complete
// import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.compiler.policyset.PolicySetBody;
import io.sapl.compiler.policyset.PolicySetDecision;
import io.sapl.compiler.policyset.PurePolicySetBody;
// TODO: Re-enable after PolicySet refactoring is complete
// import io.sapl.compiler.policyset.StreamPolicySetBody;
// import lombok.val;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
// TODO: Re-enable after PolicySet refactoring is complete
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.MethodSource;
// import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

// TODO: Re-enable after PolicySet refactoring is complete
// import static io.sapl.util.SaplTesting.*;
// import static org.assertj.core.api.Assertions.assertThat;

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
            Class<? extends PolicySetBody> expectedStratum,
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
                        """, Decision.DENY, PolicySetDecision.class, List.of("fallback-deny")),

                new PureTestCase("first policy target false, second applies", """
                        set "guild-access"
                        first-applicable

                        policy "never-matches"
                        permit false

                        policy "always-deny"
                        deny
                        """, """
                        {
                            "subject": "alice",
                            "action": "read",
                            "resource": "data"
                        }
                        """, Decision.DENY, PurePolicySetBody.class, List.of("always-deny")),

                new PureTestCase("runtime target: first policy matches", """
                        set "watch-duties"
                        first-applicable

                        policy "captain-only"
                        permit subject == "Vimes"

                        policy "default-deny"
                        deny
                        """, """
                        {
                            "subject": "Vimes",
                            "action": "patrol",
                            "resource": "city"
                        }
                        """, Decision.PERMIT, PurePolicySetBody.class, List.of("captain-only")),

                new PureTestCase("runtime target: second policy matches", """
                        set "watch-duties"
                        first-applicable

                        policy "captain-only"
                        permit subject == "Vimes"

                        policy "sergeant-fallback"
                        permit subject == "Colon"

                        policy "default-deny"
                        deny
                        """, """
                        {
                            "subject": "Colon",
                            "action": "patrol",
                            "resource": "city"
                        }
                        """, Decision.PERMIT, PurePolicySetBody.class, List.of("sergeant-fallback")),

                new PureTestCase("runtime target: no policy matches, falls through to default", """
                        set "watch-duties"
                        first-applicable

                        policy "captain-only"
                        permit subject == "Vimes"

                        policy "sergeant-fallback"
                        permit subject == "Colon"

                        policy "default-deny"
                        deny
                        """, """
                        {
                            "subject": "Nobby",
                            "action": "patrol",
                            "resource": "city"
                        }
                        """, Decision.DENY, PurePolicySetBody.class, List.of("default-deny")),

                new PureTestCase("body condition matches", """
                        set "library-access"
                        first-applicable

                        policy "wizards-reading"
                        permit subject == "Rincewind"
                        where
                          action == "read";

                        policy "default-deny"
                        deny
                        """, """
                        {
                            "subject": "Rincewind",
                            "action": "read",
                            "resource": "book"
                        }
                        """, Decision.PERMIT, PurePolicySetBody.class, List.of("wizards-reading")),

                new PureTestCase("target matches but body fails, continues to next policy", """
                        set "library-access"
                        first-applicable

                        policy "wizards-reading"
                        permit subject == "Rincewind"
                        where
                          action == "read";

                        policy "default-deny"
                        deny
                        """, """
                        {
                            "subject": "Rincewind",
                            "action": "run",
                            "resource": "away"
                        }
                        """, Decision.DENY, PurePolicySetBody.class, List.of("default-deny")),

                // --- Policy set variable tests ---

                new PureTestCase("set variable: constant folds to short-circuit", """
                        set "constant-var"
                        first-applicable

                        var allowed = true;

                        policy "check-allowed"
                        permit allowed

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
                        permit isManager

                        policy "fallback"
                        deny
                        """, """
                        {
                            "subject": { "name": "alice", "role": "manager" },
                            "action": "read",
                            "resource": "data"
                        }
                        """, Decision.PERMIT, PurePolicySetBody.class, List.of("managers-permit")),

                new PureTestCase("set variable: subscription-dependent, condition false", """
                        set "employee-access"
                        first-applicable

                        var isManager = subject.role == "manager";

                        policy "managers-permit"
                        permit isManager

                        policy "fallback"
                        deny
                        """, """
                        {
                            "subject": { "name": "bob", "role": "employee" },
                            "action": "read",
                            "resource": "data"
                        }
                        """, Decision.DENY, PurePolicySetBody.class, List.of("fallback")),

                new PureTestCase("set variable: multiple vars used across policies", """
                        set "multi-var"
                        first-applicable

                        var dept = subject.department;
                        var isAdmin = subject.role == "admin";

                        policy "admin-access"
                        permit isAdmin

                        policy "same-department"
                        permit dept == resource.department

                        policy "fallback"
                        deny
                        """, """
                        {
                            "subject": { "department": "engineering", "role": "user" },
                            "action": "read",
                            "resource": { "department": "engineering" }
                        }
                        """, Decision.PERMIT, PurePolicySetBody.class, List.of("same-department")),

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
                        """, Decision.PERMIT, PurePolicySetBody.class, List.of("level-check")),

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
                        """, Decision.DENY, PurePolicySetBody.class, List.of("fallback")),

                // --- Error and edge case tests ---

                new PureTestCase("all policies NOT_APPLICABLE returns NOT_APPLICABLE", """
                        set "no-match"
                        first-applicable

                        policy "never-matches-1"
                        permit subject == "nobody"

                        policy "never-matches-2"
                        permit subject == "ghost"
                        """, """
                        {
                            "subject": "alice",
                            "action": "read",
                            "resource": "data"
                        }
                        """, Decision.NOT_APPLICABLE, PurePolicySetBody.class, List.of()),

                new PureTestCase("error in target expression propagates as INDETERMINATE", """
                        set "error-target"
                        first-applicable

                        policy "error-policy"
                        permit subject.missing.deeply.nested

                        policy "fallback"
                        deny
                        """, """
                        {
                            "subject": "simple-string",
                            "action": "read",
                            "resource": "data"
                        }
                        """, Decision.INDETERMINATE, PurePolicySetBody.class, List.of("error-policy")),

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
                                """, Decision.NOT_APPLICABLE, PurePolicySetBody.class, List.of()));
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
                        permit subject == "Rincewind"
                        where
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
                        permit false

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
                        """, Map.of("test.attr", new Value[] { Value.TRUE }), Decision.DENY, List.of("fallback")),

                new StreamTestCase("stream path: error in target expression propagates as INDETERMINATE", """
                        set "error-target-stream"
                        first-applicable

                        policy "error-policy"
                        permit subject.missing.deeply.nested
                        where
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
                                permit subject
                                where
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

    // TODO: Re-enable after PolicySet refactoring is complete
    // @ParameterizedTest(name = "{0}")
    // @MethodSource("pureTestCases")
    // @DisplayName("Pure evaluation")
    // void pureEvaluation(PureTestCase testCase) {
    // val compiled = compilePolicySet(testCase.policySet());
    //
    // assertThat(compiled.policies()).as("Expected
    // stratum").isInstanceOf(testCase.expectedStratum());
    //
    // val ctx = subscriptionContext(testCase.subscription());
    // val result = evaluatePolicySet(compiled, ctx);
    // assertDecisionHasAllTheseContributing(result,
    // testCase.contributingPolicies());
    // assertThat(result.decision()).isEqualTo(testCase.expectedDecision());
    //
    // val resultWithCoverage = evaluatePolicySetWithCoverage(compiled, ctx);
    // assertThat(resultWithCoverage.decision()).isEqualTo(result);
    // }
    //
    // void assertDecisionHasAllTheseContributing(PolicySetDecision decision,
    // List<String> expectedNames) {
    // val actual = decision.contributingPolicyDecisions().stream().map(contribution
    // -> contribution.metadata().name())
    // .toList();
    // assertThat(actual).containsExactlyInAnyOrder(expectedNames.toArray(new
    // String[0]));
    // }
    //
    // @ParameterizedTest(name = "{0}")
    // @MethodSource("streamTestCases")
    // @DisplayName("Stream evaluation")
    // void streamEvaluation(StreamTestCase testCase) {
    // val attrBroker = attributeBroker(testCase.attributes());
    // val compiled = compilePolicySet(testCase.policySet(), attrBroker);
    //
    // assertThat(compiled.policies()).as("Expected stream
    // stratum").isInstanceOf(StreamPolicySetBody.class);
    //
    // val streamBody = (StreamPolicySetBody) compiled.policies();
    // val subscription = parseSubscription(testCase.subscription());
    // val ctx = evaluationContext(subscription, attrBroker);
    //
    // StepVerifier.create(streamBody.stream().contextWrite(c ->
    // c.put(EvaluationContext.class, ctx)))
    // .assertNext(result -> {
    // assertDecisionHasAllTheseContributing(result,
    // testCase.contributingPolicies());
    // assertThat(result.decision()).isEqualTo(testCase.expectedDecision());
    // }).verifyComplete();
    //
    // StepVerifier.create(compiled.coverageStream().contextWrite(c ->
    // c.put(EvaluationContext.class, ctx)))
    // .assertNext(resultWithCoverage ->
    // assertThat(resultWithCoverage.decision().decision())
    // .isEqualTo(testCase.expectedDecision()))
    // .verifyComplete();
    // }

}
