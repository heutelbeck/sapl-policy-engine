/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.integration.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.integration.SaplIntegrationTestFixture;
import io.sapl.test.unit.SaplUnitTestFixture;

class A_SimplePDPTests {

    private SaplTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new SaplIntegrationTestFixture("policiesIT");
    }

    @Test
    void test_simpleIT_verifyCombined() {
        fixture.constructTestCase().when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectPermit().verify();
    }

    @Test
    void test_simpleIT_testSinglePolicyA() {
        var unitFixture = new SaplUnitTestFixture("policiesIT/policy_A");
        unitFixture.constructTestCase().when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectDeny()
                .verify();
    }

    @Test
    void test_simpleIT_testSinglePolicyB() {
        var unitFixture = new SaplUnitTestFixture("policiesIT/policy_B");
        unitFixture.constructTestCase().when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectPermit()
                .verify();
    }

}
