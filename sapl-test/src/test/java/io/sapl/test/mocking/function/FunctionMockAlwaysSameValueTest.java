package io.sapl.test.mocking.function;

import static io.sapl.test.Imports.times;

import io.sapl.api.interpreter.Val;
import io.sapl.test.mocking.function.FunctionMockAlwaysSameValue;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class FunctionMockAlwaysSameValueTest {

	private Val alwaysReturnValue = Val.of("bar");
	
	@Test
	void test() {
		FunctionMockAlwaysSameValue mock = new FunctionMockAlwaysSameValue("foo", alwaysReturnValue, times(1));
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of(1))).isEqualTo(alwaysReturnValue);
	}
	
	@Test
	void test_multipleTimes() {
		FunctionMockAlwaysSameValue mock = new FunctionMockAlwaysSameValue("foo", alwaysReturnValue, times(3));
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of(1))).isEqualTo(alwaysReturnValue);
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of(2))).isEqualTo(alwaysReturnValue);
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of(3))).isEqualTo(alwaysReturnValue);
		
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
		FunctionMockAlwaysSameValue mock = new FunctionMockAlwaysSameValue("foo", alwaysReturnValue, times(1));
		Assertions.assertThat(mock.getErrorMessageForCurrentMode().isEmpty()).isFalse();
	}

}
