package io.sapl.test.verification;

import static io.sapl.hamcrest.Matchers.anyVal;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.number.OrderingComparison.comparesEqualTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedList;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
import io.sapl.test.mocking.FunctionCallSimple;

public class TimesParameterCalledVerificationTest {

	@Test
	void test() {
		MockRunInformation runInfo = new MockRunInformation("foo");
		runInfo.saveCall(new FunctionCallSimple(Val.of("bar"), Val.of(1)));
		runInfo.saveCall(new FunctionCallSimple(Val.of("xxx"), Val.of(2)));
		runInfo.saveCall(new FunctionCallSimple(Val.of("yyy"), Val.of(3)));
		runInfo.saveCall(new FunctionCallSimple(Val.of("xxx"), Val.of(2)));
		
		Matcher<Integer> matcher = comparesEqualTo(2);
		List<Matcher<Val>> expectedParameters = List.of(is(Val.of("xxx")), is(Val.of(2)));
		MockingVerification verification = new TimesParameterCalledVerification(
				new TimesCalledVerification(matcher), 
				expectedParameters
				);
		
		boolean isAssertionErrorThrown = false;
		try {
			verification.verify(runInfo);
		} catch(AssertionError e) {
			isAssertionErrorThrown = true;
		}
		
		assertFalse(isAssertionErrorThrown);
		Assertions.assertThat(runInfo.getCalls().size()).isEqualTo(4);
		Assertions.assertThat(runInfo.getCalls().get(0).isUsed()).isFalse();
		Assertions.assertThat(runInfo.getCalls().get(1).isUsed()).isTrue();
		Assertions.assertThat(runInfo.getCalls().get(2).isUsed()).isFalse();
		Assertions.assertThat(runInfo.getCalls().get(3).isUsed()).isTrue();
	}
	
	@Test
	void test_assertionError() {
		MockRunInformation runInfo = new MockRunInformation("foo");
		runInfo.saveCall(new FunctionCallSimple(Val.of("bar"), Val.of(1)));
		runInfo.saveCall(new FunctionCallSimple(Val.of("xxx"), Val.of(2)));
		runInfo.saveCall(new FunctionCallSimple(Val.of("yyy"), Val.of(3)));
		runInfo.saveCall(new FunctionCallSimple(Val.of("xxx"), Val.of(3)));
		
		Matcher<Integer> matcher = comparesEqualTo(2);
		
		List<Matcher<Val>> expectedParameters = new LinkedList<>();
		expectedParameters.add(is(Val.of("xxx")));
		expectedParameters.add(is(Val.of(2)));
		MockingVerification verification = new TimesParameterCalledVerification(
				new TimesCalledVerification(matcher), 
				expectedParameters
				);
		
		boolean isAssertionErrorThrown = false;
		try {
			verification.verify(runInfo);
		} catch(AssertionError e) {
			isAssertionErrorThrown = true;
		}
		
		assertTrue(isAssertionErrorThrown);
		Assertions.assertThat(runInfo.getCalls().size()).isEqualTo(4);
		Assertions.assertThat(runInfo.getCalls().get(0).isUsed()).isFalse();
		Assertions.assertThat(runInfo.getCalls().get(1).isUsed()).isTrue();
		Assertions.assertThat(runInfo.getCalls().get(2).isUsed()).isFalse();
		Assertions.assertThat(runInfo.getCalls().get(3).isUsed()).isFalse();
	}
	
	@Test
	void test_assertionError_tooOftenCalled() {
		MockRunInformation runInfo = new MockRunInformation("foo");
		runInfo.saveCall(new FunctionCallSimple(Val.of("bar"), Val.of(1)));
		runInfo.saveCall(new FunctionCallSimple(Val.of("xxx"), Val.of(2)));
		runInfo.saveCall(new FunctionCallSimple(Val.of("xxx"), Val.of(3)));
		runInfo.saveCall(new FunctionCallSimple(Val.of("xxx"), Val.of(3)));
		
		Matcher<Integer> matcher = comparesEqualTo(2);
		
		List<Matcher<Val>> expectedParameters = new LinkedList<>();
		expectedParameters.add(is(Val.of("xxx")));
		expectedParameters.add(anyVal());
		MockingVerification verification = new TimesParameterCalledVerification(
				new TimesCalledVerification(matcher), 
				expectedParameters
				);
		
		boolean isAssertionErrorThrown = false;
		try {
			verification.verify(runInfo);
		} catch(AssertionError e) {
			isAssertionErrorThrown = true;
		}
		
		assertTrue(isAssertionErrorThrown);
		Assertions.assertThat(runInfo.getCalls().size()).isEqualTo(4);
		Assertions.assertThat(runInfo.getCalls().get(0).isUsed()).isFalse();
		Assertions.assertThat(runInfo.getCalls().get(1).isUsed()).isTrue();
		Assertions.assertThat(runInfo.getCalls().get(2).isUsed()).isTrue();
		Assertions.assertThat(runInfo.getCalls().get(3).isUsed()).isTrue();
	}
	
