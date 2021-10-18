package io.sapl.test.unit.usecase;

import static io.sapl.hamcrest.Matchers.*;
import static io.sapl.test.Imports.whenParameters;
import static org.hamcrest.CoreMatchers.allOf;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.unit.SaplUnitTestFixture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class H_PolicyDocumentMultipleReferencesToSameAttributeTest {
	private SaplTestFixture fixture;
	
	@BeforeEach
	void setUp() {
		fixture = new SaplUnitTestFixture("policyDocumentWithMultipleCallsToSameAttribute.sapl");
	}
	
	
	@Test
	void test_withFunctionSequenceMock() {

		fixture.constructTestCaseWithMocks()
			.givenAttribute("clock.now", Val.of(1), Val.of(2), Val.of(3), Val.of(4), Val.of(5), Val.of(6))
			.givenFunctionOnce("time.localSecond", Val.of(1), Val.of(15), Val.of(25), Val.of(25), Val.of(35), Val.of(35), Val.of(45), Val.of(45), Val.of(45), Val.of(55), Val.of(55), Val.of(55))
			.when(AuthorizationSubscription.of("WILLI", "read", "something"))
			.expectNext(allOf(isPermit(), hasObligation("A")))
			.expectNext(allOf(isPermit(), hasObligation("A")))
			.expectNext(allOf(isPermit(), hasObligation("B")))
			.expectNext(allOf(isPermit(), hasObligation("B")))
			.expectNext(allOf(isPermit(), hasObligation("C")))
			.expectNext(allOf(isPermit(), hasObligation("C")))
			.verify();
			
	}
	

	@Test
	void test_withFunctionForParametersMock() {

		fixture.constructTestCaseWithMocks()
			.givenAttribute("clock.now", Val.of(1), Val.of(2), Val.of(3), Val.of(4), Val.of(5), Val.of(6))
			.givenFunction("time.localSecond", Val.of(1), whenParameters(val(1)))
			.givenFunction("time.localSecond", Val.of(15), whenParameters(val(2)))
			.givenFunction("time.localSecond", Val.of(25), whenParameters(val(3)))
			.givenFunction("time.localSecond", Val.of(35), whenParameters(val(4)))
			.givenFunction("time.localSecond", Val.of(45), whenParameters(val(5)))
			.givenFunction("time.localSecond", Val.of(55), whenParameters(val(6)))
			.when(AuthorizationSubscription.of("WILLI", "read", "something"))
			.expectNext(allOf(isPermit(), hasObligation("A")))
			.expectNext(allOf(isPermit(), hasObligation("A")))
			.expectNext(allOf(isPermit(), hasObligation("B")))
			.expectNext(allOf(isPermit(), hasObligation("B")))
			.expectNext(allOf(isPermit(), hasObligation("C")))
			.expectNext(allOf(isPermit(), hasObligation("C")))
			.verify();
			
	}
	

}
