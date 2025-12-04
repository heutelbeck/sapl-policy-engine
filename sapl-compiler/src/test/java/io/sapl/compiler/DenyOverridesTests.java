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

import io.sapl.api.pdp.Decision;
import lombok.val;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static io.sapl.util.CombiningAlgorithmTestUtil.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class DenyOverridesTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_decisionEvaluated_then_matchesExpected(String description, String policySet, Decision expectedDecision) {
        assertDecision(policySet, expectedDecision);
    }

    private static Stream<Arguments> when_decisionEvaluated_then_matchesExpected() {
        return Stream.of(
                // Basic scenarios
                arguments("No policies match returns NOT_APPLICABLE", """
                        set "test" deny-overrides
                        policy "never matches" permit subject == "non-matching"
                        """, Decision.NOT_APPLICABLE),

                arguments("Single permit returns PERMIT", "set \"test\" deny-overrides policy \"p\" permit",
                        Decision.PERMIT),

                arguments("Single deny returns DENY", "set \"test\" deny-overrides policy \"p\" deny", Decision.DENY),

                // Deny-overrides semantics
                arguments("Any deny overrides permit", """
                        set "test" deny-overrides
                        policy "p1" permit
                        policy "p2" deny
                        """, Decision.DENY),

                arguments("Deny overrides indeterminate", """
                        set "test" deny-overrides
                        policy "indeterminate" permit where (10).<test.counter> == 100;
                        policy "deny" deny
                        """, Decision.DENY),

                arguments("Indeterminate without deny returns INDETERMINATE", """
                        set "test" deny-overrides
                        policy "p1" permit transform "resource1"
                        policy "p2" permit transform "resource2"
                        policy "p3" permit
                        """, Decision.INDETERMINATE),

                arguments("Transformation uncertainty without deny returns INDETERMINATE", """
                        set "test" deny-overrides
                        policy "p1" permit transform "resource1"
                        policy "p2" permit transform "resource2"
                        """, Decision.INDETERMINATE),

                arguments("Transformation uncertainty with deny returns DENY", """
                        set "test" deny-overrides
                        policy "p1" permit transform "resource1"
                        policy "p2" permit transform "resource2"
                        policy "deny" deny
                        """, Decision.DENY),

                arguments("Permit with indeterminate without deny returns PERMIT", """
                        set "test" deny-overrides
                        policy "permit" permit
                        policy "indeterminate" permit subject == "non-matching"
                        """, Decision.PERMIT),

                arguments("Only not applicable returns NOT_APPLICABLE", """
                        set "test" deny-overrides
                        policy "na1" permit subject == "non-matching1"
                        policy "na2" permit subject == "non-matching2"
                        """, Decision.NOT_APPLICABLE),

                // Additional tests from legacy
                arguments("Indeterminate condition returns INDETERMINATE", """
                        set "test" deny-overrides
                        policy "p" permit where subject / 0 == 0;
                        """, Decision.INDETERMINATE),

                arguments("Deny with indeterminate returns DENY", """
                        set "test" deny-overrides
                        policy "deny" deny
                        policy "indet" deny where subject / 0 == 0;
                        """, Decision.DENY),

                arguments("Permit indeterminate not applicable without deny returns INDETERMINATE", """
                        set "test" deny-overrides
                        policy "permit" permit
                        policy "indet" deny where subject / 0 == 0;
                        policy "na" deny subject == "non-matching"
                        """, Decision.INDETERMINATE),

                arguments("Multiple permit no transformation returns PERMIT", """
                        set "test" deny-overrides
                        policy "p1" permit
                        policy "p2" permit
                        """, Decision.PERMIT));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_resourceTransformed_then_matchesExpected(String description, String policySet, Decision expectedDecision,
            String fieldName, Object expectedValue) {
        val result = evaluatePolicySet(policySet);
        assertDecision(result, expectedDecision);
        if (expectedValue instanceof Boolean) {
            assertResourceBoolean(result, fieldName, (Boolean) expectedValue);
        } else if (expectedValue instanceof String) {
            assertResourceText(result, fieldName, (String) expectedValue);
        }
    }

    private static Stream<Arguments> when_resourceTransformed_then_matchesExpected() {
        return Stream.of(arguments("Single permit transformation returns PERMIT with resource", """
                set "test" deny-overrides
                policy "p" permit transform { "value": true }
                """, Decision.PERMIT, "value", true),

                arguments("Transform uncertainty but deny wins uses first deny resource", """
                        set "test" deny-overrides
                        policy "deny" deny transform { "type": "deny" }
                        policy "permit" permit transform { "type": "permit" }
                        """, Decision.DENY, "type", "deny"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_obligationsAndAdviceEvaluated_then_matchesExpected(String description, String policySet,
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

    private static Stream<Arguments> when_obligationsAndAdviceEvaluated_then_matchesExpected() {
        return Stream.of(arguments("Collect obligations from deny", """
                set "test" deny-overrides
                policy "d1" deny obligation { "type": "obligation1" } advice { "type": "advice1" }
                policy "d2" deny obligation { "type": "obligation2" } advice { "type": "advice2" }
                policy "permit" permit obligation { "type": "obligation3" } advice { "type": "advice3" }
                policy "na" deny subject == "non-matching" obligation { "type": "obligation4" }
                """, Decision.DENY, List.of("obligation1", "obligation2"), null),

                arguments("Collect advice from deny", """
                        set "test" deny-overrides
                        policy "d1" deny obligation { "type": "obligation1" } advice { "type": "advice1" }
                        policy "d2" deny obligation { "type": "obligation2" } advice { "type": "advice2" }
                        policy "permit" permit obligation { "type": "obligation3" } advice { "type": "advice3" }
                        """, Decision.DENY, null, List.of("advice1", "advice2")),

                arguments("Collect obligations from permit", """
                        set "test" deny-overrides
                        policy "p1" permit obligation { "type": "obligation1" } advice { "type": "advice1" }
                        policy "p2" permit obligation { "type": "obligation2" } advice { "type": "advice2" }
                        policy "na1" deny subject == "non-matching" obligation { "type": "obligation3" }
                        policy "na2" deny where false; obligation { "type": "obligation4" }
                        """, Decision.PERMIT, List.of("obligation1", "obligation2"), null),

                arguments("Collect advice from permit",
                        """
                                set "test" deny-overrides
                                policy "p1" permit obligation { "type": "obligation1" } advice { "type": "advice1" }
                                policy "p2" permit obligation { "type": "obligation2" } advice { "type": "advice2" }
                                policy "na" deny subject == "non-matching" obligation { "type": "obligation3" } advice { "type": "advice3" }
                                """,
                        Decision.PERMIT, null, List.of("advice1", "advice2")));
    }
}
