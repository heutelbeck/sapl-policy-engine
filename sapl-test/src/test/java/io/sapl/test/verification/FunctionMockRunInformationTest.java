package io.sapl.test.verification;

import io.sapl.api.interpreter.Val;
import io.sapl.test.mocking.MockCall;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class FunctionMockRunInformationTest {

	@Test
	void test_initialization() {
		String fullname = "foo";
		
		MockRunInformation mock = new MockRunInformation(fullname);
		
		Assertions.assertThat(mock.getFullname()).isEqualTo(fullname);
		Assertions.assertThat(mock.getTimesCalled()).isEqualTo(0);
		Assertions.assertThat(mock.getCalls()).isNotNull();
	}
	
	@Test
	void test_increase() {
		String fullname = "foo";
		MockCall call = new MockCall(Val.of("foo"));
		MockRunInformation mock = new MockRunInformation(fullname);
		
		mock.saveCall(call);
		
		
		Assertions.assertThat(mock.getTimesCalled()).isEqualTo(1);
		Assertions.assertThat(mock.getCalls().get(0).isUsed()).isFalse();
		Assertions.assertThat(mock.getCalls().get(0).getCall().getNumberOfArguments()).isEqualTo(1);
		Assertions.assertThat(mock.getCalls().get(0).getCall().getArgument(0)).isEqualTo(Val.of("foo"));
	}

}
