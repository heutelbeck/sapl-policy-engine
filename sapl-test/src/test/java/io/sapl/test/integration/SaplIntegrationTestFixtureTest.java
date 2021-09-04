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
		fixture.constructTestCase()
			.when(AuthorizationSubscription.of("WILLI", "read", "foo"))
			.expectPermit()
			.verify();
	}
	
	@Test
	void test_withPDPPolicyCombiningAlgorithm() {
		SaplIntegrationTestFixture fixture = new SaplIntegrationTestFixture("policiesIT");
		fixture.withPDPPolicyCombiningAlgorithm(PolicyDocumentCombiningAlgorithm.PERMIT_UNLESS_DENY)
			.constructTestCase()
			.when(AuthorizationSubscription.of("WILLI", "read", "foo"))
			.expectDeny()
			.verify();
	}
	
	@Test
	void test_withPDPVariables() {

		SaplIntegrationTestFixture fixture = new SaplIntegrationTestFixture("it/variables");
		Map<String, JsonNode> variables = new HashMap<>();
		variables.put("test", mapper.createObjectNode().numberNode(1));
		fixture.withPDPVariables(variables)
			.constructTestCase()
			.when(AuthorizationSubscription.of("WILLI", "read", "foo"))
			.expectPermit()
			.verify();
	}
	
	
	@Test
	void test_withoutPDPVariables() {

		SaplIntegrationTestFixture fixture = new SaplIntegrationTestFixture("it/variables");
		fixture
			.constructTestCase()
			.when(AuthorizationSubscription.of("WILLI", "read", "foo"))
			.expectDeny()
			.verify();
	}
	
	@Test
	void test_invalidPath1() {
		SaplTestFixture fixture = new SaplIntegrationTestFixture("");
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() ->fixture.constructTestCase());
	}
	
	@Test
	void test_invalidPath2() {
		SaplTestFixture fixture = new SaplIntegrationTestFixture("");
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() ->fixture.constructTestCaseWithMocks());
	}
	
	@Test
	void test_invalidPath3() {
		SaplTestFixture fixture = new SaplIntegrationTestFixture(null);
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() ->fixture.constructTestCase());
	}
	
	@Test
	void test_invalidPath4() {
		SaplTestFixture fixture = new SaplIntegrationTestFixture(null);
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() ->fixture.constructTestCaseWithMocks());
	}

}
