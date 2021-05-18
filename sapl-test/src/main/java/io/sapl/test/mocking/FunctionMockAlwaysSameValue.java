package io.sapl.test.mocking;

import io.sapl.api.interpreter.Val;
import io.sapl.test.verification.MockRunInformation;
import io.sapl.test.verification.TimesCalledVerification;

public class FunctionMockAlwaysSameValue implements FunctionMock {

	private static final String ERROR_DUPLICATE_MOCK_REGISTRATION_ALWAYS_SAME_VALUE = "You already defined a Mock for %s which is always returning a specified value";
	
	private final String fullname;
	private Val alwaysMockReturnValue;
	private TimesCalledVerification timesCalledVerification;
	private final MockRunInformation mockRunInformation;
	
	public FunctionMockAlwaysSameValue(String fullname, Val returnValue, TimesCalledVerification verification) {
		this.fullname = fullname;
		this.alwaysMockReturnValue = returnValue;
		this.timesCalledVerification = verification;
		
		this.mockRunInformation = new MockRunInformation(fullname);
	}
	
	@Override
	public Val evaluateFunctionCall(FunctionCall functionCall) {
		this.mockRunInformation.saveCall(functionCall);
		return this.alwaysMockReturnValue;
	}

	@Override
	public void assertVerifications() {
		this.timesCalledVerification.verify(this.mockRunInformation);
		
	}

	@Override
	public String getErrorMessageForCurrentMode() {
		return String.format(ERROR_DUPLICATE_MOCK_REGISTRATION_ALWAYS_SAME_VALUE, this.fullname);
	}

}
