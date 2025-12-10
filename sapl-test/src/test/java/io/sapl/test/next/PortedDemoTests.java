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
package io.sapl.test.next;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.functions.libraries.TemporalFunctionLibrary;
import org.junit.jupiter.api.Test;

import static io.sapl.test.next.Matchers.any;
import static io.sapl.test.next.Matchers.args;

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
}
