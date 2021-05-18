package io.sapl.test.mocking;

import static io.sapl.test.Imports.times;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;

public class FunctionMockAlwaysSameValueTest {

	private Val alwaysReturnValue = Val.of("bar");
	
	@Test
	void test() {
		FunctionMockAlwaysSameValue mock = new FunctionMockAlwaysSameValue("foo", alwaysReturnValue, times(1));
		FunctionCall call = new FunctionCallImpl(Val.of(1));
		Assertions.assertThat(mock.evaluateFunctionCall(call)).isEqualTo(alwaysReturnValue);
	}
	
	@Test
	void test_multipleTimes() {
		FunctionMockAlwaysSameValue mock = new FunctionMockAlwaysSameValue("foo", alwaysReturnValue, times(3));
		FunctionCall call1 = new FunctionCallImpl(Val.of(1));
		Assertions.assertThat(mock.evaluateFunctionCall(call1)).isEqualTo(alwaysReturnValue);
		FunctionCall call2 = new FunctionCallImpl(Val.of(2));
		Assertions.assertThat(mock.evaluateFunctionCall(call2)).isEqualTo(alwaysReturnValue);
		FunctionCall call3 = new FunctionCallImpl(Val.of(3));
		Assertions.assertThat(mock.evaluateFunctionCall(call3)).isEqualTo(alwaysReturnValue);
		
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
