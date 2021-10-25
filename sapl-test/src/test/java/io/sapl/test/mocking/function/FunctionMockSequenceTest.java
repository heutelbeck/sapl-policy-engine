package io.sapl.test.mocking.function;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
import io.sapl.test.mocking.function.FunctionMockSequence;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class FunctionMockSequenceTest {

	private Val[] seq = new Val[] {Val.of(1), Val.of(2), Val.of(3)}; 
	
	@Test
	void test() {
		FunctionMockSequence mock = new FunctionMockSequence("foo");
		mock.loadMockReturnValue(seq);
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of("do"))).isEqualTo(seq[0]);
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of("not"))).isEqualTo(seq[1]);
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of("matter"))).isEqualTo(seq[2]);
	}
	
	@Test
	void test_tooMuchCalls() {
		FunctionMockSequence mock = new FunctionMockSequence("foo");
		mock.loadMockReturnValue(seq);
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of("do"))).isEqualTo(seq[0]);
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of("not"))).isEqualTo(seq[1]);
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of("matter"))).isEqualTo(seq[2]);
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() -> mock.evaluateFunctionCall(Val.of("returnValueUndefined")));
	}
	
	@Test
	void test_tooLessCalls() {
		FunctionMockSequence mock = new FunctionMockSequence("foo");
		mock.loadMockReturnValue(seq);
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of("do"))).isEqualTo(seq[0]);
		Assertions.assertThat(mock.evaluateFunctionCall(Val.of("not"))).isEqualTo(seq[1]);
		
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
