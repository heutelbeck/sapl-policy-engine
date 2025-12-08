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

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import lombok.val;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.sapl.util.CombiningAlgorithmTestUtil.assertDecision;
import static io.sapl.util.CombiningAlgorithmTestUtil.evaluatePolicySet;
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
}
