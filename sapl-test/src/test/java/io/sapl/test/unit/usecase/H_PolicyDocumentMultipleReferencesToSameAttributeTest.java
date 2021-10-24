package io.sapl.test.unit.usecase;

import static io.sapl.hamcrest.Matchers.*;
import static io.sapl.test.Imports.whenFunctionParams;
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
			.givenFunction("time.localSecond", whenFunctionParams(val(1)), Val.of(1))
			.givenFunction("time.localSecond", whenFunctionParams(val(2)), Val.of(15))
			.givenFunction("time.localSecond", whenFunctionParams(val(3)), Val.of(25))
			.givenFunction("time.localSecond", whenFunctionParams(val(4)), Val.of(35))
			.givenFunction("time.localSecond", whenFunctionParams(val(5)), Val.of(45))
			.givenFunction("time.localSecond", whenFunctionParams(val(6)), Val.of(55))
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
