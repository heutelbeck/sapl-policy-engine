package io.sapl.test.unit.usecase;

import static io.sapl.hamcrest.Matchers.anyVal;
import static io.sapl.test.Imports.whenParameters;
import static org.hamcrest.CoreMatchers.is;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.mocking.FunctionCall;
import io.sapl.test.unit.SaplUnitTestFixture;

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
			.givenFunction("company.complexFunction", Val.of(true), whenParameters(is(Val.of(1)), is(Val.of("foo"))))
			.givenFunction("company.complexFunction", Val.of(true), whenParameters(is(Val.of(2)), anyVal()))
			.givenFunction("company.complexFunction", Val.of(false), whenParameters(anyVal(), anyVal()))
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
			.givenFunction("company.complexFunction", (FunctionCall call) -> {
				//probably you should check for number and type of parameters first
				
				Double param0 = call.getArgument(0).get().asDouble();
				Double param1 = call.getArgument(1).get().asDouble();
				
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
