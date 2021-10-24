package io.sapl.test.mocking.function;

import static io.sapl.test.Imports.*;
import static org.hamcrest.CoreMatchers.is;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
import io.sapl.test.mocking.function.FunctionMockAlwaysSameForParameters;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class FunctionMockAlwaysSameForParametersTest {

	@Test
	void test() {
		FunctionMockAlwaysSameForParameters mock = new FunctionMockAlwaysSameForParameters("foo");
		mock.loadParameterSpecificReturnValue(Val.of("foo"), whenFunctionParams(is(Val.of(1))), times(1));
		mock.loadParameterSpecificReturnValue(Val.of("bar"), whenFunctionParams(is(Val.of(2))), times(2));
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of(1))).isEqualTo(Val.of("foo"));
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of(2))).isEqualTo(Val.of("bar"));
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of(2))).isEqualTo(Val.of("bar"));
		
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
		mock.loadParameterSpecificReturnValue(Val.of("foo"), whenFunctionParams(is(Val.of(1))), times(1));
		mock.loadParameterSpecificReturnValue(Val.of("bar"), whenFunctionParams(is(Val.of(2))), times(2));
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() -> mock.evaluateFunctionCall(Val.of(1), Val.of("tooMuch")));
	}
	
	@Test
	void test_CallParameters_TooLess() {
		FunctionMockAlwaysSameForParameters mock = new FunctionMockAlwaysSameForParameters("foo");
		mock.loadParameterSpecificReturnValue(Val.of("foo"), whenFunctionParams(is(Val.of(1))), times(1));
		mock.loadParameterSpecificReturnValue(Val.of("bar"), whenFunctionParams(is(Val.of(2))), times(1));
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() -> mock.evaluateFunctionCall());
	}
	
	@Test
	void test_MatchingParameters_TooMuch() {
		FunctionMockAlwaysSameForParameters mock = new FunctionMockAlwaysSameForParameters("foo");
		mock.loadParameterSpecificReturnValue(Val.of("foo"), whenFunctionParams(is(Val.of(1)), is(Val.of("tooMuch"))), times(1));
		mock.loadParameterSpecificReturnValue(Val.of("bar"), whenFunctionParams(is(Val.of(2)), is(Val.of("tooMuch"))), times(1));
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() -> mock.evaluateFunctionCall(Val.of(1)));
	}
	
	@Test
	void test_MatchingParameters_TooLess() {
		FunctionMockAlwaysSameForParameters mock = new FunctionMockAlwaysSameForParameters("foo");
		mock.loadParameterSpecificReturnValue(Val.of("foo"), whenFunctionParams(), times(1));
		mock.loadParameterSpecificReturnValue(Val.of("bar"), whenFunctionParams(), times(2));
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() -> mock.evaluateFunctionCall(Val.of(1)));
	}
	
	
	@Test
	void test_Parameters_NotFound() {
		FunctionMockAlwaysSameForParameters mock = new FunctionMockAlwaysSameForParameters("foo");
		mock.loadParameterSpecificReturnValue(Val.of("foo"), whenFunctionParams(is(Val.of(1))), times(1));
		mock.loadParameterSpecificReturnValue(Val.of("bar"), whenFunctionParams(is(Val.of(2))), times(2));
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() -> mock.evaluateFunctionCall(Val.of(3)));
	}

	@Test
	void test_errorMessage() {

		FunctionMockAlwaysSameForParameters mock = new FunctionMockAlwaysSameForParameters("foo");
		Assertions.assertThat(mock.getErrorMessageForCurrentMode().isEmpty()).isFalse();
	}


}