	@Test
	void test_MultipleParameterTimesVerifications_WithAnyMatcher_OrderingMatters() {
		MockRunInformation runInfo = new MockRunInformation("foo");
		runInfo.saveCall(new FunctionCallSimple(Val.of("bar"), Val.of(1)));
		runInfo.saveCall(new FunctionCallSimple(Val.of("xxx"), Val.of(2)));
		runInfo.saveCall(new FunctionCallSimple(Val.of("yyy"), Val.of(3)));
		runInfo.saveCall(new FunctionCallSimple(Val.of("xxx"), Val.of(3)));
		
		Matcher<Integer> matcher = comparesEqualTo(1);
		
		List<Matcher<Val>> expectedParameters = List.of(is(Val.of("xxx")), is(Val.of(2)));
		MockingVerification verification = new TimesParameterCalledVerification(
				new TimesCalledVerification(matcher), 
				expectedParameters
				);
		
		boolean isAssertionErrorThrown = false;
		try {
			verification.verify(runInfo);
		} catch(AssertionError e) {
			isAssertionErrorThrown = true;
		}
		
		assertFalse(isAssertionErrorThrown);
		Assertions.assertThat(runInfo.getCalls().size()).isEqualTo(4);
		Assertions.assertThat(runInfo.getCalls().get(0).isUsed()).isFalse();
		Assertions.assertThat(runInfo.getCalls().get(1).isUsed()).isTrue();
		Assertions.assertThat(runInfo.getCalls().get(2).isUsed()).isFalse();
		Assertions.assertThat(runInfo.getCalls().get(3).isUsed()).isFalse();
		

		Matcher<Integer> matcher2 = comparesEqualTo(1);
		List<Matcher<Val>> expectedParameters2 = List.of(is(Val.of("xxx")), anyVal());
		MockingVerification verification2 = new TimesParameterCalledVerification(
				new TimesCalledVerification(matcher2), 
				expectedParameters2
				);
		
		boolean isAssertionErrorThrown2 = false;
		try {
			verification2.verify(runInfo);
		} catch(AssertionError e) {
			isAssertionErrorThrown2 = true;
		}
		
		assertFalse(isAssertionErrorThrown2);
		Assertions.assertThat(runInfo.getCalls().size()).isEqualTo(4);
		Assertions.assertThat(runInfo.getCalls().get(0).isUsed()).isFalse();
		Assertions.assertThat(runInfo.getCalls().get(1).isUsed()).isTrue();
		Assertions.assertThat(runInfo.getCalls().get(2).isUsed()).isFalse();
		Assertions.assertThat(runInfo.getCalls().get(3).isUsed()).isTrue();
	}
	
	@Test
	void test_assertionError_verificationMessage() {
		MockRunInformation runInfo = new MockRunInformation("foo");
		runInfo.saveCall(new FunctionCallSimple(Val.of("bar"), Val.of(1)));
		runInfo.saveCall(new FunctionCallSimple(Val.of("xxx"), Val.of(2)));
		runInfo.saveCall(new FunctionCallSimple(Val.of("yyy"), Val.of(3)));
		runInfo.saveCall(new FunctionCallSimple(Val.of("xxx"), Val.of(3)));
		
		Matcher<Integer> matcher = comparesEqualTo(2);
		
		List<Matcher<Val>> expectedParameters = new LinkedList<>();
		expectedParameters.add(is(Val.of("xxx")));
		expectedParameters.add(is(Val.of(2)));
		MockingVerification verification = new TimesParameterCalledVerification(
				new TimesCalledVerification(matcher), 
				expectedParameters
				);
		
		
		boolean isAssertionErrorThrown = false;
		AssertionError error = null;
		try {
			verification.verify(runInfo, "VerificationMessage");
		} catch(AssertionError e) {
			error = e;
			isAssertionErrorThrown = true;
		}
		
		assertTrue(isAssertionErrorThrown);
		Assertions.assertThat(runInfo.getCalls().size()).isEqualTo(4);
		Assertions.assertThat(runInfo.getCalls().get(0).isUsed()).isFalse();
		Assertions.assertThat(runInfo.getCalls().get(1).isUsed()).isTrue();
		Assertions.assertThat(runInfo.getCalls().get(2).isUsed()).isFalse();
		Assertions.assertThat(runInfo.getCalls().get(3).isUsed()).isFalse();
		Assertions.assertThat(error.getMessage()).contains("VerificationMessage");
	}
	

