package io.sapl.test.unit.usecase;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.unit.SaplUnitTestFixture;

public class D_PolicyWithMultipleFunctionsOrPIPsTest {
	
	private SaplTestFixture fixture;
	
	@BeforeEach
	void setUp() {
		fixture = new SaplUnitTestFixture("policyWithMultipleFunctionsOrPIPs");
	}

	
	@Test
	void test_policyWithMultipleMocks() {
		
		fixture.constructTestCaseWithMocks()
			.givenAttribute("test.upper", Val.of("WILLI"))
			.givenFunction("time.dayOfWeekFrom", Val.of("SATURDAY"))
			.when(AuthorizationSubscription.of("willi", "read", "something"))
			.expectPermit()
			.verify();
	
	}	
}
