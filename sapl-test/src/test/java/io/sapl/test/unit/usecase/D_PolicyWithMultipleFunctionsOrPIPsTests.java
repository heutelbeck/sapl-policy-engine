/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.unit.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.unit.SaplUnitTestFixture;

class D_PolicyWithMultipleFunctionsOrPIPsTests {

    private SaplTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new SaplUnitTestFixture("policyWithMultipleFunctionsOrPIPs");
    }

    @Test
    void test_policyWithMultipleMocks() {
        fixture.constructTestCaseWithMocks().givenAttribute("test.upper", Val.of("WILLI"))
                .givenFunction("time.dayOfWeekFrom", Val.of("SATURDAY"))
                .when(AuthorizationSubscription.of("willi", "read", "something")).expectPermit().verify();
    }

}
