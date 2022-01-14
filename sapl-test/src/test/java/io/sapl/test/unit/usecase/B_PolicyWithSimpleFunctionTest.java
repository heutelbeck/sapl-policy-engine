/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.test.Imports.times;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.interpreter.InitializationException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.unit.SaplUnitTestFixture;

public class B_PolicyWithSimpleFunctionTest {

	private SaplTestFixture fixture;

	@BeforeEach
	void setUp() {
		fixture = new SaplUnitTestFixture("policyWithSimpleFunction");
		// Registration of Functions or PIPs for every test case
		// .registerFunction(new TemporalFunctionLibrary())
	}

	@Test
	void test_policyWithSimpleFunction() throws InitializationException {

		fixture.registerFunctionLibrary(new TemporalFunctionLibrary()) // do not mock function in this unit test
				.constructTestCase().when(AuthorizationSubscription.of("willi", "read", "something")).expectPermit()
				.verify();

	}

	@Test
	void test_policyWithSimpleMockedFunction() {

		fixture.constructTestCaseWithMocks().givenFunction("time.dayOfWeek", Val.of("SATURDAY"))
				.when(AuthorizationSubscription.of("willi", "read", "something")).expectPermit().verify();

	}

	@Test
	void test_policyWithSimpleMockedFunction_VerifyTimesCalled() {

		fixture.constructTestCaseWithMocks().givenFunction("time.dayOfWeek", Val.of("SATURDAY"), times(1))
				.when(AuthorizationSubscription.of("willi", "read", "something")).expectPermit().verify();

	}

}
