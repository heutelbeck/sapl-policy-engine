/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.hamcrest.Matchers.hasObligation;
import static io.sapl.hamcrest.Matchers.isPermit;
import static io.sapl.hamcrest.Matchers.val;
import static io.sapl.test.Imports.whenFunctionParams;
import static org.hamcrest.CoreMatchers.allOf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.unit.SaplUnitTestFixture;

class H_PolicyDocumentMultipleReferencesToSameAttributeTests {

	private SaplTestFixture fixture;

	@BeforeEach
	void setUp() {
		fixture = new SaplUnitTestFixture("policyDocumentWithMultipleCallsToSameAttribute.sapl");
	}

	@Test
	void test_withFunctionSequenceMock() {
		fixture.constructTestCaseWithMocks()
				.givenAttribute("time.now", Val.of(1), Val.of(2), Val.of(3), Val.of(4), Val.of(5), Val.of(6))
				.givenFunctionOnce("time.secondOf", Val.of(1), Val.of(15), Val.of(25), Val.of(25), Val.of(35),
						Val.of(35), Val.of(45), Val.of(45), Val.of(45), Val.of(55), Val.of(55), Val.of(55))
				.when(AuthorizationSubscription.of("WILLI", "read", "something"))
				.expectNext(allOf(isPermit(), hasObligation("A"))).expectNext(allOf(isPermit(), hasObligation("A")))
				.expectNext(allOf(isPermit(), hasObligation("B"))).expectNext(allOf(isPermit(), hasObligation("B")))
				.expectNext(allOf(isPermit(), hasObligation("C"))).expectNext(allOf(isPermit(), hasObligation("C")))
				.verify();
	}

	@Test
	void test_withFunctionForParametersMock() {
		fixture.constructTestCaseWithMocks()
				.givenAttribute("time.now", Val.of(1), Val.of(2), Val.of(3), Val.of(4), Val.of(5), Val.of(6))
				.givenFunction("time.secondOf", whenFunctionParams(val(1)), Val.of(1))
				.givenFunction("time.secondOf", whenFunctionParams(val(2)), Val.of(15))
				.givenFunction("time.secondOf", whenFunctionParams(val(3)), Val.of(25))
				.givenFunction("time.secondOf", whenFunctionParams(val(4)), Val.of(35))
				.givenFunction("time.secondOf", whenFunctionParams(val(5)), Val.of(45))
				.givenFunction("time.secondOf", whenFunctionParams(val(6)), Val.of(55))
				.when(AuthorizationSubscription.of("WILLI", "read", "something"))
				.expectNext(allOf(isPermit(), hasObligation("A"))).expectNext(allOf(isPermit(), hasObligation("A")))
				.expectNext(allOf(isPermit(), hasObligation("B"))).expectNext(allOf(isPermit(), hasObligation("B")))
				.expectNext(allOf(isPermit(), hasObligation("C"))).expectNext(allOf(isPermit(), hasObligation("C")))
				.verify();
	}

}
