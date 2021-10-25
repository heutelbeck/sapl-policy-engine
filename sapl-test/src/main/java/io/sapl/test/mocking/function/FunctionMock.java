package io.sapl.test.mocking.function;

import io.sapl.api.interpreter.Val;

public interface FunctionMock {
	Val evaluateFunctionCall(Val... parameter);
	
	void assertVerifications();
	
	String getErrorMessageForCurrentMode();
}
