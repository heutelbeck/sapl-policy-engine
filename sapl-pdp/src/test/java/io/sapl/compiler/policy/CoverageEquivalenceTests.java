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
package io.sapl.compiler.policy;

import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static io.sapl.util.SaplTesting.assertCoverageMatchesProduction;
import static io.sapl.util.SaplTesting.attributeBroker;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("Coverage Path Equivalence")
class CoverageEquivalenceTests {

    private static final String DEFAULT_SUBSCRIPTION = """
            {"subject": "alice", "action": "read", "resource": "document"}
            """;

    static Stream<Arguments> staticPolicies() {
        return Stream.of(arguments("empty permit", "policy \"test\" permit"),
                arguments("empty deny", "policy \"test\" deny"),
                arguments("true body condition", "policy \"test\" permit where true;"),
                arguments("false body condition", "policy \"test\" permit where false;"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("staticPolicies")
    @DisplayName("Static policies: coverage matches production")
    void staticPoliciesCoverageMatches(String name, String policy) {
        assertCoverageMatchesProduction(DEFAULT_SUBSCRIPTION, policy);
    }

    // Pure target tests removed - coverage path skips target evaluation
    static Stream<Arguments> purePolicies() {
        return Stream.of(arguments("pure body matches", "policy \"test\" permit where subject == \"alice\";"),
                arguments("pure body fails", "policy \"test\" permit where subject == \"bob\";"),
                arguments("pure target and body (target true)", """
                        policy "test" permit
                        where
                        subject == "alice";
                        action == "read";
                        """), arguments("multiple conditions all true", """
                        policy "test" permit
                        where subject == "alice";
                              action == "read";
                              resource == "document";
                        """), arguments("multiple conditions one false", """
                        policy "test" permit
                        where subject == "alice";
                              action == "write";
                        """), arguments("undefined field access", """
                        policy "test" permit
                        where subject.nonexistent.field == true;
                        """));
    }

    @MethodSource("purePolicies")
    @ParameterizedTest(name = "{0}")
    @DisplayName("Pure policies: coverage matches production")
    void purePoliciesCoverageMatches(String name, String policy) {
        assertCoverageMatchesProduction(DEFAULT_SUBSCRIPTION, policy);
    }

    static Stream<Arguments> policiesWithConstraints() {
        return Stream.of(arguments("single obligation", "policy \"test\" permit obligation \"log_access\""),
                arguments("single advice", "policy \"test\" permit advice \"consider_caching\""),
                arguments("single transform", "policy \"test\" permit transform \"filtered\""),
                arguments("multiple obligations", """
                        policy "test" permit
                        obligation "log_access"
                        obligation "notify_admin"
                        """), arguments("all constraint types", """
                        policy "test" permit
                        obligation "log"
                        advice "hint"
                        transform "filtered"
                        """), arguments("pure body with constraints", """
                        policy "test" permit
                        where subject == "alice";
                        obligation "log"
                        advice "hint"
                        """), arguments("pure constraints", """
                        policy "test" permit
                        obligation subject
                        advice action
                        transform resource
                        """), arguments("object transform", """
                        policy "test" permit
                        transform {"status": "approved", "level": 5}
                        """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("policiesWithConstraints")
    @DisplayName("Policies with constraints: coverage matches production")
    void constraintPoliciesCoverageMatches(String name, String policy) {
        assertCoverageMatchesProduction(DEFAULT_SUBSCRIPTION, policy);
    }

    // Note: Static errors (like 1/0) throw at compile time, so runtime errors tests
    // require attribute-based errors or undefined field access
    static Stream<Arguments> errorCases() {
        return Stream.of(
                // Short circuit prevents undefined access being an issue
                arguments("short circuit false", "policy \"test\" permit where false && subject.a.b.c;"),
                arguments("undefined equality check", "policy \"test\" permit where subject.missing == undefined;"),
                // OR short circuit
                arguments("short circuit true", "policy \"test\" permit where true || subject.a.b.c;"));
    }

    @MethodSource("errorCases")
    @ParameterizedTest(name = "{0}")
    @DisplayName("Error cases: coverage matches production")
    void errorCasesCoverageMatches(String name, String policy) {
        assertCoverageMatchesProduction(DEFAULT_SUBSCRIPTION, policy);
    }

    @Test
    @DisplayName("Runtime errors from attribute")
    void runtimeErrorFromAttribute() {
        val policy = """
                policy "test" permit
                where subject.<attr.value> == true;
                """;
        val broker = attributeBroker("attr.value", Value.error("Service unavailable"));
        assertCoverageMatchesProduction(DEFAULT_SUBSCRIPTION, policy, broker);
    }

    @Test
    @DisplayName("Stream policy with single emission")
    void streamPolicySingleEmission() {
        val policy = """
                policy "test" permit
                where subject.<attr.check> == true;
                """;
        val broker = attributeBroker("attr.check", Value.TRUE);
        assertCoverageMatchesProduction(DEFAULT_SUBSCRIPTION, policy, broker);
    }

    @Test
    @DisplayName("Stream policy with multiple emissions")
    void streamPolicyMultipleEmissions() {
        val policy = """
                policy "test" permit
                where subject.<attr.status> == "active";
                """;
        val broker = attributeBroker("attr.status", Value.of("active"), Value.of("inactive"), Value.of("active"));
        assertCoverageMatchesProduction(DEFAULT_SUBSCRIPTION, policy, broker);
    }

    @Test
    @DisplayName("Stream policy with static constraints")
    void streamPolicyWithStaticConstraints() {
        val policy = """
                policy "test" permit
                where subject.<attr.valid> == true;
                obligation "log"
                advice "cache"
                """;
        val broker = attributeBroker("attr.valid", Value.TRUE, Value.FALSE);
        assertCoverageMatchesProduction(DEFAULT_SUBSCRIPTION, policy, broker);
    }

    @Test
    @DisplayName("Stream policy with pure constraints")
    void streamPolicyWithPureConstraints() {
        val policy = """
                policy "test" permit
                where subject.<attr.allowed> == true;
                obligation subject
                advice action
                """;
        val broker = attributeBroker("attr.allowed", Value.TRUE);
        assertCoverageMatchesProduction(DEFAULT_SUBSCRIPTION, policy, broker);
    }

    @Test
    @DisplayName("Stream policy with stream constraints")
    void streamPolicyWithStreamConstraints() {
        val policy = """
                policy "test" permit
                where subject.<body.attr> == true;
                obligation <constraint.attr>
                """;
        val broker = attributeBroker(
                Map.of("body.attr", new Value[] { Value.TRUE }, "constraint.attr", new Value[] { Value.of("logged") }));
        assertCoverageMatchesProduction(DEFAULT_SUBSCRIPTION, policy, broker);
    }

    @Test
    @DisplayName("Pure body with stream obligation")
    void pureBodyStreamObligation() {
        val policy = """
                policy "test" permit
                where subject == "alice";
                obligation <audit.log>
                """;
        val broker = attributeBroker("audit.log", Value.of("recorded"));
        assertCoverageMatchesProduction(DEFAULT_SUBSCRIPTION, policy, broker);
    }

    @Test
    @DisplayName("Deny policy with constraints")
    void denyPolicyWithConstraints() {
        val policy = """
                policy "test" deny
                where subject == "alice";
                obligation "block_access"
                advice "contact_admin"
                """;
        assertCoverageMatchesProduction(DEFAULT_SUBSCRIPTION, policy);
    }
}
