package io.sapl.test.mocking;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
public class MockCall {
	
	private static final String ERROR_INVLID_ARGUMENT_INDEX = "Requested index %d for function call parameters but there are only %d parameters. Did you forget to check with \"getNumberOfArguments()\"";

	private Val[] parameter;
	
	public MockCall(Val... parameter) {
		this.parameter = parameter;
	}
	
	public int getNumberOfArguments() {
		return this.parameter.length;
	}

	public Val getArgument(int index) {
		if(index > this.parameter.length - 1) {
			throw new SaplTestException(String.format(ERROR_INVLID_ARGUMENT_INDEX, index, getNumberOfArguments()));
		}
		return this.parameter[index];
	}

	public List<Val> getListOfArguments() {
		return Collections.unmodifiableList(Arrays.asList(this.parameter));
	}
}
