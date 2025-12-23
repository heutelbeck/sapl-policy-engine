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
package io.sapl.compiler;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import lombok.val;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static io.sapl.util.CombiningAlgorithmTestUtil.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Comprehensive tests for the first-applicable combining algorithm.
 */
class FirstApplicableTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_decisionEvaluated_then_matchesExpected(String description, String policySet, Decision expectedDecision) {
        assertDecision(policySet, expectedDecision);
    }

    private static Stream<Arguments> when_decisionEvaluated_then_matchesExpected() {
        return Stream.of(arguments("Empty policy set returns NOT_APPLICABLE", """
                set "empty" first-applicable
                policy "never matches" permit where false;
                """, Decision.NOT_APPLICABLE),

                arguments("Single policy permit returns PERMIT", """
                        set "test" first-applicable
                        policy "permit policy" permit
                        """, Decision.PERMIT),

                arguments("Single policy deny returns DENY", """
                        set "test" first-applicable
                        policy "deny policy" deny
                        """, Decision.DENY),

                arguments("Single policy not applicable returns NOT_APPLICABLE", """
                        set "test" first-applicable
                        policy "not applicable policy" permit where false;
                        """, Decision.NOT_APPLICABLE),

                arguments("First permit subsequent policies ignored", """
                        set "test" first-applicable
                        policy "first permit" permit
                        policy "second permit" permit
                        policy "deny" deny
                        """, Decision.PERMIT),

                arguments("First deny subsequent policies ignored", """
                        set "test" first-applicable
                        policy "first deny" deny
                        policy "permit" permit
                        policy "second deny" deny
                        """, Decision.DENY),

                arguments("Skip not applicable return first permit", """
                        set "test" first-applicable
                        policy "not applicable 1" permit where false;
                        policy "not applicable 2" deny where false;
                        policy "first applicable permit" permit
                        policy "subsequent deny" deny
                        """, Decision.PERMIT),

                arguments("Skip not applicable return first deny", """
                        set "test" first-applicable
                        policy "not applicable 1" permit where false;
                        policy "not applicable 2" deny where false;
                        policy "first applicable deny" deny
                        policy "subsequent permit" permit
                        """, Decision.DENY),

                arguments("All not applicable returns NOT_APPLICABLE", """
                        set "test" first-applicable
                        policy "not applicable 1" permit where false;
                        policy "not applicable 2" deny where false;
                        policy "not applicable 3" permit where false;
                        """, Decision.NOT_APPLICABLE),

                arguments("Match expression evaluates to true policy applies", """
                        set "test" first-applicable
                        policy "matches with expression" permit (5 > 3)
                        """, Decision.PERMIT),

                arguments("Match expression evaluates to false policy not applicable", """
                        set "test" first-applicable
                        policy "no match with expression" permit where (2 > 5);
                        policy "fallback" deny
                        """, Decision.DENY),

                arguments("Match expression non-boolean at runtime returns INDETERMINATE", """
                        set "test" first-applicable
                        policy "invalid match" permit ""+subject where true;
                        """, Decision.INDETERMINATE),

                arguments("Target expression matches subject", """
                        set "test" first-applicable
                        policy "subject match" permit subject == "subject"
                        policy "fallback" deny
                        """, Decision.PERMIT),

                arguments("Target expression no match subject", """
                        set "test" first-applicable
                        policy "subject no match" permit subject == "wrong_subject"
                        policy "fallback" deny
                        """, Decision.DENY),

                arguments("Target expression complex subscription expression", """
                        set "test" first-applicable
                        policy "complex match" permit subject == "subject" & action == "action"
                        policy "fallback" deny
                        """, Decision.PERMIT),

                arguments("Where clause multiple conditions all must be true", """
                        set "test" first-applicable
                        policy "multiple where conditions" permit
                        where
                          true;
                          5 > 3;
                          "test" == "test";
                        """, Decision.PERMIT),

                arguments("Where clause one condition false policy not applicable", """
                        set "test" first-applicable
                        policy "one condition false" permit
                        where
                          true;
                          5 < 3;
                          "test" == "test";
                        policy "fallback" deny
                        """, Decision.DENY),

                arguments("Error in where clause returns INDETERMINATE", """
                        set "test" first-applicable
                        policy "error in where" permit where subject.x / 0 == 0;
                        """, Decision.INDETERMINATE),

                arguments("Error in first policy stops evaluation", """
                        set "test" first-applicable
                        policy "error policy" deny where subject / 0 == 0;
                        policy "valid permit" permit
                        """, Decision.INDETERMINATE),

                arguments("Variable definition used in policies", """
                        set "test" first-applicable
                        var threshold = 10;
                        policy "uses variable" permit where threshold == 10;
                        """, Decision.PERMIT),

                arguments("Variable definition multiple variables", """
                        set "test" first-applicable
                        var x = 5;
                        var y = 10;
                        var sum = x + y;
                        policy "uses variables" permit where sum == 15;
                        """, Decision.PERMIT),

                arguments("Complex expression in where", """
                        set "test" first-applicable
                        policy "complex where" permit
                        where
                          (5 + 3) * 2 == 16;
                          [1, 2, 3][1] == 2;
                        """, Decision.PERMIT));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_subscriptionUsed_then_matchesExpected(String description, AuthorizationSubscription subscription,
            String policySet, Decision expectedDecision) {
        val result = evaluatePolicySet(policySet, subscription);
        assertDecision(result, expectedDecision);
    }

    private static Stream<Arguments> when_subscriptionUsed_then_matchesExpected() {
        return Stream
                .of(arguments("Target expression with object field access",
                        new AuthorizationSubscription(ObjectValue.builder().put("name", Value.of("Alice")).build(),
                                Value.of("read"), Value.of("document"), Value.UNDEFINED),
                        """
                                set "test" first-applicable
                                policy "object field match" permit subject.name == "Alice"
                                policy "fallback" deny
                                """, Decision.PERMIT),

                        arguments("Target expression with nested object access",
                                new AuthorizationSubscription(
                                        ObjectValue.builder()
                                                .put("department",
                                                        ObjectValue.builder().put("name", Value.of("Engineering"))
                                                                .build())
                                                .build(),
                                        Value.of("read"), Value.of("document"), Value.UNDEFINED),
                                """
                                        set "test" first-applicable
                                        policy "nested field match" permit subject.department.name == "Engineering"
                                        policy "fallback" deny
                                        """, Decision.PERMIT),

                        arguments("Target expression array in subscription",
                                new AuthorizationSubscription(
                                        ObjectValue.builder()
                                                .put("roles",
                                                        ArrayValue.builder().add(Value.of("admin"))
                                                                .add(Value.of("user")).build())
                                                .build(),
                                        Value.of("read"), Value.of("document"), Value.UNDEFINED),
                                """
                                        set "test" first-applicable
                                        policy "array contains" permit subject.roles[0] == "admin"
                                        policy "fallback" deny
                                        """, Decision.PERMIT));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_obligationIncluded_then_presentInResult(String description, String policySet) {
        val result = evaluatePolicySet(policySet);
        assertDecision(result, Decision.PERMIT);
        val obj         = (ObjectValue) result;
        val obligations = (ArrayValue) obj.get("obligations");
        assertThat(obligations).isNotNull().hasSize(1).first().isEqualTo(Value.of("log_access"));
    }

    private static Stream<Arguments> when_obligationIncluded_then_presentInResult() {
        return Stream.of(arguments("Obligation included in result", """
                set "test" first-applicable
                policy "with obligation" permit obligation "log_access"
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_transformationApplied_then_resourceModified(String description, String policySet) {
        val result = evaluatePolicySet(policySet);
        assertDecision(result, Decision.PERMIT);
        val obj      = (ObjectValue) result;
        val resource = obj.get("resource");
        assertThat(resource).isEqualTo(Value.of("transformed_value"));
    }

    private static Stream<Arguments> when_transformationApplied_then_resourceModified() {
        return Stream.of(arguments("Transformation applied to resource", """
                set "test" first-applicable
                policy "with transformation" permit transform "transformed_value"
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_transformationExpressionUsed_then_evaluatedCorrectly(String description, String policySet) {
        val result   = evaluatePolicySet(policySet);
        val obj      = (ObjectValue) result;
        val resource = (ObjectValue) obj.get("resource");
        assertThat(resource).isNotNull().containsEntry("value", Value.of(42)).containsEntry("squared", Value.of(1764));
    }

    private static Stream<Arguments> when_transformationExpressionUsed_then_evaluatedCorrectly() {
        return Stream.of(arguments("Transformation expression evaluated", """
                set "test" first-applicable
                policy "transformation with expression" permit transform { "value": 42, "squared": 42 * 42 }
                """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_largePolicySetUsed_then_evaluatedCorrectly(String description, String policySet,
            Decision expectedDecision) {
        assertDecision(policySet, expectedDecision);
    }

    private static Stream<Arguments> when_largePolicySetUsed_then_evaluatedCorrectly() {
        val policies1 = new StringBuilder();
        for (int i = 1; i <= 100; i++) {
            policies1.append(String.format("""
                    policy "policy %d" permit where %d == 1;
                    """, i, i));
        }
        policies1.append("policy \"final permit\" permit");
        val source1 = "set \"test\" first-applicable\n" + policies1;

        val policies2 = new StringBuilder();
        policies2.append("policy \"first matches\" permit\n");
        for (int i = 2; i <= 100; i++) {
            policies2.append(String.format("policy \"policy %d\" deny%n", i));
        }
        val source2 = "set \"test\" first-applicable\n" + policies2;

        return Stream.of(arguments("Large number of policies first matches", source1, Decision.PERMIT),
                arguments("Very first policy matches in large set", source2, Decision.PERMIT));
    }

    /*
     * ========== Constraint Tests ========== First-applicable should only include
     * obligations/advice/resource from the
     * FIRST applicable policy. Subsequent policies are not evaluated.
     */

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_constraintsEvaluated_then_onlyFirstApplicableConstraintsIncluded(String description, String policySet,
            Decision expectedDecision, List<String> expectedObligations, List<String> expectedAdvice) {
        val result = evaluatePolicySet(policySet);
        assertDecision(result, expectedDecision);
        if (expectedObligations != null) {
            assertObligations(result, expectedObligations);
        }
        if (expectedAdvice != null) {
            assertAdvice(result, expectedAdvice);
        }
    }

    private static Stream<Arguments> when_constraintsEvaluated_then_onlyFirstApplicableConstraintsIncluded() {
        return Stream.of(
                // Obligations from first applicable only
                arguments("First permit obligations only from first policy", """
                        set "test" first-applicable
                        policy "first" permit obligation { "type": "first_obl" }
                        policy "second" permit obligation { "type": "second_obl" }
                        policy "third" deny obligation { "type": "third_obl" }
                        """, Decision.PERMIT, List.of("first_obl"), List.of()),

                arguments("First deny obligations only from first policy", """
                        set "test" first-applicable
                        policy "first" deny obligation { "type": "first_obl" }
                        policy "second" permit obligation { "type": "second_obl" }
                        """, Decision.DENY, List.of("first_obl"), List.of()),

                arguments("Skip not applicable takes obligations from first applicable", """
                        set "test" first-applicable
                        policy "not applicable" permit where false; obligation { "type": "na_obl" }
                        policy "first applicable" permit obligation { "type": "applicable_obl" }
                        policy "second applicable" permit obligation { "type": "second_obl" }
                        """, Decision.PERMIT, List.of("applicable_obl"), List.of()),

                // Advice from first applicable only
                arguments("First permit advice only from first policy", """
                        set "test" first-applicable
                        policy "first" permit advice { "type": "first_adv" }
                        policy "second" permit advice { "type": "second_adv" }
                        """, Decision.PERMIT, List.of(), List.of("first_adv")),

                arguments("First deny advice only from first policy", """
                        set "test" first-applicable
                        policy "first" deny advice { "type": "first_adv" }
                        policy "second" permit advice { "type": "second_adv" }
                        """, Decision.DENY, List.of(), List.of("first_adv")),

                // Combined obligations and advice
                arguments("First applicable has both obligations and advice", """
                        set "test" first-applicable
                        policy "first" permit obligation { "type": "obl1" } advice { "type": "adv1" }
                        policy "second" permit obligation { "type": "obl2" } advice { "type": "adv2" }
                        """, Decision.PERMIT, List.of("obl1"), List.of("adv1")),

                // Multiple obligations/advice from same policy
                arguments("Multiple obligations from first applicable", """
                        set "test" first-applicable
                        policy "first" permit
                            obligation { "type": "obl1" }
                            obligation { "type": "obl2" }
                            advice { "type": "adv1" }
                        policy "second" permit obligation { "type": "obl3" }
                        """, Decision.PERMIT, List.of("obl1", "obl2"), List.of("adv1")),

                // Empty constraints
                arguments("No constraints when first applicable has none", """
                        set "test" first-applicable
                        policy "first" permit
                        policy "second" permit obligation { "type": "obl" } advice { "type": "adv" }
                        """, Decision.PERMIT, List.of(), List.of()),

                // Not applicable result has no constraints
                arguments("Not applicable has no constraints", """
                        set "test" first-applicable
                        policy "na1" permit where false; obligation { "type": "obl1" }
                        policy "na2" deny where false; obligation { "type": "obl2" }
                        """, Decision.NOT_APPLICABLE, List.of(), List.of()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_resourceTransformationEvaluated_then_onlyFirstApplicableResourceUsed(String description, String policySet,
            Decision expectedDecision, String expectedResourceField, String expectedResourceValue) {
        val result = evaluatePolicySet(policySet);
        assertDecision(result, expectedDecision);
        if (expectedResourceField != null) {
            assertResourceText(result, expectedResourceField, expectedResourceValue);
        } else {
            // Verify resource is undefined
            val obj      = (ObjectValue) result;
            val resource = obj.get("resource");
            assertThat(resource).satisfiesAnyOf(r -> assertThat(r).isNull(),
                    r -> assertThat(r).isInstanceOf(UndefinedValue.class));
        }
    }

    private static Stream<Arguments> when_resourceTransformationEvaluated_then_onlyFirstApplicableResourceUsed() {
        return Stream.of(
                // Resource from first applicable only
                arguments("First applicable resource used", """
                        set "test" first-applicable
                        policy "first" permit transform { "source": "first" }
                        policy "second" permit transform { "source": "second" }
                        """, Decision.PERMIT, "source", "first"),

                arguments("Skip not applicable takes resource from first applicable", """
                        set "test" first-applicable
                        policy "na" permit where false; transform { "source": "na" }
                        policy "first" permit transform { "source": "first" }
                        policy "second" permit transform { "source": "second" }
                        """, Decision.PERMIT, "source", "first"),

                arguments("Deny resource from first applicable", """
                        set "test" first-applicable
                        policy "first" deny transform { "source": "deny_first" }
                        policy "second" permit transform { "source": "permit_second" }
                        """, Decision.DENY, "source", "deny_first"),

                // No transformation uncertainty in first-applicable (only one policy evaluated)
                arguments("No transformation uncertainty single policy applies", """
                        set "test" first-applicable
                        policy "first" permit transform { "source": "first" }
                        policy "second" permit transform { "source": "second" }
                        policy "third" permit transform { "source": "third" }
                        """, Decision.PERMIT, "source", "first"),

                // No resource when first applicable has no transform
                arguments("No resource when first applicable has no transform", """
                        set "test" first-applicable
                        policy "first" permit
                        policy "second" permit transform { "source": "second" }
                        """, Decision.PERMIT, null, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_allConstraintsCombined_then_correctResult(String description, String policySet, Decision expectedDecision,
            List<String> expectedObligations, List<String> expectedAdvice, String expectedResourceField,
            String expectedResourceValue) {
        val result = evaluatePolicySet(policySet);
        assertDecision(result, expectedDecision);
        assertObligations(result, expectedObligations);
        assertAdvice(result, expectedAdvice);
        if (expectedResourceField != null) {
            assertResourceText(result, expectedResourceField, expectedResourceValue);
        }
    }

    private static Stream<Arguments> when_allConstraintsCombined_then_correctResult() {
        return Stream.of(arguments("All constraints from first applicable permit", """
                set "test" first-applicable
                policy "first" permit
                    obligation { "type": "obl1" }
                    obligation { "type": "obl2" }
                    advice { "type": "adv1" }
                    transform { "source": "first" }
                policy "second" permit
                    obligation { "type": "obl3" }
                    advice { "type": "adv2" }
                    transform { "source": "second" }
                """, Decision.PERMIT, List.of("obl1", "obl2"), List.of("adv1"), "source", "first"),

                arguments("All constraints from first applicable deny", """
                        set "test" first-applicable
                        policy "first" deny
                            obligation { "type": "deny_obl" }
                            advice { "type": "deny_adv" }
                            transform { "source": "deny" }
                        policy "second" permit
                            obligation { "type": "permit_obl" }
                        """, Decision.DENY, List.of("deny_obl"), List.of("deny_adv"), "source", "deny"),

                arguments("Constraints from second when first not applicable", """
                        set "test" first-applicable
                        policy "na" permit where false;
                            obligation { "type": "na_obl" }
                            advice { "type": "na_adv" }
                            transform { "source": "na" }
                        policy "second" permit
                            obligation { "type": "second_obl" }
                            advice { "type": "second_adv" }
                            transform { "source": "second" }
                        """, Decision.PERMIT, List.of("second_obl"), List.of("second_adv"), "source", "second"));
    }
}
