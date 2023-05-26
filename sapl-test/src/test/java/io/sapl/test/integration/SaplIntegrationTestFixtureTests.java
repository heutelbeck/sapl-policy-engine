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
package io.sapl.test.integration;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixture;

class SaplIntegrationTestFixtureTests {

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void test() {
		var fixture = new SaplIntegrationTestFixture("policiesIT");
		fixture.constructTestCase().when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectPermit().verify();
	}

	@Test
	void test_withPDPPolicyCombiningAlgorithm() {
		var fixture = new SaplIntegrationTestFixture("policiesIT");
		fixture.withPDPPolicyCombiningAlgorithm(PolicyDocumentCombiningAlgorithm.PERMIT_UNLESS_DENY).constructTestCase()
				.when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectDeny().verify();
	}

	@Test
	void test_withPDPVariables() {
		var fixture   = new SaplIntegrationTestFixture("it/variables");
		var variables = new HashMap<String, JsonNode>(1);
		variables.put("test", mapper.createObjectNode().numberNode(1));
		fixture.withPDPVariables(variables).constructTestCase()
				.when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectPermit().verify();
	}

	@Test
	void test_withoutPDPVariables() {
		var fixture = new SaplIntegrationTestFixture("it/variables");
		fixture.constructTestCase().when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectNotApplicable()
				.verify();
	}

	@Test
	void test_invalidPath1() {
		var fixture = new SaplIntegrationTestFixture("");
		assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCase);
	}

	@Test
	void test_invalidPath2() {
		SaplTestFixture fixture = new SaplIntegrationTestFixture("");
		assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCaseWithMocks);
	}

	@Test
	void test_invalidPath3() {
		SaplTestFixture fixture = new SaplIntegrationTestFixture(null);
		assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCase);
	}

	@Test
	void test_invalidPath4() {
		SaplTestFixture fixture = new SaplIntegrationTestFixture(null);
		assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCaseWithMocks);
	}

}
