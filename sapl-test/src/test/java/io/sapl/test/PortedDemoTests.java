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
package io.sapl.test;

import static io.sapl.test.Matchers.any;
import static io.sapl.test.Matchers.args;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.functions.libraries.TemporalFunctionLibrary;
import org.junit.jupiter.api.Test;

/**
 * Ported tests from sapl-demo-testing A_ and B_ test classes.
 * Validates the new SaplTestFixture API works correctly.
 */
class PortedDemoTests {

    // policySimple.sapl content (originally a policy set, but we test single
    // policy)
    private static final String POLICY_SIMPLE = """
            policy "policySimple"
            permit
                action == "read"
            where
                subject == "willi";
            """;

    // policyWithSimpleFunction.sapl content
    private static final String POLICY_WITH_FUNCTION = """
            policy "policyWithSimpleFunction"
            permit
                action == "read"
            where
                time.dayOfWeek("2021-02-08T16:16:33.616Z") =~ "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY";
            """;

    // Policy set with "for" clause to test target expression coverage
    private static final String POLICY_SET_WITH_FOR = """
            set "forbidden-tomes-access"
            deny-unless-permit
            for resource == "necronomicon"

            policy "allow-cultist-access"
            permit
                action == "read"
            where
                subject.role == "cultist";

            policy "allow-researcher-with-clearance"
            permit
                action == "read"
            where
                subject.role == "researcher";
                subject.clearanceLevel > 3;
            """;

    /**
     * Ported from A_PolicySimpleTest.test_simplePolicy()
     * Tests a simple policy with subject/action matching.
     */
    @Test
    void whenSubjectIsWilliAndActionIsRead_thenPermit() {
        SaplTestFixture.createSingleTest().withPolicy(POLICY_SIMPLE)
                .whenDecide(AuthorizationSubscription.of("willi", "read", "something")).expectPermit().verify();
    }

    /**
     * Ported from B_PolicyWithSimpleFunctionTest.test_policyWithSimpleFunction()
     * Tests policy with real function library registered.
     */
    @Test
    void whenUsingRealTemporalFunctionLibrary_thenPermit() {
        SaplTestFixture.createSingleTest().withFunctionLibrary(TemporalFunctionLibrary.class)
                .withPolicy(POLICY_WITH_FUNCTION).whenDecide(AuthorizationSubscription.of("willi", "read", "something"))
                .expectPermit().verify();
    }

    /**
     * Ported from
     * B_PolicyWithSimpleFunctionTest.test_policyWithSimpleMockedFunction()
     * Tests policy with mocked function.
     */
    @Test
    void whenMockingDayOfWeekFunction_thenPermit() {
        SaplTestFixture.createSingleTest().withPolicy(POLICY_WITH_FUNCTION)
                .givenFunction("time.dayOfWeek", args(any()), Value.of("SATURDAY"))
                .whenDecide(AuthorizationSubscription.of("willi", "read", "something")).expectPermit().verify();
    }

    /**
     * Additional test: verify deny when subject doesn't match.
     */
    @Test
    void whenSubjectIsNotWilli_thenNotApplicable() {
        SaplTestFixture.createSingleTest().withPolicy(POLICY_SIMPLE)
                .whenDecide(AuthorizationSubscription.of("notWilli", "read", "something")).expectNotApplicable()
                .verify();
    }

    /**
     * Additional test: verify deny when action doesn't match.
     */
    @Test
    void whenActionIsNotRead_thenNotApplicable() {
        SaplTestFixture.createSingleTest().withPolicy(POLICY_SIMPLE)
                .whenDecide(AuthorizationSubscription.of("willi", "write", "something")).expectNotApplicable().verify();
    }

    // Tests for policy set with "for" clause

    /**
     * Tests when "for" clause matches and inner policy conditions are satisfied.
     * A cultist reading the necronomicon should be permitted.
     */
    @Test
    void whenForClauseMatchesAndCultistReadsNecronomicon_thenPermit() {
        var subject = ObjectValue.builder().put("role", Value.of("cultist")).build();
        SaplTestFixture.createSingleTest().withPolicy(POLICY_SET_WITH_FOR)
                .whenDecide(AuthorizationSubscription.of(subject, "read", "necronomicon")).expectPermit().verify();
    }

    /**
     * Tests when "for" clause does not match.
     * Any request for a different resource should be not applicable.
     */
    @Test
    void whenForClauseDoesNotMatch_thenNotApplicable() {
        var subject = ObjectValue.builder().put("role", Value.of("cultist")).build();
        SaplTestFixture.createSingleTest().withPolicy(POLICY_SET_WITH_FOR)
                .whenDecide(AuthorizationSubscription.of(subject, "read", "other-artifact")).expectNotApplicable()
                .verify();
    }

    /**
     * Tests when "for" clause matches but no inner policy permits.
     * A regular person (not cultist or high-clearance researcher) should be denied
     * due to deny-unless-permit combining algorithm.
     */
    @Test
    void whenForClauseMatchesButNoInnerPolicyPermits_thenDeny() {
        var subject = ObjectValue.builder().put("role", Value.of("visitor")).build();
        SaplTestFixture.createSingleTest().withPolicy(POLICY_SET_WITH_FOR)
                .whenDecide(AuthorizationSubscription.of(subject, "read", "necronomicon")).expectDeny().verify();
    }

    /**
     * Tests second inner policy path - researcher with sufficient clearance.
     */
    @Test
    void whenResearcherWithHighClearanceReadsNecronomicon_thenPermit() {
        var subject = ObjectValue.builder().put("role", Value.of("researcher")).put("clearanceLevel", Value.of(5))
                .build();
        SaplTestFixture.createSingleTest().withPolicy(POLICY_SET_WITH_FOR)
                .whenDecide(AuthorizationSubscription.of(subject, "read", "necronomicon")).expectPermit().verify();
    }

    /**
     * Tests researcher with insufficient clearance.
     */
    @Test
    void whenResearcherWithLowClearanceReadsNecronomicon_thenDeny() {
        var subject = ObjectValue.builder().put("role", Value.of("researcher")).put("clearanceLevel", Value.of(2))
                .build();
        SaplTestFixture.createSingleTest().withPolicy(POLICY_SET_WITH_FOR)
                .whenDecide(AuthorizationSubscription.of(subject, "read", "necronomicon")).expectDeny().verify();
    }
}
