/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.compiler;

import io.sapl.api.model.*;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.internal.TraceFields;
import io.sapl.functions.libraries.FilterFunctionLibrary;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
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

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static io.sapl.api.model.ValueJsonMarshaller.toPrettyString;
import static io.sapl.api.pdp.internal.TracedPolicyDecision.getTargetError;
import static io.sapl.api.pdp.internal.TracedPolicyDecision.hasTargetError;
import static io.sapl.api.pdp.internal.TracedPolicyDecision.isNoMatchTrace;
import static io.sapl.api.pdp.internal.TracedPolicySetDecision.*;

import io.sapl.api.pdp.internal.TracedPolicyDecision;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Tests for TracedPolicySetDecision emission from compiled policy sets.
 * <p>
 * Validates that policy set combining algorithms produce correct traced output
 * structure with decision, algorithm, and
 * nested policy traces.
 */
@DisplayName("TracedPolicySetDecision")
class TracedPolicySetDecisionTests {

    private static final DefaultSAPLInterpreter PARSER = new DefaultSAPLInterpreter();
    private static final boolean                DEBUG  = true;

    private CompilationContext context;

    @BeforeEach
    @SneakyThrows
    void setup() {
        context = PolicyDecisionPointBuilder.withoutDefaults().withFunctionLibrary(FilterFunctionLibrary.class).build()
                .compilationContext();
    }

    @Nested
    @DisplayName("Deny-Overrides Algorithm")
    class DenyOverridesTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("denyOverridesCases")
        @DisplayName("produces correct traced decision")
        void whenDenyOverrides_thenCorrectTracedDecision(String description, String policySet,
                Map<String, Value> variables, Decision expectedDecision, int expectedPolicies) {

            val traced = evaluatePolicySet(policySet, variables);

            printDecision(description, traced);

            assertThat(traced).isInstanceOf(ObjectValue.class);
            assertThat(getName(traced)).isEqualTo("arkham-archives");
            assertThat(getAlgorithm(traced)).isEqualTo("deny-overrides");
            assertThat(getDecision(traced)).isEqualTo(expectedDecision);
            assertThat(isPolicySet(traced)).isTrue();
            assertThat(getPolicies(traced)).hasSize(expectedPolicies);
        }

        static Stream<Arguments> denyOverridesCases() {
            return Stream.of(arguments("deny wins over permit", """
                    set "arkham-archives" deny-overrides
                    policy "allow-researcher" permit
                    policy "restrict-forbidden-section" deny
                    """, Map.of(), Decision.DENY, 2),

                    arguments("all permits yields permit", """
                            set "arkham-archives" deny-overrides
                            policy "allow-faculty" permit
                            policy "allow-graduate" permit
                            """, Map.of(), Decision.PERMIT, 2),

                    arguments("all not-applicable yields not-applicable", """
                            set "arkham-archives" deny-overrides
                            policy "faculty-only" permit where subject.role == "faculty";
                            policy "graduate-only" permit where subject.role == "graduate";
                            """, Map.of("subject", json("{\"role\": \"outsider\"}")), Decision.NOT_APPLICABLE, 2),

                    arguments("single policy matching", """
                            set "arkham-archives" deny-overrides
                            policy "cultist-check" permit where subject.isCultist == false;
                            policy "sanity-check" deny where subject.sanity < 20;
                            """, Map.of("subject", json("{\"isCultist\": false, \"sanity\": 80}")), Decision.PERMIT,
                            2));
        }

