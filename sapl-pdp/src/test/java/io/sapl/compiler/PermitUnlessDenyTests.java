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

import io.sapl.api.pdp.Decision;
import lombok.val;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static io.sapl.util.CombiningAlgorithmTestUtil.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class PermitUnlessDenyTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_decisionEvaluated_then_matchesExpected(String description, String policySet, Decision expectedDecision) {
        assertDecision(policySet, expectedDecision);
    }

    private static Stream<Arguments> when_decisionEvaluated_then_matchesExpected() {
        return Stream.of(arguments("No policies match returns PERMIT", """
                set "test" permit-unless-deny
                policy "never matches" permit subject == "non-matching"
                """, Decision.PERMIT),

                arguments("Single permit returns PERMIT", """
                        set "test" permit-unless-deny
                        policy "permit policy" permit
                        """, Decision.PERMIT),

                arguments("Single deny returns DENY", """
                        set "test" permit-unless-deny
                        policy "deny policy" deny
                        """, Decision.DENY),

                arguments("Any deny returns DENY", """
                        set "test" permit-unless-deny
                        policy "permit policy" permit
                        policy "deny policy" deny
                        """, Decision.DENY),

                arguments("Transformation uncertainty returns DENY", """
                        set "test" permit-unless-deny
                        policy "permit with transformation 1" permit transform "resource1"
                        policy "permit with transformation 2" permit transform "resource2"
                        """, Decision.DENY),

                arguments("Only permit returns PERMIT", """
                        set "test" permit-unless-deny
                        policy "permit policy 1" permit
                        policy "permit policy 2" permit
                        """, Decision.PERMIT),

                arguments("Only not applicable returns PERMIT", """
                        set "test" permit-unless-deny
                        policy "not applicable 1" permit subject == "non-matching1"
                        policy "not applicable 2" deny subject == "non-matching2"
                        """, Decision.PERMIT));
    }

    /*
     * ========== Constraint Tests ========== Permit-unless-deny merges constraints
     * from all policies of the winning
     * decision type. Transformation uncertainty returns DENY.
     */

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_constraintsEvaluated_then_winningSideConstraintsIncluded(String description, String policySet,
            Decision expectedDecision, List<String> expectedObligations, List<String> expectedAdvice) {
        val result = evaluatePolicySet(policySet);
        assertDecision(result, expectedDecision);
        assertObligations(result, expectedObligations);
        assertAdvice(result, expectedAdvice);
    }

    private static Stream<Arguments> when_constraintsEvaluated_then_winningSideConstraintsIncluded() {
        return Stream.of(
                // All permit constraints merged when no deny
                arguments("All permit obligations merged", """
                        set "test" permit-unless-deny
                        policy "p1" permit obligation { "type": "p1_obl" } advice { "type": "p1_adv" }
                        policy "p2" permit obligation { "type": "p2_obl" } advice { "type": "p2_adv" }
                        """, Decision.PERMIT, List.of("p1_obl", "p2_obl"), List.of("p1_adv", "p2_adv")),

                // All deny constraints merged when deny wins
                arguments("All deny obligations merged when deny wins", """
                        set "test" permit-unless-deny
                        policy "d1" deny obligation { "type": "d1_obl" } advice { "type": "d1_adv" }
                        policy "d2" deny obligation { "type": "d2_obl" } advice { "type": "d2_adv" }
                        policy "permit" permit obligation { "type": "permit_obl" }
                        """, Decision.DENY, List.of("d1_obl", "d2_obl"), List.of("d1_adv", "d2_adv")),

                // Permit constraints discarded when deny wins
                arguments("Permit constraints discarded when deny wins", """
                        set "test" permit-unless-deny
                        policy "permit" permit obligation { "type": "permit_obl" } advice { "type": "permit_adv" }
                        policy "deny" deny obligation { "type": "deny_obl" }
                        """, Decision.DENY, List.of("deny_obl"), List.of()),

                // Not applicable constraints not included
                arguments("Not applicable constraints not included", """
                        set "test" permit-unless-deny
                        policy "permit" permit obligation { "type": "permit_obl" }
                        policy "na_deny" deny subject == "non-matching" obligation { "type": "na_obl" }
                        """, Decision.PERMIT, List.of("permit_obl"), List.of()),

                // Default permit has no constraints
                arguments("Default permit when no policies match has no constraints", """
                        set "test" permit-unless-deny
                        policy "na1" permit subject == "non-matching1" obligation { "type": "na1_obl" }
                        policy "na2" deny subject == "non-matching2" obligation { "type": "na2_obl" }
                        """, Decision.PERMIT, List.of(), List.of()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_transformationEvaluated_then_correctResourceUsed(String description, String policySet,
            Decision expectedDecision, String expectedResourceField, String expectedResourceValue) {
        val result = evaluatePolicySet(policySet);
        assertDecision(result, expectedDecision);
        if (expectedResourceField != null) {
            assertResourceText(result, expectedResourceField, expectedResourceValue);
        }
    }

    private static Stream<Arguments> when_transformationEvaluated_then_correctResourceUsed() {
        return Stream.of(
                // Single permit transformation - no uncertainty
                arguments("Single permit transformation no uncertainty", """
                        set "test" permit-unless-deny
                        policy "permit" permit transform { "source": "permit" }
                        policy "permit2" permit
                        """, Decision.PERMIT, "source", "permit"),

                // Multiple permit transformations - DENY due to uncertainty
                arguments("Multiple permit transformations cause DENY", """
                        set "test" permit-unless-deny
                        policy "p1" permit transform { "source": "p1" }
                        policy "p2" permit transform { "source": "p2" }
                        """, Decision.DENY, null, null),

                // Single deny transformation
                arguments("Single deny transformation", """
                        set "test" permit-unless-deny
                        policy "deny" deny transform { "source": "deny" }
                        policy "permit" permit
                        """, Decision.DENY, "source", "deny"),

                // Multiple deny transformations - first wins
                arguments("Multiple deny transformations first wins", """
                        set "test" permit-unless-deny
                        policy "d1" deny transform { "source": "d1" }
                        policy "d2" deny transform { "source": "d2" }
                        """, Decision.DENY, "source", "d1"));
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
        return Stream.of(arguments("All permit constraints with transformation", """
                set "test" permit-unless-deny
                policy "p1" permit
                    obligation { "type": "p1_obl" }
                    advice { "type": "p1_adv" }
                    transform { "source": "p1" }
                policy "p2" permit
                    obligation { "type": "p2_obl" }
                    advice { "type": "p2_adv" }
                """, Decision.PERMIT, List.of("p1_obl", "p2_obl"), List.of("p1_adv", "p2_adv"), "source", "p1"),

                arguments("All deny constraints override permit", """
                        set "test" permit-unless-deny
                        policy "deny" deny
                            obligation { "type": "deny_obl" }
                            advice { "type": "deny_adv" }
                            transform { "source": "deny" }
                        policy "permit" permit
                            obligation { "type": "permit_obl" }
                            transform { "source": "permit" }
                        """, Decision.DENY, List.of("deny_obl"), List.of("deny_adv"), "source", "deny"));
    }
}
