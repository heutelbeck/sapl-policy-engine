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

class PermitOverridesTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_decisionEvaluated_then_matchesExpected(String description, String policySet, Decision expectedDecision) {
        assertDecision(policySet, expectedDecision);
    }

    private static Stream<Arguments> when_decisionEvaluated_then_matchesExpected() {
        return Stream.of(arguments("No policies match returns NOT_APPLICABLE", """
                set "test" permit-overrides
                policy "never matches" permit subject == "non-matching"
                """, Decision.NOT_APPLICABLE),

                arguments("Single permit returns PERMIT", """
                        set "test" permit-overrides
                        policy "permit policy" permit
                        """, Decision.PERMIT),

                arguments("Single deny returns DENY", """
                        set "test" permit-overrides
                        policy "deny policy" deny
                        """, Decision.DENY),

                arguments("Permit overrides deny", """
                        set "test" permit-overrides
                        policy "deny policy" deny
                        policy "permit policy" permit
                        """, Decision.PERMIT),

                arguments("Transformation uncertainty returns INDETERMINATE", """
                        set "test" permit-overrides
                        policy "permit with transformation 1" permit transform "resource1"
                        policy "permit with transformation 2" permit transform "resource2"
                        """, Decision.INDETERMINATE),

                arguments("Indeterminate without permit returns INDETERMINATE", """
                        set "test" permit-overrides
                        policy "permit policy 1" permit transform "resource1"
                        policy "permit policy 2" permit transform "resource2"
                        policy "deny policy" deny
                        """, Decision.INDETERMINATE),

                arguments("Only deny returns DENY", """
                        set "test" permit-overrides
                        policy "deny policy 1" deny
                        policy "deny policy 2" deny
                        """, Decision.DENY),

                arguments("Only not applicable returns NOT_APPLICABLE", """
                        set "test" permit-overrides
                        policy "not applicable 1" permit subject == "non-matching1"
                        policy "not applicable 2" deny subject == "non-matching2"
                        """, Decision.NOT_APPLICABLE),

                arguments("Permit with indeterminate returns PERMIT", """
                        set "test" permit-overrides
                        policy "permit policy" permit
                        policy "indeterminate policy" permit where "a" > 5;
                        """, Decision.PERMIT),

                arguments("Deny indeterminate not applicable returns INDETERMINATE", """
                        set "test" permit-overrides
                        policy "deny policy" deny
                        policy "indeterminate policy" permit where "a" < 5;
                        policy "not applicable policy" permit subject == "non-matching"
                        """, Decision.INDETERMINATE),

                arguments("Deny not applicable returns DENY", """
                        set "test" permit-overrides
                        policy "deny policy" deny
                        policy "not applicable" permit subject == "non-matching"
                        """, Decision.DENY));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_resourceTransformed_then_matchesExpected(String description, String policySet,
            Decision expectedDecision) {
        val result = evaluatePolicySet(policySet);
        assertDecision(result, expectedDecision);
        assertResourceBoolean(result, "value", true);
    }

    private static Stream<Arguments> when_resourceTransformed_then_matchesExpected() {
        return Stream.of(arguments("Single permit transformation resource verifies resource", """
                set "test" permit-overrides
                policy "testp" permit transform { "value": true }
                """, Decision.PERMIT));
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
        return Stream.of(
                arguments("Collect obligations from deny",
                        """
                                set "test" permit-overrides
                                policy "deny 1" deny obligation { "type": "obligation1" } advice { "type": "advice1" }
                                policy "deny 2" deny obligation { "type": "obligation2" } advice { "type": "advice2" }
                                policy "not applicable permit" permit subject == "non-matching" obligation { "type": "obligation3" } advice { "type": "advice3" }
                                policy "not applicable deny" deny subject == "non-matching" obligation { "type": "obligation4" } advice { "type": "advice4" }
                                """,
                        Decision.DENY, List.of("obligation1", "obligation2"), null),

                arguments("Collect advice from deny",
                        """
                                set "test" permit-overrides
                                policy "deny 1" deny obligation { "type": "obligation1" } advice { "type": "advice1" }
                                policy "deny 2" deny obligation { "type": "obligation2" } advice { "type": "advice2" }
                                policy "not applicable permit" permit subject == "non-matching" obligation { "type": "obligation3" } advice { "type": "advice3" }
                                """,
                        Decision.DENY, null, List.of("advice1", "advice2")),

                arguments("Collect obligations from permit",
                        """
                                set "test" permit-overrides
                                policy "permit 1" permit obligation { "type": "obligation1" } advice { "type": "advice1" }
                                policy "permit 2" permit obligation { "type": "obligation2" } advice { "type": "advice2" }
                                policy "not applicable deny" deny subject == "non-matching" obligation { "type": "obligation3" } advice { "type": "advice3" }
                                policy "not applicable permit" deny where false; obligation { "type": "obligation4" } advice { "type": "advice4" }
                                """,
                        Decision.PERMIT, List.of("obligation1", "obligation2"), null),

                arguments("Collect advice from permit",
                        """
                                set "test" permit-overrides
                                policy "permit 1" permit obligation { "type": "obligation1" } advice { "type": "advice1" }
                                policy "permit 2" permit obligation { "type": "obligation2" } advice { "type": "advice2" }
                                policy "not applicable" deny subject == "non-matching" obligation { "type": "obligation3" } advice { "type": "advice3" }
                                """,
                        Decision.PERMIT, null, List.of("advice1", "advice2")));
    }
}
