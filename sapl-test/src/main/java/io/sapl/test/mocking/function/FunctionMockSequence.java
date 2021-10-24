package io.sapl.test.mocking.function;

import static io.sapl.test.Imports.times;

import java.util.Arrays;
import java.util.LinkedList;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
import io.sapl.test.mocking.MockCall;
import io.sapl.test.verification.MockRunInformation;

public class FunctionMockSequence implements FunctionMock {
	private static final String ERROR_DUPLICATE_MOCK_REGISTRATION_SEQUENCE = "You already defined a Mock for %s which is returning a sequence of values";
	private static final String ERROR_SEQUENCE_EMPTY = "You defined a Mock for %s returning a sequence of Val's but was called more often than mock return values specified";
	
	private final String fullname;
	private LinkedList<Val> listMockReturnValues;
	private final MockRunInformation mockRunInformation;
	
	private int numberOfReturnValues = 0;
	
	public FunctionMockSequence(String fullname) {
		this.fullname = fullname;
		this.listMockReturnValues = new LinkedList<>();
		
		this.mockRunInformation = new MockRunInformation(fullname);
	}
	
	@Override
	public Val evaluateFunctionCall(Val... parameter) {
		this.mockRunInformation.saveCall(new MockCall(parameter));
		
		if(this.listMockReturnValues.size() > 0) {
			//if so, take the first element from the fifo list and return this val
			return this.listMockReturnValues.removeFirst();
		} else {
			throw new SaplTestException(String.format(ERROR_SEQUENCE_EMPTY, this.fullname));
		}
	}

	@Override
	public void assertVerifications() {
		times(this.numberOfReturnValues).verify(this.mockRunInformation);
	}
	
	public void loadMockReturnValue(Val[] mockReturnValueSequence) {
		this.listMockReturnValues.addAll(Arrays.asList(mockReturnValueSequence));
		this.numberOfReturnValues = this.numberOfReturnValues + mockReturnValueSequence.length;
	}

	@Override
	public String getErrorMessageForCurrentMode() {
		return String.format(ERROR_DUPLICATE_MOCK_REGISTRATION_SEQUENCE, this.fullname);
	}
}
