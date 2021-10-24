package io.sapl.test.unit.usecase;

import static io.sapl.hamcrest.Matchers.anyVal;
import static io.sapl.test.Imports.*;
import static org.hamcrest.CoreMatchers.is;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.unit.SaplUnitTestFixture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class F_PolicyWithComplexFunctionTest {
	
	private SaplTestFixture fixture;
	
	@BeforeEach
	void setUp() {
		fixture = new SaplUnitTestFixture("policyWithComplexFunction");
	}



	@Test
	void test_streamingPolicyWithMockedFunction_ReturnValueDependingOnSimpleParameters_AndDynamicMockedAttribute() {
		
		fixture.constructTestCaseWithMocks()
			.givenAttribute("company.pip1")
			.givenAttribute("company.pip2")
			.givenFunction("company.complexFunction", 
					whenFunctionParams(is(Val.of(1)), is(Val.of("foo"))), 
					thenReturn(Val.of(true)))
			.givenFunction("company.complexFunction",
					whenFunctionParams(is(Val.of(2)), anyVal()), 
					thenReturn(Val.of(true)))
			.givenFunction("company.complexFunction", 
					whenFunctionParams(anyVal(), anyVal()), 
					thenReturn(Val.of(false)))
			.when(AuthorizationSubscription.of("User1", "read", "heartBeatData"))
			.thenAttribute("company.pip1", Val.of(1))
			.thenAttribute("company.pip2", Val.of("foo"))
			.expectNextPermit()
			.thenAttribute("company.pip2", Val.of("bar"))
			.expectNextNotApplicable()
			.thenAttribute("company.pip1", Val.of(2))
			.expectNextPermit()
			.thenAttribute("company.pip2", Val.of("xxx"))
			.expectNextPermit()
			.thenAttribute("company.pip1", Val.of(3))
			.expectNextNotApplicable()
			.verify();
	
	}
	
	
	@Test
	void test_streamingPolicyWithMockedFunction_ReturnValueDependingOnComplexParameters_AndDynamicMockedAttribute() {
		
		fixture.constructTestCaseWithMocks()
			.givenAttribute("company.pip1")
			.givenAttribute("company.pip2")
			.givenFunction("company.complexFunction", (Val[] callParameter) -> {
				//probably you should check for number and type of parameters first
				
				Double param0 = callParameter[0].get().asDouble();
				Double param1 = callParameter[1].get().asDouble();
				
				return param0 % param1 == 0 ? Val.of(true) : Val.of(false);
			})
			.when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData"))
			.thenAttribute("company.pip1", Val.of(3))
			.thenAttribute("company.pip2", Val.of(2))
			.expectNextNotApplicable()
			.thenAttribute("company.pip1", Val.of(4))
			.expectPermit()
			.verify();
	
	}
}
