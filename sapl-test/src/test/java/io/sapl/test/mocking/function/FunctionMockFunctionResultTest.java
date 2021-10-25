package io.sapl.test.mocking.function;

import static io.sapl.test.Imports.times;

import java.util.function.Function;

import io.sapl.api.interpreter.Val;
import io.sapl.test.mocking.function.FunctionMockFunctionResult;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class FunctionMockFunctionResultTest {

	private Function<Val[], Val> returns = (call) -> {
		Double param0 = call[0].get().asDouble();
		Double param1 = call[1].get().asDouble();
		
		return param0 % param1 == 0 ? Val.of(true) : Val.of(false);
	};
	
	@Test
	void test() {
		FunctionMockFunctionResult mock = new FunctionMockFunctionResult("foo", returns, times(1));
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of(4), Val.of(2))).isEqualTo(Val.of(true));
	}
	
	@Test
	void test_multipleTimes() {
		FunctionMockFunctionResult mock = new FunctionMockFunctionResult("foo", returns, times(3));
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of(4), Val.of(2))).isEqualTo(Val.of(true));
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of(4), Val.of(3))).isEqualTo(Val.of(false));
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of(4), Val.of(4))).isEqualTo(Val.of(true));
		
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
		Assertions.assertThatExceptionOfType(IndexOutOfBoundsException.class)
			.isThrownBy(() -> mock.evaluateFunctionCall(Val.of(4)));
	}
	
	@Test
	void test_invalidNumberParams_TooMuch_Ignored() {
		FunctionMockFunctionResult mock = new FunctionMockFunctionResult("foo", returns, times(1));
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of(4), Val.of(2), Val.of("ignored"))).isEqualTo(Val.of(true));
	}
}