        @Test
        @DisplayName("nested policies contain full trace information")
        void whenDenyOverrides_thenNestedPoliciesHaveTraces() {
            val policySet = """
                    set "arkham-archives" deny-overrides
                    policy "elder-sign-check" permit
                    obligation "log-access"
                    policy "shoggoth-alert" deny
                    advice "warn-investigator"
                    """;

            val traced = evaluatePolicySet(policySet, Map.of());

            printDecision("nested policies with traces", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.DENY);

            val policies = getPolicies(traced);
            assertThat(policies).hasSize(2);

            val permitPolicy = (ObjectValue) policies.get(0);
            assertThat(permitPolicy.get(TraceFields.NAME)).isEqualTo(Value.of("elder-sign-check"));
            assertThat(permitPolicy.get(TraceFields.ENTITLEMENT)).isEqualTo(Value.of("PERMIT"));
            assertThat(permitPolicy.get(TraceFields.DECISION)).isEqualTo(Value.of("PERMIT"));

            val denyPolicy = (ObjectValue) policies.get(1);
            assertThat(denyPolicy.get(TraceFields.NAME)).isEqualTo(Value.of("shoggoth-alert"));
            assertThat(denyPolicy.get(TraceFields.ENTITLEMENT)).isEqualTo(Value.of("DENY"));
            assertThat(denyPolicy.get(TraceFields.DECISION)).isEqualTo(Value.of("DENY"));
        }
    }

    @Nested
    @DisplayName("Permit-Overrides Algorithm")
    class PermitOverridesTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("permitOverridesCases")
        @DisplayName("produces correct traced decision")
        void whenPermitOverrides_thenCorrectTracedDecision(String description, String policySet,
                Map<String, Value> variables, Decision expectedDecision, int expectedPolicies) {

            val traced = evaluatePolicySet(policySet, variables);

            printDecision(description, traced);

            assertThat(getAlgorithm(traced)).isEqualTo("permit-overrides");
            assertThat(getDecision(traced)).isEqualTo(expectedDecision);
            assertThat(getPolicies(traced)).hasSize(expectedPolicies);
        }

        static Stream<Arguments> permitOverridesCases() {
            return Stream.of(arguments("permit wins over deny", """
                    set "miskatonic-library" permit-overrides
                    policy "deny-outsiders" deny
                    policy "allow-key-holders" permit
                    """, Map.of(), Decision.PERMIT, 2),

                    arguments("all denies yields deny", """
                            set "miskatonic-library" permit-overrides
                            policy "deny-cultists" deny
                            policy "deny-after-midnight" deny
                            """, Map.of(), Decision.DENY, 2),

                    arguments("mixed with not-applicable", """
                            set "miskatonic-library" permit-overrides
                            policy "faculty-permit" permit where subject.isFaculty == true;
                            policy "default-deny" deny
                            """, Map.of("subject", json("{\"isFaculty\": true}")), Decision.PERMIT, 2));
        }
    }

    @Nested
    @DisplayName("First-Applicable Algorithm")
    class FirstApplicableTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("firstApplicableCases")
        @DisplayName("produces correct traced decision")
        void whenFirstApplicable_thenCorrectTracedDecision(String description, String policySet,
                Map<String, Value> variables, Decision expectedDecision, int expectedEvaluatedPolicies) {

            val traced = evaluatePolicySet(policySet, variables);

            printDecision(description, traced);

            assertThat(getAlgorithm(traced)).isEqualTo("first-applicable");
            assertThat(getDecision(traced)).isEqualTo(expectedDecision);
            assertThat(getPolicies(traced)).as("Only evaluated policies should be in trace")
                    .hasSize(expectedEvaluatedPolicies);
        }

        static Stream<Arguments> firstApplicableCases() {
            return Stream.of(arguments("first policy applies", """
                    set "ritual-chamber" first-applicable
                    policy "high-priest-access" permit where subject.rank == "high-priest";
                    policy "acolyte-check" permit where subject.rank == "acolyte";
                    policy "default-deny" deny
                    """, Map.of("subject", json("{\"rank\": \"high-priest\"}")), Decision.PERMIT, 1),

                    arguments("second policy applies", """
                            set "ritual-chamber" first-applicable
                            policy "high-priest-access" permit where subject.rank == "high-priest";
                            policy "acolyte-check" permit where subject.rank == "acolyte";
                            policy "default-deny" deny
                            """, Map.of("subject", json("{\"rank\": \"acolyte\"}")), Decision.PERMIT, 2),

                    arguments("falls through to default", """
                            set "ritual-chamber" first-applicable
                            policy "high-priest-access" permit where subject.rank == "high-priest";
                            policy "acolyte-check" permit where subject.rank == "acolyte";
                            policy "default-deny" deny
                            """, Map.of("subject", json("{\"rank\": \"outsider\"}")), Decision.DENY, 3),

                    arguments("short-circuits on first applicable", """
                            set "ritual-chamber" first-applicable
                            policy "always-permit" permit
                            policy "never-reached" deny
                            """, Map.of(), Decision.PERMIT, 1));
        }

        @Test
        @DisplayName("trace shows evaluated policies including non-matching for order evidence")
        void whenFirstApplicableShortCircuits_thenEvaluatedPoliciesIncludeNonMatching() {
            val policySet = """
                    set "eldritch-gate" first-applicable
                    policy "key-holder" permit where subject.hasKey == true;
                    policy "initiate" permit where subject.isInitiate == true;
                    policy "default-lock" deny
                    """;

            val traced = evaluatePolicySet(policySet, Map.of("subject", json("{\"hasKey\": true}")));

            printDecision("short-circuit trace with order evidence", traced);

            val policies = getPolicies(traced);
            assertThat(policies).hasSize(1);

            val matchingPolicy = (ObjectValue) policies.getFirst();
            assertThat(matchingPolicy.get(TraceFields.NAME)).isEqualTo(Value.of("key-holder"));
            assertThat(isNoMatchTrace(matchingPolicy)).isFalse();
        }
    }

    @Nested
    @DisplayName("First-Applicable Order Evidence")
    class FirstApplicableOrderEvidenceTests {

        @Test
        @DisplayName("non-matching policies appear in trace with targetMatch=false")
        void whenPoliciesDoNotMatch_thenAppearInTraceWithTargetMatchFalse() {
            val policySet = """
                    set "cursed-library" first-applicable
                    policy "elder-only" permit subject.age > 1000
                    policy "initiate-only" permit subject.isInitiate == true
                    policy "default-deny" deny
                    """;

            val traced = evaluatePolicySet(policySet, Map.of("subject", json("{\"age\": 30, \"isInitiate\": false}")));

            printDecision("non-matching policies in trace", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.DENY);

            val policies = getPolicies(traced);
            assertThat(policies).hasSize(3);

            val elderPolicy = (ObjectValue) policies.get(0);
            assertThat(elderPolicy.get(TraceFields.NAME)).isEqualTo(Value.of("elder-only"));
            assertThat(isNoMatchTrace(elderPolicy)).isTrue();
            assertThat(elderPolicy.get(TraceFields.TARGET_MATCH)).isEqualTo(Value.FALSE);

            val initiatePolicy = (ObjectValue) policies.get(1);
            assertThat(initiatePolicy.get(TraceFields.NAME)).isEqualTo(Value.of("initiate-only"));
            assertThat(isNoMatchTrace(initiatePolicy)).isTrue();

            val denyPolicy = (ObjectValue) policies.get(2);
            assertThat(denyPolicy.get(TraceFields.NAME)).isEqualTo(Value.of("default-deny"));
            assertThat(isNoMatchTrace(denyPolicy)).isFalse();
            assertThat(denyPolicy.get(TraceFields.DECISION)).isEqualTo(Value.of("DENY"));
        }

        @Test
        @DisplayName("trace order matches evaluation order")
        void whenPoliciesEvaluated_thenTraceOrderMatchesEvaluationOrder() {
            val policySet = """
                    set "ritual-sequence" first-applicable
                    policy "first-check" permit subject.level >= 5
                    policy "second-check" permit subject.level >= 3
                    policy "third-check" permit subject.level >= 1
                    policy "fallback" deny
                    """;

            val traced = evaluatePolicySet(policySet, Map.of("subject", json("{\"level\": 2}")));

            printDecision("evaluation order trace", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);

            val policies = getPolicies(traced);
            assertThat(policies).hasSize(3);

            assertThat(TracedPolicyDecision.getName(policies.get(0))).isEqualTo("first-check");
            assertThat(isNoMatchTrace(policies.get(0))).isTrue();

            assertThat(TracedPolicyDecision.getName(policies.get(1))).isEqualTo("second-check");
            assertThat(isNoMatchTrace(policies.get(1))).isTrue();

            assertThat(TracedPolicyDecision.getName(policies.get(2))).isEqualTo("third-check");
            assertThat(isNoMatchTrace(policies.get(2))).isFalse();
            assertThat(TracedPolicyDecision.getDecision(policies.get(2))).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("short-circuit excludes policies after winning decision")
        void whenDecisionReached_thenSubsequentPoliciesNotInTrace() {
            val policySet = """
                    set "gate-sequence" first-applicable
                    policy "no-match-first" permit subject.x == "impossible"
                    policy "winner" permit
                    policy "never-reached-1" deny
                    policy "never-reached-2" permit
                    """;

            val traced = evaluatePolicySet(policySet, Map.of("subject", json("{\"x\": \"normal\"}")));

            printDecision("short-circuit exclusion", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);

            val policies = getPolicies(traced);
            assertThat(policies).hasSize(2);

            assertThat(TracedPolicyDecision.getName(policies.get(0))).isEqualTo("no-match-first");
            assertThat(isNoMatchTrace(policies.get(0))).isTrue();

            assertThat(TracedPolicyDecision.getName(policies.get(1))).isEqualTo("winner");
            assertThat(TracedPolicyDecision.getDecision(policies.get(1))).isEqualTo(Decision.PERMIT);

            assertThat(getTotalPolicies(traced)).isEqualTo(4);
        }

        @Test
        @DisplayName("NOT_APPLICABLE policies appear in trace with full details")
        void whenPolicyNotApplicable_thenFullTraceIncluded() {
            val policySet = """
                    set "conditional-access" first-applicable
                    policy "no-target-match" permit subject.role == "admin"
                    policy "body-false" permit where subject.clearance > 5;
                    policy "winner" deny
                    """;

            val traced = evaluatePolicySet(policySet,
                    Map.of("subject", json("{\"role\": \"user\", \"clearance\": 2}")));

            printDecision("NOT_APPLICABLE in trace", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.DENY);

            val policies = getPolicies(traced);
            assertThat(policies).hasSize(3);

            val noTargetMatch = (ObjectValue) policies.get(0);
            assertThat(noTargetMatch.get(TraceFields.NAME)).isEqualTo(Value.of("no-target-match"));
            assertThat(isNoMatchTrace(noTargetMatch)).isTrue();

            val bodyFalse = (ObjectValue) policies.get(1);
            assertThat(bodyFalse.get(TraceFields.NAME)).isEqualTo(Value.of("body-false"));
            assertThat(isNoMatchTrace(bodyFalse)).isFalse();
            assertThat(bodyFalse.get(TraceFields.DECISION)).isEqualTo(Value.of("NOT_APPLICABLE"));

            val winner = (ObjectValue) policies.get(2);
            assertThat(winner.get(TraceFields.NAME)).isEqualTo(Value.of("winner"));
            assertThat(winner.get(TraceFields.DECISION)).isEqualTo(Value.of("DENY"));
        }

        @Test
        @DisplayName("totalPolicies combined with trace proves completeness")
        void whenAllPoliciesTracked_thenCompletenessProvable() {
            val policySet = """
                    set "completeness-test" first-applicable
                    policy "A" permit subject.x == 1
                    policy "B" permit subject.x == 2
                    policy "C" permit subject.x == 3
                    policy "D" deny
                    policy "E" permit
                    """;

            val traced = evaluatePolicySet(policySet, Map.of("subject", json("{\"x\": 3}")));

            printDecision("completeness proof", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);
            assertThat(getTotalPolicies(traced)).isEqualTo(5);

            val policies = getPolicies(traced);
            assertThat(policies).hasSize(3);

            assertThat(TracedPolicyDecision.getName(policies.get(0))).isEqualTo("A");
            assertThat(isNoMatchTrace(policies.get(0))).isTrue();

            assertThat(TracedPolicyDecision.getName(policies.get(1))).isEqualTo("B");
            assertThat(isNoMatchTrace(policies.get(1))).isTrue();

            assertThat(TracedPolicyDecision.getName(policies.get(2))).isEqualTo("C");
            assertThat(TracedPolicyDecision.getDecision(policies.get(2))).isEqualTo(Decision.PERMIT);
        }

        @Test
        @DisplayName("error in target still includes preceding non-matching policies")
        void whenTargetErrors_thenPrecedingNonMatchingPoliciesIncluded() {
            val policySet = """
                    set "error-sequence" first-applicable
                    policy "no-match-before-error" permit subject.x == "impossible"
                    policy "error-target" permit 1 / subject.zero > 0
                    policy "never-reached" deny
                    """;

            val traced = evaluatePolicySet(policySet, Map.of("subject", json("{\"x\": \"normal\", \"zero\": 0}")));

            printDecision("error with preceding non-match", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.INDETERMINATE);

            val policies = getPolicies(traced);
            assertThat(policies).hasSize(2);

            assertThat(TracedPolicyDecision.getName(policies.get(0))).isEqualTo("no-match-before-error");
            assertThat(isNoMatchTrace(policies.get(0))).isTrue();

            assertThat(TracedPolicyDecision.getName(policies.get(1))).isEqualTo("error-target");
            assertThat(hasTargetError(policies.get(1))).isTrue();
        }

        @Test
        @DisplayName("all policies non-matching yields NOT_APPLICABLE with all in trace")
        void whenAllPoliciesNonMatching_thenAllInTraceWithNotApplicable() {
            val policySet = """
                    set "no-matches" first-applicable
                    policy "check-1" permit subject.a == true
                    policy "check-2" permit subject.b == true
                    policy "check-3" deny subject.c == true
                    """;

            val traced = evaluatePolicySet(policySet,
                    Map.of("subject", json("{\"a\": false, \"b\": false, \"c\": false}")));

            printDecision("all non-matching", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.NOT_APPLICABLE);
            assertThat(getTotalPolicies(traced)).isEqualTo(3);

            val policies = getPolicies(traced);
            assertThat(policies).hasSize(3);

            for (val policy : policies) {
                assertThat(isNoMatchTrace(policy)).isTrue();
            }
        }

        @Test
        @DisplayName("no-match trace contains minimal fields only")
        void whenNoMatch_thenTraceIsMinimal() {
            val policySet = """
                    set "minimal-trace" first-applicable
                    policy "no-match" permit subject.x == "impossible"
                    policy "winner" deny
                    """;

            val traced = evaluatePolicySet(policySet, Map.of("subject", json("{\"x\": \"normal\"}")));

            printDecision("minimal no-match trace", traced);

            val policies      = getPolicies(traced);
            val noMatchPolicy = (ObjectValue) policies.get(0);

            assertThat(noMatchPolicy.get(TraceFields.NAME)).isEqualTo(Value.of("no-match"));
            assertThat(noMatchPolicy.get(TraceFields.TYPE)).isEqualTo(Value.of(TraceFields.TYPE_POLICY));
            assertThat(noMatchPolicy.get(TraceFields.TARGET_MATCH)).isEqualTo(Value.FALSE);

            assertThat(noMatchPolicy.containsKey(TraceFields.DECISION)).isFalse();
            assertThat(noMatchPolicy.containsKey(TraceFields.ENTITLEMENT)).isFalse();
            assertThat(noMatchPolicy.containsKey(TraceFields.OBLIGATIONS)).isFalse();
            assertThat(noMatchPolicy.containsKey(TraceFields.ADVICE)).isFalse();
        }
    }

    @Nested
    @DisplayName("Only-One-Applicable Algorithm")
    class OnlyOneApplicableTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("onlyOneApplicableCases")
        @DisplayName("produces correct traced decision")
        void whenOnlyOneApplicable_thenCorrectTracedDecision(String description, String policySet,
                Map<String, Value> variables, Decision expectedDecision) {

            val traced = evaluatePolicySet(policySet, variables);

            printDecision(description, traced);

            assertThat(getAlgorithm(traced)).isEqualTo("only-one-applicable");
            assertThat(getDecision(traced)).isEqualTo(expectedDecision);
        }

        static Stream<Arguments> onlyOneApplicableCases() {
            return Stream.of(arguments("exactly one matches - permit", """
                    set "necronomicon-vault" only-one-applicable
                    policy "senior-archivist" permit where subject.rank == "senior";
                    policy "junior-archivist" deny where subject.rank == "junior";
                    """, Map.of("subject", json("{\"rank\": \"senior\"}")), Decision.PERMIT),

                    arguments("exactly one matches - deny", """
                            set "necronomicon-vault" only-one-applicable
                            policy "senior-archivist" permit where subject.rank == "senior";
                            policy "junior-archivist" deny where subject.rank == "junior";
                            """, Map.of("subject", json("{\"rank\": \"junior\"}")), Decision.DENY),

                    arguments("none applicable yields not-applicable", """
                            set "necronomicon-vault" only-one-applicable
                            policy "senior-archivist" permit where subject.rank == "senior";
                            policy "junior-archivist" deny where subject.rank == "junior";
                            """, Map.of("subject", json("{\"rank\": \"outsider\"}")), Decision.NOT_APPLICABLE),

                    arguments("multiple applicable yields indeterminate", """
                            set "necronomicon-vault" only-one-applicable
                            policy "researcher-access" permit where subject.isResearcher == true;
                            policy "faculty-access" permit where subject.isFaculty == true;
                            """, Map.of("subject", json("{\"isResearcher\": true, \"isFaculty\": true}")),
                            Decision.INDETERMINATE));
        }

        @Test
        @DisplayName("multiple applicable shows all in trace")
        void whenMultipleApplicable_thenAllShownInTrace() {
            val policySet = """
                    set "forbidden-tome" only-one-applicable
                    policy "researcher" permit where subject.isResearcher == true;
                    policy "faculty" permit where subject.isFaculty == true;
                    """;

            val traced = evaluatePolicySet(policySet,
                    Map.of("subject", json("{\"isResearcher\": true, \"isFaculty\": true}")));

            printDecision("multiple applicable trace", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.INDETERMINATE);
            assertThat(getPolicies(traced)).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Deny-Unless-Permit Algorithm")
    class DenyUnlessPermitTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("denyUnlessPermitCases")
        @DisplayName("produces correct traced decision")
        void whenDenyUnlessPermit_thenCorrectTracedDecision(String description, String policySet,
                Map<String, Value> variables, Decision expectedDecision) {

            val traced = evaluatePolicySet(policySet, variables);

            printDecision(description, traced);

            assertThat(getAlgorithm(traced)).isEqualTo("deny-unless-permit");
            assertThat(getDecision(traced)).isEqualTo(expectedDecision);
        }

        static Stream<Arguments> denyUnlessPermitCases() {
            return Stream.of(arguments("permit when permit exists", """
                    set "deep-ones-temple" deny-unless-permit
                    policy "high-priest-only" permit
                    """, Map.of(), Decision.PERMIT),

                    arguments("deny when no permit", """
                            set "deep-ones-temple" deny-unless-permit
                            policy "initiate-check" permit where subject.isInitiate == true;
                            """, Map.of("subject", json("{\"isInitiate\": false}")), Decision.DENY),

                    arguments("deny when only not-applicable", """
                            set "deep-ones-temple" deny-unless-permit
                            policy "elder-only" permit where subject.age > 1000;
                            """, Map.of("subject", json("{\"age\": 30}")), Decision.DENY));
        }
    }

    @Nested
    @DisplayName("Permit-Unless-Deny Algorithm")
    class PermitUnlessDenyTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("permitUnlessDenyCases")
        @DisplayName("produces correct traced decision")
        void whenPermitUnlessDeny_thenCorrectTracedDecision(String description, String policySet,
                Map<String, Value> variables, Decision expectedDecision) {

            val traced = evaluatePolicySet(policySet, variables);

            printDecision(description, traced);

            assertThat(getAlgorithm(traced)).isEqualTo("permit-unless-deny");
            assertThat(getDecision(traced)).isEqualTo(expectedDecision);
        }

        static Stream<Arguments> permitUnlessDenyCases() {
            return Stream.of(arguments("permit when no deny", """
                    set "innsmouth-harbor" permit-unless-deny
                    policy "visitor-check" permit where subject.isVisitor == true;
                    """, Map.of("subject", json("{\"isVisitor\": false}")), Decision.PERMIT),

                    arguments("deny when deny exists", """
                            set "innsmouth-harbor" permit-unless-deny
                            policy "block-federal-agents" deny where subject.isFederalAgent == true;
                            """, Map.of("subject", json("{\"isFederalAgent\": true}")), Decision.DENY),

                    arguments("permit when empty (not-applicable)", """
                            set "innsmouth-harbor" permit-unless-deny
                            policy "deep-one-hybrid" deny where subject.isHybrid == true;
                            """, Map.of("subject", json("{\"isHybrid\": false}")), Decision.PERMIT));
        }
    }

    @Nested
    @DisplayName("Constraint Merging")
    class ConstraintMergingTests {

        @Test
        @DisplayName("obligations from winning decision are preserved")
        void whenWinningPolicyHasObligations_thenObligationsPreserved() {
            val policySet = """
                    set "arkham-asylum" deny-overrides
                    policy "allow-with-logging" permit
                    obligation "log-entry"
                    obligation { "notify": "security" }
                    """;

            val traced = evaluatePolicySet(policySet, Map.of());

            printDecision("obligations preserved", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);
            assertThat(getObligations(traced)).hasSize(2).contains(Value.of("log-entry"),
                    json("{\"notify\": \"security\"}"));
        }

        @Test
        @DisplayName("advice from winning decision is preserved")
        void whenWinningPolicyHasAdvice_thenAdvicePreserved() {
            val policySet = """
                    set "dreamlands-gate" permit-overrides
                    policy "dreamer-access" permit
                    advice "bring-silver-key"
                    advice { "warning": "beware-of-gugs" }
                    """;

            val traced = evaluatePolicySet(policySet, Map.of());

            printDecision("advice preserved", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);
            assertThat(getAdvice(traced)).hasSize(2).contains(Value.of("bring-silver-key"),
                    json("{\"warning\": \"beware-of-gugs\"}"));
        }

        @Test
        @DisplayName("resource transformation is preserved")
        void whenWinningPolicyHasTransform_thenResourcePreserved() {
            val policySet = """
                    set "silver-twilight-lodge" permit-overrides
                    policy "filtered-access" permit
                    transform { "sanitized": resource.public }
                    """;

            val traced = evaluatePolicySet(policySet,
                    Map.of("resource", json("{\"public\": \"visible\", \"secret\": \"hidden\"}")));

            printDecision("resource transformation", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);
            assertThat(getResource(traced)).isEqualTo(json("{\"sanitized\": \"visible\"}"));
        }

        @Test
        @DisplayName("multiple policies merge obligations")
        void whenMultiplePoliciesWithObligations_thenObligationsMerged() {
            val policySet = """
                    set "witch-house" deny-overrides
                    policy "allow-with-ward" permit
                    obligation "activate-ward"
                    policy "allow-with-seal" permit
                    obligation "check-seal"
                    """;

            val traced = evaluatePolicySet(policySet, Map.of());

            printDecision("merged obligations", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);
            assertThat(getObligations(traced)).hasSize(2).contains(Value.of("activate-ward"), Value.of("check-seal"));
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("policy error yields indeterminate in deny-overrides")
        void whenPolicyError_thenIndeterminate() {
            val policySet = """
                    set "haunted-house" deny-overrides
                    policy "broken-check" permit where 1 / subject.zero > 0;
                    policy "normal-permit" permit
                    """;

            val traced = evaluatePolicySet(policySet, Map.of("subject", json("{\"zero\": 0}")));

            printDecision("policy error handling", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.INDETERMINATE);
        }

        @Test
        @DisplayName("constraint error yields indeterminate")
        void whenConstraintError_thenIndeterminate() {
            val policySet = """
                    set "black-goat-woods" deny-overrides
                    policy "cursed-permit" permit
                    obligation 1 / subject.zero
                    """;

            val traced = evaluatePolicySet(policySet, Map.of("subject", json("{\"zero\": 0}")));

            printDecision("constraint error", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.INDETERMINATE);
        }
    }

    @Nested
    @DisplayName("Type Field")
    class TypeFieldTests {

        @Test
        @DisplayName("policy set has type 'set'")
        void whenPolicySet_thenTypeIsSet() {
            val policySet = """
                    set "yog-sothoth-gate" deny-overrides
                    policy "key-holder" permit
                    """;

            val traced = evaluatePolicySet(policySet, Map.of());

            printDecision("type field", traced);

            assertThat(isPolicySet(traced)).isTrue();
            val obj = (ObjectValue) traced;
            assertThat(obj.get(TraceFields.TYPE)).isEqualTo(Value.of(TraceFields.TYPE_SET));
        }

        @Test
        @DisplayName("nested policies have type 'policy'")
        void whenNestedPolicies_thenTypeIsPolicy() {
            val policySet = """
                    set "cthulhu-cult" deny-overrides
                    policy "cultist-check" permit
                    policy "outsider-block" deny
                    """;

            val traced = evaluatePolicySet(policySet, Map.of());

            printDecision("nested policy types", traced);

            val policies = getPolicies(traced);
            for (val policy : policies) {
                val policyObj = (ObjectValue) policy;
                assertThat(policyObj.get(TraceFields.TYPE)).isEqualTo(Value.of(TraceFields.TYPE_POLICY));
            }
        }
    }

    @Nested
    @DisplayName("Evaluation Paths")
    class EvaluationPathTests {

        @Test
        @DisplayName("empty policy set produces Value directly")
        void whenEmptyPolicySet_thenProducesValue() {
            // Value path is only taken when policy list is empty - combining algorithms
            // return constant result directly without runtime evaluation
            val emptySet = CombiningAlgorithmCompiler.denyOverrides("empty-archive", "deny-overrides", List.of());

            assertThat(emptySet).isInstanceOf(Value.class);

            val traced = (Value) emptySet;
            printDecision("empty policy set (Value path)", traced);

            assertThat(isPolicySet(traced)).isTrue();
            assertThat(getDecision(traced)).isEqualTo(Decision.NOT_APPLICABLE);
            assertThat(getPolicies(traced)).isEmpty();
        }

        @Test
        @DisplayName("constant policy set produces PureExpression for runtime combining")
        void whenConstantPolicySet_thenProducesPureExpression() {
            // Even constant policies require PureExpression because combining
            // happens at runtime (iterate, accumulate, build traced set decision)
            val policySet = """
                    set "constant-archive" deny-overrides
                    policy "always-permit" permit
                    policy "always-deny" deny
                    """;

            val compiled = compilePolicy(policySet);

            assertThat(compiled.decisionExpression()).isInstanceOf(PureExpression.class);

            val evalCtx = createEvaluationContext(Map.of());
            val traced  = ((PureExpression) compiled.decisionExpression()).evaluate(evalCtx);
            printDecision("constant policy set (PureExpression path)", traced);

            assertThat(isPolicySet(traced)).isTrue();
            assertThat(getDecision(traced)).isEqualTo(Decision.DENY);
            assertThat(getPolicies(traced)).hasSize(2);
        }

        @Test
        @DisplayName("streaming policy set produces StreamExpression")
        void whenStreamingPolicySet_thenProducesStreamExpression() {
            val policySet = """
                    set "streaming-archive" deny-overrides
                    policy "dynamic-permit" permit
                    obligation subject.dynamicObligation
                    """;

            val compiled = compilePolicy(policySet);

            assertThat(compiled.decisionExpression()).isInstanceOf(StreamExpression.class);

            val subject = json("{\"dynamicObligation\": \"log-access\"}");
            val evalCtx = createEvaluationContext(Map.of("subject", subject));

            val streamExpr = (StreamExpression) compiled.decisionExpression();
            StepVerifier.create(streamExpr.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalCtx)))
                    .assertNext(traced -> {
                        printDecision("streaming policy set (StreamExpression path)", traced);
                        assertThat(isPolicySet(traced)).isTrue();
                        assertThat(getDecision(traced)).isEqualTo(Decision.PERMIT);
                        assertThat(getObligations(traced)).hasSize(1).first().isEqualTo(Value.of("log-access"));
                    }).thenCancel().verify();
        }
    }

    @Nested
    @DisplayName("Target Error Tracing")
    class TargetErrorTracingTests {

        @Test
        @DisplayName("target expression error captured in trace with targetError field")
        void whenTargetExpressionErrors_thenTargetErrorCapturedInTrace() {
            val policySet = """
                    set "eldritch-archive" deny-overrides
                    policy "broken-target" permit subject == 1 / subject.zero
                    policy "normal-policy" deny
                    """;

            val traced = evaluatePolicySet(policySet, Map.of("subject", json("{\"zero\": 0}")));

            printDecision("target error in trace", traced);

            // deny-overrides: DENY beats INDETERMINATE from PERMIT-entitled policy
            assertThat(getDecision(traced)).isEqualTo(Decision.DENY);

            val policies        = getPolicies(traced);
            val brokenPolicy    = policies.getFirst();
            val brokenPolicyObj = (ObjectValue) brokenPolicy;

            assertThat(hasTargetError(brokenPolicy)).isTrue();
            val targetError = getTargetError(brokenPolicy);
            assertThat(targetError).isNotNull();
            assertThat(targetError.get(TraceFields.MESSAGE)).isInstanceOf(TextValue.class);
            assertThat(((TextValue) targetError.get(TraceFields.MESSAGE)).value()).contains("Division by zero");

            assertThat(brokenPolicyObj.get(TraceFields.DECISION)).isEqualTo(Value.of("INDETERMINATE"));
        }

        @Test
        @DisplayName("target error includes entitlement from original policy")
        void whenTargetErrors_thenEntitlementPreserved() {
            val policySet = """
                    set "forbidden-section" permit-overrides
                    policy "cursed-permit" permit 1 / subject.zero > 0
                    policy "cursed-deny" deny 1 / subject.zero > 0
                    """;

            val traced = evaluatePolicySet(policySet, Map.of("subject", json("{\"zero\": 0}")));

            printDecision("target error entitlement", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.INDETERMINATE);

            val policies = getPolicies(traced);
            assertThat(policies).hasSize(2);

            for (val policy : policies) {
                assertThat(hasTargetError(policy)).isTrue();
                val targetError = getTargetError(policy);
                assertThat(targetError).isNotNull();
                assertThat(((TextValue) targetError.get(TraceFields.MESSAGE)).value()).contains("Division by zero");
            }
        }

        @Test
        @DisplayName("non-boolean target expression yields error in trace")
        void whenTargetReturnsNonBoolean_thenErrorCaptured() {
            val policySet = """
                    set "strange-archive" deny-overrides
                    policy "non-boolean-target" permit subject.name
                    """;

            val traced = evaluatePolicySet(policySet, Map.of("subject", json("{\"name\": \"Cthulhu\"}")));

            printDecision("non-boolean target error", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.INDETERMINATE);

            val policies     = getPolicies(traced);
            val brokenPolicy = policies.getFirst();

            assertThat(hasTargetError(brokenPolicy)).isTrue();
            val targetError = getTargetError(brokenPolicy);
            assertThat(targetError).isNotNull();
            assertThat(((TextValue) targetError.get(TraceFields.MESSAGE)).value()).contains("Type mismatch error.");
        }

        @Test
        @DisplayName("mixed policies - some with target errors, some without")
        void whenMixedTargetResults_thenOnlyErrorsHaveTargetErrorField() {
            val policySet = """
                    set "mixed-archive" deny-overrides
                    policy "broken-policy" permit 1 / subject.zero > 0
                    policy "working-policy" deny
                    """;

            val traced = evaluatePolicySet(policySet, Map.of("subject", json("{\"zero\": 0}")));

            printDecision("mixed target results", traced);

            // deny-overrides: DENY beats INDETERMINATE from PERMIT-entitled policy
            assertThat(getDecision(traced)).isEqualTo(Decision.DENY);

            val policies      = getPolicies(traced);
            val brokenPolicy  = policies.get(0);
            val workingPolicy = policies.get(1);

            assertThat(hasTargetError(brokenPolicy)).isTrue();
            assertThat(hasTargetError(workingPolicy)).isFalse();

            val workingPolicyObj = (ObjectValue) workingPolicy;
            assertThat(workingPolicyObj.get(TraceFields.DECISION)).isEqualTo(Value.of("DENY"));
        }

        @Test
        @DisplayName("target error in first-applicable stops evaluation and captures error")
        void whenTargetErrorInFirstApplicable_thenEvaluationStopsWithError() {
            val policySet = """
                    set "sequential-archive" first-applicable
                    policy "error-first" permit 1 / subject.zero > 0
                    policy "would-permit" permit
                    """;

            val traced = evaluatePolicySet(policySet, Map.of("subject", json("{\"zero\": 0}")));

            printDecision("first-applicable target error", traced);

            assertThat(getDecision(traced)).isEqualTo(Decision.INDETERMINATE);

            val policies = getPolicies(traced);
            assertThat(policies).hasSize(1);

            val errorPolicy = policies.getFirst();
            assertThat(hasTargetError(errorPolicy)).isTrue();
        }

        @Test
        @DisplayName("streaming path captures target errors")
        void whenStreamingPolicyWithTargetError_thenErrorCaptured() {
            val policySet = """
                    set "streaming-archive" deny-overrides
                    policy "broken-stream" permit 1 / subject.zero > 0
                    obligation subject.value
                    policy "working-stream" deny
                    """;

            val compiled = compilePolicy(policySet);
            val subject  = json("{\"zero\": 0, \"value\": \"test\"}");
            val evalCtx  = createEvaluationContext(Map.of("subject", subject));

            assertThat(compiled.decisionExpression()).isInstanceOf(StreamExpression.class);

            val streamExpr = (StreamExpression) compiled.decisionExpression();
            StepVerifier.create(streamExpr.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalCtx)))
                    .assertNext(traced -> {
                        printDecision("streaming target error", traced);
                        // deny-overrides: DENY beats INDETERMINATE from PERMIT-entitled policy
                        assertThat(getDecision(traced)).isEqualTo(Decision.DENY);

                        val policies = getPolicies(traced);
                        val brokenPolicy = policies.getFirst();
                        assertThat(hasTargetError(brokenPolicy)).isTrue();
                    }).thenCancel().verify();
        }
    }

    private CompiledPolicy compilePolicy(String policySetText) {
        val sapl = PARSER.parse(policySetText);
        return SaplCompiler.compileDocument(sapl, context);
    }

    private Value evaluatePolicySet(String policySetText, Map<String, Value> variables) {
        val compiled = compilePolicy(policySetText);
        val evalCtx  = createEvaluationContext(variables);
        return evaluateExpression(compiled.decisionExpression(), evalCtx);
    }

    private Value evaluateExpression(CompiledExpression expression, EvaluationContext evalCtx) {
        return switch (expression) {
        case Value value             -> value;
        case PureExpression pure     -> pure.evaluate(evalCtx);
        case StreamExpression stream ->
            stream.stream().contextWrite(ctx -> ctx.put(EvaluationContext.class, evalCtx)).blockFirst();
        };
    }

    private EvaluationContext createEvaluationContext(Map<String, Value> variables) {
        return EvaluationContext.of("test-pdp", "test-config", "test-sub", null, variables, context.getFunctionBroker(),
                context.getAttributeBroker());
    }

    private static void printDecision(String testName, Value traced) {
        if (!DEBUG) {
            return;
        }
        System.err.println("=== " + testName + " ===");
        System.err.println(toPrettyString(traced));
        System.err.println();
    }
}
