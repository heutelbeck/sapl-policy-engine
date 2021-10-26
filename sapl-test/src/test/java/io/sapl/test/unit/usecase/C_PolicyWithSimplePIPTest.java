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

import static io.sapl.hamcrest.Matchers.val;
import static io.sapl.test.Imports.whenParentValue;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.interpreter.InitializationException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.unit.SaplUnitTestFixture;
import io.sapl.test.unit.TestPIP;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class C_PolicyWithSimplePIPTest {

	private SaplTestFixture fixture;
	
	@BeforeEach
	void setUp() {
		fixture = new SaplUnitTestFixture("policyWithSimplePIP");
	}
	
	@Test
	void test_policyWithSimpleMockedPIP() {
		
		fixture.constructTestCaseWithMocks()
			.givenAttribute("test.upper", Val.of("WILLI"))
			.when(AuthorizationSubscription.of("willi", "read", "something"))
			.expectPermit()
			.verify();
		
	}
	
	@Test
	void test_policyWithSimplePIP() throws InitializationException {
		
		fixture.registerPIP(new TestPIP())
			.constructTestCase()
			.when(AuthorizationSubscription.of("willi", "read", "something"))
			.expectPermit()
			.verify();
		
	}	
	
	
	@Test
	void test_policyWithSimplePIP_mockedWhenParameters() throws InitializationException {
		
		fixture.constructTestCaseWithMocks()
			.givenAttribute("test.upper", whenParentValue(val("willi")), Val.of("WILLI"))
			.when(AuthorizationSubscription.of("willi", "read", "something"))
			.expectPermit()
			.verify();
		
	}
}
