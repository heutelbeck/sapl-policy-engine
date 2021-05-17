package io.sapl.test.unit.usecase;

import static io.sapl.test.Imports.times;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.interpreter.InitializationException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.unit.SaplUnitTestFixture;

public class B_PolicyWithSimpleFunctionTest {
	
	private SaplTestFixture fixture;
	
	@BeforeEach
	void setUp() {
		fixture = new SaplUnitTestFixture("policyWithSimpleFunction");
			//Registration of Functions or PIPs for every test case
			//.registerFunction(new TemporalFunctionLibrary())
	}

	
	@Test
	void test_policyWithSimpleFunction() throws InitializationException {
			
		fixture
			.registerFunctionLibrary(new TemporalFunctionLibrary()) //do not mock function in this unit test
			.constructTestCase()
			.when(AuthorizationSubscription.of("willi", "read", "something"))
			.expectPermit()
			.verify();
	
	}
	
	@Test
	void test_policyWithSimpleMockedFunction() {
		
		fixture.constructTestCaseWithMocks()
			.givenFunction("time.dayOfWeekFrom", Val.of("SATURDAY"))
			.when(AuthorizationSubscription.of("willi", "read", "something"))
			.expectPermit()
			.verify();
	
	}
	
	@Test
	void test_policyWithSimpleMockedFunction_VerifyTimesCalled() {
		
		fixture.constructTestCaseWithMocks()
			.givenFunction("time.dayOfWeekFrom", Val.of("SATURDAY"), times(1))
			.when(AuthorizationSubscription.of("willi", "read", "something"))
			.expectPermit()
			.verify();
	
	}
}
