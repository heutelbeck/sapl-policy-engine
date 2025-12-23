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

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.UndefinedValue;
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

class OnlyOneApplicableTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_decisionEvaluated_then_matchesExpected(String description, String policySet, Decision expectedDecision) {
        assertDecision(policySet, expectedDecision);
    }

    private static Stream<Arguments> when_decisionEvaluated_then_matchesExpected() {
        return Stream.of(arguments("No policies match returns NOT_APPLICABLE", """
                set "test" only-one-applicable
                policy "never matches 1" permit subject == "non-matching1"
                policy "never matches 2" deny subject == "non-matching2"
                """, Decision.NOT_APPLICABLE),

                arguments("Single permit returns PERMIT", """
                        set "test" only-one-applicable
                        policy "permit policy" permit
                        policy "never matches" deny subject == "non-matching"
                        """, Decision.PERMIT),

                arguments("Single deny returns DENY", """
                        set "test" only-one-applicable
                        policy "deny policy" deny
                        policy "never matches" permit subject == "non-matching"
                        """, Decision.DENY),

                arguments("Multiple applicable returns INDETERMINATE", """
                        set "test" only-one-applicable
                        policy "permit policy" permit
                        policy "deny policy" deny
                        """, Decision.INDETERMINATE),

                arguments("Multiple permits returns INDETERMINATE", """
                        set "test" only-one-applicable
                        policy "permit policy 1" permit
                        policy "permit policy 2" permit
                        """, Decision.INDETERMINATE),

                arguments("Multiple denies returns INDETERMINATE", """
                        set "test" only-one-applicable
                        policy "deny policy 1" deny
                        policy "deny policy 2" deny
                        """, Decision.INDETERMINATE),

                arguments("Single applicable permit with not applicable returns PERMIT", """
                        set "test" only-one-applicable
                        policy "permit policy" permit
                        policy "not applicable 1" deny subject == "non-matching1"
                        policy "not applicable 2" permit subject == "non-matching2"
                        """, Decision.PERMIT));
    }

    /*
     * ========== Constraint Tests ========== Only-one-applicable should only
     * include obligations/advice/resource from
     * the SINGLE applicable policy. If multiple policies are applicable, result is
     * INDETERMINATE with no constraints.
     */

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_constraintsEvaluated_then_onlyOneApplicableConstraintsIncluded(String description, String policySet,
            Decision expectedDecision, List<String> expectedObligations, List<String> expectedAdvice) {
        val result = evaluatePolicySet(policySet);
        assertDecision(result, expectedDecision);
        assertObligations(result, expectedObligations);
        assertAdvice(result, expectedAdvice);
    }

    private static Stream<Arguments> when_constraintsEvaluated_then_onlyOneApplicableConstraintsIncluded() {
        return Stream.of(
                // Obligations from single applicable only
                arguments("Single permit obligations included", """
                        set "test" only-one-applicable
                        policy "permit" permit obligation { "type": "permit_obl" }
                        policy "na" deny subject == "non-matching"
                        """, Decision.PERMIT, List.of("permit_obl"), List.of()),

                arguments("Single deny obligations included", """
                        set "test" only-one-applicable
                        policy "deny" deny obligation { "type": "deny_obl" }
                        policy "na" permit subject == "non-matching"
                        """, Decision.DENY, List.of("deny_obl"), List.of()),

                // Multiple applicable -> INDETERMINATE with no constraints
                arguments("Multiple applicable no constraints in INDETERMINATE", """
                        set "test" only-one-applicable
                        policy "permit" permit obligation { "type": "permit_obl" }
                        policy "deny" deny obligation { "type": "deny_obl" }
                        """, Decision.INDETERMINATE, List.of(), List.of()),

                // Advice from single applicable only
                arguments("Single permit advice included", """
                        set "test" only-one-applicable
                        policy "permit" permit advice { "type": "permit_adv" }
                        policy "na" deny subject == "non-matching"
                        """, Decision.PERMIT, List.of(), List.of("permit_adv")),

                // Combined obligations and advice
                arguments("Single applicable has both obligations and advice", """
                        set "test" only-one-applicable
                        policy "permit" permit
                            obligation { "type": "obl1" }
                            advice { "type": "adv1" }
                        policy "na" deny subject == "non-matching"
                        """, Decision.PERMIT, List.of("obl1"), List.of("adv1")),

                // Multiple obligations/advice from same policy
                arguments("Multiple obligations from single applicable", """
                        set "test" only-one-applicable
                        policy "permit" permit
                            obligation { "type": "obl1" }
                            obligation { "type": "obl2" }
                            advice { "type": "adv1" }
                            advice { "type": "adv2" }
                        policy "na" deny subject == "non-matching"
                        """, Decision.PERMIT, List.of("obl1", "obl2"), List.of("adv1", "adv2")),

                // Not applicable result has no constraints
                arguments("Not applicable has no constraints", """
                        set "test" only-one-applicable
                        policy "na1" permit subject == "non-matching1" obligation { "type": "obl1" }
                        policy "na2" deny subject == "non-matching2" obligation { "type": "obl2" }
                        """, Decision.NOT_APPLICABLE, List.of(), List.of()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void when_resourceTransformationEvaluated_then_onlyOneApplicableResourceUsed(String description, String policySet,
            Decision expectedDecision, String expectedResourceField, String expectedResourceValue) {
        val result = evaluatePolicySet(policySet);
        assertDecision(result, expectedDecision);
        if (expectedResourceField != null) {
            assertResourceText(result, expectedResourceField, expectedResourceValue);
        } else {
            val obj      = (ObjectValue) result;
            val resource = obj.get("resource");
            assertThat(resource).satisfiesAnyOf(r -> assertThat(r).isNull(),
                    r -> assertThat(r).isInstanceOf(UndefinedValue.class));
        }
    }

    private static Stream<Arguments> when_resourceTransformationEvaluated_then_onlyOneApplicableResourceUsed() {
        return Stream.of(
                // Resource from single applicable only
                arguments("Single applicable resource used", """
                        set "test" only-one-applicable
                        policy "permit" permit transform { "source": "permit" }
                        policy "na" deny subject == "non-matching"
                        """, Decision.PERMIT, "source", "permit"),

                // Multiple applicable -> INDETERMINATE, no resource
                arguments("Multiple applicable no resource in INDETERMINATE", """
                        set "test" only-one-applicable
                        policy "permit" permit transform { "source": "permit" }
                        policy "deny" deny transform { "source": "deny" }
                        """, Decision.INDETERMINATE, null, null),

                // No resource when single applicable has no transform
                arguments("No resource when single applicable has no transform", """
                        set "test" only-one-applicable
                        policy "permit" permit
                        policy "na" deny subject == "non-matching" transform { "source": "na" }
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
        return Stream.of(arguments("All constraints from single applicable permit", """
                set "test" only-one-applicable
                policy "permit" permit
                    obligation { "type": "obl1" }
                    obligation { "type": "obl2" }
                    advice { "type": "adv1" }
                    transform { "source": "permit" }
                policy "na" deny subject == "non-matching"
                    obligation { "type": "na_obl" }
                """, Decision.PERMIT, List.of("obl1", "obl2"), List.of("adv1"), "source", "permit"),

                arguments("All constraints from single applicable deny", """
                        set "test" only-one-applicable
                        policy "deny" deny
                            obligation { "type": "deny_obl" }
                            advice { "type": "deny_adv" }
                            transform { "source": "deny" }
                        policy "na" permit subject == "non-matching"
                        """, Decision.DENY, List.of("deny_obl"), List.of("deny_adv"), "source", "deny"),

                arguments("Multiple applicable discards all constraints", """
                        set "test" only-one-applicable
                        policy "permit" permit
                            obligation { "type": "permit_obl" }
                            advice { "type": "permit_adv" }
                            transform { "source": "permit" }
                        policy "deny" deny
                            obligation { "type": "deny_obl" }
                            advice { "type": "deny_adv" }
                            transform { "source": "deny" }
                        """, Decision.INDETERMINATE, List.of(), List.of(), null, null));
    }
}