	@Test
	void test_assertionError_VerificationMessage_Empty() {
		MockRunInformation runInfo = new MockRunInformation("foo");
		runInfo.saveCall(new FunctionCallSimple(Val.of("bar"), Val.of(1)));
		runInfo.saveCall(new FunctionCallSimple(Val.of("xxx"), Val.of(2)));
		runInfo.saveCall(new FunctionCallSimple(Val.of("yyy"), Val.of(3)));
		runInfo.saveCall(new FunctionCallSimple(Val.of("xxx"), Val.of(3)));
		
		Matcher<Integer> matcher = comparesEqualTo(2);
		
		List<Matcher<Val>> expectedParameters = new LinkedList<>();
		expectedParameters.add(is(Val.of("xxx")));
		expectedParameters.add(is(Val.of(2)));
		MockingVerification verification = new TimesParameterCalledVerification(
				new TimesCalledVerification(matcher), 
				expectedParameters
				);
		

		boolean isAssertionErrorThrown = false;
		AssertionError error = null;
		try {
			verification.verify(runInfo, "");
		} catch(AssertionError e) {
			error = e;
			isAssertionErrorThrown = true;
		}
		
		assertTrue(isAssertionErrorThrown);
		Assertions.assertThat(runInfo.getCalls().size()).isEqualTo(4);
		Assertions.assertThat(runInfo.getCalls().get(0).isUsed()).isFalse();
		Assertions.assertThat(runInfo.getCalls().get(1).isUsed()).isTrue();
		Assertions.assertThat(runInfo.getCalls().get(2).isUsed()).isFalse();
		Assertions.assertThat(runInfo.getCalls().get(3).isUsed()).isFalse();
		Assertions.assertThat(error.getMessage()).isNotEmpty();
	}
	
	
	@Test
	void test_assertionError_VerificationMessage_Null() {
		MockRunInformation runInfo = new MockRunInformation("foo");
		runInfo.saveCall(new FunctionCallSimple(Val.of("bar"), Val.of(1)));
		runInfo.saveCall(new FunctionCallSimple(Val.of("xxx"), Val.of(2)));
		runInfo.saveCall(new FunctionCallSimple(Val.of("yyy"), Val.of(3)));
		runInfo.saveCall(new FunctionCallSimple(Val.of("xxx"), Val.of(3)));
		
		Matcher<Integer> matcher = comparesEqualTo(2);
		
		List<Matcher<Val>> expectedParameters = new LinkedList<>();
		expectedParameters.add(is(Val.of("xxx")));
		expectedParameters.add(is(Val.of(2)));
		MockingVerification verification = new TimesParameterCalledVerification(
				new TimesCalledVerification(matcher), 
				expectedParameters
				);
		

		boolean isAssertionErrorThrown = false;
		AssertionError error = null;
		try {
			verification.verify(runInfo, null);
		} catch(AssertionError e) {
			error = e;
			isAssertionErrorThrown = true;
		}
		
		assertTrue(isAssertionErrorThrown);
		Assertions.assertThat(runInfo.getCalls().size()).isEqualTo(4);
		Assertions.assertThat(runInfo.getCalls().get(0).isUsed()).isFalse();
		Assertions.assertThat(runInfo.getCalls().get(1).isUsed()).isTrue();
		Assertions.assertThat(runInfo.getCalls().get(2).isUsed()).isFalse();
		Assertions.assertThat(runInfo.getCalls().get(3).isUsed()).isFalse();
		Assertions.assertThat(error.getMessage()).isNotEmpty();
	}
	
	@Test
	void test_Exception_CountOfExpectedParamterNotEqualsFunctionCallParametersCount() {
		MockRunInformation runInfo = new MockRunInformation("foo");
		runInfo.saveCall(new FunctionCallSimple(Val.of("bar"), Val.of(1)));
		runInfo.saveCall(new FunctionCallSimple(Val.of("xxx"), Val.of(2)));
		runInfo.saveCall(new FunctionCallSimple(Val.of("yyy"), Val.of(3)));
		runInfo.saveCall(new FunctionCallSimple(Val.of("xxx"), Val.of(3)));
		
		Matcher<Integer> matcher = comparesEqualTo(2);
		
		List<Matcher<Val>> expectedParameters = new LinkedList<>();
		expectedParameters.add(is(Val.of("xxx")));
		MockingVerification verification = new TimesParameterCalledVerification(
				new TimesCalledVerification(matcher), 
				expectedParameters
				);
		

		Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> verification.verify(runInfo));
	}
	
	
}
