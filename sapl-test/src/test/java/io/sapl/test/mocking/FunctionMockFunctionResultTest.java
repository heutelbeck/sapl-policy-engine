package io.sapl.test.mocking;

import static io.sapl.test.Imports.times;

import java.util.function.Function;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;

public class FunctionMockFunctionResultTest {

	private Function<FunctionCall, Val> returns = (call) -> {
		Double param0 = call.getArgument(0).get().asDouble();
		Double param1 = call.getArgument(1).get().asDouble();
		
		return param0 % param1 == 0 ? Val.of(true) : Val.of(false);
	};
	
	@Test
	void test() {
		FunctionMockFunctionResult mock = new FunctionMockFunctionResult("foo", returns, times(1));
		FunctionCall call = new FunctionCallImpl(Val.of(4), Val.of(2));
		Assertions.assertThat(mock.evaluateFunctionCall(call)).isEqualTo(Val.of(true));
	}
	
	@Test
	void test_multipleTimes() {
		FunctionMockFunctionResult mock = new FunctionMockFunctionResult("foo", returns, times(3));
		FunctionCall call1 = new FunctionCallImpl(Val.of(4), Val.of(2));
		Assertions.assertThat(mock.evaluateFunctionCall(call1)).isEqualTo(Val.of(true));
		FunctionCall call2 = new FunctionCallImpl(Val.of(4), Val.of(3));
		Assertions.assertThat(mock.evaluateFunctionCall(call2)).isEqualTo(Val.of(false));
		FunctionCall call3 = new FunctionCallImpl(Val.of(4), Val.of(4));
		Assertions.assertThat(mock.evaluateFunctionCall(call3)).isEqualTo(Val.of(true));
		
		boolean isAssertionErrorThrown = false;
		try {
			mock.assertVerifications();			
		} catch(AssertionError e) {
			isAssertionErrorThrown = true;
		}
		
		Assertions.assertThat(isAssertionErrorThrown).isFalse();
	}
	
	@Test
	void test_errorMessage() {
		FunctionMockFunctionResult mock = new FunctionMockFunctionResult("foo", returns, times(1));
		Assertions.assertThat(mock.getErrorMessageForCurrentMode().isEmpty()).isFalse();
	}
	
	@Test
	void test_invalidNumberParams_TooLess_Exception() {
		FunctionMockFunctionResult mock = new FunctionMockFunctionResult("foo", returns, times(1));
		FunctionCall call = new FunctionCallImpl(Val.of(4));
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() -> mock.evaluateFunctionCall(call));
	}
	
	@Test
	void test_invalidNumberParams_TooMuch_Ignored() {
		FunctionMockFunctionResult mock = new FunctionMockFunctionResult("foo", returns, times(1));
		FunctionCall call = new FunctionCallImpl(Val.of(4), Val.of(2), Val.of("ignored"));
		Assertions.assertThat(mock.evaluateFunctionCall(call)).isEqualTo(Val.of(true));
	}
}
