package io.sapl.test.unit.usecase;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.unit.SaplUnitTestFixture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class I_PolicyWithEnvironmentAttribute {
	
	private SaplTestFixture fixture;

	@BeforeEach
	void setUp() {
		fixture = new SaplUnitTestFixture("policyWithEnvironmentAttribute.sapl");
	}

	@Test
	void test() throws JsonMappingException, JsonProcessingException {

		fixture.constructTestCaseWithMocks()
				.givenAttribute("org.emergencyLevel", Val.of(0))
				.when(AuthorizationSubscription.of("WILLI", "write", "something"))
				.expectPermit()
				.verify();

	}

}
