package io.sapl.test.verification;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.number.OrderingComparison.comparesEqualTo;
import static org.hamcrest.number.OrderingComparison.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.test.mocking.FunctionCallImpl;

public class TimesCalledVerificationTest {

	@Test
	void test_is() {
		MockRunInformation runInfo = new MockRunInformation("foo");
		runInfo.saveCall(new FunctionCallImpl(Val.of("bar")));
		Matcher<Integer> matcher = is(1);
		MockingVerification verification = new TimesCalledVerification(matcher);
		
		boolean isAssertionErrorThrown = false;
		try {
			verification.verify(runInfo);
		} catch(AssertionError e) {
			isAssertionErrorThrown = true;
		}
		
		assertFalse(isAssertionErrorThrown);		
	}
	
	@Test
	void test_comparesEqualTo() {
		MockRunInformation runInfo = new MockRunInformation("foo");
		runInfo.saveCall(new FunctionCallImpl(Val.of("bar")));
		Matcher<Integer> matcher = comparesEqualTo(1);
		MockingVerification verification = new TimesCalledVerification(matcher);
		
		boolean isAssertionErrorThrown = false;
		try {
			verification.verify(runInfo);
		} catch(AssertionError e) {
			isAssertionErrorThrown = true;
		}
		
		assertFalse(isAssertionErrorThrown);
	}
	
	@Test
	void test_comparesEqualTo_multipleCalls() {
		MockRunInformation runInfo = new MockRunInformation("foo");
		runInfo.saveCall(new FunctionCallImpl(Val.of("bar")));
		runInfo.saveCall(new FunctionCallImpl(Val.of("xxx")));
		Matcher<Integer> matcher = comparesEqualTo(2);
		MockingVerification verification = new TimesCalledVerification(matcher);
		
		boolean isAssertionErrorThrown = false;
		try {
			verification.verify(runInfo);
		} catch(AssertionError e) {
			isAssertionErrorThrown = true;
		}
		
		assertFalse(isAssertionErrorThrown);
	}
	
	@Test
	void test_comparesEqualTo_assertionError() {
		MockRunInformation runInfo = new MockRunInformation("foo");
		Matcher<Integer> matcher = comparesEqualTo(1);
		MockingVerification verification = new TimesCalledVerification(matcher);
		
		boolean isAssertionErrorThrown = false;
		try {
			verification.verify(runInfo);
		} catch(AssertionError e) {
			isAssertionErrorThrown = true;
		}
		
		assertTrue(isAssertionErrorThrown);
	}
	
	@Test
	void test_greaterThanOrEqualTo() {
		MockRunInformation runInfo = new MockRunInformation("foo");
		runInfo.saveCall(new FunctionCallImpl(Val.of("bar")));
		Matcher<Integer> matcher = greaterThanOrEqualTo(1);
		MockingVerification verification = new TimesCalledVerification(matcher);
		
		boolean isAssertionErrorThrown = false;
		try {
			verification.verify(runInfo);
		} catch(AssertionError e) {
			isAssertionErrorThrown = true;
		}
		
		assertFalse(isAssertionErrorThrown);
	}
	
	@Test
	void test_greaterThanOrEqualTo_assertionError() {
		MockRunInformation runInfo = new MockRunInformation("foo");
		Matcher<Integer> matcher = greaterThanOrEqualTo(1);
		MockingVerification verification = new TimesCalledVerification(matcher);
		
		boolean isAssertionErrorThrown = false;
		try {
			verification.verify(runInfo);
		} catch(AssertionError e) {
			isAssertionErrorThrown = true;
		}
		
		assertTrue(isAssertionErrorThrown);
	}

}
