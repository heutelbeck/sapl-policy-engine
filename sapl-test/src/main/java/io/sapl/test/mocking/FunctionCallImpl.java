package io.sapl.test.mocking;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;

public class FunctionCallImpl implements FunctionCall {
	
	private static final String ERROR_INVLID_ARGUMENT_INDEX = "Requested index %d for function call parameters but there are only %d parameters. Did you forget to check with \"getNumberOfArguments()\"";

	private Val[] parameters;
	
	public FunctionCallImpl(Val... parameters) {
		this.parameters = parameters;
	}
	
	@Override
	public int getNumberOfArguments() {
		return this.parameters.length;
	}

	@Override
	public Val getArgument(int index) {
		if(index > this.parameters.length - 1) {
			throw new SaplTestException(String.format(ERROR_INVLID_ARGUMENT_INDEX, index, getNumberOfArguments()));
		}
		return this.parameters[index];
	}

	@Override
	public List<Val> getListOfArguments() {
		return Collections.unmodifiableList(Arrays.asList(this.parameters));
	}

}
