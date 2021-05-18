package io.sapl.test.pdp.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.pdp.SaplIntegrationTestFixture;
import io.sapl.test.unit.SaplUnitTestFixture;

class A_SimplePDPTest {

private SaplTestFixture fixture;
	
	@BeforeEach
	void setUp() {
		fixture = new SaplIntegrationTestFixture("policiesIT");
	}
		
	@Test
	void test_simpleIT_verifyCombined() {

		fixture.constructTestCase()
			.when(AuthorizationSubscription.of("WILLI", "read", "foo"))
			.expectPermit()
			.verify();
			
	}
	
	
	@Test
	void test_simpleIT_testSinglePolicyA() {

		SaplTestFixture unitFixture = new SaplUnitTestFixture("policiesIT/policy_A");
		unitFixture.constructTestCase()
			.when(AuthorizationSubscription.of("WILLI", "read", "foo"))
			.expectDeny()
			.verify();
			
	}
	
	@Test
	void test_simpleIT_testSinglePolicyB() {

		SaplTestFixture unitFixture = new SaplUnitTestFixture("policiesIT/policy_B");
		unitFixture.constructTestCase()
			.when(AuthorizationSubscription.of("WILLI", "read", "foo"))
			.expectPermit()
			.verify();
			
	}
	
	
	

}
