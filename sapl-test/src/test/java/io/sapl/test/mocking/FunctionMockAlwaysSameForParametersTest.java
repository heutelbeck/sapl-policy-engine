package io.sapl.test.mocking;

import static io.sapl.test.Imports.times;
import static io.sapl.test.Imports.whenParameters;
import static org.hamcrest.CoreMatchers.is;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;

public class FunctionMockAlwaysSameForParametersTest {

	@Test
	void test() {
		FunctionMockAlwaysSameForParameters mock = new FunctionMockAlwaysSameForParameters("foo");
		mock.loadParameterSpecificReturnValue(Val.of("foo"), whenParameters(is(Val.of(1))), times(1));
		mock.loadParameterSpecificReturnValue(Val.of("bar"), whenParameters(is(Val.of(2))), times(2));
		FunctionCall call1 = new FunctionCallSimple(Val.of(1));
		Assertions.assertThat(mock.evaluateFunctionCall(call1)).isEqualTo(Val.of("foo"));
		FunctionCall call2 = new FunctionCallSimple(Val.of(2));
		Assertions.assertThat(mock.evaluateFunctionCall(call2)).isEqualTo(Val.of("bar"));
		FunctionCall call3 = new FunctionCallSimple(Val.of(2));
		Assertions.assertThat(mock.evaluateFunctionCall(call3)).isEqualTo(Val.of("bar"));
		
		boolean isAssertionErrorThrown = false;
		try {
			mock.assertVerifications();			
		} catch(AssertionError e) {
			isAssertionErrorThrown = true;
		}
		
		Assertions.assertThat(isAssertionErrorThrown).isFalse();
	}
	
	@Test
	void test_CallParameters_TooMuch() {
		FunctionMockAlwaysSameForParameters mock = new FunctionMockAlwaysSameForParameters("foo");
		mock.loadParameterSpecificReturnValue(Val.of("foo"), whenParameters(is(Val.of(1))), times(1));
		mock.loadParameterSpecificReturnValue(Val.of("bar"), whenParameters(is(Val.of(2))), times(2));
		FunctionCall call1 = new FunctionCallSimple(Val.of(1), Val.of("tooMuch"));
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() -> mock.evaluateFunctionCall(call1));
	}
	
	@Test
	void test_CallParameters_TooLess() {
		FunctionMockAlwaysSameForParameters mock = new FunctionMockAlwaysSameForParameters("foo");
		mock.loadParameterSpecificReturnValue(Val.of("foo"), whenParameters(is(Val.of(1))), times(1));
		mock.loadParameterSpecificReturnValue(Val.of("bar"), whenParameters(is(Val.of(2))), times(1));
		FunctionCall call1 = new FunctionCallSimple();
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() -> mock.evaluateFunctionCall(call1));
	}
	
	@Test
	void test_MatchingParameters_TooMuch() {
		FunctionMockAlwaysSameForParameters mock = new FunctionMockAlwaysSameForParameters("foo");
		mock.loadParameterSpecificReturnValue(Val.of("foo"), whenParameters(is(Val.of(1)), is(Val.of("tooMuch"))), times(1));
		mock.loadParameterSpecificReturnValue(Val.of("bar"), whenParameters(is(Val.of(2)), is(Val.of("tooMuch"))), times(1));
		FunctionCall call1 = new FunctionCallSimple(Val.of(1));
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() -> mock.evaluateFunctionCall(call1));
	}
	
	@Test
	void test_MatchingParameters_TooLess() {
		FunctionMockAlwaysSameForParameters mock = new FunctionMockAlwaysSameForParameters("foo");
		mock.loadParameterSpecificReturnValue(Val.of("foo"), whenParameters(), times(1));
		mock.loadParameterSpecificReturnValue(Val.of("bar"), whenParameters(), times(2));
		FunctionCall call1 = new FunctionCallSimple(Val.of(1));
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() -> mock.evaluateFunctionCall(call1));
	}
	
	
	@Test
	void test_Parameters_NotFound() {
		FunctionMockAlwaysSameForParameters mock = new FunctionMockAlwaysSameForParameters("foo");
		mock.loadParameterSpecificReturnValue(Val.of("foo"), whenParameters(is(Val.of(1))), times(1));
		mock.loadParameterSpecificReturnValue(Val.of("bar"), whenParameters(is(Val.of(2))), times(2));
		FunctionCall call1 = new FunctionCallSimple(Val.of(3));
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() -> mock.evaluateFunctionCall(call1));
	}

	@Test
	void test_errorMessage() {

		FunctionMockAlwaysSameForParameters mock = new FunctionMockAlwaysSameForParameters("foo");
		Assertions.assertThat(mock.getErrorMessageForCurrentMode().isEmpty()).isFalse();
	}


}
