package io.sapl.test.mocking;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;

public class FunctionMockSequenceTest {

	private Val[] seq = new Val[] {Val.of(1), Val.of(2), Val.of(3)}; 
	
	@Test
	void test() {
		FunctionMockSequence mock = new FunctionMockSequence("foo");
		mock.loadMockReturnValue(seq);
		FunctionCall call1 = new FunctionCallImpl(Val.of("do"));
		FunctionCall call2 = new FunctionCallImpl(Val.of("not"));
		FunctionCall call3 = new FunctionCallImpl(Val.of("matter"));
		Assertions.assertThat(mock.evaluateFunctionCall(call1)).isEqualTo(seq[0]);
		Assertions.assertThat(mock.evaluateFunctionCall(call2)).isEqualTo(seq[1]);
		Assertions.assertThat(mock.evaluateFunctionCall(call3)).isEqualTo(seq[2]);
	}
	
	@Test
	void test_tooMuchCalls() {
		FunctionMockSequence mock = new FunctionMockSequence("foo");
		mock.loadMockReturnValue(seq);
		FunctionCall call1 = new FunctionCallImpl(Val.of("do"));
		FunctionCall call2 = new FunctionCallImpl(Val.of("not"));
		FunctionCall call3 = new FunctionCallImpl(Val.of("matter"));
		FunctionCall call4 = new FunctionCallImpl(Val.of("returnValueUndefined"));
		Assertions.assertThat(mock.evaluateFunctionCall(call1)).isEqualTo(seq[0]);
		Assertions.assertThat(mock.evaluateFunctionCall(call2)).isEqualTo(seq[1]);
		Assertions.assertThat(mock.evaluateFunctionCall(call3)).isEqualTo(seq[2]);
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() -> mock.evaluateFunctionCall(call4));
	}
	
	@Test
	void test_tooLessCalls() {
		FunctionMockSequence mock = new FunctionMockSequence("foo");
		mock.loadMockReturnValue(seq);
		FunctionCall call1 = new FunctionCallImpl(Val.of("do"));
		FunctionCall call2 = new FunctionCallImpl(Val.of("not"));
		Assertions.assertThat(mock.evaluateFunctionCall(call1)).isEqualTo(seq[0]);
		Assertions.assertThat(mock.evaluateFunctionCall(call2)).isEqualTo(seq[1]);
		
		boolean isAssertionErrorThrown = false;
		try {
			mock.assertVerifications();			
		} catch(AssertionError e) {
			isAssertionErrorThrown = true;
		}
		
		Assertions.assertThat(isAssertionErrorThrown).isTrue();
	}
	
	@Test
	void test_errorMessage() {
		FunctionMockSequence mock = new FunctionMockSequence("foo");
		Assertions.assertThat(mock.getErrorMessageForCurrentMode().isEmpty()).isFalse();
	}

}
