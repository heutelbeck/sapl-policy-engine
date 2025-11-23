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

import io.sapl.api.model.ObjectValue;
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
import static org.junit.jupiter.params.provider.Arguments.arguments;

class DenyUnlessPermitTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void denyUnlessPermitDecisionTests(String description, String policySet, Decision expectedDecision) {
        assertDecision(policySet, expectedDecision);
    }

    private static Stream<Arguments> denyUnlessPermitDecisionTests() {
        return Stream.of(arguments("Single policy permit returns PERMIT", """
                set "test" deny-unless-permit
                policy "permit policy" permit
                """, Decision.PERMIT),

                arguments("Single policy deny returns DENY", """
                        set "test" deny-unless-permit
                        policy "deny policy" deny
                        """, Decision.DENY),

                arguments("Any permit without uncertainty returns PERMIT", """
                        set "test" deny-unless-permit
                        policy "deny policy" deny
                        policy "permit policy" permit
                        policy "another deny" deny
                        """, Decision.PERMIT),

                arguments("Transformation uncertainty returns DENY", """
                        set "test" deny-unless-permit
                        policy "permit with transformation 1" permit transform "resource1"
                        policy "permit with transformation 2" permit transform "resource2"
                        """, Decision.DENY));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void denyUnlessPermitDecisionTestsWithCustomSubscription(String description, String policySet,
            Decision expectedDecision) {
        val subscription = new AuthorizationSubscription(Value.of("actual_subject"), Value.of("action"),
                Value.of("resource"), Value.UNDEFINED);
        val result       = evaluatePolicySet(policySet, subscription);
        assertDecision(result, expectedDecision);
    }

    private static Stream<Arguments> denyUnlessPermitDecisionTestsWithCustomSubscription() {
        return Stream.of(arguments("No policies match returns DENY", """
                set "test" deny-unless-permit
                policy "never matches" permit subject == "non-matching"
                """, Decision.DENY),

                arguments("Single policy not applicable returns DENY", """
                        set "test" deny-unless-permit
                        policy "not applicable" permit subject == "non-matching"
                        """, Decision.DENY),

                arguments("All not applicable returns DENY", """
                        set "test" deny-unless-permit
                        policy "not applicable 1" permit subject == "non-matching1"
                        policy "not applicable 2" permit subject == "non-matching2"
                        """, Decision.DENY),

                arguments("Mix of deny and not applicable returns DENY", """
                        set "test" deny-unless-permit
                        policy "deny policy" deny
                        policy "not applicable" permit subject == "non-matching"
                        """, Decision.DENY));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void denyUnlessPermitObligationsAdviceTests(String description, String policySet, Decision expectedDecision,
            List<String> expectedObligations, List<String> expectedAdvice) {
        val result = evaluatePolicySet(policySet);
        assertDecision(result, expectedDecision);
        if (expectedObligations != null) {
            assertObligations(result, expectedObligations);
        }
        if (expectedAdvice != null) {
            assertAdvice(result, expectedAdvice);
        }
    }

    private static Stream<Arguments> denyUnlessPermitObligationsAdviceTests() {
        return Stream.of(arguments("Permit decision includes permit obligations", """
                set "test" deny-unless-permit
                policy "permit with obligation" permit obligation {"type": "log"}
                policy "deny with obligation" deny obligation {"type": "deny_log"}
                """, Decision.PERMIT, List.of("log"), null),

                arguments("Multiple policies collect all permit obligations", """
                        set "test" deny-unless-permit
                        policy "permit 1" permit obligation {"type": "log1"}
                        policy "permit 2" permit obligation {"type": "log2"}
                        """, Decision.PERMIT, List.of("log1", "log2"), null),

                arguments("Permit decision includes permit advice", """
                        set "test" deny-unless-permit
                        policy "permit with advice" permit advice {"type": "cache"}
                        policy "deny with advice" deny advice {"type": "deny_advice"}
                        """, Decision.PERMIT, null, List.of("cache")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void denyUnlessPermitObligationsTestsWithCustomSubscription(String description, String policySet,
            Decision expectedDecision, List<String> expectedObligations) {
        val subscription = new AuthorizationSubscription(Value.of("actual_subject"), Value.of("action"),
                Value.of("resource"), Value.UNDEFINED);
        val result       = evaluatePolicySet(policySet, subscription);
        assertDecision(result, expectedDecision);
        assertObligations(result, expectedObligations);
    }

    private static Stream<Arguments> denyUnlessPermitObligationsTestsWithCustomSubscription() {
        return Stream.of(arguments("Deny decision includes deny obligations", """
                set "test" deny-unless-permit
                policy "permit with obligation" permit subject == "non-matching" obligation {"type": "permit_log"}
                policy "deny with obligation" deny obligation {"type": "deny_log"}
                """, Decision.DENY, List.of("deny_log")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void denyUnlessPermitResourceTests(String description, String policySet, Decision expectedDecision,
            Value expectedResource) {
        assertDecisionWithResource(policySet, expectedDecision, expectedResource);
    }

    private static Stream<Arguments> denyUnlessPermitResourceTests() {
        return Stream.of(arguments("Single permit with transformation returns PERMIT with resource", """
                set "test" deny-unless-permit
                policy "permit with transformation" permit transform "modified_resource"
                """, Decision.PERMIT, Value.of("modified_resource")),

                arguments("Permit without transformation and permit with transformation uses transformation", """
                        set "test" deny-unless-permit
                        policy "permit without transformation" permit
                        policy "permit with transformation" permit transform "modified_resource"
                        """, Decision.PERMIT, Value.of("modified_resource")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void denyUnlessPermitSubscriptionFieldTests(String description, AuthorizationSubscription subscription,
            String policySet, Decision expectedDecision) {
        val result = evaluatePolicySet(policySet, subscription);
        assertDecision(result, expectedDecision);
    }

    private static Stream<Arguments> denyUnlessPermitSubscriptionFieldTests() {
        return Stream.of(
                arguments("Target expression with subscription fields",
                        new AuthorizationSubscription(Value.of("Alice"), Value.of("read"), Value.of("document"),
                                Value.UNDEFINED),
                        """
                                set "test" deny-unless-permit
                                policy "subject match" permit subject == "Alice"
                                policy "fallback" deny
                                """, Decision.PERMIT),

                arguments("Target expression with object field access",
                        new AuthorizationSubscription(ObjectValue.builder().put("name", Value.of("Alice")).build(),
                                Value.of("read"), Value.of("document"), Value.UNDEFINED),
                        """
                                set "test" deny-unless-permit
                                policy "object field match" permit subject.name == "Alice"
                                policy "fallback" deny
                                """, Decision.PERMIT));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void denyUnlessPermitSubscriptionResourceTests(String description, AuthorizationSubscription subscription,
            String policySet, Decision expectedDecision, Value expectedResource) {
        val result = evaluatePolicySet(policySet, subscription);
        assertDecision(result, expectedDecision);
        assertResource(result, expectedResource);
    }

    private static Stream<Arguments> denyUnlessPermitSubscriptionResourceTests() {
        return Stream.of(arguments("Decision expression with subscription reference",
                new AuthorizationSubscription(ObjectValue.builder().put("name", Value.of("Alice")).build(),
                        Value.of("read"), Value.of("document"), Value.UNDEFINED),
                """
                        set "test" deny-unless-permit
                        policy "transform based on subject" permit transform subject.name
                        """, Decision.PERMIT, Value.of("Alice")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void denyUnlessPermitComplexScenarioTests(String description, String policySet, Decision expectedDecision,
            List<String> expectedObligations, List<String> expectedAdvice, Value expectedResource) {
        val result = evaluatePolicySet(policySet);
        assertDecision(result, expectedDecision);
        if (expectedObligations != null) {
            assertObligations(result, expectedObligations);
        }
        if (expectedAdvice != null) {
            assertAdvice(result, expectedAdvice);
        }
        if (expectedResource != null) {
            assertResource(result, expectedResource);
        }
    }

    private static Stream<Arguments> denyUnlessPermitComplexScenarioTests() {
        return Stream.of(arguments("Permit with obligations advice and transformation", """
                set "test" deny-unless-permit
                policy "permit with everything" permit
                obligation {"type": "log"}
                advice {"type": "cache"}
                transform "modified"
                policy "deny" deny obligation {"type": "deny_log"}
                """, Decision.PERMIT, List.of("log"), List.of("cache"), Value.of("modified")),

                arguments("Multiple permits without transformation uncertainty", """
                        set "test" deny-unless-permit
                        policy "permit 1" permit obligation {"type": "log1"}
                        policy "permit 2" permit advice {"type": "cache"}
                        policy "permit 3 with transformation" permit transform "modified"
                        """, Decision.PERMIT, List.of("log1"), List.of("cache"), Value.of("modified")));
    }
}
