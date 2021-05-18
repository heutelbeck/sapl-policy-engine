package io.sapl.test.mocking;

import io.sapl.api.interpreter.Val;

public interface FunctionMock {
	/*
	void loadFunctionMockAlwaysSameValue(Val mockReturnValue, TimesCalledVerification verification);
	
	void loadFunctionMockOnceReturnValue(Val mockReturnValue);

	void loadFunctionMockReturnsSequence(Val[] mockReturnValue);
	
	void loadFunctionMockAlwaysSameValueForParameters(Val mockReturnValue, FunctionParameters parameter, TimesCalledVerification verification);
	
	void loadFunctionAlwaysSameValueForParameters(Function<FunctionCall, Val> returns, TimesCalledVerification verification);
	*/
	
	
	
	Val evaluateFunctionCall(FunctionCall functionCall);
	
	void assertVerifications();
	
	String getErrorMessageForCurrentMode();
}
