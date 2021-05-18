package io.sapl.test.mocking;

import java.util.function.Function;

import io.sapl.api.interpreter.Val;
import io.sapl.test.verification.MockRunInformation;
import io.sapl.test.verification.TimesCalledVerification;

public class FunctionMockFunctionResult implements FunctionMock {

	private static final String ERROR_DUPLICATE_MOCK_REGISTRATION_FUNCTION = "You already defined a Mock for %s which is always returning a specified value from your lambda-Expression";
		
	private final String fullname;
	Function<FunctionCall, Val> returnValue;
	private TimesCalledVerification timesCalledVerification;
	private final MockRunInformation mockRunInformation;
	
	public FunctionMockFunctionResult(String fullname, Function<FunctionCall, Val> returns, TimesCalledVerification verification) {
		this.fullname = fullname;
		this.returnValue = returns;
		this.timesCalledVerification = verification;
		
		this.mockRunInformation = new MockRunInformation(fullname);
	}
	
	@Override
	public Val evaluateFunctionCall(FunctionCall functionCall) {
		this.mockRunInformation.saveCall(functionCall);
		return this.returnValue.apply(functionCall);
	}

	@Override
	public void assertVerifications() {
		this.timesCalledVerification.verify(this.mockRunInformation);
		
	}

	@Override
	public String getErrorMessageForCurrentMode() {
		return String.format(ERROR_DUPLICATE_MOCK_REGISTRATION_FUNCTION, this.fullname);
	}


}
