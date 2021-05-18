package io.sapl.test.mocking;

import java.util.List;

import io.sapl.api.interpreter.Val;

public interface FunctionCall {
	int getNumberOfArguments();
	Val getArgument(int index);
	List<Val> getListOfArguments();
}
