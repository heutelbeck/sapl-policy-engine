package io.sapl.test.mocking.attribute;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.sapl.api.interpreter.Val;
import io.sapl.test.Imports;
import io.sapl.test.SaplTestException;
import io.sapl.test.mocking.MockCall;
import io.sapl.test.mocking.attribute.models.AttributeParameters;
import io.sapl.test.verification.MockRunInformation;
import io.sapl.test.verification.TimesParameterCalledVerification;

import org.hamcrest.Matcher;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import reactor.core.publisher.Flux;

public class AttributeMockForParentValueAndArguments implements AttributeMock {
	private static final String ERROR_DUPLICATE_MOCK_REGISTRATION_FOR_PARAMETERS = "You already defined a Mock for %s which is returning specified values when parameters are matching the expectation";
	private static final String ERROR_NO_MATCHING_ARGUMENTS = "Unable to find a mocked return value for this argument combination";
	private static final String ERROR_INVALID_NUMBER_PARAMETERS = "Test case has configured mocked attribute \"%s\" return value depending on %d parameters, but is called at runtime with %d parameters";
	private static final String ERROR_NO_MATCHING_PARENTVALUE = "Unable to find a mocked return value for this parent value";
	
	private final String fullname;
	private List<ParameterSpecificMockReturnValue> listParameterSpecificMockReturnValues;
	private final MockRunInformation mockRunInformation;
	private final List<TimesParameterCalledVerification> listMockingVerifications;

	public AttributeMockForParentValueAndArguments(String fullname) {
		this.fullname = fullname;
		this.listParameterSpecificMockReturnValues = new LinkedList<>();
		this.listMockingVerifications = new LinkedList<>();
		this.mockRunInformation = new MockRunInformation(fullname);
	}

	public void loadMockForParentValueAndArguments(AttributeParameters parameters, Val returnValue) {	
		this.listParameterSpecificMockReturnValues.add(new ParameterSpecificMockReturnValue(parameters, returnValue));
		
		List<Matcher<Val>> listOfAllMatcher = new LinkedList<>();
		listOfAllMatcher.add(parameters.getParentValueMatcher().getMatcher());
		listOfAllMatcher.addAll(List.of(parameters.getArgumentMatchers().getMatchers()));
		this.listMockingVerifications.add(
				new TimesParameterCalledVerification(
						Imports.times(1), 
						listOfAllMatcher
						)
				);

	}
	
	@Override
	public Flux<Val> evaluate(Val parentValue, Map<String, JsonNode> variables, List<Flux<Val>> args) {
				
		List<ParameterSpecificMockReturnValue> matchingParameterSpecificMockReturnValues = findMatchingParentValueMockReturnValue(parentValue);
				
		checkAtLeastOneMatchingMockReturnValueExists(matchingParameterSpecificMockReturnValues);
		
		return Flux.combineLatest(args, (latestPublishedEventsPerArgument) -> {
			
			//interpret a call to an AttributeMock as 
			//not when the evaluate method is called
			//but for every combination of Vals from parentValue and by argument flux emitted
			saveCall(parentValue, latestPublishedEventsPerArgument);
			
			for(ParameterSpecificMockReturnValue parameterSpecificMockReturnValue : matchingParameterSpecificMockReturnValues) {

				var argumentMatchers = parameterSpecificMockReturnValue.getExpectedParameters().getArgumentMatchers();
				
				checkAttributeArgumentsCountEqualsNumberOfArgumentMatcher(argumentMatchers.getMatchers(),
						latestPublishedEventsPerArgument);
				
				if(isEveryArgumentValueMatchingItsMatcher(argumentMatchers.getMatchers(), latestPublishedEventsPerArgument)) {
					return parameterSpecificMockReturnValue.getMockReturnValue();
				} 
			}
			throw new SaplTestException(ERROR_NO_MATCHING_ARGUMENTS);
		});			
	}
	
	private void saveCall(Val parentValue, Object[] arguments) {
		Val[] parameter = new Val[1 + arguments.length];
		parameter[0] = parentValue;
		for(int i = 0; i < arguments.length; i++) {
			parameter[i+1] = (Val) arguments[i];
		}
		this.mockRunInformation.saveCall(new MockCall(parameter));
	}
	
	private boolean isEveryArgumentValueMatchingItsMatcher(Matcher<Val>[] argumentMatchers, Object[] latestPublishedEventsPerArgument) {
		boolean isMatching = true;
		for(int i = 0; i<argumentMatchers.length; i++) {
			Matcher<Val> argumentMatcher = argumentMatchers[i];
			Val argumentValue = (Val) latestPublishedEventsPerArgument[i];
			if(!argumentMatcher.matches(argumentValue)) isMatching = false;
		}
		return isMatching;
	}

	private void checkAttributeArgumentsCountEqualsNumberOfArgumentMatcher(Matcher<Val>[] argumentMatchers,
			Object[] latestPublishedEventsPerArgument) {
		if(latestPublishedEventsPerArgument.length != argumentMatchers.length) {
			throw new SaplTestException(String.format(ERROR_INVALID_NUMBER_PARAMETERS, this.fullname, argumentMatchers.length, latestPublishedEventsPerArgument.length));
		}
	}

	private void checkAtLeastOneMatchingMockReturnValueExists(
			List<ParameterSpecificMockReturnValue> matchingParameterSpecificMockReturnValues) {
		if(matchingParameterSpecificMockReturnValues.size() == 0) {
			throw new SaplTestException(ERROR_NO_MATCHING_PARENTVALUE);
		}
	}
	
	private List<ParameterSpecificMockReturnValue> findMatchingParentValueMockReturnValue(Val parentValue) {
		return this.listParameterSpecificMockReturnValues.stream().filter((ParameterSpecificMockReturnValue mock) -> {
			return mock.getExpectedParameters().getParentValueMatcher().getMatcher().matches(parentValue);
		}).collect(Collectors.toList());
	}

	@Override
	public void assertVerifications() {
		this.listMockingVerifications.stream().forEach((verification) -> verification.verify(this.mockRunInformation));
	}

	@Override
	public String getErrorMessageForCurrentMode() {
		return String.format(ERROR_DUPLICATE_MOCK_REGISTRATION_FOR_PARAMETERS, this.fullname);
	}
	
	@Getter
	@AllArgsConstructor
	static class ParameterSpecificMockReturnValue {
		private AttributeParameters expectedParameters;		
		private Val mockReturnValue;
	}
}
