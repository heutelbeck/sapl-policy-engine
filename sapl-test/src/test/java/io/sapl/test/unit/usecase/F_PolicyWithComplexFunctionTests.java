/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.hamcrest.Matchers.anyVal;
import static io.sapl.test.Imports.thenReturn;
import static io.sapl.test.Imports.whenFunctionParams;
import static org.hamcrest.CoreMatchers.is;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.unit.SaplUnitTestFixture;

class F_PolicyWithComplexFunctionTests {

	private SaplTestFixture fixture;

	@BeforeEach
	void setUp() {
		fixture = new SaplUnitTestFixture("policyWithComplexFunction");
	}

	@Test
	void test_streamingPolicyWithMockedFunction_ReturnValueDependingOnSimpleParameters_AndDynamicMockedAttribute() {
		fixture.constructTestCaseWithMocks().givenAttribute("company.pip1").givenAttribute("company.pip2")
				.givenFunction("company.complexFunction", whenFunctionParams(is(Val.of(1)), is(Val.of("foo"))),
						thenReturn(Val.of(true)))
				.givenFunction("company.complexFunction", whenFunctionParams(is(Val.of(2)), anyVal()),
						thenReturn(Val.of(true)))
				.givenFunction("company.complexFunction", whenFunctionParams(anyVal(), anyVal()),
						thenReturn(Val.of(false)))
				.when(AuthorizationSubscription.of("User1", "read", "heartBeatData"))
				.thenAttribute("company.pip1", Val.of(1)).thenAttribute("company.pip2", Val.of("foo"))
				.expectNextPermit().thenAttribute("company.pip2", Val.of("bar")).expectNextNotApplicable()
				.thenAttribute("company.pip1", Val.of(2)).expectNextPermit()
				.thenAttribute("company.pip2", Val.of("xxx")).expectNextPermit()
				.thenAttribute("company.pip1", Val.of(3)).expectNextNotApplicable().verify();
	}

	@Test
	void test_streamingPolicyWithMockedFunction_ReturnValueDependingOnComplexParameters_AndDynamicMockedAttribute() {
		fixture.constructTestCaseWithMocks().givenAttribute("company.pip1").givenAttribute("company.pip2")
				.givenFunction("company.complexFunction", (Val[] callParameter) -> {
					// probably you should check for number and type of parameters first

					Double param0 = callParameter[0].get().asDouble();
					Double param1 = callParameter[1].get().asDouble();

					return param0 % param1 == 0 ? Val.of(true) : Val.of(false);
				}).when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData"))
				.thenAttribute("company.pip1", Val.of(3)).thenAttribute("company.pip2", Val.of(2))
				.expectNextNotApplicable().thenAttribute("company.pip1", Val.of(4)).expectPermit().verify();
	}

}
