package io.sapl.test.mocking;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;

public class FunctionCallImplTest {

	@Test
	void test() {
		FunctionCall call = new FunctionCallImpl(Val.of("foo"));
		
		
		Assertions.assertThat(call.getNumberOfArguments()).isEqualTo(1);
		Assertions.assertThat(call.getArgument(0)).isEqualTo(Val.of("foo"));
		Assertions.assertThat(call.getListOfArguments().size()).isEqualTo(1);
	}
	
	@Test
	void test_invalidIndex() {
		FunctionCall call = new FunctionCallImpl(Val.of("foo"));
		
		
		Assertions.assertThat(call.getNumberOfArguments()).isEqualTo(1);
		Assertions.assertThatExceptionOfType(SaplTestException.class)
			.isThrownBy(() -> call.getArgument(1));
	}
	
	@Test
	void test_modifyParameterList() {
		FunctionCall call = new FunctionCallImpl(Val.of("foo"));
		
		
		Assertions.assertThatExceptionOfType(UnsupportedOperationException.class)
			.isThrownBy(() -> call.getListOfArguments().add(Val.of("barr")));
	}

}
