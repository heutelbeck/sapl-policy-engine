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

import static io.sapl.hamcrest.Matchers.anyVal;
import static io.sapl.hamcrest.Matchers.hasObligationMatching;
import static io.sapl.hamcrest.Matchers.isPermit;
import static io.sapl.hamcrest.Matchers.isResourceMatching;
import static io.sapl.test.Imports.whenFunctionParams;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.unit.SaplUnitTestFixture;

public class Z_ComplexUnitTest {

	private SaplTestFixture fixture;

	@BeforeEach
	public void setUp() {
		fixture = new SaplUnitTestFixture("policyComplex");
	}

	// @Test
	// @Disabled
	// only to test readability of a complex test case
	void test_complexPolicy() {
		var timestamp0 = Val.of(ZonedDateTime.of(2021, 1, 7, 0, 0, 0, 0, ZoneId.of("UTC")).toString());
		var timestamp1 = Val.of(ZonedDateTime.of(2021, 1, 8, 0, 0, 1, 0, ZoneId.of("UTC")).toString());
		var timestamp2 = Val.of(ZonedDateTime.of(2021, 1, 9, 0, 0, 2, 0, ZoneId.of("UTC")).toString());

		fixture.constructTestCaseWithMocks().givenAttribute("clock.ticker", timestamp0, timestamp1, timestamp2)
				.givenFunctionOnce("time.dayOfWeekFrom", Val.of("SATURDAY"), Val.of("SUNDAY"), Val.of("MONDAY"))
				.givenAttribute("company.reportmode", Val.of("ALL"))
				.givenFunction("company.subjectConverter", whenFunctionParams(is(Val.of("ADMIN")), anyVal()),
						Val.of("ROLE_ADMIN"))
				.givenFunction("company.subjectConverter",
						whenFunctionParams(is(Val.of("USER")), is(Val.of("nikolai"))), Val.of("ROLE_ADMIN"))
				.givenFunction("company.subjectConverter", whenFunctionParams(is(Val.of("USER")), anyVal()),
						Val.of("ROLE_USER"))
				.when(AuthorizationSubscription.of("nikolai", "read", "report")).expectNextDeny(2)
				.expectNext(allOf(isPermit(), hasObligationMatching(ob -> ob.get("type").asText().equals("logAccess")),
						isResourceMatching(jsonNode -> jsonNode.has("report") && jsonNode.get("report").has("numberOfCurrentPatients")
                                && jsonNode.get("report").get("numberOfCurrentPatients").asInt() == 34)))
				.verify();
	}

}
