/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.HashMap;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixture;

class SaplIntegrationTestFixtureTest {

	private ObjectMapper mapper = new ObjectMapper();

	@Test
	void test() {
		SaplIntegrationTestFixture fixture = new SaplIntegrationTestFixture("policiesIT");
		fixture.constructTestCase().when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectPermit().verify();
	}

	@Test
	void test_withPDPPolicyCombiningAlgorithm() {
		SaplIntegrationTestFixture fixture = new SaplIntegrationTestFixture("policiesIT");
		fixture.withPDPPolicyCombiningAlgorithm(PolicyDocumentCombiningAlgorithm.PERMIT_UNLESS_DENY).constructTestCase()
				.when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectDeny().verify();
	}

	@Test
	void test_withPDPVariables() {

		SaplIntegrationTestFixture fixture   = new SaplIntegrationTestFixture("it/variables");
		Map<String, JsonNode>      variables = new HashMap<>();
		variables.put("test", mapper.createObjectNode().numberNode(1));
		fixture.withPDPVariables(variables).constructTestCase()
				.when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectPermit().verify();
	}

	@Test
	void test_withoutPDPVariables() {
		SaplIntegrationTestFixture fixture = new SaplIntegrationTestFixture("it/variables");
		fixture.constructTestCase().when(AuthorizationSubscription.of("WILLI", "read", "foo")).expectDeny().verify();
	}

	@Test
	void test_invalidPath1() {
		SaplTestFixture fixture = new SaplIntegrationTestFixture("");
		Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCase);
	}

	@Test
	void test_invalidPath2() {
		SaplTestFixture fixture = new SaplIntegrationTestFixture("");
		Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCaseWithMocks);
	}

	@Test
	void test_invalidPath3() {
		SaplTestFixture fixture = new SaplIntegrationTestFixture(null);
		Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCase);
	}

	@Test
	void test_invalidPath4() {
		SaplTestFixture fixture = new SaplIntegrationTestFixture(null);
		Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(fixture::constructTestCaseWithMocks);
	}

}
