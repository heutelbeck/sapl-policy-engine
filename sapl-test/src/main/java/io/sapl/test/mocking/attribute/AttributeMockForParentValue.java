package io.sapl.test.mocking.attribute;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.sapl.api.interpreter.Val;
import io.sapl.test.Imports;
import io.sapl.test.SaplTestException;
import io.sapl.test.mocking.MockCall;
import io.sapl.test.mocking.attribute.models.AttributeParentValueMatcher;
import io.sapl.test.verification.MockRunInformation;
import io.sapl.test.verification.MockingVerification;
import io.sapl.test.verification.TimesParameterCalledVerification;

import org.hamcrest.Matcher;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import reactor.core.publisher.Flux;

public class AttributeMockForParentValue implements AttributeMock {
	private static final String ERROR_DUPLICATE_MOCK_REGISTRATION_FOR_PARAMETERS = "You already defined a Mock for %s which is returning specified values when parameters are matching the expectation";
	private static final String ERROR_NO_MATCHING_PARENTVALUE = "Unable to find a mocked return value for this parent value";
	
	private final String fullname;
	private List<ParameterSpecificMockReturnValue> listParameterSpecificMockReturnValues;
	private final MockRunInformation mockRunInformation;
	private final List<MockingVerification> listMockingVerifications;

	public AttributeMockForParentValue(String fullname) {
		this.fullname = fullname;
		this.listParameterSpecificMockReturnValues = new LinkedList<>();
		this.mockRunInformation = new MockRunInformation(fullname);
		this.listMockingVerifications = new LinkedList<>();
	}

	public void loadMockForParentValue(AttributeParentValueMatcher parentValueMatcher, Val returnValue) {	
		this.listParameterSpecificMockReturnValues.add(new ParameterSpecificMockReturnValue(parentValueMatcher.getMatcher(), returnValue));
		
		this.listMockingVerifications.add(new TimesParameterCalledVerification(Imports.times(1), List.of(parentValueMatcher.getMatcher())));
	}
	
	@Override
	public Flux<Val> evaluate(Val parentValue, Map<String, JsonNode> variables, List<Flux<Val>> args) {
		this.mockRunInformation.saveCall(new MockCall(parentValue));
		
		Optional<ParameterSpecificMockReturnValue> matchingParameterSpecificMockReturnValues = findMatchingParameterSpecificMockReturnValue(parentValue);
				
		checkAtLeastOneMatchingMockReturnValueExists(matchingParameterSpecificMockReturnValues);

		return Flux.just(matchingParameterSpecificMockReturnValues.get().getMockReturnValue());
	}

	private void checkAtLeastOneMatchingMockReturnValueExists(
			Optional<ParameterSpecificMockReturnValue> matchingParameterSpecificMockReturnValues) {
		if(matchingParameterSpecificMockReturnValues.isEmpty()) {
			throw new SaplTestException(ERROR_NO_MATCHING_PARENTVALUE);
		}
	}
	
	private Optional<ParameterSpecificMockReturnValue> findMatchingParameterSpecificMockReturnValue(Val parentValue) {
		return this.listParameterSpecificMockReturnValues.stream().filter((ParameterSpecificMockReturnValue mock) -> {
			return mock.getExpectedParentValue().matches(parentValue);
		}).findFirst();
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
		private Matcher<Val> expectedParentValue;		
		private Val mockReturnValue;
	}
}
