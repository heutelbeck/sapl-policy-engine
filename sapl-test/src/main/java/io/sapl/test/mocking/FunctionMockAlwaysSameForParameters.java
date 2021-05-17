package io.sapl.test.mocking;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.hamcrest.Matcher;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
import io.sapl.test.verification.MockRunInformation;
import io.sapl.test.verification.TimesCalledVerification;
import io.sapl.test.verification.TimesParameterCalledVerification;

public class FunctionMockAlwaysSameForParameters implements FunctionMock {
	private static final String ERROR_DUPLICATE_MOCK_REGISTRATION_PARAMETERS = "You already defined a Mock for %s which is always returning a specified value depending on parameters";
	private static final String ERROR_INVALID_NUMBER_PARAMETERS = "Test case has configured mocked function \"%s\" return value depending on %d parameters, but is called at runtime with %d parameters";
	private static final String ERROR_NO_MATCHING_PARAMETERS = "Unable to find a mocked return value for this parameter combination";
	
	private final String fullname;
	private List<ParameterSpecificMockReturnValue> listParameterSpecificMockReturnValues;
	private final MockRunInformation mockRunInformation;
	private final List<TimesParameterCalledVerification> listMockingVerifications;
	
	public FunctionMockAlwaysSameForParameters(String fullname) {
		this.fullname = fullname;
		this.listParameterSpecificMockReturnValues = new LinkedList<>();
		
		this.listMockingVerifications = new LinkedList<>();
		this.mockRunInformation = new MockRunInformation(fullname);
	}
	
	@Override
	public Val evaluateFunctionCall(FunctionCall functionCall) {
		this.mockRunInformation.saveCall(functionCall);
		
		Optional<ParameterSpecificMockReturnValue> matchingParameterSpecificMockReturnValue = findMatchingParameterSpecificMockReturnValue(functionCall);
		if(matchingParameterSpecificMockReturnValue.isPresent()) {
			return matchingParameterSpecificMockReturnValue.get().getAlwaysMockReturnValue();
		} else {
			throw new SaplTestException(ERROR_NO_MATCHING_PARAMETERS);
		}
	}

	@Override
	public void assertVerifications() {
		this.listMockingVerifications.stream().forEach((verification) -> verification.verify(this.mockRunInformation));
	}
	
	public void loadParameterSpecificReturnValue(Val mockReturnValue, FunctionParameters parameter, TimesCalledVerification verification) {
		this.listParameterSpecificMockReturnValues.add(new ParameterSpecificMockReturnValue(parameter.getParameterMatchers(), mockReturnValue));
		
		this.listMockingVerifications.add(new TimesParameterCalledVerification(verification, parameter.getParameterMatchers()));
	}
	
	private Optional<ParameterSpecificMockReturnValue> findMatchingParameterSpecificMockReturnValue(FunctionCall call) {
		return this.listParameterSpecificMockReturnValues.stream().filter((ParameterSpecificMockReturnValue mock) -> {
			//check number of matchers are equal to number of parameters of this function call
			if(mock.getMatchers().size() != call.getNumberOfArguments()) {
				throw new SaplTestException(String.format(ERROR_INVALID_NUMBER_PARAMETERS, this.fullname, mock.getMatchers().size(), call.getNumberOfArguments()));
			}
			
			//check if one of the parameter specific mock values is applicable to this function call			
			boolean functionCallMatchesSpecifiedParameters = true;
			for (int i = 0; i < mock.getMatchers().size(); i++) {
				if(!mock.getMatchers().get(i).matches(call.getArgument(i))) {
					functionCallMatchesSpecifiedParameters = false;
				}
			}
			return functionCallMatchesSpecifiedParameters;
		}).findFirst();
	}
	

	@Override
	public String getErrorMessageForCurrentMode() {
		return String.format(ERROR_DUPLICATE_MOCK_REGISTRATION_PARAMETERS, this.fullname);
	}

	
	static class ParameterSpecificMockReturnValue {
		private List<Matcher<Val>> matchers;
		private Val alwaysMockReturnValue;
		
		ParameterSpecificMockReturnValue(List<Matcher<Val>> matchers, Val alwaysMockReturnValue) {
			this.matchers = matchers;
			this.alwaysMockReturnValue = alwaysMockReturnValue;
		}

		private List<Matcher<Val>> getMatchers() {
			return matchers;
		}
		
		private Val getAlwaysMockReturnValue() {
			return alwaysMockReturnValue;
		}
	}
}
